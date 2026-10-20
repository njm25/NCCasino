package org.nc.nccasino.helpers;

import org.bukkit.Material;

/**
 * Menu icons whose material is newer than the oldest 1.21 server. Naming the
 * constant directly (Material.IRON_CHAIN) throws NoSuchFieldError on a server
 * that predates it, so these look the name up and fall back to an older block.
 */
public final class MenuIcons {
    /** The chain block: CHAIN until 1.21.9 renamed it IRON_CHAIN. */
    public static final Material CHAIN = firstPresent("IRON_CHAIN", "CHAIN");
    /** Snowy villagers: pale oak log from 1.21.2, the snow block before that. */
    public static final Material SNOWY_VILLAGER = firstPresent("PALE_OAK_LOG", "SNOW_BLOCK");

    private MenuIcons() {
    }

    /** The first of these materials this server has, or a barrier if it has none. */
    static Material firstPresent(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.BARRIER;
    }
}
