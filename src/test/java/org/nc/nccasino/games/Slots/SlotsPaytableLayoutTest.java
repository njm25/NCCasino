package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layout and payout invariants behind the compact Slots paytable. */
class SlotsPaytableLayoutTest {

    private static final int WIDTH = SlotsGeometry.INVENTORY_WIDTH;

    @Test
    void paytableOwnsFourRowsAndLeavesTheHopperRailAlone() {
        int[] owned = SlotsPaytableLayout.paytableCanvasSlots();
        assertEquals(36, owned.length);
        assertEquals(0, owned[0]);
        assertEquals(35, owned[owned.length - 1]);
        for (int slot : owned) {
            assertFalse(SlotsInfoRail.isRailSlot(slot));
        }
    }

    @Test
    void rainbowMarqueeIsExactlyTheTopAndBottomPaytableRows() {
        for (int slot : SlotsPaytableLayout.paytableCanvasSlots()) {
            int row = slot / WIDTH;
            assertEquals(row == 0 || row == 3,
                SlotsPaytableLayout.isRainbowFrameSlot(slot), "slot " + slot);
        }
        assertFalse(SlotsPaytableLayout.isRainbowFrameSlot(-1));
        assertFalse(SlotsPaytableLayout.isRainbowFrameSlot(36));
    }

    @Test
    void headerAndSupportCardsAreBalancedAroundTheCentre() {
        assertEquals(4, SlotsPaytableLayout.MACHINE_SLOT);
        assertEquals(20, SlotsPaytableLayout.SEEDS_SLOT);
        assertEquals(22, SlotsPaytableLayout.LEGEND_SLOT);
        assertEquals(24, SlotsPaytableLayout.VOLATILITY_SLOT);
        assertEquals(WIDTH / 2, SlotsPaytableLayout.MACHINE_SLOT % WIDTH);
        assertEquals(WIDTH / 2, SlotsPaytableLayout.LEGEND_SLOT % WIDTH);
        assertEquals(2,
            (SlotsPaytableLayout.LEGEND_SLOT % WIDTH) - (SlotsPaytableLayout.SEEDS_SLOT % WIDTH));
        assertEquals(2,
            (SlotsPaytableLayout.VOLATILITY_SLOT % WIDTH) - (SlotsPaytableLayout.LEGEND_SLOT % WIDTH));
    }

    @Test
    void thePaytableHeaderPanesAreOrdinaryRainbowFrame() {
        // They briefly carried the Sound Lab's audition tiles. With the Lab
        // gone they are decoration again, and the centred machine card is the
        // only thing that interrupts the top row.
        for (int slot : new int[] {1, 2, 3, 5, 6, 7}) {
            assertTrue(SlotsPaytableLayout.isRainbowFrameSlot(slot),
                "slot " + slot + " is part of the marquee frame");
        }
        assertEquals(4, SlotsPaytableLayout.MACHINE_SLOT);
        assertTrue(SlotsPaytableLayout.isRainbowFrameSlot(SlotsPaytableLayout.MACHINE_SLOT));
    }

    @Test
    void todaysFiveSymbolsFormOneCentredUnbrokenBand() {
        int[] slots = SlotsPaytableLayout.symbolCardSlots(SlotsSymbol.payingSymbols().length);
        assertArrayContentEquals(new int[] {11, 12, 13, 14, 15}, slots);
        assertTrue(contains(slots, 13));
    }

    @Test
    void everySupportedCardCountStaysCentredInTheSymbolRow() {
        Set<Integer> support = Set.of(
            SlotsPaytableLayout.MACHINE_SLOT,
            SlotsPaytableLayout.SEEDS_SLOT,
            SlotsPaytableLayout.LEGEND_SLOT,
            SlotsPaytableLayout.VOLATILITY_SLOT);

        for (int count = 0; count <= SlotsPaytableLayout.cardCapacity(); count++) {
            int[] slots = SlotsPaytableLayout.symbolCardSlots(count);
            assertEquals(count, slots.length);
            Set<Integer> seen = new HashSet<>();
            for (int slot : slots) {
                assertEquals(1, slot / WIDTH);
                assertTrue(seen.add(slot));
                assertFalse(support.contains(slot));
                assertFalse(SlotsInfoRail.isRailSlot(slot));
            }
            if (count > 0) {
                int leftSpace = slots[0] % WIDTH;
                int rightSpace = WIDTH - 1 - slots[slots.length - 1] % WIDTH;
                assertTrue(Math.abs(leftSpace - rightSpace) <= 1);
                for (int i = 1; i < slots.length; i++) {
                    assertEquals(slots[i - 1] + 1, slots[i]);
                }
            }
        }
    }

    @Test
    void invalidCardCountsFailInsteadOfDroppingSymbols() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsPaytableLayout.symbolCardSlots(SlotsPaytableLayout.cardCapacity() + 1));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsPaytableLayout.symbolCardSlots(-1));
        assertTrue(SlotsSymbol.payingSymbols().length <= SlotsPaytableLayout.cardCapacity());
    }

    @Test
    void sevenUsesACompactRedIconRatherThanARedstoneBlock() {
        assertEquals(Material.RED_DYE, SlotsSymbol.SEVEN.material());
    }

    @Test
    void cardsListExactlyTheRunsAvailableAtEachReelCount() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
                List<Integer> runs = achievableRuns(symbol, columns, paytable);
                assertFalse(runs.isEmpty(), symbol + " must pay at " + columns + " reels");
                assertEquals(symbol.minimumRun(), runs.get(0));
                assertEquals(columns, runs.get(runs.size() - 1));
                assertEquals(columns - symbol.minimumRun() + 1, runs.size());
            }
        }
    }

    @Test
    void noCardAdvertisesARunLongerThanTheMachine() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                for (int run = columns + 1; run <= columns + 4; run++) {
                    assertEquals(0.0, paytable.multiplier(symbol, run), 1e-12);
                }
            }
        }
    }

    @Test
    void seedsNeverAppearsAmongPayingSymbols() {
        assertEquals(0.0, SlotsSymbol.SEEDS.payWeight(), 1e-12);
        assertEquals(0, SlotsSymbol.SEEDS.minimumRun());
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            assertFalse(symbol == SlotsSymbol.SEEDS);
        }
    }

    @Test
    void longerRunsNeverReturnLessForTheSameSymbol() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
                double previous = -1.0;
                for (int run = symbol.minimumRun(); run <= columns; run++) {
                    double multiplier = paytable.multiplier(symbol, run);
                    assertTrue(multiplier >= previous);
                    previous = multiplier;
                }
            }
        }
    }

    private static List<Integer> achievableRuns(
            SlotsSymbol symbol, int columns, SlotsPaytable paytable) {
        List<Integer> runs = new ArrayList<>();
        for (int run = Math.max(1, symbol.minimumRun()); run <= columns; run++) {
            if (paytable.multiplier(symbol, run) > 0.0) {
                runs.add(run);
            }
        }
        return runs;
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static void assertArrayContentEquals(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "index " + i);
        }
    }
}
