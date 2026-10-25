package org.nc.nccasino.games.Mines;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Mines reset can still be scheduled when the player closes the table (after a
 * mine hit or a cash-out). With Rebet on it used to take the stake as soon as the
 * player had any Mines table open, including the fresh one they had just
 * reopened, and put it into the closed table, where nothing ever refunded it.
 */
class MinesRebetAfterCloseTest {

    private final Object table = new Object();

    @Test
    void theLiveTableTheStakeIsForMayTakeIt() {
        assertTrue(MinesTable.isRebetDebitAllowed(false, table, table));
    }

    @Test
    void aClosedTableNeverTakesARebet() {
        assertFalse(MinesTable.isRebetDebitAllowed(true, table, table));
    }

    @Test
    void anotherOpenTableIsNotThisOne() {
        assertFalse(MinesTable.isRebetDebitAllowed(false, new Object(), table));
    }

    @Test
    void nothingOpenMeansNoRebet() {
        assertFalse(MinesTable.isRebetDebitAllowed(false, null, table));
    }
}
