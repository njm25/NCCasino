package org.nc.nccasino.games.Slots;

import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * One in-flight Slots chat prompt: everything
 * {@link SlotsChatPromptService} needs to route a submitted line, decide
 * whether it is still relevant, and end the prompt correctly.
 *
 * <p>Every Slots prompt -- profile naming and all four Auto Spin Settings
 * values -- is one of these. There is deliberately no per-prompt chat
 * listener: the single service owns the only
 * {@code AsyncPlayerChatEvent} handler, and this class carries the
 * per-prompt behavior.
 *
 * <p>The {@code session}/{@code sessionGeneration} pair is the staleness
 * guard: a callback that arrives after the owning Slots session was closed,
 * replaced, or moved on to a different prompt must be a total no-op, exactly
 * like the machine's animation generation counters.
 */
public final class SlotsChatPrompt {

    /** Which value this prompt is collecting -- also selects its instruction text. */
    public enum Type {
        PROFILE_NAME,
        SPIN_LIMIT,
        BIG_WIN_MULTIPLIER,
        PROFIT_TARGET,
        LOSS_LIMIT
    }

    /** What one submitted line did to the prompt. */
    public enum Outcome {
        /** Parsed and applied; the prompt is finished. */
        ACCEPTED,
        /** Rejected with an explanation; the prompt stays open for the remaining time. */
        RETRY,
        /** The player asked to cancel. */
        CANCELLED
    }

    /** Why a prompt ended without the player completing it. */
    public enum EndReason {
        /** The 60-second deadline expired: terminate the session, never reopen it. */
        TIMED_OUT,
        /** The player opened another game: cancel, close the old session, let the new game take over. */
        ANOTHER_GAME_OPENED,
        /** The player disconnected. */
        DISCONNECTED,
        /** The owning session was terminated, the dealer removed, or the plugin disabled. */
        SESSION_ENDED,
        /** A newer prompt replaced this one. */
        SUPERSEDED
    }

    /**
     * The per-prompt validator/parser, success action, and cancel action.
     * Every method is invoked on the main server thread by
     * {@link SlotsChatPromptService}, never on the async chat thread.
     */
    public interface Handler {

        /** Whether the owning Slots session is still the live one this prompt belongs to. */
        boolean isSessionValid();

        /**
         * Parses and applies one submitted line. Implementations explain any
         * rejection to the player themselves and return {@link Outcome#RETRY}
         * so the player can try again within the remaining time. Must never
         * reopen the inventory -- {@link #accepted()} owns that, and only
         * after the prompt has been removed.
         */
        Outcome submit(String input);

        /** Reopens the return view after a successful submission. */
        void accepted();

        /** Reopens the return view after the player cancelled. */
        void cancelled();

        /** The deadline expired: terminate the Slots session and return the player to ordinary play. */
        void timedOut();

        /** The prompt was ended by something other than the player answering it. */
        void ended(EndReason reason);
    }

    private final UUID playerId;
    private final Type type;
    private final long deadlineMillis;
    private final SlotsUiView returnView;
    private final InventoryHolder session;
    private final long sessionGeneration;
    private final Handler handler;

    /**
     * Profile naming only: the already-validated name that is waiting for an
     * explicit {@code overwrite} confirmation. Null whenever no confirmation
     * is pending. Confirming does <em>not</em> restart the deadline.
     */
    private String pendingOverwriteName;

    public SlotsChatPrompt(
        UUID playerId,
        Type type,
        long deadlineMillis,
        SlotsUiView returnView,
        InventoryHolder session,
        long sessionGeneration,
        Handler handler) {

        if (playerId == null || type == null || handler == null) {
            throw new IllegalArgumentException("a chat prompt needs a player, a type and a handler");
        }
        this.playerId = playerId;
        this.type = type;
        this.deadlineMillis = deadlineMillis;
        this.returnView = returnView == null ? SlotsUiView.GAME : returnView;
        this.session = session;
        this.sessionGeneration = sessionGeneration;
        this.handler = handler;
    }

    public UUID playerId() {
        return playerId;
    }

    public Type type() {
        return type;
    }

    public long deadlineMillis() {
        return deadlineMillis;
    }

    /** Whole seconds left before this prompt expires, never negative. */
    public long remainingSeconds(long nowMillis) {
        return Math.max(0L, (deadlineMillis - nowMillis + 999L) / 1000L);
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= deadlineMillis;
    }

    /** The view to reopen on success or cancel. */
    public SlotsUiView returnView() {
        return returnView;
    }

    /** The owning Slots machine, used to tell its own reopen apart from another game being opened. */
    public InventoryHolder session() {
        return session;
    }

    public long sessionGeneration() {
        return sessionGeneration;
    }

    public Handler handler() {
        return handler;
    }

    public String pendingOverwriteName() {
        return pendingOverwriteName;
    }

    public void awaitOverwriteConfirmation(String name) {
        this.pendingOverwriteName = name;
    }

    public void clearOverwriteConfirmation() {
        this.pendingOverwriteName = null;
    }

    /** This prompt's {@code slots.prompt-*} instruction key. */
    public String instructionKey() {
        return "slots.prompt-" + type.name().toLowerCase().replace('_', '-');
    }
}
