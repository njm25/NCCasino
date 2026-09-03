package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsReelPlanTest {

    /** Builds a grid whose every row is the given line, so any payline sees it. */
    private static SlotsOutcome allRows(SlotsSymbol... line) {
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][line.length];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            System.arraycopy(line, 0, grid[row], 0, line.length);
        }
        return new SlotsOutcome(grid);
    }

    private static SlotsOutcome uniform(SlotsSymbol symbol, int columns) {
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = symbol;
            }
        }
        return new SlotsOutcome(grid);
    }

    @Test
    @DisplayName("reels land strictly left to right")
    void reelsLandLeftToRight() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsReelPlan plan = SlotsReelPlan.build(uniform(SlotsSymbol.SEEDS, columns), 5);
            for (int reel = 1; reel < columns; reel++) {
                assertTrue(plan.landingTick(reel) > plan.landingTick(reel - 1),
                    "reel " + reel + " must land after reel " + (reel - 1) + " at width " + columns);
            }
        }
    }

    @Test
    @DisplayName("a reel counts as stopped only from its landing tick onward")
    void stoppedTracksLandingTick() {
        SlotsReelPlan plan = SlotsReelPlan.build(uniform(SlotsSymbol.SEEDS, 5), 5);
        for (int reel = 0; reel < 5; reel++) {
            long landing = plan.landingTick(reel);
            assertFalse(plan.isStopped(reel, landing - 1), "reel " + reel + " still spinning just before landing");
            assertTrue(plan.isStopped(reel, landing), "reel " + reel + " stopped at its landing tick");
        }
        assertFalse(plan.allStopped(plan.landingTick(4) - 1));
        assertTrue(plan.allStopped(plan.landingTick(4)));
        assertTrue(plan.revealStartTick() > plan.landingTick(4),
            "win presentation must wait until after the last reel lands");
    }

    @Test
    @DisplayName("anticipation fires only when a high symbol is one reel short")
    void anticipationFiresOnNearMissOnly() {
        // Four sevens then a seeds cell: the fifth reel was live for a full-width win.
        SlotsOutcome nearMiss = allRows(
            SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEEDS);
        assertTrue(SlotsReelPlan.shouldAnticipate(nearMiss, 5), "four sevens must earn an anticipation pause");
        assertTrue(SlotsReelPlan.build(nearMiss, 5).isAnticipated());

        // Cherries are too cheap to build tension around.
        SlotsOutcome cheapNearMiss = allRows(
            SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.SEEDS);
        assertFalse(SlotsReelPlan.shouldAnticipate(cheapNearMiss, 5),
            "a low-value symbol must not trigger anticipation, or the pause stops meaning anything");

        // Run already broken early -- nothing to anticipate.
        SlotsOutcome broken = allRows(
            SlotsSymbol.SEVEN, SlotsSymbol.SEEDS, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN);
        assertFalse(SlotsReelPlan.shouldAnticipate(broken, 5));

        // Seeds never anticipate however many line up.
        assertFalse(SlotsReelPlan.shouldAnticipate(uniform(SlotsSymbol.SEEDS, 5), 9));
    }

    @Test
    @DisplayName("anticipation only considers lines the player actually activated")
    void anticipationRespectsActiveLines() {
        // A near-miss placed only on the bottom row (payline 3).
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][5];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < 5; col++) {
                grid[row][col] = SlotsSymbol.SEEDS;
            }
        }
        for (int col = 0; col < 4; col++) {
            grid[2][col] = SlotsSymbol.SEVEN;
        }
        SlotsOutcome outcome = new SlotsOutcome(grid);

        assertFalse(SlotsReelPlan.shouldAnticipate(outcome, 1),
            "with only the middle line live, a bottom-row near-miss is not a near-miss");
        assertTrue(SlotsReelPlan.shouldAnticipate(outcome, 3),
            "once the bottom line is live the same grid does earn a pause");
    }

    @Test
    @DisplayName("an anticipated spin genuinely delays only the final reel")
    void anticipationDelaysOnlyTheLastReel() {
        SlotsOutcome nearMiss = allRows(
            SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEEDS);
        SlotsReelPlan anticipated = SlotsReelPlan.build(nearMiss, 5);
        SlotsReelPlan plain = SlotsReelPlan.build(uniform(SlotsSymbol.SEEDS, 5), 5);

        for (int reel = 0; reel < 4; reel++) {
            assertTrue(anticipated.landingTick(reel) == plain.landingTick(reel),
                "reel " + reel + " must be unaffected by anticipation");
        }
        assertTrue(anticipated.landingTick(4) - plain.landingTick(4) == SlotsTiming.ANTICIPATION_TICKS,
            "the final reel carries the whole anticipation delay");
    }

    @Test
    @DisplayName("every reel advances at least once and never after landing")
    void advanceScheduleIsSane() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsReelPlan plan = SlotsReelPlan.build(uniform(SlotsSymbol.SEEDS, columns), 5);
            for (int reel = 0; reel < columns; reel++) {
                int advances = 0;
                for (long tick = 0; tick <= plan.landingTick(reel); tick++) {
                    if (plan.advancesAt(reel, tick)) {
                        advances++;
                        assertTrue(tick < plan.landingTick(reel),
                            "reel " + reel + " must not advance on or after its landing tick");
                    }
                }
                assertTrue(advances > SlotsTiming.DECELERATION_STEPS,
                    "reel " + reel + " should spin visibly before settling, got " + advances + " advances");
            }
        }
    }

    @Test
    @DisplayName("a whole spin fits inside a sane bound")
    void worstCaseSpinIsBounded() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            long worst = SlotsTiming.worstCaseSpinTicks(columns);
            assertTrue(worst > SlotsTiming.lastReelStopTick(columns), "the bound must cover the reels themselves");
            // 30 seconds. Anything beyond this would mean a player waiting through
            // a spin longer than most Blackjack rounds.
            assertTrue(worst < 600L, "worst-case spin at width " + columns + " was " + worst + " ticks");
        }
    }

    // ---- redesign: anticipation must stay geometry-safe at every height ----

    private static SlotsOutcome uniform(SlotsSymbol symbol, int columns, int rows) {
        SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = symbol;
            }
        }
        return new SlotsOutcome(grid);
    }

    @Test
    @DisplayName("anticipation never throws or misbehaves at height 1 or 5")
    void anticipationIsSafeAtEveryHeight() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                // A genuine near-miss: every reel but the last shows SEVEN.
                SlotsSymbol[][] grid = new SlotsSymbol[rows][columns];
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < columns; col++) {
                        grid[row][col] = (col < columns - 1) ? SlotsSymbol.SEVEN : SlotsSymbol.SEEDS;
                    }
                }
                SlotsOutcome outcome = new SlotsOutcome(grid);
                int lines = SlotsPaylineCatalog.lineCount(rows);
                assertTrue(SlotsReelPlan.shouldAnticipate(outcome, lines),
                    "columns=" + columns + " rows=" + rows + ": a full near-miss must earn a pause");
            }
        }
    }

    @Test
    @DisplayName("a height-1 all-seeds outcome never anticipates")
    void heightOneAllSeedsNeverAnticipates() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            assertFalse(SlotsReelPlan.shouldAnticipate(uniform(SlotsSymbol.SEEDS, columns, 1), 1));
        }
    }
}
