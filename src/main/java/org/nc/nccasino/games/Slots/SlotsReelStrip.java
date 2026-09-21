package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable, ordered, circular virtual reel strip: the redesign's
 * replacement for sampling every visible cell independently.
 *
 * <p>A real spin draws exactly one uniformly random <em>stop</em> per reel
 * (see {@link SlotsSpinGenerator}); the visible grid is then the contiguous
 * circular window of the strip centred on that stop. Because every stop is
 * equally likely and every fixed vertical offset therefore sees the strip's
 * marginal symbol distribution, this preserves the exact per-column
 * probabilities {@link SlotsPaytable} already prices against -- nothing about
 * the paytable derivation changes.
 *
 * <p>The strip's composition matches {@link SlotsVariance}'s weights exactly
 * (100 stops, one per weight unit). Its <em>order</em> is a deterministic,
 * documented construction -- never a runtime-shuffled {@link java.util.Random}
 * -- so the exact resulting sequence is reproducible and can be pinned by
 * snapshot tests. Stops are placed by spacing each symbol's copies as evenly
 * as possible around the circle (largest-remainder-style even distribution),
 * which both avoids long same-symbol runs and needs no randomness.
 *
 * <p><b>Deferred design choice (redesign audit Section 14):</b> every reel
 * is a rotation of the exact same base circular sequence, not an
 * independently-ordered strip. A rotation changes which stop <em>number</em>
 * lands on a given symbol, but it is still physically the same circular
 * order -- so it is not accurate to describe different reel indexes as
 * genuinely distinct strips, only as differently numbered views of one. This
 * is not an economic defect: independent uniform stops and identical
 * per-symbol counts on every reel are what preserve the paytable (see
 * above), and that holds regardless of whether the underlying order is
 * shared or reel-specific. A genuinely distinct deterministic ordering per
 * reel (different adjacency/clustering on each one, without changing
 * approved counts or paytable derivation) was considered during this pass
 * and deliberately deferred rather than improvised under time pressure --
 * inventing a second ordering algorithm on top of the placement rule above
 * is exactly the kind of change that wants its own dedicated design and
 * review, not a rider on an unrelated audit. {@link #forReel} still applies
 * a fixed, reel-dependent rotation so consecutive reels are not numerically
 * identical, which is enough to avoid the specific bug of two reels always
 * landing on the same stop for the same draw -- it should not be read as
 * more than that.
 */
public final class SlotsReelStrip {

    /** Every production strip has exactly this many stops. */
    public static final int SIZE = 100;

    /**
     * Rotation applied per reel index, in stops. Coprime with {@link #SIZE}
     * so repeated application visits every possible rotation before it ever
     * repeats, spreading reels apart rather than cycling through a short
     * pattern.
     */
    private static final int REEL_ROTATION_STEP = 17;

    private final SlotsSymbol[] stops;
    private final SlotsVariance variance;
    private final int reelIndex;

    private SlotsReelStrip(SlotsSymbol[] stops, SlotsVariance variance, int reelIndex) {
        this.stops = stops;
        this.variance = variance;
        this.reelIndex = reelIndex;
    }

    /**
     * The strip for one reel of a machine running at {@code variance}.
     * Deterministic: calling this repeatedly for the same arguments always
     * returns an identical sequence.
     *
     * @param reelIndex 0-based, left to right
     */
    public static SlotsReelStrip forReel(SlotsVariance variance, int reelIndex) {
        SlotsVariance effective = variance == null ? SlotsVariance.BALANCED : variance;
        if (reelIndex < 0) {
            throw new IllegalArgumentException("reelIndex must not be negative; got " + reelIndex);
        }
        SlotsSymbol[] base = buildBaseSequence(effective);
        int shift = Math.floorMod(reelIndex * REEL_ROTATION_STEP, SIZE);
        SlotsSymbol[] rotated = rotate(base, shift);
        return new SlotsReelStrip(rotated, effective, reelIndex);
    }

    /**
     * Places every symbol's copies as evenly as possible around a 100-stop
     * circle: each symbol's {@code i}-th copy (of {@code count} total) wants
     * the position {@code (i + 0.5) * SIZE / count}, and every symbol's wants
     * are then merged in position order. This is a standard largest-remainder
     * / Bresenham-style even distribution -- it reproduces exact counts,
     * needs no randomness, and inherently keeps same-symbol runs short
     * because a symbol's own copies are never placed closer together than
     * {@code SIZE / count}.
     */
    private static SlotsSymbol[] buildBaseSequence(SlotsVariance variance) {
        record Want(double idealPosition, int symbolOrdinal, int copyIndex, SlotsSymbol symbol) {
        }
        List<Want> wants = new ArrayList<>(SIZE);
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            int count = variance.weight(symbol);
            for (int i = 0; i < count; i++) {
                double ideal = (i + 0.5) * SIZE / (double) count;
                wants.add(new Want(ideal, symbol.ordinal(), i, symbol));
            }
        }
        wants.sort(
            Comparator.comparingDouble(Want::idealPosition)
                .thenComparingInt(Want::symbolOrdinal)
                .thenComparingInt(Want::copyIndex));

        SlotsSymbol[] stops = new SlotsSymbol[SIZE];
        for (int i = 0; i < SIZE; i++) {
            stops[i] = wants.get(i).symbol();
        }
        return stops;
    }

    private static SlotsSymbol[] rotate(SlotsSymbol[] base, int shift) {
        SlotsSymbol[] rotated = new SlotsSymbol[base.length];
        for (int i = 0; i < base.length; i++) {
            rotated[i] = base[(i + shift) % base.length];
        }
        return rotated;
    }

    /** The centred visible-window offsets for a supported visible height, top to bottom. */
    public static int[] offsetsFor(int visibleRows) {
        SlotsGeometry.requireSupportedRowCount(visibleRows);
        return switch (visibleRows) {
            case 1 -> new int[] {0};
            case 3 -> new int[] {-1, 0, 1};
            default -> new int[] {-2, -1, 0, 1, 2};
        };
    }

    public int size() {
        return SIZE;
    }

    /** The symbol at any stop index, wrapping correctly for negative or overflowing indexes. */
    public SlotsSymbol symbolAt(int index) {
        return stops[Math.floorMod(index, SIZE)];
    }

    /**
     * The visible window centred on {@code selectedStop} for a given visible
     * height, top to bottom.
     */
    public SlotsSymbol[] window(int selectedStop, int visibleRows) {
        int[] offsets = offsetsFor(visibleRows);
        SlotsSymbol[] visible = new SlotsSymbol[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            visible[i] = symbolAt(selectedStop + offsets[i]);
        }
        return visible;
    }

    /** Exact count of one symbol on this strip -- always equal to {@link SlotsVariance#weight}. */
    public int countOf(SlotsSymbol symbol) {
        int count = 0;
        for (SlotsSymbol stop : stops) {
            if (stop == symbol) {
                count++;
            }
        }
        return count;
    }

    public SlotsVariance variance() {
        return variance;
    }

    public int reelIndex() {
        return reelIndex;
    }
}
