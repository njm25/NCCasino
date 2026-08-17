package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;

class BlackjackWagerGuidancePlanTest {

    @Test
    void guidesEveryChipSlotSimultaneously() {
        List<Integer> slots = BlackjackWagerGuidancePlan.applicableSlots();
        int chipCount = ChipSlots.LAST_SLOT - ChipSlots.FIRST_SLOT + 1;

        assertEquals(chipCount, slots.size());
        for (int i = 0; i < chipCount; i++) {
            assertEquals(ChipSlots.FIRST_SLOT + i, slots.get(i));
        }
    }

    @Test
    void allInIsNotPartOfTheGuidanceSet() {
        List<Integer> slots = BlackjackWagerGuidancePlan.applicableSlots();
        assertFalse(slots.contains(BlackjackSlotLayout.ALL_IN_SLOT), "the exact intended control set excludes All In");
    }
}
