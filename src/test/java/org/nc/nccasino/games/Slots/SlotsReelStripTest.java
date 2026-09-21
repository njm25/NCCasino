package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SlotsReelStripTest {

    @Test
    void everyStripHasExactlyOneHundredStops() {
        for (SlotsVariance variance : SlotsVariance.values()) {
            for (int reel = 0; reel < 7; reel++) {
                assertEquals(100, SlotsReelStrip.forReel(variance, reel).size());
            }
        }
    }

    @Test
    void everyStripsSymbolCountsMatchVarianceWeightsExactly() {
        for (SlotsVariance variance : SlotsVariance.values()) {
            for (int reel = 0; reel < 7; reel++) {
                SlotsReelStrip strip = SlotsReelStrip.forReel(variance, reel);
                for (SlotsSymbol symbol : SlotsSymbol.values()) {
                    assertEquals(variance.weight(symbol), strip.countOf(symbol),
                        "variance=" + variance + " reel=" + reel + " symbol=" + symbol);
                }
            }
        }
    }

    @Test
    void noStopIsNull() {
        SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.HIGH_ROLLER, 3);
        for (int i = 0; i < strip.size(); i++) {
            assertNotNull(strip.symbolAt(i));
        }
    }

    @Test
    void theSequenceIsStableAndReproducible() {
        SlotsReelStrip first = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 2);
        SlotsReelStrip second = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 2);
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            assertEquals(first.symbolAt(i), second.symbolAt(i));
        }
    }

    @Test
    void differentReelIndexesAreStableButNotIdentical() {
        // Deliberately narrow: this only proves the two reels are not
        // numbered identically (no two reels always land on the same stop
        // for the same draw). It does NOT prove they are genuinely distinct
        // circular orderings -- every reel is a rotation of one shared base
        // sequence (a documented, deferred design choice; see
        // SlotsReelStrip's class doc, "Deferred design choice").
        SlotsReelStrip reel0 = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0);
        SlotsReelStrip reel1 = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 1);
        boolean anyDifferent = false;
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            if (reel0.symbolAt(i) != reel1.symbolAt(i)) {
                anyDifferent = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(anyDifferent,
            "different reel indexes should not produce an identical sequence");
        // But both remain internally consistent (same counts) -- checked above.
    }

    @Test
    void circularIndexingHandlesNegativeAndOverflowingOffsets() {
        SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0);
        assertEquals(strip.symbolAt(0), strip.symbolAt(100));
        assertEquals(strip.symbolAt(0), strip.symbolAt(-100));
        assertEquals(strip.symbolAt(99), strip.symbolAt(-1));
        assertEquals(strip.symbolAt(1), strip.symbolAt(101));
        assertEquals(strip.symbolAt(98), strip.symbolAt(-2));
    }

    @Test
    void windowOffsetsAreCorrectPerVisibleHeight() {
        assertArrayEquals(new int[] {0}, SlotsReelStrip.offsetsFor(1));
        assertArrayEquals(new int[] {-1, 0, 1}, SlotsReelStrip.offsetsFor(3));
        assertArrayEquals(new int[] {-2, -1, 0, 1, 2}, SlotsReelStrip.offsetsFor(5));
    }

    @Test
    void windowsAreCenteredOnTheSelectedStopAtEveryHeight() {
        SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.BALANCED, 4);
        for (int rows : SlotsGeometry.supportedRowCounts()) {
            for (int stop : new int[] {0, 1, 50, 98, 99}) {
                SlotsSymbol[] window = strip.window(stop, rows);
                assertEquals(rows, window.length);
                int[] offsets = SlotsReelStrip.offsetsFor(rows);
                for (int i = 0; i < offsets.length; i++) {
                    assertEquals(strip.symbolAt(stop + offsets[i]), window[i]);
                }
            }
        }
    }

    @Test
    void windowsWrapCorrectlyAtBothStripBoundaries() {
        SlotsReelStrip strip = SlotsReelStrip.forReel(SlotsVariance.HIGH, 1);
        SlotsSymbol[] atZero = strip.window(0, 5);
        assertEquals(strip.symbolAt(-2), atZero[0]);
        assertEquals(strip.symbolAt(-1), atZero[1]);
        assertEquals(strip.symbolAt(0), atZero[2]);
        assertEquals(strip.symbolAt(1), atZero[3]);
        assertEquals(strip.symbolAt(2), atZero[4]);

        SlotsSymbol[] atNinetyNine = strip.window(99, 3);
        assertEquals(strip.symbolAt(98), atNinetyNine[0]);
        assertEquals(strip.symbolAt(99), atNinetyNine[1]);
        assertEquals(strip.symbolAt(0), atNinetyNine[2]);
    }

    // ---- distribution properties (redesign audit Section 6) --------------
    //
    // Split from a single overloaded test into four correctly-scoped ones.
    // Every statistic here is computed circularly (the strip wraps at index
    // 99 -> 0) and, because every reel is a rotation of the same base
    // sequence, is identical regardless of which reelIndex it is measured
    // on -- reel 0 is used throughout as the representative case.

    /** Circular positions of one symbol's occurrences, in strip order. */
    private static java.util.List<Integer> positionsOf(SlotsReelStrip strip, SlotsSymbol symbol) {
        java.util.List<Integer> positions = new java.util.ArrayList<>();
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            if (strip.symbolAt(i) == symbol) {
                positions.add(i);
            }
        }
        return positions;
    }

    /** The largest circular gap between consecutive occurrences of {@code symbol}. */
    private static int maxCircularGap(SlotsReelStrip strip, SlotsSymbol symbol) {
        java.util.List<Integer> positions = positionsOf(strip, symbol);
        int max = 0;
        for (int i = 0; i < positions.size(); i++) {
            int a = positions.get(i);
            int b = positions.get((i + 1) % positions.size());
            int gap = Math.floorMod(b - a, SlotsReelStrip.SIZE);
            if (gap == 0) {
                gap = SlotsReelStrip.SIZE;
            }
            max = Math.max(max, gap);
        }
        return max;
    }

    /** The smallest circular gap between consecutive occurrences of {@code symbol}. */
    private static int minCircularGap(SlotsReelStrip strip, SlotsSymbol symbol) {
        java.util.List<Integer> positions = positionsOf(strip, symbol);
        int min = SlotsReelStrip.SIZE;
        for (int i = 0; i < positions.size(); i++) {
            int a = positions.get(i);
            int b = positions.get((i + 1) % positions.size());
            int gap = Math.floorMod(b - a, SlotsReelStrip.SIZE);
            if (gap == 0) {
                gap = SlotsReelStrip.SIZE;
            }
            min = Math.min(min, gap);
        }
        return min;
    }

    /**
     * The longest run of consecutive equal stops anywhere on the circle,
     * across every symbol -- correctly counting a run that crosses the
     * index-99-to-0 boundary rather than treating the strip as a flat array.
     */
    private static int maxCircularRunOfAnySymbol(SlotsReelStrip strip) {
        int max = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            max = Math.max(max, maxCircularRunOf(strip, symbol));
        }
        return max;
    }

    /** The longest run of consecutive {@code symbol} stops, wrapping across 99 -> 0. */
    private static int maxCircularRunOf(SlotsReelStrip strip, SlotsSymbol symbol) {
        boolean allSame = true;
        for (int i = 0; i < SlotsReelStrip.SIZE && allSame; i++) {
            allSame = strip.symbolAt(i) == symbol;
        }
        if (allSame) {
            return SlotsReelStrip.SIZE;
        }
        // Find a boundary where the run breaks, then scan linearly from
        // there -- avoids double-counting a run that wraps past index 99.
        int start = -1;
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            if (strip.symbolAt(i) == symbol && strip.symbolAt(i - 1) != symbol) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return 0;
        }
        int max = 0;
        int current = 0;
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            if (strip.symbolAt(start + i) == symbol) {
                current++;
                max = Math.max(max, current);
            } else {
                current = 0;
            }
        }
        return max;
    }

    @Test
    void maxGapStaysWithinFairShareOfIdealSpacingForEveryVariance() {
        // A property of the even-distribution construction: no symbol should
        // ever leave an excessively large symbol-free region between two of
        // its circular occurrences -- more than a generous multiple of its
        // ideal spacing (SIZE / count). This catches gross under-distribution
        // (a symbol going unusually long without appearing); it says nothing
        // about occurrences clustering close together, which is a separate
        // property (minimum gap / maximum run, below).
        for (SlotsVariance variance : SlotsVariance.values()) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(variance, 0);
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                int count = variance.weight(symbol);
                if (count <= 1) {
                    continue;
                }
                double idealSpacing = (double) SlotsReelStrip.SIZE / count;
                int maxGap = maxCircularGap(strip, symbol);
                // Generous slack over the ideal spacing: this is a sanity
                // bound against gross under-distribution, not an
                // exact-uniformity requirement.
                org.junit.jupiter.api.Assertions.assertTrue(maxGap <= idealSpacing * 2.5 + 2,
                    "variance=" + variance + " symbol=" + symbol
                        + ": max gap " + maxGap + " far exceeds ideal spacing " + idealSpacing);
            }
        }
    }

    /**
     * Measured minimum circular gap between consecutive occurrences of one
     * symbol, per variance -- reel 0's pinned base sequence (see
     * {@link #reel0SequenceSnapshotsAreExactlyPinnedPerVariance}), which is
     * rotation-invariant so it applies to every reel. Recorded here as an
     * explicit drift contract: a future change to the placement algorithm
     * that clusters a symbol tighter than this would fail here even though
     * every occurrence still sits at a distinct index (the flaw in the test
     * this replaces).
     */
    private static int measuredMinGap(SlotsVariance variance, SlotsSymbol symbol) {
        return switch (variance) {
            case STEADY -> switch (symbol) {
                case CHERRY -> 1; case DIAMOND -> 9; case BELL -> 5;
                case SEEDS -> 4; case LEMON -> 3; case SEVEN -> 24;
            };
            case LOW -> switch (symbol) {
                case CHERRY -> 1; case DIAMOND -> 8; case BELL -> 6;
                case SEEDS -> 2; case LEMON -> 5; case SEVEN -> 20;
            };
            case BALANCED -> switch (symbol) {
                case CHERRY -> 3; case DIAMOND -> 9; case BELL -> 7;
                case SEEDS -> 1; case LEMON -> 5; case SEVEN -> 15;
            };
            case HIGH -> switch (symbol) {
                case CHERRY -> 3; case DIAMOND -> 8; case BELL -> 6;
                case SEEDS -> 1; case LEMON -> 4; case SEVEN -> 13;
            };
            case HIGH_ROLLER -> switch (symbol) {
                case CHERRY -> 6; case DIAMOND -> 9; case BELL -> 9;
                case SEEDS -> 1; case LEMON -> 6; case SEVEN -> 10;
            };
        };
    }

    @Test
    void minimumCircularGapMatchesMeasuredValuesAndCatchesNewClustering() {
        // Replaces a tautological predecessor: collecting occurrence indexes
        // from a plain array and taking their minimum circular distance can
        // never be less than 1 (distinct indexes are always >= 1 apart), so
        // that test could not have detected any amount of clustering -- every
        // copy could have been adjacent and it would still have passed.
        //
        // This asserts the actual measured minimum per variance/symbol
        // against the pinned base sequence, so genuine new clustering (a
        // future placement-algorithm change that packs a symbol's copies
        // tighter than today) changes this number and fails here.
        //
        // Several entries below measure exactly 1 (e.g. every variance's
        // SEEDS, and CHERRY in STEADY/LOW): the even-distribution
        // construction merges every symbol's "ideal positions" in position
        // order, and for a sufficiently common symbol two of those ideal
        // positions can legitimately round into adjacent slots even well
        // below a strict majority share. A minimum gap of 1 is already the
        // floor -- it cannot decrease any further, so this assertion cannot
        // detect those specific entries clustering any *worse* than they
        // already do; it only pins today's exact value as a drift record.
        // The maximum circular run bound
        // (maximumConsecutiveSameSymbolRunNeverExceedsFour) and the exact
        // per-variance SEEDS-run bound
        // (maximumSeedsRunPerVarianceMatchesMeasuredValues) are what actually
        // constrain those high-frequency symbols' clustering.
        for (SlotsVariance variance : SlotsVariance.values()) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(variance, 0);
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                if (variance.weight(symbol) <= 1) {
                    continue;
                }
                int minGap = minCircularGap(strip, symbol);
                assertEquals(measuredMinGap(variance, symbol), minGap,
                    "variance=" + variance + " symbol=" + symbol);
            }
        }
    }

    @Test
    void maximumConsecutiveSameSymbolRunNeverExceedsFour() {
        // Measured maximum across every approved variance's actual pinned
        // sequence (see reel0SequenceSnapshotsAreExactlyPinnedPerVariance)
        // is 4, on HIGH_ROLLER's SEEDS symbol -- not an invented number.
        for (SlotsVariance variance : SlotsVariance.values()) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(variance, 0);
            int maxRun = maxCircularRunOfAnySymbol(strip);
            org.junit.jupiter.api.Assertions.assertTrue(maxRun <= 4,
                "variance=" + variance + ": longest same-symbol run was " + maxRun);
        }
    }

    @Test
    void maximumConsecutiveRunCountingExplicitlyIncludesTheStripWrapBoundary() {
        // Every approved variance's base sequence happens to have the same
        // symbol at both index 99 and index 0 -- so a run-length count that
        // (incorrectly) treated the strip as a flat, non-wrapping array would
        // silently under-count a run that actually crosses the boundary.
        // This proves the wrap is genuinely exercised, not just coincidentally
        // absent from the data.
        for (SlotsVariance variance : SlotsVariance.values()) {
            SlotsReelStrip strip = SlotsReelStrip.forReel(variance, 0);
            SlotsSymbol boundarySymbol = strip.symbolAt(99);
            assertEquals(boundarySymbol, strip.symbolAt(0),
                "variance=" + variance + ": expected the pinned sequence to wrap on the same symbol");
            int wrappingRun = maxCircularRunOf(strip, boundarySymbol);
            org.junit.jupiter.api.Assertions.assertTrue(wrappingRun >= 2,
                "variance=" + variance + ": wrap-crossing run should count as at least 2, was " + wrappingRun);
        }
    }

    @Test
    void maximumSeedsRunPerVarianceMatchesMeasuredValues() {
        // Exact values measured directly from each variance's pinned reel-0
        // sequence -- not invented thresholds. SEEDS's run length grows with
        // its share of the 100 stops (see SlotsVariance's weights), peaking
        // at HIGH_ROLLER where SEEDS is the large majority symbol.
        assertEquals(1, maxCircularRunOf(SlotsReelStrip.forReel(SlotsVariance.STEADY, 0), SlotsSymbol.SEEDS));
        assertEquals(1, maxCircularRunOf(SlotsReelStrip.forReel(SlotsVariance.LOW, 0), SlotsSymbol.SEEDS));
        assertEquals(2, maxCircularRunOf(SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0), SlotsSymbol.SEEDS));
        assertEquals(2, maxCircularRunOf(SlotsReelStrip.forReel(SlotsVariance.HIGH, 0), SlotsSymbol.SEEDS));
        assertEquals(4, maxCircularRunOf(SlotsReelStrip.forReel(SlotsVariance.HIGH_ROLLER, 0), SlotsSymbol.SEEDS));
    }

    // ---- exact sequence snapshots (Section 14 of the redesign audit) ----

    private static char code(SlotsSymbol symbol) {
        return switch (symbol) {
            case SEEDS -> 'K';
            case CHERRY -> 'C';
            case LEMON -> 'L';
            case BELL -> 'E';
            case DIAMOND -> 'D';
            case SEVEN -> 'S';
        };
    }

    private static String sequenceOf(SlotsReelStrip strip) {
        StringBuilder sb = new StringBuilder(SlotsReelStrip.SIZE);
        for (int i = 0; i < SlotsReelStrip.SIZE; i++) {
            sb.append(code(strip.symbolAt(i)));
        }
        return sb.toString();
    }

    /**
     * Pins the exact resulting stop order for reel 0 of every variance --
     * proof the deterministic even-distribution construction cannot drift
     * silently. Any future, deliberate change to this construction (e.g.
     * the genuinely-distinct-per-reel ordering deferred in Section 14's
     * class-doc note) is expected to require updating these literals, which
     * is the point: a snapshot test's job is to make that change visible,
     * not to forbid it.
     */
    @Test
    void reel0SequenceSnapshotsAreExactlyPinnedPerVariance() {
        assertEquals(
            "CLKECDLKCELCKSCLEDKCLCKECLKDCLECKLCEKSCLDCKLECKLCEDCLKCELKCDLCKSECLKCELCDKCLEKCLCKEDLCKSCLECKLDCEKLC",
            sequenceOf(SlotsReelStrip.forReel(SlotsVariance.STEADY, 0)));
        assertEquals(
            "CKLEDCKLCKSELCKDCLEKCKLCEDKLCKSELCKDCLKECKLCDKELCKSCLEKDCLKCEKLCDKCLEKSCLKCEDLKCKELCDKCLEKSCLKCDELKC",
            sequenceOf(SlotsReelStrip.forReel(SlotsVariance.LOW, 0)));
        assertEquals(
            "KCLEKDCKLSECKLKDCEKLCKKCLEDSKCLKECKDLKCEKLSCKDELCKKCLEKDCKLSECKLKDCEKLCKKCLEDSKCLKECKDLKCEKLSCKDELCK",
            sequenceOf(SlotsReelStrip.forReel(SlotsVariance.BALANCED, 0)));
        assertEquals(
            "KCLEKDKSCKLEKDCKLKECKSDLKCKEKLCDKEKSCLKKDCEKLKCKLEDSKCKLKECDKKLCSKEKDCLKEKCKLDSKCEKLKCDKELKCSKDKELCK",
            sequenceOf(SlotsReelStrip.forReel(SlotsVariance.HIGH, 0)));
        assertEquals(
            "KKCLEDKSKKKCLEDKKSKCLKEDKKKSCLKEDKKKCLKSEDKKKCLKKEDSKCLKKKEDKSCLKKKEDKCLKSKKEDKCLKKSKEDCLKKKKSEDCLKK",
            sequenceOf(SlotsReelStrip.forReel(SlotsVariance.HIGH_ROLLER, 0)));
    }
}
