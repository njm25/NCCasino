package org.nc.nccasino.games.Slots;

/**
 * The payline shapes, defined as functions of column index and machine width
 * rather than as fixed coordinate tables, so the same nine shapes stretch
 * across a 3, 5, or 7 column machine without a separate hand-authored table
 * per width.
 *
 * <p>Every shape visits each column exactly once. That is the property the
 * whole configurable-house-edge design rests on: because reel weights depend
 * only on the column, and every line touches every column once, <em>every
 * payline has an identical run-length distribution regardless of its shape</em>.
 * So the machine's RTP equals the expected return of a single line, and stays
 * exactly on target no matter how many lines the player activates or which
 * shapes they are. See {@link SlotsPaytable}.
 *
 * <p>Order is deliberate: the first five are the classic set (centre first,
 * as on a real machine), so a 3-column machine playing 5 lines reproduces the
 * traditional three-rows-plus-two-diagonals layout exactly.
 */
public enum SlotsPayline {

    MIDDLE {
        @Override
        public int rowAt(int col, int columns) {
            return 1;
        }
    },
    TOP {
        @Override
        public int rowAt(int col, int columns) {
            return 0;
        }
    },
    BOTTOM {
        @Override
        public int rowAt(int col, int columns) {
            return SlotsGeometry.ROWS - 1;
        }
    },
    DIAGONAL_DOWN {
        @Override
        public int rowAt(int col, int columns) {
            return (int) Math.round(position(col, columns) * (SlotsGeometry.ROWS - 1));
        }
    },
    DIAGONAL_UP {
        @Override
        public int rowAt(int col, int columns) {
            return (SlotsGeometry.ROWS - 1) - DIAGONAL_DOWN.rowAt(col, columns);
        }
    },
    /** Starts at the top, dips to the bottom at the exact centre column, returns to the top. */
    VALLEY {
        @Override
        public int rowAt(int col, int columns) {
            double t = position(col, columns);
            double centreness = 1.0 - Math.abs(2.0 * t - 1.0);
            return (int) Math.round(centreness * (SlotsGeometry.ROWS - 1));
        }
    },
    /** Mirror of {@link #VALLEY} -- starts at the bottom, peaks at the centre column. */
    PEAK {
        @Override
        public int rowAt(int col, int columns) {
            return (SlotsGeometry.ROWS - 1) - VALLEY.rowAt(col, columns);
        }
    },
    ZIGZAG_TOP {
        @Override
        public int rowAt(int col, int columns) {
            return (col % 2 == 0) ? 0 : 1;
        }
    },
    ZIGZAG_BOTTOM {
        @Override
        public int rowAt(int col, int columns) {
            return (col % 2 == 0) ? SlotsGeometry.ROWS - 1 : 1;
        }
    };

    /** Every shape is valid at every supported width, so this is the cap for all machines. */
    public static final int MAX_LINES = 9;

    public static final SlotsPayline[] ALL = values();

    /** Normalized position of a column across the machine, 0.0 at the far left, 1.0 at the far right. */
    private static double position(int col, int columns) {
        if (columns <= 1) {
            return 0.0;
        }
        return (double) col / (columns - 1);
    }

    /**
     * @param col grid column, 0 = leftmost reel
     * @param columns machine width
     * @return the grid row this line passes through in that column
     */
    public abstract int rowAt(int col, int columns);

    /** The full cell path of this line, left to right, as {@code [row, col]} pairs. */
    public int[][] cells(int columns) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        int[][] path = new int[columns][2];
        for (int col = 0; col < columns; col++) {
            path[col][0] = rowAt(col, columns);
            path[col][1] = col;
        }
        return path;
    }

    /** The inventory slots this line passes through, left to right. */
    public int[] slots(int columns) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        int[] slots = new int[columns];
        for (int col = 0; col < columns; col++) {
            slots[col] = SlotsGeometry.gridSlot(columns, rowAt(col, columns), col);
        }
        return slots;
    }

    /** Clamps an arbitrary configured line count into the legal range. */
    public static int normalizeLineCount(int lines) {
        if (lines < 1) {
            return 1;
        }
        return Math.min(lines, MAX_LINES);
    }

    /** The first {@code count} lines, in the canonical order above. */
    public static SlotsPayline[] active(int count) {
        int normalized = normalizeLineCount(count);
        SlotsPayline[] lines = new SlotsPayline[normalized];
        System.arraycopy(ALL, 0, lines, 0, normalized);
        return lines;
    }
}
