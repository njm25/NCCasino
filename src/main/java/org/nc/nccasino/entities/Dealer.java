package org.nc.nccasino.entities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.games.Baccarat.BaccaratServer;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.CoinFlip.CoinFlipServer;
import org.nc.nccasino.games.RockPaperScissors.RockPaperScissorsServer;
import org.nc.nccasino.games.DragonDescent.DragonServer;
import org.nc.nccasino.games.Mines.MinesInventory;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.games.Slots.SlotsInventory;
import org.nc.nccasino.games.TestGame.TestServer;
import org.nc.nccasino.helpers.AttributeHelper;
import org.nc.nccasino.integrations.CitizensDealerSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Dealer {
    /**
     * Which system owns the entity a dealer is attached to.
     *
     * <p>{@link #MOB} dealers are spawned and owned by NCCasino: we control
     * their AI, invulnerability, name and jockey stack, and deleting the
     * dealer deletes the entity.
     *
     * <p>{@link #CITIZENS} dealers are attached to an NPC that an admin
     * created and owns through the Citizens plugin. NCCasino only borrows the
     * entity to host a game on it, so cosmetic state (name, skin, equipment,
     * look-at behaviour, position) is deliberately left to Citizens, and
     * removing the dealer only detaches the game -- it never deletes the NPC.
     */
    public enum Backend { MOB, CITIZENS }

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
    private static final NamespacedKey BACKEND_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_backend");
    private static final NamespacedKey CITIZENS_NPC_ID_KEY =
        new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_citizens_npc_id");


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
        mob.setCustomNameVisible(false);
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
    
        if (lookAtTasks.containsKey(mobId)) {
            lookAtTasks.get(mobId).cancel();
        }
    
        BukkitTask task = new BukkitRunnable() {
            private final Random random = new Random();
            private Entity currentTarget = null;
            private float bodyYaw = mob.getLocation().getYaw();
            private static final float MIN_TURN_SPEED = 0.5f;
            private static final float MAX_TURN_SPEED = 4.0f;
            private static final float SMOOTH_FACTOR = 0.2f;
            private int targetLockTime = 0;
            private static final int TARGET_LOCK_DURATION = 175; // Keep target for 5 seconds
            private boolean idleMode = false;
            private float idleYaw = bodyYaw;
    
            @Override
            public void run() {
                if (mob.isDead() || !mob.isValid()) {
                    cancel();
                    lookAtTasks.remove(mobId);
                    return;
                }
    
                // Decide whether to enter idle mode (10% chance)
                if (targetLockTime <= 0) {
                    if (random.nextDouble() < 0.05) { // 10% chance for idle mode
                        idleMode = true;
                        idleYaw = random.nextFloat() * 360.0f; // Pick a random yaw
                    } else {
                        idleMode = false;
                        // 30% chance to look at another dealer/mob instead of a player
                        currentTarget = (random.nextDouble() < 0.1) ? getNearbyDealerOrMob(mob) : getNearestPlayer(mob);
                    }
                    targetLockTime = TARGET_LOCK_DURATION;
                }
                targetLockTime--;
    
                Location mobLoc = mob.getLocation();
    
                if (idleMode) {
                    // Smoothly turn towards random idle yaw
                    float angleDiff = ((idleYaw - bodyYaw + 540) % 360) - 180;
                    float turnSpeed = Math.max(MIN_TURN_SPEED, Math.abs(angleDiff) * SMOOTH_FACTOR);
                    turnSpeed = Math.min(turnSpeed, MAX_TURN_SPEED);
                    bodyYaw = lerpAngle(bodyYaw, idleYaw, turnSpeed);
                } else if (currentTarget != null) {
                    // Normal tracking logic
                    Location targetLoc = currentTarget.getLocation();
                    double dx = targetLoc.getX() - mobLoc.getX();
                    double dz = targetLoc.getZ() - mobLoc.getZ();
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    
                    float angleDiff = ((targetYaw - bodyYaw + 540) % 360) - 180;
                    float turnSpeed = Math.max(MIN_TURN_SPEED, Math.abs(angleDiff) * SMOOTH_FACTOR);
                    turnSpeed = Math.min(turnSpeed, MAX_TURN_SPEED);
                    bodyYaw = lerpAngle(bodyYaw, targetYaw, turnSpeed);
                }
    
                setBodyYaw(mob, bodyYaw);
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(Dealer.class), 0L, 1L);
    
        lookAtTasks.put(mobId, task);
    }
    
    

    private static Entity getNearbyDealerOrMob(Mob mob) {
        double maxDistance = 20.0;
    
        return mob.getWorld().getNearbyEntities(mob.getLocation(), maxDistance, maxDistance, maxDistance)
            .stream()
            .filter(e -> e instanceof Mob && e != mob) // Ignore self
            .min((e1, e2) -> Double.compare(e1.getLocation().distance(mob.getLocation()), 
                                            e2.getLocation().distance(mob.getLocation())))
            .orElse(null);
    }
    
    
    private static float lerpAngle(float current, float target, float speed) {
        float diff = ((target - current + 540) % 360) - 180; // Ensures shortest rotation path
        return current + Math.min(Math.max(diff, -speed), speed); // Move by speed amount
    }
    
    private static void setBodyYaw(Mob mob, float yaw) {
        Location mobLoc = mob.getLocation();
        mobLoc.setYaw(yaw);
        mob.teleport(mobLoc);
    }
    
    private static Player getNearestPlayer(Mob mob) {
        double maxDistance = 20.0;
    
        return mob.getWorld().getNearbyEntities(mob.getLocation(), maxDistance, maxDistance, maxDistance)
            .stream()
            .filter(e -> e instanceof Player)
            .map(e -> (Player) e)
            .filter(player -> player.getLocation().distance(mob.getLocation()) <= maxDistance) // Ensure within range
            .min((p1, p2) -> Double.compare(p1.getLocation().distance(mob.getLocation()), 
                                            p2.getLocation().distance(mob.getLocation())))
            .orElse(null);
    }
    
    public static void initializeInventory(LivingEntity mob, UUID uniqueId, String name, Nccasino plugin) {
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
            case "Baccarat":
                inventory = new BaccaratServer(uniqueId, plugin, internalName);
                name = "Baccarat Dealer";
                defaultTimer = 10;
                break;
            case "Test Game":
                inventory = new TestServer(uniqueId, plugin, internalName);
                name = "Test Game Dealer";
                break;
            case "Coin Flip":
                inventory = new CoinFlipServer(uniqueId, plugin, internalName);
                name = "Coin Flip Dealer";
                defaultTimer = 3;
                break;
            case "Rock Paper Scissors":
                inventory = new RockPaperScissorsServer(uniqueId, plugin, internalName);
                name = "Rock Paper Scissors Dealer";
                defaultTimer = 15;
                break;
            case "Dragon Descent":
                inventory = new DragonServer(uniqueId, plugin, internalName);
                name = "Dragon Descent Dealer";
                break;
            case "Slots":
                inventory = new SlotsInventory(uniqueId, plugin, internalName);
                name = "Slots Dealer";
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

    public static boolean isDealer(LivingEntity mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        return dataContainer.has(DEALER_KEY, PersistentDataType.BYTE);
    }

    public static UUID getUniqueId(LivingEntity mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        String uuidString = dataContainer.get(UNIQUE_ID_KEY, PersistentDataType.STRING);
        return (uuidString != null) ? UUID.fromString(uuidString) : null;
    }

    public static String getName(LivingEntity mob) {
        return mob.getPersistentDataContainer().get(NAME_KEY, PersistentDataType.STRING);
    }

    /**
     * Records the dealer's display name.
     *
     * <p>For a Citizens-backed dealer the entity's custom name is left alone:
     * the NPC's name belongs to the admin who created it, and on a player-type
     * NPC the name is also what Citizens resolves the default skin from, so
     * overwriting it here would visibly change an NPC the admin had already
     * styled. The name is still stored in our own container and config so
     * menus and messages read the same as they do for mob dealers.
     */
    public static void setName(LivingEntity mob, String name) {
        if (getBackend(mob) != Backend.CITIZENS) {
            mob.setCustomName(name);
        }
        mob.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, name);
    }

    public static String getInternalName(LivingEntity mob) {
        return mob.getPersistentDataContainer().get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
    }

    public static String getAnimationMessage(LivingEntity mob) {
        return mob.getPersistentDataContainer().get(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING);
    }

    public static void setAnimationMessage(LivingEntity mob, String animationMessage) {
        mob.getPersistentDataContainer().set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, animationMessage);
    }

    /**
     * Which system owns this dealer's entity. Untagged entities -- every
     * dealer that existed before Citizens support was added -- read back as
     * {@link Backend#MOB}, so existing worlds keep working unchanged.
     */
    public static Backend getBackend(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(BACKEND_KEY, PersistentDataType.STRING);
        if (raw == null) return Backend.MOB;
        try {
            return Backend.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Backend.MOB;
        }
    }

    /** The UUID of the Citizens NPC hosting this dealer, or null for mob dealers. */
    public static UUID getCitizensNpcId(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(CITIZENS_NPC_ID_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Marks {@code entity} as the live body of a Citizens-backed dealer.
     *
     * <p>Citizens re-creates the Bukkit entity every time an NPC respawns, so
     * these tags are written fresh on each spawn from the values Citizens
     * persists on the NPC itself. Everything here is deliberately limited to
     * our own persistent-data keys: no AI, invulnerability, gravity, name or
     * equipment changes, because that state belongs to Citizens.
     */
    public static void tagCitizensDealer(LivingEntity entity, UUID dealerId, String internalName,
                                         String displayName, String gameType, UUID citizensNpcId) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.set(DEALER_KEY, PersistentDataType.BYTE, (byte) 1);
        container.set(UNIQUE_ID_KEY, PersistentDataType.STRING, dealerId.toString());
        container.set(INTERNAL_NAME_KEY, PersistentDataType.STRING, internalName);
        container.set(NAME_KEY, PersistentDataType.STRING, displayName);
        container.set(GAME_TYPE_KEY, PersistentDataType.STRING, gameType);
        container.set(ANIMATION_MESSAGE_KEY, PersistentDataType.STRING, "NCCasino - " + gameType);
        container.set(BACKEND_KEY, PersistentDataType.STRING, Backend.CITIZENS.name());
        container.set(CITIZENS_NPC_ID_KEY, PersistentDataType.STRING, citizensNpcId.toString());
    }

    /**
     * Strips every NCCasino tag from a Citizens NPC's entity, leaving the NPC
     * itself untouched and fully usable for whatever else the admin wants.
     */
    public static void clearCitizensTags(LivingEntity entity) {
        deleteAllPersistentData(entity.getPersistentDataContainer());
    }

    public static void openDealerInventory(LivingEntity mob, Player player) {
        UUID dealerId = getUniqueId(mob);
        if (dealerId != null) {
            DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
            player.openInventory(dealerInventory.getInventory());
        }
    }

    public static void switchGame(LivingEntity mob, String gameName, Player player, Boolean resetToDefault) {
        UUID dealerId = getUniqueId(mob);
        if (dealerId == null) return;

        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        
        AdminMenu.clearAllEditModes(mob);
        plugin.deleteAssociatedInventories(mob);
        DealerInventory newInventory;
        String newName;
        int defaultTimer = 30;

        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        String internalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
// do chip sizes specifically before because bj initalizes them and theyll get reset otherwise
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
            case "Baccarat":
                newInventory = new BaccaratServer(dealerId, plugin, internalName);
                newName = "Baccarat Dealer";
                defaultTimer = 10;
                break;
            case "Test Game":
                newInventory = new TestServer(dealerId, plugin, internalName);
                newName = "Test Game Dealer";
                break;
            case "Coin Flip":
                newInventory = new CoinFlipServer(dealerId, plugin, internalName);
                newName = "Coin Flip Dealer";
                defaultTimer = 3;
                break;
            case "Rock Paper Scissors":
                newInventory = new RockPaperScissorsServer(dealerId, plugin, internalName);
                newName = "Rock Paper Scissors Dealer";
                defaultTimer = 15;
                break;
            case "Dragon Descent":
                newInventory = new DragonServer(dealerId, plugin, internalName);
                newName = "Dragon Descent Dealer";
                break;
            case "Slots":
                newInventory = new SlotsInventory(dealerId, plugin, internalName);
                newName = "Slots Dealer";
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

            if(plugin.getConfig().contains("dealers." + internalName + ".stand-on-17")){
                plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            }
            if(plugin.getConfig().contains("dealers." + internalName + ".number-of-decks")){
                
                if (gameName.equals("Baccarat")){
                    plugin.getConfig().set("dealers." + internalName + ".number-of-decks", 8);
                }
                else if (gameName.equals("Blackjack")) {
                    plugin.getConfig().set("dealers." + internalName + ".number-of-decks", 6);
                }

            }
            if(plugin.getConfig().contains("dealers." + internalName + ".default-mines")){
                plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
            }
            
            // Set default values for Dragon Descent if that's the game type
            if (gameName.equals("Dragon Descent")) {
                // Set default values for Dragon Descent settings
                plugin.getConfig().set("dealers." + internalName + ".default-columns", 7);
                plugin.getConfig().set("dealers." + internalName + ".default-vines", 5);
                plugin.getConfig().set("dealers." + internalName + ".default-floors", 4);
            }

            setAnimationMessage(mob, gameName);
            setName(mob, newName);

            // Jockey stacks are a mob-dealer feature only -- a Citizens NPC
            // has no NCCasino-managed stack to rename.
            if (mob instanceof Mob mobEntity) {
                // Update names of all mobs in the stack
                JockeyManager jockeyManager = new JockeyManager(mobEntity);
                for (JockeyNode jockey : jockeyManager.getJockeys()) {
                    if (jockey.getPosition() > 0) { // Skip dealer (position 0)
                        jockey.setCustomName(newName);
                    }
                }
            }
        }
        plugin.saveConfig();
        DealerInventory.updateInventory(dealerId, newInventory);
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(plugin.getLocalization().text(
                    player,
                    "dealer.game-set",
                    "dealer",
                    internalName,
                    "game",
                    localizedGameType(plugin, player, gameName)
                ));
                break;}
            case VERBOSE:{
                player.sendMessage(plugin.getLocalization().text(
                    player,
                    "dealer.game-set",
                    "dealer",
                    internalName,
                    "game",
                    localizedGameType(plugin, player, gameName)
                ));
                break;}
            case NONE:{
                break;
            }
        }
    }

    private static String localizedGameType(Nccasino plugin, Player player, String gameType) {
        return switch (gameType) {
            case "Blackjack" -> plugin.getLocalization().text(player, "game-options.blackjack");
            case "Roulette" -> plugin.getLocalization().text(player, "game-options.roulette");
            case "Mines" -> plugin.getLocalization().text(player, "game-options.mines");
            case "Baccarat" -> plugin.getLocalization().text(player, "game-options.baccarat");
            case "Coin Flip" -> plugin.getLocalization().text(player, "game-options.coin-flip");
            case "Rock Paper Scissors" -> plugin.getLocalization().text(player, "game-options.rock-paper-scissors");
            case "Dragon Descent" -> plugin.getLocalization().text(player, "game-options.dragon-descent");
            case "Slots" -> plugin.getLocalization().text(player, "game-options.slots");
            case "Test Game" -> plugin.getLocalization().text(player, "game-options.test-game");
            default -> gameType;
        };
    }

    public static void updateGameType(LivingEntity mob, String gameName, int timer, String anmsg, String newName, List<Integer> chipSizes, String currencyMaterial, String currencyName) {
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
            case "Baccarat":
                newInventory = new BaccaratServer(dealerId, plugin, internalName);
                break;         
            case "Test Game":
                newInventory = new TestServer(dealerId, plugin, internalName);
                break;            
            case "Coin Flip":
                newInventory = new CoinFlipServer(dealerId, plugin, internalName);
                break;
            case "Rock Paper Scissors":
                newInventory = new RockPaperScissorsServer(dealerId, plugin, internalName);
                break;
            case "Dragon Descent":
                newInventory = new DragonServer(dealerId, plugin, internalName);
                break;
            case "Slots":
                newInventory = new SlotsInventory(dealerId, plugin, internalName);
                break;
            default:
                newInventory = new BlackjackInventory(dealerId, plugin, internalName);
                break;
        }

        DealerInventory.updateInventory(dealerId, newInventory);
 
    }

    public static Boolean removeDealer(LivingEntity mob) {
        PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
        UUID dealerId = getUniqueId(mob);
        if (dealerId == null) return false;

        // Read before the container is wiped below.
        Backend backend = getBackend(mob);
        UUID citizensNpcId = getCitizensNpcId(mob);

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

    
        // Remove the persistent data, then dispose of the body.
        deleteAllPersistentData(dataContainer);
        if (backend == Backend.CITIZENS) {
            // The NPC belongs to the admin, not to us. Deleting the dealer
            // only detaches the game -- Citizens keeps the NPC, its skin and
            // any other traits it carries. Clearing the NPC-side bookkeeping
            // also stops the next respawn from re-tagging it as a dealer.
            if (citizensNpcId != null) {
                CitizensDealerSupport.releaseNpc(citizensNpcId);
            }
        } else {
            mob.remove();
        }

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

    private final LivingEntity mob;

    public Dealer(LivingEntity mob) {
        this.mob = mob;
        dealers.put(mob.getUniqueId(), this);
    }

    public LivingEntity getMob() {
        return mob;
    }

    public static LivingEntity getMobFromId(UUID dealerId) {
        Dealer dealer = dealers.get(dealerId);
        return (dealer != null) ? dealer.getMob() : null;
    }

    /**
     * Finds the live entity of the dealer configured under {@code internalName},
     * scanning loaded entities in every world.
     *
     * <p>Used by the Citizens bind flow, which knows which configured dealer
     * the admin is re-binding but not where its current body is.
     */
    public static LivingEntity findDealerByInternalName(String internalName) {
        if (internalName == null) return null;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isDealer(entity) && internalName.equals(getInternalName(entity))) {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * Finds a dealer by ID using both radius search and stack traversal.
     * This is the preferred method for finding dealers as it handles both
     * direct lookup and complex stack scenarios.
     * 
     * @param dealerId The UUID of the dealer to find
     * @param location The location to search from (for radius search)
     * @param radius The radius to search within (defaults to 20 if not specified)
     * @return The found dealer entity, or null if not found
     */
    public static LivingEntity findDealer(UUID dealerId, Location location, int radius) {
        // First try direct lookup
        LivingEntity dealer = getMobFromId(dealerId);
        if (dealer != null && dealer.isValid() && !dealer.isDead()) {
            return dealer;
        }

        // Then try radius search with stack traversal
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            // Checked before the Mob narrowing below: a Citizens player-type
            // NPC is a LivingEntity but never a Mob, so a Mob-only scan would
            // walk straight past it.
            if (entity instanceof LivingEntity living
                && Dealer.isDealer(living)
                && dealerId.equals(Dealer.getUniqueId(living))) {
                return living;
            }

            // Jockey stacks below this point are mob-only by construction.
            if (!(entity instanceof Mob mob)) continue;

            // Check passengers
            for (Entity passenger : mob.getPassengers()) {
                if (passenger instanceof Mob passengerMob && 
                    Dealer.isDealer(passengerMob) && 
                    Dealer.getUniqueId(passengerMob).equals(dealerId)) {
                    return passengerMob;
                }
            }

            // Check vehicle chain
            Entity vehicle = mob.getVehicle();
            while (vehicle != null) {
                if (vehicle instanceof Mob vehicleMob && 
                    Dealer.isDealer(vehicleMob) && 
                    Dealer.getUniqueId(vehicleMob).equals(dealerId)) {
                    return vehicleMob;
                }
                vehicle = vehicle.getVehicle();
            }
        }

        return null;
    }

    /**
     * Overloaded version of findDealer that uses default radius of 20
     */
    public static LivingEntity findDealer(UUID dealerId, Location location) {
        return findDealer(dealerId, location, 20);
    }
}
