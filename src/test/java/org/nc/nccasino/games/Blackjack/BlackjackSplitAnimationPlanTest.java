package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackSplitAnimationPlanTest {

    private static final long STEP_TICKS = 10L;

    @Test
    void stepsAppearInSlideDealDealParkReactivateOrder() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(11, 12, 13, 9, 11, STEP_TICKS);

        assertEquals(5, steps.size());
        assertEquals(BlackjackAnimationStep.Kind.SLIDE_OUT, steps.get(0).getKind());
        assertEquals(BlackjackAnimationStep.Kind.DEAL, steps.get(1).getKind());
        assertEquals(BlackjackAnimationStep.Kind.DEAL, steps.get(2).getKind());
        assertEquals(BlackjackAnimationStep.Kind.PARK, steps.get(3).getKind());
        assertEquals(BlackjackAnimationStep.Kind.REACTIVATE, steps.get(4).getKind());
    }

    @Test
    void eachStepTargetsTheSlotItWasGivenInOrder() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(11, 12, 13, 9, 11, STEP_TICKS);

        assertEquals(11, steps.get(0).getSlot()); // splitCardFromSlot
        assertEquals(12, steps.get(1).getSlot()); // firstHandReplacementSlot
        assertEquals(13, steps.get(2).getSlot()); // secondHandReplacementSlot
        assertEquals(9, steps.get(3).getSlot()); // pendingHandParkSlot
        assertEquals(11, steps.get(4).getSlot()); // nextHandReactivateSlot
    }

    @Test
    void delaysAreStrictlyIncreasingByOneStepEach() {
        List<BlackjackAnimationStep> steps = BlackjackSplitAnimationPlan.build(11, 12, 13, 9, 11, STEP_TICKS);
        for (int i = 0; i < steps.size(); i++) {
            assertEquals(i * STEP_TICKS, steps.get(i).getDelayTicks());
        }
    }
}
