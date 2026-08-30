package org.nc.nccasino.games.Roulette;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;
import org.nc.nccasino.objects.Pair;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roulette's worst case, which is the one place an approximation would be
 * quietly wrong: several of a player's bets can win on the same number, and
 * the dealer owes the sum.
 */
class RouletteLiabilityTest {

    private static List<Pair<String, Integer>> bets(Object... typeAndWager) {
        List<Pair<String, Integer>> bets = new ArrayList<>();
        for (int i = 0; i < typeAndWager.length; i += 2) {
            bets.add(new Pair<>((String) typeAndWager[i], (Integer) typeAndWager[i + 1]));
        }
        return bets;
    }

    @Test
    void anEmptyTableRisksNothing() {
        assertEquals(0L, RouletteLiability.maxPossiblePayout(List.of()));
        assertEquals(0L, RouletteLiability.maxPossiblePayout(null));
        assertTrue(Money.isZero(RouletteLiability.exposureOf(List.of()).maxHouseLoss()));
    }

    @Test
    void aSingleStraightUpBetPaysThirtySixTimesTheWager() {
        assertEquals(3_600L, RouletteLiability.maxPossiblePayout(bets("17 - 35:1", 100)));
    }

    @Test
    void aSingleEvenMoneyBetPaysTwiceTheWager() {
        assertEquals(200L, RouletteLiability.maxPossiblePayout(bets("Red - 1:1", 100)));
    }

    @Test
    void simultaneouslyWinningBetsAreSummedRatherThanMaximized() {
        // 12 is red, even, first dozen, and top row. Every one of these pays
        // together, and a "largest single bet" estimate would understate the
        // dealer's obligation by a factor of five.
        List<Pair<String, Integer>> portfolio = bets(
            "12 - 35:1", 100,
            "Red - 1:1", 100,
            "Even - 1:1", 100,
            "1st Dozen - 2:1", 100,
            "Top Row - 2:1", 100);

        long worst = RouletteLiability.maxPossiblePayout(portfolio);

        // 3600 straight-up + 200 red + 200 even + 300 dozen + 300 row.
        assertEquals(4_600L, worst);
        assertTrue(worst > 3_600L, "the combined portfolio must exceed its largest single bet");
    }

    @Test
    void theWorstCaseIsFoundEvenWhenItIsNotTheMostLikelyResult() {
        // Nothing on the table wins on most numbers; the ceiling comes from
        // one specific pocket, which is exactly why every pocket is checked.
        List<Pair<String, Integer>> portfolio = bets(
            "0 - 35:1", 500,
            "Red - 1:1", 10);
        assertEquals(18_000L, RouletteLiability.maxPossiblePayout(portfolio));
    }

    @Test
    void zeroLosesEveryOutsideBetSoAnOutsideOnlyTableStillHasAWorstCase() {
        // Zero is neither red, black, odd, even, in a dozen, nor in a row.
        List<Pair<String, Integer>> outsideOnly = bets(
            "Red - 1:1", 100,
            "Black - 1:1", 100,
            "Odd - 1:1", 100,
            "Even - 1:1", 100);

        // On any non-zero number exactly one colour and one parity win.
        assertEquals(400L, RouletteLiability.maxPossiblePayout(outsideOnly));
        // And on zero, nothing does.
        assertEquals(0L, RoulettePayoutMath.evaluate(0, outsideOnly).totalPayout);
    }

    @Test
    void everyPocketOnTheWheelIsConsidered() {
        // A straight-up bet on each number in turn must be found, including
        // the highest, which an off-by-one loop bound would miss.
        for (int number = 0; number <= 36; number++) {
            assertEquals(360L, RouletteLiability.maxPossiblePayout(bets(number + " - 35:1", 10)),
                "straight up on " + number);
        }
        assertEquals(37, RouletteLiability.POCKETS);
    }

    @Test
    void theStakeIsTheSumOfEveryBetOnTheTable() {
        assertEquals(325L, RouletteLiability.totalStake(
            bets("Red - 1:1", 100, "7 - 35:1", 25, "Even - 1:1", 200)));
    }

    @Test
    void exposureCarriesBothTheStakeAndTheWorstCase() {
        Exposure exposure = RouletteLiability.exposureOf(bets("17 - 35:1", 100));
        assertEquals(0, exposure.stake().compareTo(Money.of(100L)));
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(3_600L)));
        assertEquals(0, exposure.maxHouseLoss().compareTo(Money.of(3_500L)),
            "the house risks the payout less the stake it is holding");
    }

    // ---- adding a bet -----------------------------------------------------

    @Test
    void addingABetIsPricedAsTheWholeUpdatedPortfolio() {
        List<Pair<String, Integer>> existing = bets("Red - 1:1", 100);

        Exposure after = RouletteLiability.exposureAfterAdding(existing, "12 - 35:1", 100);

        // The new straight-up bet pays 3600, and on 12 the existing red bet
        // pays another 200 alongside it.
        assertEquals(0, after.maxGrossPayout().compareTo(Money.of(3_800L)));
        assertEquals(0, after.stake().compareTo(Money.of(200L)));
    }

    @Test
    void addingABetNeverMutatesTheExistingTable() {
        List<Pair<String, Integer>> existing = bets("Red - 1:1", 100);
        RouletteLiability.exposureAfterAdding(existing, "12 - 35:1", 100);
        assertEquals(1, existing.size(), "the hypothetical must not be applied to the live table");
    }

    @Test
    void addingToAnEmptyTableWorks() {
        Exposure after = RouletteLiability.exposureAfterAdding(List.of(), "0 - 35:1", 50);
        assertEquals(0, after.maxGrossPayout().compareTo(Money.of(1_800L)));
    }

    @Test
    void aBetCanRaiseTheWorstCaseByMoreThanItsOwnPayout() {
        // The point of pricing the portfolio rather than the increment: adding
        // the dozen bet raises the ceiling by its own 300 *and* keeps the
        // straight-up 3600 that now coincides with it.
        List<Pair<String, Integer>> existing = bets("12 - 35:1", 100);
        long before = RouletteLiability.maxPossiblePayout(existing);

        Exposure after = RouletteLiability.exposureAfterAdding(existing, "1st Dozen - 2:1", 100);

        assertEquals(0, after.maxGrossPayout().compareTo(Money.of(before + 300L)));
    }
}
