package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.session.ExitReason;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level coverage for a hand that hits enough times to fill its
 * entire row (see {@link BlackjackSlotLayout#SEAT_CARD_CAPACITY}): once the
 * row is already showing the maximum, a further Hit slides the whole
 * visible window one card left (the leftmost visible card disappears,
 * every other one shifts down an index) before the new card deals in at
 * the row's own rightmost slot -- rather than the row silently falling
 * mute past the 7th card (see {@code BlackjackInventory#handleHit}) or the
 * math bleeding into whatever's below (see {@code isRenderableCardSlot}).
 * The canonical hand data itself is never windowed -- only the row's own
 * rendering is.
 */
class BlackjackHandOverflowWindowIntegrationTest {

    private static final int SEAT_SLOT = BlackjackSlotLayout.SEAT_SLOTS[0];

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    private static ItemStack item(BlackjackControllerTestSupport.Harness h, Player viewer, int slot) {
        return h.inventory.getOrCreateView(viewer).getItem(slot);
    }

    /**
     * A must-not-bust rank per hit (all 2s) except a distinctly-ranked
     * second card and eighth card, so the shift can be pinned down by
     * amount alone: after 5 hits the row is exactly full (2 initial + 5
     * hits = 7); a 6th hit is the one that must trigger the shift.
     */
    @Test
    void handThatFillsItsRowShiftsLeftInsteadOfFallingMuteOrBleedingIntoTheRowBelow() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.TWO));    // A (index 0) -- must vanish once the row overflows
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.THREE));   // B (index 1) -- distinctly ranked, must end up at slot 0 after the shift
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole
            stack.addAll(flatStack(Rank.TWO, 5));          // hits 1-5 (indices 2-6) -- fills the row to exactly capacity (7)
            stack.add(new Card(Suit.DIAMONDS, Rank.FOUR)); // hit 6 (the overflow hit) -- distinctly ranked, must land at the row's own rightmost slot
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, SEAT_SLOT);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            int slot0 = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
            int slot6 = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, BlackjackSlotLayout.SEAT_CARD_CAPACITY - 1);

            // First hit is still the initial two-card decision (Hit at its
            // plain 47); every hit after that leaves only Hit/Stand
            // available, centered at 48/49 (see BlackjackActionLayout's
            // own centering doc), so Hit itself moves to 48 from the
            // second hit onward.
            for (int i = 0; i < 5; i++) {
                int hitSlot = i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT;
                h.click(alice, hitSlot);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            assertEquals(7, h.inventory.activeHandCardCountForTest(alice.getUniqueId()), "test setup: the row must be exactly full (2 initial + 5 hits)");
            assertEquals(2, item(h, alice, slot0).getAmount(), "test setup: slot 0 must still show the hand's own original first card (rank 2)");

            // The 6th hit overflows the row -- this is the one under test.
            // The instant the click lands, the shift itself is synchronous
            // (see handleHit's own doc) -- the rightmost slot must be
            // genuinely empty right here, not still showing the card that
            // just "moved" out of it, since the new card's own flight
            // hasn't even started yet.
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, item(h, alice, slot6).getType(),
                "the rightmost slot must be empty right after the shift, before the new card's own flight has had any chance to land");

            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);

            assertEquals(8, h.inventory.activeHandCardCountForTest(alice.getUniqueId()), "the canonical hand must still hold every card ever dealt, never windowed itself");
            assertEquals(3, item(h, alice, slot0).getAmount(), "slot 0 must now show what used to be at slot 1 (rank 3) -- the true original first card (rank 2) must be gone, not silently kept");
            assertEquals(4, item(h, alice, slot6).getAmount(), "the new (6th) hit card must land at the row's own rightmost slot, not bleed past it");

            // No slot in the row is ever left blank by the shift itself --
            // every one of the 7 visible cells still shows a real card.
            for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                ItemStack slotItem = item(h, alice, BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, i));
                assertTrue(slotItem.getType() == Material.BLACK_STAINED_GLASS_PANE || slotItem.getType() == Material.RED_STAINED_GLASS_PANE,
                    "slot " + i + " must still show a real card after the shift, was " + slotItem.getType());
            }

            // Nothing spilled into the (unoccupied, still plain felt) row
            // belonging to the next seat down.
            int seatOneFirstCardSlot = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[1], 0);
            ItemStack belowRow = item(h, alice, seatOneFirstCardSlot);
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, belowRow.getType(), "the shift must never bleed a card into the unoccupied seat's row below");
        }
    }

    @Test
    void leavingDuringAnOverflowHitFlightClearsTheShiftAndNeverDealsTheReservedCard() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 80));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, SEAT_SLOT);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            for (int i = 0; i < 5; i++) {
                h.click(alice, i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT); // overflow shift + reserved flight
            h.scheduler.advance(1);
            h.inventory.onSessionTerminated(alice.getUniqueId(), ExitReason.KICKED);

            for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                assertEquals(Material.GREEN_STAINED_GLASS_PANE,
                    item(h, alice, BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, i)).getType());
            }
            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory reopened = h.inventory.getOrCreateView(spectator);
            for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                int slot = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, i);
                assertEquals(item(h, alice, slot).getType(), reopened.getItem(slot).getType());
                assertEquals(item(h, alice, slot).getAmount(), reopened.getItem(slot).getAmount());
            }

            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS + 100);
            assertTrue(h.inventory.playerHandsForTest(alice.getUniqueId()).isEmpty());
            assertTrue(!h.inventory.isSeatedForTest(alice.getUniqueId()));
        }
    }
}
