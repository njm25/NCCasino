package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A shoe-exhaustion round abort must refund every debit of that round --
 * original wager, split wagers, double wagers, and any insurance stake --
 * for every seated player, not just the triggering player's own wager.
 */
class BlackjackRoundAbortRefundTest {

    @Test
    void refundsASingleUnsplitUndoubledHandPlusNoInsurance() {
        BlackjackHand hand = new BlackjackHand(20);
        assertEquals(20, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 0));
    }

    @Test
    void refundsADoubledHandsCurrentWagerNotItsOriginal() {
        BlackjackHand hand = new BlackjackHand(20);
        hand.setWager(40); // doubled
        assertEquals(40, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 0));
    }

    @Test
    void refundsEverySplitHandsOwnWagerSummed() {
        BlackjackHand first = new BlackjackHand(20);
        BlackjackHand sibling = new BlackjackHand(20); // one matching wager debited at split
        assertEquals(40, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(first, sibling), 0));
    }

    @Test
    void refundsSplitAndDoubledHandsTogether() {
        BlackjackHand first = new BlackjackHand(20);
        first.setWager(40); // doubled after split
        BlackjackHand sibling = new BlackjackHand(20); // not doubled
        assertEquals(60, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(first, sibling), 0));
    }

    @Test
    void includesInsuranceStakeOnTopOfHandWagers() {
        BlackjackHand hand = new BlackjackHand(20);
        assertEquals(30, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 10));
    }

    @Test
    void handlesNullOrEmptyHandsGracefully() {
        assertEquals(0, BlackjackRoundAbortRefund.totalRefundForPlayer(null, 0));
        assertEquals(15, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(), 15));
    }

    @Test
    void negativeInsuranceStakeNeverReducesTheRefund() {
        BlackjackHand hand = new BlackjackHand(20);
        assertEquals(20, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), -5));
    }

    // --- Scenarios mirroring the shutdown/shoe-exhaustion refund audit --

    @Test
    void repeatedSplitsRefundEveryResultingHandsOwnWager() {
        // Original hand split twice -> three live hands, each carrying its
        // own matching-wager debit.
        BlackjackHand first = new BlackjackHand(10);
        BlackjackHand second = new BlackjackHand(10);
        BlackjackHand third = new BlackjackHand(10);
        assertEquals(30, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(first, second, third), 0));
    }

    @Test
    void multipleIndependentlyDoubledHandsEachRefundTheirOwnCurrentWager() {
        BlackjackHand doubledA = new BlackjackHand(10);
        doubledA.setWager(20);
        BlackjackHand doubledB = new BlackjackHand(10);
        doubledB.setWager(20);
        BlackjackHand notDoubled = new BlackjackHand(10);
        assertEquals(50, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(doubledA, doubledB, notDoubled), 0));
    }

    @Test
    void pendingInsuranceIsIncludedExactlyOnceNeverDoubleCounted() {
        BlackjackHand hand = new BlackjackHand(20);
        double refund = BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 10);
        assertEquals(30, refund);
        // Calling it again with the same inputs must be idempotent (pure
        // function, no hidden state) -- never accumulate across calls.
        assertEquals(30, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 10));
    }

    @Test
    void originalWagerOnlyMatchesTheHandsSoleWager() {
        BlackjackHand hand = new BlackjackHand(15);
        assertEquals(15, BlackjackRoundAbortRefund.totalRefundForPlayer(List.of(hand), 0));
    }
}
