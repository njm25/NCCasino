package org.nc.nccasino.games.Slots;

/**
 * The Auto Spin Settings view's upper-canvas layout: which of the 45 canvas
 * slots carry an entry, and which entry each one is.
 *
 * <p>The eight entries sit on a deliberately symmetric cross -- an
 * instructions card centred on the top row, then three editable settings, a
 * centred Start, and three more settings, all mirrored about canvas column 4:
 *
 * <pre>
 *   .  .  .  .  4  .  .  .  .      overview
 *   .  . 11  . 13  . 15  .  .      spin limit / stop on any win / big win
 *   .  .  .  . 22  .  .  .  .      start auto spin
 *   .  . 29  . 31  . 33  .  .      profit target / loss limit / reset
 *   .  .  .  .  .  .  .  .  .      (informational rail row -- not used here)
 * </pre>
 *
 * <p>Every other canvas slot is a blank backdrop, so no stale reel symbol
 * can ever show through this modal view.
 */
public final class SlotsAutoSettingsLayout {

    public static final int OVERVIEW_SLOT = 4;
    public static final int SPIN_LIMIT_SLOT = 11;
    public static final int STOP_ON_ANY_WIN_SLOT = 13;
    public static final int BIG_WIN_SLOT = 15;
    public static final int START_SLOT = 22;
    public static final int PROFIT_TARGET_SLOT = 29;
    public static final int LOSS_LIMIT_SLOT = 31;
    public static final int RESET_SLOT = 33;

    private SlotsAutoSettingsLayout() {
    }

    /** Which Auto Spin Settings entry a canvas slot carries, if any. */
    public enum Entry {
        OVERVIEW,
        SPIN_LIMIT,
        STOP_ON_ANY_WIN,
        BIG_WIN_MULTIPLIER,
        START,
        PROFIT_TARGET,
        LOSS_LIMIT,
        RESET
    }

    /** @return the entry at {@code slot}, or {@code null} for backdrop/non-entry slots. */
    public static Entry entryAt(int slot) {
        return switch (slot) {
            case OVERVIEW_SLOT -> Entry.OVERVIEW;
            case SPIN_LIMIT_SLOT -> Entry.SPIN_LIMIT;
            case STOP_ON_ANY_WIN_SLOT -> Entry.STOP_ON_ANY_WIN;
            case BIG_WIN_SLOT -> Entry.BIG_WIN_MULTIPLIER;
            case START_SLOT -> Entry.START;
            case PROFIT_TARGET_SLOT -> Entry.PROFIT_TARGET;
            case LOSS_LIMIT_SLOT -> Entry.LOSS_LIMIT;
            case RESET_SLOT -> Entry.RESET;
            default -> null;
        };
    }

    /** Every entry-bearing slot, ascending. */
    public static int[] entrySlots() {
        return new int[] {
            OVERVIEW_SLOT, SPIN_LIMIT_SLOT, STOP_ON_ANY_WIN_SLOT, BIG_WIN_SLOT,
            START_SLOT, PROFIT_TARGET_SLOT, LOSS_LIMIT_SLOT, RESET_SLOT
        };
    }

    /** Whether {@code slot} is a plain backdrop slot in this view (a canvas slot with no entry). */
    public static boolean isBackdrop(int slot) {
        return slot >= 0
            && slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS
            && entryAt(slot) == null;
    }
}
