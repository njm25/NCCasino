package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsSymbolTest {

    @Test
    @DisplayName("weights sum to the declared total")
    void weightsSumToTotal() {
        int sum = 0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            assertTrue(symbol.weight() > 0, symbol + " must have a positive weight");
            sum += symbol.weight();
        }
        assertEquals(SlotsSymbol.TOTAL_WEIGHT, sum);
    }

    @Test
    @DisplayName("probabilities form a complete distribution")
    void probabilitiesSumToOne() {
        double total = 0.0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            total += symbol.probability();
        }
        assertEquals(1.0, total, 1e-12);
    }

    @Test
    @DisplayName("only SEEDS is non-paying, and it is the most common symbol")
    void seedsIsTheOnlyNonPayingSymbol() {
        assertFalse(SlotsSymbol.SEEDS.pays());
        assertEquals(0, SlotsSymbol.SEEDS.minimumRun());
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            assertTrue(symbol.pays(), symbol + " must pay");
            assertTrue(symbol.minimumRun() >= 2, symbol + " must need at least a pair");
            assertTrue(symbol.weight() < SlotsSymbol.SEEDS.weight(),
                "SEEDS must be more common than " + symbol + " so runs actually break");
        }
    }

    @Test
    @DisplayName("rarer symbols pay more and the pay order matches the weight order")
    void rarityTracksPayout() {
        SlotsSymbol[] ascending = SlotsSymbol.payingSymbols();
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(ascending[i].weight() < ascending[i - 1].weight(),
                ascending[i] + " must be rarer than " + ascending[i - 1]);
            assertTrue(ascending[i].payWeight() > ascending[i - 1].payWeight(),
                ascending[i] + " must be worth more than " + ascending[i - 1]);
        }
    }

    @Test
    @DisplayName("cherry is the only symbol paying from a pair")
    void onlyCherryPaysFromTwo() {
        assertEquals(2, SlotsSymbol.CHERRY.minimumRun());
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            if (symbol != SlotsSymbol.CHERRY) {
                assertEquals(3, symbol.minimumRun(), symbol + " must need three in a row");
            }
        }
        assertEquals(SlotsSymbol.GLOBAL_MIN_RUN, SlotsSymbol.CHERRY.minimumRun(),
            "the length-factor curve must be anchored at the shortest paying run");
    }

    @Test
    @DisplayName("every symbol has a distinct material to render with")
    void materialsAreDistinct() {
        for (SlotsSymbol a : SlotsSymbol.values()) {
            for (SlotsSymbol b : SlotsSymbol.values()) {
                if (a != b) {
                    assertFalse(a.material() == b.material(),
                        a + " and " + b + " must not share a material");
                }
            }
        }
    }
}
