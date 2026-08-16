package org.nc.nccasino.games.Slots;

/**
 * The five fixed paylines evaluated on every spin. Each cell is
 * {@code [row, column]} into a 3x3 grid, row 0 = top.
 */
public enum SlotsPayline {
    TOP(new int[][] {{0, 0}, {0, 1}, {0, 2}}),
    MIDDLE(new int[][] {{1, 0}, {1, 1}, {1, 2}}),
    BOTTOM(new int[][] {{2, 0}, {2, 1}, {2, 2}}),
    DOWN_DIAGONAL(new int[][] {{0, 0}, {1, 1}, {2, 2}}),
    UP_DIAGONAL(new int[][] {{2, 0}, {1, 1}, {0, 2}});

    public static final SlotsPayline[] ALL = values();

    private final int[][] cells;

    SlotsPayline(int[][] cells) {
        this.cells = cells;
    }

    /** Defensive copy -- callers must not be able to mutate the fixed payline shape. */
    public int[][] cells() {
        int[][] copy = new int[cells.length][];
        for (int i = 0; i < cells.length; i++) {
            copy[i] = cells[i].clone();
        }
        return copy;
    }
}
