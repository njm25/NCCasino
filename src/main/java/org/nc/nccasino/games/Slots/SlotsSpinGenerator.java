package org.nc.nccasino.games.Slots;

/**
 * Produces the immutable, authoritative result of a spin: exactly one
 * uniformly random stop drawn per reel from that reel's circular
 * {@link SlotsReelStrip}, never independent per-cell sampling.
 *
 * <p>A uniformly selected stop on a circular strip gives every fixed vertical
 * offset the strip's marginal symbol distribution. With independent stops per
 * reel and identical symbol counts per reel, every fixed payline still has the
 * same left-to-right run-length distribution regardless of its shape or the
 * machine's visible height -- which is what makes the configured house edge
 * ({@link SlotsPaytable}) hold for any line count, shape, or height. There is
 * exactly one production path to a paid outcome: this one. No code in this
 * package independently samples a grid cell by cell.
 */
public final class SlotsSpinGenerator {

    private SlotsSpinGenerator() {
    }

    /**
     * The redesigned generation model: one uniform stop drawn per reel from
     * that reel's {@link SlotsReelStrip}, rather than sampling every visible
     * cell independently. Retains the exact stops alongside the derived
     * outcome, since the committed stop -- not the derived grid -- is the
     * authoritative record a replay/audit must reconstruct from.
     */
    public record StripResult(int[] stops, SlotsOutcome outcome) {
        public StripResult {
            stops = stops.clone();
        }

        @Override
        public int[] stops() {
            return stops.clone();
        }
    }

    /**
     * Draws exactly one stop per reel (columns draws total) and derives the
     * visible grid from each reel's strip window centred on that stop.
     */
    public static StripResult generateFromStrips(
        int columns, int visibleRows, SlotsRandomSource random, SlotsVariance variance) {

        SlotsGeometry.requireSupportedColumnCount(columns);
        SlotsGeometry.requireSupportedRowCount(visibleRows);
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        SlotsVariance effective = variance == null ? SlotsVariance.BALANCED : variance;

        int[] stops = new int[columns];
        for (int col = 0; col < columns; col++) {
            stops[col] = random.nextInt(SlotsReelStrip.SIZE);
        }
        return new StripResult(stops, outcomeFromStops(stops, visibleRows, effective));
    }

    /**
     * Rebuilds the exact visible outcome for already-committed stops, without
     * drawing any new randomness -- the reconstruction path a replay/audit or
     * a reconnecting client uses.
     *
     * @throws IllegalArgumentException if {@code stops} is null/empty, the
     *     geometry is unsupported, or any stop is outside
     *     {@code [0, SlotsReelStrip.SIZE)} -- reconstruction is a strict
     *     boundary: an invalid committed stop must never be silently wrapped
     *     by {@link SlotsReelStrip#symbolAt}'s circular indexing, which
     *     remains deliberately permissive for its own normal (in-bounds)
     *     circular use
     */
    public static SlotsOutcome outcomeFromStops(int[] stops, int visibleRows, SlotsVariance variance) {
        if (stops == null || stops.length == 0) {
            throw new IllegalArgumentException("stops must not be empty");
        }
        int columns = stops.length;
        SlotsGeometry.requireSupportedColumnCount(columns);
        SlotsGeometry.requireSupportedRowCount(visibleRows);
        for (int stop : stops) {
            if (stop < 0 || stop >= SlotsReelStrip.SIZE) {
                throw new IllegalArgumentException(
                    "stop must be in [0, " + SlotsReelStrip.SIZE + "); got " + stop);
            }
        }
        SlotsVariance effective = variance == null ? SlotsVariance.BALANCED : variance;

        SlotsSymbol[][] grid = new SlotsSymbol[visibleRows][columns];
        for (int col = 0; col < columns; col++) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(effective, col);
            SlotsSymbol[] window = strip.window(stops[col], visibleRows);
            for (int row = 0; row < visibleRows; row++) {
                grid[row][col] = window[row];
            }
        }
        return new SlotsOutcome(grid);
    }
}
