package org.nc.nccasino.games.Slots;

/**
 * Named animation delays, in server ticks. Pure constants -- nothing here
 * decides an outcome, and none of it can alter a result already committed by
 * {@link SlotsSpinController}.
 *
 * <p>Kept in one place, the way {@code BlackjackTiming} is, so the ordering
 * relationships between phases stay visible and testable rather than being
 * scattered as inline literals across the renderer.
 */
public final class SlotsTiming {

    private SlotsTiming() {
    }

    // ---- reel spin ------------------------------------------------------

    /** The animation ticker runs every tick; individual reels advance on their own schedule. */
    public static final long TICK_INTERVAL = 1L;

    /** Ticks between symbol advances while a reel is at full speed. */
    public static final long SPIN_STEP_TICKS = 1L;

    /** How long the leftmost reel spins at full speed before it begins slowing. */
    public static final long FIRST_REEL_SPIN_TICKS = 14L;

    /** Each reel to the right spins this much longer than the one before it. */
    public static final long REEL_STAGGER_TICKS = 9L;

    /**
     * How many progressively-slower advances a reel makes as it settles. The
     * deceleration is what sells the reel as a physical object with mass --
     * a reel that simply stops dead reads as a texture swap.
     */
    public static final int DECELERATION_STEPS = 4;

    /** Each deceleration step waits this many ticks longer than the previous one. */
    public static final long DECELERATION_GROWTH_TICKS = 3L;

    /**
     * Extra ticks the final reel hangs when the reels already down could
     * still become a big win. This is the single most important piece of
     * tension in a slot machine: the pause exists only when it means
     * something, so players learn to read it.
     */
    public static final long ANTICIPATION_TICKS = 26L;

    /** How long a reel's landing bounce (overshoot then settle) takes. */
    public static final long REEL_LANDING_BOUNCE_TICKS = 2L;

    // ---- win presentation ------------------------------------------------

    /** Pause after the last reel lands before any win is announced. */
    public static final long PRE_REVEAL_PAUSE_TICKS = 6L;

    /** How long each winning line stays lit during the sequential walk-through. */
    public static final long LINE_REVEAL_HOLD_TICKS = 14L;

    /** Gap between one winning line going dark and the next lighting up. */
    public static final long LINE_REVEAL_GAP_TICKS = 3L;

    /** After every line has been walked, all winners light together for this long. */
    public static final long ALL_LINES_FINALE_TICKS = 20L;

    /** Ticks between win-meter count-up increments. */
    public static final long WIN_METER_STEP_TICKS = 2L;

    /** The count-up never takes longer than this, however large the win. */
    public static final long WIN_METER_MAX_TICKS = 40L;

    /** Pause on a losing spin before controls unlock -- short, so a dead spin does not drag. */
    public static final long LOSS_SETTLE_TICKS = 8L;

    // ---- idle / attract --------------------------------------------------

    /** How often the idle attract shimmer advances while the machine sits unplayed. */
    public static final long ATTRACT_STEP_TICKS = 8L;

    /**
     * Total ticks a spin takes with no anticipation and no win, for the
     * widest machine -- used to sanity-check that a spin never outlives its
     * own callback guard.
     */
    public static long worstCaseSpinTicks(int columns) {
        return lastReelStopTick(columns) + ANTICIPATION_TICKS + PRE_REVEAL_PAUSE_TICKS
            + (SlotsPayline.MAX_LINES * (LINE_REVEAL_HOLD_TICKS + LINE_REVEAL_GAP_TICKS))
            + ALL_LINES_FINALE_TICKS + WIN_METER_MAX_TICKS;
    }

    /** Tick at which reel {@code index} finishes decelerating, ignoring anticipation. */
    public static long reelStopTick(int index) {
        long fullSpeed = FIRST_REEL_SPIN_TICKS + (index * REEL_STAGGER_TICKS);
        long decelerating = 0L;
        for (int step = 1; step <= DECELERATION_STEPS; step++) {
            decelerating += SPIN_STEP_TICKS + (step * DECELERATION_GROWTH_TICKS);
        }
        return fullSpeed + decelerating + REEL_LANDING_BOUNCE_TICKS;
    }

    public static long lastReelStopTick(int columns) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        return reelStopTick(columns - 1);
    }
}
