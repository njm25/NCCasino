package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsGeometryTest {

    @Test
    void supportedRowCountsAreExactly1And3And5() {
        assertArrayEquals(new int[] {1, 3, 5}, SlotsGeometry.supportedRowCounts());
        for (int rows : SlotsGeometry.supportedRowCounts()) {
            assertTrue(SlotsGeometry.isSupportedRowCount(rows));
        }
        assertFalse(SlotsGeometry.isSupportedRowCount(0));
        assertFalse(SlotsGeometry.isSupportedRowCount(2));
        assertFalse(SlotsGeometry.isSupportedRowCount(4));
        assertFalse(SlotsGeometry.isSupportedRowCount(6));
    }

    @Test
    void rowCountNormalizesToTheNearestSupportedValue() {
        assertEquals(1, SlotsGeometry.normalizeRowCount(-5));
        assertEquals(1, SlotsGeometry.normalizeRowCount(0));
        assertEquals(1, SlotsGeometry.normalizeRowCount(1));
        assertEquals(3, SlotsGeometry.normalizeRowCount(2));
        assertEquals(3, SlotsGeometry.normalizeRowCount(3));
        assertEquals(3, SlotsGeometry.normalizeRowCount(4));
        assertEquals(5, SlotsGeometry.normalizeRowCount(5));
        assertEquals(5, SlotsGeometry.normalizeRowCount(99));
    }

    @Test
    void everyHeightIsVerticallyCenteredInTheFiveRowCanvas() {
        assertEquals(2, SlotsGeometry.firstCanvasRow(1), "height 1 occupies inventory row 2");
        assertEquals(1, SlotsGeometry.firstCanvasRow(3), "height 3 occupies inventory rows 1-3");
        assertEquals(0, SlotsGeometry.firstCanvasRow(5), "height 5 occupies inventory rows 0-4");
    }

    @Test
    void allNineGeometriesMapInsideTheFortyFiveCellCanvas() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                int[] slots = SlotsGeometry.gridSlots(columns, rows);
                assertEquals(columns * rows, slots.length);

                Set<Integer> unique = new HashSet<>();
                for (int slot : slots) {
                    assertTrue(slot >= 0 && slot < 45,
                        "columns=" + columns + " rows=" + rows + ": slot " + slot + " must be in the 5x9 canvas");
                    unique.add(slot);
                }
                assertEquals(slots.length, unique.size(), "no duplicate slots in one geometry's grid");
            }
        }
    }

    @Test
    void reelSlotsAreOrderedTopToBottom() {
        for (int rows : SlotsGeometry.supportedRowCounts()) {
            int[] reel = SlotsGeometry.reelSlots(5, rows, 2);
            assertEquals(rows, reel.length);
            for (int i = 1; i < reel.length; i++) {
                assertTrue(reel[i] > reel[i - 1], "each row must sit strictly below the previous one");
            }
        }
    }

    @Test
    void gridSlotsAreNotReportedOutsideTheirOwnGeometry() {
        // A narrow, short machine's cells must not be misreported as part of
        // a wider/taller one's grid -- the gutters are real gutters.
        assertFalse(SlotsGeometry.isGridSlot(3, 1, SlotsGeometry.gridSlot(7, 5, 0, 0)));
    }

    @Test
    void invalidRowCountsAreRejectedByTheStrictApi() {
        assertThrows(IllegalArgumentException.class, () -> SlotsGeometry.requireSupportedRowCount(2));
        assertThrows(IllegalArgumentException.class, () -> SlotsGeometry.gridSlot(5, 2, 0, 0));
    }

    @Test
    void legacyThreeRowApiStillWorksUnchanged() {
        // The pre-redesign fixed-height overloads must keep behaving exactly
        // as before until every caller migrates to the row-aware ones.
        assertArrayEquals(
            SlotsGeometry.gridSlots(5, 3),
            SlotsGeometry.gridSlots(5));
        assertEquals(SlotsGeometry.gridSlot(5, 1, 2), SlotsGeometry.gridSlot(5, 3, 1, 2));
    }
}
