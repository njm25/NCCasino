package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression coverage for a controller-level bug: once the bottom seat's
 * hand grows all the way out to the deck's own resting slot (44 --
 * {@link BlackjackSlotLayout#DECK_HOME_SLOT}), the deck token is gone for
 * the rest of the round (see {@code dealCardToPlayer}'s {@code
 * dealerDeckTokenSlot = -1} reset). A dealer card dealt after that point
 * must never fly from (and repaint over) that now-real-card slot, or
 * anything it would corrupt downstream (the dealer head, its own
 * already-dealt cards) -- it must instead originate from the door-adjacent
 * fallback (see {@code BlackjackInventory#flightPathFromDeck} and
 * {@code BlackjackCardFlightPlan#dealerDoorPath}).
 */
class BlackjackDealerHitDeckAnimationIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void dealerHitAfterBottomRowFillsNeverCorruptsTheBoard() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Every card is a TWO: the bottom seat's hand climbs +2 per hit
            // (never busts, never hits 21) and the dealer's own hand climbs
            // the same way, needing several hits of its own to pass 17.
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 60));
            h.currencyProvider.setBalance(1000);

            // Seat the bottom seat (index 4) alone -- its row (36-44) is the
            // one that shares columns with the deck's own resting slot (44).
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[4]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 60);
            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest());

            // Hit 5 times: 2 initial cards + 5 hits = 7 cards, filling every
            // visible cell in the bottom row (38..44), including 44 itself
            // -- the deck's own resting slot.
            for (int i = 0; i < 5; i++) {
                h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            assertEquals(7, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));
            ItemStack bottomRowLastCard = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DECK_HOME_SLOT);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, bottomRowLastCard.getType(), "setup: slot 44 must now hold a real (spade = black) card");

            // Stand -- the only seated player, so this immediately starts
            // the dealer's own turn (dealer at 2+2=4, needs several hits).
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);

            // Advance well past the dealer's first several hits (each ~20
            // ticks apart, reveal ~20 more before the first) but nowhere
            // near the full climb to 17+/settlement/reset, so the board is
            // still mid-hit-sequence when asserted below.
            for (int i = 0; i < 15; i++) {
                h.scheduler.advance(10);
            }

            // The bottom row's real card at the deck's own slot must still
            // be there, untouched by any later dealer flight.
            assertEquals(Material.BLACK_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DECK_HOME_SLOT).getType(),
                "the bottom seat's own last card must never be repainted over by a dealer card's flight");

            // The dealer head must still be a head, never wiped to background.
            Material dealerHeadType = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT).getType();
            assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, dealerHeadType, "the dealer head must never be erased to background by a dealer card's flight");

            // The dealer's original up-card and hole-card, and its own
            // newly-hit cards in the renderable range (47-50), must all
            // still be real cards -- none of them silently wiped to
            // background by a corrupted flight path.
            for (int slot = BlackjackSlotLayout.ACTION_HIT_SLOT; slot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT; slot++) {
                Material type = h.inventory.getOrCreateView(alice).getItem(slot).getType();
                assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, type, "dealer card slot " + slot + " must never be wiped to background mid-sequence");
            }
        }
    }

    /**
     * Distinct from the above: here the deck's own resting slot (44) is
     * still free -- only an intermediate slot along the row-first sweep is
     * blocked. A vertical-first detour out of the deck's own column would
     * land on the dealer's own head slot (53, directly below 44) before it
     * could slide sideways, erasing the head -- so this case must also
     * route through the door fallback, never a vertical drop.
     */
    @Test
    void dealerHitBlockedMidRowByOnlyAPartialBottomRowNeverErasesTheDealerHead() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 60));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[4]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 60);
            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest());

            // Only 3 hits: 2 initial + 3 = 5 cards, filling slots 38-42 and
            // leaving 43 and 44 (the deck's own slot) free -- the dealer's
            // first hit (target 50, column 5) sweeps through columns 7, 6,
            // 5 (slots 43, 42, 41); 42 and 41 are occupied, blocking the
            // sweep well before the deck's own slot itself is ever touched.
            for (int i = 0; i < 3; i++) {
                h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            assertEquals(5, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));
            Material deckSlotType = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DECK_HOME_SLOT).getType();
            assertNotEquals(Material.BLACK_STAINED_GLASS_PANE, deckSlotType, "setup: the deck's own resting slot (44) must still show the deck icon, not a real card");
            assertNotEquals(Material.RED_STAINED_GLASS_PANE, deckSlotType, "setup: the deck's own resting slot (44) must still show the deck icon, not a real card");

            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);

            // Advance well past the dealer's first several hits.
            for (int i = 0; i < 15; i++) {
                h.scheduler.advance(10);
            }

            Material dealerHeadType = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT).getType();
            assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, dealerHeadType, "a vertical-first detour out of the deck's column would land on (and erase) the dealer head -- must never happen");

            for (int slot = BlackjackSlotLayout.ACTION_HIT_SLOT; slot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT; slot++) {
                Material type = h.inventory.getOrCreateView(alice).getItem(slot).getType();
                assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, type, "dealer card slot " + slot + " must never be wiped to background by the detour");
            }

            // And the bottom seat's own real cards (blocking the sweep) must be untouched too.
            assertEquals(5, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));
        }
    }
}
