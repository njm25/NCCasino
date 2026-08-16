package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotsSymbolTest {

    @Test
    void weightsSumToExactlyOneHundred() {
        int total = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            total += symbol.weight();
        }
        assertEquals(SlotsSymbol.TOTAL_WEIGHT, total);
    }

    @Test
    void auditedWeightsAndMultipliersAreExact() {
        assertEquals(40, SlotsSymbol.CHERRY.weight());
        assertEquals(8, SlotsSymbol.CHERRY.multiplier());
        assertEquals(25, SlotsSymbol.LEMON.weight());
        assertEquals(13, SlotsSymbol.LEMON.multiplier());
        assertEquals(18, SlotsSymbol.BELL.weight());
        assertEquals(21, SlotsSymbol.BELL.multiplier());
        assertEquals(11, SlotsSymbol.DIAMOND.weight());
        assertEquals(39, SlotsSymbol.DIAMOND.multiplier());
        assertEquals(6, SlotsSymbol.SEVEN.weight());
        assertEquals(104, SlotsSymbol.SEVEN.multiplier());
    }
}
