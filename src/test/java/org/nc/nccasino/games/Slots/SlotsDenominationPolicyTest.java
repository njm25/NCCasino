package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsDenominationPolicyTest {

    private static final int LINES = 5;
    private static final SlotsPaytable PAYTABLE = SlotsPaytable.forConfig(3, 0.03);

    /** Largest per-line wager whose worst case still fits under the item-mode ceiling. */
    private static long ceilingUnits() {
        return (long) Math.floor(SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * LINES));
    }

    @Test
    @DisplayName("vault mode allows any finite positive denomination")
    void vaultModeAllowsAnyPositiveDenomination() {
        assertTrue(SlotsDenominationPolicy.isAllowed(1, LINES, false, PAYTABLE));
        assertTrue(SlotsDenominationPolicy.isAllowed(100_000, LINES, false, PAYTABLE));
    }

    @Test
    @DisplayName("non-positive and non-finite denominations are always rejected")
    void invalidDenominationsRejected() {
        assertFalse(SlotsDenominationPolicy.isAllowed(0, LINES, false, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(-5, LINES, false, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(0.4, LINES, false, PAYTABLE), "rounds to zero units");
        assertFalse(SlotsDenominationPolicy.isAllowed(Double.NaN, LINES, false, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(Double.POSITIVE_INFINITY, LINES, false, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(10, LINES, false, null), "a missing paytable is never safe");
    }

    @Test
    @DisplayName("item mode enforces the payout ceiling exactly at the boundary")
    void itemModeEnforcesCeiling() {
        long safe = ceilingUnits();
        assertTrue(safe > 0, "at least one denomination must remain playable in item mode");
        assertTrue(SlotsDenominationPolicy.isAllowed(safe, LINES, true, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(safe + 1, LINES, true, PAYTABLE));
        // The same wager is fine when payouts are not item-bound.
        assertTrue(SlotsDenominationPolicy.isAllowed(safe + 1, LINES, false, PAYTABLE));
    }

    @Test
    @DisplayName("more active lines lower the safe denomination in item mode")
    void moreLinesTightenTheCeiling() {
        long safeAtFive = (long) Math.floor(SlotsMath.MAX_ITEM_MODE_PAYOUT / (PAYTABLE.maxLineMultiplier() * 5));
        assertTrue(SlotsDenominationPolicy.isAllowed(safeAtFive, 5, true, PAYTABLE));
        assertFalse(SlotsDenominationPolicy.isAllowed(safeAtFive, 9, true, PAYTABLE),
            "the same denomination exposes more with nine lines live");
    }

    @Test
    @DisplayName("cycling skips unsafe denominations in the requested direction")
    void cyclingSkipsUnsafeDenominations() {
        long safe = ceilingUnits();
        double[] denominations = {1, safe, safe * 1000, 2};

        // Forward from index 1 must skip the oversized entry at index 2.
        assertEquals(3, SlotsDenominationPolicy.nextAllowedIndex(denominations, 1, 1, LINES, true, PAYTABLE));
        // Backward from index 3 wraps past the oversized entry too.
        assertEquals(1, SlotsDenominationPolicy.nextAllowedIndex(denominations, 3, -1, LINES, true, PAYTABLE));
        // No movement requested.
        assertEquals(1, SlotsDenominationPolicy.nextAllowedIndex(denominations, 1, 0, LINES, true, PAYTABLE));
    }

    @Test
    @DisplayName("when nothing is safe the current index is retained")
    void retainsIndexWhenNothingIsSafe() {
        // Derived from the live ceiling rather than hardcoded: with the old
        // 10,000-item delivery limit any large literal was unsafe, but under
        // the precision ceiling that replaced it a fixed 1e12 is comfortably
        // playable, which quietly stopped this from testing what it claims.
        double justOver = ceilingUnits() + 1d;
        double[] denominations = {justOver, justOver * 2d};
        assertFalse(SlotsDenominationPolicy.isAllowed(denominations[0], LINES, true, PAYTABLE),
            "fixture must actually contain no safe denomination");
        assertFalse(SlotsDenominationPolicy.isAllowed(denominations[1], LINES, true, PAYTABLE));

        assertEquals(0, SlotsDenominationPolicy.nextAllowedIndex(denominations, 0, 1, LINES, true, PAYTABLE));
    }

    @Test
    @DisplayName("malformed inputs throw rather than silently misbehaving")
    void malformedInputsThrow() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsDenominationPolicy.nextAllowedIndex(new double[0], 0, 1, LINES, false, PAYTABLE));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsDenominationPolicy.nextAllowedIndex(null, 0, 1, LINES, false, PAYTABLE));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsDenominationPolicy.nextAllowedIndex(new double[] {1, 2}, 5, 1, LINES, false, PAYTABLE));
    }
}
