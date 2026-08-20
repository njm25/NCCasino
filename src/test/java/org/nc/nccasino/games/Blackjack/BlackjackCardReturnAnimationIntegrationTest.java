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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the round-end animation: every
 * visible card slides back along its own reversed deal-in path showing its
 * real face the whole way (never flipping to a hidden placeholder), while
 * the dealer head and deck token stay exactly where they've sat all round
 * until every card has actually landed -- only then do the dealer and deck
 * walk back up to the lobby together, and only once <em>that</em> finishes
 * does the board actually wipe to its fresh pregame state.
 */
class BlackjackCardReturnAnimationIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.HEARTS, rank));
        }
        return cards;
    }

    private static ItemStack item(BlackjackControllerTestSupport.Harness h, Player viewer, int slot) {
        return h.inventory.getOrCreateView(viewer).getItem(slot);
    }

    private static Material typeOf(ItemStack item) {
        return item == null ? null : item.getType();
    }

    @Test
    void aDealtHandsCardsShowTheirRealFaceThroughReturnThenClearBeforeTheDealerWalksUp() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, seatSlot);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            assertEquals(2, h.inventory.activeHandCardCountForTest(aliceId), "test setup must actually deal alice in");
            int firstCardSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, 0);
            ItemStack beforeReset = item(h, alice, firstCardSlot);
            assertEquals(Material.RED_STAINED_GLASS_PANE, beforeReset.getType(), "sanity: a real (7 of hearts, rendered red by suit) card is there before reset");
            assertEquals(7, beforeReset.getAmount());
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(), "sanity: the dealer is genuinely in play before reset");

            h.inventory.resetGameForTest();

            // Immediately after reset: the card must still be showing its
            // own real face -- exactly as it was before reset, never
            // flipped to a hidden placeholder, never instantly wiped. The
            // dealer head must not have moved an inch yet either.
            ItemStack immediatelyAfterReset = item(h, alice, firstCardSlot);
            assertEquals(Material.RED_STAINED_GLASS_PANE, immediatelyAfterReset.getType(), "the card must keep showing its real face, never flip to a hidden placeholder");
            assertEquals(7, immediatelyAfterReset.getAmount());
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(), "the dealer must not move until every card has actually returned to the deck");
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId), "the canonical hand is still cleared immediately, same as before -- only the rendering is deferred");

            // Tick forward one at a time until the card's own flight lands,
            // checking the dealer hasn't budged at every single tick along
            // the way -- rather than guessing a fixed window, since the two
            // phases' own durations aren't independent of each other (the
            // dealer's walk-up phase begins the instant the card phase's
            // onComplete fires, same tick).
            boolean cardCleared = false;
            for (int tick = 0; tick < 100; tick++) {
                h.scheduler.advance(1);
                assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                    "the dealer must not move a single tick before the card has actually landed");
                if (typeOf(item(h, alice, firstCardSlot)) == Material.GREEN_STAINED_GLASS_PANE) {
                    cardCleared = true;
                    break;
                }
            }
            assertTrue(cardCleared, "the card's own reversed flight must land (and clear to background) within a reasonable number of ticks");

            // Tick through the dealer/deck walk-up one at a time: the deck
            // token must hug the row directly ABOVE the dealer's current
            // slot at every single step -- never trail one row below it,
            // which is what reusing the down-slide's own "trail into the
            // vacated slot" logic unmodified would do once the direction is
            // reversed.
            for (int tick = 0; tick < 100 && h.inventory.dealerHeadSlotForTest() != BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT; tick++) {
                h.scheduler.advance(1);
                int deckTokenSlot = h.inventory.dealerDeckTokenSlotForTest();
                if (deckTokenSlot != -1) {
                    assertEquals(h.inventory.dealerHeadSlotForTest() - BlackjackSlotLayout.SEAT_ROW_WIDTH, deckTokenSlot,
                        "the deck token must stay exactly one row above the dealer's current slot while walking up, never below it");
                }
            }
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the dealer must have walked all the way back up to the lobby by now");

            // Ample extra time for the final board wipe behind it to finish too.
            h.scheduler.advance(20);
        }
    }

    /**
     * Two seated hands' cards all share the deck's own column for the
     * vertical leg of their return flight (see {@code
     * animateCardsReturnToDeck}'s own doc) -- real collisions, not just a
     * near-miss, are expected here. This doesn't assert the exact winner of
     * any one collision (that's the collision-resolution algorithm's own
     * business), just that the board still comes out clean on the other
     * side: no exception, every seat's row fully cleared, dealer back at
     * the lobby -- i.e. a merged-away card never gets stuck rendering a
     * ghost icon forever, and the whole chain still actually completes.
     */
    @Test
    void multipleHandsWithColliderReturnPathsStillEndInACleanBoard() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // alice card 1
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // bob card 1
            stack.add(new Card(Suit.HEARTS, Rank.NINE));   // dealer up
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // alice card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // bob card 2
            stack.add(new Card(Suit.HEARTS, Rank.NINE));   // dealer hole
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            UUID bobId = UUID.randomUUID();
            Player bob = h.seatOnlinePlayer(bobId, "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[3]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);
            assertEquals(2, h.inventory.activeHandCardCountForTest(aliceId), "test setup must actually deal alice in");
            assertEquals(2, h.inventory.activeHandCardCountForTest(bobId), "test setup must actually deal bob in");

            h.inventory.resetGameForTest();
            h.scheduler.advance(400); // comfortably past the full round-end animation chain

            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the whole chain must still reach the lobby even with colliding return paths");
            for (int seatSlot : new int[] {BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[3]}) {
                for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                    int slot = BlackjackSlotLayout.playerCardSlot(seatSlot, i);
                    if (slot == BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT) {
                        continue; // seat 0's own 7th card cell coincides with the dealer's own lobby head slot by table geometry -- the dealer legitimately rests there once the board settles, that's not a leftover card
                    }
                    Material type = typeOf(item(h, alice, slot));
                    assertEquals(Material.GREEN_STAINED_GLASS_PANE, type,
                        "every card cell must end up fully cleared, not stuck showing a merged-away card's ghost icon");
                }
            }
        }
    }

    @Test
    void resetWithNoCardsEverDealtSkipsStraightToTheFinalBoardWipe() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]); // seated, never commits, never dealt in

            h.inventory.resetGameForTest();

            // With nothing to animate back into the deck, and the dealer
            // never having genuinely entered play (still at the lobby
            // slot), both animation phases must be a same-tick no-op --
            // the board settles immediately, no leftover card-return or
            // dealer-walk-up delay tacked on.
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId));
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
        }
    }
}
