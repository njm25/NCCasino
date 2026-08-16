package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the reproduction the audit called out: the
 * dealer schedules a delayed finishGame, the last player leaves
 * (cancelling/resetting the table -- roundGeneration bumps), another
 * player reseats and commits a new wager (gameActive becomes true again)
 * before the stale callback fires. Without this guard, that callback would
 * settle/reset the new round's fresh state using an old round's captured
 * identity.
 */
class BlackjackDealerSequenceGuardTest {

    @Test
    void freshCallbackWithMatchingGenerationAndActiveGameIsNotStale() {
        assertFalse(BlackjackDealerSequenceGuard.isStale(5L, 5L, true));
    }

    @Test
    void resetBetweenScheduleAndFireMakesItStale() {
        // resetGame/cancelGame/delete all bump roundGeneration -- a callback
        // captured before that bump must never fire against the new state.
        assertTrue(BlackjackDealerSequenceGuard.isStale(5L, 6L, true));
    }

    @Test
    void lastPlayerLeavesThenNewRoundReseatsBeforeStaleCallbackFires() {
        // The exact reproduction: round 5's dealer sequence is mid-flight,
        // the last player leaves (cancelGame bumps to round 6, gameActive
        // false), then someone reseats and a new round deals (gameActive
        // true again, round 7). The stale round-5 callback must still be
        // rejected even though gameActive is true again.
        long capturedAtRound5 = 5L;
        long liveRoundAfterReseat = 7L;
        boolean gameActiveAgain = true;
        assertTrue(BlackjackDealerSequenceGuard.isStale(capturedAtRound5, liveRoundAfterReseat, gameActiveAgain));
    }

    @Test
    void gameNoLongerActiveMakesItStaleEvenWithMatchingGeneration() {
        // The round ended (finishGame -> resetGame) without a generation
        // change being the only signal -- gameActive itself must gate too.
        assertTrue(BlackjackDealerSequenceGuard.isStale(5L, 5L, false));
    }
}
