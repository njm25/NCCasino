package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which materials render with Minecraft's enchantment glint <em>inherently</em>,
 * regardless of whether the item actually carries an enchantment -- the exact
 * class of bug Section 4 of the redesign audit found (the Paytable control
 * used {@code ENCHANTED_BOOK} without ever calling {@code setGlowingItem()},
 * and still visibly glinted).
 *
 * <p>Glint in this UI is reserved for exactly three cases: a ready real
 * Spin, a matched paid winning symbol, and a matched hypothetical Demo
 * winning symbol -- each of those is produced deliberately via {@code
 * setGlowingItem()}. Every other control must use a material this class
 * reports as {@code false}, or it glints by accident no matter what the
 * rendering code intended.
 */
public final class SlotsGlintPolicy {

    private SlotsGlintPolicy() {
    }

    /**
     * Materials known to render with the enchantment glint in vanilla
     * Minecraft even when unenchanted. Not exhaustive of every such
     * material in the game -- scoped to the ones this plugin has reached
     * for, or could plausibly reach for, when picking Slots control icons.
     */
    private static final Set<Material> INHERENTLY_GLINTING = EnumSet.of(
        Material.ENCHANTED_BOOK,
        Material.ENCHANTED_GOLDEN_APPLE,
        Material.KNOWLEDGE_BOOK
    );

    public static boolean hasInherentGlint(Material material) {
        return material != null && INHERENTLY_GLINTING.contains(material);
    }
}
