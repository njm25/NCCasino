package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoinFlipPayoutMathTest {

    @Test
    void compoundsAndRoundsNormalPots() {
        assertEquals(198L, CoinFlipPayoutMath.compound(100, 1.98));
        assertEquals(2L, CoinFlipPayoutMath.compound(1, 1.98));
    }

    @Test
    void doesNotClampAtTheOldIntBoundary() {
        // A pot well past Integer.MAX_VALUE (exactly the pot-doubling
        // overflow this type was widened to fix) must compound exactly, not
        // saturate the way the old int-based version did -- saturating here
        // would silently destroy real, already-withdrawn currency.
        assertEquals(3_960_000_000L, CoinFlipPayoutMath.compound(2_000_000_000L, 1.98));
        assertEquals(4_252_017_621L, CoinFlipPayoutMath.compound(Integer.MAX_VALUE, 1.98));
    }

    @Test
    void capsAtTheDoubleSafeIntegerBoundaryInsteadOfWrapping() {
        // 2^53 is the real ceiling: every payout eventually crosses a
        // double-typed boundary (creditPlayer, PendingPayout, Vault), so a
        // chain that would compound past it is capped here rather than
        // being handed a value that comes back rounded on the other side.
        assertEquals(CoinFlipPayoutMath.MAX_SAFE_POT, CoinFlipPayoutMath.compound(CoinFlipPayoutMath.MAX_SAFE_POT, 1.98));
        assertEquals(CoinFlipPayoutMath.MAX_SAFE_POT, CoinFlipPayoutMath.compound(Long.MAX_VALUE / 2, 1.98));
    }

    @Test
    void nonPositivePotsCompoundToZero() {
        assertEquals(0L, CoinFlipPayoutMath.compound(0, 1.98));
        assertEquals(0L, CoinFlipPayoutMath.compound(-5, 1.98));
    }
}
