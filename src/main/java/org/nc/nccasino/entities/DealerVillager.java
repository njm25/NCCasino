package org.nc.nccasino.entities;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.games.DealerInventory;
import org.nc.nccasino.games.GameMenuInventory;
import org.nc.nccasino.games.BlackjackInventory;
import org.nc.nccasino.games.RouletteInventory;
import org.nc.nccasino.games.BettingTable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DealerVillager {

    private static final NamespacedKey DEALER_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_villager");
    private static final NamespacedKey UNIQUE_ID_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_unique_id");
    private static final NamespacedKey NAME_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_name");
    private static final NamespacedKey GAME_TYPE_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_game_type");

    // Map to store player betting tables associated with each dealer
    private static final Map<UUID, Map<UUID, BettingTable>> dealerBettingTables = new HashMap<>();

    // Method to spawn a DealerVillager at a specific location
    public static Villager spawnDealer(JavaPlugin plugin, Location location, String name) {
        // Adjust the location to center on the block
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        // Spawn a new villager entity at the centered location
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);

        // Customize the villager's properties
        initializeVillager(villager, centeredLocation, name);

        return villager;
    }

    // Method to initialize the dealer villager
    private static void initializeVillager(Villager villager, Location location, String name) {
        villager.setAI(true); // Allow AI for natural rotation
        villager.setInvulnerable(true); // Make the villager invulnerable to all damage
        villager.setCustomName(name); // Set the custom name
        villager.setCustomNameVisible(true); // Make the custom name visible
        villager.setProfession(Villager.Profession.NONE); // Set profession if needed
        villager.setSilent(true); // Silence the villager

        // Generate a unique ID for this dealer villager
        UUID uniqueId = UUID.randomUUID();

        // Tag the villager as a DealerVillager using PersistentDataContainer
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, "Game Menu"); // Default to Game Menu

        // Initialize the correct inventory type based on the stored game type
        initializeInventory(villager, uniqueId, name);

        // Store the fixed location in the villager's metadata
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villager.isValid()) {
                    this.cancel(); // Stop the task if the villager is no longer valid
                    return;
                }
                Location currentLocation = villager.getLocation();
                if (currentLocation.distanceSquared(location) > 0.25) { // Check for significant deviation
                    villager.teleport(location.clone().setDirection(currentLocation.getDirection()));
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(DealerVillager.class), 0L, 20L); // Run every second (20 ticks)
    }

    // Public method to initialize inventory based on the game type stored in the persistent data
    public static void initializeInventory(Villager villager, UUID uniqueId, String name) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String gameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);

        if (gameType == null) {
            gameType = "Game Menu"; // Default if not set
            dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        }

        DealerInventory inventory;
        switch (gameType) {
            case "Blackjack":
                inventory = new BlackjackInventory(uniqueId);
                name = "Blackjack Dealer";
                break;
            case "Roulette":
                inventory = new RouletteInventory(uniqueId);
                name = "Roulette Dealer";
                break;
            default:
                inventory = new GameMenuInventory(uniqueId);
                name = "Game Menu";
                break;
        }

        DealerInventory.updateInventory(uniqueId, inventory);
        setName(villager, name);  // Update the name based on the game type
    }

    // Static method to check if a villager is a DealerVillager
    public static boolean isDealerVillager(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.has(DEALER_KEY, PersistentDataType.BYTE);
    }

    // Static method to get the unique ID of a DealerVillager
    public static UUID getUniqueId(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String uuidString = dataContainer.get(UNIQUE_ID_KEY, PersistentDataType.STRING);
        return uuidString != null ? UUID.fromString(uuidString) : null;
    }

    // Static method to get the name of a DealerVillager
    public static String getName(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.get(NAME_KEY, PersistentDataType.STRING);
    }

    // Static method to set the name of a DealerVillager
    public static void setName(Villager villager, String name) {
        villager.setCustomName(name);
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
    }

    // Static method to open the dealer's custom inventory
    public static void openDealerInventory(Villager villager, Player player) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    // Static method to switch the dealer's game
    public static void switchGame(Villager villager, String gameName) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        DealerInventory newInventory;
        String newName;

        // Properly initialize the new inventory based on game selection
        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId);
                newName = "Blackjack Dealer";
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId);
                newName = "Roulette Dealer";
                break;
            default:
                newInventory = new GameMenuInventory(dealerId);
                newName = "Game Menu";
                break;
        }
        
        DealerInventory.updateInventory(dealerId, newInventory);
        setName(villager, newName);

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
    }

    // Adjusted method to create or get a betting table
    public static BettingTable getOrCreateBettingTable(Villager dealer, Player player) {
        UUID dealerId = dealer.getUniqueId();
        UUID playerId = player.getUniqueId();
        
        // Initialize map for dealer if not present
        dealerBettingTables.putIfAbsent(dealerId, new HashMap<>());

        // Get the player's betting table, creating a new one if necessary
        Map<UUID, BettingTable> playerBettingTables = dealerBettingTables.get(dealerId);
        
        // Check if the player already has a betting table with this dealer
        if (!playerBettingTables.containsKey(playerId)) {
            // Create a new betting table if it does not exist
            BettingTable newBettingTable = new BettingTable(player, dealer);
            playerBettingTables.put(playerId, newBettingTable);
        }
        
        return playerBettingTables.get(playerId);
    }

    public static void removePlayerBettingTable(Villager dealer, Player player) {
        UUID dealerId = dealer.getUniqueId();
        UUID playerId = player.getUniqueId();
        Map<UUID, BettingTable> playerTables = dealerBettingTables.get(dealerId);
        if (playerTables != null) {
            playerTables.remove(playerId);
        }
    }

}
