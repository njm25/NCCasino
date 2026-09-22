package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsHouseEdgeInputTest {

    @Test
    void acceptsPercentAndConfigFractionForms() {
        assertParsed(0.025, "2.5");
        assertParsed(0.025, "2.5%");
        assertParsed(0.025, "0.025");
        assertParsed(0.025, "2,5%");
        assertParsed(0.01, "1");
        assertParsed(0.06, "6%");
    }

    @Test
    void rejectsMalformedNonFiniteAndOutOfRangeValues() {
        for (String input : new String[] {"", "%", "nope", "NaN", "Infinity",
                "0", "0.5", "0.009", "6.01", "6.01%", "0.061"}) {
            assertTrue(SlotsHouseEdgeInput.parse(input).isEmpty(), input);
        }
        assertTrue(SlotsHouseEdgeInput.parse(null).isEmpty());
    }

    private static void assertParsed(double expected, String input) {
        assertTrue(SlotsHouseEdgeInput.parse(input).isPresent(), input);
        assertEquals(expected, SlotsHouseEdgeInput.parse(input).getAsDouble(), 1e-12, input);
    }
}
