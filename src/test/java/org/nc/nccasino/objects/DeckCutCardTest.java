package org.nc.nccasino.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blackjack and Baccarat dealt their shoe to the last card and never refilled
 * it between rounds. Blackjack then aborted and refunded every round once the
 * shoe ran low (and a player counting the last cards could make a losing hand
 * refund itself); late Baccarat shoes could be counted for the pair bets.
 */
class DeckCutCardTest {

    @Test
    void aFreshShoeIsDealtFrom() {
        assertFalse(new Deck(6).shouldReshuffleBeforeRound(4));
    }

    @Test
    void theShoeIsReshuffledOnceItReachesTheCutCard() {
        Deck shoe = new Deck(6); // 312 cards; the cut is a quarter, 78
        while (shoe.remainingCards() >= 78) {
            shoe.dealCard();
        }
        assertTrue(shoe.shouldReshuffleBeforeRound(2));

        shoe.resetDeck();
        assertEquals(312, shoe.remainingCards());
        assertFalse(shoe.shouldReshuffleBeforeRound(2));
    }

    @Test
    void aFullTableNeedsMoreThanAQuarterOfASmallShoe() {
        Deck single = new Deck(1); // 52 cards
        for (int i = 0; i < 10; i++) {
            single.dealCard();
        }
        assertFalse(single.shouldReshuffleBeforeRound(2), "42 left covers two hands");
        assertTrue(single.shouldReshuffleBeforeRound(5), "but not five");
    }

    @Test
    void aDeckStackedByATestKeepsItsOrder() {
        Deck stacked = new Deck(6);
        List<Card> three = new ArrayList<>(List.of(
            new Card(Suit.SPADES, Rank.SEVEN), new Card(Suit.SPADES, Rank.SEVEN), new Card(Suit.SPADES, Rank.SEVEN)));
        stacked.stackForTest(three);
        assertFalse(stacked.shouldReshuffleBeforeRound(4));
    }
}
