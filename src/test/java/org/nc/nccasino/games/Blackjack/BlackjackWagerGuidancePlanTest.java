package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;

class BlackjackWagerGuidancePlanTest {

    private static final long ON_TICKS = 20L;

    @Test
    void guidesEveryChipSlotLeftToRight() {
        List<BlackjackAnimationStep> steps = BlackjackWagerGuidancePlan.build(ON_TICKS);
        int chipCount = ChipSlots.LAST_SLOT - ChipSlots.FIRST_SLOT + 1;

        assertEquals(chipCount * 2, steps.size());
        for (int i = 0; i < chipCount; i++) {
            int slot = ChipSlots.FIRST_SLOT + i;
            BlackjackAnimationStep on = steps.get(i * 2);
            BlackjackAnimationStep off = steps.get(i * 2 + 1);
            assertEquals(slot, on.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_ON, on.getKind());
            assertEquals(i * ON_TICKS, on.getDelayTicks());
            assertEquals(slot, off.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_OFF, off.getKind());
            assertEquals(i * ON_TICKS + ON_TICKS, off.getDelayTicks());
        }
    }

    @Test
    void cycleDurationMatchesTheChipSlotCount() {
        int chipCount = ChipSlots.LAST_SLOT - ChipSlots.FIRST_SLOT + 1;
        assertEquals((long) chipCount * ON_TICKS, BlackjackWagerGuidancePlan.cycleDurationTicks(ON_TICKS));
    }
}
