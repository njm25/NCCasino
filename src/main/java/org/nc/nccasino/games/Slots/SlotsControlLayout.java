package org.nc.nccasino.games.Slots;

import org.bukkit.event.inventory.ClickType;

/**
 * The bottom control row's exact slot assignment, and the one place a raw
 * click on the Slots inventory is turned into the action it means.
 *
 * <p>The row is fixed left to right:
 *
 * <pre>
 *   45 Exit                46 Reels              47 Height
 *   48 Paytable            49 Spin               50 Clock
 *   51 Paylines            52 Wager Per Line     53 Saved Profiles
 * </pre>
 *
 * <p>The four configuration controls therefore read, left to right across the
 * row, Reels (46), Height (47), Paylines (51), Wager Per Line (52). Each keeps
 * its own colour identity from {@link SlotsControlPresentation.Role} -- brown
 * for Reels, pink for Height, green for Paylines, black for Wager Per Line --
 * so a colour travels with its control rather than staying pinned to a slot.
 *
 * <p>Routing lives here rather than inline in {@code SlotsMachine} so the
 * whole matrix -- every slot, every accepted click type, every view -- is
 * testable without a live server. The ordering of the checks is itself part
 * of the contract:
 *
 * <ol>
 *   <li>the Clock's shift-right-click is recognized <em>before</em> the
 *   ordinary-click gate, because that gate would otherwise swallow the only
 *   shift-modified action the UI has;
 *   <li>anything that is not an ordinary left/right click is then dropped;
 *   <li>Back to Game owns its slot outright in whichever modal view is open,
 *   ahead of that slot's ordinary Game View behaviour;
 *   <li>canvas clicks are handed to the open view;
 *   <li>in a modal editor every bottom control except Exit is inert, so a
 *   stray click can never change the game behind the open menu.
 * </ol>
 */
public final class SlotsControlLayout {

    public static final int EXIT_SLOT = 45;
    public static final int REELS_SLOT = 46;
    public static final int HEIGHT_SLOT = 47;
    public static final int PAYTABLE_SLOT = 48;
    public static final int SPIN_SLOT = 49;
    public static final int CLOCK_SLOT = 50;
    public static final int LINES_SLOT = 51;
    public static final int WAGER_SLOT = 52;
    public static final int PROFILES_SLOT = 53;

    public static final int FIRST_CONTROL_SLOT = EXIT_SLOT;
    public static final int LAST_CONTROL_SLOT = PROFILES_SLOT;

    /** The first slot of the bottom row, i.e. one past the last canvas slot. */
    public static final int CANVAS_SLOT_COUNT =
        SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS;

    private SlotsControlLayout() {
    }

    /** What a click resolves to. */
    public enum Target {
        /** A safe no-op: an unsupported click type, or a slot with nothing mapped to it. */
        NONE,
        /** A click inside the upper 45-slot canvas; the open view decides what it means. */
        CANVAS,
        /** The Back to Game substitution the open modal view owns. */
        BACK_TO_GAME,
        /** Shift-right-click on the Clock: open Auto Spin Settings. */
        AUTO_SETTINGS,
        /** A bottom control that is deliberately inert while a modal editor is open. */
        MODAL_LOCKED,
        EXIT,
        WAGER,
        REELS,
        PAYTABLE,
        SPIN,
        CLOCK,
        PAYLINES,
        HEIGHT,
        PROFILES
    }

    /**
     * @param target what the click means
     * @param direction +1 for a left click and -1 for a right click on a
     *     cycling control; 0 where direction is meaningless
     */
    public record Route(Target target, int direction) {
    }

    private static final Route NO_ACTION = new Route(Target.NONE, 0);

    /** Which control occupies {@code slot}, ignoring the open view entirely. */
    public static Target controlAt(int slot) {
        return switch (slot) {
            case EXIT_SLOT -> Target.EXIT;
            case REELS_SLOT -> Target.REELS;
            case HEIGHT_SLOT -> Target.HEIGHT;
            case PAYTABLE_SLOT -> Target.PAYTABLE;
            case SPIN_SLOT -> Target.SPIN;
            case CLOCK_SLOT -> Target.CLOCK;
            case LINES_SLOT -> Target.PAYLINES;
            case WAGER_SLOT -> Target.WAGER;
            case PROFILES_SLOT -> Target.PROFILES;
            default -> Target.NONE;
        };
    }

    /** Resolves one click. See the class javadoc for why the checks are in this order. */
    public static Route route(SlotsUiView view, int slot, ClickType clickType) {
        SlotsUiView effective = view == null ? SlotsUiView.GAME : view;

        if (slot == CLOCK_SLOT
            && clickType == ClickType.SHIFT_RIGHT
            && effective.allowsConfigurationChanges()) {
            return new Route(Target.AUTO_SETTINGS, 0);
        }
        if (!SlotsClickClassifier.isOrdinaryClick(clickType)) {
            return NO_ACTION;
        }
        if (slot >= 0 && effective.backToGameSlot() == slot) {
            return new Route(Target.BACK_TO_GAME, 0);
        }
        if (slot >= 0 && slot < CANVAS_SLOT_COUNT) {
            return new Route(Target.CANVAS, 0);
        }
        // Resolved before the modal gate so a slot that carries no control at
        // all (an out-of-range index, an outside-the-window click) stays a
        // plain no-op instead of being reported as a locked control.
        Target target = controlAt(slot);
        if (target == Target.NONE) {
            return NO_ACTION;
        }
        if (!effective.allowsConfigurationChanges() && slot != EXIT_SLOT) {
            return new Route(Target.MODAL_LOCKED, 0);
        }
        return new Route(target, SlotsClickClassifier.cycleDirection(clickType));
    }
}
