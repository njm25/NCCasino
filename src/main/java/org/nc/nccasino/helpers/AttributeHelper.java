package org.nc.nccasino.helpers;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

public class AttributeHelper {
    public static Attribute getAttributeSafely(String name) {
        try {
            return (Attribute) Attribute.class.getField(name).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fallback for versions before 1.21.4
            NamespacedKey key = NamespacedKey.minecraft("generic." + name.toLowerCase());
            return Registry.ATTRIBUTE.get(key); // Use Registry instead of valueOf
        }
    }
}
