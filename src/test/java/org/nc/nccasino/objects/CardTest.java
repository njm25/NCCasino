package org.nc.nccasino.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Locks down Card's identity semantics: no equals/hashCode override, so two
 * Card instances with the same rank/suit are distinct objects (default
 * Object identity equality). Callers that need "same logical card" (e.g.
 * BlackjackFrameTest) must compare rank/suit explicitly or reuse the same
 * instance -- they must not rely on, or reintroduce, value equality here.
 */
class CardTest {

    @Test
    void distinctInstancesWithTheSameRankAndSuitAreNotEqual() {
        Card a = new Card(Suit.HEARTS, Rank.ACE);
        Card b = new Card(Suit.HEARTS, Rank.ACE);

        assertNotEquals(a, b);
    }

    @Test
    void aCardIsOnlyEqualToItself() {
        Card card = new Card(Suit.SPADES, Rank.KING);
        assertEquals(card, card);
        assertSame(card, card);
    }

    @Test
    void rankAndSuitAccessorsReturnConstructorValues() {
        Card card = new Card(Suit.DIAMONDS, Rank.SEVEN);
        assertEquals(Suit.DIAMONDS, card.getSuit());
        assertEquals(Rank.SEVEN, card.getRank());
    }
}
