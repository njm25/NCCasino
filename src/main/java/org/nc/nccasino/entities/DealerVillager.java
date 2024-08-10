package org.nc.nccasino.entities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.DealerInventory;
import org.nc.nccasino.games.GameMenuInventory;
import org.nc.nccasino.games.BlackjackInventory;
import org.nc.nccasino.games.RouletteInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DealerVillager {
 public static final Map<UUID, DealerVillager> dealers = new HashMap<>();
    private static final NamespacedKey DEALER_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_villager");
    private static final NamespacedKey UNIQUE_ID_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_unique_id");
    private static final NamespacedKey NAME_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_name");
    private static final NamespacedKey GAME_TYPE_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_game_type");
    private static final NamespacedKey INTERNAL_NAME_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "internal_name");

    public static Villager spawnDealer(JavaPlugin plugin, Location location, String name, String internalName) {
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);

        initializeVillager(villager, centeredLocation, name, internalName);

        return villager;
    }

    private static void initializeVillager(Villager villager, Location location, String name, String internalName) {
        villager.setAI(true);
        villager.setInvulnerable(true);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.setSilent(true);

        UUID uniqueId = UUID.randomUUID();

        AttributeInstance movementSpeedAttribute = villager.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        movementSpeedAttribute.setBaseValue(0.0);

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, "Menu"); // Default game type
        dataContainer.set(INTERNAL_NAME_KEY, PersistentDataType.STRING, internalName);

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
                if (currentLocation.distanceSquared(location) > 0.1) {
                    villager.teleport(location.clone().setDirection(currentLocation.getDirection()));
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(DealerVillager.class), 0L, 20L);
    }

    public static void initializeInventory(Villager villager, UUID uniqueId, String name, Nccasino plugin) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String gameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);

        if (gameType == null) {
            gameType = "Game Menu";
            dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        }

        DealerInventory inventory;
        switch (gameType) {
            case "Blackjack":
                inventory = new BlackjackInventory(uniqueId, plugin, internalName);
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

    public static String getInternalName(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    }

    public static void openDealerInventory(Villager villager, Player player) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    public static void switchGame(Villager villager, String gameName, Player player) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;
    
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        String newName;
    
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    
        // Determine the appropriate inventory and name based on the game type
        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                newName = "Blackjack Dealer";
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId, plugin);
                newName = "Roulette Dealer";
                break;
            default:
                newInventory = new GameMenuInventory(dealerId);
                newName = "Game Menu";
                gameName = "Menu"; // Ensure gameName is "Menu" if it doesn't match other cases
                break;
        }
    
        // Update the inventory and the villager's name
        DealerInventory.updateInventory(dealerId, newInventory);
        setName(villager, newName);
    
        // Update the villager's game type in the persistent data container
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
    
        // Update the configuration with the new game type
        plugin.getConfig().set("dealers." + internalName + ".game", gameName);
        plugin.saveConfig();  // Save the configuration to persist changes
    
        // Send styled message only to the player who switched the game
        player.sendMessage(Component.text("Dealer '")
                .color(NamedTextColor.GREEN)
                .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                .append(Component.text("' has been set to ").color(NamedTextColor.GREEN))
                .append(Component.text(gameName).color(NamedTextColor.YELLOW))
                .append(Component.text(".").color(NamedTextColor.GREEN)));
    }
    public static void updateGameType(Villager villager, String gameName) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;
    
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        String newName;
    
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    
        // Determine the appropriate inventory and name based on the game type
        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                newName = "Blackjack Dealer";
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId, plugin);
                newName = "Roulette Dealer";
                break;
            default:
                newInventory = new GameMenuInventory(dealerId);
                newName = "Game Menu";
                gameName = "Menu"; // Ensure gameName is "Menu" if it doesn't match other cases
                break;
        }
    
        // Update the inventory and the villager's name
        DealerInventory.updateInventory(dealerId, newInventory);
        setName(villager, newName);
    
        // Update the villager's game type in the persistent data container
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
    
        // Update the configuration with the new game type
        plugin.getConfig().set("dealers." + internalName + ".game", gameName);
        plugin.saveConfig();  // Save the configuration to persist changes
    }
    
    public static void registerDealer(UUID dealerId, DealerVillager dealer) {
        dealers.put(dealerId, dealer);
    }

    public static void removeDealerFromMap(UUID dealerId) {
        dealers.remove(dealerId);
    }
    private static void deleteAllPersistentData(PersistentDataContainer container) {
        for (NamespacedKey key : container.getKeys()) {
            container.remove(key);
        }
    }

    public static void removeDealer(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        UUID dealerId = getUniqueId(villager);

        if (dealerId == null) {
            return; // No dealer ID, nothing to clean up
        }

        // Get internal name
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);

        // Specifically remove and delete the dealer's managed inventory
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory != null) {
            dealerInventory.delete(); // Delete the inventory entirely
        }

        // Remove dealer's entry from the configuration
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        if (internalName != null) {
            plugin.getConfig().set("dealers." + internalName, null);
            plugin.saveConfig();
        }

        // Recursively delete all persistent data related to the dealer
        deleteAllPersistentData(dataContainer);

        // Broadcast a message indicating the dealer has been removed

        // Remove the villager entity from the game world
        villager.remove();
    }
    private final Villager villager;

    public DealerVillager(Villager villager) {
        this.villager = villager;
        dealers.put(villager.getUniqueId(), this);
    }

    public Villager getVillager() {
        return villager;
    }
}
