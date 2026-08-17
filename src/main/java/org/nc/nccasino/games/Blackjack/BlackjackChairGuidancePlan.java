package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The chair-guidance glow set for one phase: every currently empty/selectable
 * seat, in table order, skipping already-filled seats. The runtime alternates
 * rendering this whole set GLOW then PLAIN, {@link BlackjackTiming#CHAIR_GUIDANCE_STEP_TICKS}
 * apart, re-deriving the set fresh at each phase so a seat filling mid-loop
 * drops out of the very next phase -- never a sequential one-seat-at-a-time
 * sweep. Begins {@link BlackjackTiming#CHAIR_GUIDANCE_START_DELAY_TICKS}
 * after a viewer opens the table; this class only describes one phase's set.
 */
public final class BlackjackChairGuidancePlan {

    private BlackjackChairGuidancePlan() {
    }

    /** @param filledSeatSlots seat head slots currently occupied -- excluded entirely */
    public static List<Integer> applicableSlots(Set<Integer> filledSeatSlots) {
        List<Integer> slots = new ArrayList<>();
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            if (!filledSeatSlots.contains(seatSlot)) {
                slots.add(seatSlot);
            }
        }
        return slots;
    }
}
