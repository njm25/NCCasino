package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotsPaylineTest {

    @Test
    void allFiveLinesAreDefinedInOrder() {
        assertEquals(5, SlotsPayline.ALL.length);
    }

    @Test
    void topLineIsRowZero() {
        assertArrayEquals(new int[][] {{0, 0}, {0, 1}, {0, 2}}, SlotsPayline.TOP.cells());
    }

    @Test
    void middleLineIsRowOne() {
        assertArrayEquals(new int[][] {{1, 0}, {1, 1}, {1, 2}}, SlotsPayline.MIDDLE.cells());
    }

    @Test
    void bottomLineIsRowTwo() {
        assertArrayEquals(new int[][] {{2, 0}, {2, 1}, {2, 2}}, SlotsPayline.BOTTOM.cells());
    }

    @Test
    void downDiagonalRunsTopLeftToBottomRight() {
        assertArrayEquals(new int[][] {{0, 0}, {1, 1}, {2, 2}}, SlotsPayline.DOWN_DIAGONAL.cells());
    }

    @Test
    void upDiagonalRunsBottomLeftToTopRight() {
        assertArrayEquals(new int[][] {{2, 0}, {1, 1}, {0, 2}}, SlotsPayline.UP_DIAGONAL.cells());
    }

    @Test
    void cellsReturnsADefensiveCopy() {
        int[][] cells = SlotsPayline.TOP.cells();
        cells[0][0] = 99;
        assertArrayEquals(new int[][] {{0, 0}, {0, 1}, {0, 2}}, SlotsPayline.TOP.cells());
    }
}
