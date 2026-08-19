package org.nc.nccasino.games.Blackjack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-geometry coverage for {@link BlackjackCardFlightPlan}. */
class BlackjackCardFlightPlanTest {

    @Test
    void dealerCardSlidesAlongDeckRowThenDropsDown() {
        // Deck at 44 (row4,col8), target 47 (row5,col2).
        List<Integer> path = BlackjackCardFlightPlan.path(44, 47, true);
        assertEquals(44, path.get(0));
        assertEquals(38, path.get(path.size() - 2)); // last row-4 slot before dropping
        assertEquals(47, path.get(path.size() - 1));
        assertFalse(path.contains(53)); // never wanders through the dealer head slot
    }

    @Test
    void dealerRowLegClearIsTrueWhenNothingIsOccupied() {
        assertTrue(BlackjackCardFlightPlan.dealerRowLegClear(44, 47, slot -> false));
    }

    @Test
    void dealerRowLegClearIsFalseWhenAnIntermediateSlotIsOccupied() {
        // Row-first sweep from 44 to 47 passes through 43,42,41,40,39,38 -- 40 is mid-sweep.
        assertFalse(BlackjackCardFlightPlan.dealerRowLegClear(44, 47, slot -> slot == 40));
    }

    @Test
    void dealerRowLegClearIsFalseWhenTheOriginItselfIsOccupied() {
        // The bottom seat's hand has grown all the way out to the deck's own slot.
        assertFalse(BlackjackCardFlightPlan.dealerRowLegClear(44, 47, slot -> slot == 44));
    }

    @Test
    void dealerDoorPathOriginatesRightOfTheDoorAndNeverTouchesTheDeckRowOrHeadSlot() {
        List<Integer> path = BlackjackCardFlightPlan.dealerDoorPath(BlackjackSlotLayout.DEALER_UP_CARD_SLOT);
        assertEquals(BlackjackSlotLayout.TURN_TIMER_SLOT, path.get(0));
        assertEquals(BlackjackSlotLayout.DEALER_UP_CARD_SLOT, path.get(path.size() - 1));
        for (int slot : path) {
            assertFalse(slot == BlackjackSlotLayout.DECK_HOME_SLOT, "must never touch the deck's own (possibly occupied) resting slot");
            assertFalse(slot == BlackjackSlotLayout.ACTIVE_EXIT_SLOT, "must never touch the door itself");
            assertFalse(slot == BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, "must never touch the dealer head slot");
        }
    }
}
