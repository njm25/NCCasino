package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the opening animation's pure fall/stagger math (Section 2 of the redesign). */
class SlotsOpeningColumnMotionTest {

    private static List<String> finalColumn(String label) {
        List<String> col = new ArrayList<>();
        for (int row = 0; row < SlotsOpeningColumnMotion.ROWS; row++) {
            col.add(label + "-row" + row);
        }
        return col;
    }

    @Test
    void shiftDownAndInsertMovesEveryCellDownAndDropsTheBottomOne() {
        String[] column = {"a", "b", "c", "d", "e", "f"};
        SlotsOpeningColumnMotion.shiftDownAndInsert(column, "new");
        assertEquals("new", column[0]);
        assertEquals("a", column[1]);
        assertEquals("b", column[2]);
        assertEquals("c", column[3]);
        assertEquals("d", column[4]);
        assertEquals("e", column[5]);
        // "f" fell off the bottom and is gone.
    }

    @Test
    void shiftDownAndInsertRejectsTheWrongColumnHeight() {
        String[] column = {"a", "b", "c"};
        assertThrows(IllegalArgumentException.class, () -> SlotsOpeningColumnMotion.shiftDownAndInsert(column, "x"));
    }

    @Test
    void entrySequenceFeedsAtLeastNineFillersBeforeAnyRealTarget() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequence(filler, finalColumn);

        assertTrue(entries.size() >= filler.size() + SlotsOpeningColumnMotion.ROWS);
        for (int i = 0; i < filler.size(); i++) {
            assertEquals(filler.get(i), entries.get(i));
        }
        for (int i = filler.size(); i < entries.size(); i++) {
            assertTrue(finalColumn.contains(entries.get(i)), "everything after the filler run must be a real target item");
        }
    }

    @Test
    void entrySequenceRejectsAFinalColumnOfTheWrongSize() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8");
        assertThrows(IllegalArgumentException.class,
            () -> SlotsOpeningColumnMotion.buildEntrySequence(filler, List.of("only-one")));
    }

    /**
     * The end-to-end proof that bottom-to-top target introduction order is
     * correct: feeding {@link SlotsOpeningColumnMotion#buildEntrySequence}'s
     * output through {@link SlotsOpeningColumnMotion#shiftDownAndInsert} one
     * entry at a time must leave the column reading top-to-bottom exactly as
     * the original final column was specified.
     */
    @Test
    void feedingTheEntrySequenceThroughShiftDownLandsTheExactFinalColumn() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequence(filler, finalColumn);

        String[] column = new String[SlotsOpeningColumnMotion.ROWS];
        for (String entry : entries) {
            SlotsOpeningColumnMotion.shiftDownAndInsert(column, entry);
        }

        assertEquals(finalColumn, java.util.Arrays.asList(column));
    }

    @Test
    void bottomTargetEntersFirstAndTopTargetEntersLast() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequence(filler, finalColumn);

        String firstTargetEntered = entries.get(filler.size());
        String lastEntered = entries.get(entries.size() - 1);
        assertEquals(finalColumn.get(SlotsOpeningColumnMotion.ROWS - 1), firstTargetEntered,
            "the bottom-row target must be the first real item introduced");
        assertEquals(finalColumn.get(0), lastEntered,
            "the top-row target must be the last item introduced");
    }

    @Test
    void staggerLeavesEachColumnInactiveBeforeItsOwnStart() {
        int entryCount = 15;
        long stagger = 2L;
        assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(3, stagger, 5L, entryCount),
            "column 3 (starts at tick 6) must not be active yet at tick 5");
        assertEquals(0, SlotsOpeningColumnMotion.localEntryIndexAt(3, stagger, 6L, entryCount));
        assertEquals(1, SlotsOpeningColumnMotion.localEntryIndexAt(3, stagger, 7L, entryCount));
    }

    @Test
    void staggerLeavesEachColumnInactiveAfterItsSequenceEnds() {
        int entryCount = 15;
        long stagger = 2L;
        long lastActiveTick = 0L + entryCount - 1;
        assertEquals(entryCount - 1, SlotsOpeningColumnMotion.localEntryIndexAt(0, stagger, lastActiveTick, entryCount));
        assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(0, stagger, lastActiveTick + 1, entryCount));
    }

    @Test
    void columnsOverlapRatherThanRunningOneAfterAnother() {
        // With a 2-tick stagger and a run far longer than the stagger,
        // columns 0 and 1 must both be simultaneously active well before
        // column 0 finishes -- proving overlap, not strict sequencing.
        int entryCount = 15;
        long stagger = 2L;
        long midTick = 6L;
        assertTrue(SlotsOpeningColumnMotion.localEntryIndexAt(0, stagger, midTick, entryCount) >= 0);
        assertTrue(SlotsOpeningColumnMotion.localEntryIndexAt(1, stagger, midTick, entryCount) >= 0);
    }

    @Test
    void settleSequenceFeedsTheCorrectSixTwiceBeforeLanding() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8");
        List<String> settleFiller = List.of("s0", "s1", "s2", "s3");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequenceWithSettle(filler, settleFiller, finalColumn);

        int expectedSize = filler.size() + SlotsOpeningColumnMotion.ROWS + settleFiller.size() + SlotsOpeningColumnMotion.ROWS;
        assertEquals(expectedSize, entries.size());

        // First pass of the correct six, immediately after the initial filler.
        int firstPassStart = filler.size();
        for (int row = SlotsOpeningColumnMotion.ROWS - 1, i = firstPassStart; row >= 0; row--, i++) {
            assertEquals(finalColumn.get(row), entries.get(i));
        }
        // Settle filler pushes that first pass off the bottom.
        int settleStart = firstPassStart + SlotsOpeningColumnMotion.ROWS;
        for (int i = 0; i < settleFiller.size(); i++) {
            assertEquals(settleFiller.get(i), entries.get(settleStart + i));
        }
        // Second pass of the correct six is the true, final tail of the sequence.
        int secondPassStart = settleStart + settleFiller.size();
        for (int row = SlotsOpeningColumnMotion.ROWS - 1, i = secondPassStart; row >= 0; row--, i++) {
            assertEquals(finalColumn.get(row), entries.get(i));
        }
        assertEquals(entries.size() - 1, secondPassStart + SlotsOpeningColumnMotion.ROWS - 1);
    }

    @Test
    void feedingTheSettleSequenceThroughShiftDownStillLandsTheExactFinalColumn() {
        // The first pass of the correct six must NOT be what ends up landed --
        // only the second pass, since it's pushed off the bottom by settleFiller
        // in between and the second pass is what's left standing at the end.
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9");
        List<String> settleFiller = List.of("s0", "s1", "s2", "s3", "s4");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequenceWithSettle(filler, settleFiller, finalColumn);

        String[] column = new String[SlotsOpeningColumnMotion.ROWS];
        for (String entry : entries) {
            SlotsOpeningColumnMotion.shiftDownAndInsert(column, entry);
        }

        assertEquals(finalColumn, java.util.Arrays.asList(column));
    }

    @Test
    void settleSequenceRejectsAFinalColumnOfTheWrongSize() {
        List<String> filler = List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8");
        List<String> settleFiller = List.of("s0", "s1", "s2", "s3");
        assertThrows(IllegalArgumentException.class,
            () -> SlotsOpeningColumnMotion.buildEntrySequenceWithSettle(filler, settleFiller, List.of("only-one")));
    }

    @Test
    void fixedRainbowUsedForBothFillerSegmentsProducesTheExpectedThirtyFourEntryShape() {
        // Real usage: SlotsMachine feeds SlotsOpeningFiller.fixedRainbowSequence()
        // (11 panes) as BOTH the filler and settleFiller segments, so every
        // column's stream is 11 + 6 + 11 + 6 = 34 entries, always -- no
        // per-column variance, which is what made every column's entry count
        // safe to index independently (the original crash came from columns
        // having different lengths when the settle burst was randomized).
        List<String> rainbow = List.of(
            "red", "orange", "yellow", "lime", "green", "blue", "cyan", "light-blue", "pink", "magenta", "purple");
        List<String> finalColumn = finalColumn("t");
        List<String> entries = SlotsOpeningColumnMotion.buildEntrySequenceWithSettle(rainbow, rainbow, finalColumn);

        assertEquals(34, entries.size());

        String[] column = new String[SlotsOpeningColumnMotion.ROWS];
        for (String entry : entries) {
            SlotsOpeningColumnMotion.shiftDownAndInsert(column, entry);
        }
        assertEquals(finalColumn, java.util.Arrays.asList(column));
    }

    @Test
    void finalTickAccountsForTheLastColumnsStaggerDelay() {
        int columnCount = 9;
        long stagger = 2L;
        int entryCount = 15;
        long expected = (columnCount - 1) * stagger + entryCount - 1;
        assertEquals(expected, SlotsOpeningColumnMotion.finalTick(columnCount, stagger, entryCount));
    }
}
