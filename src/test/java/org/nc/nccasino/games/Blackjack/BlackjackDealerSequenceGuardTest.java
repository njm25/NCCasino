package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the reproduction the audit called out: the
 * dealer schedules a delayed finishGame, the last player leaves
 * (cancelling/resetting the table -- roundGeneration and dealerSequenceToken
 * both bump), another player reseats and commits a new wager (gameActive
 * becomes true again) before the stale callback fires. Without this guard,
 * that callback would settle/reset the new round's fresh state using an
 * old round's captured identity.
 *
 * <p>roundGeneration alone isn't quite enough: two dealer sequences could
 * in principle be initiated within the very same round generation. The
 * dedicated dealerSequenceToken, bumped every time a dealer sequence
 * begins, distinguishes those two sequences even when they share an
 * identical round generation.
 */
class BlackjackDealerSequenceGuardTest {

    @Test
    void freshCallbackWithMatchingGenerationTokenAndActiveGameIsNotStale() {
        assertFalse(BlackjackDealerSequenceGuard.isStale(5L, 5L, 1, 1, true));
    }

    @Test
    void resetBetweenScheduleAndFireMakesItStale() {
        // resetGame/cancelGame/delete all bump roundGeneration (and
        // dealerSequenceToken) -- a callback captured before that bump must
        // never fire against the new state.
        assertTrue(BlackjackDealerSequenceGuard.isStale(5L, 6L, 1, 1, true));
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
        assertTrue(BlackjackDealerSequenceGuard.isStale(capturedAtRound5, liveRoundAfterReseat, 1, 3, gameActiveAgain));
    }

    @Test
    void gameNoLongerActiveMakesItStaleEvenWithMatchingGenerationAndToken() {
        // The round ended (finishGame -> resetGame) without a generation
        // change being the only signal -- gameActive itself must gate too.
        assertTrue(BlackjackDealerSequenceGuard.isStale(5L, 5L, 1, 1, false));
    }

    // --- Second dealer sequence within the very same round generation ---

    @Test
    void secondDealerSequenceInTheSameRoundInvalidatesTheFirst() {
        // Same round generation throughout -- only the dealer-sequence
        // token distinguishes the two sequences. The first sequence's own
        // captured token (1) must go stale the instant the second begins
        // (token bumps to 2), even though roundGeneration never changed.
        long sameRound = 5L;
        int firstSequenceToken = 1;
        int liveTokenAfterSecondSequenceBegins = 2;
        assertTrue(BlackjackDealerSequenceGuard.isStale(sameRound, sameRound, firstSequenceToken, liveTokenAfterSecondSequenceBegins, true));
    }

    @Test
    void theSecondDealerSequencesOwnCallbacksAreNotStale() {
        long sameRound = 5L;
        int secondSequenceToken = 2;
        assertFalse(BlackjackDealerSequenceGuard.isStale(sameRound, sameRound, secondSequenceToken, secondSequenceToken, true));
    }
}
