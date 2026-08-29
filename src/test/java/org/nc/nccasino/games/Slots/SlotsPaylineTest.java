package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsPaylineTest {

    /**
     * The invariant the whole configurable-house-edge design rests on. If any
     * line ever visited a column twice (or skipped one), that line's
     * run-length distribution would differ from the others and the machine's
     * RTP would stop matching the configured edge.
     */
    @Test
    @DisplayName("every payline visits every column exactly once")
    void everyPaylineVisitsEveryColumnExactlyOnce() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (SlotsPayline payline : SlotsPayline.ALL) {
                int[][] cells = payline.cells(columns);
                assertEquals(columns, cells.length,
                    payline + " must have one cell per column at width " + columns);

                Set<Integer> visitedColumns = new HashSet<>();
                for (int i = 0; i < cells.length; i++) {
                    assertEquals(i, cells[i][1], payline + " cell " + i + " must sit in column " + i);
                    visitedColumns.add(cells[i][1]);
                }
                assertEquals(columns, visitedColumns.size(),
                    payline + " must touch " + columns + " distinct columns");
            }
        }
    }

    @Test
    @DisplayName("every payline stays inside the grid at every width")
    void paylinesStayInsideTheGrid() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (SlotsPayline payline : SlotsPayline.ALL) {
                for (int col = 0; col < columns; col++) {
                    int row = payline.rowAt(col, columns);
                    assertTrue(row >= 0 && row < SlotsGeometry.ROWS,
                        payline + " row " + row + " out of range at column " + col + ", width " + columns);
                }
            }
        }
    }

    @Test
    @DisplayName("the first five lines reproduce the classic three-column layout")
    void firstFiveLinesAreTheClassicSet() {
        SlotsPayline[] active = SlotsPayline.active(5);
        assertEquals(5, active.length);
        assertEquals(SlotsPayline.MIDDLE, active[0], "line 1 is the centre line on a real machine");
        assertEquals(SlotsPayline.TOP, active[1]);
        assertEquals(SlotsPayline.BOTTOM, active[2]);
        assertEquals(SlotsPayline.DIAGONAL_DOWN, active[3]);
        assertEquals(SlotsPayline.DIAGONAL_UP, active[4]);

        assertArrayRows(SlotsPayline.MIDDLE, 3, 1, 1, 1);
        assertArrayRows(SlotsPayline.TOP, 3, 0, 0, 0);
        assertArrayRows(SlotsPayline.BOTTOM, 3, 2, 2, 2);
        assertArrayRows(SlotsPayline.DIAGONAL_DOWN, 3, 0, 1, 2);
        assertArrayRows(SlotsPayline.DIAGONAL_UP, 3, 2, 1, 0);
    }

    @Test
    @DisplayName("symmetric shapes turn on the exact centre column")
    void symmetricShapesPeakAtTheCentre() {
        // Odd widths guarantee a true centre, which is why even widths are rejected.
        assertArrayRows(SlotsPayline.VALLEY, 5, 0, 1, 2, 1, 0);
        assertArrayRows(SlotsPayline.PEAK, 5, 2, 1, 0, 1, 2);
        assertEquals(2, SlotsPayline.VALLEY.rowAt(3, 7), "width 7 valley bottoms out at the centre column");
        assertEquals(0, SlotsPayline.PEAK.rowAt(3, 7), "width 7 peak tops out at the centre column");
    }

    @Test
    @DisplayName("all nine shapes stay distinct at every width")
    void shapesAreDistinct() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            Set<String> seen = new HashSet<>();
            for (SlotsPayline payline : SlotsPayline.ALL) {
                StringBuilder signature = new StringBuilder();
                for (int col = 0; col < columns; col++) {
                    signature.append(payline.rowAt(col, columns));
                }
                assertTrue(seen.add(signature.toString()),
                    payline + " duplicates another line's shape at width " + columns + ": " + signature);
            }
            assertEquals(SlotsPayline.MAX_LINES, seen.size());
        }
    }

    @Test
    @DisplayName("line count is clamped into range")
    void lineCountIsClamped() {
        assertEquals(1, SlotsPayline.normalizeLineCount(0));
        assertEquals(1, SlotsPayline.normalizeLineCount(-3));
        assertEquals(SlotsPayline.MAX_LINES, SlotsPayline.normalizeLineCount(50));
        assertEquals(4, SlotsPayline.normalizeLineCount(4));
    }

    @Test
    @DisplayName("slot indices land inside the inventory and match the geometry")
    void slotsMatchGeometry() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (SlotsPayline payline : SlotsPayline.ALL) {
                int[] slots = payline.slots(columns);
                for (int col = 0; col < columns; col++) {
                    assertEquals(SlotsGeometry.gridSlot(columns, payline.rowAt(col, columns), col), slots[col]);
                    assertTrue(slots[col] >= 0 && slots[col] < SlotsGeometry.INVENTORY_SIZE);
                }
            }
        }
        assertNotEquals(SlotsPayline.TOP.slots(3)[0], SlotsPayline.TOP.slots(7)[0],
            "a wider machine starts further left");
    }

    private static void assertArrayRows(SlotsPayline payline, int columns, int... expectedRows) {
        for (int col = 0; col < columns; col++) {
            assertEquals(expectedRows[col], payline.rowAt(col, columns),
                payline + " at width " + columns + ", column " + col);
        }
    }
}
