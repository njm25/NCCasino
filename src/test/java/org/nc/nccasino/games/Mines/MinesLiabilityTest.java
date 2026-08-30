package org.nc.nccasino.games.Mines;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mines cash-out exposure, checked before each tile reveal.
 *
 * <p>The property that matters: the obligation after {@code n+1} safe picks is
 * a pure function of the board, known in advance -- so denial can always
 * happen before the tile is turned over, never after.
 */
class MinesLiabilityTest {

    private static final int TOTAL_TILES = 25;
    private static final int MINES = 3;

    @Test
    void zeroPicksReturnsExactlyTheStakeAsACancellationNotAWin() {
        assertEquals(1.0, MinesLiability.payoutMultiplier(TOTAL_TILES, MINES, 0), 1e-9);
        Exposure cancellation = MinesLiability.cancellation(100.0);
        assertEquals(0, cancellation.maxGrossPayout().compareTo(Money.of(100L)));
        assertTrue(Money.isZero(cancellation.maxHouseLoss()),
            "a returned stake cannot cost the house anything");
    }

    @Test
    void eachSafePickRaisesTheMultiplierAboveTheLastOne() {
        double previous = 1.0;
        for (int picks = 1; picks <= TOTAL_TILES - MINES; picks++) {
            double multiplier = MinesLiability.payoutMultiplier(TOTAL_TILES, MINES, picks);
            assertTrue(multiplier > previous, "pick " + picks + " must raise the multiplier");
            previous = multiplier;
        }
    }

    @Test
    void clearingEverySafeTileIsTheAbsoluteCeiling() {
        int allSafe = TOTAL_TILES - MINES;
        double multiplier = MinesLiability.payoutMultiplier(TOTAL_TILES, MINES, allSafe);
        assertTrue(multiplier > 1.0);
        // One more than every safe tile is impossible and must not explode.
        assertEquals(0.0, MinesLiability.payoutMultiplier(TOTAL_TILES, MINES, allSafe + 1), 1e-9);
    }

    @Test
    void theHouseEdgeIsAppliedAsTheStatedOneninetyNinePercentFactor() {
        // One pick of 25 tiles / 3 mines: probability of survival is 22/25.
        double expected = MinesLiability.RETURN_FACTOR / (22.0 / 25.0);
        assertEquals(expected, MinesLiability.payoutMultiplier(TOTAL_TILES, MINES, 1), 1e-9);
    }

    @Test
    void theNextPickIsPricedBeforeItIsRevealed() {
        Exposure now = MinesLiability.currentExposure(100.0, TOTAL_TILES, MINES, 2);
        Exposure next = MinesLiability.exposureAfterNextSafePick(100.0, TOTAL_TILES, MINES, 2);

        assertTrue(next.maxGrossPayout().compareTo(now.maxGrossPayout()) > 0,
            "the next pick must be priced as the higher obligation, in advance");
        assertEquals(0,
            next.maxGrossPayout().compareTo(
                MinesLiability.currentExposure(100.0, TOTAL_TILES, MINES, 3).maxGrossPayout()),
            "and must equal what that pick would actually owe once safe");
    }

    @Test
    void theStakeNeverGrowsAsPicksAccumulate() {
        for (int picks = 0; picks <= TOTAL_TILES - MINES; picks++) {
            Exposure exposure = MinesLiability.currentExposure(100.0, TOTAL_TILES, MINES, picks);
            assertEquals(0, exposure.stake().compareTo(Money.of(100L)), "stake after " + picks + " picks");
        }
    }

    @Test
    void degenerateBoardsYieldNoObligationRatherThanDividingByZero() {
        assertEquals(0.0, MinesLiability.payoutMultiplier(0, 0, 1), 1e-9);
        assertEquals(0.0, MinesLiability.payoutMultiplier(5, 5, 1), 1e-9);
        assertEquals(0.0, MinesLiability.payoutMultiplier(5, 10, 1), 1e-9);
    }

    @Test
    void aZeroOrNegativeWagerRisksNothing() {
        assertTrue(Money.isZero(MinesLiability.cashOutValue(0.0, TOTAL_TILES, MINES, 3)));
        assertTrue(Money.isZero(MinesLiability.cashOutValue(-5.0, TOTAL_TILES, MINES, 3)));
    }
}
