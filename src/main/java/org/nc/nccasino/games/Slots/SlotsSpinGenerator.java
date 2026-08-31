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
 *
 * <p>Sampling weights come from a {@link SlotsVariance}, never from
 * {@link SlotsSymbol} directly, so the symbols actually rolled always match
 * the probabilities {@link SlotsPaytable} priced the payout table against --
 * a machine set to a rarer-hitting variance really does hit less often, not
 * just display a paytable that claims it does.
 */
public final class SlotsSpinGenerator {

    private SlotsSpinGenerator() {
    }

    /** {@link #generate(int, SlotsRandomSource, SlotsVariance)} at {@link SlotsVariance#BALANCED}. */
    public static SlotsOutcome generate(int columns, SlotsRandomSource random) {
        return generate(columns, random, SlotsVariance.BALANCED);
    }

    public static SlotsOutcome generate(int columns, SlotsRandomSource random, SlotsVariance variance) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        SlotsVariance effective = variance == null ? SlotsVariance.BALANCED : variance;
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = sampleSymbol(col, random, effective);
            }
        }
        return new SlotsOutcome(grid);
    }

    /**
     * This reel's weight for a symbol under one variance. Currently uniform
     * across reels; kept as a seam so per-reel weighting is a one-method
     * change rather than a restructure.
     */
    static int reelWeight(SlotsSymbol symbol, int col) {
        return reelWeight(symbol, col, SlotsVariance.BALANCED);
    }

    static int reelWeight(SlotsSymbol symbol, int col, SlotsVariance variance) {
        return variance.weight(symbol);
    }

    /**
     * Weighted single-symbol sample via cumulative-weight bucketing over a
     * uniform draw in {@code [0, TOTAL_WEIGHT)}. Boundary values (the exact
     * cumulative thresholds) are covered explicitly in
     * {@code SlotsSpinGeneratorTest} to pin down which symbol owns each edge.
     */
    static SlotsSymbol sampleSymbol(int col, SlotsRandomSource random) {
        return sampleSymbol(col, random, SlotsVariance.BALANCED);
    }

    static SlotsSymbol sampleSymbol(int col, SlotsRandomSource random, SlotsVariance variance) {
        int roll = random.nextInt(SlotsSymbol.TOTAL_WEIGHT);
        int cumulative = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            cumulative += reelWeight(symbol, col, variance);
            if (roll < cumulative) {
                return symbol;
            }
        }
        // Unreachable while a variance's weights sum to TOTAL_WEIGHT (enforced
        // in SlotsVariance's static initializer); guards against future drift
        // instead of returning null.
        SlotsSymbol[] values = SlotsSymbol.values();
        return values[values.length - 1];
    }
}
