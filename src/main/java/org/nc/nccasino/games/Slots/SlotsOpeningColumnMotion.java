package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, Bukkit-free motion math for the Slots opening animation: how one
 * inventory column's six-cell contents fall from the top, and how the nine
 * columns stagger left to right. {@link SlotsMachine}'s opening-animation
 * driver wires this to real scheduled ticks and real captured
 * {@code ItemStack}s; nothing here depends on Bukkit or on any live
 * inventory, so every rule can be pinned by a plain unit test.
 */
public final class SlotsOpeningColumnMotion {

    /** Every opening-animation column has exactly this many cells: the 5 canvas rows, then the control row. */
    public static final int ROWS = SlotsGeometry.CANVAS_ROWS + 1;

    private SlotsOpeningColumnMotion() {
    }

    /**
     * Shifts every existing cell down one row (the bottom cell falls off
     * the end and is discarded) and places {@code newTop} at row 0 -- the
     * animation's one visible motion, applied once per scheduled tick for
     * whichever column is currently receiving an entry.
     */
    public static <T> void shiftDownAndInsert(T[] column, T newTop) {
        if (column.length != ROWS) {
            throw new IllegalArgumentException("column must have exactly " + ROWS + " cells; got " + column.length);
        }
        for (int row = column.length - 1; row > 0; row--) {
            column[row] = column[row - 1];
        }
        column[0] = newTop;
    }

    /**
     * One column's full insertion sequence: every filler entry first (in
     * the order given), then the column's six real final items -- fed
     * bottom cell first, top cell last.
     *
     * <p>That bottom-to-top order is what {@link #shiftDownAndInsert}
     * requires to leave the column correctly arranged: each later insertion
     * pushes every earlier one down one row, so the item inserted last ends
     * up at the top and the item inserted first (of the six) ends up
     * deepest -- exactly at the bottom, once the six real items have fully
     * displaced the filler. After the whole sequence has been fed through
     * {@link #shiftDownAndInsert}, the column therefore reads top-to-bottom
     * exactly as {@code finalColumnTopToBottom} already has it.
     */
    public static <T> List<T> buildEntrySequence(List<T> filler, List<T> finalColumnTopToBottom) {
        if (finalColumnTopToBottom.size() != ROWS) {
            throw new IllegalArgumentException(
                "finalColumnTopToBottom must have exactly " + ROWS + " entries; got " + finalColumnTopToBottom.size());
        }
        List<T> entries = new ArrayList<>(filler.size() + ROWS);
        entries.addAll(filler);
        appendBottomToTop(entries, finalColumnTopToBottom);
        return entries;
    }

    /**
     * The "settle" variant of {@link #buildEntrySequence}: the correct final
     * six items are fed through the column <em>twice</em> instead of once,
     * so the column doesn't insta-stop the instant it first reads correctly.
     * The first pass (bottom-to-top, same as {@link #buildEntrySequence})
     * keeps going rather than being the animation's last word; {@code
     * settleFiller} then pushes that first pass off the bottom, and the
     * correct six are fed a second time -- that second pass is the one that
     * actually lands and stays, since nothing follows it.
     *
     * <p>Sequence shape: {@code filler + finalColumn(pass 1) + settleFiller
     * + finalColumn(pass 2)}. Feeding the result through
     * {@link #shiftDownAndInsert} one entry at a time still leaves the
     * column reading top-to-bottom exactly as {@code finalColumnTopToBottom}
     * -- the second pass alone determines the landed result, since it's the
     * last {@link #ROWS} entries in the sequence.
     */
    public static <T> List<T> buildEntrySequenceWithSettle(
            List<T> filler, List<T> settleFiller, List<T> finalColumnTopToBottom) {
        if (finalColumnTopToBottom.size() != ROWS) {
            throw new IllegalArgumentException(
                "finalColumnTopToBottom must have exactly " + ROWS + " entries; got " + finalColumnTopToBottom.size());
        }
        List<T> entries = new ArrayList<>(filler.size() + ROWS + settleFiller.size() + ROWS);
        entries.addAll(filler);
        appendBottomToTop(entries, finalColumnTopToBottom);
        entries.addAll(settleFiller);
        appendBottomToTop(entries, finalColumnTopToBottom);
        return entries;
    }

    private static <T> void appendBottomToTop(List<T> entries, List<T> finalColumnTopToBottom) {
        for (int row = ROWS - 1; row >= 0; row--) {
            entries.add(finalColumnTopToBottom.get(row));
        }
    }

    /**
     * The global tick on which {@code column} feeds its very first entry.
     * Accumulates {@link SlotsTiming#OPENING_COLUMN_STAGGER_GAPS} rather
     * than multiplying one flat interval, so the columns wake on an uneven
     * -- but entirely fixed -- left-to-right cadence. The pattern repeats if
     * there are ever more columns than gaps.
     */
    public static long columnStartTick(int column) {
        if (column < 0) {
            throw new IllegalArgumentException("column must not be negative; got " + column);
        }
        long[] gaps = SlotsTiming.OPENING_COLUMN_STAGGER_GAPS;
        long start = 0L;
        for (int index = 0; index < column; index++) {
            start += gaps[index % gaps.length];
        }
        return start;
    }

    /**
     * Ticks a column waits after feeding entry {@code localIndex - 1} before
     * it feeds entry {@code localIndex}: {@link SlotsTiming#OPENING_STEP_TICKS}
     * at full speed, then progressively longer over the final
     * {@link SlotsTiming#OPENING_DECELERATION_STEPS} entries so the column
     * eases to rest instead of stopping dead on its last frame. The very
     * first entry waits nothing -- it lands on the column's own start tick.
     */
    public static long stepTicksBefore(int localIndex, int entryCount) {
        if (localIndex <= 0) {
            return 0L;
        }
        int fromEnd = entryCount - 1 - localIndex;
        if (fromEnd >= SlotsTiming.OPENING_DECELERATION_STEPS) {
            return SlotsTiming.OPENING_STEP_TICKS;
        }
        long slowdown = (SlotsTiming.OPENING_DECELERATION_STEPS - fromEnd)
            * SlotsTiming.OPENING_DECELERATION_GROWTH_TICKS;
        return SlotsTiming.OPENING_STEP_TICKS + slowdown;
    }

    /** Ticks after a column's own start tick at which it feeds entry {@code localIndex}. */
    public static long localTickOfEntry(int localIndex, int entryCount) {
        long tick = 0L;
        for (int index = 0; index <= localIndex; index++) {
            tick += stepTicksBefore(index, entryCount);
        }
        return tick;
    }

    /**
     * The local entry index {@code column} feeds on exactly {@code globalTick},
     * or -1 if it feeds nothing then -- because the column has not started
     * yet, because it has already landed its last entry, or because it is
     * mid-wait between two entries while decelerating.
     *
     * <p>Unlike the flat-cadence version this replaced, most ticks are now a
     * deliberate no-op for any given column: the caller must shift the column
     * only on the ticks this method actually names one.
     */
    public static int localEntryIndexAt(int column, long globalTick, int entryCount) {
        long local = globalTick - columnStartTick(column);
        if (local < 0) {
            return -1;
        }
        long tick = 0L;
        for (int index = 0; index < entryCount; index++) {
            tick += stepTicksBefore(index, entryCount);
            if (tick == local) {
                return index;
            }
            if (tick > local) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * The global tick on which the whole animation -- every column, given
     * both the stagger and the deceleration -- finishes its very last
     * insertion (the rightmost column's last entry, since it starts latest).
     */
    public static long finalTick(int columnCount, int entryCount) {
        return columnStartTick(columnCount - 1) + localTickOfEntry(entryCount - 1, entryCount);
    }
}
