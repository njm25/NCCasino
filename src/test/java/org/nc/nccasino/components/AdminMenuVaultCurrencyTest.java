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

    private org.mockito.MockedStatic<org.bukkit.plugin.java.JavaPlugin> javaPluginStatic;

    @org.junit.jupiter.api.BeforeEach
    void providingPlugin() {
        // Dealer's static keys need a providing plugin outside a real server.
        org.bukkit.plugin.java.JavaPlugin providing = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(providing.getName()).thenReturn("NCCasino");
        javaPluginStatic = org.mockito.Mockito.mockStatic(org.bukkit.plugin.java.JavaPlugin.class);
        javaPluginStatic.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(any())).thenReturn(providing);
    }

    @org.junit.jupiter.api.AfterEach
    void closeStatic() {
        javaPluginStatic.close();
    }

    @Test
    void aShiftClickCannotChangeAVaultDealersCurrencyItem() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dealers.vaultdealer.currency.material", "EMERALD");
        config.set("dealers.vaultdealer.currency.mode", "VAULT");
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        Preferences preferences = mock(Preferences.class);
        when(preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.NONE);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        AdminMenu menu = mock(AdminMenu.class, CALLS_REAL_METHODS);
        set(menu, Menu.class, "plugin", plugin);
        org.bukkit.entity.LivingEntity dealer = mock(org.bukkit.entity.LivingEntity.class);
        org.bukkit.persistence.PersistentDataContainer data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(dealer.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenReturn("vaultdealer");
        set(menu, AdminMenu.class, "dealer", dealer);
        // The menu's cached mode is stale (opened before another admin switched
        // the dealer to Vault); the refusal must go by the config.
        Field mode = AdminMenu.class.getDeclaredField("currencyMode");
        mode.setAccessible(true);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object vanilla = Enum.valueOf((Class) mode.getType(), "VANILLA");
        mode.set(menu, vanilla);

        Player admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        ItemStack dirt = mock(ItemStack.class);
        when(dirt.getType()).thenReturn(Material.DIRT);

        InventoryClickEvent shiftClick = mock(InventoryClickEvent.class);
        menu.handleDrag(dirt, admin, shiftClick);

        assertEquals("EMERALD", config.getString("dealers.vaultdealer.currency.material"));
        // Refused or not, the click must be cancelled: otherwise vanilla moves the
        // stack into the menu, which deletes it when the menu closes.
        org.mockito.Mockito.verify(shiftClick).setCancelled(true);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
