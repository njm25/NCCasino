package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the removal of the old 10,000-item pre-spin ceiling.
 *
 * <p>Before overflow banking, item mode refused any spin whose worst case
 * could exceed 10,000 units, because there was nowhere safe to put a payout
 * larger than a player's inventory. Banking supplies that destination, so
 * the delivery limit is gone -- while the numeric/precision limits it used
 * to be conflated with are deliberately still enforced.
 */
class SlotsItemModeCeilingTest {

    private static final int COLUMNS = 5;
    private static final int LINES = 5;
    private static final SlotsPaytable PAYTABLE =
        SlotsPaytable.forConfig(COLUMNS, SlotsPaytable.DEFAULT_HOUSE_EDGE);

    /** The physical-delivery ceiling that used to reject these spins. */
    private static final long OLD_ITEM_CEILING = 10_000L;

    private static SlotsRandomSource allSevens() {
        return bound -> 95;
    }

    private static final class RecordingDebit {
        private int calls;
        private long lastAmount;

        boolean test(long amount) {
            calls++;
            lastAmount = amount;
            return true;
        }
    }

    @Test
    void theCeilingIsNoLongerAPhysicalInventoryLimit() {
        assertTrue(SlotsMath.MAX_ITEM_MODE_PAYOUT > OLD_ITEM_CEILING,
            "the 10,000-item delivery ceiling must be gone");
        assertEquals(1L << 53, SlotsMath.MAX_ITEM_MODE_PAYOUT,
            "what remains is the double-precision limit, not an inventory-size one");
    }

    @Test
    void itemModeAcceptsASpinWhoseWorstCaseFarExceedsTheOldCeiling() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();
        long denomination = 1_000L;

        long worstCase = SlotsMath.maxPossiblePayout(denomination, LINES, PAYTABLE);
        assertTrue(worstCase > OLD_ITEM_CEILING,
            "fixture must be a wager the old ceiling would have rejected (worst case " + worstCase + ")");

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            denomination, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertEquals(1, debit.calls);
        assertEquals(denomination * LINES, debit.lastAmount);
    }

    @Test
    void anItemModePayoutAboveTheOldCeilingIsCommittedAndSettledInFull() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            1_000L, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);

        SlotsSpinController.SpinAttempt.Accepted accepted =
            assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertTrue(accepted.payout() > OLD_ITEM_CEILING,
            "an all-sevens grid at this denomination must pay above the old ceiling (was "
                + accepted.payout() + ")");

        controller.beginAnimating();
        long[] delivered = {0L};
        SlotsSettlementResult result = controller.settle(
            amount -> {
                delivered[0] = amount;
                return true;
            },
            amount -> false);

        assertEquals(SlotsSettlementResult.DELIVERED, result);
        assertEquals(accepted.payout(), delivered[0], "the whole committed payout must be delivered, not clamped");
        assertEquals(0L, controller.pendingPayoutAmount());
    }

    @Test
    void denominationSelectionNoLongerSkipsWagersTheOldCeilingBanned() {
        double denomination = 1_000d;
        assertTrue(SlotsMath.maxPossiblePayout(1_000L, LINES, PAYTABLE) > OLD_ITEM_CEILING);

        assertTrue(SlotsDenominationPolicy.isAllowed(denomination, LINES, true, PAYTABLE),
            "item mode must now offer denominations the physical ceiling used to hide");
    }

    @Test
    void sevenReelMachinesArePlayableInItemModeAtOrdinaryDenominations() {
        SlotsPaytable wide = SlotsPaytable.forConfig(7, SlotsPaytable.DEFAULT_HOUSE_EDGE);
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();

        // A seven-reel machine's worst case is enormous relative to the stake;
        // this is exactly the case item mode used to refuse outright.
        assertTrue(SlotsMath.maxPossiblePayout(100L, 9, wide) > OLD_ITEM_CEILING);

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            100L, 7, 9, true, wide, allSevens(), debit::test);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
    }

    // ---- what is still enforced -------------------------------------------

    @Test
    void aWagerBeyondThePrecisionCeilingIsStillRejectedBeforeAnyDebit() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();
        long oversized = (long) Math.floor(
            SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * LINES)) + 1;

        assertTrue(SlotsMath.maxPossiblePayout(oversized, LINES, PAYTABLE) > SlotsMath.MAX_ITEM_MODE_PAYOUT);

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            oversized, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.BET_TOO_LARGE_FOR_MODE,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(0, debit.calls, "a spin beyond representable precision must never withdraw anything");
    }

    @Test
    void anArithmeticallyImpossibleWagerIsStillRejectedAsOverflow() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            Long.MAX_VALUE, COLUMNS, LINES, true, PAYTABLE, allSevens(), debit::test);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.WAGER_OVERFLOW,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
        assertEquals(0, debit.calls);
    }

    @Test
    void precisionCeilingStillAppliesOnlyToItemMode() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingDebit debit = new RecordingDebit();
        long oversized = (long) Math.floor(
            SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * LINES)) + 1;

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            oversized, COLUMNS, LINES, false, PAYTABLE, allSevens(), debit::test);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
    }

    @Test
    void anOversizedItemModeDenominationIsStillFilteredOutOfSelection() {
        double oversized = Math.floor(
            SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * LINES)) + 1;

        assertFalse(SlotsDenominationPolicy.isAllowed(oversized, LINES, true, PAYTABLE));
    }
}
