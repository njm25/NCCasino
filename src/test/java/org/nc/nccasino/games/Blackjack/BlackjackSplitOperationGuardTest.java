package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * Every scheduled split-animation step must prove it still belongs to the
 * exact same split operation before mutating or rendering anything -- round
 * generation, phase, the acting player's own seat, and both hands still
 * present by stable handId <em>with the exact handGeneration this specific
 * step expects</em> (never a captured list index or object reference, and
 * never one generation value shared across every step -- the split's own
 * animation legitimately mutates each hand once, so each step needs its own
 * expected generation via {@link BlackjackSplitOperationGuard#withExpectedGenerations}).
 * Distinguishes the two departure scenarios the plan calls out: a random
 * <em>other</em> viewer closing their inventory must never invalidate this
 * guard, but the <em>acting</em> player themselves leaving their seat must.
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
            original.getHandId(), sibling.getHandId(),
            original.getHandGeneration(), sibling.getHandGeneration()
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

    // --- Per-step expected generation: the guard's whole point ---

    @Test
    void invalidWhenOriginalHandGenerationHasAdvancedPastWhatThisStepExpects() {
        // A resplit-before-auto-complete or any other legitimate mutation
        // that lands between when this step was scheduled and when it
        // fires must invalidate it -- exactly the "stale hand generation"
        // scenario.
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        original.addCard(new Card(Suit.SPADES, Rank.KING)); // advances original's generation past what the guard captured
        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
    }

    @Test
    void invalidWhenSiblingHandGenerationHasAdvancedPastWhatThisStepExpects() {
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guard = guardFor(9, 5L, original, sibling);

        sibling.addCard(new Card(Suit.SPADES, Rank.KING));
        assertFalse(guard.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
    }

    @Test
    void withExpectedGenerationsProducesAGuardValidAgainstTheAdvancedState() {
        // Mirrors runSplitAnimation's own step 2 -> step 3 handoff: after
        // the original hand's replacement card lands (legitimately bumping
        // its generation), the *next* step's guard must expect that new
        // generation, not the pre-deal one, while keeping the exact same
        // identity (player/seat/round/phase/both handIds).
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard beforeDeal = guardFor(9, 5L, original, sibling);

        original.addCard(new Card(Suit.SPADES, Rank.KING)); // the legitimate step-2 mutation
        assertFalse(beforeDeal.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling), "the old guard must now be stale");

        BlackjackSplitOperationGuard afterDeal = beforeDeal.withExpectedGenerations(original.getHandGeneration(), sibling.getHandGeneration());
        assertTrue(afterDeal.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling), "the derived guard must be valid against the exact post-mutation state");

        // Identity must carry forward unchanged.
        assertEquals(ACTING_PLAYER, afterDeal.getPlayerId());
        assertEquals(9, afterDeal.getSeatSlot());
        assertEquals(original.getHandId(), afterDeal.getOriginalHandId());
        assertEquals(sibling.getHandId(), afterDeal.getSiblingHandId());
    }

    @Test
    void chainedWithExpectedGenerationsMirrorsTheFullThreeStepSplitSequence() {
        // Step 1/2 guard (before either replacement card lands) -> step 3
        // guard (after the original's lands) -> step 4 guard (after the
        // sibling's too) -- each derived guard must only ever validate
        // against its own step's exact expected state.
        BlackjackHand original = hand();
        BlackjackHand sibling = hand();
        BlackjackSplitOperationGuard guardBeforeAnyDeal = guardFor(9, 5L, original, sibling);

        original.addCard(new Card(Suit.SPADES, Rank.KING));
        BlackjackSplitOperationGuard guardAfterOriginalDeal = guardBeforeAnyDeal.withExpectedGenerations(original.getHandGeneration(), sibling.getHandGeneration());
        assertFalse(guardBeforeAnyDeal.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
        assertTrue(guardAfterOriginalDeal.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));

        sibling.addCard(new Card(Suit.SPADES, Rank.QUEEN));
        BlackjackSplitOperationGuard guardAfterBothDealt = guardAfterOriginalDeal.withExpectedGenerations(original.getHandGeneration(), sibling.getHandGeneration());
        assertFalse(guardAfterOriginalDeal.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling), "stale once the sibling's own deal advances past it");
        assertTrue(guardAfterBothDealt.isValid(5L, BlackjackFrame.Phase.ACTIVE, 9, original, sibling));
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

        assertEquals(ACTING_PLAYER, guard.getPlayerId());
        assertEquals(9, guard.getSeatSlot());
        assertEquals(original.getHandId(), guard.getOriginalHandId());
        assertEquals(sibling.getHandId(), guard.getSiblingHandId());
        assertEquals(original.getHandGeneration(), guard.getExpectedOriginalHandGeneration());
        assertEquals(sibling.getHandGeneration(), guard.getExpectedSiblingHandGeneration());
    }
}
