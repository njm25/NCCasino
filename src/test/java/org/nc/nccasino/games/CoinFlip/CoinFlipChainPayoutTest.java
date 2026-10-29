package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.payout.PendingPayoutStore;

/**
 * X2 at the table: a Coin Flip chain against the dealer pays 1.98 times the
 * pot per win, in cents on Vault ($10 pays $19.80) and with the fraction
 * rounded by a roll on items; a win owed to a player who left survives a
 * restart with its cents.
 */
class CoinFlipChainPayoutTest {

    @TempDir
    Path dataFolder;

    private MockedStatic<Bukkit> bukkit;
    private Nccasino plugin;
    private Player alice;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), nullable(String.class)))
            .thenAnswer(invocation -> mock(Inventory.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("CoinFlipChainPayoutTest"));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> mock(BukkitTask.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        plugin = mock(Nccasino.class);
        when(plugin.getChipValue(anyString(), anyInt())).thenReturn(1.0);
        when(plugin.getCoinFlipMode(anyString())).thenReturn(CoinFlipMode.PLAYER_VS_DEALER);
        when(plugin.getCurrency(anyString())).thenReturn(Material.EMERALD);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CoinFlipChainPayoutTest"));
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        alice = mock(Player.class);
        when(alice.getUniqueId()).thenReturn(UUID.randomUUID());
        when(alice.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void vaultTenPaysExactly1980() {
        when(plugin.getCurrencyMode(anyString())).thenReturn(CurrencyMode.VAULT);
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
        TableServer server = new TableServer(plugin);
        StubClient client = (StubClient) server.getOrCreateClient(alice);

        winOnce(server, client, 10L);
        assertEquals(19.8, client.lastPot, "the pot the player sees");
        server.onClientUpdate(client, "PLAYER_CASH_OUT", null);

        assertEquals(List.of(19.8), server.credited);
    }

    @Test
    void anItemChainRoundsEachWinByTheRoll() {
        TableServer server = new TableServer(plugin);
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
        StubClient client = (StubClient) server.getOrCreateClient(alice);
        server.roundingRoll = () -> 0.5;

        winOnce(server, client, 10L); // 19.8: the roll 0.5 is below 0.8, so 20
        assertEquals(20.0, client.lastPot);
        winAgain(server, client); // 39.6: 0.5 is below 0.6, so 40
        assertEquals(40.0, client.lastPot);
        server.roundingRoll = () -> 0.9;
        winAgain(server, client); // 79.2: 0.9 is not below 0.2, so 79
        assertEquals(79.0, client.lastPot);
        server.onClientUpdate(client, "PLAYER_CASH_OUT", null);

        assertEquals(List.of(79.0), server.credited);
    }

    @Test
    void aVaultChainKeepsItsCents() {
        when(plugin.getCurrencyMode(anyString())).thenReturn(CurrencyMode.VAULT);
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
        TableServer server = new TableServer(plugin);
        StubClient client = (StubClient) server.getOrCreateClient(alice);
        server.roundingRoll = () -> 0.5;

        winOnce(server, client, 10L);
        winAgain(server, client); // 1980 cents x 1.98 = 3920.4: 0.5 is not below 0.4
        assertEquals(39.2, client.lastPot);
        server.onClientUpdate(client, "PLAYER_CASH_OUT", null);

        assertEquals(List.of(39.2), server.credited);
    }

    @Test
    void aVaultWinOwedToAPlayerWhoLeftSurvivesARestartWithItsCents() {
        when(plugin.getCurrencyMode(anyString())).thenReturn(CurrencyMode.VAULT);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(store);
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
        TableServer server = new TableServer(plugin);
        StubClient client = (StubClient) server.getOrCreateClient(alice);
        winOnce(server, client, 10L);

        // Alice is gone when the chain is cashed out.
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(null);
        server.onClientUpdate(client, "PLAYER_CASH_OUT", null);

        assertTrue(server.credited.isEmpty());
        List<PendingPayout> afterRestart = new PendingPayoutStore(plugin).getPending(alice.getUniqueId());
        assertEquals(1, afterRestart.size());
        assertEquals(19.8, afterRestart.get(0).amount());
        assertEquals(CurrencyMode.VAULT, afterRestart.get(0).currencyMode());
    }

    /** Bets {@code stake} and wins the first flip. */
    private void winOnce(TableServer server, StubClient client, long stake) {
        server.onClientUpdate(client, "PLAYER_SUBMIT_BET", stake);
        winAgain(server, client);
    }

    /** Picks left and the coin lands left: a win, and the chain continues. */
    private void winAgain(TableServer server, StubClient client) {
        int pots = client.pots.size();
        server.onClientUpdate(client, "PLAYER_PICK_LEFT", null);
        Object[] winner = client.lastWinner;
        server.onClientUpdate(client, "ANIMATION_FINISHED", new Object[] {winner[0], winner[1]});
        assertEquals(pots + 1, client.pots.size(), "the flip was won and the chain continued");
    }

    private static final class TableServer extends CoinFlipServer {
        final List<Double> credited = new ArrayList<>();

        TableServer(Nccasino plugin) {
            super(UUID.randomUUID(), plugin, "coin-flip-dealer");
            coin = () -> 0;
        }

        @Override
        protected Client createClientForPlayer(Player player) {
            return new StubClient(this, player, plugin);
        }

        @Override
        protected void creditPlayer(Player player, double amount) {
            credited.add(amount);
        }

        @Override
        public void sendPayoutMessage(Player player, double payout, boolean isWinner, double profit) {
        }

        @Override
        public void applyWinEffects(Player player) {
        }

        @Override
        public void applyLoseEffects(Player player) {
        }
    }

    private static final class StubClient extends Client {
        final List<Double> pots = new ArrayList<>();
        double lastPot;
        Object[] lastWinner;

        StubClient(Server server, Player player, Nccasino plugin) {
            super(server, player, "coin-flip", plugin, "coin-flip-dealer");
        }

        @Override
        protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        }

        @Override
        public void onServerUpdate(String eventType, Object data) {
            if ("WINNER".equals(eventType)) {
                lastWinner = (Object[]) data;
            } else if ("CHAIN_WIN".equals(eventType)) {
                lastPot = ((Number) ((Object[]) data)[1]).doubleValue();
                pots.add(lastPot);
            }
        }
    }
}
