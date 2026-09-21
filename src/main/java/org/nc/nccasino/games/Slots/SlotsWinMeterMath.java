package org.nc.nccasino.games.Slots;

/**
 * Pure timing math for the Last Win count-up animation.
 *
 * <p>The bug this replaces: the old code computed {@code increment = payout
 * / steps} with floor division. For a payout just below a multiple of the
 * step count (e.g. 39 at 20 steps: floor(39/20) = 1), the resulting
 * increment under-shoots badly enough that the number of ticks actually
 * needed to reach the payout ({@code ceil(payout / increment)}) can exceed
 * the method's own claimed bound. Using ceiling division for the increment
 * instead guarantees the reverse: however the payout divides, the number of
 * ticks needed to reach it from an increment this large can never exceed
 * {@code steps}.
 */
public final class SlotsWinMeterMath {

    private SlotsWinMeterMath() {
    }

    /** How many discrete steps the animation has to work with. */
    public static long steps(long maxTicks, long stepTicks) {
        if (stepTicks <= 0) {
            throw new IllegalArgumentException("stepTicks must be positive");
        }
        return Math.max(1L, maxTicks / stepTicks);
    }

    /**
     * The per-tick increment for a given payout and step budget, rounded up
     * so the animation can never take more than {@code steps} ticks to
     * complete (see {@link #ticksNeeded}).
     */
    public static long increment(long payout, long steps) {
        if (payout <= 0) {
            return 0L;
        }
        long safeSteps = Math.max(1L, steps);
        return ceilDiv(payout, safeSteps);
    }

    /** How many ticks {@code increment}-sized steps actually need to reach {@code payout}. */
    public static long ticksNeeded(long payout, long increment) {
        if (payout <= 0) {
            return 0L;
        }
        if (increment <= 0) {
            throw new IllegalArgumentException("increment must be positive for a positive payout");
        }
        return ceilDiv(payout, increment);
    }

    /**
     * Overflow-safe ceiling division -- {@code numerator + denominator - 1}
     * would overflow for a numerator near {@link Long#MAX_VALUE}, which a
     * sufficiently large payout can genuinely reach.
     */
    private static long ceilDiv(long numerator, long denominator) {
        long quotient = numerator / denominator;
        long remainder = numerator % denominator;
        return remainder == 0 ? quotient : quotient + 1;
    }
}
