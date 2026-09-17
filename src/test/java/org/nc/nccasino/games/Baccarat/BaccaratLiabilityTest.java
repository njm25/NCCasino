package org.nc.nccasino.games.Baccarat;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baccarat's worst case across every result family, including the two pair
 * side bets that pay independently of who wins the hand.
 */
class BaccaratLiabilityTest {

    private static Map<BaccaratClient.BetOption, Double> bets(Object... optionAndWager) {
        Map<BaccaratClient.BetOption, Double> map =
            new EnumMap<>(BaccaratClient.BetOption.class);
        for (int i = 0; i < optionAndWager.length; i += 2) {
            map.put((BaccaratClient.BetOption) optionAndWager[i], (Double) optionAndWager[i + 1]);
        }
        return map;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, Money.of(new BigDecimal(expected)).compareTo(actual),
            "expected " + expected + " but was " + actual.toPlainString());
    }

    @Test
    void anEmptyTableRisksNothing() {
        assertMoney("0", BaccaratLiability.maxPossiblePayout(bets()));
        assertMoney("0", BaccaratLiability.maxPossiblePayout(null));
    }

    // ---- the individual payout rules --------------------------------------

    @Test
    void aPlayerBetPaysTwiceTheStake() {
        assertMoney("200", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.PLAYER, 100.0)));
    }

    @Test
    void aBankerBetPaysTheCommissionAdjustedAmountNotAFlatDouble() {
        // 1.95x, not 2x: the 5% commission on the winnings is real money and
        // reserving 2x would overstate the dealer's obligation on every hand.
        assertMoney("195", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.BANKER, 100.0)));
    }

    @Test
    void aTieBetPaysNineTimesTheStake() {
        assertMoney("900", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.TIE, 100.0)));
    }

    @Test
    void aPairBetPaysTwelveTimesTheStake() {
        assertMoney("1200", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.PLAYERPAIR, 100.0)));
        assertMoney("1200", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.BANKERPAIR, 100.0)));
    }

    // ---- results that combine ---------------------------------------------

    @Test
    void aTiePushesTheMainBetsRatherThanLosingThem() {
        // Both main bets come back at 1x on a Tie, and the Tie bet pays 9x --
        // all three at once, which is the worst case for this portfolio.
        Map<BaccaratClient.BetOption, Double> portfolio = bets(
            BaccaratClient.BetOption.PLAYER, 100.0,
            BaccaratClient.BetOption.BANKER, 100.0,
            BaccaratClient.BetOption.TIE, 100.0);

        // Tie: 900 + 100 + 100 = 1100. Player wins: 200. Banker wins: 195.
        assertMoney("1100", BaccaratLiability.maxPossiblePayout(portfolio));
    }

    @Test
    void aPairPaysAlongsideTheMainResult() {
        Map<BaccaratClient.BetOption, Double> portfolio = bets(
            BaccaratClient.BetOption.PLAYER, 100.0,
            BaccaratClient.BetOption.PLAYERPAIR, 100.0);
        // Player wins with a player pair showing: 200 + 1200.
        assertMoney("1400", BaccaratLiability.maxPossiblePayout(portfolio));
    }

    @Test
    void bothPairsCanLandTogetherWithTheMainResult() {
        Map<BaccaratClient.BetOption, Double> portfolio = bets(
            BaccaratClient.BetOption.TIE, 100.0,
            BaccaratClient.BetOption.PLAYER, 100.0,
            BaccaratClient.BetOption.BANKER, 100.0,
            BaccaratClient.BetOption.PLAYERPAIR, 100.0,
            BaccaratClient.BetOption.BANKERPAIR, 100.0);

        // Tie (900 + 100 push + 100 push) plus both pairs (1200 + 1200).
        assertMoney("3500", BaccaratLiability.maxPossiblePayout(portfolio));
    }

    @Test
    void theWorstCaseBeatsEveryIndividualResultFamily() {
        Map<BaccaratClient.BetOption, Double> portfolio = bets(
            BaccaratClient.BetOption.BANKER, 1000.0,
            BaccaratClient.BetOption.PLAYERPAIR, 50.0);

        BigDecimal worst = BaccaratLiability.maxPossiblePayout(portfolio);
        // Banker wins with a player pair: 1950 + 600.
        assertMoney("2550", worst);
        assertTrue(worst.compareTo(Money.of(1950L)) > 0,
            "the pair must be counted on top of the main result");
    }

    @Test
    void aBankerHeavyPortfolioIsNotOverReservedByAssumingATie() {
        // Only Banker is staked, so a Tie merely pushes it. Reserving the Tie
        // branch would tie up funds the dealer never risks.
        assertMoney("1950", BaccaratLiability.maxPossiblePayout(
            bets(BaccaratClient.BetOption.BANKER, 1000.0)));
    }

    // ---- stake and exposure -----------------------------------------------

    @Test
    void theStakeIsTheSumOfEveryOption() {
        assertMoney("350", BaccaratLiability.totalStake(bets(
            BaccaratClient.BetOption.PLAYER, 100.0,
            BaccaratClient.BetOption.TIE, 50.0,
            BaccaratClient.BetOption.BANKERPAIR, 200.0)));
    }

    @Test
    void exposureCarriesBothTheStakeAndTheWorstCase() {
        Exposure exposure = BaccaratLiability.exposureOf(
            bets(BaccaratClient.BetOption.TIE, 100.0));
        assertMoney("100", exposure.stake());
        assertMoney("900", exposure.maxGrossPayout());
        assertMoney("800", exposure.maxHouseLoss());
    }

    @Test
    void aFractionalVaultWagerKeepsItsExactValue() {
        // 0.1 has no exact binary representation; three of them must still be
        // exactly 0.3 of stake and exactly 2.7 of Tie payout.
        Map<BaccaratClient.BetOption, Double> portfolio = bets(
            BaccaratClient.BetOption.TIE, 0.3);
        assertMoney("0.3", BaccaratLiability.totalStake(portfolio));
        assertMoney("2.7", BaccaratLiability.maxPossiblePayout(portfolio));
    }

    // ---- adding a bet -----------------------------------------------------

    @Test
    void addingABetIsPricedAsTheWholeUpdatedPortfolio() {
        Map<BaccaratClient.BetOption, Double> existing =
            bets(BaccaratClient.BetOption.PLAYER, 100.0);

        Exposure after = BaccaratLiability.exposureAfterAdding(
            existing, BaccaratClient.BetOption.PLAYERPAIR, 100.0);

        assertMoney("1400", after.maxGrossPayout());
        assertMoney("200", after.stake());
    }

    @Test
    void addingToAnExistingOptionAccumulatesRatherThanReplaces() {
        Map<BaccaratClient.BetOption, Double> existing =
            bets(BaccaratClient.BetOption.TIE, 100.0);

        Exposure after = BaccaratLiability.exposureAfterAdding(
            existing, BaccaratClient.BetOption.TIE, 50.0);

        assertMoney("150", after.stake());
        assertMoney("1350", after.maxGrossPayout());
    }

    @Test
    void addingABetNeverMutatesTheExistingPortfolio() {
        Map<BaccaratClient.BetOption, Double> existing =
            bets(BaccaratClient.BetOption.PLAYER, 100.0);

        BaccaratLiability.exposureAfterAdding(
            existing, BaccaratClient.BetOption.TIE, 500.0);

        assertEquals(1, existing.size(), "the hypothetical must not touch the live portfolio");
        assertEquals(100.0, existing.get(BaccaratClient.BetOption.PLAYER));
    }

    @Test
    void everyBetOptionIsPricedSoANewOneCannotBeSilentlyFree() {
        // If an option is ever added to the enum without a payout rule here,
        // this catches it: staking on it alone must produce a nonzero
        // obligation for at least one result.
        for (BaccaratClient.BetOption option : BaccaratClient.BetOption.values()) {
            BigDecimal worst = BaccaratLiability.maxPossiblePayout(bets(option, 100.0));
            assertTrue(worst.compareTo(Money.ZERO) > 0,
                option + " has no payout rule in the liability calculator");
        }
    }
}
