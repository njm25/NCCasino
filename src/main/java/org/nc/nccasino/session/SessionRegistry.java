package org.nc.nccasino.session;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Central, UUID-keyed, idempotent entry point for tearing down a player's
 * active game sessions. Games register themselves when that player enters
 * a wager-bearing state, and
 * unregister when it resolves normally.
 *
 * <p>{@link #terminatePlayerSession} is the single path every disconnect,
 * kick, plugin-shutdown, or voluntary-exit route should funnel through, so
 * a given session is only ever resolved once — regardless of how many
 * events fire around it (Kick, Quit, InventoryClose) or in what order.
 * Idempotency comes from atomically removing the UUID's complete session
 * set before invoking any of it: whichever caller removes the set first is
 * the only one that does any work, and every later call no-ops.
 */
public final class SessionRegistry {

    private static final Map<UUID, Set<TerminableSession>> activeSessions = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingKick = ConcurrentHashMap.newKeySet();

    private SessionRegistry() {
    }

    /**
     * Registers one independently resolvable session for {@code playerId}.
     * Adding the same session instance repeatedly is harmless, while other
     * unresolved games for the player remain registered.
     */
    public static void register(UUID playerId, TerminableSession session) {
        activeSessions.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * Clears only the matching session without disturbing any other
     * unresolved game registered for the same player.
     */
    public static void unregister(UUID playerId, TerminableSession session) {
        activeSessions.computeIfPresent(playerId, (ignored, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public static boolean isRegistered(UUID playerId, TerminableSession session) {
        Set<TerminableSession> sessions = activeSessions.get(playerId);
        return sessions != null && sessions.contains(session);
    }

    /**
     * Claims and terminates only {@code session}, leaving every other game
     * for the UUID registered. Used by voluntary inventory-close paths;
     * true player exits must use {@link #terminatePlayerSession}.
     */
    public static void terminateSession(
        UUID playerId,
        TerminableSession session,
        ExitReason reason
    ) {
        AtomicBoolean claimed = new AtomicBoolean(false);
        activeSessions.computeIfPresent(playerId, (ignored, sessions) -> {
            claimed.set(sessions.remove(session));
            return sessions.isEmpty() ? null : sessions;
        });
        if (claimed.get()) {
            invokeTermination(playerId, session, reason);
        }
    }

    public static boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /** Records that this UUID's next quit was caused by a (non-cancelled) kick. */
    public static void markKicked(UUID playerId) {
        pendingKick.add(playerId);
    }

    /** Clears a pending-kick marker without terminating anything. */
    public static void clearKickMarker(UUID playerId) {
        pendingKick.remove(playerId);
    }

    /**
     * Consumes and returns the exit reason a quit should be classified as:
     * {@link ExitReason#KICKED} if this UUID was just marked by a kick,
     * otherwise {@link ExitReason#DISCONNECTED}. Clears the marker either
     * way, so it is only ever consumed once per quit.
     */
    public static ExitReason consumeQuitReason(UUID playerId) {
        return pendingKick.remove(playerId) ? ExitReason.KICKED : ExitReason.DISCONNECTED;
    }

    /**
     * The single idempotent termination entry point. Safe to call more
     * than once, from more than one listener, in any order: only the call
     * that actually finds and removes an active registration performs any
     * work.
     */
    public static void terminatePlayerSession(UUID playerId, ExitReason reason) {
        Set<TerminableSession> sessions = activeSessions.remove(playerId);
        if (sessions == null) {
            return;
        }
        for (TerminableSession session : sessions) {
            invokeTermination(playerId, session, reason);
        }
    }

    private static void invokeTermination(
        UUID playerId,
        TerminableSession session,
        ExitReason reason
    ) {
        try {
            session.onSessionTerminated(playerId, reason);
        } catch (RuntimeException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                "[NCCasino] Failed to terminate session for " + playerId + " (" + reason + ")", e);
        }
    }

    /**
     * Terminates every currently active session with {@code reason},
     * isolating failures per player so one broken session does not stop
     * the rest from being resolved. Intended for plugin shutdown.
     */
    public static void terminateAll(ExitReason reason) {
        for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
            terminatePlayerSession(playerId, reason);
        }
    }
}
