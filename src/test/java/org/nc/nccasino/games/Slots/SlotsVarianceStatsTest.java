package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings-preview summary: consistent with the paytable it describes, and nothing more. */
class SlotsVarianceStatsTest {

    @Test
    void everyFieldMatchesTheUnderlyingPaytableItSummarizes() {
        SlotsVarianceStats stats = SlotsVarianceStats.forConfig(
            5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.HIGH, 10L, 5);
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.HIGH);

        assertEquals(SlotsVariance.HIGH, stats.variance());
        assertEquals(5, stats.columns());
        assertEquals(paytable.theoreticalRtp(), stats.theoreticalRtp(), 1e-12);
        assertEquals(paytable.maxLineMultiplier(), stats.maxLineMultiplier(), 1e-12);
        assertEquals(SlotsPaytable.lineHitProbability(SlotsVariance.HIGH), stats.lineHitProbability(), 1e-12);
        assertEquals(SlotsMath.maxPossiblePayout(10L, 5, paytable), stats.maxPossiblePayoutAtDenomination());
    }

    @Test
    void everyLevelReportsTheSameRtpAtTheSameHouseEdge() {
        for (SlotsVarianceStats row : SlotsVarianceStats.allLevels(5, SlotsPaytable.DEFAULT_HOUSE_EDGE, 10L, 5)) {
            assertEquals(1.0 - SlotsPaytable.DEFAULT_HOUSE_EDGE, row.theoreticalRtp(), 1e-12,
                row.variance() + " must show the same RTP as every other level");
        }
    }

    @Test
    void allLevelsCoversEveryDeclaredVarianceExactlyOnce() {
        SlotsVarianceStats[] rows = SlotsVarianceStats.allLevels(5, SlotsPaytable.DEFAULT_HOUSE_EDGE, 10L, 5);
        assertEquals(SlotsVariance.values().length, rows.length);
        for (int i = 0; i < rows.length; i++) {
            assertEquals(SlotsVariance.values()[i], rows[i].variance());
        }
    }

    @Test
    void maxPossiblePayoutGrowsWithBothDenominationAndLines() {
        SlotsVarianceStats small = SlotsVarianceStats.forConfig(
            5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED, 1L, 1);
        SlotsVarianceStats large = SlotsVarianceStats.forConfig(
            5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED, 100L, 5);
        assertTrue(large.maxPossiblePayoutAtDenomination() > small.maxPossiblePayoutAtDenomination());
    }
}
