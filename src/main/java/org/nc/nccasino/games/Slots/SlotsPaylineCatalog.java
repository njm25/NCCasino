package org.nc.nccasino.games.Slots;

import java.util.List;

/**
 * The exact, geometry-specific payline shapes for the redesigned variable
 * height machine, as immutable data rather than a per-shape formula.
 *
 * <p>Unlike the legacy {@link SlotsPayline} (a fixed function of column index
 * over an always-3-row grid), a line here is a concrete row array pinned by
 * the design: height 1 has exactly one line; heights 3 and 5 each have
 * exactly nine, in a fixed canonical activation order. Every line visits
 * every reel exactly once, which is what keeps the paytable's derived RTP
 * identical across every line shape -- see {@link SlotsPaytable}.
 */
public final class SlotsPaylineCatalog {

    private SlotsPaylineCatalog() {
    }

    /** One payline: a stable number, a display key, and its exact row per reel. */
    public record Line(int number, String shapeKey, int[] rows) {

        public Line {
            rows = rows.clone();
        }

        /**
         * The compact constructor clones the incoming array, but a record's
         * generated accessor returns the field verbatim -- this override is
         * what actually makes the line's row path immutable from the
         * outside: a caller mutating the returned array can never affect
         * this instance, and repeated calls never share a mutable array.
         */
        @Override
        public int[] rows() {
            return rows.clone();
        }

        /** This line's logical (row, col) cells, left to right. */
        public int[][] cells() {
            int[][] path = new int[rows.length][2];
            for (int col = 0; col < rows.length; col++) {
                path[col][0] = rows[col];
                path[col][1] = col;
            }
            return path;
        }

        /** This line's inventory slots for a given geometry, left to right. */
        public int[] slots(int columns, int visibleRows) {
            if (rows.length != columns) {
                throw new IllegalArgumentException(
                    "line has " + rows.length + " reels but columns=" + columns);
            }
            int[] slots = new int[columns];
            for (int col = 0; col < columns; col++) {
                slots[col] = SlotsGeometry.gridSlot(columns, visibleRows, rows[col], col);
            }
            return slots;
        }
    }

    /**
     * The full nine-line catalog (or one-line catalog at height 1), in
     * canonical activation order, for one geometry.
     */
    public static List<Line> forGeometry(int columns, int visibleRows) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        SlotsGeometry.requireSupportedRowCount(visibleRows);

        if (visibleRows == 1) {
            return List.of(new Line(1, "middle", zeros(columns)));
        }
        if (visibleRows == 3) {
            return height3(columns);
        }
        return height5(columns);
    }

    private static int[] zeros(int columns) {
        int[] rows = new int[columns];
        return rows;
    }

    private static List<Line> height3(int columns) {
        return switch (columns) {
            case 3 -> List.of(
                new Line(1, "middle", new int[] {1, 1, 1}),
                new Line(2, "top", new int[] {0, 0, 0}),
                new Line(3, "bottom", new int[] {2, 2, 2}),
                new Line(4, "diagonal-down", new int[] {0, 1, 2}),
                new Line(5, "diagonal-up", new int[] {2, 1, 0}),
                new Line(6, "valley", new int[] {0, 2, 0}),
                new Line(7, "peak", new int[] {2, 0, 2}),
                new Line(8, "upper-zigzag", new int[] {0, 1, 0}),
                new Line(9, "lower-zigzag", new int[] {2, 1, 2}));
            case 5 -> List.of(
                new Line(1, "middle", new int[] {1, 1, 1, 1, 1}),
                new Line(2, "top", new int[] {0, 0, 0, 0, 0}),
                new Line(3, "bottom", new int[] {2, 2, 2, 2, 2}),
                new Line(4, "diagonal-down", new int[] {0, 1, 1, 2, 2}),
                new Line(5, "diagonal-up", new int[] {2, 1, 1, 0, 0}),
                new Line(6, "valley", new int[] {0, 1, 2, 1, 0}),
                new Line(7, "peak", new int[] {2, 1, 0, 1, 2}),
                new Line(8, "upper-zigzag", new int[] {0, 1, 0, 1, 0}),
                new Line(9, "lower-zigzag", new int[] {2, 1, 2, 1, 2}));
            case 7 -> List.of(
                new Line(1, "middle", new int[] {1, 1, 1, 1, 1, 1, 1}),
                new Line(2, "top", new int[] {0, 0, 0, 0, 0, 0, 0}),
                new Line(3, "bottom", new int[] {2, 2, 2, 2, 2, 2, 2}),
                new Line(4, "diagonal-down", new int[] {0, 0, 1, 1, 1, 2, 2}),
                new Line(5, "diagonal-up", new int[] {2, 2, 1, 1, 1, 0, 0}),
                new Line(6, "valley", new int[] {0, 1, 1, 2, 1, 1, 0}),
                new Line(7, "peak", new int[] {2, 1, 1, 0, 1, 1, 2}),
                new Line(8, "upper-zigzag", new int[] {0, 1, 0, 1, 0, 1, 0}),
                new Line(9, "lower-zigzag", new int[] {2, 1, 2, 1, 2, 1, 2}));
            default -> throw new IllegalArgumentException("unsupported columns: " + columns);
        };
    }

    private static List<Line> height5(int columns) {
        return switch (columns) {
            case 3 -> List.of(
                new Line(1, "center", new int[] {2, 2, 2}),
                new Line(2, "inner-upper", new int[] {1, 1, 1}),
                new Line(3, "inner-lower", new int[] {3, 3, 3}),
                new Line(4, "top", new int[] {0, 0, 0}),
                new Line(5, "bottom", new int[] {4, 4, 4}),
                new Line(6, "diagonal-down", new int[] {0, 2, 4}),
                new Line(7, "diagonal-up", new int[] {4, 2, 0}),
                new Line(8, "v", new int[] {0, 4, 0}),
                new Line(9, "inverted-v", new int[] {4, 0, 4}));
            case 5 -> List.of(
                new Line(1, "center", new int[] {2, 2, 2, 2, 2}),
                new Line(2, "inner-upper", new int[] {1, 1, 1, 1, 1}),
                new Line(3, "inner-lower", new int[] {3, 3, 3, 3, 3}),
                new Line(4, "top", new int[] {0, 0, 0, 0, 0}),
                new Line(5, "bottom", new int[] {4, 4, 4, 4, 4}),
                new Line(6, "diagonal-down", new int[] {0, 1, 2, 3, 4}),
                new Line(7, "diagonal-up", new int[] {4, 3, 2, 1, 0}),
                new Line(8, "v", new int[] {0, 2, 4, 2, 0}),
                new Line(9, "inverted-v", new int[] {4, 2, 0, 2, 4}));
            case 7 -> List.of(
                new Line(1, "center", new int[] {2, 2, 2, 2, 2, 2, 2}),
                new Line(2, "inner-upper", new int[] {1, 1, 1, 1, 1, 1, 1}),
                new Line(3, "inner-lower", new int[] {3, 3, 3, 3, 3, 3, 3}),
                new Line(4, "top", new int[] {0, 0, 0, 0, 0, 0, 0}),
                new Line(5, "bottom", new int[] {4, 4, 4, 4, 4, 4, 4}),
                new Line(6, "diagonal-down", new int[] {0, 1, 1, 2, 3, 3, 4}),
                new Line(7, "diagonal-up", new int[] {4, 3, 3, 2, 1, 1, 0}),
                new Line(8, "v", new int[] {0, 1, 2, 4, 2, 1, 0}),
                new Line(9, "inverted-v", new int[] {4, 3, 2, 0, 2, 3, 4}));
            default -> throw new IllegalArgumentException("unsupported columns: " + columns);
        };
    }

    /** How many lines a geometry offers: 1 at height 1, otherwise 9. */
    public static int lineCount(int visibleRows) {
        SlotsGeometry.requireSupportedRowCount(visibleRows);
        return visibleRows == 1 ? 1 : SlotsPayline.MAX_LINES;
    }

    /** Clamps a requested active-line count into what this height actually offers. */
    public static int normalizeLineCount(int visibleRows, int requestedLines) {
        int max = lineCount(visibleRows);
        if (requestedLines < 1) {
            return 1;
        }
        return Math.min(requestedLines, max);
    }

    /** The first {@code count} lines of a geometry's catalog, in canonical order. */
    public static List<Line> active(int columns, int visibleRows, int count) {
        List<Line> all = forGeometry(columns, visibleRows);
        int normalized = normalizeLineCount(visibleRows, count);
        return all.subList(0, normalized);
    }
}
