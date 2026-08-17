package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Exercises the exact lifecycle the controller performs against the real
 * map shape it holds ({@code Map<UUID, Deque<Double>>} for committed
 * increments) using the real {@link BlackjackWagerLedger} commit/undo math
 * and the real {@link BlackjackRoundWagerLedger#clearConsumed} the
 * controller's own {@code clearConsumedRoundWagerLedger} delegates to -- not
 * a parallel pure-summation simulation. This is the regression coverage for
 * the currency-integrity defect where {@code resetGame()} cleared
 * {@code playerBets} but left the committed-increment ledger behind,
 * letting Undo All refund an already-settled round's wager a second time.
 *
 * <p>Deliberately does not exercise {@code selectedWager} -- per the table
 * redesign plan, a persistent wager selection survives a normal round reset
 * for a seated player, so {@code clearConsumed} no longer touches it (see
 * {@link BlackjackControllerTestSupport} integration tests for that
 * lifecycle instead).
 */
class BlackjackRoundWagerLedgerTest {

    private static Map<UUID, Deque<Double>> increments() {
        return new HashMap<>();
    }

    /** Mirrors commitWagerFundsAlreadyRemoved's own ledger push. */
    private static void commit(Map<UUID, Deque<Double>> ledger, UUID playerId, double amount) {
        BlackjackWagerLedger.commit(ledger.computeIfAbsent(playerId, k -> new ArrayDeque<>()), amount);
    }

    @Test
    void completedRoundThenUndoAllCannotRefundThePriorWager() {
        UUID player = UUID.randomUUID();
        Map<UUID, Deque<Double>> ledger = increments();

        // Round 1: commit 20, then the round is fully settled (payout/refund
        // already delivered elsewhere -- this test only proves the ledger
        // itself is consumed, matching the ordering invariant).
        commit(ledger, player, 20.0);
        assertEquals(20.0, BlackjackWagerLedger.total(ledger.get(player)));

        BlackjackRoundWagerLedger.clearConsumed(ledger);

        // Round 2's pregame: a click on Undo All looks the player's deque up
        // fresh, exactly like the controller's handleUndoAllBets does.
        Deque<Double> nextRoundDeque = ledger.get(player);
        double refund = nextRoundDeque == null ? 0.0 : BlackjackWagerLedger.undoAll(nextRoundDeque);
        assertEquals(0.0, refund, "the settled round's wager must never be refundable again");
    }

    @Test
    void completedRoundThenNewWagerContainsOnlyNewlyDebitedIncrements() {
        UUID player = UUID.randomUUID();
        Map<UUID, Deque<Double>> ledger = increments();

        commit(ledger, player, 50.0); // round 1's wager
        BlackjackRoundWagerLedger.clearConsumed(ledger);

        commit(ledger, player, 10.0); // round 2's fresh commit
        assertEquals(10.0, BlackjackWagerLedger.total(ledger.get(player)), "round 2's total must never include round 1's leftover 50");
    }

    @Test
    void twoConsecutiveRoundsHaveIndependentLedgers() {
        UUID player = UUID.randomUUID();
        Map<UUID, Deque<Double>> ledger = increments();

        commit(ledger, player, 15.0);
        commit(ledger, player, 5.0);
        assertEquals(20.0, BlackjackWagerLedger.total(ledger.get(player)));
        BlackjackRoundWagerLedger.clearConsumed(ledger);
        assertTrue(ledger.isEmpty(), "round 1's ledger must not survive into round 2's pregame");

        commit(ledger, player, 8.0);
        assertEquals(8.0, BlackjackWagerLedger.total(ledger.get(player)));
        BlackjackRoundWagerLedger.clearConsumed(ledger);
        assertTrue(ledger.isEmpty(), "round 2's ledger must not survive into round 3's pregame either");
    }

    @Test
    void shoeAbortRefundThenUndoAllCannotRefundItAgain() {
        // Mirrors abortRoundForShoeExhaustion: refund is computed and
        // delivered first (not modeled here -- see BlackjackRoundAbortRefundTest
        // for that math), then the ledger is cleared exactly like a normal
        // settlement.
        UUID player = UUID.randomUUID();
        Map<UUID, Deque<Double>> ledger = increments();

        commit(ledger, player, 30.0);
        double refundDeliveredOnce = BlackjackWagerLedger.total(ledger.get(player));
        assertEquals(30.0, refundDeliveredOnce);

        BlackjackRoundWagerLedger.clearConsumed(ledger);

        Deque<Double> afterAbort = ledger.get(player);
        double secondRefundAttempt = afterAbort == null ? 0.0 : BlackjackWagerLedger.undoAll(afterAbort);
        assertEquals(0.0, secondRefundAttempt, "a shoe-abort refund must never be payable a second time via Undo All");
    }

    @Test
    void clearConsumedIsANoOpOnAnAlreadyEmptyMap() {
        Map<UUID, Deque<Double>> ledger = increments();
        BlackjackRoundWagerLedger.clearConsumed(ledger);
        assertTrue(ledger.isEmpty());
    }

    @Test
    void multiplePlayersEachGetAnIndependentFreshLedgerNextRound() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, Deque<Double>> ledger = increments();

        commit(ledger, alice, 40.0);
        commit(ledger, bob, 60.0);
        BlackjackRoundWagerLedger.clearConsumed(ledger);

        assertTrue(ledger.get(alice) == null && ledger.get(bob) == null, "neither player's stale deque should still be present");
    }
}
