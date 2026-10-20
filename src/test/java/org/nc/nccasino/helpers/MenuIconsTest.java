package org.nc.nccasino.helpers;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuIconsTest {
    @Test
    void usesTheNewestNameThisServerHas() {
        assertEquals(Material.IRON_CHAIN, MenuIcons.CHAIN);
        assertEquals(Material.PALE_OAK_LOG, MenuIcons.SNOWY_VILLAGER);
    }

    @Test
    void skipsNamesThisServerDoesNotHave() {
        assertEquals(Material.SNOW_BLOCK, MenuIcons.firstPresent("NOT_A_BLOCK", "SNOW_BLOCK"));
        assertEquals(Material.BARRIER, MenuIcons.firstPresent("NOT_A_BLOCK"));
    }
}
