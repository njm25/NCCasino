package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level tests for the pure per-player spin/settlement lifecycle.
 * Fixes two real defects the old monolithic controller had: (1) a second
 * accepted spin after a resolved round used to throw
 * {@code RESOLVED -> DEBIT_ACCEPTED} is illegal -- see
 * {@link #manyConsecutiveSpinsEachDebitExactlyOnceWithNoException()}; (2) a
 * payout that could be neither delivered nor durably queued used to be
 * dropped -- see {@link #failedSettlementRetainsTheAmountAndRetryDoesNotDoublePay()}.
 */
class SlotsSpinControllerTest {

    private static final int COLUMNS = 3;
    private static final int LINES = 5;
    private static final SlotsPaytable PAYTABLE =
        SlotsPaytable.forConfig(COLUMNS, SlotsPaytable.DEFAULT_HOUSE_EDGE);

    /** Every draw returns the same roll, so every cell samples the same symbol. */
    private static SlotsRandomSource constantRoll(int roll) {
        return bound -> roll;
    }

    /**
     * Cumulative weight boundaries are BLANK[0,30) CHERRY[30,52) LEMON[52,70)
     * BELL[70,84) DIAMOND[84,94) SEVEN[94,100), so these two fixtures pin the
     * extremes: a full grid of sevens (every line wins at full width) and a
     * full grid of blanks (nothing can pay, since BLANK never pays).
     */
    private static SlotsRandomSource allSevens() {
        return constantRoll(95);
    }

    private static SlotsRandomSource allBlanks() {
        return constantRoll(0);
    }

    /** A fixed sequence of rolls, one per cell, crafted so no payline matches (see class javadoc math in the test body). */
    private static SlotsRandomSource sequence(int... rolls) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int roll : rolls) {
            queue.add(roll);
        }
        return bound -> queue.poll();
    }

    private static final class RecordingPredicate {
        final List<Long> calls = new java.util.ArrayList<>();
        private final boolean result;

        RecordingPredicate(boolean result) {
            this.result = result;
        }

        boolean test(long amount) {
            calls.add(amount);
            return result;
        }

        int callCount() {
            return calls.size();
        }
    }

    // ---- repeat-spin lifecycle -------------------------------------------

    @Test
    void manyConsecutiveSpinsEachDebitExactlyOnceWithNoException() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        RecordingPredicate liveDeliver = new RecordingPredicate(true);
        RecordingPredicate queue = new RecordingPredicate(true);

        for (int spinNumber = 1; spinNumber <= 25; spinNumber++) {
            SlotsSpinController.SpinAttempt attempt = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
            assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt,
                "spin #" + spinNumber + " must be accepted");
            assertEquals(spinNumber, debit.callCount(), "exactly one debit for spin #" + spinNumber);

            SlotsSettlementResult result = controller.settle(liveDeliver::test, queue::test);
            assertEquals(SlotsSettlementResult.DELIVERED, result);
            assertTrue(controller.isReadyForSpin(), "controller must be ready to spin again after resolving spin #" + spinNumber);
        }
        assertEquals(25, debit.callCount());
    }

    @Test
    void secondSpinAfterResolvedNeverThrowsAndDebitsExactlyOnce() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);

        controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        controller.settle(amount -> true, amount -> true);
        assertEquals(SlotsSessionState.RESOLVED, controller.state());

        SlotsSpinController.SpinAttempt second = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, second);
        assertEquals(2, debit.callCount());
    }

    @Test
    void rejectedSpinPerformsZeroDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(false);

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.INSUFFICIENT_FUNDS,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(1, debit.callCount(), "a rejected (insufficient funds) attempt still calls the predicate once, but withdraws nothing per its own contract");
        assertTrue(controller.isReadyForSpin(), "a failed debit must not lock the table");
        assertEquals(SlotsSessionState.IDLE, controller.state());
    }

    @Test
    void notReadyDuringAnActiveSpinRejectsWithoutTouchingDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertEquals(SlotsSessionState.RESULT_COMMITTED, controller.state());

        SlotsSpinController.SpinAttempt second = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, second);
        assertEquals(SlotsSpinController.RejectReason.NOT_READY,
            ((SlotsSpinController.SpinAttempt.Rejected) second).reason());
        assertEquals(1, debit.callCount(), "the second, not-ready attempt must never debit");
    }

    // ---- exposure / overflow ----------------------------------------------

    @Test
    void invalidDenominationIsRejectedBeforeAnyDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(0, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.INVALID_DENOMINATION,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(0, debit.callCount());
    }

    @Test
    void wagerOverflowIsRejectedBeforeAnyDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(Long.MAX_VALUE, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.WAGER_OVERFLOW,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(0, debit.callCount());
    }

    /**
     * Largest per-line wager whose worst case still fits under the item-mode
     * ceiling. Derived from the live paytable rather than hardcoded, so this
     * stays a genuine boundary test if the paytable's shape or the configured
     * edge ever changes.
     */
    private static long largestSafeItemModeWager() {
        return (long) Math.floor(SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * LINES));
    }

    @Test
    void itemModeRejectsAWagerWhoseWorstCasePayoutExceedsTheCeilingBeforeAnyDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        long oversized = largestSafeItemModeWager() + 1;
        assertTrue(SlotsMath.maxPossiblePayout(oversized, LINES, PAYTABLE) > SlotsMath.MAX_ITEM_MODE_PAYOUT,
            "fixture must actually exceed the ceiling");

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(oversized, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.BET_TOO_LARGE_FOR_MODE,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(0, debit.callCount(), "an oversized item-mode spin must never withdraw anything");
    }

    @Test
    void sameWagerIsAcceptedWhenNotInItemMode() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        long oversized = largestSafeItemModeWager() + 1;
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(oversized, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertEquals(1, debit.callCount());
    }

    @Test
    void itemModeAcceptsAWagerAtExactlyTheCeiling() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingPredicate debit = new RecordingPredicate(true);
        long atCeiling = largestSafeItemModeWager();
        assertTrue(atCeiling > 0, "the ceiling must leave at least one playable denomination");
        assertTrue(SlotsMath.maxPossiblePayout(atCeiling, LINES, PAYTABLE) <= SlotsMath.MAX_ITEM_MODE_PAYOUT);

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(atCeiling, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertEquals(1, debit.callCount());
    }

    // ---- settlement result semantics --------------------------------------

    @Test
    void successfulLiveDeliveryNeverQueues() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), amount -> true);
        RecordingPredicate liveDeliver = new RecordingPredicate(true);
        RecordingPredicate queue = new RecordingPredicate(true);

        SlotsSettlementResult result = controller.settle(liveDeliver::test, queue::test);

        assertEquals(SlotsSettlementResult.DELIVERED, result);
        assertEquals(1, liveDeliver.callCount());
        assertEquals(0, queue.callCount());
        assertEquals(SlotsSessionState.RESOLVED, controller.state());
        assertEquals(0, controller.pendingPayoutAmount());
    }

    @Test
    void failedLiveDeliveryFallsBackToQueueAndReportsQueued() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), amount -> true);
        RecordingPredicate liveDeliver = new RecordingPredicate(false);
        RecordingPredicate queue = new RecordingPredicate(true);

        SlotsSettlementResult result = controller.settle(liveDeliver::test, queue::test);

        assertEquals(SlotsSettlementResult.QUEUED, result);
        assertEquals(1, liveDeliver.callCount());
        assertEquals(1, queue.callCount());
        assertEquals(SlotsSessionState.RESOLVED, controller.state());
        assertEquals(0, controller.pendingPayoutAmount());
    }

    @Test
    void zeroPayoutIsACompletedLossAndNeverInvokesDeliveryOrQueue() {
        SlotsSpinController controller = new SlotsSpinController();
        // A full grid of BLANK cannot pay on any line at any width, since
        // BLANK is the one symbol with no payout at all.
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allBlanks(), amount -> true);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertEquals(0, ((SlotsSpinController.SpinAttempt.Accepted) attempt).payout());

        RecordingPredicate liveDeliver = new RecordingPredicate(true);
        RecordingPredicate queue = new RecordingPredicate(true);
        SlotsSettlementResult result = controller.settle(liveDeliver::test, queue::test);

        assertEquals(SlotsSettlementResult.DELIVERED, result);
        assertEquals(0, liveDeliver.callCount());
        assertEquals(0, queue.callCount());
        assertEquals(0, controller.lastWinAmount());
    }

    @Test
    void failedSettlementRetainsTheAmountAndRetryDoesNotDoublePay() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), amount -> true);
        long owed = ((SlotsSpinController.SpinAttempt.Accepted) attempt).payout();
        assertTrue(owed > 0);

        SlotsSettlementResult first = controller.settle(amount -> false, amount -> false);
        assertEquals(SlotsSettlementResult.FAILED, first);
        assertEquals(SlotsSessionState.SETTLEMENT_FAILED, controller.state());
        assertEquals(owed, controller.pendingPayoutAmount(), "a failed settlement must retain the exact committed amount");
        assertFalse(controller.isReadyForSpin(), "no new spin may be accepted while settlement is unresolved");

        List<Long> creditedAmounts = new java.util.ArrayList<>();
        SlotsSettlementResult retried = controller.retrySettlement(
            amount -> {
                creditedAmounts.add(amount);
                return true;
            },
            amount -> {
                throw new AssertionError("queue must not be tried once live delivery succeeds on retry");
            });

        assertEquals(SlotsSettlementResult.DELIVERED, retried);
        assertEquals(List.of(owed), creditedAmounts, "the retry must credit the retained amount exactly once");
        assertEquals(SlotsSessionState.RESOLVED, controller.state());
        assertEquals(0, controller.pendingPayoutAmount());
    }

    @Test
    void repeatedRetryFailuresNeverClearTheObligation() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(10, COLUMNS, LINES, false, PAYTABLE, allSevens(), amount -> true);
        long owed = ((SlotsSpinController.SpinAttempt.Accepted) attempt).payout();

        controller.settle(amount -> false, amount -> false);
        for (int i = 0; i < 5; i++) {
            SlotsSettlementResult retry = controller.retrySettlement(amount -> false, amount -> false);
            assertEquals(SlotsSettlementResult.FAILED, retry);
            assertEquals(owed, controller.pendingPayoutAmount());
            assertEquals(SlotsSessionState.SETTLEMENT_FAILED, controller.state());
        }
    }

    @Test
    void retrySettlementOutsideFailedStateThrows() {
        SlotsSpinController controller = new SlotsSpinController();
        assertThrows(IllegalStateException.class, () -> controller.retrySettlement(amount -> true, amount -> true));
    }

    @Test
    void terminateIsIdempotentAndAbsorbing() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.terminate();
        assertEquals(SlotsSessionState.TERMINATED, controller.state());
        controller.terminate();
        assertEquals(SlotsSessionState.TERMINATED, controller.state());
    }
}
