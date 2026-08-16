package org.nc.nccasino.games.Slots;

/**
 * An immutable, already-final 3x3 grid of symbols. Once constructed this
 * never changes -- it is the authoritative committed result of a spin, and
 * animation is purely a cosmetic replay of a result already decided here.
 */
public final class SlotsOutcome {
    private final SlotsSymbol[][] grid;

    public SlotsOutcome(SlotsSymbol[][] grid) {
        if (grid == null || grid.length != 3) {
            throw new IllegalArgumentException("grid must have exactly 3 rows");
        }
        SlotsSymbol[][] copy = new SlotsSymbol[3][3];
        for (int row = 0; row < 3; row++) {
            if (grid[row] == null || grid[row].length != 3) {
                throw new IllegalArgumentException("grid row " + row + " must have exactly 3 columns");
            }
            for (int col = 0; col < 3; col++) {
                if (grid[row][col] == null) {
                    throw new IllegalArgumentException("grid cell [" + row + "," + col + "] must not be null");
                }
                copy[row][col] = grid[row][col];
            }
        }
        this.grid = copy;
    }

    public SlotsSymbol symbolAt(int row, int col) {
        return grid[row][col];
    }

    /** Defensive copy for callers (e.g. rendering) that want the whole grid. */
    public SlotsSymbol[][] gridCopy() {
        SlotsSymbol[][] copy = new SlotsSymbol[3][3];
        for (int row = 0; row < 3; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, 3);
        }
        return copy;
    }
}
