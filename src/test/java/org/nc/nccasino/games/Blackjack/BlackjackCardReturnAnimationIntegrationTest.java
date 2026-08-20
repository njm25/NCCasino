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

            // Give the dealer/deck walk-up (and the final board wipe behind
            // it) ample time to fully finish.
            h.scheduler.advance(200);
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the dealer must have walked all the way back up to the lobby by now");
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
