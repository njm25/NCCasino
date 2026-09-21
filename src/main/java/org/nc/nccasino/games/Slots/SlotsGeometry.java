package org.nc.nccasino.games.Slots;

/**
 * Pure layout math for a variable-width, variable-height slot machine inside
 * the fixed 54-slot (6 row x 9 column) dealer inventory.
 *
 * <p>The reel grid's selectable visible height is 1, 3, or 5 rows
 * ({@link #MIN_VISIBLE_ROWS} through {@link #MAX_VISIBLE_ROWS}), always
 * vertically centred within the {@link #CANVAS_ROWS}-row canvas (see {@link #firstCanvasRow}),
 * and its width is an <em>odd</em> number of columns
 * ({@link #MIN_COLUMNS}, 5, or {@link #MAX_COLUMNS}). Odd widths are a
 * deliberate constraint, not an arbitrary one: an odd column count always
 * has a true centre column, which is what lets every symmetric payline shape
 * (the V, the inverted V) land on an exact centre rather than straddling two
 * columns.
 *
 * <p>The grid is horizontally centred in the 9-wide inventory, so growing
 * the machine from 3 to 5 to 7 columns expands it outward from the middle
 * rather than shifting it sideways.
 *
 * <p>{@link #ROWS} is a deprecated compatibility constant fixed at 3, kept
 * only for the legacy fixed-height overloads that predate selectable height
 * -- it does not describe every machine's actual grid height.
 */
public final class SlotsGeometry {

    public static final int INVENTORY_SIZE = 54;
    public static final int INVENTORY_WIDTH = 9;

    /**
     * @deprecated the machine's visible height is now a player/dealer
     *     selectable value ({@link #MIN_VISIBLE_ROWS}, 3, or
     *     {@link #MAX_VISIBLE_ROWS}), not a fixed constant. Kept only so the
     *     pre-redesign fixed-3-row call sites keep compiling until they are
     *     migrated to the row-aware overloads below; do not use it in new
     *     code.
     */
    @Deprecated
    public static final int ROWS = 3;

    public static final int MIN_COLUMNS = 3;
    public static final int MAX_COLUMNS = 7;

    /** The 5-row canvas every visible height is centred within (inventory rows 0-4). */
    public static final int CANVAS_ROWS = 5;
    public static final int MIN_VISIBLE_ROWS = 1;
    public static final int MAX_VISIBLE_ROWS = CANVAS_ROWS;

    /** The inventory row the top row of the (legacy, fixed 3-high) reel grid occupies. */
    private static final int GRID_TOP_ROW = 1;

    private SlotsGeometry() {
    }

    // ---- visible row count (new, redesign geometry) -----------------------

    /** @return every legal visible row count, ascending */
    public static int[] supportedRowCounts() {
        return new int[] {1, 3, 5};
    }

    public static boolean isSupportedRowCount(int rows) {
        return rows == 1 || rows == 3 || rows == 5;
    }

    /**
     * @throws IllegalArgumentException if {@code rows} is not one of the
     *     supported visible heights
     */
    public static void requireSupportedRowCount(int rows) {
        if (!isSupportedRowCount(rows)) {
            throw new IllegalArgumentException("rows must be 1, 3 or 5; got " + rows);
        }
    }

    /**
     * Clamps an arbitrary configured/stored value onto the nearest supported
     * visible height, so a hand-edited config can never select a height the
     * layout cannot centre. Ties round down toward the smaller supported
     * height (deterministic, and matches {@link #normalizeColumnCount}'s
     * "round toward the nearer bound" spirit).
     */
    public static int normalizeRowCount(int rows) {
        if (rows <= 1) {
            return 1;
        }
        if (rows >= 5) {
            return 5;
        }
        return 3;
    }

    /**
     * The first (topmost) of the 5 canvas rows this visible height occupies,
     * so every height is vertically centred: height 1 -> row 2, height 3 ->
     * rows 1-3, height 5 -> rows 0-4.
     */
    public static int firstCanvasRow(int rows) {
        requireSupportedRowCount(rows);
        return (CANVAS_ROWS - rows) / 2;
    }

    /**
     * @param rows the machine's selected visible height
     * @param row grid row, 0 = top of the visible window
     * @param col grid column, 0 = leftmost reel
     * @return the inventory slot index that grid cell renders into
     */
    public static int gridSlot(int columns, int rows, int row, int col) {
        requireSupportedColumnCount(columns);
        requireSupportedRowCount(rows);
        if (row < 0 || row >= rows) {
            throw new IllegalArgumentException("row must be in [0, " + rows + "); got " + row);
        }
        if (col < 0 || col >= columns) {
            throw new IllegalArgumentException("col must be in [0, " + columns + "); got " + col);
        }
        return (firstCanvasRow(rows) + row) * INVENTORY_WIDTH + firstColumn(columns) + col;
    }

    /** All grid slots for a geometry, row-major (top row left-to-right first). */
    public static int[] gridSlots(int columns, int rows) {
        requireSupportedColumnCount(columns);
        requireSupportedRowCount(rows);
        int[] slots = new int[rows * columns];
        int i = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                slots[i++] = gridSlot(columns, rows, row, col);
            }
        }
        return slots;
    }

    /** The inventory slots making up one reel (column), top to bottom, for a geometry. */
    public static int[] reelSlots(int columns, int rows, int col) {
        requireSupportedColumnCount(columns);
        requireSupportedRowCount(rows);
        int[] slots = new int[rows];
        for (int row = 0; row < rows; row++) {
            slots[row] = gridSlot(columns, rows, row, col);
        }
        return slots;
    }

    public static boolean isGridSlot(int columns, int rows, int slot) {
        requireSupportedColumnCount(columns);
        requireSupportedRowCount(rows);
        int slotRow = slot / INVENTORY_WIDTH;
        int slotCol = slot % INVENTORY_WIDTH;
        int firstRow = firstCanvasRow(rows);
        int first = firstColumn(columns);
        return slotRow >= firstRow
            && slotRow < firstRow + rows
            && slotCol >= first
            && slotCol < first + columns;
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
