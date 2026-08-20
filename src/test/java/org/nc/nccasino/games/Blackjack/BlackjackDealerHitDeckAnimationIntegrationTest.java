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
            // -- the deck's own resting slot. The very first hit is still
            // the initial two-card decision (Double/Split may also be
            // offered, so Hit stays at its plain 47), but every hit after
            // that leaves only Hit/Stand available -- which then render
            // shifted one slot right, centered at 48/49 (see
            // BlackjackActionLayout's own centering doc), so Hit itself
            // moves to 48 from the second hit onward.
            for (int i = 0; i < 5; i++) {
                int hitSlot = i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT;
                h.click(alice, hitSlot);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            assertEquals(7, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));
            ItemStack bottomRowLastCard = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DECK_HOME_SLOT);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, bottomRowLastCard.getType(), "setup: slot 44 must now hold a real (spade = black) card");

            // Stand -- the only seated player, so this immediately starts
            // the dealer's own turn (dealer at 2+2=4, needs several hits).
            // Only Hit/Stand are available by now, so Stand itself has
            // shifted one slot right too, to 49.
            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);

            // Advance until the dealer's own hand has fully settled (all
            // TWOs, starting at 4, needs 7 hits to reach 18 and stop) but
            // stop short of settlement/round-end actually beginning --
            // this table needs 190-200 ticks for that, well clear of
            // both boundaries. Along the way, the dealer's own row
            // legitimately fills past its 6-slot capacity (up, hole, +4
            // more) and shifts (see
            // BlackjackInventory#nextDealerCardSlotWithOverflowShift) --
            // slot 47 specifically can be transiently background between
            // an overflow shift and its own next card landing, so this
            // asserts the fully-settled state, not every tick along the way.
            for (int i = 0; i < 21; i++) {
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

            // Every dealer card slot, now that the whole sequence has
            // settled, must show a real card -- none of them silently
            // stuck on background by a corrupted flight path.
            for (int slot = BlackjackSlotLayout.ACTION_HIT_SLOT; slot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT; slot++) {
                Material type = h.inventory.getOrCreateView(alice).getItem(slot).getType();
                assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, type, "dealer card slot " + slot + " must show a real card once the sequence has settled");
            }

            // Every one of these door-path flights departs from the
            // turn-timer/edge-glass slot itself (46) -- it must be restored
            // to the idle brown edge glass afterward, not left cleared to
            // plain background forever (see scheduleCardFlightHops's own
            // TURN_TIMER_SLOT special case).
            assertEquals(Material.BROWN_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.TURN_TIMER_SLOT).getType(),
                "the door-adjacent turn-timer slot must be restored to the idle brown edge glass after a dealer door-path flight departs from it");
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
            // The first hit is still the initial two-card decision (Hit at
            // its plain 47); every hit after that leaves only Hit/Stand
            // available, shifted one slot right and centered at 48/49 (see
            // BlackjackActionLayout's own centering doc), so Hit itself
            // moves to 48 from the second hit onward.
            for (int i = 0; i < 3; i++) {
                int hitSlot = i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT;
                h.click(alice, hitSlot);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            assertEquals(5, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));
            Material deckSlotType = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DECK_HOME_SLOT).getType();
            assertNotEquals(Material.BLACK_STAINED_GLASS_PANE, deckSlotType, "setup: the deck's own resting slot (44) must still show the deck icon, not a real card");
            assertNotEquals(Material.RED_STAINED_GLASS_PANE, deckSlotType, "setup: the deck's own resting slot (44) must still show the deck icon, not a real card");

            // Only Hit/Stand are available by now, so Stand itself has
            // shifted one slot right too, to 49.
            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);

            // Advance until the dealer's own hand has fully settled, same
            // budget (and same reason) as the sibling test above.
            for (int i = 0; i < 21; i++) {
                h.scheduler.advance(10);
            }

            Material dealerHeadType = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT).getType();
            assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, dealerHeadType, "a vertical-first detour out of the deck's column would land on (and erase) the dealer head -- must never happen");

            for (int slot = BlackjackSlotLayout.ACTION_HIT_SLOT; slot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT; slot++) {
                Material type = h.inventory.getOrCreateView(alice).getItem(slot).getType();
                assertNotEquals(Material.GREEN_STAINED_GLASS_PANE, type, "dealer card slot " + slot + " must show a real card once the sequence has settled, not be wiped by the detour");
            }

            // And the bottom seat's own real cards (blocking the sweep) must be untouched too.
            assertEquals(5, h.inventory.activeHandCardCountForTest(alice.getUniqueId()));

            assertEquals(Material.BROWN_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.TURN_TIMER_SLOT).getType(),
                "the door-adjacent turn-timer slot must be restored to the idle brown edge glass after a dealer door-path flight departs from it");
        }
    }
}
