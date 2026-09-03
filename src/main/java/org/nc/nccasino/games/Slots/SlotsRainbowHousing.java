package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

/**
 * The Slots canvas housing's dynamic rainbow: every gutter/frame cell's
 * material is derived purely from its inventory column ({@code slot % 9}),
 * never from a per-geometry hardcoded slot table. This is exactly what lets
 * the housing automatically grow or shrink around every supported reel
 * count (3, 5, 7) and visible height (1, 3, 5) -- {@link SlotsMachine}'s
 * frame renderer skips whichever slots the current geometry's grid already
 * owns and paints every remaining slot in the 5-row canvas from this
 * palette.
 */
public final class SlotsRainbowHousing {

    private SlotsRainbowHousing() {
    }

    /** Left-to-right across the 9-wide inventory, exactly one color per column. */
    private static final Material[] PALETTE = {
        Material.RED_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
    };

    /** @return the palette, left to right, one entry per inventory column (defensive copy) */
    public static Material[] palette() {
        return PALETTE.clone();
    }

    /** @param column a 0-based inventory column, 0-8 (or any value -- wraps via floorMod) */
    public static Material materialForColumn(int column) {
        return PALETTE[Math.floorMod(column, PALETTE.length)];
    }

    /** @param slot any inventory slot (0-53); only {@code slot % 9} (the column) matters */
    public static Material materialForSlot(int slot) {
        return materialForColumn(slot % SlotsGeometry.INVENTORY_WIDTH);
    }
}
