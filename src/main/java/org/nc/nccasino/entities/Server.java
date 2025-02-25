package org.nc.nccasino.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.SoundHelper;

public abstract class Server extends DealerInventory {

    protected final Nccasino plugin;

    protected final Map<UUID, Client> clients = new HashMap<>();

    protected boolean bettingEnabled = true;

    protected SessionState serverState = SessionState.LOBBY;

    protected String internalName;

    protected final Map<UUID, SessionState> clientStates = new HashMap<>();

    public enum GameState { WAITING, RUNNING, PAUSED }

    protected GameState gameState = GameState.WAITING;

    public Server(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 9, "");
        this.plugin = plugin;
        this.internalName = internalName;
        registerListener();
    }

    public GameState getGameState() {
        return gameState;
    }

    protected void setGameState(GameState newState) {
        gameState = newState;
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
            String gameType = plugin.getConfig().getString("dealers." + internalName + ".game");

            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aWelcome to " + gameType);
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
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

    public abstract void onClientUpdate(Client client, String eventType, Object data);

    protected void broadcastUpdate(String eventType, Object data) {
        for (Client client : clients.values()) {
            client.onServerUpdate(eventType, data);
        }
    }

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

    protected void playCountdownSound() {
        for (Client client : clients.values()) {
            Player player = client.getPlayer();
            if (player != null) {
                if (SoundHelper.getSoundSafely("block.note_block.hat",player) != null) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }
        }
    }

    public boolean hasClient(UUID playerUuid) {
        return clients.containsKey(playerUuid);
    }
        
    protected void sendPayoutMessage(Player player, int payout, boolean isWinner) {
        String currencyName = plugin.getCurrencyName(internalName).toLowerCase();
        boolean isSingle = Math.abs(payout) == 1;
    
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
                player.sendMessage(isWinner
                        ? "§a§lPaid " + payout + " " + currencyName + (isSingle ? "" : "s")
                        : "§c§lYou lose!");
                break;
            case VERBOSE:
                int profit = Math.abs(payout / 2);
                player.sendMessage(isWinner
                        ? "§a§lPaid " + payout + " " + currencyName + (isSingle ? "" : "s") +
                          "\n §r§a§o(profit of " + profit + ")"
                        : "§c§lYou lose!");
                break;
            case NONE:
                break;
        }
    }
    
    protected void applyWinEffects(Player player) {
        if (player != null) {
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            playRandomPitchSound(player);
        }
    }
    
    protected void applyLoseEffects(Player player) {
        if (player != null) {
            if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
        }
    }
    
    protected void playRandomPitchSound(Player player) {
        if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null) {
            Random random = new Random();
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f, 0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, chosenPitch);
            }
        }
    }

    
    protected void creditPlayer(Player player, double amount) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            player.sendMessage("Error: Currency material is not set. Unable to credit winnings.");
            return;
        }

        int fullStacks = (int) amount / 64;
        int remainder = (int) amount % 64;
        int totalLeftoverAmount = 0;
        HashMap<Integer, ItemStack> leftover;

        // Try adding full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(currencyMaterial, 64);
            leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }

        // Try adding remainder
        if (remainder > 0) {
            ItemStack remainderStack = new ItemStack(currencyMaterial, remainder);
            leftover = player.getInventory().addItem(remainderStack);
            if (!leftover.isEmpty()) {
                totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }

        if (totalLeftoverAmount > 0) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");

                    break;}
                case VERBOSE:{
                    player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            dropExcessItems(player, totalLeftoverAmount, currencyMaterial);
        }
    }

    protected void dropExcessItems(Player player, int amount, Material currencyMaterial) {
        while (amount > 0) {
            int dropAmount = Math.min(amount, 64);
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(currencyMaterial, dropAmount));
            amount -= dropAmount;
        }
    }
}
