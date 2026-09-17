package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The redesign's stop-based generation path: {@link SlotsSpinGenerator#generateFromStrips}. */
class SlotsStripGenerationTest {

    /** Deterministic {@link SlotsRandomSource} that returns a fixed queue of draws. */
    private static SlotsRandomSource scripted(int... draws) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int draw : draws) {
            queue.add(draw);
        }
        return bound -> {
            Integer next = queue.poll();
            if (next == null) {
                throw new IllegalStateException("scripted source exhausted");
            }
            return next;
        };
    }

    @Test
    void drawsExactlyOneStopPerReel() {
        SlotsRandomSource rng = scripted(10, 20, 30, 40, 50);
        SlotsSpinGenerator.StripResult result =
            SlotsSpinGenerator.generateFromStrips(5, 3, rng, SlotsVariance.BALANCED);
        assertEquals(5, result.stops().length);
        assertEquals(10, result.stops()[0]);
        assertEquals(20, result.stops()[1]);
        assertEquals(30, result.stops()[2]);
        assertEquals(40, result.stops()[3]);
        assertEquals(50, result.stops()[4]);
    }

    @Test
    void deterministicInjectedRngProducesExactStopsAndWindows() {
        SlotsRandomSource rng = scripted(0, 50, 99);
        SlotsSpinGenerator.StripResult result =
            SlotsSpinGenerator.generateFromStrips(3, 3, rng, SlotsVariance.BALANCED);

        SlotsOutcome outcome = result.outcome();
        for (int col = 0; col < 3; col++) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.BALANCED, col);
            SlotsSymbol[] window = strip.window(result.stops()[col], 3);
            for (int row = 0; row < 3; row++) {
                assertEquals(window[row], outcome.symbolAt(row, col));
            }
        }
    }

    @Test
    void everyGeometryProducesACorrectlyShapedImmutableGrid() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                int[] draws = new int[columns];
                SlotsRandomSource rng = scripted(draws);
                SlotsOutcome outcome =
                    SlotsSpinGenerator.generateFromStrips(columns, rows, rng, SlotsVariance.BALANCED).outcome();
                assertEquals(columns, outcome.columns());
                assertEquals(rows, outcome.rows());
            }
        }
    }

    @Test
    void storedStopIndexesExactlyReconstructTheOutcome() {
        int[] stops = {7, 42, 99, 0, 55};
        SlotsOutcome first = SlotsSpinGenerator.outcomeFromStops(stops, 5, SlotsVariance.HIGH);
        SlotsOutcome second = SlotsSpinGenerator.outcomeFromStops(stops, 5, SlotsVariance.HIGH);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                assertEquals(first.symbolAt(row, col), second.symbolAt(row, col));
            }
        }
    }

    @Test
    void changingHeightChangesOnlyVisibleOffsetsForTheSameStops() {
        int[] stops = {33, 66, 12};
        SlotsOutcome five = SlotsSpinGenerator.outcomeFromStops(stops, 5, SlotsVariance.BALANCED);
        SlotsOutcome three = SlotsSpinGenerator.outcomeFromStops(stops, 3, SlotsVariance.BALANCED);
        SlotsOutcome one = SlotsSpinGenerator.outcomeFromStops(stops, 1, SlotsVariance.BALANCED);

        // Height 3's middle row is height 5's centre row; height 1 is that
        // same centre row again -- all three windows share the same centre
        // because they are all centred on the identical committed stops.
        for (int col = 0; col < stops.length; col++) {
            assertEquals(five.symbolAt(2, col), three.symbolAt(1, col));
            assertEquals(five.symbolAt(2, col), one.symbolAt(0, col));
        }
    }

    @Test
    void invalidGeometryOrStopsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.generateFromStrips(4, 3, scripted(0, 0, 0, 0), SlotsVariance.BALANCED));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.generateFromStrips(3, 2, scripted(0, 0, 0), SlotsVariance.BALANCED));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.outcomeFromStops(new int[] {1, 2}, 3, SlotsVariance.BALANCED));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.outcomeFromStops(new int[] {1, 2, 3}, 4, SlotsVariance.BALANCED));
    }

    // ---- committed-stop reconstruction boundary is strict (final three fixes, Section 3) ----

    @Test
    void negativeStopIsRejectedDuringReconstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.outcomeFromStops(new int[] {0, -1, 5}, 3, SlotsVariance.BALANCED));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.outcomeFromStops(new int[] {-1}, 3, SlotsVariance.BALANCED));
    }

    @Test
    void stopEqualToStripSizeIsRejectedDuringReconstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsSpinGenerator.outcomeFromStops(
                new int[] {0, SlotsReelStrip.SIZE, 5}, 3, SlotsVariance.BALANCED));
    }

    @Test
    void zeroAndSizeMinusOneRemainValidBoundaryStops() {
        // The strict boundary is [0, SIZE) -- 0 and SIZE - 1 are the two
        // legal extremes, not off-by-one casualties of the new check.
        int[] stops = {0, SlotsReelStrip.SIZE - 1, 50};
        SlotsOutcome outcome = SlotsSpinGenerator.outcomeFromStops(stops, 3, SlotsVariance.BALANCED);
        assertEquals(3, outcome.columns());
    }

    @Test
    void reelStripSymbolAtStillWrapsCircularlyForOutOfRangeIndexes() {
        // Only the committed-stop reconstruction boundary became strict --
        // SlotsReelStrip's own circular indexing (its normal, in-bounds
        // mode of operation for animation/window math) is unaffected.
        SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0);
        assertEquals(strip.symbolAt(0), strip.symbolAt(-SlotsReelStrip.SIZE));
        assertEquals(strip.symbolAt(0), strip.symbolAt(SlotsReelStrip.SIZE));
        assertEquals(strip.symbolAt(SlotsReelStrip.SIZE - 1), strip.symbolAt(-1));
    }
}
