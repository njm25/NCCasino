package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BettingTableBetTypeTest {

    @Test
    void pageOneSpecialBetsKeepCanonicalIdentifiers() {
        assertSlots(1, 28, 31, "1st Dozen - 2:1");
        assertSlots(1, 32, 35, "2nd Dozen - 2:1");
        assertSlots(1, 37, 38, "1-18 - 1:1");
        assertSlots(1, 39, 40, "Even - 1:1");
        assertSlots(1, 41, 42, "Red - 1:1");
        assertSlots(1, 43, 44, "Black - 1:1");
    }

    @Test
    void pageTwoSpecialBetsKeepCanonicalIdentifiers() {
        assertEquals("Top Row - 2:1", BettingTable.canonicalBetType(2, 8, "translated"));
        assertEquals("Middle Row - 2:1", BettingTable.canonicalBetType(2, 17, "translated"));
        assertEquals("Bottom Row - 2:1", BettingTable.canonicalBetType(2, 26, "translated"));
        assertSlots(2, 27, 30, "2nd Dozen - 2:1");
        assertSlots(2, 31, 34, "3rd Dozen - 2:1");
        assertSlots(2, 36, 37, "Red - 1:1");
        assertSlots(2, 38, 39, "Black - 1:1");
        assertSlots(2, 40, 41, "Odd - 1:1");
        assertSlots(2, 42, 43, "19-36 - 1:1");
    }

    @Test
    void straightUpBetIdentifiersRemainTheirDisplayedNumbers() {
        assertEquals("17 - 35:1", BettingTable.canonicalBetType(1, 15, "17 - 35:1"));
        assertEquals("32 - 35:1", BettingTable.canonicalBetType(2, 15, "32 - 35:1"));
    }

    private void assertSlots(int page, int firstSlot, int lastSlot, String expected) {
        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            assertEquals(expected, BettingTable.canonicalBetType(page, slot, "translated"));
        }
    }
}
