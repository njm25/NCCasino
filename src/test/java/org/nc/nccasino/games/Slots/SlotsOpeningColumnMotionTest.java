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
        long start = SlotsOpeningColumnMotion.columnStartTick(3);
        assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(3, start - 1, entryCount),
            "a column must feed nothing on the tick before its own start");
        assertEquals(0, SlotsOpeningColumnMotion.localEntryIndexAt(3, start, entryCount));
        assertEquals(1, SlotsOpeningColumnMotion.localEntryIndexAt(3, start + SlotsTiming.OPENING_STEP_TICKS, entryCount));
    }

    @Test
    void staggerLeavesEachColumnInactiveAfterItsSequenceEnds() {
        int entryCount = 15;
        long lastActiveTick = SlotsOpeningColumnMotion.localTickOfEntry(entryCount - 1, entryCount);
        assertEquals(entryCount - 1, SlotsOpeningColumnMotion.localEntryIndexAt(0, lastActiveTick, entryCount));
        assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(0, lastActiveTick + 1, entryCount));
    }

    @Test
    void columnsOverlapRatherThanRunningOneAfterAnother() {
        // Column 1 has started well before column 0 has finished, so the two
        // genuinely overlap in time rather than running one after the other.
        int entryCount = 15;
        long columnOneStart = SlotsOpeningColumnMotion.columnStartTick(1);
        long columnZeroEnd = SlotsOpeningColumnMotion.localTickOfEntry(entryCount - 1, entryCount);
        assertTrue(columnOneStart < columnZeroEnd,
            "column 1 must start before column 0 lands its last entry");
    }

    @Test
    void columnsStartInStrictLeftToRightOrderOnAnUnevenCadence() {
        long previous = -1L;
        java.util.Set<Long> gaps = new java.util.HashSet<>();
        for (int column = 0; column < SlotsGeometry.INVENTORY_WIDTH; column++) {
            long start = SlotsOpeningColumnMotion.columnStartTick(column);
            if (previous >= 0) {
                assertTrue(start > previous,
                    "column " + column + " must start strictly after the column to its left");
                gaps.add(start - previous);
            }
            previous = start;
        }
        assertEquals(0L, SlotsOpeningColumnMotion.columnStartTick(0), "the leftmost column starts immediately");
        assertTrue(gaps.size() > 1,
            "the stagger must not be one flat interval -- that is what read as a metronome");
    }

    @Test
    void everyColumnStartsOnTheSameTicksEveryRun() {
        for (int column = 0; column < SlotsGeometry.INVENTORY_WIDTH; column++) {
            assertEquals(SlotsOpeningColumnMotion.columnStartTick(column),
                SlotsOpeningColumnMotion.columnStartTick(column),
                "the stagger is fixed, never randomized");
        }
    }

    @Test
    void aColumnRunsAtFullSpeedThenDeceleratesIntoItsLanding() {
        int entryCount = 30;
        // Everything before the deceleration tail advances one step per tick.
        int firstSlowIndex = entryCount - SlotsTiming.OPENING_DECELERATION_STEPS;
        for (int index = 1; index < firstSlowIndex; index++) {
            assertEquals(SlotsTiming.OPENING_STEP_TICKS,
                SlotsOpeningColumnMotion.stepTicksBefore(index, entryCount),
                "entry " + index + " must still be at full speed");
        }
        // The tail waits strictly longer at every successive step.
        long previous = SlotsTiming.OPENING_STEP_TICKS;
        for (int index = firstSlowIndex; index < entryCount; index++) {
            long step = SlotsOpeningColumnMotion.stepTicksBefore(index, entryCount);
            assertTrue(step > previous,
                "entry " + index + " must wait longer than the one before it");
            previous = step;
        }
    }

    @Test
    void theFirstEntryLandsOnTheColumnsOwnStartTick() {
        assertEquals(0L, SlotsOpeningColumnMotion.stepTicksBefore(0, 30));
        assertEquals(0L, SlotsOpeningColumnMotion.localTickOfEntry(0, 30));
    }

    @Test
    void aDeceleratingColumnFeedsNothingOnItsWaitingTicks() {
        int entryCount = 30;
        long lastTick = SlotsOpeningColumnMotion.localTickOfEntry(entryCount - 1, entryCount);
        long secondLastTick = SlotsOpeningColumnMotion.localTickOfEntry(entryCount - 2, entryCount);
        assertTrue(lastTick - secondLastTick > 1L, "the final step must span more than one tick");
        // Every tick strictly between the two is a deliberate no-op.
        for (long tick = secondLastTick + 1; tick < lastTick; tick++) {
            assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(0, tick, entryCount),
                "tick " + tick + " falls mid-wait and must feed nothing");
        }
        assertEquals(entryCount - 1, SlotsOpeningColumnMotion.localEntryIndexAt(0, lastTick, entryCount));
    }

    @Test
    void everyEntryStillFiresExactlyOnceAcrossTheWholeRun() {
        int entryCount = 30;
        long finalTick = SlotsOpeningColumnMotion.finalTick(SlotsGeometry.INVENTORY_WIDTH, entryCount);
        for (int column = 0; column < SlotsGeometry.INVENTORY_WIDTH; column++) {
            boolean[] seen = new boolean[entryCount];
            for (long tick = 0; tick <= finalTick; tick++) {
                int index = SlotsOpeningColumnMotion.localEntryIndexAt(column, tick, entryCount);
                if (index < 0) {
                    continue;
                }
                assertTrue(!seen[index], "entry " + index + " fired twice in column " + column);
                seen[index] = true;
            }
            for (int index = 0; index < entryCount; index++) {
                assertTrue(seen[index], "entry " + index + " never fired in column " + column);
            }
        }
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
    void finalTickAccountsForTheLastColumnsStaggerDelayAndItsDeceleration() {
        int columnCount = SlotsGeometry.INVENTORY_WIDTH;
        int entryCount = 15;
        long expected = SlotsOpeningColumnMotion.columnStartTick(columnCount - 1)
            + SlotsOpeningColumnMotion.localTickOfEntry(entryCount - 1, entryCount);
        assertEquals(expected, SlotsOpeningColumnMotion.finalTick(columnCount, entryCount));
        // The deceleration tail means the run outlasts a flat one-tick-per-entry
        // schedule; nothing may land after finalTick.
        assertTrue(expected > SlotsOpeningColumnMotion.columnStartTick(columnCount - 1) + entryCount - 1);
        assertEquals(-1, SlotsOpeningColumnMotion.localEntryIndexAt(columnCount - 1, expected + 1, entryCount));
    }
}
