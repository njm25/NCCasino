package org.nc.nccasino.games.Slots;

/**
 * Pure layout math for a variable-width slot machine inside the fixed
 * 54-slot (6 row x 9 column) dealer inventory.
 *
 * <p>The reel grid is always {@link #ROWS} rows tall and an <em>odd</em>
 * number of columns wide ({@link #MIN_COLUMNS}, 5, or {@link #MAX_COLUMNS}).
 * Odd widths are a deliberate constraint, not an arbitrary one: an odd
 * column count always has a true centre column, which is what lets every
 * symmetric payline shape (the V, the inverted V) land on an exact centre
 * rather than straddling two columns.
 *
 * <p>The grid is horizontally centred in the 9-wide inventory, so growing
 * the machine from 3 to 5 to 7 columns expands it outward from the middle
 * rather than shifting it sideways.
 */
public final class SlotsGeometry {

    public static final int INVENTORY_SIZE = 54;
    public static final int INVENTORY_WIDTH = 9;

    /** Every machine width is 3 symbols tall; only the column count varies. */
    public static final int ROWS = 3;

    public static final int MIN_COLUMNS = 3;
    public static final int MAX_COLUMNS = 7;

    /** The inventory row the top row of the reel grid occupies. */
    private static final int GRID_TOP_ROW = 1;

    private SlotsGeometry() {
    }

    /** @return every legal column count, ascending */
    public static int[] supportedColumnCounts() {
        return new int[] {3, 5, 7};
    }

    public static boolean isSupportedColumnCount(int columns) {
        return columns >= MIN_COLUMNS
            && columns <= MAX_COLUMNS
            && (columns % 2) == 1;
    }

    /**
     * @throws IllegalArgumentException if {@code columns} is not an odd
     *     value within [{@link #MIN_COLUMNS}, {@link #MAX_COLUMNS}]
     */
    public static void requireSupportedColumnCount(int columns) {
        if (!isSupportedColumnCount(columns)) {
            throw new IllegalArgumentException(
                "columns must be an odd value in [" + MIN_COLUMNS + ", " + MAX_COLUMNS + "]; got " + columns);
        }
    }

    /**
     * Clamps and rounds an arbitrary configured value onto the nearest legal
     * odd column count, so a hand-edited config can never put the machine
     * into a width the layout cannot render.
     */
    public static int normalizeColumnCount(int columns) {
        if (columns <= MIN_COLUMNS) {
            return MIN_COLUMNS;
        }
        if (columns >= MAX_COLUMNS) {
            return MAX_COLUMNS;
        }
        // Only 5 remains between the bounds, but round rather than assume so
        // this stays correct if the supported set ever widens.
        return (columns % 2) == 1 ? columns : columns + 1;
    }

    /** The leftmost inventory column the grid starts at, for the grid to sit centred. */
    public static int firstColumn(int columns) {
        requireSupportedColumnCount(columns);
        return (INVENTORY_WIDTH - columns) / 2;
    }

    /**
     * @param row grid row, 0 = top
     * @param col grid column, 0 = leftmost reel
     * @return the inventory slot index that grid cell renders into
     */
    public static int gridSlot(int columns, int row, int col) {
        requireSupportedColumnCount(columns);
        if (row < 0 || row >= ROWS) {
            throw new IllegalArgumentException("row must be in [0, " + ROWS + "); got " + row);
        }
        if (col < 0 || col >= columns) {
            throw new IllegalArgumentException("col must be in [0, " + columns + "); got " + col);
        }
        return (GRID_TOP_ROW + row) * INVENTORY_WIDTH + firstColumn(columns) + col;
    }

    /** All grid slots for a width, row-major (top row left-to-right first). */
    public static int[] gridSlots(int columns) {
        requireSupportedColumnCount(columns);
        int[] slots = new int[ROWS * columns];
        int i = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                slots[i++] = gridSlot(columns, row, col);
            }
        }
        return slots;
    }

    /** The inventory slots making up one reel (column), top to bottom. */
    public static int[] reelSlots(int columns, int col) {
        requireSupportedColumnCount(columns);
        int[] slots = new int[ROWS];
        for (int row = 0; row < ROWS; row++) {
            slots[row] = gridSlot(columns, row, col);
        }
        return slots;
    }

    public static boolean isGridSlot(int columns, int slot) {
        requireSupportedColumnCount(columns);
        int row = slot / INVENTORY_WIDTH;
        int col = slot % INVENTORY_WIDTH;
        int first = firstColumn(columns);
        return row >= GRID_TOP_ROW
            && row < GRID_TOP_ROW + ROWS
            && col >= first
            && col < first + columns;
    }
}
