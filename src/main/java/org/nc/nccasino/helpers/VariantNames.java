package org.nc.nccasino.helpers;

import org.bukkit.Keyed;

/**
 * The id of a mob variant (cat type, villager biome, dye color, ...) for the
 * menus to format. Cat, frog and villager types are registry objects, not
 * enums, and their toString() differs by server: Spigot prints the name but
 * Paper prints an internal "CraftType{holder=...}" description. Their
 * registry key ("all_black") is the same on both.
 */
public final class VariantNames {
    private VariantNames() {
    }

    /** The variant's id, or "unknown" when there is none (the menus hide that). */
    public static String id(Object variant) {
        if (variant == null) {
            return "unknown";
        }
        if (variant instanceof Enum<?> constant) {
            return constant.name();
        }
        if (variant instanceof Keyed keyed) {
            return keyed.getKey().getKey();
        }
        return String.valueOf(variant);
    }
}
