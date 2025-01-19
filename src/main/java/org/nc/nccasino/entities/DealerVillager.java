package org.nc.nccasino.entities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Dice.DiceInventory;
import org.nc.nccasino.games.DragonClimb.DragonInventory;
import org.nc.nccasino.games.Mines.MinesInventory;
import org.nc.nccasino.games.RailRunner.RailInventory;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.helpers.DealerInventory;
import org.nc.nccasino.helpers.GameMenuInventory;

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
    private static final NamespacedKey ANIMATION_MESSAGE_KEY = new NamespacedKey(JavaPlugin.getProvidingPlugin(DealerVillager.class), "animation_message");
  
    public static Villager spawnDealer(JavaPlugin plugin, Location location, String name, String internalName) {
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);
    
        // Initialize the dealer villager with proper data
        initializeVillager(villager, centeredLocation, name, internalName);
    
        // Update the configuration with the default values for this dealer
        Nccasino nccasino = (Nccasino) plugin;
    
        // Ensure the correct order of config entries
        nccasino.getConfig().set("dealers." + internalName + ".game", "Menu");
        nccasino.getConfig().set("dealers." + internalName + ".timer", 0);
        nccasino.getConfig().set("dealers." + internalName + ".animation-message", "Menu"); // Set the animation message to default
        nccasino.saveConfig();  // Immediately save the configuration after setting the values
    
        return villager;
    }
    

    private static void initializeVillager(Villager villager, Location location, String name, String internalName) {
        villager.setAI(true);
        villager.setInvulnerable(true);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.setGravity(false);
        villager.setSilent(true);
        villager.setCollidable(false);

        UUID uniqueId = UUID.randomUUID();


        AttributeInstance movementSpeedAttribute = villager.getAttribute(Attribute.MOVEMENT_SPEED);
        movementSpeedAttribute.setBaseValue(0.0);

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, "Menu"); // Default game type
        dataContainer.set(INTERNAL_NAME_KEY, PersistentDataType.STRING, internalName);
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, "Menu"); // Default animation message is the game name

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class);
        initializeInventory(villager, uniqueId, name, plugin);

    }

    public static void initializeInventory(Villager villager, UUID uniqueId, String name, Nccasino plugin) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String gameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);

        if (gameType == null) {
            gameType = "Game Menu"; // Set to default Game Menu only if the game type is null
            dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        }
  // Set the default animation message to the game type if not already set
  String animationMessage = dataContainer.get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
  if (animationMessage == null) {
      dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, gameType);
  }
        DealerInventory inventory = null;
        switch (gameType) {
            case "Blackjack":
                inventory = new BlackjackInventory(uniqueId, plugin, internalName);
                name = "Blackjack Dealer";
                break;
            case "Roulette":
                inventory = new RouletteInventory(uniqueId, plugin, internalName);
                name = "Roulette Dealer";
                break;
            case "Mines":
                inventory = new MinesInventory(uniqueId, plugin);
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
                /*         
            case "Digital Baccarat":
                inventory = new DigitalBaccaratInventory(uniqueId, plugin);
                name = "Digital Baccarat Dealer";
                break;
            case "Higher or Lower":
                inventory = new HigherOrLowerInventory(uniqueId, plugin);
                name = "Higher or Lower Dealer";
                break;
                case "Keno":
                inventory = new KenoInventory(uniqueId, plugin);
                name = "Keno Dealer";
                break;
                case "Bandit Wheel":
                inventory = new BanditWheelInventory(uniqueId, plugin);
                name = "Bandit Wheel Dealer";
                break;
                case "Rail Runner":
                inventory = new RailInventory(uniqueId, plugin);
                name = "Rail Runner Dealer";
                break;
            
 */
            default:
                if (inventory == null) {
                    inventory = new GameMenuInventory(uniqueId);
                    name = "Game Menu";
                }
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

    public static String getAnimationMessage(Villager villager) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        return dataContainer.get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
    }

    public static void setAnimationMessage(Villager villager, String animationMessage) {
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, animationMessage);
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
        int defaultTimer = 0;
    
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    
        String currentGameType = dataContainer.get(GAME_TYPE_KEY, PersistentDataType.STRING);
        if (!gameName.equals(currentGameType)) {
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
                    newInventory = new MinesInventory(dealerId, plugin);
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
                    newInventory = new GameMenuInventory(dealerId);
                    newName = "Game Menu";
                    gameName = "Menu";
                    break;
            }
    
            // Update the config and ensure correct hierarchy
            plugin.getConfig().set("dealers." + internalName + ".game", gameName);
            plugin.getConfig().set("dealers." + internalName + ".timer", defaultTimer);
            plugin.getConfig().set("dealers." + internalName + ".animation-message", "NCCasino - "+gameName); 
            plugin.saveConfig();
    
            DealerInventory.updateInventory(dealerId, newInventory);
            setName(villager, newName);
            setAnimationMessage(villager, gameName); // Update the animation message to match the game type
    
            player.sendMessage(Component.text("Dealer '")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                    .append(Component.text("' has been set to ").color(NamedTextColor.GREEN))
                    .append(Component.text(gameName).color(NamedTextColor.YELLOW))
                    .append(Component.text(".").color(NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("The game is already set to " + gameName + ".")
                    .color(NamedTextColor.RED));
        }
    }
    public static void updateGameType(Villager villager, String gameName, int timer, String anmsg) {
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
                newInventory = new RouletteInventory(dealerId, plugin, internalName);
                newName = "Roulette Dealer";
                break;
            case "Mines":
                newInventory = new MinesInventory(dealerId, plugin);
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
                newInventory = new GameMenuInventory(dealerId);
                newName = "Game Menu";
                gameName = "Menu";
                break;
        }

        DealerInventory.updateInventory(dealerId, newInventory);
        setName(villager, newName);

        // Update the game type and animation message
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameName);
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, gameName); // Set animation message to game name by default

        plugin.getConfig().set("dealers." + internalName + ".game", gameName);
        plugin.getConfig().set("dealers." + internalName + ".timer", timer);
        plugin.getConfig().set("dealers." + internalName + ".animation-message", anmsg); // Set animation message to game name by default
        plugin.saveConfig();
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

        deleteAllPersistentData(dataContainer);
        villager.remove();
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

    private final Villager villager;

    public DealerVillager(Villager villager) {
        this.villager = villager;
        dealers.put(villager.getUniqueId(), this);
    }

    public Villager getVillager() {
        return villager;
    }
}
