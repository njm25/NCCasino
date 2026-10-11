package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.payout.PendingPayoutStore;

/**
 * Once a PvP round settles, a chair whose player already left must empty --
 * a departed chair-2 player must never be moved up into chair 1 and left
 * there, where it blocks the table for everyone who comes next.
 */
class CoinFlipPvpReseatTest {

    private MockedStatic<Bukkit> bukkit;
    private Nccasino plugin;
    private final List<Runnable> repeatingTasks = new ArrayList<>();
    private final List<Runnable> delayedTasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), nullable(String.class)))
            .thenAnswer(invocation -> mock(Inventory.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("CoinFlipPvpReseatTest"));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncRepeatingTask(any(), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(invocation -> {
                repeatingTasks.add(invocation.getArgument(1));
                return repeatingTasks.size();
            });
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                delayedTasks.add(invocation.getArgument(1));
                return mock(BukkitTask.class);
            });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        plugin = mock(Nccasino.class);
        when(plugin.getChipValue(anyString(), anyInt())).thenReturn(1.0);
        when(plugin.getCoinFlipMode(anyString())).thenReturn(CoinFlipMode.PLAYER_VS_PLAYER);
        when(plugin.getTimer(anyString())).thenReturn(0);
        when(plugin.getCurrency(anyString())).thenReturn(Material.EMERALD);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CoinFlipPvpReseatTest"));
        PendingPayoutStore pendingPayouts = mock(PendingPayoutStore.class);
        when(pendingPayouts.addPendingPayout(any())).thenReturn(true);
        when(plugin.getPendingPayoutStore()).thenReturn(pendingPayouts);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void bothPlayersLeavingMidRoundLeavesBothChairsEmpty() {
        TableServer server = new TableServer(plugin);
        Player alice = player();
        Player bob = player();
        StubClient aliceClient = (StubClient) server.getOrCreateClient(alice);
        StubClient bobClient = (StubClient) server.getOrCreateClient(bob);
        server.onClientUpdate(aliceClient, "PLAYER_SIT_ONE", null);
        server.onClientUpdate(bobClient, "PLAYER_SIT_TWO", null);
        server.onClientUpdate(aliceClient, "PLAYER_SUBMIT_BET", 10L);
        server.onClientUpdate(bobClient, "PLAYER_ACCEPT_BET", null);

        // Both disconnect mid-round; their stakes ride to the result.
        server.removeClient(alice.getUniqueId());
        server.removeClient(bob.getUniqueId());
        runAll(repeatingTasks); // the countdown runs out and the coin is flipped
        runAll(delayedTasks);   // nobody is left to finish the animation, so the fallback settles it

        Object[] chairs = chairsSeenBy(server, player());
        assertNull(chairs[0], "chair 1 must not be held by a player who left");
        assertNull(chairs[1], "chair 2 must not be held by a player who left");
    }

    @Test
    void chairOneLeavingStillMovesAConnectedChairTwoUp() {
        TableServer server = new TableServer(plugin);
        Player alice = player();
        Player bob = player();
        StubClient aliceClient = (StubClient) server.getOrCreateClient(alice);
        StubClient bobClient = (StubClient) server.getOrCreateClient(bob);
        server.onClientUpdate(aliceClient, "PLAYER_SIT_ONE", null);
        server.onClientUpdate(bobClient, "PLAYER_SIT_TWO", null);
        server.onClientUpdate(aliceClient, "PLAYER_SUBMIT_BET", 10L);
        server.onClientUpdate(bobClient, "PLAYER_ACCEPT_BET", null);

        server.removeClient(alice.getUniqueId());
        runAll(repeatingTasks);
        runAll(delayedTasks);

        Object[] chairs = chairsSeenBy(server, player());
        assertEquals(bob, chairs[0], "the player still at the table moves up to chair 1");
        assertNull(chairs[1]);
    }

    private static void runAll(List<Runnable> tasks) {
        for (Runnable task : new ArrayList<>(tasks)) {
            task.run();
        }
    }

    private static Object[] chairsSeenBy(TableServer server, Player newcomer) {
        StubClient client = (StubClient) server.getOrCreateClient(newcomer);
        server.onClientUpdate(client, "GET_CHAIRS", null);
        return (Object[]) client.updates.get("GET_CHAIRS");
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
        final Map<String, Object> updates = new HashMap<>();

        StubClient(Server server, Player player, Nccasino plugin) {
            super(server, player, "coin-flip", plugin, "coin-flip-dealer");
        }

        @Override
        protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        }

        @Override
        public void onServerUpdate(String eventType, Object data) {
            updates.put(eventType, data);
        }
    }
}
