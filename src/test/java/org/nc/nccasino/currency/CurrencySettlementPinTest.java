package org.nc.nccasino.currency;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.commands.ReloadCommand;
import org.nc.nccasino.economy.VaultHook;
import org.nc.nccasino.localization.LocalizationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * X1: a dealer's tables settling after its currency changed (a hand-edited
 * config reloaded with /ncc reload) pay in the currency they were played in.
 * While they settle, every currency lookup for that dealer answers with the
 * old currency; afterwards the config's again.
 */
class CurrencySettlementPinTest {

    private org.mockito.MockedStatic<org.bukkit.plugin.java.JavaPlugin> javaPluginStatic;
    private YamlConfiguration config;
    private Nccasino plugin;
    private LivingEntity dealer;

    @BeforeEach
    void setUp() throws Exception {
        org.bukkit.plugin.java.JavaPlugin providing = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(providing.getName()).thenReturn("NCCasino");
        javaPluginStatic = org.mockito.Mockito.mockStatic(org.bukkit.plugin.java.JavaPlugin.class);
        javaPluginStatic.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(any())).thenReturn(providing);

        // The config already names the new currency: Vault.
        config = new YamlConfiguration();
        config.set("dealers.cf.currency.material", "DIAMOND");
        config.set("dealers.cf.currency.name", "Diamond");
        config.set("dealers.cf.currency.mode", "VAULT");
        plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CurrencySettlementPinTest"));
        VaultHook vault = mock(VaultHook.class);
        when(vault.isEconomyAvailable()).thenReturn(true);
        when(plugin.getVaultHook()).thenReturn(vault);
        doCallRealMethod().when(plugin).getCurrency(anyString());
        doCallRealMethod().when(plugin).getCurrencyName(anyString());
        doCallRealMethod().when(plugin).getCurrencyMode(anyString());
        doCallRealMethod().when(plugin).settlingCurrency(anyString());
        doCallRealMethod().when(plugin).currencySnapshot(anyString());
        doCallRealMethod().when(plugin).settleDealerTables(any(), any());
        // A mock skips field initializers; give it the maps the real plugin has.
        setField("invalidCurrencyWarned", java.util.concurrent.ConcurrentHashMap.newKeySet());
        setField("settlingCurrencies", new java.util.concurrent.ConcurrentHashMap<>());

        dealer = mock(LivingEntity.class);
        org.bukkit.persistence.PersistentDataContainer data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(dealer.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenReturn("cf");
    }

    @AfterEach
    void closeStatic() {
        javaPluginStatic.close();
    }

    @Test
    void tablesSettleInTheCurrencyTheyWerePlayedIn() {
        DealerCurrencySnapshot emeralds = new DealerCurrencySnapshot(CurrencyMode.STANDARD, Material.EMERALD, "Emerald");
        List<String> seenWhileSettling = new ArrayList<>();
        doAnswer(invocation -> {
            seenWhileSettling.add(DealerCurrencySettings.getMode(plugin, "cf") + "/" + plugin.getCurrency("cf") + "/" + plugin.getCurrencyName("cf"));
            seenWhileSettling.add("other dealer: " + DealerCurrencySettings.getMode(plugin, "other"));
            return null;
        }).when(plugin).deleteAssociatedInventories(dealer);

        plugin.settleDealerTables(dealer, emeralds);

        assertEquals(List.of("STANDARD/EMERALD/Emerald", "other dealer: STANDARD"), seenWhileSettling,
            "the provider (by mode), the item and its name all answer with the old currency");
        assertNull(plugin.settlingCurrency("cf"));
        assertEquals(CurrencyMode.VAULT, DealerCurrencySettings.getMode(plugin, "cf"), "afterwards the config's currency");
        assertEquals(Material.DIAMOND, plugin.getCurrency("cf"));
    }

    @Test
    void thePinIsReleasedEvenWhenATableFailsToSettle() {
        doAnswer(invocation -> {
            throw new IllegalStateException("broken table");
        }).when(plugin).deleteAssociatedInventories(dealer);

        assertThrows(IllegalStateException.class, () -> plugin.settleDealerTables(dealer,
            new DealerCurrencySnapshot(CurrencyMode.STANDARD, Material.EMERALD, "Emerald")));

        assertNull(plugin.settlingCurrency("cf"));
        assertEquals(Material.DIAMOND, plugin.getCurrency("cf"));
    }

    @Test
    void aSnapshotIsTheConfigsCurrencyNow() {
        assertEquals(new DealerCurrencySnapshot(CurrencyMode.VAULT, Material.DIAMOND, "Diamond"), plugin.currencySnapshot("cf"));
    }

    @Test
    void reloadTakesEachDealersCurrencyBeforeReadingTheNewConfig() {
        org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class);
        try {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
            Map<String, DealerCurrencySnapshot> before = Map.of("cf",
                new DealerCurrencySnapshot(CurrencyMode.STANDARD, Material.EMERALD, "Emerald"));
            when(plugin.currencySnapshots()).thenReturn(before);
            LocalizationService localization = mock(LocalizationService.class);
            when(plugin.getLocalization()).thenReturn(localization);

            new ReloadCommand(plugin).execute(mock(CommandSender.class), new String[0]);

            InOrder order = inOrder(plugin);
            order.verify(plugin).currencySnapshots();
            order.verify(plugin).reloadConfig();
            order.verify(plugin).reloadDealerConfigurations(before);
        } finally {
            bukkit.close();
        }
    }

    private void setField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = Nccasino.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
