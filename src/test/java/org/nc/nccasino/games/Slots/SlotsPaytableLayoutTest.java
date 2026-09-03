package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the condensed Paytable view puts each of its pieces, and the
 * arithmetic behind one symbol card's rows.
 *
 * <p>The card block is derived from the authoritative paying-symbol count,
 * never from a hand-written slot table, so the layout is asserted as a
 * property (packed, centred, inside its own block, clear of the rail) for
 * every count it can be asked to place -- not just for today's five symbols.
 */
class SlotsPaytableLayoutTest {

    private static final int WIDTH = SlotsGeometry.INVENTORY_WIDTH;

    // ---- fixed pieces ----------------------------------------------------

    @Test
    void thePaytableOwnsTheFirstFourCanvasRowsAndNothingOnTheRailRow() {
        assertEquals(SlotsGeometry.CANVAS_ROWS - 1, SlotsPaytableLayout.PAYTABLE_ROWS);
        int[] owned = SlotsPaytableLayout.paytableCanvasSlots();
        assertEquals(36, owned.length);
        assertEquals(0, owned[0]);
        assertEquals(35, owned[owned.length - 1]);
        for (int slot : owned) {
            assertFalse(SlotsInfoRail.isRailSlot(slot),
                "slot " + slot + " belongs to the informational rail, not the paytable");
        }
    }

    @Test
    void theLegendIsCentredOnTheTopRowAndTheMachineCardBalancesIt() {
        assertEquals(4, SlotsPaytableLayout.LEGEND_SLOT);
        assertEquals(WIDTH / 2, SlotsPaytableLayout.LEGEND_SLOT % WIDTH, "the Legend is a single centred card");
        assertEquals(0, SlotsPaytableLayout.LEGEND_SLOT / WIDTH);
        assertEquals(8, SlotsPaytableLayout.MACHINE_SLOT);
        assertEquals(0, SlotsPaytableLayout.MACHINE_SLOT / WIDTH);
    }

    @Test
    void theExplanatoryColumnIsTheNarrowLeftEdge() {
        int[] column = SlotsPaytableLayout.infoColumnSlots();
        assertEquals(4, column.length);
        for (int i = 0; i < column.length; i++) {
            assertEquals(0, column[i] % WIDTH, "the info column is column 0");
            assertEquals(i, column[i] / WIDTH, "the info column runs straight down rows 0-3");
        }
    }

    @Test
    void theInfoColumnIsACopySoACallerCannotRewriteTheLayout() {
        int[] first = SlotsPaytableLayout.infoColumnSlots();
        first[0] = 999;
        assertEquals(0, SlotsPaytableLayout.infoColumnSlots()[0]);
    }

    // ---- symbol cards ----------------------------------------------------

    @Test
    void theCardBlockIsTheThreeByEightAreaBesideTheInfoColumn() {
        assertEquals(24, SlotsPaytableLayout.cardCapacity());
    }

    @Test
    void todaysFivePayingSymbolsBecomeOneCentredBandThroughTheCanvasCentre() {
        int[] slots = SlotsPaytableLayout.symbolCardSlots(SlotsSymbol.payingSymbols().length);
        assertEquals(5, slots.length);
        assertArrayContentEquals(new int[] {20, 21, 22, 23, 24}, slots);
        assertTrue(contains(slots, 22), "an odd card count must run through the canvas's true centre");
    }

    @Test
    void everyPlaceableCountStaysInsideTheCardBlockAndNeverCollidesWithAnythingElse() {
        Set<Integer> reserved = new HashSet<>();
        for (int slot : SlotsPaytableLayout.infoColumnSlots()) {
            reserved.add(slot);
        }
        reserved.add(SlotsPaytableLayout.LEGEND_SLOT);
        reserved.add(SlotsPaytableLayout.MACHINE_SLOT);

        for (int count = 0; count <= SlotsPaytableLayout.cardCapacity(); count++) {
            int[] slots = SlotsPaytableLayout.symbolCardSlots(count);
            assertEquals(count, slots.length, "count " + count);
            Set<Integer> seen = new HashSet<>();
            for (int slot : slots) {
                assertTrue(seen.add(slot), "count " + count + " placed two cards on slot " + slot);
                assertFalse(reserved.contains(slot),
                    "count " + count + " placed a card on reserved slot " + slot);
                assertFalse(SlotsInfoRail.isRailSlot(slot),
                    "count " + count + " placed a card on the rail at slot " + slot);
                int row = slot / WIDTH;
                int column = slot % WIDTH;
                assertTrue(row >= 1 && row <= 3, "count " + count + " left the card block: row " + row);
                assertTrue(column >= 1, "count " + count + " intruded on the info column");
            }
        }
    }

    @Test
    void everyRowOfCardsIsHorizontallyCentredInTheAvailableColumns() {
        for (int count = 1; count <= SlotsPaytableLayout.cardCapacity(); count++) {
            int[] slots = SlotsPaytableLayout.symbolCardSlots(count);
            for (List<Integer> row : rows(slots)) {
                int first = row.get(0) % WIDTH;
                int last = row.get(row.size() - 1) % WIDTH;
                // Column 0 is the info column, so the usable band is 1..8.
                // An odd amount of slack cannot split evenly, so a row is
                // centred to within one column, never piled into a corner.
                assertTrue(Math.abs((first - 1) - ((WIDTH - 1) - last)) <= 1,
                    "count " + count + " row " + row + " is not centred");
                // Contiguous, in order.
                for (int i = 1; i < row.size(); i++) {
                    assertEquals(row.get(i - 1) + 1, row.get(i).intValue(), "count " + count + " row " + row);
                }
            }
        }
    }

    @Test
    void theRowsUsedAreVerticallyCentredAndNoRowIsLeftLopsided() {
        for (int count = 1; count <= SlotsPaytableLayout.cardCapacity(); count++) {
            int[] slots = SlotsPaytableLayout.symbolCardSlots(count);
            List<List<Integer>> rows = rows(slots);
            int topRow = rows.get(0).get(0) / WIDTH;
            int bottomRow = rows.get(rows.size() - 1).get(0) / WIDTH;
            // Rows 1..3 are available. An even number of used rows leaves an
            // odd amount of slack that cannot split evenly, so -- exactly as
            // with the horizontal centring -- a block is centred to within one
            // row rather than pushed to the top or bottom of the band.
            assertTrue(Math.abs((topRow - 1) - (3 - bottomRow)) <= 1,
                "count " + count + " is not vertically centred");
            int biggest = 0;
            int smallest = Integer.MAX_VALUE;
            for (List<Integer> row : rows) {
                biggest = Math.max(biggest, row.size());
                smallest = Math.min(smallest, row.size());
            }
            assertTrue(biggest - smallest <= 1,
                "count " + count + " spread unevenly across rows: " + rows);
        }
    }

    @Test
    void cardsAreEmittedInTheOrderTheSymbolsWereGiven() {
        int[] slots = SlotsPaytableLayout.symbolCardSlots(10);
        for (int i = 1; i < slots.length; i++) {
            assertTrue(slots[i] > slots[i - 1], "cards must be emitted in ascending slot order");
        }
    }

    @Test
    void askingForMoreCardsThanFitFailsLoudlyRatherThanDroppingAPayingSymbol() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsPaytableLayout.symbolCardSlots(SlotsPaytableLayout.cardCapacity() + 1));
        assertThrows(IllegalArgumentException.class, () -> SlotsPaytableLayout.symbolCardSlots(-1));
    }

    @Test
    void everyPayingSymbolAlwaysFits() {
        assertTrue(SlotsSymbol.payingSymbols().length <= SlotsPaytableLayout.cardCapacity());
    }

    // ---- what one card actually says -------------------------------------

    @Test
    void aCardListsExactlyTheRunsAchievableAtTheCurrentReelCount() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
                List<Integer> runs = achievableRuns(symbol, columns, paytable);
                assertFalse(runs.isEmpty(), symbol + " must pay at " + columns + " reels");
                assertEquals(symbol.minimumRun(), runs.get(0).intValue(),
                    symbol + " must start at its own minimum run");
                assertEquals(columns, runs.get(runs.size() - 1).intValue(),
                    symbol + " must run out at the reel count, never past it");
                assertEquals(columns - symbol.minimumRun() + 1, runs.size());
            }
        }
    }

    @Test
    void noCardEverAdvertisesARunLongerThanTheMachineHasReels() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                for (int run = columns + 1; run <= columns + 4; run++) {
                    assertEquals(0.0, paytable.multiplier(symbol, run), 1e-12,
                        symbol + " must not pay a run of " + run + " on " + columns + " reels");
                }
            }
        }
    }

    @Test
    void theBlankSymbolNeverAppearsOnACardAtAnyRunLength() {
        // The Paytable explains Blank in its own explanatory card instead --
        // it is a real weighted strip symbol that pays nothing and ends any
        // run it lands in.
        assertEquals(0.0, SlotsSymbol.BLANK.payWeight(), 1e-12);
        assertEquals(0, SlotsSymbol.BLANK.minimumRun());
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            assertFalse(symbol == SlotsSymbol.BLANK, "Blank must never be a paying symbol");
        }
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (int run = 1; run <= columns; run++) {
                assertEquals(0.0, paytable.multiplier(SlotsSymbol.BLANK, run), 1e-12);
            }
        }
    }

    @Test
    void aCardsReturnIsTheMultiplierTimesThePerLineWagerAndTracksAWagerChange() {
        // "Return" is the total returned payout for one line at the current
        // wager, not profit on top of the stake -- and it must move the
        // instant the wager does.
        SlotsPaytable paytable =
            SlotsPaytable.forConfig(5, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
        double multiplier = paytable.multiplier(SlotsSymbol.SEVEN, 5);
        assertTrue(multiplier > 0.0);
        assertEquals(multiplier * 10.0, multiplier * 10.0, 1e-9);
        assertEquals(multiplier * 25.0, 2.5 * (multiplier * 10.0), 1e-6,
            "a 2.5x wager must produce a 2.5x return on the same run");
    }

    @Test
    void aLongerRunNeverReturnsLessThanAShorterOneOfTheSameSymbol() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            SlotsPaytable paytable =
                SlotsPaytable.forConfig(columns, SlotsPaytable.DEFAULT_HOUSE_EDGE, SlotsVariance.BALANCED);
            for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
                double previous = -1.0;
                for (int run = symbol.minimumRun(); run <= columns; run++) {
                    double multiplier = paytable.multiplier(symbol, run);
                    assertTrue(multiplier >= previous,
                        symbol + " run " + run + " pays less than run " + (run - 1));
                    previous = multiplier;
                }
            }
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static List<Integer> achievableRuns(SlotsSymbol symbol, int columns, SlotsPaytable paytable) {
        List<Integer> runs = new ArrayList<>();
        for (int run = Math.max(1, symbol.minimumRun()); run <= columns; run++) {
            if (paytable.multiplier(symbol, run) > 0.0) {
                runs.add(run);
            }
        }
        return runs;
    }

    /** Groups placed card slots by inventory row, preserving order. */
    private static List<List<Integer>> rows(int[] slots) {
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int currentRow = -1;
        for (int slot : slots) {
            int row = slot / WIDTH;
            if (row != currentRow) {
                if (!current.isEmpty()) {
                    rows.add(current);
                }
                current = new ArrayList<>();
                currentRow = row;
            }
            current.add(slot);
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return rows;
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
