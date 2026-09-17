package org.nc.nccasino.games.Slots;

/**
 * An immutable, already-final grid of symbols, one of the supported visible
 * heights (1, 3, or 5 rows -- see {@link SlotsGeometry}) tall and as wide as
 * the machine that produced it.
 *
 * <p>This is a derived, cached view, not the authoritative result: for a real
 * spin, {@link SlotsCommittedResult} and its committed stops are authoritative,
 * and this grid is reconstructed from them. {@link SlotsOutcome} remains
 * useful on its own for hand-built pure math fixtures (tests, paytable
 * scoring) that only need a grid of symbols and no strip/stop identity.
 */
public final class SlotsOutcome {

    private final SlotsSymbol[][] grid;
    private final int columns;

    private final int rows;

    public SlotsOutcome(SlotsSymbol[][] grid) {
        if (grid == null || !SlotsGeometry.isSupportedRowCount(grid.length)) {
            throw new IllegalArgumentException(
                "grid must have a supported row count (1, 3 or 5); got "
                    + (grid == null ? "null" : grid.length));
        }
        if (grid[0] == null) {
            throw new IllegalArgumentException("grid row 0 must not be null");
        }
        int height = grid.length;
        int width = grid[0].length;
        SlotsGeometry.requireSupportedColumnCount(width);

        SlotsSymbol[][] copy = new SlotsSymbol[height][width];
        for (int row = 0; row < height; row++) {
            if (grid[row] == null || grid[row].length != width) {
                throw new IllegalArgumentException(
                    "grid row " + row + " must have exactly " + width + " columns");
            }
            for (int col = 0; col < width; col++) {
                if (grid[row][col] == null) {
                    throw new IllegalArgumentException("grid cell [" + row + "," + col + "] must not be null");
                }
                copy[row][col] = grid[row][col];
            }
        }
        this.grid = copy;
        this.columns = width;
        this.rows = height;
    }

    public int columns() {
        return columns;
    }

    /** The grid's visible height: 1, 3, or 5, per {@link SlotsGeometry}. */
    public int rows() {
        return rows;
    }

    public SlotsSymbol symbolAt(int row, int col) {
        return grid[row][col];
    }

    /** Defensive copy for callers (e.g. rendering) that want the whole grid. */
    public SlotsSymbol[][] gridCopy() {
        SlotsSymbol[][] copy = new SlotsSymbol[rows][columns];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, columns);
        }
        return copy;
    }
}
