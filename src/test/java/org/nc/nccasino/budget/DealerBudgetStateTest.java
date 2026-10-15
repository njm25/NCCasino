package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settled-id tombstones stay bounded however they arrive. */
class DealerBudgetStateTest {

    @Test
    void settlingPastTheCapEvictsTheOldestIds() {
        DealerBudgetState state = new DealerBudgetState("dealer");
        int count = DealerBudgetState.MAX_TOMBSTONES + 5;
        for (int i = 0; i < count; i++) {
            state.tombstone("id-" + i);
        }

        assertEquals(DealerBudgetState.MAX_TOMBSTONES, state.tombstoneEntries().size());
        assertFalse(state.isTombstoned("id-4"), "the oldest ids are evicted first");
        assertTrue(state.isTombstoned("id-5"));
        assertTrue(state.isTombstoned("id-" + (count - 1)));
    }

    @Test
    void loadingAFileWrittenUnderALargerCapKeepsOnlyTheNewestIds() {
        DealerBudgetState state = new DealerBudgetState("dealer");
        int count = DealerBudgetState.MAX_TOMBSTONES + 50;
        for (int i = 0; i < count; i++) {
            state.restoreTombstone("id-" + i, 1_700_000_000L + i);
        }

        assertEquals(DealerBudgetState.MAX_TOMBSTONES, state.tombstoneEntries().size());
        assertFalse(state.isTombstoned("id-49"));
        assertTrue(state.isTombstoned("id-50"));
        assertTrue(state.isTombstoned("id-" + (count - 1)));
    }
}
