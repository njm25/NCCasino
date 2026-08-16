package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * BlackjackMaxHands models {@code splitting.max-hands} as either genuinely
 * unbounded or a validated integer &gt;= 2 -- never a large sentinel magic
 * number -- per the table redesign plan's explicit correction.
 */
class BlackjackMaxHandsTest {

    @Test
    void unboundedIsUnbounded() {
        assertTrue(BlackjackMaxHands.unbounded().isUnbounded());
        assertTrue(BlackjackMaxHands.unbounded().limit().isEmpty());
    }

    @Test
    void unboundedAllowsAnotherHandAtAnyCount() {
        BlackjackMaxHands unbounded = BlackjackMaxHands.unbounded();
        assertTrue(unbounded.allowsAnotherHand(1));
        assertTrue(unbounded.allowsAnotherHand(1000));
    }

    @Test
    void unboundedConfigValueIsTheLiteralString() {
        assertEquals("UNBOUNDED", BlackjackMaxHands.unbounded().configValue());
    }

    @Test
    void limitedRejectsBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackMaxHands.limited(1));
        assertThrows(IllegalArgumentException.class, () -> BlackjackMaxHands.limited(0));
        assertThrows(IllegalArgumentException.class, () -> BlackjackMaxHands.limited(-5));
    }

    @Test
    void limitedAcceptsTwoAndAbove() {
        assertEquals(2, BlackjackMaxHands.limited(2).limit().get());
        assertEquals(8, BlackjackMaxHands.limited(8).limit().get());
    }

    @Test
    void limitedIsNotUnbounded() {
        assertFalse(BlackjackMaxHands.limited(4).isUnbounded());
    }

    @Test
    void limitedAllowsAnotherHandOnlyBelowTheLimit() {
        BlackjackMaxHands limit = BlackjackMaxHands.limited(3);
        assertTrue(limit.allowsAnotherHand(1));
        assertTrue(limit.allowsAnotherHand(2));
        assertFalse(limit.allowsAnotherHand(3));
        assertFalse(limit.allowsAnotherHand(4));
    }

    @Test
    void limitedConfigValueIsTheIntegerAsAString() {
        assertEquals("5", BlackjackMaxHands.limited(5).configValue());
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        assertEquals(BlackjackMaxHands.limited(4), BlackjackMaxHands.limited(4));
        assertEquals(BlackjackMaxHands.unbounded(), BlackjackMaxHands.unbounded());
        assertFalse(BlackjackMaxHands.limited(4).equals(BlackjackMaxHands.limited(5)));
        assertFalse(BlackjackMaxHands.limited(4).equals(BlackjackMaxHands.unbounded()));
    }
}
