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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level coverage for the hand-to-hand transition animation (see
 * {@code BlackjackInventory#activateSplitHand}/{@code
 * #runHandTransitionCollapse}/{@code #runHandTransitionReveal}): once a
 * split hand finishes and the queue advances to the next one, the finished
 * hand collapses down to just its first and last card, then the newly
 * active hand slides out from under it and back over it -- rather than the
 * row jump-cutting straight from one hand's cards to the other's.
 */
class BlackjackHandTransitionAnimationIntegrationTest {

    private static final Material BACKGROUND = Material.GREEN_STAINED_GLASS_PANE;
    private static final Material BLACK_CARD = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material RED_CARD = Material.RED_STAINED_GLASS_PANE;

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

    private static void advanceUntil(BlackjackControllerTestSupport.Harness h, java.util.function.BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks && !condition.getAsBoolean(); i++) {
            h.scheduler.advance(1);
        }
        assertTrue(condition.getAsBoolean(), "condition never became true within " + maxTicks + " ticks");
    }

    /**
     * Hand 1 only ever had two cards (no collapse needed) -- the transition
     * should go straight to hand 2's own out-and-back reveal, correctly
     * landing hand 2's real cards and leaving nothing of hand 1 behind.
     */
    @Test
    void handWithOnlyTwoCardsSkipsTheCollapseAndRevealsTheNextHandCorrectly() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));  // A
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));   // B
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));  // C (hand 1's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.NINE)); // D (hand 2's replacement)
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, SEAT_SLOT);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 20);
            h.advanceToActionableTurn(1, 300);

            // Stand on hand 1 (8/2 = 10) immediately -- it never grows past its original two cards.
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);

            int slotA = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
            int slotB = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 1);
            advanceUntil(h, () -> item(h, alice, slotA).getAmount() == 8
                && item(h, alice, slotB).getType() == RED_CARD && item(h, alice, slotB).getAmount() == 9, 300);

            ItemStack card0 = item(h, alice, slotA);
            assertEquals(BLACK_CARD, card0.getType());
            assertEquals(8, card0.getAmount(), "hand 2's own first card (8 of clubs)");
            ItemStack card1 = item(h, alice, slotB);
            assertEquals(RED_CARD, card1.getType());
            assertEquals(9, card1.getAmount(), "hand 2's own second card (9 of diamonds)");
            assertEquals(BACKGROUND, item(h, alice, BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 2)).getType());

            List<BlackjackHand> hands = h.inventory.playerHandsForTest(alice.getUniqueId());
            assertEquals(1, h.inventory.activeHandIndexForTest(alice.getUniqueId()), "hand 2 must now be the active hand");
            assertTrue(hands.get(0).isDone(), "hand 1 must be resolved");
            assertNotNull(item(h, alice, BlackjackSlotLayout.ACTION_STAND_SLOT), "hand 2 must now be actionable");
        }
    }

    /**
     * Hand 1 grew to three cards (a hit) before finishing -- it must
     * collapse to just its first (untouched) and last card before hand 2
     * reveals, not leave the middle card lingering or jump straight to
     * hand 2 with hand 1's full row still showing.
     */
    @Test
    void handWithExtraCardsCollapsesToFirstAndLastBeforeTheNextHandReveals() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.TWO));    // A
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.TWO));     // B
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));  // C (hand 1's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.NINE)); // D (hand 2's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.THREE)); // hand 1's own hit card
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, SEAT_SLOT);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 20);
            h.advanceToActionableTurn(1, 300);

            // Hit once on hand 1 (2/2 -> 2/2/3 = 7), then stand. Once past
            // this hand's own initial two-card decision, only Hit/Stand are
            // offered -- shifted one slot right and centered at 48/49 (see
            // BlackjackActionLayout's own centering doc), so Stand itself
            // now renders at 49, not 48.
            h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
            assertEquals(3, h.inventory.activeHandCardCountForTest(alice.getUniqueId()), "test setup: hand 1 must have three cards before it finishes");
            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);

            int slot0 = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
            int slot1 = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 1);
            int slot2 = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 2);

            // Mid-transition: hand 1 collapsed to exactly two cards -- its
            // own original first card (2 of spades) untouched, and its
            // last (3 of diamonds) now beside it -- before hand 2 ever
            // appears anywhere.
            advanceUntil(h, () -> item(h, alice, slot1).getType() == RED_CARD && item(h, alice, slot1).getAmount() == 3, 300);
            assertEquals(BLACK_CARD, item(h, alice, slot0).getType());
            assertEquals(2, item(h, alice, slot0).getAmount(), "hand 1's own original first card must be untouched throughout its collapse");
            assertEquals(BACKGROUND, item(h, alice, slot2).getType(), "hand 1's own middle card must already be gone");

            // Eventually: hand 2 (2 of clubs / 9 of diamonds) lands for real.
            advanceUntil(h, () -> item(h, alice, slot1).getType() == RED_CARD && item(h, alice, slot1).getAmount() == 9, 300);
            assertEquals(BLACK_CARD, item(h, alice, slot0).getType());
            assertEquals(2, item(h, alice, slot0).getAmount(), "hand 2's own first card (2 of clubs)");
            assertEquals(RED_CARD, item(h, alice, slot1).getType());
            assertEquals(9, item(h, alice, slot1).getAmount(), "hand 2's own second card (9 of diamonds)");
            assertEquals(BACKGROUND, item(h, alice, slot2).getType());

            assertNotNull(item(h, alice, BlackjackSlotLayout.ACTION_STAND_SLOT), "hand 2 must now be actionable");
        }
    }

    /**
     * Regression test for a real exploit: the hand-to-hand transition's own
     * scheduled callbacks used to be guarded only by roundGeneration --
     * which does NOT change when a single player leaves mid-round -- so a
     * player who forfeits (door click) mid-transition left every already-
     * scheduled collapse/reveal step free to keep firing into their now-
     * vacated seat, and {@code finishActivatingSplitHand} would eventually
     * call {@code beginActionableDecision()} again for whoever the turn had
     * already moved on to, silently resetting *their* turn timer back to
     * full. Pins down that the next player's timer is never disturbed by
     * the departed player's stale transition.
     */
    @Test
    void leavingMidTransitionNeverResetsTheNextPlayersTurnTimer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            int seatSlotAlice = BlackjackSlotLayout.SEAT_SLOTS[0];
            int seatSlotBob = BlackjackSlotLayout.SEAT_SLOTS[1];

            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));  // Alice's A
            stack.add(new Card(Suit.CLUBS, Rank.TWO));     // Bob's own first card
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));   // Alice's B
            stack.add(new Card(Suit.DIAMONDS, Rank.THREE));// Bob's own second card
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));  // C (Alice hand 1's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.THREE));// D (Alice hand 2's replacement)
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(alice, seatSlotAlice);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.click(bob, seatSlotBob);
            h.inventory.commitWagerForTest(bob, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);
            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest(), "test setup: Alice must act first");

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 20);
            h.advanceToActionableTurn(1, 300);

            // Stand on hand 1 (8/2 = 10) -- schedules the hand 1 -> hand 2
            // transition (collapse skipped, straight to reveal since hand 1
            // never grew past two cards).
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);

            // Immediately forfeit via the door, before the transition has
            // any chance to finish -- this is the acting player, so the
            // turn advances to Bob synchronously, right here, starting his
            // turn timer fresh.
            h.click(alice, BlackjackSlotLayout.ACTIVE_EXIT_SLOT);
            assertEquals(bob.getUniqueId(), h.inventory.currentPlayerIdForTest(), "test setup: the turn must move to Bob the instant Alice forfeits");

            // Step tick-by-tick through the entire duration Alice's now-
            // stale transition (pause + reveal) would have taken, however
            // long that is, asserting the timer only ever holds steady or
            // ticks down -- never back up. If any of the transition's
            // guarded steps fired incorrectly, finishActivatingSplitHand
            // would call beginActionableDecision() again partway through,
            // bumping the value back up for exactly one tick before normal
            // decay resumes from that higher point -- a before/after
            // snapshot taken on either side of that single bump would still
            // see "less than", so only sampling every tick actually catches
            // a one-tick spike like this.
            long staleWindowTicks = 2 * BlackjackTiming.HAND_TRANSITION_PAUSE_TICKS
                + 20 * BlackjackTiming.HAND_TRANSITION_STEP_TICKS + 100;
            int previous = h.inventory.turnTimerSecondsRemainingForTest();
            for (long i = 0; i < staleWindowTicks; i++) {
                h.scheduler.advance(1);
                int now = h.inventory.turnTimerSecondsRemainingForTest();
                assertTrue(now <= previous, "Bob's turn timer must never tick back up -- Alice's stale transition reset it at tick "
                    + i + ": was " + previous + " -> " + now);
                previous = now;
            }

            assertEquals(bob.getUniqueId(), h.inventory.currentPlayerIdForTest(), "Bob must still be the current player -- Alice's stale transition must never reassign the turn");
        }
    }
}
