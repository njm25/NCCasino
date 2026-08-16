package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
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
}
