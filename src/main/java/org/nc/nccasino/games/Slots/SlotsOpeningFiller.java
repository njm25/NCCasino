package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

/**
 * The opening animation's fixed cosmetic filler sequence. Every column
 * receives the same deterministic rainbow in the same order, so successive
 * colors visibly travel down each column instead of appearing as random
 * noise. The columns themselves still start on a left-to-right stagger.
 */
public final class SlotsOpeningFiller {

    private SlotsOpeningFiller() {
    }

    /** The exact approved vertical rainbow order, first to last. */
    private static final Material[] FIXED_RAINBOW = {
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

    /** Every fixed rainbow burst is exactly this many panes. */
    public static final int FIXED_RAINBOW_LENGTH = FIXED_RAINBOW.length;

    /** @return the fixed rainbow sequence as a defensive copy. */
    public static Material[] fixedRainbowSequence() {
        return FIXED_RAINBOW.clone();
    }
}
