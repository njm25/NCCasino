package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

class BlackjackSplitEligibilityTest {

    private static Card card(Rank rank) {
        return new Card(Suit.SPADES, rank);
    }

    private static List<Card> pair(Rank a, Rank b) {
        return List.of(card(a), card(b));
    }

    // --- cardsMatch: SAME_RANK vs SAME_VALUE ---

    @Test
    void sameRankRequiresIdenticalRank() {
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.KING), card(Rank.KING), BlackjackSplitMatching.SAME_RANK));
        assertFalse(BlackjackSplitEligibility.cardsMatch(card(Rank.KING), card(Rank.QUEEN), BlackjackSplitMatching.SAME_RANK));
    }

    @Test
    void sameValueAllowsAnyTwoTenValueRanks() {
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.KING), card(Rank.QUEEN), BlackjackSplitMatching.SAME_VALUE));
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.TEN), card(Rank.JACK), BlackjackSplitMatching.SAME_VALUE));
    }

    @Test
    void sameValueStillRequiresIdenticalRankBelowTen() {
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.FIVE), card(Rank.FIVE), BlackjackSplitMatching.SAME_VALUE));
        assertFalse(BlackjackSplitEligibility.cardsMatch(card(Rank.FIVE), card(Rank.SIX), BlackjackSplitMatching.SAME_VALUE));
    }

    @Test
    void twoAcesMatchUnderEitherRule() {
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.ACE), card(Rank.ACE), BlackjackSplitMatching.SAME_RANK));
        assertTrue(BlackjackSplitEligibility.cardsMatch(card(Rank.ACE), card(Rank.ACE), BlackjackSplitMatching.SAME_VALUE));
    }

    // --- isEligible: every gate is required in addition to, not instead of, every other ---

    @Test
    void eligibleWhenEveryConditionHolds() {
        assertTrue(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 2
        ));
    }

    @Test
    void ineligibleWhenSplittingDisabled() {
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            false, true, 1, BlackjackMaxHands.unbounded(), 2
        ));
    }

    @Test
    void ineligibleWithoutExactlyTwoCards() {
        List<Card> threeCards = List.of(card(Rank.EIGHT), card(Rank.EIGHT), card(Rank.TWO));
        assertFalse(BlackjackSplitEligibility.isEligible(
            threeCards, BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 2
        ));
        assertFalse(BlackjackSplitEligibility.isEligible(
            null, BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 2
        ));
    }

    @Test
    void ineligibleWhenCardsDoNotMatch() {
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.NINE), BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 2
        ));
    }

    @Test
    void ineligibleWithoutAffordability() {
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, false, 1, BlackjackMaxHands.unbounded(), 2
        ));
    }

    @Test
    void ineligibleWhenPerPlayerMaxHandsExhausted() {
        // max-hands=2, player already has 2 hands -- their own allowance is used up.
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 2, BlackjackMaxHands.limited(2), 2
        ));
    }

    @Test
    void eligibleWhenPerPlayerMaxHandsNotYetExhausted() {
        assertTrue(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.limited(2), 2
        ));
    }

    @Test
    void maxHandsIsPerPlayerNeverTableWide() {
        // A limit of 2 applies independently to each of two different
        // players' own hand counts -- one player's existing 2 hands must
        // never block a different player (currentHandCountForPlayer=1) who
        // still has headroom.
        BlackjackMaxHands limit = BlackjackMaxHands.limited(2);
        assertFalse(BlackjackSplitEligibility.isEligible(pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK, true, true, 2, limit, 2));
        assertTrue(BlackjackSplitEligibility.isEligible(pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK, true, true, 1, limit, 2));
    }

    @Test
    void ineligibleWhenShoeCannotSupplyBothReplacementCards() {
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 1
        ));
        assertFalse(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 1, BlackjackMaxHands.unbounded(), 0
        ));
    }

    @Test
    void unboundedMaxHandsNeverBlocksRepeatedSplitting() {
        assertTrue(BlackjackSplitEligibility.isEligible(
            pair(Rank.EIGHT, Rank.EIGHT), BlackjackSplitMatching.SAME_RANK,
            true, true, 50, BlackjackMaxHands.unbounded(), 2
        ));
    }
}
