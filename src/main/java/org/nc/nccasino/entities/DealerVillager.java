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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.DealerInventory;
import org.nc.nccasino.games.GameMenuInventory;
import org.nc.nccasino.games.BlackjackInventory;
import org.nc.nccasino.games.RouletteInventory;

import java.util.UUID;

public class DealerVillager {

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
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, "Game Menu");
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

    public static void switchGame(Villager villager, String gameName) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        String newName;

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
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
                break;
        }

        DealerInventory.updateInventory(dealerId, newInventory);
        setName(villager, newName);

        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
    }
}
