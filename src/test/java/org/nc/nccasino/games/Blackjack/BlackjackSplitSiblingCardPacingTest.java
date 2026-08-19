package org.nc.nccasino.games.Blackjack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@code BlackjackInventory#halvedIdleGapHopTicks} -- the rate
 * D's own deal-in flight uses so it starts about halfway through what would
 * otherwise be idle screen time between C's own flip and D visibly
 * beginning to move, without moving D's own fixed landing tick at all.
 *
 * <p>Computed per-call (from the flight's own hop count) rather than a flat
 * constant, specifically because a flat rate calibrated for a short flight
 * (a seat close to the deck) would, for a longer one (a seat far from the
 * deck), overshoot and start moving before the prior phase has even landed
 * -- the safety property this class pins down across a range of hop counts.
 */
class BlackjackSplitSiblingCardPacingTest {

    private static final long STEP_TICKS = BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS;

    @Test
    void neverStartsAFlightBeforeThePriorPhaseLands() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            for (int hopCount = 1; hopCount <= 8; hopCount++) {
                long hopTicks = h.inventory.halvedIdleGapHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
                long flightTicks = hopCount * hopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
                long flightStart = 2 * STEP_TICKS - flightTicks;
                assertTrue(flightStart >= STEP_TICKS,
                    "hopCount=" + hopCount + ": flight must never start before the prior phase lands, was " + flightStart);
                assertTrue(hopTicks >= BlackjackTiming.CARD_FLIGHT_HOP_TICKS,
                    "hopCount=" + hopCount + ": must never go faster than the ordinary dealt-card rate");
            }
        }
    }

    @Test
    void actuallyCutsTheIdleGapRoughlyInHalfForAShortFlight() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            int hopCount = 2; // the bottom seat's own D-flight hop count
            long ordinaryFlightTicks = hopCount * BlackjackTiming.CARD_FLIGHT_HOP_TICKS + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
            long ordinaryStart = 2 * STEP_TICKS - ordinaryFlightTicks;
            long ordinaryIdle = ordinaryStart - STEP_TICKS;

            long hopTicks = h.inventory.halvedIdleGapHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
            long newFlightTicks = hopCount * hopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
            long newStart = 2 * STEP_TICKS - newFlightTicks;
            long newIdle = newStart - STEP_TICKS;

            assertTrue(newIdle < ordinaryIdle, "the idle gap must actually shrink, was " + ordinaryIdle + " -> " + newIdle);
            assertTrue(newIdle <= ordinaryIdle / 2 + 2,
                "must land close to half the original idle gap (" + ordinaryIdle + "), was " + newIdle);
        }
    }

    @Test
    void actuallyCutsTheIdleGapRoughlyInHalfForALongFlight() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            int hopCount = 6; // the topmost seat's own D-flight hop count (farthest from the deck)
            long ordinaryFlightTicks = hopCount * BlackjackTiming.CARD_FLIGHT_HOP_TICKS + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
            long ordinaryStart = 2 * STEP_TICKS - ordinaryFlightTicks;
            long ordinaryIdle = ordinaryStart - STEP_TICKS;

            long hopTicks = h.inventory.halvedIdleGapHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
            long newFlightTicks = hopCount * hopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
            long newStart = 2 * STEP_TICKS - newFlightTicks;
            long newIdle = newStart - STEP_TICKS;

            assertTrue(newIdle < ordinaryIdle, "the idle gap must actually shrink, was " + ordinaryIdle + " -> " + newIdle);
            assertTrue(newStart >= STEP_TICKS, "must never start before the prior phase lands, even for a long flight");
        }
    }
}
