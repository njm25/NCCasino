package org.nc.nccasino.helpers;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.Nccasino;

import java.lang.reflect.Method;
import java.util.Base64;

public class NBTHelper {

    private static final boolean IS_PAPER = isPaper();
    private static final Nccasino PLUGIN = (Nccasino) JavaPlugin.getProvidingPlugin(Nccasino.class);

    private static boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String getNBTString(ItemStack item) {
        try {
            if (IS_PAPER) {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                NamespacedKey key = new NamespacedKey(PLUGIN, "nccasino");

                return container.has(key, PersistentDataType.STRING) 
                        ? container.get(key, PersistentDataType.STRING) 
                        : "";
            }

            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopyMethod.invoke(null, item);

            Method hasTagMethod = nmsItem.getClass().getMethod("hasTag");
            boolean hasTag = (boolean) hasTagMethod.invoke(nmsItem);

            Object tag = hasTag ? nmsItem.getClass().getMethod("getTag").invoke(nmsItem) : null;
            if (tag == null) return "";

            return Base64.getEncoder().encodeToString(tag.toString().getBytes());

        } catch (Exception e) {
            return "";
        }
    }
}
