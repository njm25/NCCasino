package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SlotsMath}'s height-aware ({@code *ForGeometry}) evaluation and payout methods. */
class SlotsMathGeometryTest {

    private static SlotsRandomSource scripted(int... draws) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int draw : draws) {
            queue.add(draw);
        }
        return bound -> queue.poll();
    }

    private static SlotsOutcome uniformOutcome(SlotsSymbol symbol, int columns, int rows) {
        SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                grid[r][c] = symbol;
            }
        }
        return new SlotsOutcome(grid);
    }

    @Test
    void evaluationUsesTheGeometrySpecificPath() {
        // A grid where only row 0 is all SEVEN; only lines whose row-0 entry
        // is 0 (e.g. "top") should win, height 5 "center"/"bottom" etc must not.
        SlotsSymbol[][] grid = new SlotsSymbol[5][3];
        for (int c = 0; c < 3; c++) {
            grid[0][c] = SlotsSymbol.SEVEN;
            for (int r = 1; r < 5; r++) {
                grid[r][c] = SlotsSymbol.SEEDS;
            }
        }
        SlotsOutcome outcome = new SlotsOutcome(grid);
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);
        List<SlotsMath.CatalogLineResult> results = SlotsMath.evaluateActiveCatalogLines(outcome, 9, paytable);

        // Only row 0 carries the paying symbol, so a line wins here if and
        // only if every one of its reels sits on row 0 -- the only line in
        // this 9-line catalog matching that is "top".
        List<SlotsPaylineCatalog.Line> lines = SlotsPaylineCatalog.forGeometry(3, 5);
        for (int i = 0; i < lines.size(); i++) {
            int[] rows = lines.get(i).rows();
            boolean expectedWin = true;
            for (int row : rows) {
                if (row != 0) {
                    expectedWin = false;
                    break;
                }
            }
            assertEquals(expectedWin, results.get(i).winning(), "line " + lines.get(i).shapeKey());
        }
    }

    @Test
    void inactiveLinesNeverPay() {
        SlotsOutcome outcome = uniformOutcome(SlotsSymbol.SEVEN, 3, 5);
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);
        // Only 2 lines active out of 9 -- evaluateActiveLines must not even
        // consider the rest.
        List<SlotsMath.CatalogLineResult> results = SlotsMath.evaluateActiveCatalogLines(outcome, 2, paytable);
        assertEquals(2, results.size());

        long payout = SlotsMath.totalPayoutForGeometry(outcome, 2, 1L, paytable);
        long payoutNine = SlotsMath.totalPayoutForGeometry(outcome, 9, 1L, paytable);
        assertTrue(payoutNine > payout, "activating more lines on an all-winning grid must pay more");
    }

    @Test
    void totalBetIncrementsOneLineAtATime() {
        for (int lines = 1; lines <= 9; lines++) {
            assertEquals(lines, SlotsMath.totalBetForGeometry(1L, 5, lines));
            assertEquals(lines * 3L, SlotsMath.totalBetForGeometry(3L, 3, lines));
        }
    }

    @Test
    void heightOneForcesExactlyOneLine() {
        assertEquals(1L, SlotsMath.totalBetForGeometry(1L, 1, 9));
        assertEquals(1L, SlotsMath.totalBetForGeometry(1L, 1, 500));
    }

    @Test
    void maximumPayoutBoundsGeneratedOutcomesAtEveryGeometry() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                SlotsPaytable paytable = SlotsPaytable.forConfig(columns, 0.03, SlotsVariance.HIGH_ROLLER);
                for (int lines = 1; lines <= SlotsPaylineCatalog.lineCount(rows); lines++) {
                    long worstCase = SlotsMath.maxPossiblePayoutForGeometry(10L, rows, lines, paytable);

                    // The literal worst outcome: every cell showing the top symbol.
                    SlotsOutcome allSeven = uniformOutcome(SlotsSymbol.SEVEN, columns, rows);
                    long actual = SlotsMath.totalPayoutForGeometry(allSeven, lines, 10L, paytable);
                    assertTrue(actual <= worstCase,
                        "columns=" + columns + " rows=" + rows + " lines=" + lines
                            + ": actual " + actual + " exceeded worst-case " + worstCase);
                }
            }
        }
    }

    @Test
    void heightDoesNotAlterALineMultiplier() {
        // Same columns, same run -- height must not change the payout.
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        for (int rows : SlotsGeometry.supportedRowCounts()) {
            SlotsOutcome outcome = uniformOutcome(SlotsSymbol.DIAMOND, 5, rows);
            long payout = SlotsMath.totalPayoutForGeometry(outcome, 1, 1L, paytable);
            long expectedFloor = (long) Math.floor(paytable.multiplier(SlotsSymbol.DIAMOND, 5));
            assertEquals(expectedFloor, payout, "rows=" + rows);
        }
    }

    @Test
    void unbiasedRoundingIsPreservedAtEveryHeight() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);
        double multiplier = paytable.multiplier(SlotsSymbol.CHERRY, 2);
        assertTrue(multiplier > 0.0);
        long floorValue = (long) Math.floor(multiplier);
        double fractional = multiplier - floorValue;
        assertTrue(fractional > 0.0, "need a fractional payout to exercise rounding");

        SlotsSymbol[][] grid = {
            {SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.SEEDS}
        };
        SlotsOutcome outcome = new SlotsOutcome(grid);

        int trials = 50_000;
        int roundedUp = 0;
        SlotsRandomSource rng = bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
        for (int i = 0; i < trials; i++) {
            long paid = SlotsMath.totalPayoutForGeometry(outcome, 1, 1L, paytable, rng);
            assertTrue(paid == floorValue || paid == floorValue + 1);
            if (paid > floorValue) {
                roundedUp++;
            }
        }
        double observedRate = (double) roundedUp / trials;
        assertTrue(Math.abs(observedRate - fractional) < 0.02,
            "expected roughly " + fractional + " round-up rate, observed " + observedRate);
    }

    @Test
    void deterministicRngProducesExactPayoutForGeometry() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);
        SlotsSymbol[][] grid = {
            {SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.SEEDS}
        };
        SlotsOutcome outcome = new SlotsOutcome(grid);
        double multiplier = paytable.multiplier(SlotsSymbol.CHERRY, 2);
        long floorValue = (long) Math.floor(multiplier);

        long paidLow = SlotsMath.totalPayoutForGeometry(outcome, 1, 1L, paytable, scripted(0));
        assertEquals(floorValue + 1, paidLow, "roll 0 must always round up when there is a fractional remainder");

        long paidHigh = SlotsMath.totalPayoutForGeometry(outcome, 1, 1L, paytable, scripted(999_999));
        assertEquals(floorValue, paidHigh, "the top of the draw range must always round down");
    }

    @Test
    void anAllSeedsOutcomePaysNothingAtEveryGeometry() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                SlotsOutcome outcome = uniformOutcome(SlotsSymbol.SEEDS, columns, rows);
                SlotsPaytable paytable = SlotsPaytable.forConfig(columns, 0.03, SlotsVariance.BALANCED);
                assertEquals(0L, SlotsMath.totalPayoutForGeometry(
                    outcome, SlotsPaylineCatalog.lineCount(rows), 5L, paytable));
                assertFalse(SlotsMath.evaluateActiveCatalogLines(
                    outcome, SlotsPaylineCatalog.lineCount(rows), paytable)
                    .stream().anyMatch(SlotsMath.CatalogLineResult::winning));
            }
        }
    }
}
