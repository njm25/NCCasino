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

    /** Every step of any path -- forward or return -- moves exactly one slot, along a single row or column, never diagonally. */
    private static void assertOnlyOrthogonalSingleSteps(List<Integer> path) {
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        for (int i = 1; i < path.size(); i++) {
            int prev = path.get(i - 1);
            int cur = path.get(i);
            int rowDelta = Math.abs(cur / width - prev / width);
            int colDelta = Math.abs(cur % width - prev % width);
            assertTrue((rowDelta == 1 && colDelta == 0) || (rowDelta == 0 && colDelta == 1),
                "step from " + prev + " to " + cur + " must move exactly one slot along a single row or column");
        }
    }

    @Test
    void playerReturnPathSlidesRightToTheDecksColumnThenDropsIntoTheDecksRow() {
        // Card at seat 0's own 3rd cell (row0,col4), deck resting at 44 (row4,col8).
        int cardSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[0], 2);
        List<Integer> path = BlackjackCardFlightPlan.returnToDeckPath(cardSlot, BlackjackSlotLayout.DECK_HOME_SLOT, false);
        assertEquals(cardSlot, path.get(0));
        assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, path.get(path.size() - 1));
        assertOnlyOrthogonalSingleSteps(path);
        // Every slot before the vertical drop begins stays in the card's own row (row0), sliding right.
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int deckCol = BlackjackSlotLayout.DECK_HOME_SLOT % width;
        boolean reachedDeckColumn = false;
        for (int slot : path) {
            if (slot % width == deckCol) {
                reachedDeckColumn = true;
                continue;
            }
            assertFalse(reachedDeckColumn, "must never leave the deck's own column once it's reached -- the horizontal leg must fully finish before the vertical one starts");
            assertEquals(cardSlot / width, slot / width, "must stay in the card's own row for the entire rightward leg");
        }
    }

    @Test
    void playerReturnPathFromTheBottomSeatIsPurelyHorizontal() {
        // The bottom seat already shares the deck's own row -- no vertical leg needed at all.
        int cardSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[4], 0);
        List<Integer> path = BlackjackCardFlightPlan.returnToDeckPath(cardSlot, BlackjackSlotLayout.DECK_HOME_SLOT, false);
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        for (int slot : path) {
            assertEquals(cardSlot / width, slot / width, "every slot must stay in the shared row");
        }
        assertOnlyOrthogonalSingleSteps(path);
    }

    @Test
    void dealerReturnPathNeverCrossesTheHeadsColumnExceptOnItsFinalSafeStep() {
        // A hit card way out at 47 (row5,col2), deck resting at 44 (row4,col8).
        List<Integer> path = BlackjackCardFlightPlan.returnToDeckPath(BlackjackSlotLayout.ACTION_HIT_SLOT, BlackjackSlotLayout.DECK_HOME_SLOT, true);
        assertEquals(BlackjackSlotLayout.ACTION_HIT_SLOT, path.get(0));
        assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, path.get(path.size() - 1));
        assertOnlyOrthogonalSingleSteps(path);

        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int deckCol = BlackjackSlotLayout.DECK_HOME_SLOT % width;
        // The head's own column (one past every dealer card slot) must never appear anywhere
        // except the path's own final slot (which sits a full row above the head).
        for (int i = 0; i < path.size() - 1; i++) {
            assertFalse(path.get(i) % width == deckCol, "must never cross the head's column before the final safe step: slot " + path.get(i));
        }
        // The rightward leg within the dealer's own row must stop one column short of the deck's.
        for (int slot : path) {
            if (slot / width == BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT / width) {
                assertTrue(slot % width <= deckCol - 1, "must never advance past the column just short of the deck's own while still in the dealer's row");
            }
        }
    }

    @Test
    void dealerReturnPathFromTheUpCardColumnGoesStraightUpThenOneStepRight() {
        // The up-card already sits in the "approach column" (one short of the deck's own) --
        // no rightward leg needed at all before rising.
        List<Integer> path = BlackjackCardFlightPlan.returnToDeckPath(BlackjackSlotLayout.DEALER_UP_CARD_SLOT, BlackjackSlotLayout.DECK_HOME_SLOT, true);
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int approachCol = BlackjackSlotLayout.DECK_HOME_SLOT % width - 1;
        assertEquals(BlackjackSlotLayout.DEALER_UP_CARD_SLOT % width, approachCol, "sanity: the up-card's own column is exactly the approach column");
        for (int i = 0; i < path.size() - 1; i++) {
            assertEquals(approachCol, path.get(i) % width, "every slot before the final step must stay in the approach column");
        }
        assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, path.get(path.size() - 1));
        assertOnlyOrthogonalSingleSteps(path);
    }
}
