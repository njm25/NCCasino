package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Depth-first hand-queue ordering: a new sibling is always inserted
 * immediately after the hand it was split from, and the current hand
 * finishes fully before the queue advances -- see the table redesign
 * plan's "Depth-first split processing" section.
 */
class BlackjackSplitQueueTest {

    private static BlackjackHand hand() {
        return new BlackjackHand(10);
    }

    @Test
    void siblingInsertsImmediatelyAfterCurrentIndex() {
        BlackjackHand first = hand();
        BlackjackHand second = hand();
        List<BlackjackHand> hands = new ArrayList<>(List.of(first, second));

        BlackjackHand sibling = hand();
        BlackjackSplitQueue.insertSiblingAfterCurrent(hands, 0, sibling);

        assertEquals(List.of(first, sibling, second), hands);
    }

    @Test
    void repeatedSplittingPreservesDepthFirstOrder() {
        // Split hand A -> [A, A'] ; then split A again -> [A, A'', A'] ;
        // the newest sibling always lands directly after the hand it came
        // from, never appended to the end of the whole queue.
        BlackjackHand a = hand();
        List<BlackjackHand> hands = new ArrayList<>(List.of(a));

        BlackjackHand aPrime = hand();
        BlackjackSplitQueue.insertSiblingAfterCurrent(hands, 0, aPrime);
        assertEquals(List.of(a, aPrime), hands);

        BlackjackHand aDoublePrime = hand();
        BlackjackSplitQueue.insertSiblingAfterCurrent(hands, 0, aDoublePrime); // resplitting a, still at index 0
        assertEquals(List.of(a, aDoublePrime, aPrime), hands);
    }

    @Test
    void resplittingASiblingInsertsAfterThatSiblingsOwnPosition() {
        BlackjackHand a = hand();
        BlackjackHand b = hand();
        List<BlackjackHand> hands = new ArrayList<>(List.of(a, b));

        // Now resplit b (index 1).
        BlackjackHand bPrime = hand();
        BlackjackSplitQueue.insertSiblingAfterCurrent(hands, 1, bPrime);
        assertEquals(List.of(a, b, bPrime), hands);
    }

    @Test
    void nextActionableIndexSkipsDoneHandsAndFindsThePendingOne() {
        BlackjackHand a = hand();
        a.setDone(true);
        BlackjackHand b = hand(); // pending
        BlackjackHand c = hand();
        c.setDone(true);
        List<BlackjackHand> hands = List.of(a, b, c);

        assertEquals(1, BlackjackSplitQueue.nextActionableIndex(hands, 0));
    }

    @Test
    void nextActionableIndexReturnsMinusOneWhenEveryRemainingHandIsDone() {
        BlackjackHand a = hand();
        BlackjackHand b = hand();
        b.setDone(true);
        List<BlackjackHand> hands = List.of(a, b);

        assertEquals(-1, BlackjackSplitQueue.nextActionableIndex(hands, 0));
    }

    @Test
    void nextActionableIndexNeverLooksBeforeCurrentIndex() {
        BlackjackHand a = hand(); // pending, but before currentIndex
        BlackjackHand b = hand();
        b.setDone(true);
        List<BlackjackHand> hands = List.of(a, b);

        assertEquals(-1, BlackjackSplitQueue.nextActionableIndex(hands, 1));
    }

    @Test
    void insertedSiblingIsTheSameInstancePassedIn() {
        List<BlackjackHand> hands = new ArrayList<>(List.of(hand()));
        BlackjackHand sibling = hand();
        BlackjackSplitQueue.insertSiblingAfterCurrent(hands, 0, sibling);
        assertSame(sibling, hands.get(1));
    }
}
