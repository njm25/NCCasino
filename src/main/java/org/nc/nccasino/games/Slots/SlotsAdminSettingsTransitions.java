package org.nc.nccasino.games.Slots;

/**
 * Pure admin-settings transitions for {@code SlotsMenu}'s Default Height and
 * Default Paylines controls, extracted so the height-one interaction (and
 * the persistence rule that must go with it) can be tested without a live
 * Bukkit inventory (redesign audit Section 3).
 *
 * <p>The bug this exists to prevent: at Default Height 1, the effective
 * line count always loads as 1 ({@link SlotsConfig#load} clamps it), but
 * cycling Default Paylines still computed and announced 2 or 9 -- a value
 * the very next refresh silently clamped back to 1, telling the admin one
 * thing while showing another. And because only {@code slots-rows} was
 * persisted (never {@code slots-lines}), a stale larger stored line count
 * could resurface the moment height moved back to 3 or 5.
 *
 * <p>Post-audit correction: persisting {@code slots-lines} only when the
 * <em>destination</em> height was 1 missed the reverse direction -- a
 * hand-edited or legacy stored pair like {@code rows=1, lines=9} displays
 * correctly (height 1 always clamps the effective count to 1), but cycling
 * back out to height 3 or 5 wrote only {@code slots-rows}, leaving the stale
 * raw 9 to resurface immediately. {@link #rowsTransition} fixes this by
 * checking both ends of the transition atomically and always returning the
 * line count {@code SlotsMenu} must persist alongside the new height,
 * whether or not it actually changes.
 */
public final class SlotsAdminSettingsTransitions {

    private SlotsAdminSettingsTransitions() {
    }

    /** The next Default Height in {@code direction}'s order, wrapping both ways. */
    public static int nextRows(int currentRows, int direction) {
        int[] supported = SlotsGeometry.supportedRowCounts();
        int index = indexOf(supported, currentRows);
        return supported[Math.floorMod(index + direction, supported.length)];
    }

    /**
     * One atomic Default Height transition: the new height to persist, and
     * the default line count that must be persisted alongside it.
     *
     * @param nextRows the new Default Height
     * @param nextPersistedLines exactly 1 if either end of the transition
     *     touches height 1 (leaving it or entering it) -- otherwise
     *     {@code currentLines}, unchanged, so a genuine 3/5-to-3/5 move never
     *     touches the stored line count at all
     */
    public record RowsTransition(int nextRows, int nextPersistedLines) {
    }

    /**
     * Computes {@link RowsTransition} for cycling Default Height from
     * {@code currentRows} in {@code direction}, given the currently stored
     * {@code currentLines}. Checking both {@code currentRows} and the
     * resulting height (rather than only the destination) is what lets this
     * correctly clean up a stale raw line count left over from before this
     * persistence rule existed, or from a hand-edited config -- leaving
     * height 1 forces the persisted value to 1 just as surely as entering it
     * does.
     */
    public static RowsTransition rowsTransition(int currentRows, int currentLines, int direction) {
        int nextRows = nextRows(currentRows, direction);
        int nextPersistedLines = (currentRows == 1 || nextRows == 1) ? 1 : currentLines;
        return new RowsTransition(nextRows, nextPersistedLines);
    }

    /**
     * The next Default Paylines value in {@code direction}'s order, wrapping
     * 1..9 both ways -- or {@link #INERT} if {@code visibleRows} is 1, in
     * which case the click must be a pure no-op: nothing computed here may
     * be stored or announced.
     */
    public static int nextLinesOrInert(int visibleRows, int currentLines, int direction) {
        if (visibleRows == 1) {
            return INERT;
        }
        return Math.floorMod((currentLines - 1) + direction, SlotsPayline.MAX_LINES) + 1;
    }

    /** Sentinel returned by {@link #nextLinesOrInert} when Default Paylines is inert at height 1. */
    public static final int INERT = -1;

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }
}
