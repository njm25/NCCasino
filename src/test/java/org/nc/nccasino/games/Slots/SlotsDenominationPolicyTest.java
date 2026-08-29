package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsDenominationPolicyTest {

    private static final double[] DEFAULTS = {1, 5, 10, 25, 50};

    @Test
    void itemModeSkipsDefaultDenominationsAboveTheProvisionalExposureCeiling() {
        assertEquals(0, SlotsDenominationPolicy.nextAllowedIndex(DEFAULTS, 2, 1, true));
        assertEquals(2, SlotsDenominationPolicy.nextAllowedIndex(DEFAULTS, 0, -1, true));
    }

    @Test
    void vaultModeKeepsEveryPositiveDefaultDenominationAvailable() {
        assertEquals(3, SlotsDenominationPolicy.nextAllowedIndex(DEFAULTS, 2, 1, false));
        assertEquals(4, SlotsDenominationPolicy.nextAllowedIndex(DEFAULTS, 0, -1, false));
    }

    @Test
    void validityMatchesTheCurrentPrototypeMaximumExposure() {
        assertTrue(SlotsDenominationPolicy.isAllowed(19, true));
        assertFalse(SlotsDenominationPolicy.isAllowed(20, true));
        assertTrue(SlotsDenominationPolicy.isAllowed(50, false));
    }

    @Test
    void invalidAndNonPositiveDenominationsAreNeverSelectable() {
        assertFalse(SlotsDenominationPolicy.isAllowed(0, false));
        assertFalse(SlotsDenominationPolicy.isAllowed(-1, false));
        assertFalse(SlotsDenominationPolicy.isAllowed(Double.NaN, false));
        assertFalse(SlotsDenominationPolicy.isAllowed(Double.POSITIVE_INFINITY, false));
    }

    @Test
    void retainsCurrentIndexWhenNoConfiguredDenominationIsSafe() {
        assertEquals(1, SlotsDenominationPolicy.nextAllowedIndex(new double[] {25, 50}, 1, 1, true));
    }
}
