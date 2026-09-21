package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Auto Spin stop decisions, at both moments they can be made: before the
 * next wager is committed, and after a committed spin has settled.
 *
 * <p>The loss limit is the one rule that genuinely needs both. Checking it
 * only after settlement would knowingly overshoot the player's chosen
 * maximum loss by one whole wager, so the pre-spin guard is the primary
 * enforcement and the post-settlement check is the backstop.
 */
class SlotsAutoSpinRulesTest {

    private static SlotsAutoSpinBatch batchWith(long spins, long wagerEach, long awardEach) {
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        for (long i = 0; i < spins; i++) {
            batch.recordCommittedWager(wagerEach);
            batch.recordAward(awardEach);
        }
        return batch;
    }

    // ---- spin limit ------------------------------------------------------

    @Test
    void aFreshBatchMayAlwaysCommitItsFirstSpin() {
        assertNull(SlotsAutoSpinRules.beforeNextSpin(
            SlotsAutoSpinSettings.defaults(), new SlotsAutoSpinBatch(), 50L));
    }

    @Test
    void theSpinLimitStopsTheLoopOnceThatManySpinsAreCommitted() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withSpinLimit(3L);
        assertNull(SlotsAutoSpinRules.beforeNextSpin(settings, batchWith(2, 10L, 0L), 10L));
        assertEquals(SlotsAutoSpinRules.StopReason.SPIN_LIMIT_REACHED,
            SlotsAutoSpinRules.beforeNextSpin(settings, batchWith(3, 10L, 0L), 10L));
    }

    @Test
    void onlyCommittedSpinsCountTowardTheLimit() {
        // Recording an award without a wager is not a spin -- the count only
        // ever moves when a wager was actually debited and accepted.
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordAward(500L);
        batch.recordAward(500L);
        assertEquals(0L, batch.committedSpins());
        assertNull(SlotsAutoSpinRules.beforeNextSpin(
            SlotsAutoSpinSettings.defaults().withSpinLimit(1L), batch, 10L));
    }

    @Test
    void anUnlimitedSpinLimitNeverStopsTheLoopOnCount() {
        SlotsAutoSpinSettings unlimited =
            SlotsAutoSpinSettings.defaults().withSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS);
        assertNull(SlotsAutoSpinRules.beforeNextSpin(unlimited, batchWith(10_000, 1L, 1L), 1L));
        assertNull(SlotsAutoSpinRules.afterSettlement(
            unlimited, batchWith(10_000, 1L, 1L), 1L, 1L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void theSpinLimitAlsoStopsTheLoopAfterTheFinalSpinSettles() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withSpinLimit(2L);
        assertEquals(SlotsAutoSpinRules.StopReason.SPIN_LIMIT_REACHED,
            SlotsAutoSpinRules.afterSettlement(
                settings, batchWith(2, 10L, 0L), 10L, 0L, SlotsSettlementResult.DELIVERED));
    }

    // ---- stop on any win -------------------------------------------------

    @Test
    void stopOnAnyWinStopsAfterAnySettledSpinWithAPositivePayout() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withStopOnAnyWin(true);
        assertEquals(SlotsAutoSpinRules.StopReason.WIN,
            SlotsAutoSpinRules.afterSettlement(
                settings, batchWith(1, 50L, 1L), 50L, 1L, SlotsSettlementResult.DELIVERED));
        assertNull(SlotsAutoSpinRules.afterSettlement(
            settings, batchWith(1, 50L, 0L), 50L, 0L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void stopOnAnyWinIsOffByDefault() {
        assertNull(SlotsAutoSpinRules.afterSettlement(
            SlotsAutoSpinSettings.defaults(), batchWith(1, 50L, 500L), 50L, 500L,
            SlotsSettlementResult.DELIVERED));
    }

    // ---- big win ---------------------------------------------------------

    @Test
    void aBigWinIsTotalReturnedPayoutAtLeastTheMultipleOfThatSpinsTotalBet() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withBigWinMultiplier(10.0);
        assertFalse(SlotsAutoSpinRules.isBigWin(settings, 50L, 499L));
        assertTrue(SlotsAutoSpinRules.isBigWin(settings, 50L, 500L), "the threshold is inclusive");
        assertTrue(SlotsAutoSpinRules.isBigWin(settings, 50L, 501L));
    }

    @Test
    void aFractionalMultiplierIsComparedExactly() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withBigWinMultiplier(2.5);
        assertFalse(SlotsAutoSpinRules.isBigWin(settings, 10L, 24L));
        assertTrue(SlotsAutoSpinRules.isBigWin(settings, 10L, 25L));
    }

    @Test
    void anEnormousPayoutIsNeverMisjudgedByDoublePrecision() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withBigWinMultiplier(2.0);
        long bet = 4_000_000_000_000_000L;
        assertFalse(SlotsAutoSpinRules.isBigWin(settings, bet, (2 * bet) - 1));
        assertTrue(SlotsAutoSpinRules.isBigWin(settings, bet, 2 * bet));
    }

    @Test
    void bigWinIsOffByDefaultAndNeverFiresOnALosingOrFreeSpin() {
        assertFalse(SlotsAutoSpinRules.isBigWin(SlotsAutoSpinSettings.defaults(), 50L, 5000L));
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withBigWinMultiplier(2.0);
        assertFalse(SlotsAutoSpinRules.isBigWin(settings, 50L, 0L));
        assertFalse(SlotsAutoSpinRules.isBigWin(settings, 0L, 500L));
        assertFalse(SlotsAutoSpinRules.isBigWin(null, 50L, 500L));
    }

    @Test
    void aBigWinStopsTheBatchAfterSettlement() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withBigWinMultiplier(10.0);
        assertEquals(SlotsAutoSpinRules.StopReason.BIG_WIN,
            SlotsAutoSpinRules.afterSettlement(
                settings, batchWith(1, 50L, 500L), 50L, 500L, SlotsSettlementResult.DELIVERED));
    }

    // ---- profit target ---------------------------------------------------

    @Test
    void theProfitTargetIsMeasuredAgainstTheBatchLedgerNotOneSpin() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withProfitTarget(100.0);
        // Three spins at 50 each, awarding 60, 60 and 60: net is +30.
        SlotsAutoSpinBatch batch = batchWith(3, 50L, 60L);
        assertEquals(BigDecimal.valueOf(30L), batch.net());
        assertNull(SlotsAutoSpinRules.afterSettlement(
            settings, batch, 50L, 60L, SlotsSettlementResult.DELIVERED));

        batch.recordCommittedWager(50L);
        batch.recordAward(220L);
        assertEquals(BigDecimal.valueOf(200L), batch.net());
        assertEquals(SlotsAutoSpinRules.StopReason.PROFIT_TARGET_REACHED,
            SlotsAutoSpinRules.afterSettlement(
                settings, batch, 50L, 220L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void theProfitTargetThresholdIsInclusive() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withProfitTarget(100.0);
        SlotsAutoSpinBatch batch = new SlotsAutoSpinBatch();
        batch.recordCommittedWager(50L);
        batch.recordAward(150L);
        assertEquals(SlotsAutoSpinRules.StopReason.PROFIT_TARGET_REACHED,
            SlotsAutoSpinRules.afterSettlement(
                settings, batch, 50L, 150L, SlotsSettlementResult.DELIVERED));
    }

    // ---- loss limit ------------------------------------------------------

    @Test
    void theLossLimitStopsBeforeCommittingAWagerThatWouldOvershootIt() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withLossLimit(100.0);
        // Net is -80 after two losing 40s; a third 40 would take it to -120.
        SlotsAutoSpinBatch batch = batchWith(2, 40L, 0L);
        assertEquals(BigDecimal.valueOf(-80L), batch.net());
        assertEquals(SlotsAutoSpinRules.StopReason.LOSS_LIMIT_REACHED,
            SlotsAutoSpinRules.beforeNextSpin(settings, batch, 40L));
    }

    @Test
    void aWagerThatLandsExactlyOnTheLimitIsStillAllowedToBePlaced() {
        // The guard is "would paying this exceed the chosen maximum loss",
        // so landing exactly on it is permitted -- the player asked to be
        // able to lose that much, not that much minus one wager.
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withLossLimit(100.0);
        SlotsAutoSpinBatch batch = batchWith(3, 20L, 0L);
        assertEquals(BigDecimal.valueOf(-60L), batch.net());
        assertNull(SlotsAutoSpinRules.beforeNextSpin(settings, batch, 40L));
    }

    @Test
    void theLossLimitAlsoStopsAfterSettlementOnceItIsReached() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withLossLimit(100.0);
        SlotsAutoSpinBatch batch = batchWith(5, 20L, 0L);
        assertEquals(BigDecimal.valueOf(-100L), batch.net());
        assertEquals(SlotsAutoSpinRules.StopReason.LOSS_LIMIT_REACHED,
            SlotsAutoSpinRules.afterSettlement(
                settings, batch, 20L, 0L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void winningsPushTheLedgerBackAwayFromTheLossLimit() {
        SlotsAutoSpinSettings settings = SlotsAutoSpinSettings.defaults().withLossLimit(100.0);
        SlotsAutoSpinBatch batch = batchWith(5, 20L, 0L);
        batch.recordAward(60L);
        assertEquals(BigDecimal.valueOf(-40L), batch.net());
        assertNull(SlotsAutoSpinRules.beforeNextSpin(settings, batch, 20L));
        assertNull(SlotsAutoSpinRules.afterSettlement(
            settings, batch, 20L, 60L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void anOffLossLimitNeverStopsTheLoopHoweverDeepTheBatchGoes() {
        SlotsAutoSpinSettings settings =
            SlotsAutoSpinSettings.defaults().withSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS);
        assertNull(SlotsAutoSpinRules.beforeNextSpin(settings, batchWith(500, 100L, 0L), 100L));
    }

    // ---- settlement failure ---------------------------------------------

    @Test
    void aFailedSettlementAlwaysStopsTheBatch() {
        assertEquals(SlotsAutoSpinRules.StopReason.SETTLEMENT_FAILED,
            SlotsAutoSpinRules.afterSettlement(
                SlotsAutoSpinSettings.defaults(), new SlotsAutoSpinBatch(), 50L, 500L,
                SlotsSettlementResult.FAILED));
    }

    @Test
    void aDurablyQueuedPayoutIsNotASettlementFailure() {
        assertNull(SlotsAutoSpinRules.afterSettlement(
            SlotsAutoSpinSettings.defaults(), batchWith(1, 50L, 20L), 50L, 20L,
            SlotsSettlementResult.QUEUED));
    }

    // ---- defensive ------------------------------------------------------

    @Test
    void missingSettingsOrLedgerStopTheLoopRatherThanRunningUnbounded() {
        assertEquals(SlotsAutoSpinRules.StopReason.SPIN_REJECTED,
            SlotsAutoSpinRules.beforeNextSpin(null, new SlotsAutoSpinBatch(), 10L));
        assertEquals(SlotsAutoSpinRules.StopReason.SPIN_REJECTED,
            SlotsAutoSpinRules.beforeNextSpin(SlotsAutoSpinSettings.defaults(), null, 10L));
        assertEquals(SlotsAutoSpinRules.StopReason.SETTLEMENT_FAILED,
            SlotsAutoSpinRules.afterSettlement(
                null, new SlotsAutoSpinBatch(), 10L, 0L, SlotsSettlementResult.DELIVERED));
    }

    @Test
    void everyStopReasonHasItsOwnLocalizationKey() {
        for (SlotsAutoSpinRules.StopReason reason : SlotsAutoSpinRules.StopReason.values()) {
            String key = reason.messageKey();
            assertTrue(key.startsWith("slots.auto-stop-"), reason + " -> " + key);
            assertFalse(key.contains("_"), reason + " must use hyphens, not underscores: " + key);
            assertEquals(key.toLowerCase(java.util.Locale.ROOT), key);
        }
        assertEquals("slots.auto-stop-spin-limit-reached",
            SlotsAutoSpinRules.StopReason.SPIN_LIMIT_REACHED.messageKey());
        assertEquals("slots.auto-stop-big-win", SlotsAutoSpinRules.StopReason.BIG_WIN.messageKey());
    }
}
