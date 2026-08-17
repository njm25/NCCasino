package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackActionGuidancePlanTest {

    @Test
    void guidesEveryAvailableActionSlotSimultaneously() {
        List<Integer> actionSlots = List.of(
            BlackjackSlotLayout.ACTION_HIT_SLOT, BlackjackSlotLayout.ACTION_STAND_SLOT, BlackjackSlotLayout.ACTION_DOUBLE_SLOT
        );
        assertEquals(actionSlots, BlackjackActionGuidancePlan.applicableSlots(actionSlots));
    }

    @Test
    void noAvailableActionsProducesAnEmptySet() {
        assertEquals(List.of(), BlackjackActionGuidancePlan.applicableSlots(List.of()));
    }

    @Test
    void singleActionStillProducesAOneItemSet() {
        List<Integer> slots = BlackjackActionGuidancePlan.applicableSlots(List.of(BlackjackSlotLayout.ACTION_STAND_SLOT));
        assertEquals(1, slots.size());
        assertEquals(BlackjackSlotLayout.ACTION_STAND_SLOT, slots.get(0));
    }
}
