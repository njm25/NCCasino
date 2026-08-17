package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlackjackChairGuidancePlanTest {

    @Test
    void emptyTableGuidesEverySeatSimultaneously() {
        List<Integer> slots = BlackjackChairGuidancePlan.applicableSlots(Set.of());

        assertEquals(BlackjackSlotLayout.SEAT_SLOTS.length, slots.size());
        for (int i = 0; i < BlackjackSlotLayout.SEAT_SLOTS.length; i++) {
            assertEquals(BlackjackSlotLayout.SEAT_SLOTS[i], slots.get(i), "table order preserved");
        }
    }

    @Test
    void filledSeatsAreExcludedFromTheSimultaneousSet() {
        int filled = BlackjackSlotLayout.SEAT_SLOTS[1];
        List<Integer> slots = BlackjackChairGuidancePlan.applicableSlots(Set.of(filled));

        assertEquals(BlackjackSlotLayout.SEAT_SLOTS.length - 1, slots.size());
        assertFalse(slots.contains(filled), "filled seat must never appear in the guidance set");
    }

    @Test
    void everySeatFilledProducesAnEmptySet() {
        Set<Integer> allFilled = Set.of(
            BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[1], BlackjackSlotLayout.SEAT_SLOTS[2],
            BlackjackSlotLayout.SEAT_SLOTS[3], BlackjackSlotLayout.SEAT_SLOTS[4]
        );
        assertEquals(List.of(), BlackjackChairGuidancePlan.applicableSlots(allFilled));
    }

    @Test
    void aSeatFilledBetweenCallsDropsOutOfTheNextDerivedSet() {
        // Simulates re-deriving the set fresh at each phase boundary: a seat
        // that was empty for one phase's set is excluded from the next call
        // once it's reported filled -- never baked into a stale plan.
        int seat0 = BlackjackSlotLayout.SEAT_SLOTS[0];
        List<Integer> firstPhase = BlackjackChairGuidancePlan.applicableSlots(Set.of());
        assertTrue(firstPhase.contains(seat0));

        List<Integer> nextPhase = BlackjackChairGuidancePlan.applicableSlots(Set.of(seat0));
        assertFalse(nextPhase.contains(seat0));
    }
}
