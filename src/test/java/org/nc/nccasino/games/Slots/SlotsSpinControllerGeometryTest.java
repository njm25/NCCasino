package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SlotsSpinController}'s height-aware {@code trySpin} overload. */
class SlotsSpinControllerGeometryTest {

    private static SlotsRandomSource fixed(int value) {
        return bound -> value;
    }

    @Test
    void exactlyOneDebitOnAcceptedGeometryAwareSpin() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        int[] debitCalls = {0};

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, 5, 5, 9, false, paytable, fixed(0), SlotsUnderwriting.unlimited(),
            amount -> {
                debitCalls[0]++;
                return true;
            });

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        assertEquals(1, debitCalls[0]);
        assertEquals(90L, ((SlotsSpinController.SpinAttempt.Accepted) attempt).totalBetUnits());
    }

    @Test
    void aRejectedSpinNeverDebits() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        int[] debitCalls = {0};

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, 5, 5, 9, false, paytable, fixed(0), SlotsUnderwriting.unlimited(),
            amount -> {
                debitCalls[0]++;
                return false;
            });

        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(1, debitCalls[0], "the debit is still attempted once -- rejection here means the debit itself failed");
        assertEquals(SlotsSpinController.RejectReason.INSUFFICIENT_FUNDS,
            ((SlotsSpinController.SpinAttempt.Rejected) attempt).reason());
    }

    @Test
    void heightOneProducesACorrectlyShapedOutcome() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            5L, 3, 1, 1, false, paytable, fixed(50), SlotsUnderwriting.unlimited(), amount -> true);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        SlotsOutcome outcome = ((SlotsSpinController.SpinAttempt.Accepted) attempt).outcome();
        assertEquals(1, outcome.rows());
        assertEquals(3, outcome.columns());
    }

    @Test
    void eachGeometrysWorstCaseIsUnderwrittenCorrectly() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                SlotsSpinController controller = new SlotsSpinController();
                SlotsPaytable paytable = SlotsPaytable.forConfig(columns, 0.03, SlotsVariance.HIGH_ROLLER);
                int lines = SlotsPaylineCatalog.lineCount(rows);
                long expectedMax = SlotsMath.maxPossiblePayoutForGeometry(10L, rows, lines, paytable);
                long[] seenExposure = {-1L};

                controller.trySpin(10L, columns, rows, lines, false, paytable, fixed(0),
                    new SlotsUnderwriting() {
                        @Override
                        public org.nc.nccasino.budget.Commitment underwrite(long totalBetUnits, long maxPossiblePayout) {
                            seenExposure[0] = maxPossiblePayout;
                            return org.nc.nccasino.budget.Commitment.forUnlimitedDealer();
                        }

                        @Override
                        public void cancel(org.nc.nccasino.budget.Commitment commitment, long totalBetUnits) {
                        }

                        @Override
                        public void settle(org.nc.nccasino.budget.Commitment commitment, long payout) {
                        }
                    },
                    amount -> true);

                assertEquals(expectedMax, seenExposure[0],
                    "columns=" + columns + " rows=" + rows + ": worst-case exposure must match the geometry-aware ceiling");
            }
        }
    }

    @Test
    void deniedAdmissionNeverGeneratesAnOutcomeOrDebits() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        int[] debitCalls = {0};

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, 5, 5, 9, false, paytable, fixed(0),
            new SlotsUnderwriting() {
                @Override
                public org.nc.nccasino.budget.Commitment underwrite(long totalBetUnits, long maxPossiblePayout) {
                    return org.nc.nccasino.budget.Commitment.refused(org.nc.nccasino.budget.AdmissionDecision.EXCEEDS_RISK_TIER);
                }

                @Override
                public void cancel(org.nc.nccasino.budget.Commitment commitment, long totalBetUnits) {
                }

                @Override
                public void settle(org.nc.nccasino.budget.Commitment commitment, long payout) {
                }
            },
            amount -> {
                debitCalls[0]++;
                return true;
            });

        assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(0, debitCalls[0], "a denied admission must never reach the debit");
        assertEquals(null, controller.currentOutcome());
    }
}
