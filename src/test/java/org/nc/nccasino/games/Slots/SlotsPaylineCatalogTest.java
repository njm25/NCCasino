package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the exact per-geometry payline arrays specified by the Slots redesign. */
class SlotsPaylineCatalogTest {

    @Test
    void heightOneHasExactlyOneMiddleLineAtEveryWidth() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            List<SlotsPaylineCatalog.Line> lines = SlotsPaylineCatalog.forGeometry(columns, 1);
            assertEquals(1, lines.size());
            int[] expected = new int[columns];
            assertArrayEquals(expected, lines.get(0).rows());
        }
    }

    @Test
    void heightThreeAndFiveHaveExactlyNineLinesAtEveryWidth() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            assertEquals(9, SlotsPaylineCatalog.forGeometry(columns, 3).size());
            assertEquals(9, SlotsPaylineCatalog.forGeometry(columns, 5).size());
        }
    }

    @Test
    void heightThreeWidthThreeExactArrays() {
        assertLines(3, 3,
            new int[] {1, 1, 1}, new int[] {0, 0, 0}, new int[] {2, 2, 2},
            new int[] {0, 1, 2}, new int[] {2, 1, 0}, new int[] {0, 2, 0},
            new int[] {2, 0, 2}, new int[] {0, 1, 0}, new int[] {2, 1, 2});
    }

    @Test
    void heightThreeWidthFiveExactArrays() {
        assertLines(5, 3,
            new int[] {1, 1, 1, 1, 1}, new int[] {0, 0, 0, 0, 0}, new int[] {2, 2, 2, 2, 2},
            new int[] {0, 1, 1, 2, 2}, new int[] {2, 1, 1, 0, 0}, new int[] {0, 1, 2, 1, 0},
            new int[] {2, 1, 0, 1, 2}, new int[] {0, 1, 0, 1, 0}, new int[] {2, 1, 2, 1, 2});
    }

    @Test
    void heightThreeWidthSevenExactArrays() {
        assertLines(7, 3,
            new int[] {1, 1, 1, 1, 1, 1, 1}, new int[] {0, 0, 0, 0, 0, 0, 0}, new int[] {2, 2, 2, 2, 2, 2, 2},
            new int[] {0, 0, 1, 1, 1, 2, 2}, new int[] {2, 2, 1, 1, 1, 0, 0}, new int[] {0, 1, 1, 2, 1, 1, 0},
            new int[] {2, 1, 1, 0, 1, 1, 2}, new int[] {0, 1, 0, 1, 0, 1, 0}, new int[] {2, 1, 2, 1, 2, 1, 2});
    }

    @Test
    void heightFiveWidthThreeExactArrays() {
        assertLines(3, 5,
            new int[] {2, 2, 2}, new int[] {1, 1, 1}, new int[] {3, 3, 3},
            new int[] {0, 0, 0}, new int[] {4, 4, 4}, new int[] {0, 2, 4},
            new int[] {4, 2, 0}, new int[] {0, 4, 0}, new int[] {4, 0, 4});
    }

    @Test
    void heightFiveWidthFiveExactArrays() {
        assertLines(5, 5,
            new int[] {2, 2, 2, 2, 2}, new int[] {1, 1, 1, 1, 1}, new int[] {3, 3, 3, 3, 3},
            new int[] {0, 0, 0, 0, 0}, new int[] {4, 4, 4, 4, 4}, new int[] {0, 1, 2, 3, 4},
            new int[] {4, 3, 2, 1, 0}, new int[] {0, 2, 4, 2, 0}, new int[] {4, 2, 0, 2, 4});
    }

    @Test
    void heightFiveWidthSevenExactArrays() {
        assertLines(7, 5,
            new int[] {2, 2, 2, 2, 2, 2, 2}, new int[] {1, 1, 1, 1, 1, 1, 1}, new int[] {3, 3, 3, 3, 3, 3, 3},
            new int[] {0, 0, 0, 0, 0, 0, 0}, new int[] {4, 4, 4, 4, 4, 4, 4}, new int[] {0, 1, 1, 2, 3, 3, 4},
            new int[] {4, 3, 3, 2, 1, 1, 0}, new int[] {0, 1, 2, 4, 2, 1, 0}, new int[] {4, 3, 2, 0, 2, 3, 4});
    }

    private static void assertLines(int columns, int rows, int[]... expected) {
        List<SlotsPaylineCatalog.Line> lines = SlotsPaylineCatalog.forGeometry(columns, rows);
        assertEquals(expected.length, lines.size());
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], lines.get(i).rows(),
                "columns=" + columns + " rows=" + rows + " line " + (i + 1));
        }
    }

    @Test
    void everyLineLengthEqualsReelCount() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                for (SlotsPaylineCatalog.Line line : SlotsPaylineCatalog.forGeometry(columns, rows)) {
                    assertEquals(columns, line.rows().length);
                }
            }
        }
    }

    @Test
    void everyRowIsInRangeForItsHeight() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                for (SlotsPaylineCatalog.Line line : SlotsPaylineCatalog.forGeometry(columns, rows)) {
                    for (int row : line.rows()) {
                        assertTrue(row >= 0 && row < rows,
                            "columns=" + columns + " rows=" + rows + ": row " + row + " out of range");
                    }
                }
            }
        }
    }

    @Test
    void everyPathVisitsEveryReelExactlyOnce() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                for (SlotsPaylineCatalog.Line line : SlotsPaylineCatalog.forGeometry(columns, rows)) {
                    int[][] cells = line.cells();
                    assertEquals(columns, cells.length);
                    for (int col = 0; col < columns; col++) {
                        assertEquals(col, cells[col][1], "cell " + col + " must belong to reel " + col);
                    }
                }
            }
        }
    }

    @Test
    void noDuplicateLinePathsWithinAGeometry() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                Set<String> seen = new HashSet<>();
                for (SlotsPaylineCatalog.Line line : SlotsPaylineCatalog.forGeometry(columns, rows)) {
                    String key = java.util.Arrays.toString(line.rows());
                    assertTrue(seen.add(key),
                        "columns=" + columns + " rows=" + rows + ": duplicate line path " + key);
                }
            }
        }
    }

    @Test
    void heightOneNormalizesActiveLinesToOne() {
        assertEquals(1, SlotsPaylineCatalog.normalizeLineCount(1, 9));
        assertEquals(1, SlotsPaylineCatalog.lineCount(1));
    }

    @Test
    void heightThreeAndFiveClampLineCountToOneThroughNine() {
        for (int rows : new int[] {3, 5}) {
            assertEquals(1, SlotsPaylineCatalog.normalizeLineCount(rows, 0));
            assertEquals(1, SlotsPaylineCatalog.normalizeLineCount(rows, -5));
            assertEquals(9, SlotsPaylineCatalog.normalizeLineCount(rows, 9));
            assertEquals(9, SlotsPaylineCatalog.normalizeLineCount(rows, 500));
            assertEquals(5, SlotsPaylineCatalog.normalizeLineCount(rows, 5));
        }
    }

    @Test
    void activePrefixMatchesTheCanonicalCatalogOrder() {
        List<SlotsPaylineCatalog.Line> all = SlotsPaylineCatalog.forGeometry(5, 5);
        List<SlotsPaylineCatalog.Line> active = SlotsPaylineCatalog.active(5, 5, 4);
        assertEquals(4, active.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(all.get(i).number(), active.get(i).number());
            assertArrayEquals(all.get(i).rows(), active.get(i).rows());
        }
    }

    @Test
    void inventorySlotsCorrespondToGeometry() {
        SlotsPaylineCatalog.Line middle = SlotsPaylineCatalog.forGeometry(3, 3).get(0);
        int[] slots = middle.slots(3, 3);
        for (int col = 0; col < 3; col++) {
            assertEquals(SlotsGeometry.gridSlot(3, 3, 1, col), slots[col]);
        }
    }
}
