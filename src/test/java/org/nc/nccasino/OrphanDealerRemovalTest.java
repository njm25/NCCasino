package org.nc.nccasino;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nc.nccasino.commands.DeleteCommand;
import org.nc.nccasino.localization.LocalizationService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orphan dealers: records in config.yml and data/dealers.yaml whose mob or
 * NPC is gone. {@link Nccasino#removeOrphanDealer} must remove both records
 * and nothing else, must leave a dealer alone if its body turns up, and must
 * never save over an unreadable dealers.yaml; {@code /ncc delete <name>} must
 * report success only when the records were removed.
 */
class OrphanDealerRemovalTest {

    @TempDir
    File dataFolder;

    private Nccasino plugin;
    private YamlConfiguration config;
    private File dealersFile;
    private MockedStatic<JavaPlugin> javaPluginStatic;

    @BeforeEach
    void setUp() throws Exception {
        // As in CitizensDealerSupportTest: Dealer's static initializer needs
        // a providing plugin outside a real plugin classloader.
        JavaPlugin providingPlugin = mock(JavaPlugin.class);
        when(providingPlugin.getName()).thenReturn("NCCasino");
        javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);
        javaPluginStatic.when(() -> JavaPlugin.getProvidingPlugin(any())).thenReturn(providingPlugin);

        config = new YamlConfiguration();
        config.set("dealers.orphan.game", "Blackjack");
        config.set("dealers.orphan.timer", 10);
        config.set("dealers.keeper.game", "Roulette");

        dealersFile = new File(dataFolder, "data/dealers.yaml");
        dealersFile.getParentFile().mkdirs();
        YamlConfiguration dealers = new YamlConfiguration();
        dealers.set("dealers.orphan.world", "world");
        dealers.set("dealers.orphan.X", 20000.5);
        dealers.set("dealers.keeper.world", "world");
        dealers.set("dealers.keeper.X", 6.5);
        dealers.save(dealersFile);

        plugin = mock(Nccasino.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OrphanDealerRemovalTest"));
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getConfig()).thenReturn(config);
        doCallRealMethod().when(plugin).removeOrphanDealer(anyString());
    }

    @AfterEach
    void tearDown() {
        javaPluginStatic.close();
    }

    private YamlConfiguration dealersOnDisk() {
        return YamlConfiguration.loadConfiguration(dealersFile);
    }

    @Test
    void removesBothRecordsOfAnOrphanAndNothingElse() {
        assertTrue(plugin.removeOrphanDealer("orphan"));

        assertFalse(config.contains("dealers.orphan"));
        assertTrue(config.contains("dealers.keeper.game"));
        verify(plugin).saveConfig();
        assertFalse(dealersOnDisk().contains("dealers.orphan"));
        assertEquals(6.5, dealersOnDisk().getDouble("dealers.keeper.X"));
    }

    @Test
    void leavesADealerWhoseBodyTurnedUpAlone() {
        when(plugin.getDealerByInternalName("orphan")).thenReturn(mock(LivingEntity.class));

        assertFalse(plugin.removeOrphanDealer("orphan"));

        assertTrue(config.contains("dealers.orphan.game"));
        verify(plugin, never()).saveConfig();
        assertTrue(dealersOnDisk().contains("dealers.orphan"));
    }

    @Test
    void removesALocationLeftWithoutSettings() {
        config.set("dealers.orphan", null);

        assertTrue(plugin.removeOrphanDealer("orphan"));

        verify(plugin, never()).saveConfig();
        assertFalse(dealersOnDisk().contains("dealers.orphan"));
        assertTrue(dealersOnDisk().contains("dealers.keeper"));
    }

    @Test
    void neverSavesOverAnUnreadableDealersFile() throws Exception {
        String broken = "dealers:\n  orphan: [unclosed\n  keeper:\n    world: world\n";
        Files.writeString(dealersFile.toPath(), broken, StandardCharsets.UTF_8);

        assertTrue(plugin.removeOrphanDealer("orphan"));

        assertEquals(broken, Files.readString(dealersFile.toPath(), StandardCharsets.UTF_8));
        assertFalse(config.contains("dealers.orphan"));
    }

    @Test
    void deleteCommandReportsAnOrphanAsDeleted() {
        Player sender = senderDeleting("orphan");
        orphanCallbackFor(sender, "orphan").run();

        assertFalse(config.contains("dealers.orphan"));
        verify(sender).sendMessage("deleted orphan");
    }

    @Test
    void deleteCommandSaysNothingWhenTheBodyTurnedUp() {
        Player sender = senderDeleting("orphan");
        Runnable onOrphan = orphanCallbackFor(sender, "orphan");
        when(plugin.getDealerByInternalName("orphan")).thenReturn(mock(LivingEntity.class));
        onOrphan.run();

        assertTrue(config.contains("dealers.orphan"));
        verify(sender, never()).sendMessage(anyString());
    }

    private Player senderDeleting(String name) {
        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(CommandSender.class), eq("commands.dealer-deleted"), eq("name"), eq(name)))
            .thenReturn("deleted " + name);
        when(plugin.getLocalization()).thenReturn(localization);
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        return sender;
    }

    private Runnable orphanCallbackFor(Player sender, String name) {
        new DeleteCommand(plugin).execute(sender, new String[] {"delete", name});
        ArgumentCaptor<Runnable> onOrphan = ArgumentCaptor.forClass(Runnable.class);
        verify(plugin).executeOnDealer(eq(name), any(Runnable.class), onOrphan.capture());
        return onOrphan.getValue();
    }
}
