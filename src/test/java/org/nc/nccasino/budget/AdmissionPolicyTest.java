package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admission rule: may this dealer take this commitment right now?
 *
 * <p>Two properties matter more than any individual case. First, the risk tier
 * is derived from the <em>fixed</em> underwriting baseline, so a dealer that
 * loses money keeps offering the same wagers rather than shrinking toward zero
 * (the death spiral the design explicitly rules out). Second, the denial
 * reasons stay distinct, because "never" and "not right now" need different
 * messages and different player behavior.
 */
class AdmissionPolicyTest {

    private static DealerBudgetSettings limited(String baseline, int rounds) {
        return DealerBudgetSettings.parse(
            "LIMITED", baseline, String.valueOf(rounds), "NONE", null, null, null, null);
    }

    private static BigDecimal money(String value) {
        return Money.of(new BigDecimal(value));
    }

    // ---- unlimited backward compatibility --------------------------------

    @Test
    void anUnlimitedDealerAdmitsAnythingWithoutConsultingAnyBalance() {
        DealerBudgetSettings unlimited = DealerBudgetSettings.unlimited();
        // Zero available, an astronomically large payout: still admitted,
        // because Phase 2 must not change how existing dealers behave.
        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(unlimited, Money.ZERO,
                Exposure.of(money("1"), money("999999999999"))));
    }

    @Test
    void anUnlimitedDealerIsAdmittedEvenWithNonsenseNumbers() {
        // The unlimited short-circuit runs before validation on purpose: it is
        // on every wager of every existing server and must stay free.
        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(DealerBudgetSettings.unlimited(), null, null));
    }

    // ---- the four distinct checks ----------------------------------------

    @Test
    void aCommitmentWithinBothTierAndFundingIsAdmitted() {
        DealerBudgetSettings settings = limited("1000", 1);
        // Stake 100, may pay back 600 gross -> the house can lose 500.
        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(settings, money("500"), Exposure.of(money("100"), money("600"))));
    }

    @Test
    void exceedingTheBaselineTierIsReportedAsPermanentNotAsAShortage() {
        DealerBudgetSettings settings = limited("1000", 1);
        // House loss of 5000 against a 1000 baseline, but funds are plentiful.
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER,
            AdmissionPolicy.admit(settings, money("999999"),
                Exposure.of(money("100"), money("5100"))));
    }

    @Test
    void aTierFittingCommitmentTheDealerCannotCurrentlyAffordIsTemporary() {
        DealerBudgetSettings settings = limited("1000", 1);
        AdmissionDecision decision = AdmissionPolicy.admit(
            settings, money("10"), Exposure.of(money("100"), money("600")));
        assertEquals(AdmissionDecision.INSUFFICIENT_FUNDS, decision);
        assertTrue(decision.isTemporary(),
            "a funding shortage must be distinguishable from a permanent refusal");
    }

    @Test
    void theTierIsCheckedBeforeFundingSoAHopelessWagerNeverLooksTemporary() {
        // Both would fail. The player must be told the wager is too large for
        // this dealer, not that it might work later -- it never will.
        DealerBudgetSettings settings = limited("1000", 1);
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER,
            AdmissionPolicy.admit(settings, Money.ZERO, Exposure.of(money("1"), money("100000"))));
    }

    @Test
    void unusableNumbersAreTheirOwnReasonAndNotAGameplayRefusal() {
        DealerBudgetSettings settings = limited("1000", 1);
        assertEquals(AdmissionDecision.NUMERIC_LIMIT,
            AdmissionPolicy.admit(settings, money("500"),
                Exposure.of(money("1"), Money.MAX.add(BigDecimal.ONE))));
        assertEquals(AdmissionDecision.NUMERIC_LIMIT,
            AdmissionPolicy.admit(settings, money("-5"), Exposure.of(money("1"), money("2"))));
    }

    @Test
    void aLimitedDealerWithNoUsableBaselineFailsClosed() {
        DealerBudgetSettings broken = limited("not-a-number", 1);
        assertFalse(broken.isUsable());
        assertEquals(AdmissionDecision.CONFIGURATION_INVALID,
            AdmissionPolicy.admit(broken, money("100000"), Exposure.of(money("1"), money("2"))));
    }

    @Test
    void aMissingSettingsBlockFailsClosedRatherThanAdmitting() {
        assertEquals(AdmissionDecision.CONFIGURATION_INVALID,
            AdmissionPolicy.admit(null, money("100000"), Exposure.of(money("1"), money("2"))));
    }

    // ---- guaranteed worst-case rounds ------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 100})
    void theBaselineIsDividedByTheGuaranteedRoundCount(int rounds) {
        DealerBudgetSettings settings = limited("1000", rounds);
        BigDecimal perRound = settings.maxHouseLossPerRound();
        assertEquals(0, perRound.compareTo(money(String.valueOf(1000 / rounds))),
            "a baseline of 1000 over " + rounds + " guaranteed rounds");

        // Exactly at the limit is admitted; a hair over is not.
        assertEquals(AdmissionDecision.ADMITTED, AdmissionPolicy.admit(
            settings, money("100000"), Exposure.of(Money.ZERO, perRound)));
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER, AdmissionPolicy.admit(
            settings, money("100000"), Exposure.of(Money.ZERO, Money.add(perRound, money("0.000001")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-500", "abc", ""})
    void anInvalidGuaranteedRoundCountFallsBackToOneRatherThanDividingByZero(String raw) {
        DealerBudgetSettings settings = DealerBudgetSettings.parse(
            "LIMITED", "1000", raw, "NONE", null, null, null, null);
        assertEquals(1, settings.guaranteedWorstCaseRounds());
        assertEquals(0, settings.maxHouseLossPerRound().compareTo(money("1000")));
    }

    @Test
    void anExtremeGuaranteedRoundCountShrinksTheTierWithoutBreaking() {
        DealerBudgetSettings settings = limited("1000", Integer.MAX_VALUE);
        // 1000 / 2147483647 floors to 0 at the stored scale, so the dealer
        // underwrites nothing rather than producing a nonsense tier.
        assertTrue(Money.isZero(settings.maxHouseLossPerRound()));
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER, AdmissionPolicy.admit(
            settings, money("100000"), Exposure.of(money("1"), money("2"))));
    }

    // ---- the fixed baseline does not ratchet -----------------------------

    @Test
    void theRiskTierIsUnchangedAsTheLiveBalanceRisesAndFalls() {
        DealerBudgetSettings settings = limited("1000", 1);
        Exposure wager = Exposure.of(money("100"), money("600"));
        BigDecimal tier = settings.maxHouseLossPerRound();

        // The identical wager is admitted at every funding level that covers
        // it, and the tier itself never moves -- no death spiral after losses,
        // and no automatic ratchet upward after profits.
        for (String balance : List.of("500", "1000", "9999", "500", "500.000001")) {
            assertEquals(0, settings.maxHouseLossPerRound().compareTo(tier));
            assertEquals(AdmissionDecision.ADMITTED,
                AdmissionPolicy.admit(settings, money(balance), wager),
                "balance " + balance);
        }
    }

    @Test
    void aDenominationBecomesUnavailableThenAvailableAgainAsFundingRecovers() {
        DealerBudgetSettings settings = limited("1000", 1);
        Exposure wager = Exposure.of(money("100"), money("600"));

        assertEquals(AdmissionDecision.ADMITTED, AdmissionPolicy.admit(settings, money("500"), wager));
        assertEquals(AdmissionDecision.INSUFFICIENT_FUNDS,
            AdmissionPolicy.admit(settings, money("499"), wager));
        assertEquals(AdmissionDecision.ADMITTED, AdmissionPolicy.admit(settings, money("500"), wager));

        // Throughout, the wager itself is still within the dealer's tier --
        // the system never invents a smaller wager to fit.
        assertTrue(AdmissionPolicy.withinRiskTier(settings, wager));
    }

    // ---- commitments that cannot cost the house anything ------------------

    @Test
    void aCommitmentThatCannotPayMoreThanItsStakeIsAlwaysAdmitted() {
        DealerBudgetSettings settings = limited("1000", 1);
        // A zero-pick Mines cash-out: the stake comes straight back.
        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(settings, Money.ZERO, Exposure.of(money("100"), money("100"))));
        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(settings, Money.ZERO, Exposure.none()));
    }

    @Test
    void grossPayoutBelowStakeIsTreatedAsZeroLossNotNegative() {
        Exposure exposure = Exposure.of(money("100"), money("40"));
        assertTrue(Money.isZero(exposure.maxHouseLoss()));
    }

    // ---- headroom --------------------------------------------------------

    @Test
    void headroomIsTheTighterOfTheTierAndTheAvailableFunds() {
        DealerBudgetSettings settings = limited("1000", 2); // tier = 500
        assertEquals(0, AdmissionPolicy.headroom(settings, money("9999")).compareTo(money("500")));
        assertEquals(0, AdmissionPolicy.headroom(settings, money("120")).compareTo(money("120")));
        assertEquals(0, AdmissionPolicy.headroom(settings, Money.ZERO).compareTo(Money.ZERO));
    }

    @Test
    void combiningExposuresAddsBothSides() {
        Exposure combined = Exposure.of(money("100"), money("250"))
            .plus(Exposure.of(money("50"), money("125")));
        assertEquals(0, combined.stake().compareTo(money("150")));
        assertEquals(0, combined.maxGrossPayout().compareTo(money("375")));
        assertEquals(0, combined.maxHouseLoss().compareTo(money("225")));
    }
}
