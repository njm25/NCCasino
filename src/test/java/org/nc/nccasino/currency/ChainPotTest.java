package org.nc.nccasino.currency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X2: a dealer chain's win pays 1.98 times the pot, its fraction as one more
 * unit with that probability, so the house keeps exactly 1 % at every stake.
 * Rounding to the nearest unit paid stakes of 25 or less exactly double.
 */
class ChainPotTest {

    @Test
    void theExpectedWinIsExactly198PercentAtEveryStake() {
        for (long stake : new long[] {1, 10, 25, 26, 1000}) {
            // Every fraction is in hundredths, so 100 evenly spaced rolls
            // give the exact expectation.
            long total = 0;
            for (int i = 0; i < 100; i++) {
                total += ChainPot.compound(stake, (i + 0.5) / 100.0);
            }
            assertEquals(stake * 198, total, "stake " + stake);
        }
    }

    @Test
    void manySeededFlipsAverage198PercentAndOnlyTheTwoNeighboursArePaid() {
        SplittableRandom random = new SplittableRandom(20261029L);
        for (long stake : new long[] {1, 10, 25, 26, 1000}) {
            int flips = 200_000;
            long total = 0;
            long low = stake * 198 / 100;
            for (int i = 0; i < flips; i++) {
                long win = ChainPot.compound(stake, random.nextDouble());
                assertTrue(win == low || win == low + 1, "stake " + stake + " paid " + win);
                total += win;
            }
            double mean = (double) total / flips;
            // The fraction's standard error is at most 0.5 / sqrt(flips) ~ 0.0011.
            assertEquals(stake * 1.98, mean, 0.006, "stake " + stake);
        }
    }

    @Test
    void smallStakesNoLongerPayExactlyDouble() {
        assertEquals(1, ChainPot.compound(1, 0.99), "1.98: 2 only with probability 0.98");
        assertEquals(2, ChainPot.compound(1, 0.5));
        assertEquals(49, ChainPot.compound(25, 0.5), "49.5: 50 only with probability 0.5");
        assertEquals(50, ChainPot.compound(25, 0.49));
        assertEquals(19, ChainPot.compound(10, 0.8));
        assertEquals(20, ChainPot.compound(10, 0.79));
        assertEquals(1980, ChainPot.compound(1000, 0.0), "a whole product is paid as it is");
        assertEquals(1980, ChainPot.compound(1000, 0.999));
    }

    @Test
    void vaultCountsInCentsSoTenPaysExactly1980() {
        long pot = ChainPot.fromStake(10, CurrencyMode.VAULT);
        assertEquals(1000, pot);
        long won = ChainPot.compound(pot, 0.3);
        assertEquals(new BigDecimal("19.80"), ChainPot.toMoney(won, CurrencyMode.VAULT));
        assertEquals(19.8, ChainPot.toAmount(won, CurrencyMode.VAULT));
        assertEquals(new BigDecimal("10"), ChainPot.toMoney(ChainPot.fromStake(10, CurrencyMode.STANDARD), CurrencyMode.STANDARD));
    }

    @Test
    void aChainKeepsTheEdgeExactInBothModes() {
        // Items: 10 -> 19.8 -> 39.204 expected. Each step rounds stochastically,
        // so averaged over every pair of rolls the two-win pot is 10 x 1.98^2.
        double itemTotal = 0;
        double vaultTotal = 0;
        int n = 400;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double r1 = (i + 0.5) / n;
                double r2 = (j + 0.5) / n;
                itemTotal += ChainPot.compound(ChainPot.compound(10, r1), r2);
                vaultTotal += ChainPot.compound(ChainPot.compound(1000, r1), r2);
            }
        }
        assertEquals(39.204, itemTotal / (n * n), 1e-9);
        // Vault: $10 -> $19.80 exactly -> 3920.4 cents, so $39.20 or $39.21.
        assertEquals(3920.4, vaultTotal / (n * n), 1e-9);
        long second = ChainPot.compound(ChainPot.compound(1000, 0.5), 0.5);
        assertEquals(new BigDecimal("39.20"), ChainPot.toMoney(second, CurrencyMode.VAULT));
        assertEquals(new BigDecimal("39.21"), ChainPot.toMoney(ChainPot.compound(1980, 0.1), CurrencyMode.VAULT));
    }

    @Test
    void theBudgetReservesTheRoundedUpWin() {
        assertEquals(52, ChainPot.maxCompound(26), "51.48 can pay 52");
        assertEquals(1980, ChainPot.maxCompound(1000), "a whole product needs nothing more");
        for (int i = 0; i < 100; i++) {
            assertTrue(ChainPot.compound(26, i / 100.0) <= ChainPot.maxCompound(26));
        }
    }

    @Test
    void doesNotClampAtTheOldIntBoundary() {
        assertEquals(3_960_000_000L, ChainPot.compound(2_000_000_000L, 0.5));
        // 2147483647 x 1.98 = 4252017621.06
        assertEquals(4_252_017_621L, ChainPot.compound(Integer.MAX_VALUE, 0.5));
        assertEquals(4_252_017_622L, ChainPot.compound(Integer.MAX_VALUE, 0.05));
    }

    @Test
    void capsAtTheDoubleSafeIntegerBoundary() {
        assertEquals(ChainPot.MAX_SAFE_POT, ChainPot.compound(ChainPot.MAX_SAFE_POT, 0.5));
        assertEquals(ChainPot.MAX_SAFE_POT, ChainPot.compound(Long.MAX_VALUE / 2, 0.5));
        assertTrue(ChainPot.wouldExceedSafeMax(5_000_000_000_000_000L));
        assertFalse(ChainPot.wouldExceedSafeMax(4_000_000_000_000_000L));
        assertTrue(ChainPot.wouldExceedSafeMax(ChainPot.MAX_SAFE_POT));
        assertFalse(ChainPot.wouldExceedSafeMax(100));
        // Any pot cleared for another round compounds without clamping.
        assertEquals(7_920_000_000_000_000L, ChainPot.compound(4_000_000_000_000_000L, 0.5));
    }

    @Test
    void nonPositivePotsCompoundToZeroAndRollsMustBeInRange() {
        assertEquals(0, ChainPot.compound(0, 0.5));
        assertEquals(0, ChainPot.compound(-5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> ChainPot.compound(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> ChainPot.compound(10, Double.NaN));
    }
}
