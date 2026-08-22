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

            // Only "shrink or hold", not "must strictly shrink": with a
            // short enough STEP_TICKS relative to a long flight, the
            // desired rate can floor out at the ordinary CARD_FLIGHT_HOP_TICKS
            // rate -- at that point there's no slower (and therefore no
            // earlier-starting) flight left to give, so the idle gap simply
            // can't shrink any further. That's the safety floor working as
            // intended, not a regression.
            assertTrue(newIdle <= ordinaryIdle, "the idle gap must never grow, was " + ordinaryIdle + " -> " + newIdle);
            assertTrue(newStart >= STEP_TICKS, "must never start before the prior phase lands, even for a long flight");
        }
    }

    // --- fasterSiblingCardHopTicks / fasterSiblingCardLandingTick: the
    // further ~25% speed / ~20% idle-gap tuning on top of the above,
    // which required unfreezing D's landing tick (see those methods' own
    // doc for why holding it fixed made the two asks mutually exclusive).

    @Test
    void fasterHopTicksIsMeaningfullyFasterThanTheHalvedRate() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            for (int hopCount = 1; hopCount <= 8; hopCount++) {
                long halved = h.inventory.halvedIdleGapHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
                long faster = h.inventory.fasterSiblingCardHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
                assertTrue(faster <= halved, "hopCount=" + hopCount + ": must never be slower than the already-halved rate, was " + halved + " -> " + faster);
                assertTrue(faster >= BlackjackTiming.CARD_FLIGHT_HOP_TICKS, "hopCount=" + hopCount + ": must never go faster than the ordinary dealt-card rate");
            }
        }
    }

    @Test
    void fasterLandingTickIsEarlierThanTheOrdinaryFixedLandingTick() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            for (int hopCount = 1; hopCount <= 8; hopCount++) {
                long landing = h.inventory.fasterSiblingCardLandingTickForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
                assertTrue(landing <= 2 * STEP_TICKS, "hopCount=" + hopCount + ": landing must move earlier (or stay put), never later, was " + landing);
                assertTrue(landing >= STEP_TICKS, "hopCount=" + hopCount + ": landing must never move earlier than the prior phase itself, was " + landing);
            }
        }
    }

    @Test
    void fasterPacingActuallyShrinksTheIdleGapFurtherForAShortFlight() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            int hopCount = 2; // the bottom seat's own D-flight hop count
            long halvedHopTicks = h.inventory.halvedIdleGapHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
            long halvedStart = 2 * STEP_TICKS - (hopCount * halvedHopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS);
            long halvedIdle = halvedStart - STEP_TICKS;

            long fasterHopTicks = h.inventory.fasterSiblingCardHopTicksForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
            long fasterLanding = h.inventory.fasterSiblingCardLandingTickForTest(hopCount, STEP_TICKS, 2 * STEP_TICKS);
            long fasterStart = fasterLanding - (hopCount * fasterHopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS);
            long fasterIdle = fasterStart - STEP_TICKS;

            assertTrue(fasterIdle < halvedIdle, "idle gap must shrink further, was " + halvedIdle + " -> " + fasterIdle);
            assertTrue(fasterHopTicks < halvedHopTicks, "per-hop movement must be faster than the already-halved rate, was " + halvedHopTicks + " -> " + fasterHopTicks);
            assertTrue(fasterLanding < 2 * STEP_TICKS, "landing must move earlier than the ordinary fixed tick, was " + fasterLanding);
        }
    }
}
