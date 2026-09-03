package org.nc.nccasino.games.Slots;

import org.bukkit.event.inventory.ClickType;

/**
 * Pure classification of which raw Bukkit click types the redesigned Slots UI
 * accepts, and which cycle direction an accepted click means.
 *
 * <p>The bug this exists to prevent: treating "not a right click" as "a left
 * click" without checking what the click actually was. Bukkit's own
 * {@code InventoryClickEvent.isRightClick()}/{@code isLeftClick()} both
 * return {@code true} for their shift-modified variants too (a
 * {@code SHIFT_RIGHT} click is also {@code isRightClick()}), and neither
 * method says anything about middle-click, double-click, drop, number-key,
 * or border clicks -- all of which the shared listener still routes here
 * after cancelling item movement. Every one of those must be a safe no-op:
 * never mutate a setting, spin, change UI mode, retry a payout, or move
 * items.
 */
public final class SlotsClickClassifier {

    private SlotsClickClassifier() {
    }

    /**
     * Only an ordinary, unmodified left or right click is ever accepted by a
     * cycling or action control -- shift-click, double-click, middle-click,
     * number-key, drop, and border clicks are all rejected here rather than
     * silently falling through to "must be a left click".
     */
    public static boolean isOrdinaryClick(ClickType clickType) {
        return clickType == ClickType.LEFT || clickType == ClickType.RIGHT;
    }

    /**
     * +1 (forward/up) for an ordinary left click, -1 (backward/down) for an
     * ordinary right click.
     *
     * @throws IllegalArgumentException if {@code clickType} is not one of
     *     the two ordinary click types -- callers must check
     *     {@link #isOrdinaryClick} first rather than relying on this to fail
     *     safe, since a thrown exception is not itself a safe no-op inside
     *     an inventory click handler.
     */
    public static int cycleDirection(ClickType clickType) {
        if (!isOrdinaryClick(clickType)) {
            throw new IllegalArgumentException(
                "cycleDirection is only defined for an ordinary left/right click; got " + clickType);
        }
        return clickType == ClickType.RIGHT ? -1 : 1;
    }
}
