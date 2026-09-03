package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the derived paytable actually returns what it claims.
 *
 * <p>The central test enumerates every possible symbol sequence along a single
 * payline and sums {@code probability * payout} directly from
 * {@link SlotsMath}'s own run-scoring logic -- deliberately re-deriving the
 * return rather than re-using {@link SlotsPaytable}'s internal closed-form
 * sum, so a mistake in either one is caught by disagreeing with the other.
 */
class SlotsPaytableTest {

    /**
     * Expected return of one line, computed by brute force over all
     * {@code symbols^columns} sequences.
     *
     * <p>Deliberately allocation-free: it reuses one index array and scores
     * each sequence arithmetically rather than materialising a
     * {@link SlotsOutcome} per sequence. At width 7 this walks 279,936
     * sequences per paytable, and building a grid object for each one
     * exhausted the test worker's heap.
     * {@link #productionEvaluatorAgreesWithTheEnumeration()} separately pins
     * this scoring against the real {@link SlotsMath} evaluator, so the
     * shortcut cannot drift from production behaviour unnoticed.
     */
    private static double enumerateLineRtp(SlotsPaytable paytable, int columns) {
        SlotsSymbol[] symbols = SlotsSymbol.values();
        int[] index = new int[columns];
        double total = 0.0;

        while (true) {
            double probability = 1.0;
            for (int col = 0; col < columns; col++) {
                probability *= symbols[index[col]].probability();
            }
            total += probability * scoreIndices(symbols, index, columns, paytable);

            int position = columns - 1;
            while (position >= 0 && ++index[position] == symbols.length) {
                index[position] = 0;
                position--;
            }
            if (position < 0) {
                break;
            }
        }
        return total;
    }

    /** Measures the leftmost run directly and prices it, without building a grid. */
    private static double scoreIndices(SlotsSymbol[] symbols, int[] index, int columns, SlotsPaytable paytable) {
        SlotsSymbol first = symbols[index[0]];
        if (!first.pays()) {
            return 0.0;
        }
        int run = 1;
        while (run < columns && index[run] == index[0]) {
            run++;
        }
        return run < first.minimumRun() ? 0.0 : paytable.multiplier(first, run);
    }

    /** Scores one explicit line through the real evaluator by building a grid whose middle row is that line. */
    private static double scoreLine(SlotsSymbol[] line, SlotsPaytable paytable) {
        int columns = line.length;
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = SlotsSymbol.SEEDS;
            }
        }
        System.arraycopy(line, 0, grid[1], 0, columns);
        SlotsMath.LineResult result =
            SlotsMath.evaluateLine(new SlotsOutcome(grid), SlotsPayline.MIDDLE, paytable);
        return result.winning() ? result.multiplier() : 0.0;
    }

    /**
     * Ties the fast enumeration above to the real evaluator. Run at width 5
     * only (7,776 sequences), which is cheap enough to materialise a grid for
     * every one and still covers short runs, full-width runs, Seeds breaks,
     * and the cherry pair rule.
     */
    @Test
    @DisplayName("the production evaluator agrees with the enumeration's scoring")
    void productionEvaluatorAgreesWithTheEnumeration() {
        int columns = 5;
        SlotsPaytable paytable = SlotsPaytable.forConfig(columns, 0.03);
        SlotsSymbol[] symbols = SlotsSymbol.values();
        int[] index = new int[columns];
        SlotsSymbol[] line = new SlotsSymbol[columns];

        while (true) {
            for (int col = 0; col < columns; col++) {
                line[col] = symbols[index[col]];
            }
            assertEquals(scoreLine(line, paytable), scoreIndices(symbols, index, columns, paytable), 1e-12,
                "shortcut scoring must match SlotsMath for " + java.util.Arrays.toString(line));

            int position = columns - 1;
            while (position >= 0 && ++index[position] == symbols.length) {
                index[position] = 0;
                position--;
            }
            if (position < 0) {
                break;
            }
        }
    }

    @Test
    @DisplayName("derived RTP equals the configured target at every width and edge")
    void derivedRtpMatchesConfiguredEdge() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (double edge : new double[] {0.01, 0.02, 0.03, 0.045, 0.06}) {
                SlotsPaytable paytable = SlotsPaytable.forConfig(columns, edge);
                double enumerated = enumerateLineRtp(paytable, columns);
                assertEquals(1.0 - edge, enumerated, 1e-9,
                    "columns=" + columns + " edge=" + edge + " should return exactly 1-edge");
                assertEquals(paytable.theoreticalRtp(), enumerated, 1e-9,
                    "closed-form RTP and enumerated RTP must agree (columns=" + columns + ", edge=" + edge + ")");
            }
        }
    }

    @Test
    @DisplayName("house edge is clamped into the supported band")
    void houseEdgeIsClamped() {
        assertEquals(SlotsPaytable.MIN_HOUSE_EDGE, SlotsPaytable.normalizeHouseEdge(0.0));
        assertEquals(SlotsPaytable.MIN_HOUSE_EDGE, SlotsPaytable.normalizeHouseEdge(-5.0));
        assertEquals(SlotsPaytable.MAX_HOUSE_EDGE, SlotsPaytable.normalizeHouseEdge(0.5));
        assertEquals(SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsPaytable.normalizeHouseEdge(Double.NaN));
        assertEquals(0.025, SlotsPaytable.normalizeHouseEdge(0.025));

        // A wildly out-of-band configured value still yields a valid machine.
        assertEquals(1.0 - SlotsPaytable.MAX_HOUSE_EDGE,
            SlotsPaytable.forConfig(5, 0.99).theoreticalRtp(), 1e-12);
    }

    @Test
    @DisplayName("run probabilities over one line form a complete distribution")
    void runProbabilitiesSumToOne() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            double total = 0.0;
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                for (int run = 1; run <= columns; run++) {
                    total += SlotsPaytable.runProbability(symbol, run, columns);
                }
            }
            assertEquals(1.0, total, 1e-9,
                "every line must land in exactly one (symbol, run-length) bucket at width " + columns);
        }
    }

    @Test
    @DisplayName("seeds never pay and cherries pay from two")
    void payingRulesHold() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03);
        for (int run = 1; run <= 5; run++) {
            assertEquals(0.0, paytable.multiplier(SlotsSymbol.SEEDS, run),
                "SEEDS must never pay (run=" + run + ")");
        }
        assertEquals(0.0, paytable.multiplier(SlotsSymbol.CHERRY, 1));
        assertTrue(paytable.multiplier(SlotsSymbol.CHERRY, 2) > 0.0, "cherries pay from a run of two");
        assertEquals(0.0, paytable.multiplier(SlotsSymbol.LEMON, 2), "only cherries pay from two");
        assertTrue(paytable.multiplier(SlotsSymbol.LEMON, 3) > 0.0);
    }

    @Test
    @DisplayName("longer runs and rarer symbols always pay strictly more")
    void paytableIsMonotonic() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(7, 0.03);
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            for (int run = symbol.minimumRun(); run < 7; run++) {
                assertTrue(paytable.multiplier(symbol, run + 1) > paytable.multiplier(symbol, run),
                    symbol + " must pay more at run " + (run + 1) + " than at " + run);
            }
        }
        SlotsSymbol[] ascending = SlotsSymbol.payingSymbols();
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(paytable.multiplier(ascending[i], 3) > paytable.multiplier(ascending[i - 1], 3),
                ascending[i] + " must outpay " + ascending[i - 1] + " at the same run length");
        }
    }

    @Test
    @DisplayName("hit frequency lands in real-slot territory")
    void hitFrequencyIsRealistic() {
        double perLine = SlotsPaytable.lineHitProbability();
        // Any one line pays fairly rarely; the machine's felt hit rate comes
        // from playing several lines at once.
        assertTrue(perLine > 0.04 && perLine < 0.08,
            "per-line hit probability should be ~6%, was " + perLine);

        double fiveLines = 1.0 - Math.pow(1.0 - perLine, 5);
        double nineLines = 1.0 - Math.pow(1.0 - perLine, 9);
        assertTrue(fiveLines > 0.20 && fiveLines < 0.35,
            "five-line hit frequency should sit in real-slot range, was " + fiveLines);
        assertTrue(nineLines > 0.35 && nineLines < 0.50,
            "nine-line hit frequency should sit in real-slot range, was " + nineLines);
    }

    @Test
    @DisplayName("width must be odd and within range")
    void widthIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> SlotsPaytable.forConfig(4, 0.03));
        assertThrows(IllegalArgumentException.class, () -> SlotsPaytable.forConfig(9, 0.03));
        assertThrows(IllegalArgumentException.class, () -> SlotsPaytable.forConfig(2, 0.03));
        assertSame(SlotsSymbol.SEVEN,
            SlotsSymbol.payingSymbols()[SlotsSymbol.payingSymbols().length - 1]);
    }
}
