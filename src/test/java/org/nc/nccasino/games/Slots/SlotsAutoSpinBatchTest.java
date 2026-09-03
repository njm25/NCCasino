package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Auto Spin batch ledger: {@code net = awarded returns - paid wagers},
 * moved only by real economic events, and never carried from one batch to
 * the next.
 */
class SlotsAutoSpinBatchTest {

    @Test
    void aFreshBatchStartsAtZeroSpinsAndZeroNet() {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        assertEquals(0L, batch.committedSpins());
        assertEquals(BigDecimal.ZERO, batch.net());
    }

    @Test
    void aCommittedWagerCountsOneSpinAndDebitsTheLedger() {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordCommittedWager(50L);
        assertEquals(1L, batch.committedSpins());
        assertEquals(BigDecimal.valueOf(-50L), batch.net());
    }

    @Test
    void anAwardCreditsTheLedgerWithoutCountingASpin() {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordCommittedWager(50L);
        batch.recordAward(80L);
        assertEquals(1L, batch.committedSpins());
        assertEquals(BigDecimal.valueOf(30L), batch.net());
    }

    @Test
    void aLosingSpinAwardsNothingAndLeavesTheCreditSideAlone() {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordCommittedWager(50L);
        batch.recordAward(0L);
        batch.recordAward(-10L);
        assertEquals(BigDecimal.valueOf(-50L), batch.net());
    }

    @Test
    void theLedgerStaysExactAcrossAVeryLongBatch() {
        // BigDecimal rather than long specifically so an enormous batch can
        // never silently overflow the ledger meant to bound the player's
        // losses.
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        long wager = Long.MAX_VALUE / 4;
        for (int i = 0; i < 8; i++) {
            batch.recordCommittedWager(wager);
        }
        assertEquals(8L, batch.committedSpins());
        assertEquals(BigDecimal.valueOf(wager).multiply(BigDecimal.valueOf(-8L)), batch.net());
    }

    @Test
    void resetClearsBothHalvesSoOneBatchIsNeverJudgedAgainstAnothersHistory() {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordCommittedWager(500L);
        batch.recordAward(20L);
        batch.reset();
        assertEquals(0L, batch.committedSpins());
        assertEquals(BigDecimal.ZERO, batch.net());
        // And a brand-new batch's limits are judged from that clean slate.
        assertNull(SlotsAutoSpinRules.beforeNextSpin(
            SlotsAutoSpinSettings.defaults().withLossLimit(100.0), batch, 100L));
    }
}
