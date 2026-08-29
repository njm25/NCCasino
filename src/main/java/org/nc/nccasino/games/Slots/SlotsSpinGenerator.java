package org.nc.nccasino.games.Slots;

/**
 * Produces the immutable final result of a spin. Each cell is sampled
 * independently from its reel's weight table.
 *
 * <p>Weights are looked up per <em>column</em>, which is what makes the
 * machine's return exactly derivable: every payline visits every column once,
 * so every line shares one run-length distribution and the configured house
 * edge holds for any line count or machine width. It also leaves room for a
 * future per-reel weighting pass (making the rightmost reel stingier is the
 * classic tension lever) without disturbing that property -- only the numbers
 * in {@link #reelWeight} would change.
 */
public final class SlotsSpinGenerator {

    private SlotsSpinGenerator() {
    }

    public static SlotsOutcome generate(int columns, SlotsRandomSource random) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = sampleSymbol(col, random);
            }
        }
        return new SlotsOutcome(grid);
    }

    /**
     * This reel's weight for a symbol. Currently uniform across reels; kept as
     * a seam so per-reel weighting is a one-method change rather than a
     * restructure.
     */
    static int reelWeight(SlotsSymbol symbol, int col) {
        return symbol.weight();
    }

    /**
     * Weighted single-symbol sample via cumulative-weight bucketing over a
     * uniform draw in {@code [0, TOTAL_WEIGHT)}. Boundary values (the exact
     * cumulative thresholds) are covered explicitly in
     * {@code SlotsSpinGeneratorTest} to pin down which symbol owns each edge.
     */
    static SlotsSymbol sampleSymbol(int col, SlotsRandomSource random) {
        int roll = random.nextInt(SlotsSymbol.TOTAL_WEIGHT);
        int cumulative = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            cumulative += reelWeight(symbol, col);
            if (roll < cumulative) {
                return symbol;
            }
        }
        // Unreachable while weights sum to TOTAL_WEIGHT; guards against
        // future weight-table drift instead of returning null.
        SlotsSymbol[] values = SlotsSymbol.values();
        return values[values.length - 1];
    }
}
