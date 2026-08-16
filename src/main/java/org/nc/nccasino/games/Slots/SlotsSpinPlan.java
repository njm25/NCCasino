package org.nc.nccasino.games.Slots;

/**
 * Fixed animation timing, in server ticks from spin start. Purely cosmetic
 * -- the committed {@link SlotsOutcome} is decided before any of this runs
 * and none of it can alter or reroll the result.
 */
public final class SlotsSpinPlan {

    public static final long TICKER_INTERVAL = 2L;
    public static final long LEFT_REEL_STOP_TICK = 20L;
    public static final long CENTER_REEL_STOP_TICK = 32L;
    public static final long RIGHT_REEL_STOP_TICK = 44L;
    public static final long HIGHLIGHT_START_TICK = RIGHT_REEL_STOP_TICK + 4L;
    public static final long HIGHLIGHT_DURATION_TICKS = 30L;
    public static final long SETTLE_TICK = HIGHLIGHT_START_TICK + HIGHLIGHT_DURATION_TICKS;

    private SlotsSpinPlan() {
    }

    /** @param reelIndex 0 = left, 1 = center, 2 = right */
    public static boolean isReelStopped(int reelIndex, long elapsedTicks) {
        return switch (reelIndex) {
            case 0 -> elapsedTicks >= LEFT_REEL_STOP_TICK;
            case 1 -> elapsedTicks >= CENTER_REEL_STOP_TICK;
            case 2 -> elapsedTicks >= RIGHT_REEL_STOP_TICK;
            default -> throw new IllegalArgumentException("reelIndex must be 0, 1, or 2");
        };
    }

    public static boolean allReelsStopped(long elapsedTicks) {
        return elapsedTicks >= RIGHT_REEL_STOP_TICK;
    }

    public static boolean isHighlightActive(long elapsedTicks) {
        return elapsedTicks >= HIGHLIGHT_START_TICK && elapsedTicks < SETTLE_TICK;
    }
}
