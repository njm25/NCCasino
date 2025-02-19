package org.nc.nccasino.entities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Mines.MinesInventory;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.games.TestGame.TestServer;
import org.nc.nccasino.helpers.AttributeHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Dealer {
    // Static map of Dealer references
    public static final Map<UUID, Dealer> dealers = new HashMap<>();
    private static final Map<UUID, BukkitTask> lookAtTasks = new HashMap<>();

    private static final NamespacedKey DEALER_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_villager");
    private static final NamespacedKey UNIQUE_ID_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_unique_id");
    private static final NamespacedKey NAME_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_name");
    private static final NamespacedKey GAME_TYPE_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_game_type");
    private static final NamespacedKey INTERNAL_NAME_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "internal_name");
    private static final NamespacedKey ANIMATION_MESSAGE_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "animation_message");


    /**
     * Helper: Spawn a new Dealer at a location.
     */
    public static Mob spawnDealer(JavaPlugin plugin, Location location, String name, String internalName, String gameType, EntityType type) {
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Mob mob = (Mob) centeredLocation.getWorld().spawnEntity(centeredLocation, type);
        initializeDealer(mob, centeredLocation, name, internalName, gameType);
        return mob;
    }

    private static void initializeDealer(Mob mob, Location location, String name, String internalName, String gameType) {
        if (mob instanceof org.bukkit.entity.LivingEntity livingEntity) {
            var equipment = livingEntity.getEquipment();
            if (equipment != null) { // Ensure the entity has equipment
                equipment.setItemInMainHand(null);
                equipment.setItemInOffHand(null);
                equipment.setHelmet(null);
                equipment.setChestplate(null);
                equipment.setLeggings(null);
                equipment.setBoots(null);
            }
        }
        if (mob.getVehicle() != null) {
        mob.getVehicle().removePassenger(mob);
        }
        if (!mob.getPassengers().isEmpty()) {
            for (Entity passenger : mob.getPassengers()) {
                passenger.remove(); 
            }
            mob.eject(); 
        }
        mob.setInvisible(false);                                    
        mob.setInvulnerable(true);
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
        mob.setGravity(false);
        mob.setSilent(true);
        mob.setCollidable(false);
        if (mob instanceof Villager){
            ((Villager) mob).setProfession(Villager.Profession.NONE);
            //mob.setAI(true);
        }
        if (mob instanceof Slime){
            ((Slime)mob).setSize(3);
        }
        if (mob instanceof MagmaCube){
            ((MagmaCube)mob).setSize(3);
        }
        if (mob instanceof Panda ){
            ((Panda)mob).setMainGene(Panda.Gene.NORMAL);
            ((Panda)mob).setHiddenGene(Panda.Gene.NORMAL);

        }
        if (mob instanceof Shulker){
            mob.setAI(true);
        }
        else{
            mob.setAI(false);
        }
        startLookingAtPlayers(mob);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
     

        

        // Unique ID for referencing this Dealer
        UUID uniqueId = UUID.randomUUID();

        AttributeInstance movementSpeedAttribute =
        mob.getAttribute(AttributeHelper.getAttributeSafely("MOVEMENT_SPEED"));
        movementSpeedAttribute.setBaseValue(0.0);

        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        dataContainer.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(UNIQUE_ID_KEY, PersistentDataType.STRING, uniqueId.toString());
        dataContainer.set(NAME_KEY, PersistentDataType.STRING, name);
        dataContainer.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        dataContainer.set(INTERNAL_NAME_KEY, PersistentDataType.STRING, internalName);
        dataContainer.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, "NCCasino - " + gameType);

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        initializeInventory(mob, uniqueId, name, plugin);
    }
    
    public static void startLookingAtPlayers(Mob mob) {
        UUID mobId = mob.getUniqueId();

        // Cancel any existing task for this mob
        if (lookAtTasks.containsKey(mobId)) {
            lookAtTasks.get(mobId).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            private Player currentTarget = null;
            private float bodyYaw = mob.getLocation().getYaw();
            private static final float MIN_TURN_SPEED = 0.5f;
            private static final float MAX_TURN_SPEED = 4.0f;
            private static final float SMOOTH_FACTOR = 0.2f;
            private static final double SWITCH_THRESHOLD = 0.5;

            @Override
            public void run() {
                if (mob.isDead() || !mob.isValid()) {
                    cancel();
                    lookAtTasks.remove(mobId); // Cleanup task reference
                    return;
                }

                Player nearest = getNearestPlayer(mob);

                if (nearest != null) {
                    if (currentTarget == null || 
                        (nearest.getWorld().equals(mob.getWorld()) && 
                         currentTarget.getWorld().equals(mob.getWorld()) && 
                         nearest.getLocation().distance(mob.getLocation()) < 
                         currentTarget.getLocation().distance(mob.getLocation()) - SWITCH_THRESHOLD)) {
                        currentTarget = nearest;
                    }
                }

                if (currentTarget != null) {
                    Location mobLoc = mob.getLocation();
                    Location targetLoc = currentTarget.getLocation();

                    double dx = targetLoc.getX() - mobLoc.getX();
                    double dz = targetLoc.getZ() - mobLoc.getZ();

                    float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                    mobLoc.setYaw(targetYaw);
                    mob.teleport(mobLoc);

                    float angleDiff = ((targetYaw - bodyYaw + 540) % 360) - 180;
                    float turnSpeed = Math.max(MIN_TURN_SPEED, Math.abs(angleDiff) * SMOOTH_FACTOR);
                    turnSpeed = Math.min(turnSpeed, MAX_TURN_SPEED);

                    bodyYaw = lerpAngle(bodyYaw, targetYaw, turnSpeed);
                    setBodyYaw(mob, bodyYaw);
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(Dealer.class), 0L, 1L);

        lookAtTasks.put(mobId, task);
    }
    
    /**
     * Linearly interpolates between angles for smooth motion.
     */
    private static float lerpAngle(float current, float target, float speed) {
        float diff = ((target - current + 540) % 360) - 180; // Ensures shortest rotation path
        return current + Math.min(Math.max(diff, -speed), speed); // Move by speed amount
    }
    
    /**
     * Adjusts the body yaw without affecting head movement.
     */
    private static void setBodyYaw(Mob mob, float yaw) {
        Location mobLoc = mob.getLocation();
        mobLoc.setYaw(yaw);
        mob.teleport(mobLoc);
    }
    
    
    
    /**
     * Get the nearest player within range.
     */
    private static Player getNearestPlayer(Mob mob) {
        return mob.getWorld().getNearbyEntities(mob.getLocation(), 10, 10, 10)
            .stream()
            .filter(e -> e instanceof Player)
            .map(e -> (Player) e)
            .min((p1, p2) -> Double.compare(p1.getLocation().distance(mob.getLocation()), 
                                            p2.getLocation().distance(mob.getLocation())))
            .orElse(null);
    }
    
    
    

    public static void initializeInventory(Mob mob, UUID uniqueId, String name, Nccasino plugin) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
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
            case "Test Game":
                inventory = new TestServer(uniqueId, plugin, internalName);
                name = "Test Game Dealer";
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
        setName(mob, name);
    }

    public static boolean isDealer(Mob mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        return dataContainer.has(DEALER_KEY, PersistentDataType.BYTE);
    }

    public static UUID getUniqueId(Mob mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        String uuidString = dataContainer.get(UNIQUE_ID_KEY, PersistentDataType.STRING);
        return (uuidString != null) ? UUID.fromString(uuidString) : null;
    }

    public static String getName(Mob mob) {
        return mob.getPersistentDataContainer().get(NAME_KEY, PersistentDataType.STRING);
    }

    public static void setName(Mob mob, String name) {
        mob.setCustomName(name);
        mob.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, name);
    }

    public static String getInternalName(Mob mob) {
        return mob.getPersistentDataContainer().get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    }

    public static String getAnimationMessage(Mob mob) {
        return mob.getPersistentDataContainer().get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
    }

    public static void setAnimationMessage(Mob mob, String animationMessage) {
        mob.getPersistentDataContainer().set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, animationMessage);
    }

    public static void openDealerInventory(Mob mob, Player player) {
        UUID dealerId = getUniqueId(mob);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    public static void switchGame(Mob mob, String gameName, Player player, Boolean resetToDefault) {
        UUID dealerId = getUniqueId(mob);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        DealerInventory newInventory;
        String newName;
        int defaultTimer = 30;

        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
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
            case "Test Game":
                newInventory = new TestServer(dealerId, plugin, internalName);
                newName = "Test Game Dealer";
                defaultTimer = 10;
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
            setAnimationMessage(mob, gameName);
            setName(mob, newName);
           
        }
        plugin.saveConfig();
        DealerInventory.updateInventory(dealerId, newInventory);
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(ChatColor.GREEN + "Dealer '" + ChatColor.YELLOW + internalName+ ChatColor.GREEN + "' has been set to " + ChatColor.YELLOW + gameName + ChatColor.GREEN + ".");
                break;}
            case VERBOSE:{
                player.sendMessage(ChatColor.GREEN + "Dealer '" + ChatColor.YELLOW + internalName+ ChatColor.GREEN + "' has been set to " + ChatColor.YELLOW + gameName + ChatColor.GREEN + ".");
                break;}
            case NONE:{
                break;
            }
        }
    }

    public static void updateGameType(Mob mob, String gameName, int timer, String anmsg, String newName, List<Integer> chipSizes, String currencyMaterial, String currencyName) {
        UUID dealerId = getUniqueId(mob);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        DealerInventory newInventory;
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
        setName(mob, newName);

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
            case "Test Game":
                newInventory = new TestServer(dealerId, plugin, internalName);
                break;
            default:
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                break;
        }

        DealerInventory.updateInventory(dealerId, newInventory);
 
    }

    public static Boolean removeDealer(Mob mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        UUID dealerId = getUniqueId(mob);
        if (dealerId == null) return false;

        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);

    
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory != null) {
            //System.out.println("yah"+dealerInventory);
            if( DealerInventory.inventories.get(dealerId)!=null){
            //System.out.println("yahasdas"+DealerInventory.inventories.get(dealerId)); 
                DealerInventory.inventories.remove(dealerId);
            }
        
                dealerInventory.delete();
        

        }
     
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        if (internalName != null) {
            plugin.getConfig().set("dealers." + internalName, null);
            plugin.saveConfig();
        }

    
        // Remove the persistent data & the entity itself
        deleteAllPersistentData(dataContainer);
        mob.remove();

        // Finally remove from Dealer map
        removeDealerFromMap(dealerId);
        return true;
    }

    private static void deleteAllPersistentData(PersistentDataContainer container) {
        for (NamespacedKey key : container.getKeys()) {
            container.remove(key);
        }
    }

    public static void registerDealer(UUID dealerId, Dealer dealer) {
        dealers.put(dealerId, dealer);
    }

    public static void removeDealerFromMap(UUID dealerId) {
        dealers.remove(dealerId);
    }

    /**
     * Clears out all the Dealer references from the static map.
     * Useful in onDisable().
     */
    public static void cleanupAllDealers() {
        dealers.clear();
    }

    private final Mob mob;

    public Dealer(Mob mob) {
        this.mob = mob;
        dealers.put(mob.getUniqueId(), this);
    }

    public Mob getMob() {
        return mob;
    }

    public static Mob getMobFromId(UUID dealerId) {
        Dealer dealer = dealers.get(dealerId);
        return (dealer != null) ? dealer.getMob() : null;
    }
}
