package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-geometry coverage for the split's original-hand replacement card
 * (C)'s deck-flight path -- it detours through the row below the target
 * seat (toward the deck) to avoid cutting through B, already resting at
 * its own temp slot in the target row. See
 * BlackjackInventory#splitOriginalCardFlightPath.
 */
class BlackjackSplitCardFlightPathTest {

    private static List<Card> twoSeatDeck() {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // seat0 card0
        stack.add(new Card(Suit.CLUBS, Rank.SEVEN));    // seat1 card0
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer up
        stack.add(new Card(Suit.SPADES, Rank.TWO));     // seat0 card1
        stack.add(new Card(Suit.CLUBS, Rank.EIGHT));    // seat1 card1
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer hole
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        return stack;
    }

    /** Seats seat0 alone (seat1 left empty) and deals a round in, settling the deck token at its resting slot. */
    private static BlackjackControllerTestSupport.Harness newTableSeat0Only() {
        BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness();
        h.inventory.stackDeckForTest(twoSeatDeck());
        h.currencyProvider.setBalance(1000);
        Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
        h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
        h.inventory.commitWagerForTest(alice, 10.0);
        h.inventory.beginStartTransitionForTest();
        for (int i = 0; i < 300 && h.inventory.activeHandCardCountForTest(alice.getUniqueId()) < 2; i++) {
            h.scheduler.advance(1);
        }
        return h;
    }

    /** Seats both seat0 and seat1 and deals a round in, so seat1's row (seat0's own split detour row) holds real cards. */
    private static BlackjackControllerTestSupport.Harness newTableBothSeated() {
        BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness();
        h.inventory.stackDeckForTest(twoSeatDeck());
        h.currencyProvider.setBalance(1000);
        Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
        h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
        h.inventory.commitWagerForTest(alice, 10.0);
        Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
        h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
        h.inventory.commitWagerForTest(bob, 10.0);
        h.inventory.beginStartTransitionForTest();
        for (int i = 0; i < 300 && h.inventory.activeHandCardCountForTest(bob.getUniqueId()) < 2; i++) {
            h.scheduler.advance(1);
        }
        return h;
    }

    @Test
    void detourRowClearGoesStraightAcrossThenUpIntoTheTarget() {
        try (BlackjackControllerTestSupport.Harness h = newTableSeat0Only()) {
            int targetSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[0], 1); // seat 0's row, index 1 -- C's slot
            List<Integer> path = h.inventory.splitOriginalCardFlightPathForTest(targetSlot);

            assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, path.get(0));
            assertEquals(targetSlot, path.get(path.size() - 1));

            int detourRow = 1; // seat 0's row (0) + 1
            int deckCol = BlackjackSlotLayout.DECK_HOME_SLOT % BlackjackSlotLayout.SEAT_ROW_WIDTH;
            int targetCol = targetSlot % BlackjackSlotLayout.SEAT_ROW_WIDTH;

            // Clear detour row -> the sweep reaches the target's own column
            // before hopping up, so the second-to-last step is the detour
            // row directly above the target column.
            int detourAtTargetCol = detourRow * BlackjackSlotLayout.SEAT_ROW_WIDTH + targetCol;
            assertEquals(detourAtTargetCol, path.get(path.size() - 2), "an unobstructed detour row must sweep all the way to the target's own column before hopping up");

            // And it must have genuinely traveled through the detour row (up into it right after the deck), never straight up the target's own row.
            int detourAtDeckCol = detourRow * BlackjackSlotLayout.SEAT_ROW_WIDTH + deckCol;
            assertEquals(detourAtDeckCol, path.get(1));
        }
    }

    @Test
    void detourRowOccupiedStopsBeforeItThenFinishesInsideTheTargetRow() {
        try (BlackjackControllerTestSupport.Harness h = newTableBothSeated()) {
            // Seat 1 -- row 1, exactly seat 0's own detour row -- now holds
            // a real two-card hand from the deal above, occupying columns 2 and 3.
            int targetSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[0], 1); // seat 0's row, index 1 -- C's slot, column 3
            List<Integer> path = h.inventory.splitOriginalCardFlightPathForTest(targetSlot);

            int blockedSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[1], 1); // detour row, column 3 -- occupied
            assertFalse(path.contains(blockedSlot), "the path must never step onto an occupied slot in the detour row");

            // It must still end at the real target, having hopped up into
            // the target row earlier (at column 4, one short of blocked
            // column 3) and finished the last stretch inside the target
            // row itself.
            assertEquals(targetSlot, path.get(path.size() - 1));
            int targetRow = 0;
            int earlyHopUp = targetRow * BlackjackSlotLayout.SEAT_ROW_WIDTH + 4;
            assertTrue(path.contains(earlyHopUp), "must hop up into the target row before reaching the blocked column");
        }
    }
}
