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

/**
 * Abstract "Server" class representing the server-side portion
 * of a game. This class manages multiple Client instances,
 * one for each player who joins the game, and can manage
 * a "session" or "game state" in a standardized way.
 */
public abstract class Server extends DealerInventory {

    protected final Nccasino plugin;

    /**
     * Tracks all clients currently served by this server (one per player).
     */
    protected final Map<UUID, Client> clients = new HashMap<>();

    /**
     * Example server-wide setting you might want to share with clients.
     */
    protected boolean bettingEnabled = true;

    /**
     * An example of a server-level "session state". 
     * Could be an enum describing the overall phase of the game,
     * e.g. LOBBY, IN_PROGRESS, FINISHED, etc.
     */
    protected SessionState serverState = SessionState.LOBBY;

    protected String internalName;

    /**
     * If you want to store per-player session states, you can track them here.
     * Or store more robust data structures if needed.
     */
    protected final Map<UUID, SessionState> clientStates = new HashMap<>();

    /**
     * Constructs a new Server instance.
     *
     * @param dealerId  Unique ID for the dealer
     * @param size      Size of the inventory (slots)
     * @param title     Title for the server inventory (if used)
     * @param plugin    Main plugin reference
     */
    public Server(UUID dealerId, int size, String title, Nccasino plugin, String internalName) {
        super(dealerId, size, title);
        this.plugin = plugin;
        this.internalName = internalName;
        registerListener();
    }

    /**
     * Possible states for the entire "session".
     * You can expand or rename these based on your own game flow.
     */
    public enum SessionState {
        LOBBY,
        IN_PROGRESS,
        FINISHED
        // etc.
    }

    /************************************************
     *             GAME STATE MANAGEMENT
     ***********************************************/

    /**
     * Updates the overall server session state and notifies all clients.
     */
    public void setServerState(SessionState newState) {
        SessionState oldState = this.serverState;
        this.serverState = newState;

        // Broadcast update to all clients
        broadcastServerStateUpdate(oldState, newState);
    }

    /**
     * Get the current overall server session state.
     */
    public SessionState getServerState() {
        return serverState;
    }

    /**
     * Update an individual client's session state (if each player 
     * may be in a slightly different "sub-state" than the global state).
     *
     * For example, in some games, each player has their own local "PLAYING" or "FOLDED" state. 
     */
    public void setClientState(UUID playerUuid, SessionState newState) {
        SessionState oldState = clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
        clientStates.put(playerUuid, newState);

        // Notify just that single client
        sendStateUpdateToClient(playerUuid, oldState, newState);
    }

    /**
     * Retrieves a player's individual session state.
     * If none is stored, returns a default (e.g. LOBBY).
     */
    public SessionState getClientState(UUID playerUuid) {
        return clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
    }

    /**
     * Sends a state update to a single client.
     * The client can override `onServerStateChange(...)` to handle it.
     */
    protected void sendStateUpdateToClient(UUID playerUuid, SessionState oldState, SessionState newState) {
        Client client = clients.get(playerUuid);
        if (client != null) {
            client.onServerStateChange(oldState, newState);
        }
    }

    /**
     * Broadcasts a state update to all connected clients.
     */
    protected void broadcastServerStateUpdate(SessionState oldState, SessionState newState) {
        for (Client client : clients.values()) {
            client.onServerStateChange(oldState, newState);
        }
    }


    /************************************************
     *             CLIENT MANAGEMENT
     ***********************************************/

    /**
     * Handle the player who directly opened the "Server" inventory.
     * Usually, you might just redirect them to a Client UI.
     */
    protected void handleServerOpen(Player player) {
        Client client = getOrCreateClient(player);
        if (client != null) {
            player.openInventory(client.getInventory());
        } else {
        }
    }
    

    /**
     * Get an existing Client for the given player, or create one if none exists.
     */
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

    /**
     * Create a Client instance for the given player.
     * This is a factory method you must implement in a subclass,
     * e.g. MinesServer creates a MinesClient.
     */
    protected abstract Client createClientForPlayer(Player player);

    /**
     * Called to remove a Client from this server, e.g. if the
     * player closes the game or the game ends for them.
     *
     * @param uuid Player UUID
     */
    public void removeClient(UUID uuid) {
        // Remove from the states map if desired
        clientStates.remove(uuid);

        // Remove from client map
        Client client = clients.remove(uuid);
        if (client != null) {
            client.cleanup();
        }
    }

    /************************************************
     *             EVENT LISTENER HOOKS
     ***********************************************/

    /**
     * Called by the framework or plugin when the server inventory is opened.
     * Typically, you only want to "serve" the clients by opening their
     * individual game GUIs, rather than letting players see this raw inventory.
     */
    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() == this) {
            Player player = (Player) event.getPlayer();
    
            event.setCancelled(true); // Prevents the server inventory from actually opening
    
            handleServerOpen(player);
        }
    }
    
    /**
     * Handles updates received from a client. Subclasses should override this
     * to process different types of updates.
     *
     * @param client    The client sending the update.
     * @param eventType The type of event (e.g., "SLOT_CLICKED").
     * @param data      Any extra data related to the event (e.g., slot number).
     */
    public void onClientUpdate(Client client, String eventType, Object data){}

    /**
     * Registers this Server as an event listener.
     */
    protected void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Unregisters this Server from Bukkit events.
     */
    protected void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    /**
     * Called when this Server is no longer needed.
     * Clean up all resources, clients, and event registrations.
     */
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
