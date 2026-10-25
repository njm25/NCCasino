package org.nc.nccasino.games.DragonDescent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cash-out or a dragon hit schedules the next round's reset two seconds later.
 * If the player closed the window in between, that reset used to run anyway and,
 * with Rebet on, take the stake into a session nothing settles any more.
 */
class DragonResetAfterCloseTest {

    @Test
    void anOpenSessionResets() {
        assertTrue(DragonClient.mayReset(true, false, false));
    }

    @Test
    void aClosedOrDisconnectedSessionNeverResets() {
        assertFalse(DragonClient.mayReset(true, true, false));
        assertFalse(DragonClient.mayReset(true, false, true));
    }

    @Test
    void aSessionWithoutAnInventoryNeverResets() {
        assertFalse(DragonClient.mayReset(false, false, false));
    }
}
