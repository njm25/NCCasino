package org.nc.nccasino.games.Slots;

/**
 * The Last Win control's state, extracted as a pure (Bukkit-free) model so
 * its lifecycle can be tested without a live inventory.
 *
 * <p>Two values matter, and keeping them distinct is the whole fix this
 * class exists for:
 * <ul>
 *   <li>{@link #lastCompletedWin()} -- the authoritative result of the last
 *   completed real spin. Set exactly once per settlement (or settlement
 *   retry) and never touched by meter animation.
 *   <li>{@link #displayedWin()} -- what the control currently shows. During
 *   a count-up animation this trails {@link #lastCompletedWin()}; at every
 *   other time, and always once the animation ends or is cancelled, it
 *   equals it exactly.
 * </ul>
 *
 * <p>The bug this replaces: the old code rendered the full payout, then
 * started a count-up whose internal counter began at zero, so the very next
 * tick visibly overwrote the full amount with a small partial one -- and if
 * that count-up was interrupted (a new spin, a close, any task cleanup), the
 * partial value stuck around as "the" last win until the next settlement.
 * Both are structurally impossible here: {@link #settle} never sets
 * {@link #displayedWin()} to the full amount before an animation starts, and
 * {@link #cancelAnimation()} always snaps the presentation back to the
 * authoritative value rather than leaving whatever the animation last
 * reached.
 */
public final class SlotsLastWinState {

    private long lastCompletedWin = -1L;
    private long displayedWin = -1L;
    private long animationTarget = -1L;
    private long animationShown = 0L;

    /** No spins yet: the initial state, and the only way back to it (there is none in play). */
    public long lastCompletedWin() {
        return lastCompletedWin;
    }

    /** What the control should currently render. */
    public long displayedWin() {
        return displayedWin;
    }

    public boolean isAnimating() {
        return animationTarget >= 0;
    }

    /**
     * A real spin just settled for {@code payout}. A positive payout begins
     * a count-up from zero -- never from, or through, the full amount first
     * -- so there is no full-to-partial backward jump to begin with. A zero
     * payout (a loss) shows immediately; there is nothing to animate.
     */
    public void settle(long payout) {
        lastCompletedWin = payout;
        if (payout > 0) {
            animationTarget = payout;
            animationShown = 0L;
            displayedWin = 0L;
        } else {
            animationTarget = -1L;
            animationShown = 0L;
            displayedWin = 0L;
        }
    }

    /**
     * A settlement retry resolved. This is a delivery event, not a new
     * result -- the spin's payout was already fixed and already shown (or
     * already snapped to) when it first settled, so this never re-triggers
     * the count-up animation. Re-animating here would visibly drop the
     * display back to zero and count back up to the same number it was
     * already showing, which is exactly the backward-jump defect this class
     * exists to prevent.
     */
    public void retrySettled(long payout) {
        lastCompletedWin = payout;
        animationTarget = -1L;
        animationShown = 0L;
        displayedWin = payout;
    }

    /**
     * Advances the running count-up by {@code increment} (already
     * pre-computed by {@link SlotsWinMeterMath#increment}). No-op if no
     * animation is running.
     *
     * @return the newly displayed value, or -1 if nothing was animating
     */
    public long tick(long increment) {
        if (!isAnimating()) {
            return -1L;
        }
        animationShown = Math.min(animationTarget, animationShown + Math.max(1L, increment));
        displayedWin = animationShown;
        if (animationShown >= animationTarget) {
            animationTarget = -1L;
        }
        return displayedWin;
    }

    /**
     * Cancels any in-progress animation, snapping the presentation to the
     * authoritative last-completed value -- the exact fix for "cancelled
     * mid-count leaves a stale partial amount forever." Safe to call when
     * nothing is animating (a no-op).
     */
    public void cancelAnimation() {
        if (isAnimating()) {
            animationTarget = -1L;
            animationShown = 0L;
            displayedWin = lastCompletedWin;
        }
    }
}
