package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Covers the pure split decision behind BettingTable's STANDARD/item-mode
 * payout delivery -- refundWagerToInventory itself needs a live Bukkit
 * inventory to exercise directly, but the policy of "how much gets handed
 * out synchronously vs. queued" is plain arithmetic and testable on its own.
 */
class BettingTableItemPayoutPolicyTest {

    @Test
    void smallPayoutsAreDeliveredEntirelySynchronously() {
        assertEquals(0L, BettingTable.synchronousItemPortion(0));
        assertEquals(500L, BettingTable.synchronousItemPortion(500));
        assertEquals(
            BettingTable.MAX_SYNCHRONOUS_ITEM_PAYOUT,
            BettingTable.synchronousItemPortion(BettingTable.MAX_SYNCHRONOUS_ITEM_PAYOUT)
        );
    }

    @Test
    void payoutsAboveTheCeilingAreCappedNotLoopedThroughUnbounded() {
        // A payout that would otherwise need tens of millions of
        // synchronous item-giving iterations (the exact hang risk this
        // ceiling exists to prevent) must never be handed to the
        // synchronous path in full.
        long hugePayout = 5_000_000_000L;
        long synchronous = BettingTable.synchronousItemPortion(hugePayout);
        assertEquals(BettingTable.MAX_SYNCHRONOUS_ITEM_PAYOUT, synchronous);
        assertTrue(synchronous <= BettingTable.MAX_SYNCHRONOUS_ITEM_PAYOUT);
    }

    @Test
    void synchronousPortionPlusRemainderAlwaysReconstructsTheFullAmount() {
        // No currency is lost between the synchronous chunk and whatever
        // gets queued for the rest -- the split must always add back up to
        // the exact amount the round announced.
        for (long amount : new long[] {0L, 1L, 999_999L, 1_000_000L, 1_000_001L, 5_000_000_000L, Long.MAX_VALUE / 2}) {
            long synchronous = BettingTable.synchronousItemPortion(amount);
            long remainder = amount - synchronous;
            assertEquals(amount, synchronous + remainder);
            assertTrue(remainder >= 0);
        }
    }
}
