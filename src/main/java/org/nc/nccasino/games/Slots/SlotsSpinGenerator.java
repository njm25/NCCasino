package org.nc.nccasino.games.Slots;

/**
 * Produces the immutable final result of a spin. Each of the nine cells is
 * sampled independently using {@link SlotsSymbol}'s fixed weights -- there
 * is no per-line or per-reel correlation beyond what naturally falls out of
 * paylines sharing cells.
 */
public final class SlotsSpinGenerator {

    private SlotsSpinGenerator() {
    }

    public static SlotsOutcome generate(SlotsRandomSource random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        SlotsSymbol[][] grid = new SlotsSymbol[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                grid[row][col] = sampleSymbol(random);
            }
        }
        return new SlotsOutcome(grid);
    }

    /**
     * Weighted single-symbol sample via cumulative-weight bucketing over a
     * uniform draw in {@code [0, TOTAL_WEIGHT)}. Boundary values (the exact
     * cumulative thresholds) are covered explicitly in
     * {@code SlotsSpinGeneratorTest} to pin down which symbol owns each edge.
     */
    static SlotsSymbol sampleSymbol(SlotsRandomSource random) {
        int roll = random.nextInt(SlotsSymbol.TOTAL_WEIGHT);
        int cumulative = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            cumulative += symbol.weight();
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
