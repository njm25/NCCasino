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

    /**
     * Reproduces a reported break: two seated hands each hit enough TWOs to
     * overflow their own row (see {@code BlackjackHandOverflowWindowIntegrationTest}),
     * then the round ends while both rows -- and potentially the dealer's
     * own overflowed row too -- are still full of real cards. Every one of
     * those cards funnels through the same handful of shared lanes on its
     * way back to the deck (see {@link BlackjackCardFlightPlan#returnToDeckPath}),
     * so this is the heaviest realistic collision load the round-end
     * animation ever sees. Asserts the same clean-board outcome as {@link
     * #multipleHandsWithColliderReturnPathsStillEndInACleanBoard} -- no
     * exception, no stuck ghost icon anywhere, dealer back at the lobby --
     * under that heavier load specifically.
     */
    @Test
    void twoOverflowingHandsStillCollectCleanlyAtRoundEnd() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 200));
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

            // Alice hits until her row overflows (2 initial + 5 hits = 7,
            // exactly capacity) then one more to actually trigger the shift.
            for (int i = 0; i < 6; i++) {
                int hitSlot = i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT;
                h.click(alice, hitSlot);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT); // Stand -- advances to bob's turn
            h.scheduler.advance(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            assertEquals(bobId, h.inventory.currentPlayerIdForTest(), "test setup: turn must have actually advanced to bob");

            for (int i = 0; i < 6; i++) {
                int hitSlot = i == 0 ? BlackjackSlotLayout.ACTION_HIT_SLOT : BlackjackSlotLayout.ACTION_STAND_SLOT;
                h.click(bob, hitSlot);
                h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            }
            h.click(bob, BlackjackSlotLayout.ACTION_DOUBLE_SLOT); // Stand -- starts the dealer's own turn

            assertTrue(h.inventory.activeHandCardCountForTest(aliceId) > BlackjackSlotLayout.SEAT_CARD_CAPACITY,
                "test setup: alice's own hand must have genuinely overflowed its row");
            assertTrue(h.inventory.activeHandCardCountForTest(bobId) > BlackjackSlotLayout.SEAT_CARD_CAPACITY,
                "test setup: bob's own hand must have genuinely overflowed its row too");

            // Let the dealer's own hit-until-17 sequence run for a while
            // (it'll overflow its own row too, on this all-TWOs stack),
            // then force the round to end right in the middle of it --
            // the actual reported scenario, cards from every row all still
            // visible at once when collection begins.
            h.scheduler.advance(150);
            h.inventory.resetGameForTest();
            h.scheduler.advance(500); // comfortably past the full round-end animation chain

            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the whole chain must still reach the lobby under the heaviest realistic collision load");
            for (int seatSlot : new int[] {BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[3]}) {
                for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                    int slot = BlackjackSlotLayout.playerCardSlot(seatSlot, i);
                    if (slot == BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT) {
                        continue; // see the sibling test's identical caveat
                    }
                    Material type = typeOf(item(h, alice, slot));
                    assertEquals(Material.GREEN_STAINED_GLASS_PANE, type,
                        "every card cell must end up fully cleared, not stuck showing a merged-away card's ghost icon");
                }
            }
            // The dealer's own card row (47-52) isn't checked here the same
            // way -- both players are still seated post-reset, so that
            // bottom row has already flipped back to the seated-wager-phase
            // layout (chip denominations, Undo All/Last) by the time the
            // chain settles, not the active-play dealer row anymore.
        }
    }

    /**
     * The bottom seat's own two starting cards sit in adjacent columns
     * (index 0 one hop behind index 1), sharing the same row as the deck
     * -- no vertical leg for either, both just sliding right. That means
     * index 0's own arrival at index 1's starting column and index 1's own
     * departure from it happen on the exact same tick: a normal Snake-style
     * hand-off, not a collision (see {@code animateCardsReturnToDeck}'s own
     * doc on why departures must always be cleared before arrivals are
     * drawn, in every tick's own batch, regardless of which card's hop
     * happened to be scheduled first). Asserts the arriving card is
     * actually visible at that exact tick, not wiped by the departing
     * card's own clear landing after it.
     */
    @Test
    void aFasterCardArrivingExactlyWhereASlowerCardJustVacatedIsNotWipedByTheDeparture() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // alice card 0 (index 0, one hop behind)
            stack.add(new Card(Suit.HEARTS, Rank.NINE));  // dealer up
            stack.add(new Card(Suit.HEARTS, Rank.EIGHT)); // alice card 1 (index 1, one hop ahead)
            stack.add(new Card(Suit.HEARTS, Rank.NINE));  // dealer hole
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            int bottomSeat = BlackjackSlotLayout.SEAT_SLOTS[4];
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, bottomSeat);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);
            assertEquals(2, h.inventory.activeHandCardCountForTest(aliceId), "test setup must actually deal alice in");

            int slot0 = BlackjackSlotLayout.playerCardSlot(bottomSeat, 0);
            int slot1 = BlackjackSlotLayout.playerCardSlot(bottomSeat, 1);

            h.inventory.resetGameForTest();

            long handoffTick = BlackjackTiming.RETURN_TO_DECK_START_PAUSE_TICKS + BlackjackTiming.RETURN_TO_DECK_HOP_TICKS;
            h.scheduler.advance(handoffTick);

            // Card 0 (7 of hearts) must have arrived at slot 1's own
            // starting column exactly as card 1 (8 of hearts) vacated it --
            // showing the 7, not background.
            ItemStack atHandoff = item(h, alice, slot1);
            assertEquals(Material.RED_STAINED_GLASS_PANE, atHandoff.getType(),
                "the arriving card must be visible at the hand-off tick, not wiped to background by the departing card's own clear");
            assertEquals(7, atHandoff.getAmount(), "the arriving card must specifically be the one that was behind (rank 7), not some other state");
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
