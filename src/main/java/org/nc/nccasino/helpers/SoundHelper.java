package org.nc.nccasino.helpers;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SoundHelper {
    public static Sound getSoundSafely(String name,Player player) {
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
                    // Convert to lowercase and ensure proper namespaced format
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());

        // Try to fetch from the sound registry
        Sound sound = Registry.SOUNDS.get(key);

        // Return the found sound or null if not present
        return (sound != null) ? sound : null;
       
        }
        else{
            return null;
        }

    }
}
