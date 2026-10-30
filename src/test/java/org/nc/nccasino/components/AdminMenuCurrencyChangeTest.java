package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.DealerCurrencySnapshot;
import org.nc.nccasino.economy.VaultHook;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X1: an admin changing a dealer's currency (Vault/item mode, or the item)
 * while players are mid-game. Their games are settled first, the way a server
 * stop settles them, in the currency they were played in; only then is the
 * new currency written and the dealer rebuilt. With players mid-game nothing
 * happens until the admin confirms.
 */
class AdminMenuCurrencyChangeTest {

    private org.mockito.MockedStatic<org.bukkit.plugin.java.JavaPlugin> javaPluginStatic;
    private org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit;
    private YamlConfiguration config;
    private Nccasino plugin;
    private LivingEntity dealer;
    private Player admin;
    private final List<String> events = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> refundContexts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Dealer's static keys need a providing plugin outside a real server.
        org.bukkit.plugin.java.JavaPlugin providing = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(providing.getName()).thenReturn("NCCasino");
        javaPluginStatic = org.mockito.Mockito.mockStatic(org.bukkit.plugin.java.JavaPlugin.class);
        javaPluginStatic.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(any())).thenReturn(providing);
        bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class);
        bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));

        config = new YamlConfiguration();
        config.set("dealers.cf.currency.material", "EMERALD");
        config.set("dealers.cf.currency.name", "Emerald");
        config.set("dealers.cf.currency.mode", "STANDARD");
        plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        VaultHook vault = mock(VaultHook.class);
        when(vault.isEconomyAvailable()).thenReturn(true);
        when(plugin.getVaultHook()).thenReturn(vault);
        Preferences preferences = mock(Preferences.class);
        when(preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.NONE);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);
        LocalizationService localization = mock(LocalizationService.class,
            invocation -> invocation.getMethod().getReturnType() == String.class && invocation.getArguments().length > 1
                ? String.valueOf(invocation.getArguments()[1]) : null);
        when(plugin.getLocalization()).thenReturn(localization);
        // What the config names right now, as the real plugin reads it.
        when(plugin.currencySnapshot("cf")).thenAnswer(invocation -> new DealerCurrencySnapshot(
            "VAULT".equals(config.getString("dealers.cf.currency.mode")) ? CurrencyMode.VAULT : CurrencyMode.STANDARD,
            Material.matchMaterial(config.getString("dealers.cf.currency.material")),
            config.getString("dealers.cf.currency.name")));

        dealer = mock(LivingEntity.class);
        org.bukkit.persistence.PersistentDataContainer data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(dealer.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenReturn("cf");
        doAnswer(invocation -> {
            DealerCurrencySnapshot playedIn = invocation.getArgument(1);
            refundContexts.add(org.nc.nccasino.payout.PayoutMessages.serverRestartRefundContext("Roulette"));
            events.add("settle in " + playedIn.mode() + "/" + playedIn.material()
                + " while config says " + config.getString("dealers.cf.currency.mode") + "/" + config.getString("dealers.cf.currency.material"));
            return null;
        }).when(plugin).settleDealerTables(eq(dealer), any());
        doAnswer(invocation -> {
            events.add("rebuild with " + config.getString("dealers.cf.currency.mode") + "/" + config.getString("dealers.cf.currency.material"));
            return null;
        }).when(plugin).reloadDealer(dealer);
        doAnswer(invocation -> {
            events.add("save");
            return null;
        }).when(plugin).saveConfig();

        admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(admin).sendMessage(anyString());
    }

    @AfterEach
    void closeStatics() {
        javaPluginStatic.close();
        bukkit.close();
    }

    @Test
    void emeraldsStakedThenSwitchedToVaultSettleInEmeraldsBeforeTheSwitch() throws Exception {
        when(plugin.playersMidGame(dealer)).thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        AdminMenu menu = menu();
        AtomicReference<Runnable> yes = new AtomicReference<>();
        doAnswer(invocation -> {
            yes.set(invocation.getArgument(1));
            return null;
        }).when(menu).confirmCurrencyChange(eq(admin), any());

        toggleMode(menu);

        assertEquals("STANDARD", config.getString("dealers.cf.currency.mode"), "nothing changes before the admin confirms");
        assertTrue(events.isEmpty(), "nobody is settled before the admin confirms: " + events);
        assertEquals(List.of("admin.currency-change-players"), messages, "the admin is told who is mid-game first");
        assertNotNull(yes.get());

        yes.get().run();

        assertEquals("VAULT", config.getString("dealers.cf.currency.mode"));
        assertEquals(List.of(
            "settle in STANDARD/EMERALD while config says STANDARD/EMERALD",
            "save",
            "rebuild with VAULT/EMERALD"), events);
    }

    @Test
    void vaultStakedThenSwitchedToItemsSettlesInVault() throws Exception {
        config.set("dealers.cf.currency.mode", "VAULT");
        when(plugin.playersMidGame(dealer)).thenReturn(Set.of(UUID.randomUUID()));
        AdminMenu menu = menu();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run(); // the admin clicks Yes
            return null;
        }).when(menu).confirmCurrencyChange(eq(admin), any());

        toggleMode(menu);

        assertEquals(List.of(
            "settle in VAULT/EMERALD while config says VAULT/EMERALD",
            "save",
            "rebuild with VANILLA/EMERALD"), events);
        org.nc.nccasino.payout.PayoutMessages.StoredContext refund =
            org.nc.nccasino.payout.PayoutMessages.decodeContext(refundContexts.get(0));
        assertEquals("payout.context-currency-change", refund.localizationKey(),
            "a refund from a currency change does not claim the server restarted");
        assertEquals("Roulette", refund.gameType());
        assertEquals("Vault", refund.currency(), "it names the old currency");
        assertEquals("payout.context-server-restart", org.nc.nccasino.payout.PayoutMessages.decodeContext(
            org.nc.nccasino.payout.PayoutMessages.serverRestartRefundContext("Roulette")).localizationKey(),
            "restart refunds afterwards keep their message");
    }

    @Test
    void aMaterialSwitchSettlesInTheOldMaterial() throws Exception {
        when(plugin.playersMidGame(dealer)).thenReturn(Set.of(UUID.randomUUID()));
        AdminMenu menu = menu();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(menu).confirmCurrencyChange(eq(admin), any());
        ItemStack diamond = mock(ItemStack.class);
        when(diamond.getType()).thenReturn(Material.DIAMOND);
        when(diamond.clone()).thenReturn(diamond);

        menu.handleDrag(diamond, admin, mock(InventoryClickEvent.class));

        assertEquals("DIAMOND", config.getString("dealers.cf.currency.material"));
        assertEquals(List.of(
            "settle in STANDARD/EMERALD while config says STANDARD/EMERALD",
            "save",
            "rebuild with STANDARD/DIAMOND"), events);
    }

    @Test
    void withNobodyPlayingTheChangeIsAppliedWithoutAPrompt() throws Exception {
        when(plugin.playersMidGame(dealer)).thenReturn(Set.of());
        AdminMenu menu = menu();

        toggleMode(menu);

        verify(menu, never()).confirmCurrencyChange(any(), any());
        assertTrue(messages.isEmpty());
        assertEquals("VAULT", config.getString("dealers.cf.currency.mode"));
        assertEquals("settle in STANDARD/EMERALD while config says STANDARD/EMERALD", events.get(0));
    }

    private AdminMenu menu() throws Exception {
        AdminMenu menu = mock(AdminMenu.class, CALLS_REAL_METHODS);
        set(menu, Menu.class, "plugin", plugin);
        set(menu, Menu.class, "player", admin);
        set(menu, AdminMenu.class, "dealer", dealer);
        return menu;
    }

    private void toggleMode(AdminMenu menu) throws Exception {
        Method toggle = AdminMenu.class.getDeclaredMethod("handleToggleCurrencyMode", Player.class);
        toggle.setAccessible(true);
        toggle.invoke(menu, admin);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
