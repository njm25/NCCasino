package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure coverage for the redesigned staged split visual sequence: B slides
 * out to a temporary slot, C deals beside A, D deals beside temp-B, the
 * inactive [B][D] pair slides one step left, then both temporary slots
 * clear. See {@link BlackjackSplitAnimationPlan}'s own class doc for the
 * full [A][B] -> [A][C] / [B][D] -> [A][C] visual story.
 */
class BlackjackSplitAnimationPlanTest {

    private static final long STEP_TICKS = 10L;
    private static final int SEAT_SLOT = 9;

    // Card-cell indices within the seat's row: 0=A, 1=origB/laterC, 2=gap, 3=tempB, 4=tempD.
    private static final int SLOT_A = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
    private static final int SLOT_ORIG_B = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 1);
    private static final int SLOT_GAP = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 2);
    private static final int SLOT_TEMP_B = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 3);
    private static final int SLOT_TEMP_D = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 4);

    @Test
    void phase1SlidesBOutToItsTemporarySlot() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);

        assertEquals(BlackjackAnimationStep.Kind.SLIDE_OUT, steps.get(0).getKind());
        assertEquals(SLOT_ORIG_B, steps.get(0).getSlot(), "B's original slot must be vacated");
        assertEquals(0L, steps.get(0).getDelayTicks());

        assertEquals(BlackjackAnimationStep.Kind.MOVE, steps.get(1).getKind());
        assertEquals(SLOT_TEMP_B, steps.get(1).getSlot(), "B must appear at its temporary right-hand slot");
        assertEquals(0L, steps.get(1).getDelayTicks(), "phase 1's two steps happen together");
    }

    @Test
    void phase2DealsCBesideA() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);

        BlackjackAnimationStep dealC = steps.get(2);
        assertEquals(BlackjackAnimationStep.Kind.DEAL, dealC.getKind());
        assertEquals(SLOT_ORIG_B, dealC.getSlot(), "C lands in the slot B just vacated, beside A");
        assertEquals(STEP_TICKS, dealC.getDelayTicks());
    }

    @Test
    void phase3DealsDBesideTempB() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);

        BlackjackAnimationStep dealD = steps.get(3);
        assertEquals(BlackjackAnimationStep.Kind.DEAL, dealD.getKind());
        assertEquals(SLOT_TEMP_D, dealD.getSlot());
        assertEquals(2 * STEP_TICKS, dealD.getDelayTicks());
    }

    @Test
    void phase4SlidesTheInactivePairOneStepLeftWithoutTouchingActiveHandSlots() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);

        BlackjackAnimationStep moveB = steps.get(4);
        BlackjackAnimationStep moveD = steps.get(5);
        assertEquals(BlackjackAnimationStep.Kind.MOVE, moveB.getKind());
        assertEquals(SLOT_GAP, moveB.getSlot(), "B slides into the unused gap cell");
        assertEquals(BlackjackAnimationStep.Kind.MOVE, moveD.getKind());
        assertEquals(SLOT_TEMP_B, moveD.getSlot(), "D slides into B's old temporary slot");
        assertEquals(3 * STEP_TICKS, moveB.getDelayTicks());
        assertEquals(3 * STEP_TICKS, moveD.getDelayTicks());

        for (BlackjackAnimationStep step : steps) {
            assertTrue(step.getSlot() != SLOT_A, "no step may ever touch A's own slot");
        }
    }

    @Test
    void phase5ParksBothTemporarySlotsLeavingOnlyActiveHandVisible() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);

        List<BlackjackAnimationStep> parkSteps = steps.subList(6, steps.size());
        assertEquals(3, parkSteps.size());
        for (BlackjackAnimationStep step : parkSteps) {
            assertEquals(BlackjackAnimationStep.Kind.PARK, step.getKind());
            assertEquals(4 * STEP_TICKS, step.getDelayTicks());
        }
        List<Integer> parkedSlots = parkSteps.stream().map(BlackjackAnimationStep::getSlot).toList();
        assertTrue(parkedSlots.containsAll(List.of(SLOT_GAP, SLOT_TEMP_B, SLOT_TEMP_D)));
        // The active hand's own slots (A and C's shared slot with orig-B) are never parked.
        assertTrue(!parkedSlots.contains(SLOT_A) && !parkedSlots.contains(SLOT_ORIG_B));
    }

    @Test
    void everyStepStaysInsideTheActingSeatsOwnRow() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);
        int rowStart = SEAT_SLOT;
        int rowEnd = SEAT_SLOT + BlackjackSlotLayout.SEAT_ROW_WIDTH - 1;
        for (BlackjackAnimationStep step : steps) {
            assertTrue(step.getSlot() >= rowStart && step.getSlot() <= rowEnd,
                "slot " + step.getSlot() + " escapes seat " + SEAT_SLOT + "'s own row");
        }
    }

    @Test
    void delaysAreMonotonicAcrossPhasesAndDurationMatchesFourPhaseGaps() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(SEAT_SLOT, STEP_TICKS);
        long previous = -1;
        for (BlackjackAnimationStep step : steps) {
            assertTrue(step.getDelayTicks() >= previous, "delays must never decrease across the ordered step list");
            previous = step.getDelayTicks();
        }
        assertEquals(4 * STEP_TICKS, BlackjackSplitAnimationPlan.durationTicks(STEP_TICKS));
        assertEquals(steps.get(steps.size() - 1).getDelayTicks(), BlackjackSplitAnimationPlan.durationTicks(STEP_TICKS));
    }
}
