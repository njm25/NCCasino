package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackDealerInspectionPlanTest {

    private static final long BASE_TICKS = 5L;

    @Test
    void fullPathMatchesBlackjackSlotLayoutsStartTransitionPathInOrder() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        List<Integer> expectedPath = BlackjackSlotLayout.dealerStartTransitionPath();

        assertEquals(expectedPath.size(), steps.size());
        for (int i = 0; i < expectedPath.size(); i++) {
            assertEquals(expectedPath.get(i), steps.get(i).getSlot());
            assertEquals(BlackjackAnimationStep.Kind.MOVE, steps.get(i).getKind());
        }
    }

    @Test
    void everyStepUsesTheSameUniformDurationWithNoStops() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        for (int i = 1; i < steps.size(); i++) {
            long gap = steps.get(i).getDelayTicks() - steps.get(i - 1).getDelayTicks();
            assertEquals(BASE_TICKS, gap, "every leg of the slide must take exactly the base step duration -- no pauses");
        }
    }

    // ==================================================================
    // withBottomRowCoordination: minimum-required bottom-row gate
    // ==================================================================

    @Test
    void coordinationShiftsOnlyTheFinalBottomRowStepByExactlyTheAmountNeeded() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        long naturalFinalStep = steps.get(steps.size() - 1).getDelayTicks(); // slot 53, the only bottom-row slot on this path
        assertEquals(25L, naturalFinalStep);

        long requiredNotBefore = 40L; // later than the natural arrival -- a shift is genuinely required
        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, requiredNotBefore);

        // Every step before the bottom row is completely untouched.
        for (int i = 0; i < steps.size() - 1; i++) {
            assertEquals(steps.get(i).getDelayTicks(), coordinated.get(i).getDelayTicks(), "step " + i + " must never be shifted");
            assertSame(steps.get(i), coordinated.get(i), "an unshifted step should be reused, not copied");
        }
        // The final (bottom-row) step lands exactly on the required time -- the minimum possible shift.
        assertEquals(requiredNotBefore, coordinated.get(coordinated.size() - 1).getDelayTicks());
    }

    @Test
    void coordinationAddsNoUnnecessaryGapWhenTheNaturalArrivalIsAlreadyLateEnough() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        long naturalFinalStep = steps.get(steps.size() - 1).getDelayTicks();

        List<BlackjackAnimationStep> exactlyOnTime = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, naturalFinalStep);
        List<BlackjackAnimationStep> alreadyLate = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, naturalFinalStep - 5);

        assertSame(steps, exactlyOnTime, "arriving exactly on time needs no shift -- the original list must be reused, not copied");
        assertSame(steps, alreadyLate, "arriving later than required needs no shift either");
    }

    @Test
    void coordinationKeepsEveryStepMonotonicallyNondecreasing() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, 500L);

        for (int i = 1; i < coordinated.size(); i++) {
            assertTrue(coordinated.get(i).getDelayTicks() >= coordinated.get(i - 1).getDelayTicks(),
                "coordinated step " + i + " must not be scheduled earlier than step " + (i - 1));
        }
    }

    @Test
    void totalDurationTicksMatchesTheActualFinalScheduledStep() {
        List<BlackjackAnimationStep> uncoordinated = BlackjackDealerInspectionPlan.build(BASE_TICKS);
        assertEquals(uncoordinated.get(uncoordinated.size() - 1).getDelayTicks(), BlackjackDealerInspectionPlan.totalDurationTicks(uncoordinated));

        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(uncoordinated, 200L);
        assertEquals(coordinated.get(coordinated.size() - 1).getDelayTicks(), BlackjackDealerInspectionPlan.totalDurationTicks(coordinated));
        assertTrue(BlackjackDealerInspectionPlan.totalDurationTicks(coordinated) > BlackjackDealerInspectionPlan.totalDurationTicks(uncoordinated),
            "a genuinely required shift must be reflected in the reported total duration");
    }

    @Test
    void totalDurationTicksIsZeroForAnEmptyPath() {
        assertEquals(0L, BlackjackDealerInspectionPlan.totalDurationTicks(List.of()));
    }
}
