package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

import org.nc.nccasino.currency.ChipSlots;

/**
 * The wager-guidance glow set for one phase: every chip-denomination slot,
 * left to right. Mirrors {@link BlackjackChairGuidancePlan}'s shape -- the
 * runtime alternates rendering this whole set GLOW then PLAIN,
 * {@link BlackjackTiming#WAGER_GUIDANCE_STEP_TICKS} apart, until the viewer
 * selects a chip/all-in or closes their inventory.
 */
public final class BlackjackWagerGuidancePlan {

    private BlackjackWagerGuidancePlan() {
    }

    public static List<Integer> applicableSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
            slots.add(slot);
        }
        return slots;
    }
}
