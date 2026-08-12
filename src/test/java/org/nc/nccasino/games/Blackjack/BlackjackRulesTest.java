package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

class BlackjackRulesTest {

    private static Card card(Rank rank) {
        return new Card(Suit.SPADES, rank);
    }

    // --- rankValue: value of every Rank ---

    @Test
    void rankValuesMatchTraditionalBlackjackValues() {
        assertEquals(2, BlackjackRules.rankValue(Rank.TWO));
        assertEquals(3, BlackjackRules.rankValue(Rank.THREE));
        assertEquals(4, BlackjackRules.rankValue(Rank.FOUR));
        assertEquals(5, BlackjackRules.rankValue(Rank.FIVE));
        assertEquals(6, BlackjackRules.rankValue(Rank.SIX));
        assertEquals(7, BlackjackRules.rankValue(Rank.SEVEN));
        assertEquals(8, BlackjackRules.rankValue(Rank.EIGHT));
        assertEquals(9, BlackjackRules.rankValue(Rank.NINE));
        assertEquals(10, BlackjackRules.rankValue(Rank.TEN));
        assertEquals(10, BlackjackRules.rankValue(Rank.JACK));
        assertEquals(10, BlackjackRules.rankValue(Rank.QUEEN));
        assertEquals(10, BlackjackRules.rankValue(Rank.KING));
        assertEquals(1, BlackjackRules.rankValue(Rank.ACE));
    }

    @Test
    void cardStackSizeMatchesRankValueForEveryRank() {
        for (Rank rank : Rank.values()) {
            assertEquals(BlackjackRules.rankValue(rank), BlackjackRules.cardStackSize(card(rank)));
        }
    }

    // --- hard hand totals ---

    @Test
    void hardTotalsSumFaceAndPipCardsWithoutAces() {
        List<Card> hand = List.of(card(Rank.KING), card(Rank.SEVEN));
        BlackjackHandValue value = BlackjackRules.evaluate(hand);
        assertEquals(17, value.getHardTotal());
        assertEquals(17, value.getBestTotal());
        assertFalse(value.isSoft());
    }

    @Test
    void emptyOrNullHandIsZero() {
        assertEquals(0, BlackjackRules.handValue(List.of()));
        assertEquals(0, BlackjackRules.handValue(null));
        assertEquals(0, BlackjackRules.evaluate(null).getHardTotal());
    }

    // --- soft Ace totals ---

    @Test
    void singleAceIsPromotedToElevenWhenItDoesNotBust() {
        List<Card> hand = List.of(card(Rank.ACE), card(Rank.SEVEN));
        BlackjackHandValue value = BlackjackRules.evaluate(hand);
        assertEquals(8, value.getHardTotal());
        assertEquals(18, value.getBestTotal());
        assertTrue(value.isSoft());
    }

    @Test
    void aceIsNotPromotedWhenPromotingWouldBust() {
        List<Card> hand = List.of(card(Rank.ACE), card(Rank.NINE), card(Rank.FIVE));
        BlackjackHandValue value = BlackjackRules.evaluate(hand);
        assertEquals(15, value.getHardTotal());
        assertEquals(15, value.getBestTotal());
        assertFalse(value.isSoft());
    }

    // --- multiple-Ace totals ---

    @Test
    void twoAcesPromoteOnlyOneToEleven() {
        // Ace + Ace = hard 2, best is 12 (one Ace as 11, one as 1) not 22.
        List<Card> hand = List.of(card(Rank.ACE), card(Rank.ACE));
        BlackjackHandValue value = BlackjackRules.evaluate(hand);
        assertEquals(2, value.getHardTotal());
        assertEquals(12, value.getBestTotal());
    }

    @Test
    void fourAcesTotalFourteen() {
        List<Card> hand = List.of(card(Rank.ACE), card(Rank.ACE), card(Rank.ACE), card(Rank.ACE));
        BlackjackHandValue value = BlackjackRules.evaluate(hand);
        assertEquals(4, value.getHardTotal());
        assertEquals(14, value.getBestTotal()); // one Ace as 11, three as 1
    }

    // --- bust detection ---

    @Test
    void bustDetectionTriggersOnlyAboveTwentyOne() {
        assertFalse(BlackjackRules.isBust(List.of(card(Rank.KING), card(Rank.QUEEN), card(Rank.ACE)))); // 21, not bust
        assertTrue(BlackjackRules.isBust(List.of(card(Rank.KING), card(Rank.QUEEN), card(Rank.TWO)))); // 22, bust
        assertFalse(BlackjackRules.isBust(List.of(card(Rank.KING), card(Rank.QUEEN)))); // 20
    }

    // --- natural blackjack detection ---

    @Test
    void aceAndTenValueCardWithExactlyTwoCardsIsNaturalBlackjack() {
        assertTrue(BlackjackRules.isNaturalBlackjack(List.of(card(Rank.ACE), card(Rank.KING))));
        assertTrue(BlackjackRules.isNaturalBlackjack(List.of(card(Rank.QUEEN), card(Rank.ACE))));
        assertTrue(BlackjackRules.isNaturalBlackjack(List.of(card(Rank.ACE), card(Rank.TEN))));
    }

    @Test
    void naturalBlackjackRequiresExactlyTwoCards() {
        // 7 + 7 + 7 = 21 with three cards is not a natural blackjack.
        List<Card> threeCardTwentyOne = List.of(card(Rank.SEVEN), card(Rank.SEVEN), card(Rank.SEVEN));
        assertEquals(21, BlackjackRules.handValue(threeCardTwentyOne));
        assertFalse(BlackjackRules.isNaturalBlackjack(threeCardTwentyOne));

        // Ace + 6 + 4 = 21 across three cards is also not a natural blackjack.
        List<Card> threeCardAceHand = List.of(card(Rank.ACE), card(Rank.SIX), card(Rank.FOUR));
        assertEquals(21, BlackjackRules.handValue(threeCardAceHand));
        assertFalse(BlackjackRules.isNaturalBlackjack(threeCardAceHand));
    }

    @Test
    void twentyOneWithoutAceIsNotNaturalBlackjack() {
        assertFalse(BlackjackRules.isNaturalBlackjack(List.of(card(Rank.TEN), card(Rank.JACK))));
    }

    @Test
    void naturalBlackjackRejectsNullOrWrongSizedHands() {
        assertFalse(BlackjackRules.isNaturalBlackjack(null));
        assertFalse(BlackjackRules.isNaturalBlackjack(List.of(card(Rank.ACE))));
    }

    // --- dealer hit/stand decisions, including stand-on-17 semantics ---

    @Test
    void dealerAlwaysHitsBelowSeventeenRegardlessOfDraw() {
        assertTrue(BlackjackRules.dealerShouldHit(16, 100, 0.0));
        assertTrue(BlackjackRules.dealerShouldHit(16, 0, 99.9));
        assertTrue(BlackjackRules.dealerShouldHit(4, 50, 50.0));
    }

    @Test
    void dealerNeverHitsAboveSeventeenRegardlessOfDraw() {
        assertFalse(BlackjackRules.dealerShouldHit(18, 0, 0.0));
        assertFalse(BlackjackRules.dealerShouldHit(21, 0, 0.0));
    }

    @Test
    void atExactlySeventeenDealerStandsWhenDrawIsBelowStandChance() {
        // Math.random() * 100 < standOn17Chance  ->  stand (do not hit)
        assertFalse(BlackjackRules.dealerShouldHit(17, 100, 0.0));
        assertFalse(BlackjackRules.dealerShouldHit(17, 50, 49.9));
    }

    @Test
    void atExactlySeventeenDealerHitsWhenDrawIsAtOrAboveStandChance() {
        assertTrue(BlackjackRules.dealerShouldHit(17, 50, 50.0));
        assertTrue(BlackjackRules.dealerShouldHit(17, 0, 0.0));
        assertTrue(BlackjackRules.dealerShouldHit(17, 0, 99.9));
    }

    @Test
    void standOn17ChanceOfOneHundredAlwaysStandsOnSeventeen() {
        for (double draw = 0; draw < 100; draw += 5) {
            assertFalse(BlackjackRules.dealerShouldHit(17, 100, draw));
        }
    }

    // --- payout outcome classification / multipliers ---

    @Test
    void naturalBlackjackPaysTwoAndAHalfTimes() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.ACE), card(Rank.KING)),
            List.of(card(Rank.NINE), card(Rank.EIGHT))
        );
        assertEquals(BlackjackOutcome.BLACKJACK, outcome);
        assertEquals(2.5, outcome.getMultiplier());
    }

    @Test
    void regularWinPaysTwoTimes() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.TEN), card(Rank.NINE)), // 19
            List.of(card(Rank.TEN), card(Rank.EIGHT)) // 18
        );
        assertEquals(BlackjackOutcome.WIN, outcome);
        assertEquals(2.0, outcome.getMultiplier());
    }

    @Test
    void winByDealerBustPaysTwoTimesEvenWithLowerPlayerTotal() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.TEN), card(Rank.TWO)), // 12
            List.of(card(Rank.TEN), card(Rank.QUEEN), card(Rank.TWO)) // 22, bust
        );
        assertEquals(BlackjackOutcome.WIN, outcome);
    }

    @Test
    void playerNaturalBeatsDealerNaturalUndefeatedPushIsNotAwarded() {
        // Regression for finishGame's branch order: isNaturalBlackjack is
        // checked for the player only, before any dealer-side comparison,
        // so a player natural still pays 2.5x even when the dealer also
        // has a natural. This is preserved, pre-existing behavior -- not
        // "correct" push-on-both-naturals blackjack rules -- and must not
        // be silently fixed during the localization/insurance work.
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.ACE), card(Rank.KING)), // player natural
            List.of(card(Rank.ACE), card(Rank.QUEEN)) // dealer also natural
        );
        assertEquals(BlackjackOutcome.BLACKJACK, outcome);
        assertEquals(2.5, outcome.getMultiplier());
    }

    @Test
    void pushRefundsWithMultiplierOfOne() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.TEN), card(Rank.NINE)), // 19
            List.of(card(Rank.TEN), card(Rank.NINE)) // 19
        );
        assertEquals(BlackjackOutcome.PUSH, outcome);
        assertEquals(1.0, outcome.getMultiplier());
    }

    @Test
    void lossPaysNothing() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.TEN), card(Rank.EIGHT)), // 18
            List.of(card(Rank.TEN), card(Rank.NINE)) // 19
        );
        assertEquals(BlackjackOutcome.LOSS, outcome);
        assertEquals(0.0, outcome.getMultiplier());
    }

    @Test
    void playerBustLosesEvenIfDealerAlsoBusts() {
        BlackjackOutcome outcome = BlackjackRules.classify(
            List.of(card(Rank.TEN), card(Rank.QUEEN), card(Rank.TWO)), // 22, bust
            List.of(card(Rank.TEN), card(Rank.QUEEN), card(Rank.THREE)) // 23, bust
        );
        assertEquals(BlackjackOutcome.BUST, outcome);
        assertEquals(0.0, outcome.getMultiplier());
    }

    // --- localized card names can never affect hand totals ---

    @Test
    void handValueDependsOnlyOnRankNeverOnSuitOrCardIdentity() {
        // Same ranks, different suits (a stand-in for "different locale
        // rendering") must produce identical totals -- the domain model
        // carries no display strings for rules to accidentally read.
        List<Card> hearts = List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.HEARTS, Rank.ACE));
        List<Card> spades = List.of(new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.ACE));
        assertEquals(BlackjackRules.handValue(hearts), BlackjackRules.handValue(spades));
        assertEquals(BlackjackRules.isNaturalBlackjack(hearts), BlackjackRules.isNaturalBlackjack(spades));
    }
}
