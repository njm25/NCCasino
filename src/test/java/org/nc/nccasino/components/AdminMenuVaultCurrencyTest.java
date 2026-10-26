package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.Preferences;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Vault dealer plays for money, so its currency item can't be changed. The
 * currency button refused it, but a shift-click of an item from the admin's own
 * inventory (DealerInteractListener -> handleDrag) changed it anyway.
 */
class AdminMenuVaultCurrencyTest {

    @Test
    void aShiftClickCannotChangeAVaultDealersCurrencyItem() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dealers.vaultdealer.currency.material", "EMERALD");
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        Preferences preferences = mock(Preferences.class);
        when(preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.NONE);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        AdminMenu menu = mock(AdminMenu.class, CALLS_REAL_METHODS);
        set(menu, Menu.class, "plugin", plugin);
        Field mode = AdminMenu.class.getDeclaredField("currencyMode");
        mode.setAccessible(true);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object vault = Enum.valueOf((Class) mode.getType(), "VAULT");
        mode.set(menu, vault);

        Player admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        ItemStack dirt = mock(ItemStack.class);
        when(dirt.getType()).thenReturn(Material.DIRT);

        menu.handleDrag(dirt, admin, mock(InventoryClickEvent.class));

        assertEquals("EMERALD", config.getString("dealers.vaultdealer.currency.material"));
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
