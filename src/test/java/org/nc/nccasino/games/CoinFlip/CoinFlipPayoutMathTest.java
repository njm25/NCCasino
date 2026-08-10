package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoinFlipPayoutMathTest {

    @Test
    void compoundsAndRoundsNormalPots() {
        assertEquals(198, CoinFlipPayoutMath.compound(100, 1.98));
        assertEquals(2, CoinFlipPayoutMath.compound(1, 1.98));
    }

    @Test
    void saturatesInsteadOfWrappingLargePotsNegative() {
        assertEquals(Integer.MAX_VALUE, CoinFlipPayoutMath.compound(2_000_000_000, 1.98));
        assertEquals(Integer.MAX_VALUE, CoinFlipPayoutMath.compound(Integer.MAX_VALUE, 1.98));
    }
}
