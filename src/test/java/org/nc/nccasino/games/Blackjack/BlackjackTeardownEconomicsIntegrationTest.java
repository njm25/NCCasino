package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.payout.PendingPayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the confirmed critical defect
 * (audit finding 2): {@code delete()} used to call {@code cancelGame()},
 * which cleared every committed wager/hand/split/double/insurance stake
 * without refunding or queuing anything -- reached whenever a dealer is
 * administratively reloaded, replaced, or removed, including from a single
 * ordinary settings-menu toggle click. Fixed by refunding (delivered live
 * when possible, durably queued otherwise) every seated player's complete
 * round stake as the very first step of {@code delete()}, before any of
 * that state is cleared, reusing the same {@code refundRoundDebit}/
 * {@code totalRoundRefundForPlayer} machinery {@code abortRoundForShoeExhaustion}
 * already relied on.
 */
class BlackjackTeardownEconomicsIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void pregameCommittedWagerIsRefundedOnTeardown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 12.5);

            h.inventory.delete();

            assertEquals(1, h.currencyProvider.depositAttempts.size());
            assertEquals(0, BigDecimal.valueOf(12.5).compareTo(h.currencyProvider.depositAttempts.get(0)),
                "the exact committed pregame wager must be refunded, preserving the odd decimal amount");
        }
    }

    @Test
    void selectedButUncommittedWagerIsClearedNotRefunded() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            // Deliberately never committed (commitWagerForTest not called) --
            // totalRoundRefundForPlayer must never read selectedWager, since
            // nothing was ever debited for it.
            assertEquals(0.0, h.inventory.totalRoundRefundForPlayerForTest(alice.getUniqueId()));

            h.inventory.delete();

            assertTrue(h.currencyProvider.depositAttempts.isEmpty(), "nothing was ever debited, so nothing may be refunded");
        }
    }

    @Test
    void activeHandWagerIsRefundedOnTeardown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 20.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertTrue(h.inventory.isGameActiveForTest());

            h.inventory.delete();

            assertEquals(1, h.currencyProvider.depositAttempts.size());
            assertEquals(0, BigDecimal.valueOf(20.0).compareTo(h.currencyProvider.depositAttempts.get(0)),
                "the active hand's own wager, not just the stale pregame bet-slip figure, must be refunded exactly");
        }
    }

    @Test
    void doubledHandWagerIsRefundedInFull() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertTrue(h.inventory.isGameActiveForTest());

            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);
            h.scheduler.advance(100);

            h.inventory.delete();

            assertEquals(1, h.currencyProvider.depositAttempts.size());
            assertEquals(0, BigDecimal.valueOf(30.0).compareTo(h.currencyProvider.depositAttempts.get(0)),
                "a doubled hand's full (2x) wager must be refunded, not just the original half");
        }
    }

    @Test
    void offlinePlayersRefundIsQueuedNotAttemptedLive() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID playerId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(playerId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 17.0);

            h.setOnline(alice, false);
            h.inventory.delete();

            assertTrue(h.currencyProvider.depositAttempts.isEmpty(), "an offline player's refund must never attempt a live deposit");
            List<PendingPayout> pending = h.pendingPayoutStore.getPending(playerId);
            assertEquals(1, pending.size());
            assertEquals(17.0, pending.get(0).amount(), 0.0001, "the exact wagered amount must be queued for an offline player");
        }
    }

    @Test
    void failedLiveVaultDepositFallsBackToQueuingTheExactAmount() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID playerId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(playerId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 9.5);

            h.currencyProvider.setNextDepositSucceeds(false);
            h.inventory.delete();

            assertEquals(1, h.currencyProvider.depositAttempts.size(), "a live delivery must still be attempted for an online player");
            List<PendingPayout> pending = h.pendingPayoutStore.getPending(playerId);
            assertEquals(1, pending.size(), "a failed live deposit must fall back to a durable queued record");
            assertEquals(9.5, pending.get(0).amount(), 0.0001, "the exact amount must be queued, never rounded or dropped");
        }
    }

    @Test
    void alreadyQueuedPayoutsAreUntouchedByATeardownForADifferentPlayer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID unrelatedPlayerId = UUID.randomUUID();
            PendingPayout preexisting = PendingPayout.create(
                unrelatedPlayerId, "Blackjack", h.internalName, org.nc.nccasino.currency.CurrencyMode.VAULT,
                null, "Dollar", 42.0, "pre-existing unrelated record"
            );
            assertTrue(h.pendingPayoutStore.addPendingPayout(preexisting));

            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);

            h.inventory.delete();

            assertEquals(java.util.List.of(preexisting), h.pendingPayoutStore.getPending(unrelatedPlayerId),
                "a teardown must never touch an already-queued record for someone else");
        }
    }

    @Test
    void deleteIsIdempotentAndNeverDoubleRefunds() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 8.0);

            h.inventory.delete();
            assertEquals(1, h.currencyProvider.depositAttempts.size());

            // A second, overlapping teardown call (e.g. a duplicate
            // reloadDealer/deleteAssociatedInventories invocation) must be a
            // safe no-op, never a second refund.
            h.inventory.delete();

            assertEquals(1, h.currencyProvider.depositAttempts.size(), "a duplicate delete() must never refund the same wager twice");
        }
    }

    @Test
    void splitHandsWithADoubledSiblingAreFullyRefundedOnTeardownAndNeverTwice() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Alice: 8/8 (splittable pair), dealer up-card/hole-card both 7
            // (no insurance offered, no dealer blackjack). The split's two
            // immediate replacement cards are a 2 (-> original hand totals
            // 10, a clean double) and an 8 (sibling hand just happens to
            // pair again, unused here -- the resplit case is covered
            // separately below). Everything after that is flat 2s so
            // neither the double-down draw nor any other draw can bust or
            // exhaust the shoe.
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // player card 1
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer up-card
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));    // player card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer hole card
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));   // original hand's split replacement
            stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT)); // sibling hand's split replacement
            for (int i = 0; i < 40; i++) {
                stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
            }
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertTrue(h.inventory.isGameActiveForTest(), "test setup must actually reach an actionable initial hand");

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(100);
            h.advanceToActionableTurn(20, 40);

            h.click(alice, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);
            h.scheduler.advance(100);

            h.inventory.delete();

            // Original hand doubled (15 -> 30) plus the sibling hand's own
            // untouched wager (15) = 45, delivered as a single refund for
            // this one seated player.
            assertEquals(1, h.currencyProvider.depositAttempts.size());
            assertEquals(0, BigDecimal.valueOf(45.0).compareTo(h.currencyProvider.depositAttempts.get(0)),
                "both split hands' wagers -- one of them doubled -- must be summed and refunded exactly once");

            // A duplicate teardown call must never refund this same money again.
            h.inventory.delete();
            assertEquals(1, h.currencyProvider.depositAttempts.size(),
                "a duplicate delete() after a split/double teardown must never double-refund");
        }
    }

    @Test
    void resplitHandsAreAllFullyRefundedOnTeardown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Same opening pair as above, but the sibling hand's own
            // replacement is also an 8 -- once the original hand stands,
            // the sibling becomes active as a pair in its own right and can
            // be split again (a genuine resplit), producing three
            // simultaneously tracked hands.
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // player card 1
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer up-card
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));    // player card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));   // dealer hole card
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));   // first split: original hand's replacement
            stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT)); // first split: sibling hand's replacement (pairs again)
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));   // resplit: original (former sibling) hand's replacement
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));   // resplit: new third hand's replacement
            for (int i = 0; i < 40; i++) {
                stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
            }
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertTrue(h.inventory.isGameActiveForTest(), "test setup must actually reach an actionable initial hand");

            // First split: hand1 (8,2=10) stays active, hand2 (8,8) is queued next.
            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(100);
            h.advanceToActionableTurn(20, 40);

            // Stand on hand1 so hand2 (the 8,8 pair) becomes the active hand.
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);
            h.scheduler.advance(100);
            h.advanceToActionableTurn(20, 40);

            // Resplit hand2 into hand2 (8,2=10) and a brand-new hand3 (8,2=10).
            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(100);

            h.inventory.delete();

            // Three hands, each carrying the original 15 wager (no
            // doubling here): 15 + 15 + 15 = 45, refunded as a single sum
            // for this one seated player.
            assertEquals(1, h.currencyProvider.depositAttempts.size());
            assertEquals(0, BigDecimal.valueOf(45.0).compareTo(h.currencyProvider.depositAttempts.get(0)),
                "all three resplit hands' wagers must be summed and refunded exactly once");

            h.inventory.delete();
            assertEquals(1, h.currencyProvider.depositAttempts.size(),
                "a duplicate delete() after a resplit teardown must never double-refund");
        }
    }

    @Test
    void insuranceStakeIsRefundedOnTeardown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // player1 gets a 7, dealer's up-card is an Ace (offering
            // insurance), player1's second card is a 7 (no natural
            // blackjack), dealer's hole card is a 7 (no dealer blackjack
            // either) -- the round must pause in the INSURANCE phase. Two
            // eligible players are seated and only one answers, so the
            // insurance phase stays genuinely unresolved (checkInsuranceAllDecided
            // never fires the dealer's peek) when delete() runs -- otherwise
            // the single eligible player's own answer would immediately
            // resolve the whole phase (dealer peek: no blackjack here means
            // the stake is legitimately forfeited, not left "unresolved").
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // player 1 card 1
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // player 2 card 1
            stack.add(new Card(Suit.HEARTS, Rank.ACE));    // dealer up-card
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // player 1 card 2
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // player 2 card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole card
            for (int i = 0; i < 40; i++) {
                stack.add(new Card(Suit.DIAMONDS, Rank.SEVEN));
            }
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            UUID playerId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(playerId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 20.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 20.0);

            h.inventory.beginStartTransitionForTest();

            for (int i = 0; i < 40 && h.inventory.capturePhaseForTest() != BlackjackFrame.Phase.INSURANCE; i++) {
                h.scheduler.advance(20);
            }
            assertEquals(BlackjackFrame.Phase.INSURANCE, h.inventory.capturePhaseForTest(), "test setup must actually reach the insurance phase");

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(10.0, h.inventory.insuranceStakeForTest(playerId), 0.0001, "test setup must have genuinely debited the insurance stake");
            assertEquals(BlackjackFrame.Phase.INSURANCE, h.inventory.capturePhaseForTest(), "bob's own decision is still outstanding -- insurance must still be open");

            h.inventory.delete();

            // Two seated players, each refunded independently: alice's main
            // wager (20) plus her still-undetermined insurance stake (10),
            // and bob's own main wager (20, no insurance taken).
            assertEquals(2, h.currencyProvider.depositAttempts.size());
            boolean aliceRefundPresent = h.currencyProvider.depositAttempts.stream()
                .anyMatch(amount -> BigDecimal.valueOf(30.0).compareTo(amount) == 0);
            assertTrue(aliceRefundPresent, "alice's main wager (20) plus her insurance stake (10) must both be refunded together: " + h.currencyProvider.depositAttempts);
        }
    }
}
