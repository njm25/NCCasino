package org.nc.nccasino.games.Slots;

/**
 * Converts real server ticks into the reel animation's <em>simulated</em>
 * ticks at a chosen {@link SlotsSpinSpeed}, exactly and without drift.
 *
 * <p>The reel schedule ({@link SlotsReelPlan}) is defined once, in simulated
 * ticks, and must stay tick-exact at every speed: every scheduled advance,
 * landing, anticipation and reveal event happens on its own simulated tick,
 * in order, exactly once. So speed must never rewrite that schedule -- it
 * only changes how many simulated ticks each real tick is worth.
 *
 * <p>The animation loop still runs {@code runTaskTimer} every real tick and
 * asks this class, once per real tick, how many simulated ticks to play out
 * now. Because the answer is derived from an exact rational progression
 * ({@code floor(realTicks * numerator / denominator)}) rather than an
 * accumulated float, the count never drifts and the totals are exact:
 *
 * <ul>
 *   <li>{@link SlotsSpinSpeed#FAST} (2/1) yields 2, 2, 2, ... -- 50% duration.
 *   <li>{@link SlotsSpinSpeed#NORMAL} (1/1) yields 1, 1, 1, ... -- the baseline.
 *   <li>{@link SlotsSpinSpeed#SLOW} (2/3) yields 0, 1, 1, 0, 1, 1, ... -- two
 *   simulated ticks every three real ticks, i.e. 150% duration.
 * </ul>
 *
 * <p>A real tick that yields zero simulated ticks is what makes SLOW
 * possible at all: the earlier integer {@code speedStep} model advanced at
 * least one simulated tick per real tick, so it could only ever speed the
 * presentation up.
 */
public final class SlotsReelCadence {

    private final long numerator;
    private final long denominator;
    private long realTicks;
    private long simulatedTicks;

    public SlotsReelCadence(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException(
                "cadence must be positive; got " + numerator + "/" + denominator);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static SlotsReelCadence forSpeed(SlotsSpinSpeed speed) {
        SlotsSpinSpeed effective = speed == null ? SlotsSpinSpeed.NORMAL : speed;
        return new SlotsReelCadence(effective.cadenceNumerator(), effective.cadenceDenominator());
    }

    /**
     * Consumes one real server tick.
     *
     * @return how many simulated ticks the caller must play out now, in
     *     order, starting at {@link #simulatedTicksElapsed()} before the
     *     call. Zero is a legitimate answer at {@link SlotsSpinSpeed#SLOW}.
     */
    public long advanceOneRealTick() {
        realTicks++;
        long target = simulatedTicksAfter(realTicks);
        long produced = target - simulatedTicks;
        simulatedTicks = target;
        return produced;
    }

    /** Total simulated ticks emitted so far -- the next one to play out. */
    public long simulatedTicksElapsed() {
        return simulatedTicks;
    }

    /** Total real server ticks consumed so far. */
    public long realTicksElapsed() {
        return realTicks;
    }

    /** How many simulated ticks have been emitted once {@code realTicks} real ticks have passed. */
    public long simulatedTicksAfter(long realTicks) {
        if (realTicks <= 0) {
            return 0L;
        }
        return Math.multiplyExact(realTicks, numerator) / denominator;
    }

    /** How many real ticks it takes to reach {@code simulated} simulated ticks -- the presentation's real duration. */
    public long realTicksFor(long simulated) {
        if (simulated <= 0) {
            return 0L;
        }
        // The smallest r with floor(r * num / den) >= simulated.
        return ceilDiv(Math.multiplyExact(simulated, denominator), numerator);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
