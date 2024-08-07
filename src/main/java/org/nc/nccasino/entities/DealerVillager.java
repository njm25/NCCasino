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
import org.nc.nccasino.Nccasino;
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
    private static final NamespacedKey BETS_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "player_bets");

    private static final Map<UUID, Map<UUID, BettingTable>> dealerBettingTables = new HashMap<>();

    public static Villager spawnDealer(JavaPlugin plugin, Location location, String name) {
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);

        initializeVillager(villager, centeredLocation, name);

        return villager;
    }

    private static void initializeVillager(Villager villager, Location location, String name) {
        villager.setAI(true);
        villager.setInvulnerable(true);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.setSilent(true);

        UUID uniqueId = UUID.randomUUID();

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, "Game Menu");

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        initializeInventory(villager, uniqueId, name, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villager.isValid()) {
                    this.cancel();
                    return;
                }
                Location currentLocation = villager.getLocation();
                if (currentLocation.distanceSquared(location) > 0.25) {
                    villager.teleport(location.clone().setDirection(currentLocation.getDirection()));
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(DealerVillager.class), 0L, 20L);
    }

    public static void initializeInventory(Villager villager, UUID uniqueId, String name, Nccasino plugin) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String gameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);

        if (gameType == null) {
            gameType = "Game Menu";
            dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        }

        DealerInventory inventory;
        switch (gameType) {
            case "Blackjack":
                inventory = new BlackjackInventory(uniqueId);
                name = "Blackjack Dealer";
                break;
            case "Roulette":
                inventory = new RouletteInventory(uniqueId, plugin);
                name = "Roulette Dealer";
                break;
            default:
                inventory = new GameMenuInventory(uniqueId);
                name = "Game Menu";
                break;
        }

        DealerInventory.updateInventory(uniqueId, inventory);
        setName(villager, name);
    }

    public static boolean isDealerVillager(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.has(DEALER_KEY, PersistentDataType.BYTE);
    }

    public static UUID getUniqueId(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String uuidString = dataContainer.get(UNIQUE_ID_KEY, PersistentDataType.STRING);
        return uuidString != null ? UUID.fromString(uuidString) : null;
    }

    public static String getName(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.get(NAME_KEY, PersistentDataType.STRING);
    }

    public static void setName(Villager villager, String name) {
        villager.setCustomName(name);
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
    }

    public static void openDealerInventory(Villager villager, Player player) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    public static void switchGame(Villager villager, String gameName) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        String newName;

        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId);
                newName = "Blackjack Dealer";
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId, plugin);
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

    public static BettingTable createNewBettingTable(Villager dealer, Player player, Nccasino plugin) {
        UUID dealerId = dealer.getUniqueId();
        UUID playerId = player.getUniqueId();
        Map<Integer, Double> existingBets = retrievePlayerBets(dealer, player);
        // Retrieve the existing bets for the player from the RouletteInventory

        /* 
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        Map<Integer, Double> existingBets = new HashMap<>();
        if (dealerInventory instanceof RouletteInventory) {
            existingBets = ((RouletteInventory) dealerInventory).getPlayerBets(playerId);
        }*/

        // Create a new betting table with the existing bets
        
        BettingTable newBettingTable = new BettingTable(player, dealer, plugin, existingBets);
        dealerBettingTables.computeIfAbsent(dealerId, k -> new HashMap<>()).put(playerId, newBettingTable);

        return newBettingTable;
    }
    public static Map<Integer, Double> retrievePlayerBets(Villager dealer, Player player) {
        PersistentDataContainer container = dealer.getPersistentDataContainer();
        UUID playerId = player.getUniqueId();
        NamespacedKey key = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), playerId.toString());

        // Attempt to get the stored bets
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            PersistentDataContainer playerContainer = container.get(key, PersistentDataType.TAG_CONTAINER);
            Map<Integer, Double> bets = new HashMap<>();

            for (NamespacedKey betKey : playerContainer.getKeys()) {
                if (playerContainer.has(betKey, PersistentDataType.DOUBLE)) {
                    int betNumber = Integer.parseInt(betKey.getKey()); // Use betKey.getKey() to get the key as a String
                    double amount = playerContainer.get(betKey, PersistentDataType.DOUBLE);
                    bets.put(betNumber, amount);
                }
            }
            

            return bets;
        }

        return new HashMap<>();
    }

    public static void savePlayerBets(Villager dealer, Player player, Map<Integer, Double> bets) {
        PersistentDataContainer container = dealer.getPersistentDataContainer();
        UUID playerId = player.getUniqueId();
        NamespacedKey key = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), playerId.toString());

        // Create a new PDC for the player's bets
        PersistentDataContainer playerContainer = container.getAdapterContext().newPersistentDataContainer();

        // Save each bet in the container
        bets.forEach((number, amount) -> {
            playerContainer.set(new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), number.toString()), PersistentDataType.DOUBLE, amount);
        });

        // Store the player's bets in the main container
        container.set(key, PersistentDataType.TAG_CONTAINER, playerContainer);
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
