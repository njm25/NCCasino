package org.nc.nccasino.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.nc.nccasino.Nccasino;

public abstract class Server extends DealerInventory {

    protected final Nccasino plugin;

    protected final Map<UUID, Client> clients = new HashMap<>();

    protected boolean bettingEnabled = true;

    protected SessionState serverState = SessionState.LOBBY;

    protected String internalName;

    protected final Map<UUID, SessionState> clientStates = new HashMap<>();

    public Server(UUID dealerId, String title, Nccasino plugin, String internalName) {
        super(dealerId, 9, title);
        this.plugin = plugin;
        this.internalName = internalName;
        registerListener();
    }

    public enum SessionState {
        LOBBY,
        IN_PROGRESS,
        FINISHED
        // etc.
    }

    public void setServerState(SessionState newState) {
        SessionState oldState = this.serverState;
        this.serverState = newState;

        // Broadcast update to all clients
        broadcastServerStateUpdate(oldState, newState);
    }

    public SessionState getServerState() {
        return serverState;
    }

    public void setClientState(UUID playerUuid, SessionState newState) {
        SessionState oldState = clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
        clientStates.put(playerUuid, newState);

        // Notify just that single client
        sendStateUpdateToClient(playerUuid, oldState, newState);
    }

    public SessionState getClientState(UUID playerUuid) {
        return clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
    }

    protected void sendStateUpdateToClient(UUID playerUuid, SessionState oldState, SessionState newState) {
        Client client = clients.get(playerUuid);
        if (client != null) {
            client.onServerStateChange(oldState, newState);
        }
    }

    protected void broadcastServerStateUpdate(SessionState oldState, SessionState newState) {
        for (Client client : clients.values()) {
            client.onServerStateChange(oldState, newState);
        }
    }

    protected void handleServerOpen(Player player) {
        Client client = getOrCreateClient(player);
        if (client != null) {
            player.openInventory(client.getInventory());
        } else {
        }
    }

    public Client getOrCreateClient(Player player) {
        UUID uuid = player.getUniqueId();
        if (clients.containsKey(uuid)) {
            return clients.get(uuid);
        }
        // Create a new client using the abstract factory method
        Client newClient = createClientForPlayer(player);
        clients.put(uuid, newClient);

        // Optionally, store an initial session state for them
        clientStates.put(uuid, SessionState.LOBBY);

        return newClient;
    }

    protected abstract Client createClientForPlayer(Player player);

    public void removeClient(UUID uuid) {
        // Remove from the states map if desired
        clientStates.remove(uuid);

        // Remove from client map
        Client client = clients.remove(uuid);
        if (client != null) { 
            client.cleanup();
        }
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() == this) {
            Player player = (Player) event.getPlayer();
    
            event.setCancelled(true); // Prevents the server inventory from actually opening
    
            handleServerOpen(player);
        }
    }

    public void onClientUpdate(Client client, String eventType, Object data){}

    protected void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    protected void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void delete() {
        // Let the parent class handle any standard cleanup
        super.delete();

        // Clean up each client
        for (Client client : clients.values()) {
            client.cleanup();
        }
        clients.clear();
        clientStates.clear();

        // Unregister from Bukkit events
        unregisterListener();
    }
}
