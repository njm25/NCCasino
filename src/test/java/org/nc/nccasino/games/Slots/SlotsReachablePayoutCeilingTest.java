package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the dealer liability probe follows reachable shared reel windows. */
class SlotsReachablePayoutCeilingTest {

    @Test
    void exactThreeReelCeilingIsActuallyAttainable() {
        int columns = 3;
        int rows = 3;
        int lines = 3;
        SlotsPaytable paytable = SlotsPaytable.forConfig(
            columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
        long ceiling = SlotsMath.maxPossiblePayoutForGeometry(1L, rows, lines, paytable);

        SlotsSymbol[][][] windows = new SlotsSymbol[columns][SlotsReelStrip.SIZE][];
        for (int reel = 0; reel < columns; reel++) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.BALANCED, reel);
            for (int stop = 0; stop < SlotsReelStrip.SIZE; stop++) {
                windows[reel][stop] = strip.window(stop, rows);
            }
        }

        long observedMaximum = 0L;
        for (int first = 0; first < SlotsReelStrip.SIZE; first++) {
            for (int second = 0; second < SlotsReelStrip.SIZE; second++) {
                for (int third = 0; third < SlotsReelStrip.SIZE; third++) {
                    SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
                    for (int row = 0; row < rows; row++) {
                        grid[row][0] = windows[0][first][row];
                        grid[row][1] = windows[1][second][row];
                        grid[row][2] = windows[2][third][row];
                    }
                    long payout = SlotsMath.totalPayoutForGeometry(
                        new SlotsOutcome(grid), lines, 1L, paytable, bound -> 0);
                    observedMaximum = Math.max(observedMaximum, payout);
                }
            }
        }
        assertEquals(observedMaximum, ceiling,
            "the dynamic-program ceiling must be attained by at least one real stop combination");
    }

    @Test
    void sevenReelNineLineProbeNoLongerReservesNineImpossibleJackpots() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(
            7, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.HIGH_ROLLER);
        long reachable = SlotsMath.maxPossiblePayoutForGeometry(1L, 5, 9, paytable);
        long oldEstimate = (long) Math.ceil(paytable.maxLineMultiplier() * 9.0);

        assertEquals(3_267_247L, reachable);
        assertTrue(reachable * 5L < oldEstimate,
            "the old estimate was more than five times stricter than the real cabinet");
    }
}
