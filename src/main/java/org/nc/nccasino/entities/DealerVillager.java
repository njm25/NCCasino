package org.nc.nccasino.entities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Dice.DiceInventory;
import org.nc.nccasino.games.DragonClimb.DragonInventory;
import org.nc.nccasino.games.Mines.MinesInventory;
import org.nc.nccasino.games.RailRunner.RailInventory;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.helpers.AttributeHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DealerVillager {
    // Static map of DealerVillager references
    public static final Map<UUID, DealerVillager> dealers = new HashMap<>();

    private static final NamespacedKey DEALER_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_villager");
    private static final NamespacedKey UNIQUE_ID_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_unique_id");
    private static final NamespacedKey NAME_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_name");
    private static final NamespacedKey GAME_TYPE_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "dealer_game_type");
    private static final NamespacedKey INTERNAL_NAME_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "internal_name");
    private static final NamespacedKey ANIMATION_MESSAGE_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "animation_message");

    /**
     * Helper: Spawn a new Dealer Villager at a location.
     */
    public static Villager spawnDealer(JavaPlugin plugin, Location location, String name,
                                       String internalName, String gameType) {
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);

        initializeVillager(villager, centeredLocation, name, internalName, gameType);
        return villager;
    }

    private static void initializeVillager(Villager villager, Location location, String name,
                                           String internalName, String gameType) {

        villager.setAI(true);
        villager.setInvulnerable(true);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.setGravity(false);
        villager.setSilent(true);
        villager.setCollidable(false);

        // Unique ID for referencing this Dealer
        UUID uniqueId = UUID.randomUUID();

        AttributeInstance movementSpeedAttribute =
            villager.getAttribute(AttributeHelper.getAttributeSafely("MOVEMENT_SPEED"));
        movementSpeedAttribute.setBaseValue(0.0);

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        dataContainer.set(INTERNAL_NAME_KEY, PersistentDataType.STRING, internalName);
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, "NCCasino - " + gameType);

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        initializeInventory(villager, uniqueId, name, plugin);
    }

    public static void initializeInventory(Villager villager, UUID uniqueId, String name, Nccasino plugin) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String gameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
        int defaultTimer = 0;

        if (gameType == null) {
            gameType = "Game Menu";
            dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        }

        String animationMessage = dataContainer.get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
        if (animationMessage == null) {
            dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, gameType);
        }

        DealerInventory inventory = null;
        switch (gameType) {
            case "Blackjack":
                defaultTimer = 10;
                inventory = new BlackjackInventory(uniqueId, plugin, internalName);
                name = "Blackjack Dealer";
                break;
            case "Roulette":
                defaultTimer = 30;
                inventory = new RouletteInventory(uniqueId, plugin, internalName);
                name = "Roulette Dealer";
                break;
            case "Mines":
                inventory = new MinesInventory(uniqueId, plugin, internalName);
                name = "Mines Dealer";
                break;
            case "Dragon Dodger":
                inventory = new DragonInventory(uniqueId, plugin);
                name = "Dragon Dodger Dealer";
                break;
            case "Rail Runner":
                inventory = new RailInventory(uniqueId, plugin);
                name = "Rail Runner Dealer";
                break;
            case "Dice":
                inventory = new DiceInventory(uniqueId, plugin);
                name = "Dice Dealer";
                break;
            default:
                defaultTimer = 10;
                inventory = new BlackjackInventory(uniqueId, plugin, internalName);
                name = "Blackjack Dealer";
                break;
        }

        // Update config with default values
        plugin.getConfig().set("dealers." + internalName + ".display-name", name);
        plugin.getConfig().set("dealers." + internalName + ".game", gameType);
        plugin.getConfig().set("dealers." + internalName + ".timer", defaultTimer);
        plugin.getConfig().set("dealers." + internalName + ".animation-message", "NCCasino - " + gameType);
        plugin.getConfig().set("dealers." + internalName + ".currency.material", "EMERALD");
        plugin.getConfig().set("dealers." + internalName + ".currency.name", "Emerald");
        plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size1", 1);
        plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size2", 5);
        plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size3", 10);
        plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size4", 25);
        plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size5", 50);
        plugin.saveConfig();

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
        return (uuidString != null) ? UUID.fromString(uuidString) : null;
    }

    public static String getName(Villager villager) {
        return villager.getPersistentDataContainer().get(NAME_KEY, PersistentDataType.STRING);
    }

    public static void setName(Villager villager, String name) {
        villager.setCustomName(name);
        villager.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, name);
    }

    public static String getInternalName(Villager villager) {
        return villager.getPersistentDataContainer().get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    }

    public static String getAnimationMessage(Villager villager) {
        return villager.getPersistentDataContainer().get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
    }

    public static void setAnimationMessage(Villager villager, String animationMessage) {
        villager.getPersistentDataContainer().set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, animationMessage);
    }

    public static void openDealerInventory(Villager villager, Player player) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    public static void switchGame(Villager villager, String gameName, Player player, Boolean resetToDefault) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        String newName;
        int defaultTimer = 0;

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
// do chip sizes specificially before because bj initalizes them and theyll get reset otherwise
        if(resetToDefault){
            plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size1", 1);
            plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size2", 5);
            plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size3", 10);
            plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size4", 25);
            plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size5", 50);
        }

        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                newName = "Blackjack Dealer";
                defaultTimer = 10;
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId, plugin, internalName);
                newName = "Roulette Dealer";
                defaultTimer = 30;
                break;
            case "Mines":
                newInventory = new MinesInventory(dealerId, plugin, internalName);
                newName = "Mines Dealer";
                break;
            case "Dragon Climb":
                newInventory = new DragonInventory(dealerId, plugin);
                newName = "Dragon Climb Dealer";
                break;
            case "Rail Runner":
                newInventory = new RailInventory(dealerId, plugin);
                newName = "Rail Runner Dealer";
                break;
            case "Dice":
                newInventory = new DiceInventory(dealerId, plugin);
                newName = "Dice Dealer";
                break;
            default:
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                newName = "Blackjack Dealer";
                defaultTimer = 10;
                break;
        }
        plugin.getConfig().set("dealers." + internalName + ".game", gameName);
        // Update config
        if (resetToDefault){
            plugin.getConfig().set("dealers." + internalName + ".display-name", newName);
            plugin.getConfig().set("dealers." + internalName + ".timer", defaultTimer);
            plugin.getConfig().set("dealers." + internalName + ".animation-message", "NCCasino - " + gameName);
            plugin.getConfig().set("dealers." + internalName + ".currency.material", "EMERALD");
            plugin.getConfig().set("dealers." + internalName + ".currency.name", "Emerald");
            setAnimationMessage(villager, gameName);
            setName(villager, newName);
           
        }
        plugin.saveConfig();
        DealerInventory.updateInventory(dealerId, newInventory);

        player.sendMessage(ChatColor.GREEN + "Dealer '" + ChatColor.YELLOW + internalName
            + ChatColor.GREEN + "' has been set to " + ChatColor.YELLOW + gameName + ChatColor.GREEN + ".");
    }

    public static void updateGameType(Villager villager, String gameName, int timer, String anmsg, String newName, List<Integer> chipSizes, String currencyMaterial, String currencyName) {
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        DealerInventory newInventory;
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
        setName(villager, newName);

        // Update the game type & animation message
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, gameName);

        plugin.getConfig().set("dealers." + internalName + ".display-name", newName);
        plugin.getConfig().set("dealers." + internalName + ".game", gameName);
        plugin.getConfig().set("dealers." + internalName + ".timer", timer);
        plugin.getConfig().set("dealers." + internalName + ".animation-message", anmsg);
        plugin.getConfig().set("dealers." + internalName + ".currency.material", currencyMaterial);
        plugin.getConfig().set("dealers." + internalName + ".currency.name", currencyName);
                // Update chip sizes dynamically
        if (chipSizes != null && !chipSizes.isEmpty()) {
            for (int i = 0; i < chipSizes.size(); i++) {
                plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size" + (i + 1), chipSizes.get(i));
            }
        }
        plugin.saveConfig();
        switch (gameName) {
            case "Blackjack":
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                break;
            case "Roulette":
                newInventory = new RouletteInventory(dealerId, plugin, internalName);
                break;
            case "Mines":
                newInventory = new MinesInventory(dealerId, plugin, internalName);
                break;
            case "Dragon Climb":
                newInventory = new DragonInventory(dealerId, plugin);
                break;
            case "Rail Runner":
                newInventory = new RailInventory(dealerId, plugin);
                break;
            case "Dice":
                newInventory = new DiceInventory(dealerId, plugin);
                break;
            default:
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                break;
        }

        DealerInventory.updateInventory(dealerId, newInventory);
 
    }

    public static void removeDealer(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        UUID dealerId = getUniqueId(villager);
        if (dealerId == null) return;

        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);

        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory != null) {
            dealerInventory.delete();
        }

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        if (internalName != null) {
            plugin.getConfig().set("dealers." + internalName, null);
            plugin.saveConfig();
        }

        // Remove the persistent data & the entity itself
        deleteAllPersistentData(dataContainer);
        villager.remove();

        // Finally remove from DealerVillager map
        removeDealerFromMap(dealerId);
    }

    private static void deleteAllPersistentData(PersistentDataContainer container) {
        for (NamespacedKey key : container.getKeys()) {
            container.remove(key);
        }
    }

    public static void registerDealer(UUID dealerId, DealerVillager dealer) {
        dealers.put(dealerId, dealer);
    }

    public static void removeDealerFromMap(UUID dealerId) {
        dealers.remove(dealerId);
    }

    /**
     * Clears out all the DealerVillager references from the static map.
     * Useful in onDisable().
     */
    public static void cleanupAllDealers() {
        dealers.clear();
    }

    private final Villager villager;

    public DealerVillager(Villager villager) {
        this.villager = villager;
        dealers.put(villager.getUniqueId(), this);
    }

    public Villager getVillager() {
        return villager;
    }

    public static Villager getVillagerFromId(UUID dealerId) {
        DealerVillager dealer = dealers.get(dealerId);
        return (dealer != null) ? dealer.getVillager() : null;
    }
}
