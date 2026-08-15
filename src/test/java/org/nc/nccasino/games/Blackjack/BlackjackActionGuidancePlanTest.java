package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackActionGuidancePlanTest {

    private static final long ON_TICKS = 20L;

    @Test
    void guidesEveryAvailableActionSlotInOrder() {
        List<Integer> actionSlots = List.of(
            BlackjackSlotLayout.ACTION_HIT_SLOT, BlackjackSlotLayout.ACTION_STAND_SLOT, BlackjackSlotLayout.ACTION_DOUBLE_SLOT
        );
        List<BlackjackAnimationStep> steps = BlackjackActionGuidancePlan.build(actionSlots, ON_TICKS);

        assertEquals(6, steps.size());
        for (int i = 0; i < actionSlots.size(); i++) {
            BlackjackAnimationStep on = steps.get(i * 2);
            BlackjackAnimationStep off = steps.get(i * 2 + 1);
            assertEquals(actionSlots.get(i), on.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_ON, on.getKind());
            assertEquals(i * ON_TICKS, on.getDelayTicks());
            assertEquals(actionSlots.get(i), off.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_OFF, off.getKind());
            assertEquals(i * ON_TICKS + ON_TICKS, off.getDelayTicks());
        }
    }

    @Test
    void noAvailableActionsProducesAnEmptyPlan() {
        assertEquals(List.of(), BlackjackActionGuidancePlan.build(List.of(), ON_TICKS));
        assertEquals(0L, BlackjackActionGuidancePlan.cycleDurationTicks(0, ON_TICKS));
    }

    @Test
    void cycleDurationMatchesTheActionCount() {
        assertEquals(3 * ON_TICKS, BlackjackActionGuidancePlan.cycleDurationTicks(3, ON_TICKS));
    }

    @Test
    void singleActionStillProducesAnOnOffPair() {
        List<BlackjackAnimationStep> steps = BlackjackActionGuidancePlan.build(List.of(BlackjackSlotLayout.ACTION_STAND_SLOT), ON_TICKS);
        assertEquals(2, steps.size());
        assertEquals(BlackjackSlotLayout.ACTION_STAND_SLOT, steps.get(0).getSlot());
        assertEquals(BlackjackSlotLayout.ACTION_STAND_SLOT, steps.get(1).getSlot());
    }
}
