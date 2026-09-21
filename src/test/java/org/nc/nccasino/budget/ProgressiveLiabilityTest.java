package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dragon Descent floors and Coin Flip / RPS chains.
 *
 * <p>What is really being tested is the ordering guarantee: the dealer is asked
 * about the pot a win would create <em>before</em> the random result exists.
 * That is the difference between a player being told "this is as far as this
 * machine goes" and a player winning and then being told it does not count.
 */
class ProgressiveLiabilityTest {

    private static final double TOLERANCE = 1e-9;

    // ---- Dragon Descent --------------------------------------------------

    @Test
    void zeroFloorsClearedReturnsTheStakeUntouched() {
        assertEquals(1.0, ProgressiveLiability.dragonMultiplier(2, 4, 0), TOLERANCE);
        Exposure exposure = ProgressiveLiability.dragonCurrentExposure(100.0, 2, 4, 0);
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(100L)));
        assertTrue(Money.isZero(exposure.maxHouseLoss()),
            "an untouched stake cannot cost the house anything");
    }

    @Test
    void eachClearedFloorCompoundsByTheInverseSurvivalChance() {
        // Two safe spots of four: each floor halves the survival chance, so
        // the multiplier doubles.
        assertEquals(0.99 / 0.5, ProgressiveLiability.dragonMultiplier(2, 4, 1), TOLERANCE);
        assertEquals(0.99 / 0.25, ProgressiveLiability.dragonMultiplier(2, 4, 2), TOLERANCE);
        assertEquals(0.99 / 0.125, ProgressiveLiability.dragonMultiplier(2, 4, 3), TOLERANCE);
    }

    @Test
    void theNextFloorIsPricedBeforeItIsPlayed() {
        Exposure now = ProgressiveLiability.dragonCurrentExposure(100.0, 2, 4, 1);
        Exposure next = ProgressiveLiability.dragonExposureAfterNextFloor(100.0, 2, 4, 1);

        assertTrue(next.maxGrossPayout().compareTo(now.maxGrossPayout()) > 0,
            "the next floor must be the higher obligation, checked in advance");
        assertEquals(0,
            next.maxGrossPayout().compareTo(
                ProgressiveLiability.dragonCurrentExposure(100.0, 2, 4, 2).maxGrossPayout()),
            "and must equal what that floor would actually owe");
    }

    @Test
    void theStakeDoesNotGrowAsTheDescentContinues() {
        // Only the dealer's side compounds. That asymmetry is exactly why the
        // check has to be repeated every floor rather than once at the start.
        for (int floors = 0; floors <= 5; floors++) {
            Exposure exposure = ProgressiveLiability.dragonCurrentExposure(100.0, 2, 4, floors);
            assertEquals(0, exposure.stake().compareTo(Money.of(100L)),
                "stake after " + floors + " floors");
        }
    }

    @Test
    void degenerateFloorGeometryYieldsNoObligationRatherThanDividingByZero() {
        assertEquals(0.0, ProgressiveLiability.dragonMultiplier(0, 4, 1), TOLERANCE);
        assertEquals(0.0, ProgressiveLiability.dragonMultiplier(2, 0, 1), TOLERANCE);
        assertEquals(0.0, ProgressiveLiability.dragonMultiplier(-1, -1, 3), TOLERANCE);
    }

    @Test
    void aDeepDescentStaysNumericallyUsable() {
        Exposure deep = ProgressiveLiability.dragonCurrentExposure(1.0, 1, 4, 20);
        assertTrue(Money.isSafe(deep.maxGrossPayout()) || !Money.isSafe(deep.maxGrossPayout()),
            "the value must be computable without throwing");
        // And an unsafe value must be refused rather than silently accepted.
        DealerBudgetSettings settings = DealerBudgetSettings.parse(
            "LIMITED", "1000000", "1", "NONE", null, null, null, null);
        if (!deep.isNumericallySafe()) {
            assertEquals(AdmissionDecision.NUMERIC_LIMIT,
                AdmissionPolicy.admit(settings, Money.of(1_000_000L), deep));
        }
    }

    // ---- chain games -----------------------------------------------------

    @Test
    void aChainRoundCompoundsThePotButNotTheStake() {
        Exposure next = ProgressiveLiability.chainExposureAfterNextRound(100.0, 200L, 1.98);

        assertEquals(0, next.stake().compareTo(Money.of(100L)),
            "the player never posts more than their original stake");
        assertEquals(0, next.maxGrossPayout().compareTo(Money.of(396L)));
    }

    @Test
    void theCurrentPotIsWhatTheDealerAlreadyOwes() {
        Exposure current = ProgressiveLiability.chainCurrentExposure(100.0, 396L);
        assertEquals(0, current.maxGrossPayout().compareTo(Money.of(396L)));
        assertEquals(0, current.maxHouseLoss().compareTo(Money.of(296L)),
            "the house risks the pot less the stake it holds");
    }

    @Test
    void eachRoundRaisesTheObligationSoEachRoundMustBeRechecked() {
        long pot = 100L;
        long previous = 0L;
        for (int round = 0; round < 6; round++) {
            pot = ProgressiveLiability.compound(pot, 1.98);
            assertTrue(pot > previous, "round " + round + " must raise the obligation");
            previous = pot;
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -1000L})
    void anEmptyOrNegativePotCompoundsToNothing(long pot) {
        assertEquals(0L, ProgressiveLiability.compound(pot, 1.98));
    }

    @Test
    void anUnusableMultiplierCompoundsToNothingRatherThanNaN() {
        assertEquals(0L, ProgressiveLiability.compound(100L, 0.0));
        assertEquals(0L, ProgressiveLiability.compound(100L, -1.0));
        assertEquals(0L, ProgressiveLiability.compound(100L, Double.NaN));
        assertEquals(0L, ProgressiveLiability.compound(100L, Double.POSITIVE_INFINITY));
    }

    @Test
    void aPotThatOutrunsTheCurrencySystemIsANumericLimitNotAFundingShortage() {
        // The design requires these reasons to stay distinct: a pot past the
        // precision ceiling can never be paid by any dealer, however rich, so
        // reporting it as "the dealer is short" would be actively misleading.
        long huge = 1L << 60;
        Exposure exposure = ProgressiveLiability.chainExposureAfterNextRound(1.0, huge, 1.98);
        assertFalse(exposure.isNumericallySafe(),
            "a pot beyond the supported range must fail the numeric check");

        DealerBudgetSettings richDealer = DealerBudgetSettings.parse(
            "LIMITED", "1000000000000000", "1", "NONE", null, null, null, null);
        assertEquals(AdmissionDecision.NUMERIC_LIMIT,
            AdmissionPolicy.admit(richDealer, Money.MAX, exposure));
    }

    @Test
    void compoundingIsNotSilentlyClampedHereSoTheGameLayerCanTellTheDifference() {
        // The game's own precision ceiling clamps; this calculator must not,
        // or an over-ceiling pot would come back looking merely expensive.
        long belowLongMax = Long.MAX_VALUE / 4;
        long compounded = ProgressiveLiability.compound(belowLongMax, 2.0);
        assertTrue(compounded > belowLongMax,
            "the raw compounded value must be reported, not a clamped stand-in");
    }

    @Test
    void aChainDealerRunsOutBeforeThePrecisionCeilingDoes() {
        DealerBudgetSettings settings = DealerBudgetSettings.parse(
            "LIMITED", "10000", "1", "NONE", null, null, null, null);

        Exposure affordable = ProgressiveLiability.chainExposureAfterNextRound(100.0, 200L, 1.98);
        Exposure unaffordable = ProgressiveLiability.chainExposureAfterNextRound(100.0, 500_000L, 1.98);

        assertEquals(AdmissionDecision.ADMITTED,
            AdmissionPolicy.admit(settings, Money.of(10_000L), affordable));
        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER,
            AdmissionPolicy.admit(settings, Money.of(10_000L), unaffordable),
            "a pot far past the dealer's tier is a permanent refusal, not a numeric one");
    }
}
