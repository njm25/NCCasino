package org.nc.nccasino.helpers;

import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariantNamesTest {
    @Test
    void enumVariantsUseTheirName() {
        assertEquals("LIGHT_BLUE", VariantNames.id(DyeColor.LIGHT_BLUE));
    }

    @Test
    void registryVariantsUseTheirKeyNotToString() {
        // Paper's cat, frog and villager types print "CraftType{holder=...}".
        Keyed paperStyleCatType = new Keyed() {
            @Override
            public NamespacedKey getKey() {
                return NamespacedKey.minecraft("all_black");
            }

            @Override
            public String toString() {
                return "CraftType{holder=Reference{ResourceKey[minecraft:cat_variant / minecraft:all_black]=...}}";
            }
        };
        assertEquals("all_black", VariantNames.id(paperStyleCatType));
    }

    @Test
    void plainLabelsPassThrough() {
        assertEquals("Untamed", VariantNames.id("Untamed"));
    }
}
