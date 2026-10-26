package org.nc.nccasino.games.Mines;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hand-edited default-mines of 25 or more was clamped only in the config, so
 * the table tried to place 25+ mines on 25 tiles and froze the server.
 */
class MinesConfigClampTest {

    @Test
    void placingMinesAlwaysLeavesASafeTile() {
        assertEquals(24, MinesTable.placeableMines(30, 25));
        assertEquals(24, MinesTable.placeableMines(25, 25));
        assertEquals(3, MinesTable.placeableMines(3, 25));
        assertEquals(0, MinesTable.placeableMines(-2, 25));
    }
}
