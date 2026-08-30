package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.AdmissionDecision;
import org.nc.nccasino.budget.AdmissionPolicy;
import org.nc.nccasino.budget.Commitment;
import org.nc.nccasino.budget.DealerBudgetSettings;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slots against a funded dealer.
 *
 * <p>The rule being protected is the design's commitment rule: a spin the
 * dealer cannot cover must be refused <em>before</em> the reels are generated.
 * Once an outcome exists the machine owns it, and a jackpot it cannot pay is
 * not something that can be fixed afterwards without either clamping a
 * legitimate win or leaving the dealer negative.
 */
class SlotsDealerBudgetTest {

    private static final int COLUMNS = 3;
    private static final int LINES = 1;
    private static final SlotsPaytable PAYTABLE =
        SlotsPaytable.forConfig(COLUMNS, SlotsPaytable.DEFAULT_HOUSE_EDGE);

    /** Records what the controller asked of the budget, and in what order. */
    private static final class RecordingUnderwriting implements SlotsUnderwriting {
        final List<String> calls = new ArrayList<>();
        final AtomicLong lastMaxPayout = new AtomicLong(-1);
        final AtomicInteger settlements = new AtomicInteger();
        final AtomicLong settledAmount = new AtomicLong(-1);
        private final Commitment answer;

        RecordingUnderwriting(Commitment answer) {
            this.answer = answer;
        }

        @Override
        public Commitment underwrite(long totalBetUnits, long maxPossiblePayout) {
            calls.add("underwrite");
            lastMaxPayout.set(maxPossiblePayout);
            return answer;
        }

        @Override
        public void cancel(Commitment commitment, long totalBetUnits) {
            calls.add("cancel");
        }

        @Override
        public void settle(Commitment commitment, long payout) {
            calls.add("settle");
            settlements.incrementAndGet();
            settledAmount.set(payout);
        }
    }

    private static Commitment acceptedCommitment() {
        return Commitment.accepted(new org.nc.nccasino.budget.Reservation(
            "test-commitment", "dealer", java.util.UUID.randomUUID(), "Slots",
            null, Money.of(1_000_000L), 0L));
    }

    private static SlotsSpinController.SpinAttempt spin(
        SlotsSpinController controller, RecordingUnderwriting underwriting, boolean playerCanPay) {

        return controller.trySpin(
            10L, COLUMNS, LINES, true, PAYTABLE,
            SlotsRandomSource.production(), underwriting, bet -> playerCanPay);
    }

    // ---- denial happens before any random result exists -------------------

    @Test
    void aDealerThatCannotCoverTheSpinIsRefusedBeforeTheReelsAreGenerated() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting =
            new RecordingUnderwriting(Commitment.refused(AdmissionDecision.INSUFFICIENT_FUNDS));
        AtomicInteger debits = new AtomicInteger();

        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, COLUMNS, LINES, true, PAYTABLE, SlotsRandomSource.production(), underwriting,
            bet -> {
                debits.incrementAndGet();
                return true;
            });

        SlotsSpinController.SpinAttempt.Rejected rejected =
            assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.DEALER_CANNOT_COVER, rejected.reason());
        assertEquals(0, debits.get(), "the player must not be debited by a refused spin");
        assertNull(controller.currentOutcome(), "no outcome may be committed for a refused spin");
        assertEquals(0L, controller.generation(), "a refused spin must not consume a generation");
        assertEquals(List.of("underwrite"), underwriting.calls);
    }

    @Test
    void thePlayerIsToldWhoIsShortByTheDecisionCarriedOnTheRejection() {
        // A permanently over-tier wager and a temporary shortage must stay
        // distinguishable, or a player is told to retry something that will
        // never be accepted.
        SlotsSpinController overTier = new SlotsSpinController();
        SlotsSpinController.SpinAttempt.Rejected tier =
            (SlotsSpinController.SpinAttempt.Rejected) spin(overTier,
                new RecordingUnderwriting(
                    Commitment.refused(AdmissionDecision.EXCEEDS_RISK_TIER)), true);
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER, tier.dealerDecision());
        assertFalse(tier.dealerDecision().isTemporary());

        SlotsSpinController broke = new SlotsSpinController();
        SlotsSpinController.SpinAttempt.Rejected funds =
            (SlotsSpinController.SpinAttempt.Rejected) spin(broke,
                new RecordingUnderwriting(
                    Commitment.refused(AdmissionDecision.INSUFFICIENT_FUNDS)), true);
        assertTrue(funds.dealerDecision().isTemporary());
    }

    @Test
    void theUnderwriterIsAskedAboutTheRealWorstCaseNotAnEstimate() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());

        spin(controller, underwriting, true);

        assertEquals(
            SlotsMath.maxPossiblePayout(10L, LINES, PAYTABLE),
            underwriting.lastMaxPayout.get(),
            "exposure must be every active line at the top symbol, full width");
    }

    // ---- unwinding when the player cannot pay -----------------------------

    @Test
    void aPlayerWhoCannotPayLeavesTheDealerHoldingNothing() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());

        SlotsSpinController.SpinAttempt attempt = spin(controller, underwriting, false);

        SlotsSpinController.SpinAttempt.Rejected rejected =
            assertInstanceOf(SlotsSpinController.SpinAttempt.Rejected.class, attempt);
        assertEquals(SlotsSpinController.RejectReason.INSUFFICIENT_FUNDS, rejected.reason());
        assertEquals(List.of("underwrite", "cancel"), underwriting.calls,
            "the dealer side must be unwound when the player side fails");
        assertNull(controller.commitment(), "no promise may survive a refused spin");
    }

    // ---- the budget moves once, at award time -----------------------------

    @Test
    void theDealerIsDebitedOnceWhenTheResultIsAwarded() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());
        spin(controller, underwriting, true);
        long committed = controller.pendingPayoutAmount();

        controller.settle(owed -> 0L, amount -> true, underwriting);

        assertEquals(1, underwriting.settlements.get());
        assertEquals(committed, underwriting.settledAmount.get(),
            "the dealer is debited the awarded amount, not the delivered one");
        assertNull(controller.commitment(), "the promise is closed at settlement");
    }

    @Test
    void aBankedOrQueuedPayoutStillLeavesTheDealerExactlyOnce() {
        // Delivery fails outright and the win is durably queued instead. The
        // money has still left the dealer: it is owed to the player either way.
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());
        spin(controller, underwriting, true);
        long committed = controller.pendingPayoutAmount();

        controller.settle(owed -> owed, amount -> true, underwriting);

        assertEquals(1, underwriting.settlements.get());
        assertEquals(committed, underwriting.settledAmount.get());
    }

    @Test
    void retryingDeliveryHasNoDealerBudgetEffectAtAll() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());
        spin(controller, underwriting, true);

        // Force a settlement failure so a retry is possible at all.
        SlotsSettlementResult first = controller.settle(owed -> owed, amount -> false, underwriting);
        int settlementsAfterAward = underwriting.settlements.get();

        if (first == SlotsSettlementResult.FAILED) {
            controller.retrySettlement(owed -> 0L, amount -> true);
            controller.state();
        }

        assertEquals(settlementsAfterAward, underwriting.settlements.get(),
            "delivery is not an economic event; only the award is");
        assertTrue(settlementsAfterAward <= 1);
    }

    @Test
    void terminationClosesTheBooksAndDoesSoOnlyOnce() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());
        spin(controller, underwriting, true);

        controller.settleBudgetOnTermination(underwriting);
        controller.settleBudgetOnTermination(underwriting);
        controller.settleBudgetOnTermination(underwriting);

        assertEquals(1, underwriting.settlements.get(),
            "a duplicated termination must not debit the dealer again");
    }

    @Test
    void aNormallySettledRoundIsNotSettledAgainByALaterTermination() {
        SlotsSpinController controller = new SlotsSpinController();
        RecordingUnderwriting underwriting = new RecordingUnderwriting(acceptedCommitment());
        spin(controller, underwriting, true);

        controller.settle(owed -> 0L, amount -> true, underwriting);
        controller.settleBudgetOnTermination(underwriting);

        assertEquals(1, underwriting.settlements.get());
    }

    // ---- unlimited dealers behave exactly as before ------------------------

    @Test
    void anUnlimitedDealerAcceptsTheSpinAndRecordsNothing() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, COLUMNS, LINES, true, PAYTABLE, SlotsRandomSource.production(),
            SlotsUnderwriting.unlimited(), bet -> true);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, attempt);
        Commitment commitment = controller.commitment();
        assertNotNull(commitment);
        assertTrue(commitment.unlimited());
        assertFalse(commitment.requiresSettlement(),
            "an unlimited dealer must not create anything that needs settling");
    }

    @Test
    void theDefaultOverloadIsUnlimitedSoPreExistingBehaviorIsUnchanged() {
        SlotsSpinController explicit = new SlotsSpinController();
        SlotsSpinController implicit = new SlotsSpinController();

        SlotsSpinController.SpinAttempt a = explicit.trySpin(
            10L, COLUMNS, LINES, true, PAYTABLE, SlotsRandomSource.production(),
            SlotsUnderwriting.unlimited(), bet -> true);
        SlotsSpinController.SpinAttempt b = implicit.trySpin(
            10L, COLUMNS, LINES, true, PAYTABLE, SlotsRandomSource.production(), bet -> true);

        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, a);
        assertInstanceOf(SlotsSpinController.SpinAttempt.Accepted.class, b);
    }

    // ---- the exposure a real limited dealer would be asked to cover --------

    @Test
    void aModestlyFundedDealerCanUnderwriteASmallThreeReelSpinButNotAHugeOne() {
        DealerBudgetSettings settings = DealerBudgetSettings.parse(
            "LIMITED", "1000000", "1", "NONE", null, null, null, null);
        BigDecimal available = Money.of(1_000_000L);

        Exposure small = exposureFor(1L, 1);
        Exposure large = exposureFor(10_000L, 9);

        assertEquals(AdmissionDecision.ADMITTED, AdmissionPolicy.admit(settings, available, small));
        assertFalse(AdmissionPolicy.admit(settings, available, large).isAdmitted(),
            "a nine-line high-denomination spin far outruns a 1,000,000 baseline");
    }

    @Test
    void moreActiveLinesRaiseExposureProportionally() {
        Exposure one = exposureFor(100L, 1);
        Exposure three = exposureFor(100L, 3);

        // Each active line carries its own stake and its own top prize, so
        // three lines cost and risk three times as much.
        assertEquals(0,
            Money.multiply(one.stake(), Money.of(3L)).compareTo(three.stake()),
            "three lines stake exactly three times one line");

        // The payout ceiling rounds up per call, so three lines can land a
        // whisker under 3x a single ceiled line rather than exactly on it.
        BigDecimal tripled = Money.multiply(one.maxGrossPayout(), Money.of(3L));
        assertTrue(three.maxGrossPayout().compareTo(tripled) <= 0,
            "exposure must not exceed three single lines");
        assertTrue(Money.subtract(tripled, three.maxGrossPayout()).compareTo(Money.of(3L)) < 0,
            "and must be within rounding of it: " + tripled + " vs " + three.maxGrossPayout());
        assertTrue(three.maxGrossPayout().compareTo(one.maxGrossPayout()) > 0);
    }

    private static Exposure exposureFor(long perLine, int lines) {
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, SlotsPaytable.DEFAULT_HOUSE_EDGE);
        return Exposure.of(
            Money.of(SlotsMath.totalBet(perLine, lines)),
            Money.of(SlotsMath.maxPossiblePayout(perLine, lines, paytable)));
    }
}
