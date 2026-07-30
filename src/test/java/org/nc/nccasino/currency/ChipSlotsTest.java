package org.nc.nccasino.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChipSlotsTest {

    @Test
    void assignsPositiveConfiguredValuesInAscendingSlotOrder() {
        Map<Integer, Double> slots = ChipSlots.assign(List.of(25.0, 1.0, 10.0, 5.0, 50.0));

        assertEquals(
            Map.of(47, 1.0, 48, 5.0, 49, 10.0, 50, 25.0, 51, 50.0),
            slots
        );
    }

    @Test
    void preservesVaultDecimalsWithoutReadingDisplayText() {
        Map<Integer, Double> slots = ChipSlots.assign(List.of(10.50, 0.25, 2.75));

        assertEquals(0.25, slots.get(47));
        assertEquals(2.75, slots.get(48));
        assertEquals(10.50, slots.get(49));
    }

    @Test
    void ignoresDisabledInvalidAndDuplicateDenominations() {
        Map<Integer, Double> slots = ChipSlots.assign(
            List.of(5.0, 0.0, -1.0, 5.0, Double.NaN, Double.POSITIVE_INFINITY)
        );

        assertEquals(Map.of(47, 5.0), slots);
    }

    @Test
    void recognizesOnlyTheFiveReservedChipSlots() {
        assertFalse(ChipSlots.isChipSlot(46));
        assertTrue(ChipSlots.isChipSlot(47));
        assertTrue(ChipSlots.isChipSlot(51));
        assertFalse(ChipSlots.isChipSlot(52));
    }
}
