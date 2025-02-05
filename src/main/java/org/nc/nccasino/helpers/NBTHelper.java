package org.nc.nccasino.helpers;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;


import java.lang.reflect.Method;

public class NBTHelper {

    private static final boolean IS_PAPER = isRunningPaper();

    private static boolean isRunningPaper() {
        return Bukkit.getServer().getName().equalsIgnoreCase("Paper") || 
               Bukkit.getServer().getVersion().contains("Paper");
    }

    public static String getNBTString(ItemStack item) {
        System.out.println("Starting NBT Extraction...");
        System.out.println("Detected Paper: " + IS_PAPER);

        return getSpigotNBT(item); // Only Spigot for now
    }

    
    private static String getSpigotNBT(ItemStack item) {
        
        try {
            System.out.println("Extracting Spigot NBT...");
            String nmsVersion = getNMSVersion();
            if (nmsVersion.isEmpty()) {
                return "";
            }
    
            // Convert ItemStack to NMS ItemStack
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopyMethod.invoke(null, item);
            System.out.println("Converted to NMS ItemStack: " + nmsItem.toString());
            Method getTagMethod = nmsItem.getClass().getMethod("a");
            Object tag = getTagMethod.invoke(nmsItem);
            
            
    
            if (tag == null) {
                System.out.println("No NBT found.");
                return "";
            }
    
            // Convert to string
            Method toStringMethod = tag.getClass().getMethod("toString");
            String nbtString = (String) toStringMethod.invoke(tag);
    
            System.out.println("Extracted Spigot NBT: " + nbtString);
            return nbtString;
    
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public static ItemStack applyNBTFromString(ItemStack item, String nbtData) {
        if (item == null || nbtData == null || nbtData.isEmpty()) {
            return item;
        }

        try {
            System.out.println("Applying NBT...");
            String nmsVersion = getNMSVersion();
            if (nmsVersion.isEmpty()) {
                return item;
            }

            // Convert Bukkit ItemStack to NMS ItemStack
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopyMethod.invoke(null, item);

            // Parse NBT data
            Class<?> mojangsonParserClass = Class.forName("net.minecraft.nbt.MojangsonParser");
            Method parseMethod = mojangsonParserClass.getMethod("parse", String.class);
            Object nbtTag = parseMethod.invoke(null, nbtData);

            // Apply NBT to NMS ItemStack
            Method setTagMethod = nmsItem.getClass().getMethod("a", nbtTag.getClass());
            setTagMethod.invoke(nmsItem, nbtTag);

            // Convert back to Bukkit ItemStack
            Method asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", nmsItem.getClass());
            ItemStack bukkitItem = (ItemStack) asBukkitCopyMethod.invoke(null, nmsItem);

            System.out.println("NBT applied successfully.");
            return bukkitItem;

        } catch (Exception e) {
            e.printStackTrace();
            return item;
        }
    }
    
    

    private static String getNMSVersion() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");

            if (parts.length >= 4) {
                return parts[3]; // Expected format: v1_21_R1
            } else {
                System.out.println("Unexpected package format: " + packageName);
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
}
