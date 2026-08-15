package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * Characterizes BlackjackHand: a pure, locale-neutral per-hand state
 * carrier with stable identity ({@code handId}) and an advancing generation
 * counter ({@code handGeneration}) -- see the table redesign plan's "Stable
 * hand identity" section for why both are needed together (a scheduled
 * callback must validate roundGeneration + handId + handGeneration +
 * expected phase/state, never handId alone).
 */
class BlackjackHandTest {

    private static final Card ACE_SPADES = new Card(Suit.SPADES, Rank.ACE);
    private static final Card TEN_CLUBS = new Card(Suit.CLUBS, Rank.TEN);
    private static final Card KING_HEARTS = new Card(Suit.HEARTS, Rank.KING);

    // --- no Bukkit types ---

    @Test
    void handDeclaresNoBukkitTypes() {
        for (Field field : BlackjackHand.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertFalse(field.getType().getPackageName().startsWith("org.bukkit"), field + " must not reference a Bukkit type");
        }
    }

    // --- handId is unique per instance and stable across the hand's lifetime ---

    @Test
    void handIdIsUniqueAcrossManyHands() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(new BlackjackHand(10).getHandId());
        }
        assertEquals(100, ids.size(), "every BlackjackHand must get its own unique handId");
    }

    @Test
    void handIdNeverChangesAcrossMutation() {
        BlackjackHand hand = new BlackjackHand(10);
        long id = hand.getHandId();

        hand.addCard(ACE_SPADES);
        hand.setWager(20);
        hand.setDone(true);
        hand.setDoubled(true);
        hand.setSplitFromAce(true);

        assertEquals(id, hand.getHandId(), "handId must be stable across every mutation");
    }

    @Test
    void distinctHandsNeverShareAHandId() {
        BlackjackHand a = new BlackjackHand(10);
        BlackjackHand b = new BlackjackHand(10);
        assertNotEquals(a.getHandId(), b.getHandId());
    }

    // --- handGeneration starts at zero and advances on every action ---

    @Test
    void handGenerationStartsAtZero() {
        BlackjackHand hand = new BlackjackHand(10);
        assertEquals(0, hand.getHandGeneration());
    }

    @Test
    void addCardAdvancesGeneration() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.addCard(ACE_SPADES);
        assertEquals(1, hand.getHandGeneration());
        hand.addCard(TEN_CLUBS);
        assertEquals(2, hand.getHandGeneration());
    }

    @Test
    void setDoneAdvancesGeneration() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.setDone(true);
        assertEquals(1, hand.getHandGeneration());
    }

    @Test
    void setWagerAdvancesGeneration() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.setWager(20);
        assertEquals(1, hand.getHandGeneration());
        assertEquals(20, hand.getWager());
    }

    @Test
    void setDoubledAdvancesGeneration() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.setDoubled(true);
        assertEquals(1, hand.getHandGeneration());
        assertTrue(hand.isDoubled());
    }

    @Test
    void setSplitFromAceAdvancesGeneration() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.setSplitFromAce(true);
        assertEquals(1, hand.getHandGeneration());
        assertTrue(hand.isSplitFromAce());
    }

    @Test
    void explicitBumpGenerationAdvancesWithoutAnyOtherMutation() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.bumpGeneration();
        hand.bumpGeneration();
        assertEquals(2, hand.getHandGeneration());
    }

    @Test
    void aLaterActionsGenerationIsAlwaysGreaterThanAnEarlierCapturedOne() {
        // Mirrors the exact scenario the plan calls out: a delayed callback
        // from an earlier Hit captures (handId, handGeneration) at schedule
        // time; if a later action has since advanced the hand, that capture
        // must compare stale against the hand's current generation.
        BlackjackHand hand = new BlackjackHand(10);
        hand.addCard(ACE_SPADES); // "earlier Hit" -- generation 1
        int capturedAtEarlierHit = hand.getHandGeneration();

        hand.addCard(TEN_CLUBS); // a later action supersedes it -- generation 2
        hand.setDone(true); // and the hand completes -- generation 3

        assertTrue(hand.getHandGeneration() > capturedAtEarlierHit,
            "a later action must leave the hand's generation strictly ahead of an earlier callback's captured value");
    }

    // --- originalPreSplitWager defaults to the hand's initial wager and is independently settable ---

    @Test
    void originalPreSplitWagerDefaultsToTheInitialWager() {
        BlackjackHand hand = new BlackjackHand(25);
        assertEquals(25, hand.getWager());
        assertEquals(25, hand.getOriginalPreSplitWager());
    }

    @Test
    void wagerCanChangeIndependentlyOfOriginalPreSplitWager() {
        BlackjackHand hand = new BlackjackHand(25);
        hand.setWager(50); // e.g. a double-down
        assertEquals(50, hand.getWager());
        assertEquals(25, hand.getOriginalPreSplitWager(), "insurance must always price off the pre-split wager, never a later double/split wager");
    }

    // --- cards accumulate in dealt order ---

    @Test
    void cardsAccumulateInDealtOrder() {
        BlackjackHand hand = new BlackjackHand(10);
        hand.addCard(ACE_SPADES);
        hand.addCard(TEN_CLUBS);
        hand.addCard(KING_HEARTS);
        assertEquals(java.util.List.of(ACE_SPADES, TEN_CLUBS, KING_HEARTS), hand.getCards());
    }

    // --- fresh hand defaults ---

    @Test
    void freshHandStartsUndoneUndoubledAndNotSplitFromAce() {
        BlackjackHand hand = new BlackjackHand(10);
        assertFalse(hand.isDone());
        assertFalse(hand.isDoubled());
        assertFalse(hand.isSplitFromAce());
        assertTrue(hand.getCards().isEmpty());
    }
}
