package org.nc.nccasino.games.Slots;

/**
 * The Auto Spin Settings view's upper-canvas layout: which of the 45 canvas
 * slots carry an entry, and which entry each one is.
 *
 * <p>The seven entries sit on a deliberately symmetric cross -- the Clock
 * centred on the top row, then three editable settings and three more, all
 * mirrored about canvas column 4:
 *
 * <pre>
 *   .  .  .  .  4  .  .  .  .      the Clock
 *   .  . 11  . 13  . 15  .  .      spin limit / stop on any win / big win
 *   .  .  .  .  .  .  .  .  .
 *   .  . 29  . 31  . 33  .  .      profit target / loss limit / reset
 *   .  .  .  .  .  .  .  .  .      (informational rail row -- not used here)
 * </pre>
 *
 * <p>The Clock at slot 4 is the bottom row's Clock, carried over whole: the
 * same item, the same title, and the same left-click-starts /
 * right-click-cycles-speed / shift-left-click-leaves behaviour. There is
 * deliberately no separate Start control any more -- a second lever sitting
 * directly above the real Spin lever read as a duplicate of it, and the Clock
 * is already the control that owns Auto Spin everywhere else in the machine.
 *
 * <p>Every other canvas slot carries the machine's own rainbow housing, so
 * the menu reads as part of the same cabinet rather than as a grey overlay
 * dropped on top of it.
 */
public final class SlotsAutoSettingsLayout {

    public static final int CLOCK_SLOT = 4;
    public static final int SPIN_LIMIT_SLOT = 11;
    public static final int STOP_ON_ANY_WIN_SLOT = 13;
    public static final int BIG_WIN_SLOT = 15;
    public static final int PROFIT_TARGET_SLOT = 29;
    public static final int LOSS_LIMIT_SLOT = 31;
    public static final int RESET_SLOT = 33;

    private SlotsAutoSettingsLayout() {
    }

    /** Which Auto Spin Settings entry a canvas slot carries, if any. */
    public enum Entry {
        CLOCK,
        SPIN_LIMIT,
        STOP_ON_ANY_WIN,
        BIG_WIN_MULTIPLIER,
        PROFIT_TARGET,
        LOSS_LIMIT,
        RESET
    }

    /** @return the entry at {@code slot}, or {@code null} for backdrop/non-entry slots. */
    public static Entry entryAt(int slot) {
        return switch (slot) {
            case CLOCK_SLOT -> Entry.CLOCK;
            case SPIN_LIMIT_SLOT -> Entry.SPIN_LIMIT;
            case STOP_ON_ANY_WIN_SLOT -> Entry.STOP_ON_ANY_WIN;
            case BIG_WIN_SLOT -> Entry.BIG_WIN_MULTIPLIER;
            case PROFIT_TARGET_SLOT -> Entry.PROFIT_TARGET;
            case LOSS_LIMIT_SLOT -> Entry.LOSS_LIMIT;
            case RESET_SLOT -> Entry.RESET;
            default -> null;
        };
    }

    /** Every entry-bearing slot, ascending. */
    public static int[] entrySlots() {
        return new int[] {
            CLOCK_SLOT, SPIN_LIMIT_SLOT, STOP_ON_ANY_WIN_SLOT, BIG_WIN_SLOT,
            PROFIT_TARGET_SLOT, LOSS_LIMIT_SLOT, RESET_SLOT
        };
    }

    /** Whether {@code slot} is a plain backdrop slot in this view (a canvas slot with no entry). */
    public static boolean isBackdrop(int slot) {
        return slot >= 0
            && slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS
            && entryAt(slot) == null;
    }
}
