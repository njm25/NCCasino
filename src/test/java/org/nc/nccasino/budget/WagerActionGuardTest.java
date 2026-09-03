package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WagerActionGuardTest {

    @Test
    void theSameActionIdIsAcceptedExactlyOnce() {
        WagerActionGuard guard = new WagerActionGuard();

        assertTrue(guard.accept("action-1"), "the first sighting of an id must be accepted");
        assertFalse(guard.accept("action-1"), "a replay of the same id must be rejected");
        assertFalse(guard.accept("action-1"), "a second replay must also be rejected");
    }

    @Test
    void distinctIdsAreEachAcceptedOnce() {
        WagerActionGuard guard = new WagerActionGuard();

        assertTrue(guard.accept("action-1"));
        assertTrue(guard.accept("action-2"));
        assertTrue(guard.accept("action-3"));
        assertFalse(guard.accept("action-1"));
        assertFalse(guard.accept("action-2"));
    }

    @Test
    void nullAndBlankIdsAreAlwaysRejected() {
        WagerActionGuard guard = new WagerActionGuard();

        assertFalse(guard.accept(null));
        assertFalse(guard.accept(""));
        assertFalse(guard.accept("   "));
        // Rejecting a blank id must not itself occupy a guard slot or affect
        // any other id's acceptance.
        assertTrue(guard.accept("action-1"));
    }

    @Test
    void theGuardIsBoundedAndEvictsTheOldestEntryFirst() {
        WagerActionGuard guard = new WagerActionGuard(3);

        assertTrue(guard.accept("a"));
        assertTrue(guard.accept("b"));
        assertTrue(guard.accept("c"));
        // Pushes the guard past capacity -- "a" (the oldest) must be evicted.
        assertTrue(guard.accept("d"));

        assertTrue(guard.accept("a"), "the evicted, oldest id must be accepted again as if new");
        // Re-adding "a" pushed the guard past capacity again, evicting "b"
        // (now the oldest) in turn -- "c" and "d" are still the two most
        // recent and must still be remembered.
        assertFalse(guard.accept("c"));
        assertFalse(guard.accept("d"));
        assertTrue(guard.accept("b"), "\"b\" was evicted in turn when \"a\" was re-added and must be accepted again");
    }

    @Test
    void capacityMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new WagerActionGuard(0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new WagerActionGuard(-1));
    }
}
