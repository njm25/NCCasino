package org.nc.nccasino.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

/**
 * A table deleted while the plugin keeps running (every dealer is rebuilt by
 * /ncc reload) must settle its own players through the shutdown policy before
 * its round is stopped, and must leave every other game's players alone.
 */
class ServerTeardownTest {

    private MockedStatic<Bukkit> bukkit;
    private Nccasino plugin;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), nullable(String.class)))
            .thenAnswer(invocation -> mock(Inventory.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("ServerTeardownTest"));
        plugin = mock(Nccasino.class);
        when(plugin.getChipValue(anyString(), anyInt())).thenReturn(1.0);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void deleteSettlesItsOwnPlayersBeforeStoppingTheRound() {
        TeardownServer server = new TeardownServer(plugin);
        Player seated = onlinePlayer();
        Player leftMidRound = onlinePlayer();
        TeardownClient client = (TeardownClient) server.getOrCreateClient(seated);
        TerminableSession otherGame = (id, reason) -> server.events.add("other-game");
        SessionRegistry.register(seated.getUniqueId(), otherGame);
        SessionRegistry.register(leftMidRound.getUniqueId(), server.ridingSession);

        server.delete();

        assertEquals(List.of(ExitReason.PLUGIN_DISABLE), client.terminations);
        assertTrue(server.events.contains("riding:PLUGIN_DISABLE"), "a stake riding a round is settled too");
        assertEquals("cancel", server.events.get(server.events.size() - 1),
            "the round is stopped only after its players are settled");
        assertFalse(server.events.contains("other-game"));
        assertTrue(SessionRegistry.isRegistered(seated.getUniqueId(), otherGame), "another game's session survives");
        assertFalse(SessionRegistry.isRegistered(leftMidRound.getUniqueId(), server.ridingSession));

        SessionRegistry.unregister(seated.getUniqueId(), otherGame);
    }

    @Test
    void aClientRemovedByTeardownIgnoresALateClick() {
        TeardownServer server = new TeardownServer(plugin);
        Player seated = onlinePlayer();
        TeardownClient client = (TeardownClient) server.getOrCreateClient(seated);

        server.delete();

        assertTrue(client.isRetired());
        client.handleClick(10, seated, mock(InventoryClickEvent.class));
        assertEquals(0, client.clicks);
    }

    @Test
    void aClientStillAtItsTableHandlesClicks() {
        TeardownServer server = new TeardownServer(plugin);
        Player seated = onlinePlayer();
        TeardownClient client = (TeardownClient) server.getOrCreateClient(seated);

        client.handleClick(10, seated, mock(InventoryClickEvent.class));

        assertFalse(client.isRetired());
        assertEquals(1, client.clicks);
        SessionRegistry.unregister(seated.getUniqueId(), client);
    }

    private static Player onlinePlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static final class TeardownServer extends Server {
        final List<String> events = new ArrayList<>();
        final TerminableSession ridingSession = (id, reason) -> events.add("riding:" + reason);

        TeardownServer(Nccasino plugin) {
            super(UUID.randomUUID(), plugin, "teardown-dealer");
        }

        @Override
        protected Client createClientForPlayer(Player player) {
            return new TeardownClient(this, player, plugin);
        }

        @Override
        public void onClientUpdate(Client client, String eventType, Object data) {
        }

        @Override
        protected boolean ownsSession(TerminableSession session) {
            return super.ownsSession(session) || session == ridingSession;
        }

        @Override
        protected void cancelScheduledTasks() {
            events.add("cancel");
        }
    }

    private static final class TeardownClient extends Client implements TerminableSession {
        final List<ExitReason> terminations = new ArrayList<>();
        int clicks;

        TeardownClient(Server server, Player player, Nccasino plugin) {
            super(server, player, "teardown", plugin, "teardown-dealer");
            SessionRegistry.register(player.getUniqueId(), this);
        }

        @Override
        protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
            clicks++;
        }

        @Override
        public void onServerUpdate(String eventType, Object data) {
        }

        @Override
        public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
            terminations.add(reason);
            server.removeClient(terminatedPlayerId);
        }
    }
}
