package org.nc.nccasino.session;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central, UUID-keyed, idempotent entry point for tearing down a player's
 * active game session. Games register themselves as the current owner of a
 * player's session when that player enters a wager-bearing state, and
 * unregister when it resolves normally.
 *
 * <p>{@link #terminatePlayerSession} is the single path every disconnect,
 * kick, plugin-shutdown, or voluntary-exit route should funnel through, so
 * a given session is only ever resolved once — regardless of how many
 * events fire around it (Kick, Quit, InventoryClose) or in what order.
 * Idempotency comes from {@link Map#remove(Object)} being atomic: whichever
 * caller removes the registration first is the only one that does any
 * work, every later call for the same UUID finds nothing and no-ops.
 */
public final class SessionRegistry {

    private static final Map<UUID, TerminableSession> activeSessions = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingKick = ConcurrentHashMap.newKeySet();

    private SessionRegistry() {
    }

    /**
     * Registers {@code session} as the current owner of {@code playerId}'s
     * active game session. Overwrites any previous registration for the
     * same UUID — a new session always supersedes a stale one.
     */
    public static void register(UUID playerId, TerminableSession session) {
        activeSessions.put(playerId, session);
    }

    /**
     * Clears the registration for {@code playerId}, but only if it still
     * points at {@code session}. Prevents a late unregister call from a
     * superseded session clobbering a newer one already registered for the
     * same UUID.
     */
    public static void unregister(UUID playerId, TerminableSession session) {
        activeSessions.remove(playerId, session);
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
        TerminableSession session = activeSessions.remove(playerId);
        if (session == null) {
            return;
        }
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
