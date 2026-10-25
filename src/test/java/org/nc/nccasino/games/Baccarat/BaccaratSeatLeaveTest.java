package org.nc.nccasino.games.Baccarat;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.entities.Server.GameState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaving a Baccarat chair refunds every bet. Since 1.4.9 a player could stand
 * up after the cards showed a losing hand and get the whole stake back, because
 * settlement only pays the bets still on the table.
 */
class BaccaratSeatLeaveTest {

    @Test
    void aPlayerWithBetsCannotStandUpWhileTheHandIsDealt() {
        assertFalse(BaccaratServer.mayLeaveSeat(GameState.RUNNING, true));
    }

    @Test
    void standingUpIsFineWhileBetsAreOpen() {
        assertTrue(BaccaratServer.mayLeaveSeat(GameState.WAITING, true));
        assertTrue(BaccaratServer.mayLeaveSeat(GameState.PAUSED, true));
    }

    @Test
    void aPlayerWithoutBetsCanAlwaysStandUp() {
        assertTrue(BaccaratServer.mayLeaveSeat(GameState.RUNNING, false));
    }
}
