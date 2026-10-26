package org.nc.nccasino.games.Baccarat;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.entities.Client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * On a LIMITED Baccarat dealer the hand's reservation lives on the player's
 * client. A player who pressed Esc or disconnected while their bet rode the hand
 * left with that client, so the hand's result could no longer settle the
 * reservation and the dealer's capacity drained for good.
 */
class BaccaratBudgetHolderTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void aClientThatLeavesWithARidingBetStillSettlesTheHand() throws Exception {
        BaccaratServer server = server();
        BaccaratClient leaving = client(true);
        clients(server).put(player, leaving);
        playerBets(server).put(player, new HashMap<>(Map.of(BaccaratClient.BetOption.PLAYER, 10.0)));

        server.removeClient(player);

        assertSame(leaving, server.takeBudgetHolder(player), "the hand's result settles the departed client's reservation");
        assertNull(server.takeBudgetHolder(player), "and only once");
    }

    @Test
    void aPlayerWhoReopensTheTableIsStillSettledThroughTheirOriginalReservation() throws Exception {
        BaccaratServer server = server();
        BaccaratClient leaving = client(true);
        clients(server).put(player, leaving);
        playerBets(server).put(player, new HashMap<>(Map.of(BaccaratClient.BetOption.PLAYER, 10.0)));
        server.removeClient(player);

        BaccaratClient reopened = client(false);
        clients(server).put(player, reopened);

        assertSame(leaving, server.takeBudgetHolder(player));
        assertSame(reopened, server.takeBudgetHolder(player), "afterwards the open client is the holder again");
    }

    @Test
    void aClientLeavingWithoutBetsIsNotKept() throws Exception {
        BaccaratServer server = server();
        clients(server).put(player, client(true));

        server.removeClient(player);

        assertNull(server.takeBudgetHolder(player));
    }

    @Test
    void anOpenClientWithItsOwnReservationIsTheHolder() throws Exception {
        BaccaratServer server = server();
        BaccaratClient open = client(true);
        clients(server).put(player, open);

        assertSame(open, server.takeBudgetHolder(player));
    }

    @Test
    void aPlayerWhoUndoesEveryBetIsNoLongerABettor() throws Exception {
        BaccaratServer server = server();
        playerBets(server).put(player, new HashMap<>(Map.of(BaccaratClient.BetOption.PLAYER, 10.0)));
        org.bukkit.entity.Player bukkitPlayer = mock(org.bukkit.entity.Player.class);
        when(bukkitPlayer.getUniqueId()).thenReturn(player);

        server.undoLastBet(bukkitPlayer, BaccaratClient.BetOption.PLAYER, 10.0);

        // Otherwise the hand's result "paid" them 0 with a push sound.
        org.junit.jupiter.api.Assertions.assertFalse(playerBets(server).containsKey(player));
    }

    /** A server built without its constructor (which needs a live plugin and inventory). */
    private static BaccaratServer server() throws Exception {
        BaccaratServer server = mock(BaccaratServer.class, CALLS_REAL_METHODS);
        set(server, org.nc.nccasino.entities.Server.class, "clients", new HashMap<UUID, Client>());
        set(server, org.nc.nccasino.entities.Server.class, "clientStates", new HashMap<>());
        set(server, BaccaratServer.class, "playerBets", new HashMap<>());
        set(server, BaccaratServer.class, "departedBudgetHolders", new HashMap<>());
        set(server, BaccaratServer.class, "totalBets", new HashMap<>());
        return server;
    }

    private static BaccaratClient client(boolean holdsReservation) {
        BaccaratClient client = mock(BaccaratClient.class);
        when(client.holdsBudgetCommitment()).thenReturn(holdsReservation);
        return client;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Client> clients(BaccaratServer server) throws Exception {
        return (Map<UUID, Client>) get(server, org.nc.nccasino.entities.Server.class, "clients");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Map<BaccaratClient.BetOption, Double>> playerBets(BaccaratServer server) throws Exception {
        return (Map<UUID, Map<BaccaratClient.BetOption, Double>>) get(server, BaccaratServer.class, "playerBets");
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
