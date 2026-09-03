package org.nc.nccasino.games.Slots;

/**
 * Pure generation/config/mode staleness guard for the Paylines blink flash
 * (Section 12), extracted from {@link SlotsMachine}'s scheduled-frame
 * callback so the exact supersession/invalidation rule -- and the sequences
 * of events that exercise it (rapid same-direction changes, rapid
 * opposite-direction changes, a wrap, a stale callback after cancellation)
 * -- can be unit-tested without Bukkit's scheduler or a live inventory.
 *
 * <p>{@link SlotsMachine} holds exactly one instance per session and drives
 * it: {@link #supersede()} at the start of every new Paylines input (and at
 * every mode/geometry change, spin start, or teardown that must invalidate
 * an active flash) and {@link #isStale} inside every scheduled frame
 * callback before painting anything.
 */
public final class SlotsLineFlashGuard {

    private long generation = 0;

    /**
     * Cancels/invalidates whatever flash is currently active, if any --
     * bumps the generation so any token already captured by a scheduled
     * frame goes stale. Safe to call even when nothing is active.
     *
     * @return the new generation, valid whether or not a new flash follows
     */
    public long cancel() {
        return ++generation;
    }

    /**
     * Synchronously supersedes any active flash and returns the token a new
     * flash's scheduled frames must capture. Identical to {@link #cancel()}
     * -- the distinct name documents the call site's intent: this is always
     * followed immediately by rendering the newest change's own feedback,
     * never left as a bare cancellation.
     */
    public long supersede() {
        return cancel();
    }

    public long currentGeneration() {
        return generation;
    }

    /**
     * @return true if a scheduled frame captured under {@code token} (and
     *     the config/mode active when it was scheduled) must not paint,
     *     given the session's live state right now. A frame is stale if the
     *     session has closed, a later flash (or any other cancellation) has
     *     superseded it, or the geometry/view has changed since it was
     *     scheduled -- even if no explicit cancellation happened to run.
     */
    public boolean isStale(
            long token, boolean closeFlag,
            Object configAtFlash, Object currentConfig,
            Object modeAtFlash, Object currentMode) {
        return closeFlag
            || token != generation
            || configAtFlash != currentConfig
            || modeAtFlash != currentMode;
    }
}
