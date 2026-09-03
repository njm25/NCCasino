package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsWinMeterMathTest {

    private static final long MAX_TICKS = SlotsTiming.WIN_METER_MAX_TICKS;
    private static final long STEP_TICKS = SlotsTiming.WIN_METER_STEP_TICKS;

    @Test
    void stepsIsTheConfiguredBudget() {
        assertEquals(20L, SlotsWinMeterMath.steps(MAX_TICKS, STEP_TICKS));
        assertEquals(1L, SlotsWinMeterMath.steps(1L, 100L), "never fewer than one step");
    }

    @Test
    void stepsRejectsANonPositiveStepDuration() {
        assertThrows(IllegalArgumentException.class, () -> SlotsWinMeterMath.steps(40L, 0L));
        assertThrows(IllegalArgumentException.class, () -> SlotsWinMeterMath.steps(40L, -1L));
    }

    @Test
    void zeroOrNegativePayoutNeedsNoIncrementOrTicks() {
        long steps = SlotsWinMeterMath.steps(MAX_TICKS, STEP_TICKS);
        assertEquals(0L, SlotsWinMeterMath.increment(0L, steps));
        assertEquals(0L, SlotsWinMeterMath.ticksNeeded(0L, SlotsWinMeterMath.increment(1L, steps)));
    }

    /**
     * The exact defect: the old {@code payout / steps} floor division could
     * make completion take far longer than the method's own claimed bound.
     * Every one of these payouts -- including the two exact boundaries (39
     * and 59, one below a multiple of the 20-step budget) -- must complete
     * in at most {@code steps} ticks.
     */
    @Test
    void everyPayoutCompletesWithinTheStepBudget() {
        long steps = SlotsWinMeterMath.steps(MAX_TICKS, STEP_TICKS);
        long[] payouts = {1L, 19L, 20L, 21L, 39L, 40L, 41L, 59L, 1_000_000L, Long.MAX_VALUE / 2};
        for (long payout : payouts) {
            long increment = SlotsWinMeterMath.increment(payout, steps);
            assertTrue(increment > 0, "payout " + payout + " must have a positive increment");
            long ticks = SlotsWinMeterMath.ticksNeeded(payout, increment);
            assertTrue(ticks <= steps,
                "payout " + payout + " needed " + ticks + " ticks, exceeding the " + steps + "-tick budget");
        }
    }

    @Test
    void largePayoutsNeverOverflowTheCeilingArithmetic() {
        long steps = SlotsWinMeterMath.steps(MAX_TICKS, STEP_TICKS);
        long increment = SlotsWinMeterMath.increment(Long.MAX_VALUE, steps);
        assertTrue(increment > 0);
        long ticks = SlotsWinMeterMath.ticksNeeded(Long.MAX_VALUE, increment);
        assertTrue(ticks <= steps);
    }

    @Test
    void ticksNeededRejectsANonPositiveIncrementForAPositivePayout() {
        assertThrows(IllegalArgumentException.class, () -> SlotsWinMeterMath.ticksNeeded(10L, 0L));
    }
}
