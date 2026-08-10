package org.nc.nccasino.games.RockPaperScissors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RpsPayoutMathTest {

    @Test
    void compoundsAndRoundsNormalPots() {
        assertEquals(198, RpsPayoutMath.compound(100, 1.98));
        assertEquals(2, RpsPayoutMath.compound(1, 1.98));
    }

    @Test
    void saturatesInsteadOfWrappingLargePotsNegative() {
        assertEquals(Integer.MAX_VALUE, RpsPayoutMath.compound(2_000_000_000, 1.98));
        assertEquals(Integer.MAX_VALUE, RpsPayoutMath.compound(Integer.MAX_VALUE, 1.98));
    }
}
