package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // --- findById: never trust a captured list index across ticks ---

    @Test
    void findByIdResolvesTheMatchingHand() {
        BlackjackHand a = hand();
        BlackjackHand b = hand();
        List<BlackjackHand> hands = List.of(a, b);
        assertSame(b, BlackjackSplitQueue.findById(hands, b.getHandId()));
    }

    @Test
    void findByIdReturnsNullWhenNoHandMatches() {
        List<BlackjackHand> hands = List.of(hand(), hand());
        assertNull(BlackjackSplitQueue.findById(hands, -1L));
    }

    @Test
    void findByIdReturnsNullForANullList() {
        assertNull(BlackjackSplitQueue.findById(null, 1L));
    }

    // --- Turn-timeout depth-first advance: timing out hand 1 of several
    // must activate the pending sibling, never skip straight to the next
    // player; only timing out the final hand does that. ---

    @Test
    void timingOutTheFirstOfTwoSplitHandsActivatesThePendingSibling() {
        BlackjackHand first = hand();
        BlackjackHand second = hand(); // still pending
        List<BlackjackHand> hands = List.of(first, second);

        // autoStandOnTurnTimeout marks the active hand done, then looks for
        // the next actionable one from its position -- exactly this call.
        first.setDone(true);
        assertEquals(1, BlackjackSplitQueue.nextActionableIndex(hands, 0), "the pending second hand must activate next, not the next player");
    }

    @Test
    void timingOutTheFinalSplitHandLeavesNoNextActionableIndex() {
        BlackjackHand first = hand();
        first.setDone(true); // already resolved earlier
        BlackjackHand second = hand();
        List<BlackjackHand> hands = List.of(first, second);

        second.setDone(true); // timeout resolves the last hand
        assertEquals(-1, BlackjackSplitQueue.nextActionableIndex(hands, 1), "with every hand resolved, the table must advance to the next player");
    }

    @Test
    void timingOutAMiddleSplitHandOfThreeActivatesTheNextPendingOne() {
        BlackjackHand first = hand();
        first.setDone(true);
        BlackjackHand second = hand();
        BlackjackHand third = hand();
        List<BlackjackHand> hands = List.of(first, second, third);

        second.setDone(true); // timeout resolves the middle hand
        assertEquals(2, BlackjackSplitQueue.nextActionableIndex(hands, 1));
    }
}
