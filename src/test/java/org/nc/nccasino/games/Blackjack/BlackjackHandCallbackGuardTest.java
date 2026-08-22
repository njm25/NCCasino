package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.games.Blackjack.BlackjackHandCallbackGuard.ExpectedHandState;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * BlackjackInventory#resolveExpectedHand genuinely delegates to
 * {@link BlackjackHandCallbackGuard#matches} for hand-identity matching --
 * this exercises the exact scenarios Hit/Double/turn-timeout callbacks
 * depend on it for.
 */
class BlackjackHandCallbackGuardTest {

    private static BlackjackHand hand() {
        return new BlackjackHand(10);
    }

    @Test
    void matchesWhenIdAndGenerationAreExactlyAsCaptured() {
        BlackjackHand hand = hand();
        assertTrue(BlackjackHandCallbackGuard.matches(hand, hand.getHandId(), hand.getHandGeneration()));
    }

    @Test
    void nullHandNeverMatches() {
        assertFalse(BlackjackHandCallbackGuard.matches(null, 1L, 0));
    }

    @Test
    void wrongHandIdNeverMatchesEvenWithTheRightGeneration() {
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.matches(hand, hand.getHandId() + 999, hand.getHandGeneration()));
    }

    @Test
    void earlierHitCallbackAfterALaterActionOnTheSameHandIsStale() {
        // Simulates: Hit is clicked (generation captured for the render
        // step), but by the time it fires, a *later* action has already
        // advanced the same hand (e.g. a Double resolved it) -- same
        // handId, but the captured generation is now behind.
        BlackjackHand hand = hand();
        long handId = hand.getHandId();
        int capturedAtEarlierHit = hand.getHandGeneration();

        hand.addCard(new Card(Suit.SPADES, Rank.KING)); // the later action's own mutation
        hand.setDone(true); // and it finishes the hand

        assertFalse(BlackjackHandCallbackGuard.matches(hand, handId, capturedAtEarlierHit),
            "a stale earlier-Hit callback must never match a hand a later action has already superseded");
        assertTrue(BlackjackHandCallbackGuard.matches(hand, handId, hand.getHandGeneration()),
            "the hand's own current generation must still match itself");
    }

    @Test
    void sameHandIdButStaleGenerationNeverMatches() {
        BlackjackHand hand = hand();
        long handId = hand.getHandId();
        int staleGeneration = hand.getHandGeneration();
        hand.bumpGeneration(); // any advance at all
        assertFalse(BlackjackHandCallbackGuard.matches(hand, handId, staleGeneration));
    }

    @Test
    void resplitShiftingListIndexNeverConfusesTwoDistinctHandsSameGenerationValue() {
        // Two independently-created hands can share the same numeric
        // handGeneration (both start at 0) but must never be treated as
        // interchangeable -- only handId identity may ever match, index
        // position must never matter.
        BlackjackHand original = hand();
        BlackjackHand sibling = hand(); // e.g. inserted by a resplit, shifting what index 1 means
        List<BlackjackHand> hands = List.of(original, sibling);

        BlackjackHand resolvedByOriginalId = BlackjackSplitQueue.findById(hands, original.getHandId());
        assertTrue(BlackjackHandCallbackGuard.matches(resolvedByOriginalId, original.getHandId(), original.getHandGeneration()));
        assertFalse(BlackjackHandCallbackGuard.matches(resolvedByOriginalId, sibling.getHandId(), sibling.getHandGeneration()));
    }

    // --- isExpectedActiveHand: the complete expected-state contract ---

    @Test
    void expectedActiveHandValidWhenEverythingMatchesActionable() {
        BlackjackHand hand = hand();
        assertTrue(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, true, true, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void matchingIdAndGenerationButNoLongerTheActiveHandIsRejected() {
        // The caller is responsible for resolving "candidate" as
        // activeHandIndex's own hand -- if a hand with the right id and
        // generation exists elsewhere in the queue but isn't the one
        // passed in as the active candidate (e.g. it became a completed,
        // no-longer-active sibling), this must reject it. Simulated here by
        // passing null as the candidate, exactly what the controller passes
        // when its own activeHandIndex lookup didn't land on this handId.
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            null, hand.getHandId(), hand.getHandGeneration(), true, true, true, true, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void seatedButNoLongerCurrentPlayerIsRejected() {
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, false, true, true, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void noLongerSeatedIsRejected() {
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), false, true, true, true, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void matchingHandButPhaseIsInsuranceOrDealerRatherThanActivePlayerTurnIsRejected() {
        // The table moved into insurance or the dealer's own turn around a
        // still-technically-matching hand -- Hit/Double/timeout callbacks
        // must never fire outside the actionable player-turn phase.
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, false, true, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void timerCallbackAgainstProcessingRatherThanActionableStateIsRejected() {
        // ExpectedHandState.ACTIONABLE (the turn timer) must reject a hand
        // that's mid-processing (playerTurnActive=false) even if everything
        // else matches.
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, true, false, ExpectedHandState.ACTIONABLE
        ));
    }

    @Test
    void processingCallbackAgainstActionableRatherThanProcessingStateIsRejected() {
        // The other direction of the same bug this enum replaced a bare
        // boolean to fix: a Hit/Double render/eval callback scheduled while
        // the decision was "processing" must be rejected if, by the time it
        // fires, the decision has already become genuinely actionable again
        // (e.g. a stale render step racing a fresh decision) -- it must not
        // be treated as still-valid "processing" just because
        // ExpectedHandState.PROCESSING doesn't literally require
        // playerTurnActive to be true.
        BlackjackHand hand = hand();
        assertFalse(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, true, true, ExpectedHandState.PROCESSING
        ));
    }

    @Test
    void hitRenderCallbackDuringProcessingIsStillValidWhenProcessingExpected() {
        // Hit/Double's own render/eval callbacks legitimately fire while
        // playerTurnActive is false (ExpectedHandState.PROCESSING) --
        // processing is the expected state for them, not a staleness signal.
        BlackjackHand hand = hand();
        assertTrue(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, true, false, ExpectedHandState.PROCESSING
        ));
    }

    @Test
    void timerCallbackAgainstGenuinelyActionableStateIsValid() {
        BlackjackHand hand = hand();
        assertTrue(BlackjackHandCallbackGuard.isExpectedActiveHand(
            hand, hand.getHandId(), hand.getHandGeneration(), true, true, true, true, ExpectedHandState.ACTIONABLE
        ));
    }
}
