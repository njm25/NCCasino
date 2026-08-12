package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void flagsPotsThatWouldNeedClampingIfCompoundedAgain() {
        // 5e15 * 1.98 = 9.9e15, past MAX_SAFE_POT (~9.0072e15) -- offering
        // another round from this pot would produce a win compound() could
        // only pay by clamping, so callers must stop here instead.
        assertTrue(CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(5_000_000_000_000_000L, 1.98));
        // 4e15 * 1.98 = 7.92e15, comfortably under the ceiling -- safe to
        // offer one more round.
        assertFalse(CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(4_000_000_000_000_000L, 1.98));
        assertFalse(CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(100, 1.98));
        assertTrue(CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(CoinFlipPayoutMath.MAX_SAFE_POT, 1.98));
    }

    @Test
    void everyRoundSafeToOfferCompoundsWithoutEverClamping() {
        // The whole point of wouldExceedSafeMaxIfCompoundedAgain: any pot it
        // clears must compound to its true, un-clamped value -- compound()
        // should never actually need its own internal clamp for a pot this
        // gated on.
        long pot = 4_000_000_000_000_000L;
        assertFalse(CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(pot, 1.98));
        long compounded = CoinFlipPayoutMath.compound(pot, 1.98);
        assertEquals(7_920_000_000_000_000L, compounded);
        assertTrue(compounded < CoinFlipPayoutMath.MAX_SAFE_POT);
    }
}
