package org.nc.nccasino.games.Slots;

/**
 * An immutable, already-final grid of symbols, {@link SlotsGeometry#ROWS}
 * tall and as wide as the machine that produced it. Once constructed this
 * never changes -- it is the authoritative committed result of a spin, and
 * the animation is purely a cosmetic replay of a result already decided here.
 */
public final class SlotsOutcome {

    private final SlotsSymbol[][] grid;
    private final int columns;

    public SlotsOutcome(SlotsSymbol[][] grid) {
        if (grid == null || grid.length != SlotsGeometry.ROWS) {
            throw new IllegalArgumentException("grid must have exactly " + SlotsGeometry.ROWS + " rows");
        }
        if (grid[0] == null) {
            throw new IllegalArgumentException("grid row 0 must not be null");
        }
        int width = grid[0].length;
        SlotsGeometry.requireSupportedColumnCount(width);

        SlotsSymbol[][] copy = new SlotsSymbol[SlotsGeometry.ROWS][width];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
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
    }

    public int columns() {
        return columns;
    }

    public SlotsSymbol symbolAt(int row, int col) {
        return grid[row][col];
    }

    /** Defensive copy for callers (e.g. rendering) that want the whole grid. */
    public SlotsSymbol[][] gridCopy() {
        SlotsSymbol[][] copy = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, columns);
        }
        return copy;
    }
}
