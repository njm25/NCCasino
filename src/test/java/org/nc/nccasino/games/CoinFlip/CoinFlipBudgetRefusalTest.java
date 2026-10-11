package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.budget.AdmissionDecision;
import org.nc.nccasino.budget.Commitment;
import org.nc.nccasino.budget.DealerBudgetService;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

/**
 * When a LIMITED dealer cannot cover the next flip, the pick is refused --
 * and the player's client, which already showed that pick as locked in,
 * must be told so it can unlock and offer Cash Out again. Without that the
 * table sits on "Round started" with nothing the player can click.
 */
class CoinFlipBudgetRefusalTest {

    private MockedStatic<Bukkit> bukkit;
    private Nccasino plugin;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), nullable(String.class)))
            .thenAnswer(invocation -> mock(Inventory.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("CoinFlipBudgetRefusalTest"));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> mock(BukkitTask.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        plugin = mock(Nccasino.class);
        when(plugin.getChipValue(anyString(), anyInt())).thenReturn(1.0);
        when(plugin.getCoinFlipMode(anyString())).thenReturn(CoinFlipMode.PLAYER_VS_DEALER);
        when(plugin.getCurrency(anyString())).thenReturn(Material.EMERALD);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CoinFlipBudgetRefusalTest"));
        DealerBudgetService budget = mock(DealerBudgetService.class);
        when(budget.reserve(any(), any(), any(), any(), any(), any()))
            .thenReturn(Commitment.refused(AdmissionDecision.INSUFFICIENT_FUNDS));
        when(plugin.getDealerBudgetService()).thenReturn(budget);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void aRefusedPickIsReportedBackSoThePlayerCanCashOut() {
        TableServer server = new TableServer(plugin);
        Player alice = player();
        StubClient client = (StubClient) server.getOrCreateClient(alice);
        server.onClientUpdate(client, "PLAYER_SUBMIT_BET", 10L);

        server.onClientUpdate(client, "PLAYER_PICK_LEFT", null);

        assertTrue(client.updates.contains("PICK_REFUSED"), "the client must learn its pick was refused");
        assertFalse(client.updates.contains("WINNER"), "no coin is flipped for a refused pick");
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static final class TableServer extends CoinFlipServer {
        TableServer(Nccasino plugin) {
            super(UUID.randomUUID(), plugin, "coin-flip-dealer");
        }

        @Override
        protected Client createClientForPlayer(Player player) {
            return new StubClient(this, player, plugin);
        }
    }

    private static final class StubClient extends Client {
        final List<String> updates = new ArrayList<>();

        StubClient(Server server, Player player, Nccasino plugin) {
            super(server, player, "coin-flip", plugin, "coin-flip-dealer");
        }

        @Override
        protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        }

        @Override
        public void onServerUpdate(String eventType, Object data) {
            updates.add(eventType);
        }
    }
}
