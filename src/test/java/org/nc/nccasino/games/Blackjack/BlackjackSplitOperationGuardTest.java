package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Every scheduled split-animation step must prove it still belongs to the
 * exact same split operation before mutating or rendering anything -- round
 * generation, phase, the acting player's own seat, and both hands still
 * present by stable handId, never a captured list index or object
 * reference. Distinguishes the two departure scenarios the plan calls out:
 * a random <em>other</em> viewer closing their inventory must never
 * invalidate this guard, but the <em>acting</em> player themselves leaving
 * their seat must.
 */
class BlackjackSplitOperationGuardTest {

    private static final UUID ACTING_PLAYER = UUID.randomUUID();
    private static final UUID OTHER_VIEWER = UUID.randomUUID();

    private static BlackjackHand hand() {
        return new BlackjackHand(10);
    }

    private static BlackjackSplitOperationGuard guardFor(int seatSlot, long roundGeneration, BlackjackHand original, BlackjackHand sibling) {
        return new BlackjackSplitOperationGuard(
            ACTING_PLAYER, seatSlot, roundGeneration, BlackjackFrame.Phase.ACTIVE,
            original.getHandId(), sibling.getHandId()
        );
    }

    @Test
    void validWhenEverythingStillMatches() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        assertTrue(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
    }

    @Test
    void invalidWhenRoundGenerationAdvancesUnderneathIt() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        // A table-wide reset/cancel bumps roundGeneration -- must invalidate.
        assertFalse(guard.isValid(6L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
    }

    @Test
    void invalidWhenPhaseChanges() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.INSURANCE, 9, original, sibling));
    }

    // --- Acting-player departure: the seat/hands come back null/mismatched ---

    @Test
    void invalidWhenActingPlayersSeatIsNoLongerPresent() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        // removePlayerData clears playerSeats for a departed player -- the
        // live lookup comes back null.
        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, null, original, sibling));
    }

    @Test
    void invalidWhenActingPlayerResatToADifferentSeat() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 18, original, sibling));
    }

    @Test
    void invalidWhenTheOriginalHandNoLongerExists() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        // Leaving clears playerHands entirely -- looking the hand up by id
        // (BlackjackSplitQueue.findById) comes back null.
        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, null, sibling));
    }

    @Test
    void invalidWhenTheSiblingHandNoLongerExists() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, null));
    }

    @Test
    void invalidWhenTheOriginalHandIdNoLongerMatches() {
        // A hand with the same list position but a different identity (e.g.
        // a completely different round's leftover object) must never be
        // treated as the same split -- captured identity is the handId,
        // never a position/reference.
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        BlackjackHand impostor = hand();
        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, impostor, sibling));
    }

    // --- Unrelated viewer departure: guard is completely unaffected ---

    @Test
    void unrelatedViewerClosingNeverInvalidatesTheGuard() {
        // A random other viewer closing their inventory touches none of the
        // acting player's own seat/hand state -- the guard must still be
        // valid against the same live values as before.
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        // Simulate "OTHER_VIEWER closed their view" by simply re-checking
        // against the acting player's unchanged live state -- nothing about
        // OTHER_VIEWER ever enters this guard's own identity at all.
        assertTrue(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
        assertTrue(guard.getPlayerId().equals(ACTING_PLAYER));
        assertFalse(guard.getPlayerId().equals(OTHER_VIEWER));
    }

    @Test
    void captureExposesTheExactIdentityItWasBuiltWith() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        org.junit.jupiter.api.Assertions.assertEquals(ACTING_PLAYER, guard.getPlayerId());
        org.junit.jupiter.api.Assertions.assertEquals(9, guard.getSeatSlot());
        org.junit.jupiter.api.Assertions.assertEquals(original.getHandId(), guard.getOriginalHandId());
        org.junit.jupiter.api.Assertions.assertEquals(sibling.getHandId(), guard.getSiblingHandId());
    }
}
