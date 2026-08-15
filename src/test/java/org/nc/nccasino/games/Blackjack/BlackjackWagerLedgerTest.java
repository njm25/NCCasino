package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

class BlackjackWagerLedgerTest {

    @Test
    void totalOfAnEmptyLedgerIsZero() {
        assertEquals(0.0, BlackjackWagerLedger.total(new ArrayDeque<>()));
    }

    @Test
    void totalSumsEveryCommittedIncrement() {
        Deque<Double> ledger = new ArrayDeque<>();
        BlackjackWagerLedger.commit(ledger, 10);
        BlackjackWagerLedger.commit(ledger, 25);
        BlackjackWagerLedger.commit(ledger, 5);
        assertEquals(40.0, BlackjackWagerLedger.total(ledger));
    }

    @Test
    void commitRejectsNonPositiveAmounts() {
        Deque<Double> ledger = new ArrayDeque<>();
        assertThrows(IllegalArgumentException.class, () -> BlackjackWagerLedger.commit(ledger, 0));
        assertThrows(IllegalArgumentException.class, () -> BlackjackWagerLedger.commit(ledger, -5));
        assertTrue(ledger.isEmpty());
    }

    @Test
    void undoLastPopsExactlyTheMostRecentlyCommittedIncrement() {
        Deque<Double> ledger = new ArrayDeque<>();
        BlackjackWagerLedger.commit(ledger, 10);
        BlackjackWagerLedger.commit(ledger, 25);

        Double popped = BlackjackWagerLedger.undoLast(ledger);

        assertEquals(25.0, popped);
        assertEquals(10.0, BlackjackWagerLedger.total(ledger));
    }

    @Test
    void undoLastOnAnEmptyLedgerReturnsNull() {
        assertNull(BlackjackWagerLedger.undoLast(new ArrayDeque<>()));
    }

    @Test
    void repeatedUndoLastEventuallyDrainsTheLedgerInReverseCommitOrder() {
        Deque<Double> ledger = new ArrayDeque<>();
        BlackjackWagerLedger.commit(ledger, 10);
        BlackjackWagerLedger.commit(ledger, 20);
        BlackjackWagerLedger.commit(ledger, 30);

        assertEquals(30.0, BlackjackWagerLedger.undoLast(ledger));
        assertEquals(20.0, BlackjackWagerLedger.undoLast(ledger));
        assertEquals(10.0, BlackjackWagerLedger.undoLast(ledger));
        assertNull(BlackjackWagerLedger.undoLast(ledger));
        assertEquals(0.0, BlackjackWagerLedger.total(ledger));
    }

    @Test
    void undoAllDrainsEveryIncrementAndReturnsTheirSum() {
        Deque<Double> ledger = new ArrayDeque<>();
        BlackjackWagerLedger.commit(ledger, 10);
        BlackjackWagerLedger.commit(ledger, 25);
        BlackjackWagerLedger.commit(ledger, 5);

        double refund = BlackjackWagerLedger.undoAll(ledger);

        assertEquals(40.0, refund);
        assertTrue(ledger.isEmpty());
    }

    @Test
    void undoAllOnAnEmptyLedgerRefundsZeroAndStaysEmpty() {
        Deque<Double> ledger = new ArrayDeque<>();
        assertEquals(0.0, BlackjackWagerLedger.undoAll(ledger));
        assertTrue(ledger.isEmpty());
    }
}
