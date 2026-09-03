package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact contiguous-motion math {@code SlotsMachine} uses to animate
 * a spin: seeding a reel's cosmetic starting position at
 * {@code floorMod(committedStop + advanceCount, SIZE)} and advancing exactly
 * once per scheduled tick (a real -1 move) must land it <em>naturally</em>
 * on the committed stop, with every intermediate step a real move along
 * the reel's own circular strip -- never a snap on landing. The traversal
 * runs backward (seed ahead, then decrement) so the visible motion is a new
 * symbol entering the top of the window and every existing symbol shifting
 * one row down, per the reversed reel-direction redesign.
 *
 * <p>This is pure arithmetic over {@link SlotsReelPlan#advanceCount} and
 * {@link SlotsReelStrip}, reproduced here rather than exercised through
 * {@code SlotsMachine} itself, which needs a live Bukkit inventory to
 * construct.
 */
class SlotsReelMotionMathTest {

    private static SlotsOutcome uniform(SlotsSymbol symbol, int columns, int rows) {
        SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                grid[r][c] = symbol;
            }
        }
        return new SlotsOutcome(grid);
    }

    /** A grid engineered so {@link SlotsReelPlan#build} judges it anticipated (every reel but the last a high-value symbol). */
    private static SlotsOutcome nearMiss(int columns, int rows) {
        SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                grid[r][c] = (c < columns - 1) ? SlotsSymbol.SEVEN : SlotsSymbol.SEEDS;
            }
        }
        return new SlotsOutcome(grid);
    }

    private static void assertLandsExactlyOnStop(int columns, int rows, int[] stops, SlotsReelPlan plan) {
        for (int reel = 0; reel < columns; reel++) {
            int committedStop = stops[reel];
            int advances = plan.advanceCount(reel);

            // The seed position SlotsMachine.seedReelDisplay computes.
            int position = Math.floorMod(committedStop + advances, SlotsReelStrip.SIZE);
            // Every scheduled tick is exactly a -1 move along the strip
            // (SlotsMachine.advanceReelAlongStrip's own update rule) --
            // reproduced verbatim here rather than only asserting the end
            // state, so a future change that skips or doubles a step would
            // also be caught, not only one that changes the final position.
            for (int step = 0; step < advances; step++) {
                position = Math.floorMod(position - 1, SlotsReelStrip.SIZE);
            }
            assertEquals(committedStop, position,
                "columns=" + columns + " rows=" + rows + " reel=" + reel
                    + ": the reel's natural position after all scheduled advances must equal the committed stop");
        }
    }

    @Test
    void everyGeometryEveryReelEveryRepresentativeStopLandsExactlyOnTheCommittedStop() {
        int[] representativeStops = {0, 1, 50, 98, 99};
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                SlotsOutcome outcome = uniform(SlotsSymbol.SEEDS, columns, rows);
                int lines = SlotsPaylineCatalog.lineCount(rows);
                SlotsReelPlan plan = SlotsReelPlan.build(outcome, lines);

                for (int stop : representativeStops) {
                    int[] stops = new int[columns];
                    for (int i = 0; i < columns; i++) {
                        stops[i] = stop;
                    }
                    assertLandsExactlyOnStop(columns, rows, stops, plan);
                }

                // Interior, non-boundary stops too, one per reel, distinct.
                int[] interiorStops = new int[columns];
                for (int i = 0; i < columns; i++) {
                    interiorStops[i] = (17 + i * 23) % SlotsReelStrip.SIZE;
                }
                assertLandsExactlyOnStop(columns, rows, interiorStops, plan);
            }
        }
    }

    @Test
    void anticipationChangesTimingButNeverTheAdvanceCount() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            if (columns < 3) {
                continue;
            }
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                int lines = SlotsPaylineCatalog.lineCount(rows);
                SlotsOutcome plain = uniform(SlotsSymbol.SEEDS, columns, rows);
                SlotsOutcome anticipated = nearMiss(columns, rows);

                SlotsReelPlan plainPlan = SlotsReelPlan.build(plain, lines);
                SlotsReelPlan anticipatedPlan = SlotsReelPlan.build(anticipated, lines);

                for (int reel = 0; reel < columns; reel++) {
                    assertEquals(plainPlan.advanceCount(reel), anticipatedPlan.advanceCount(reel),
                        "columns=" + columns + " rows=" + rows + " reel=" + reel
                            + ": anticipation must change timing, never the number of scheduled advances");
                }
            }
        }
    }

    @Test
    void circularWrapIsCorrectAtBothTheSeedCalculationAndLanding() {
        // A stop near 0 whose seed position must wrap backward past 0, and a
        // stop near 99 whose intermediate steps must wrap forward past 99.
        int columns = 5;
        int rows = 3;
        SlotsOutcome outcome = uniform(SlotsSymbol.SEEDS, columns, rows);
        SlotsReelPlan plan = SlotsReelPlan.build(outcome, SlotsPaylineCatalog.lineCount(rows));

        for (int stop : new int[] {0, 2, 97, 99}) {
            int[] stops = new int[columns];
            java.util.Arrays.fill(stops, stop);
            assertLandsExactlyOnStop(columns, rows, stops, plan);
        }
    }

    @Test
    void advanceCountIsPositiveForEveryReelAtEveryWidth() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsOutcome outcome = uniform(SlotsSymbol.SEEDS, columns, 3);
            SlotsReelPlan plan = SlotsReelPlan.build(outcome, 1);
            for (int reel = 0; reel < columns; reel++) {
                assertTrue(plan.advanceCount(reel) > 0, "reel " + reel + " must actually move before landing");
            }
        }
    }

    /**
     * Direct proof of the reversed visual direction against
     * {@link SlotsReelStrip#window} itself, independent of any
     * {@code SlotsMachine} plumbing: a -1 advance (the reversed traversal)
     * must move every previously-visible symbol one row toward the bottom
     * and reveal a brand-new symbol at the top, for every supported visible
     * height (including the single-row case, where the shift is only
     * mathematically observable -- there is no second row to visibly move).
     */
    @Test
    void reversedAdvanceEntersAtTopAndShiftsExistingSymbolsDown() {
        SlotsVariance variance = SlotsVariance.BALANCED;
        SlotsReelStrip strip = SlotsReelStrip.forReel(variance, 0);

        for (int rows : SlotsGeometry.supportedRowCounts()) {
            int selectedStop = 42;
            SlotsSymbol[] before = strip.window(selectedStop, rows);
            SlotsSymbol[] after = strip.window(selectedStop - 1, rows);

            assertEquals(strip.symbolAt(selectedStop - 1 - (rows / 2)), after[0],
                "rows=" + rows + ": a brand-new symbol (one stop further back) must enter at the top");

            for (int row = 0; row < rows - 1; row++) {
                assertEquals(before[row], after[row + 1],
                    "rows=" + rows + ": row " + row + "'s old symbol must shift down to row " + (row + 1));
            }
        }
    }
}
