package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deterministic coverage of weighted symbol selection using an injected
 * {@link SlotsRandomSource} -- production randomness is never exercised
 * here, only the pure cumulative-weight bucketing logic.
 */
class SlotsSpinGeneratorTest {

    private static SlotsRandomSource fixed(int value) {
        return bound -> value;
    }

    // Cumulative thresholds: CHERRY [0,40), LEMON [40,65), BELL [65,83), DIAMOND [83,94), SEVEN [94,100)

    @Test
    void rollZeroIsCherry() {
        assertEquals(SlotsSymbol.CHERRY, SlotsSpinGenerator.sampleSymbol(fixed(0)));
    }

    @Test
    void rollThirtyNineIsStillCherry() {
        assertEquals(SlotsSymbol.CHERRY, SlotsSpinGenerator.sampleSymbol(fixed(39)));
    }

    @Test
    void rollFortyIsFirstLemon() {
        assertEquals(SlotsSymbol.LEMON, SlotsSpinGenerator.sampleSymbol(fixed(40)));
    }

    @Test
    void rollSixtyFourIsLastLemon() {
        assertEquals(SlotsSymbol.LEMON, SlotsSpinGenerator.sampleSymbol(fixed(64)));
    }

    @Test
    void rollSixtyFiveIsFirstBell() {
        assertEquals(SlotsSymbol.BELL, SlotsSpinGenerator.sampleSymbol(fixed(65)));
    }

    @Test
    void rollEightyTwoIsLastBell() {
        assertEquals(SlotsSymbol.BELL, SlotsSpinGenerator.sampleSymbol(fixed(82)));
    }

    @Test
    void rollEightyThreeIsFirstDiamond() {
        assertEquals(SlotsSymbol.DIAMOND, SlotsSpinGenerator.sampleSymbol(fixed(83)));
    }

    @Test
    void rollNinetyThreeIsLastDiamond() {
        assertEquals(SlotsSymbol.DIAMOND, SlotsSpinGenerator.sampleSymbol(fixed(93)));
    }

    @Test
    void rollNinetyFourIsFirstSeven() {
        assertEquals(SlotsSymbol.SEVEN, SlotsSpinGenerator.sampleSymbol(fixed(94)));
    }

    @Test
    void rollNinetyNineIsLastSeven() {
        assertEquals(SlotsSymbol.SEVEN, SlotsSpinGenerator.sampleSymbol(fixed(99)));
    }

    @Test
    void generateProducesAFullyPopulatedThreeByThreeGrid() {
        SlotsOutcome outcome = SlotsSpinGenerator.generate(fixed(0));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals(SlotsSymbol.CHERRY, outcome.symbolAt(row, col));
            }
        }
    }

    @Test
    void nullRandomSourceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SlotsSpinGenerator.generate(null));
    }
}
