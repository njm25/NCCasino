package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsMathTest {

    private static final SlotsPaytable PAYTABLE_3 = SlotsPaytable.forConfig(3, 0.03);
    private static final SlotsPaytable PAYTABLE_5 = SlotsPaytable.forConfig(5, 0.03);

    /** Builds a grid whose middle row is the given line and whose other rows are Seeds. */
    private static SlotsOutcome middleRow(SlotsSymbol... line) {
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][line.length];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < line.length; col++) {
                grid[row][col] = SlotsSymbol.SEEDS;
            }
        }
        System.arraycopy(line, 0, grid[1], 0, line.length);
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

    // ---- run scoring ------------------------------------------------------

    @Test
    @DisplayName("a run must start at the leftmost reel")
    void runsAreLeftAnchored() {
        // Three bells, but not starting at column 0 -- not a win on a real machine.
        SlotsOutcome offset = middleRow(
            SlotsSymbol.SEEDS, SlotsSymbol.BELL, SlotsSymbol.BELL, SlotsSymbol.BELL, SlotsSymbol.SEEDS);
        assertFalse(SlotsMath.evaluateLine(offset, SlotsPayline.MIDDLE, PAYTABLE_5).winning(),
            "a matching trio away from the left edge must not pay");

        SlotsOutcome anchored = middleRow(
            SlotsSymbol.BELL, SlotsSymbol.BELL, SlotsSymbol.BELL, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS);
        SlotsMath.LineResult result = SlotsMath.evaluateLine(anchored, SlotsPayline.MIDDLE, PAYTABLE_5);
        assertTrue(result.winning());
        assertEquals(3, result.runLength());
        assertEquals(SlotsSymbol.BELL, result.symbol());
    }

    @Test
    @DisplayName("seeds never pay and always break a run")
    void seedsNeverPay() {
        SlotsOutcome allSeeds = uniform(SlotsSymbol.SEEDS, 5);
        assertFalse(SlotsMath.evaluateLine(allSeeds, SlotsPayline.MIDDLE, PAYTABLE_5).winning());
        assertEquals(0L, SlotsMath.totalPayout(allSeeds, 9, 100L, PAYTABLE_5));

        SlotsOutcome broken = middleRow(
            SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEEDS, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN);
        assertFalse(SlotsMath.evaluateLine(broken, SlotsPayline.MIDDLE, PAYTABLE_5).winning(),
            "a Seeds cell in column 2 must cut the run below SEVEN's minimum of three");
    }

    @Test
    @DisplayName("cherries pay from two, every other symbol needs three")
    void minimumRunsAreEnforced() {
        SlotsOutcome twoCherries = middleRow(
            SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS);
        SlotsMath.LineResult cherryResult = SlotsMath.evaluateLine(twoCherries, SlotsPayline.MIDDLE, PAYTABLE_5);
        assertTrue(cherryResult.winning(), "two cherries pay");
        assertEquals(2, cherryResult.runLength());

        SlotsOutcome twoLemons = middleRow(
            SlotsSymbol.LEMON, SlotsSymbol.LEMON, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS);
        assertFalse(SlotsMath.evaluateLine(twoLemons, SlotsPayline.MIDDLE, PAYTABLE_5).winning(),
            "two lemons must not pay");
    }

    @Test
    @DisplayName("a longer run pays strictly more than a shorter one")
    void longerRunsPayMore() {
        SlotsOutcome three = middleRow(
            SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEEDS, SlotsSymbol.SEEDS);
        SlotsOutcome five = middleRow(
            SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN);

        double shortPay = SlotsMath.evaluateLine(three, SlotsPayline.MIDDLE, PAYTABLE_5).multiplier();
        double longPay = SlotsMath.evaluateLine(five, SlotsPayline.MIDDLE, PAYTABLE_5).multiplier();
        assertTrue(longPay > shortPay, "five sevens must beat three sevens");
    }

    // ---- stake and payout -------------------------------------------------

    @Test
    @DisplayName("total bet scales with active lines, not machine width")
    void totalBetScalesWithLines() {
        assertEquals(50L, SlotsMath.totalBet(10L, 5));
        assertEquals(90L, SlotsMath.totalBet(10L, 9));
        assertEquals(10L, SlotsMath.totalBet(10L, 1));
        // Out-of-range line counts clamp rather than throw.
        assertEquals(90L, SlotsMath.totalBet(10L, 99));
        assertThrows(IllegalArgumentException.class, () -> SlotsMath.totalBet(-1L, 5));
    }

    @Test
    @DisplayName("only active lines are paid")
    void onlyActiveLinesArePaid() {
        SlotsOutcome allSevens = uniform(SlotsSymbol.SEVEN, 3);
        long onePayout = SlotsMath.totalPayout(allSevens, 1, 10L, PAYTABLE_3);
        long fivePayout = SlotsMath.totalPayout(allSevens, 5, 10L, PAYTABLE_3);
        assertTrue(fivePayout > onePayout, "more active lines must pay more on a full grid");

        List<SlotsMath.LineResult> results = SlotsMath.evaluateActiveLines(allSevens, 3, PAYTABLE_3);
        assertEquals(3, results.size(), "only the requested lines are evaluated");
        assertTrue(results.stream().allMatch(SlotsMath.LineResult::winning));
    }

    @Test
    @DisplayName("a zero wager pays zero and never throws")
    void zeroWagerIsSafe() {
        SlotsOutcome allSevens = uniform(SlotsSymbol.SEVEN, 5);
        assertEquals(0L, SlotsMath.totalPayout(allSevens, 9, 0L, PAYTABLE_5));
        assertEquals(0L, SlotsMath.totalBet(0L, 9));
    }

    @Test
    @DisplayName("worst-case payout bounds any real outcome")
    void worstCaseBoundsRealOutcomes() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable = SlotsPaytable.forConfig(columns, 0.03);
            long wager = 25L;
            int lines = SlotsPayline.MAX_LINES;
            long ceiling = SlotsMath.maxPossiblePayout(wager, lines, paytable);

            for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
                long actual = SlotsMath.totalPayout(uniform(symbol, columns), lines, wager, paytable);
                assertTrue(actual <= ceiling,
                    "a full grid of " + symbol + " (" + actual + ") must not exceed the probe ceiling (" + ceiling + ")");
            }
        }
    }

    @Test
    @DisplayName("an absurd wager is rejected rather than silently overflowing")
    void overflowThrows() {
        assertThrows(ArithmeticException.class, () -> SlotsMath.totalBet(Long.MAX_VALUE, 9));
        assertThrows(ArithmeticException.class,
            () -> SlotsMath.maxPossiblePayout(Long.MAX_VALUE, 9, PAYTABLE_5));
    }
}
