package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsSpinGeneratorTest {

    private static SlotsRandomSource constantRoll(int roll) {
        return bound -> roll;
    }

    /**
     * Cumulative weight boundaries: BLANK[0,30) CHERRY[30,52) LEMON[52,70)
     * BELL[70,84) DIAMOND[84,94) SEVEN[94,100). Each boundary is pinned
     * explicitly so a future weight change cannot silently shift which symbol
     * owns an edge.
     */
    @Test
    @DisplayName("cumulative weight boundaries map to the expected symbols")
    void boundariesMapToExpectedSymbols() {
        assertEquals(SlotsSymbol.BLANK, SlotsSpinGenerator.sampleSymbol(0, constantRoll(0)));
        assertEquals(SlotsSymbol.BLANK, SlotsSpinGenerator.sampleSymbol(0, constantRoll(29)));
        assertEquals(SlotsSymbol.CHERRY, SlotsSpinGenerator.sampleSymbol(0, constantRoll(30)));
        assertEquals(SlotsSymbol.CHERRY, SlotsSpinGenerator.sampleSymbol(0, constantRoll(51)));
        assertEquals(SlotsSymbol.LEMON, SlotsSpinGenerator.sampleSymbol(0, constantRoll(52)));
        assertEquals(SlotsSymbol.LEMON, SlotsSpinGenerator.sampleSymbol(0, constantRoll(69)));
        assertEquals(SlotsSymbol.BELL, SlotsSpinGenerator.sampleSymbol(0, constantRoll(70)));
        assertEquals(SlotsSymbol.BELL, SlotsSpinGenerator.sampleSymbol(0, constantRoll(83)));
        assertEquals(SlotsSymbol.DIAMOND, SlotsSpinGenerator.sampleSymbol(0, constantRoll(84)));
        assertEquals(SlotsSymbol.DIAMOND, SlotsSpinGenerator.sampleSymbol(0, constantRoll(93)));
        assertEquals(SlotsSymbol.SEVEN, SlotsSpinGenerator.sampleSymbol(0, constantRoll(94)));
        assertEquals(SlotsSymbol.SEVEN, SlotsSpinGenerator.sampleSymbol(0, constantRoll(99)));
    }

    @Test
    @DisplayName("generated grids are the requested width and fully populated")
    void gridsAreWellFormed() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsOutcome outcome = SlotsSpinGenerator.generate(columns, constantRoll(95));
            assertEquals(columns, outcome.columns());
            for (int row = 0; row < SlotsGeometry.ROWS; row++) {
                for (int col = 0; col < columns; col++) {
                    assertNotNull(outcome.symbolAt(row, col));
                    assertEquals(SlotsSymbol.SEVEN, outcome.symbolAt(row, col));
                }
            }
        }
    }

    @Test
    @DisplayName("every reel currently shares one weight table")
    void reelWeightsAreUniformForNow() {
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            for (int col = 0; col < SlotsGeometry.MAX_COLUMNS; col++) {
                assertEquals(symbol.weight(), SlotsSpinGenerator.reelWeight(symbol, col),
                    "per-reel weighting is a future seam; today every reel matches the base table");
            }
        }
    }

    @Test
    @DisplayName("sampling over many draws tracks the declared weights")
    void samplingTracksWeights() {
        Map<SlotsSymbol, Integer> counts = new EnumMap<>(SlotsSymbol.class);
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            counts.put(symbol, 0);
        }
        // Deterministic sweep across the whole roll space -- each roll value
        // exactly once, so observed counts must equal the declared weights.
        for (int roll = 0; roll < SlotsSymbol.TOTAL_WEIGHT; roll++) {
            SlotsSymbol sampled = SlotsSpinGenerator.sampleSymbol(0, constantRoll(roll));
            counts.merge(sampled, 1, Integer::sum);
        }
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            assertEquals(symbol.weight(), counts.get(symbol),
                symbol + " should own exactly its weight in roll values");
        }
    }

    @Test
    @DisplayName("invalid inputs are rejected")
    void invalidInputsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SlotsSpinGenerator.generate(3, null));
        assertThrows(IllegalArgumentException.class, () -> SlotsSpinGenerator.generate(4, constantRoll(0)));
        assertThrows(IllegalArgumentException.class, () -> SlotsSpinGenerator.generate(9, constantRoll(0)));
    }

    @Test
    @DisplayName("a real random source produces varied grids")
    void randomSourceProducesVariety() {
        SlotsRandomSource production = SlotsRandomSource.production();
        boolean sawDifference = false;
        SlotsOutcome first = SlotsSpinGenerator.generate(5, production);
        for (int attempt = 0; attempt < 50 && !sawDifference; attempt++) {
            SlotsOutcome next = SlotsSpinGenerator.generate(5, production);
            for (int row = 0; row < SlotsGeometry.ROWS && !sawDifference; row++) {
                for (int col = 0; col < 5 && !sawDifference; col++) {
                    if (first.symbolAt(row, col) != next.symbolAt(row, col)) {
                        sawDifference = true;
                    }
                }
            }
        }
        assertTrue(sawDifference, "50 production spins should not all be identical");
    }
}
