package org.nc.nccasino.games.Slots;

/**
 * Coordinates the Last Win count-up animation's lifecycle on top of the pure
 * {@link SlotsLastWinState} model, so the actual orchestration sequence used
 * by {@link SlotsMachine} -- not just the state class in isolation -- can be
 * pinned by a Bukkit-free test.
 *
 * <p>The bug this exists to prevent (redesign audit, post-audit correction
 * Section 1): {@code SlotsMachine} used to call {@code settle()}, then
 * immediately call a single "cancel" method to stop any previous scheduler,
 * and that same cancel method also snapped the just-started animation
 * straight to its own target before a single tick ever ran -- so the first
 * scheduled tick saw no animation left to advance, returned a sentinel, and
 * the intended count-up never happened. This class separates "stop the old
 * scheduler" from "cancel an animation and snap to its target," and adds a
 * generation token so a tick already in flight from a superseded animation
 * can never repaint over a newer result.
 */
public final class SlotsWinMeterAnimation {

    private final SlotsLastWinState state = new SlotsLastWinState();
    private long generation = 0;

    public long lastCompletedWin() {
        return state.lastCompletedWin();
    }

    public long displayedWin() {
        return state.displayedWin();
    }

    public boolean isAnimating() {
        return state.isAnimating();
    }

    /**
     * Starts a new settlement's presentation: a count-up from zero for a
     * positive payout, or an immediate loss display for zero.
     *
     * @return the token this animation's scheduled ticks must present to
     *     {@link #tick} to be honored; any tick presenting an earlier token
     *     is stale and is silently ignored
     */
    public long settle(long payout) {
        generation++;
        state.settle(payout);
        return generation;
    }

    /**
     * A settlement retry: a delivery-only event that never restarts the
     * count-up (see {@link SlotsLastWinState#retrySettled}). Still advances
     * the generation, so a tick still in flight from an earlier animation can
     * never land after this.
     */
    public void retrySettled(long payout) {
        generation++;
        state.retrySettled(payout);
    }

    /**
     * One scheduled tick. {@code forGeneration} must be the token returned by
     * the {@link #settle} call this tick belongs to.
     *
     * @return the newly displayed value to render, or {@code -1} if this
     *     tick must not repaint anything -- either it is stale (superseded by
     *     a later settle/retry/interrupt) or nothing is animating
     */
    public long tick(long forGeneration, long increment) {
        if (forGeneration != generation || !state.isAnimating()) {
            return -1L;
        }
        return state.tick(increment);
    }

    /** Whether {@code forGeneration}'s animation is still the live, in-progress one. */
    public boolean isCurrent(long forGeneration) {
        return forGeneration == generation && state.isAnimating();
    }

    /**
     * A genuine interruption -- a new spin starting, the inventory closing,
     * session teardown -- cancels any in-progress animation and snaps the
     * presentation to the authoritative completed payout. Advances the
     * generation, so a tick already scheduled from the interrupted animation
     * can never land afterward. Safe to call when nothing is animating.
     */
    public void interrupt() {
        generation++;
        state.cancelAnimation();
    }
}
