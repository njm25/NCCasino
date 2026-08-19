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

    /** Seats the bottom seat (index 4) alone -- its own row shares the deck's row, the one case with a dedicated path. */
    private static BlackjackControllerTestSupport.Harness newTableBottomSeatOnly() {
        BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness();
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // bottom seat card0
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer up
        stack.add(new Card(Suit.CLUBS, Rank.EIGHT));     // bottom seat card1
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer hole -- stays hidden throughout any split
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        h.inventory.stackDeckForTest(stack);
        h.currencyProvider.setBalance(1000);
        Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
        h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[4]);
        h.inventory.commitWagerForTest(alice, 10.0);
        h.inventory.beginStartTransitionForTest();
        for (int i = 0; i < 300 && h.inventory.activeHandCardCountForTest(alice.getUniqueId()) < 2; i++) {
            h.scheduler.advance(1);
        }
        return h;
    }

    /**
     * The bottom seat has no "row below" (it already shares the deck's
     * row), so C gets an entirely different visual strategy -- see
     * BlackjackInventory#bottomSeatSplitDashPath. Left along the bottom
     * seat's own row until directly above the column left of the hole
     * card, down one slot (now directly left of the hole card), left along
     * the dealer's row until it's in the target's own column, then parks --
     * waiting for B's slide-out before the final single hop up.
     *
     * <p>Explicitly pins down A's slot and B's own original slot are never
     * touched at any point in the row-4 leg -- the exact concern from a
     * live-tested regression where an earlier version of this path
     * wandered too far left.
     */
    @Test
    void bottomSeatDashPathNeverTouchesAOrBAndParksLeftOfTheHoleCard() {
        try (BlackjackControllerTestSupport.Harness h = newTableBottomSeatOnly()) {
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[4];
            int slotA = BlackjackSlotLayout.playerCardSlot(seatSlot, 0);
            int targetSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, 1); // C's eventual slot -- B's original slot
            List<Integer> path = h.inventory.bottomSeatSplitDashPathForTest(targetSlot);

            assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, path.get(0));
            assertFalse(path.contains(targetSlot), "the dash only parks beneath the target -- the final hop up is a separate step");
            assertFalse(path.contains(slotA), "must never touch A's own slot");

            // Parks directly beneath the target, one row down, same column.
            int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
            int stagingSlot = path.get(path.size() - 1);
            assertEquals(targetSlot % width, stagingSlot % width, "must park in the same column as the target");
            assertEquals(targetSlot / width + 1, stagingSlot / width, "must park exactly one row below the target -- the dealer's row");

            // The drop point (row-4-to-row-5 transition) must be exactly
            // one column left of the hole card, never any farther left.
            int holeCardCol = BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT % width;
            int dealerRow = BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT / width;
            int lastRow4Slot = -1;
            for (int slot : path) {
                if (slot / width != dealerRow) {
                    lastRow4Slot = slot;
                }
            }
            assertEquals(holeCardCol - 1, lastRow4Slot % width, "must stop in row 4 exactly one column left of the hole card before dropping down");

            assertFalse(path.contains(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT), "must never touch the dealer's hole card");
            assertFalse(path.contains(BlackjackSlotLayout.DEALER_UP_CARD_SLOT), "must never touch the dealer's real up-card");
            assertFalse(path.contains(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT), "must never touch the dealer head slot");
        }
    }
}
