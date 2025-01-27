package org.nc.nccasino.helpers;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

public class SoundHelper {
    public static Sound getSoundSafely(String name) {
        // Convert to lowercase and ensure proper namespaced format
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());

        // Try to fetch from the sound registry
        Sound sound = Registry.SOUNDS.get(key);

        // Return the found sound or null if not present
        return (sound != null) ? sound : null;
    }
}
