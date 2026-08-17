package org.nc.nccasino.games.Blackjack;

/**
 * The pregame wager bar's sit/unsit slide, modeled as a single solid
 * nine-item strip -- Undo All | Undo Last | Chip 1..5 | All In | Door --
 * sliding right (reveal) or left (conceal) through slots 45-53 as one
 * contiguous object, rather than nine independently-timed slot reveals.
 *
 * <p>{@link #CLOSED} (0) is the fully-unseated resting frame (door@45, brown
 * edge glass@46, background 47-53). {@link #OPEN} (8) is the fully-seated
 * resting frame (the canonical Undo All@45 .. Door@53 bar). Positions 1-7
 * are the intermediate slide frames in between. The door is always the
 * rightmost visible item at {@code UNDO_ALL_SLOT + position}; the remaining
 * controls trail immediately to its left, right-to-left in the same order
 * they hold at {@link #OPEN}, so the strip is always contiguous with no gap
 * and no duplicated control.
 *
 * <p>Pure data only -- zero Bukkit types -- so the exact frame at every
 * position is unit-testable without a running server. The controller
 * ({@code BlackjackInventory}) owns turning a {@link Control} into an actual
 * {@code ItemStack} and tracking each viewer's live position/target.
 */
public final class BlackjackWagerRevealPlan {

    private BlackjackWagerRevealPlan() {
    }

    /** Fully closed/unseated: door@45, brown edge glass@46, background 47-53. */
    public static final int CLOSED = 0;
    /** Fully open/seated: the canonical Undo All@45 .. Door@53 bar. */
    public static final int OPEN = 8;

    /** How many slots the bottom bar occupies (45-53 inclusive). */
    public static final int SLOT_COUNT = BlackjackSlotLayout.PREGAME_EXIT_SLOT - BlackjackSlotLayout.UNDO_ALL_SLOT + 1;

    /** The logical identity of whatever occupies one bottom-bar slot in a given frame -- the controller maps each to a real ItemStack. */
    public enum Control {
        DOOR,
        EDGE_GLASS,
        BACKGROUND,
        UNDO_ALL,
        UNDO_LAST,
        CHIP_1,
        CHIP_2,
        CHIP_3,
        CHIP_4,
        CHIP_5,
        ALL_IN
    }

    /**
     * The 8 non-door controls, in the same left-to-right order they hold in
     * the fully-open bar (Undo All .. All In). At an intermediate position
     * the trailing (rightmost) {@code position} entries of this array are
     * the ones currently visible, immediately left of the door.
     */
    private static final Control[] NON_DOOR_ORDER = {
        Control.UNDO_ALL, Control.UNDO_LAST,
        Control.CHIP_1, Control.CHIP_2, Control.CHIP_3, Control.CHIP_4, Control.CHIP_5,
        Control.ALL_IN
    };

    /**
     * The complete frame at {@code position} (0-8, see {@link #CLOSED}/{@link #OPEN}) -- one
     * {@link Control} per bottom-bar slot, index {@code i} corresponding to slot
     * {@code UNDO_ALL_SLOT + i}. Always exactly one {@link Control#DOOR} entry
     * (at index {@code position}, the strip's right edge), always contiguous
     * (no gap between the door and its trailing controls), never a duplicated
     * control.
     */
    public static Control[] frame(int position) {
        if (position < CLOSED || position > OPEN) {
            throw new IllegalArgumentException("position out of range [0,8]: " + position);
        }
        Control[] frame = new Control[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            frame[i] = Control.BACKGROUND;
        }
        frame[position] = Control.DOOR;
        if (position == CLOSED) {
            frame[1] = Control.EDGE_GLASS; // only the fully-closed resting frame shows the decorative glass
        } else {
            for (int i = 0; i < position; i++) {
                int nonDoorIndex = (NON_DOOR_ORDER.length - position) + i;
                frame[i] = NON_DOOR_ORDER[nonDoorIndex];
            }
        }
        return frame;
    }

    /** The absolute inventory slot for frame index {@code i} (0-based from {@link BlackjackSlotLayout#UNDO_ALL_SLOT}). */
    public static int slotForFrameIndex(int i) {
        return BlackjackSlotLayout.UNDO_ALL_SLOT + i;
    }

    /**
     * Total ticks a full endpoint-to-endpoint slide takes, at one frame per
     * {@code frameTicks} -- {@code OPEN - CLOSED} frame transitions. Used both
     * as the reveal and the conceal duration (the two are exact mirrors of
     * each other in this model), and as the dealer start-transition's
     * bottom-row coordination gate (the worst-case real conceal length).
     */
    public static long revealDurationTicks(long frameTicks) {
        return (long) (OPEN - CLOSED) * frameTicks;
    }

    /** Alias of {@link #revealDurationTicks(long)} -- reveal and conceal take the exact same duration in this symmetric model. */
    public static long concealDurationTicks(long frameTicks) {
        return revealDurationTicks(frameTicks);
    }
}
