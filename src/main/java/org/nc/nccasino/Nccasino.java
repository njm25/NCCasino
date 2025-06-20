package org.nc.nccasino;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.commands.CommandExecution;
import org.nc.nccasino.commands.CommandTabCompleter;
import org.nc.nccasino.components.AnimationMessage;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Mines.MinesTable;
import org.nc.nccasino.games.Roulette.BettingTable;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.Metrics;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.listeners.DealerDeathHandler;
import org.nc.nccasino.listeners.DealerEventListener;
import org.nc.nccasino.listeners.DealerInitializeListener;
import org.nc.nccasino.listeners.DealerInteractListener;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;
import org.bukkit.Chunk;
import org.bukkit.entity.EntityType;

public final class Nccasino extends JavaPlugin implements Listener {
    private final Set<String> currentlyDeletingChunks = new HashSet<>();
    private Map<UUID, Preferences> playerPreferences = new HashMap<>();
    private File preferencesFile;
    private FileConfiguration preferencesConfig;
    private NamespacedKey INTERNAL_NAME_KEY; // Declare it here

    private Material currency;    // Material used for betting currency
    private String currencyName;  // Display name for the currency

    /**
     * This local map was used in your code. If you still need it,
     * ensure you also clean it up. If not needed, consider removing.
     */
    public Map<UUID, DealerInventory> inventories = new HashMap<>();

    @Override
    public void onDisable() {
        savePreferences();
    }

    @Override
    public void onEnable() {
        INTERNAL_NAME_KEY = new NamespacedKey(this, "internal_name");
        checkForUpdates();
        saveDefaultConfig();
        loadPreferences();

        // Load currency from config
        loadCurrencyFromConfig();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new DealerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DealerDeathHandler(this), this);
        getServer().getPluginManager().registerEvents(new DealerEventListener(), this);
        getServer().getPluginManager().registerEvents(new DealerInitializeListener(this), this); // Register the chunk listener


        // Register the command executor
        if (this.getCommand("ncc") != null) {
            this.getCommand("ncc").setExecutor(new CommandExecution(this));
            this.getCommand("ncc").setTabCompleter(new CommandTabCompleter(this));
        }

        // Load any pre-existing dealer mob from config
        initializeDealersIfLoaded();

        int pluginId = 24579;
        //bStats support
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this, pluginId);
        

        getLogger().info("NCCasino plugin enabled!");
    }



     public Preferences getPreferences(UUID playerId) {
        return playerPreferences.computeIfAbsent(playerId, id -> {
            Preferences newPrefs = new Preferences(id);
            savePreferences();
            return newPrefs;
        });
    }

    private void loadPreferences() {
        File dataFolder = new File(getDataFolder(), "data"); // Ensure it's in `data/`
        if (!dataFolder.exists()) {
            dataFolder.mkdirs(); // Create the `data/` directory if missing
        }
    
        preferencesFile = new File(dataFolder, "preferences.yml");
    
        if (!preferencesFile.exists()) {
            try {
                preferencesFile.createNewFile();
                getLogger().info("Created new data/preferences.yml file.");
            } catch (IOException e) {
                getLogger().severe("Could not create data/preferences.yml!");
                return;
            }
        }
    
        // Load YAML configuration
        preferencesConfig = YamlConfiguration.loadConfiguration(preferencesFile);
    
        // Read stored preferences into memory
        for (String key : preferencesConfig.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID found in preferences.yml: " + key);
                continue;
            }
    
            Preferences preferences = new Preferences(playerId);
            preferences.setSoundSetting(Preferences.SoundSetting.valueOf(preferencesConfig.getString(key + ".sound", "ON")));
            preferences.setMessageSetting(Preferences.MessageSetting.valueOf(preferencesConfig.getString(key + ".messages", "STANDARD")));
            playerPreferences.put(playerId, preferences);
        }
    }
    
    private void initializeDealersIfLoaded() {
       // getLogger().info("[Debug] Starting dealer initialization on server start");
        
        // Track which dealers we've already initialized
        Set<UUID> initializedDealers = new HashSet<>();

        Bukkit.getWorlds().forEach(world -> {
            //getLogger().info("[Debug] Checking world: " + world.getName());
            
            // First, load all chunks that have dealers
            File dealersFile = new File(getDataFolder(), "data/dealers.yaml");
            if (dealersFile.exists()) {
                FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
                if (dealersConfig.contains("dealers")) {
                    for (String internalName : dealersConfig.getConfigurationSection("dealers").getKeys(false)) {
                        if (dealersConfig.getString("dealers." + internalName + ".world").equals(world.getName())) {
                            double x = dealersConfig.getDouble("dealers." + internalName + ".X");
                            double z = dealersConfig.getDouble("dealers." + internalName + ".Z");
                            int chunkX = (int) x >> 4;
                            int chunkZ = (int) z >> 4;
                            world.loadChunk(chunkX, chunkZ);
                        }
                    }
                }
            }

            // Now check all entities
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob) {
                    if (Dealer.isDealer(mob)) {
                        // Skip if we've already initialized this dealer
                        if (initializedDealers.contains(mob.getUniqueId())) {
                            //getLogger().info("[Debug] Skipping already initialized dealer: " + mob.getUniqueId());
                            continue;
                        }
                        
                       // getLogger().info("[Debug] Found dealer on server start: " + mob.getType() + " uuid:" + mob.getUniqueId());
                        initializedDealers.add(mob.getUniqueId());

                        mob.setCollidable(false);
                        String internalName = Dealer.getInternalName(mob);
                        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
                        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
                        int timer = getConfig().getInt("dealers." + internalName + ".timer", 30);
                        String anmsg = getConfig().getString("dealers." + internalName + ".animation-message");
                        List<Integer> chipSizes = new ArrayList<>();
                        ConfigurationSection chipSizeSection = getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
                        
                        if (chipSizeSection != null) {
                            for (String key : chipSizeSection.getKeys(false)) {
                                chipSizes.add(getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
                            }
                        }
                        chipSizes.sort(Integer::compareTo);
                
                        String currencyMaterial = getConfig().getString("dealers." + internalName + ".currency.material");
                        String currencyName = getConfig().getString("dealers." + internalName + ".currency.name");
                        Dealer.updateGameType(mob, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
                        
                        // Force chunk to stay loaded briefly while we initialize
                        Chunk chunk = mob.getLocation().getChunk();
                        boolean wasForceLoaded = chunk.isForceLoaded();
                        chunk.setForceLoaded(true);
                        
                        // Delay JockeyManager initialization to ensure stack is loaded
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            // Find the bottom-most mob in the stack
                            Mob bottomMob = mob;
                            while (bottomMob.getVehicle() instanceof Mob) {
                                bottomMob = (Mob) bottomMob.getVehicle();
                            }
                            
                            // Store all mobs in the stack from bottom to top
                            List<Mob> stackMobs = new ArrayList<>();
                            Mob current = bottomMob;
                            while (current != null) {
                                stackMobs.add(current);
                                if (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
                                    current = (Mob) current.getPassengers().get(0);
                                } else {
                                    current = null;
                                }
                            }

                            // Store armor stand info if present
                            ArmorStand armorStand = null;
                            String armorStandName = null;
                            for (Entity passenger : mob.getPassengers()) {
                                if (passenger instanceof ArmorStand) {
                                    armorStand = (ArmorStand) passenger;
                                    armorStandName = armorStand.getCustomName();
                                    break;
                                }
                            }
                            
                            // Temporarily unmount everything
                            for (Mob stackMob : stackMobs) {
                                if (stackMob.getVehicle() != null) {
                                    stackMob.getVehicle().removePassenger(stackMob);
                                }
                                for (Entity passenger : new ArrayList<>(stackMob.getPassengers())) {
                                    stackMob.removePassenger(passenger);
                                }
                            }

                            // Remove old armor stand if it exists
                            if (armorStand != null) {
                                armorStand.remove();
                            }
                            
                            // Remount everything in order from bottom to top
                            for (int i = 0; i < stackMobs.size() - 1; i++) {
                                Mob lower = stackMobs.get(i);
                                Mob upper = stackMobs.get(i + 1);
                                lower.addPassenger(upper);
                                lower.setCustomNameVisible(false);
                            }

                            // If there was an armor stand and no regular passengers, add it back
                            if (armorStandName != null && !mob.getPassengers().stream().anyMatch(p -> p instanceof Mob)) {
                                ArmorStand newArmorStand = (ArmorStand) world.spawnEntity(mob.getLocation(), EntityType.ARMOR_STAND);
                                newArmorStand.setVisible(false);
                                newArmorStand.setGravity(false);
                                newArmorStand.setInvulnerable(true);
                                newArmorStand.setSmall(true);
                                newArmorStand.setMarker(true);
                                newArmorStand.setCustomNameVisible(true);
                                newArmorStand.setCustomName(armorStandName);
                                mob.setCustomNameVisible(false);
                                mob.addPassenger(newArmorStand);
                            }
                            
                            // Now create JockeyManager after ensuring stack is properly mounted
                            new JockeyManager(mob);
                            
                            // Restore original force loaded state after a delay
                            if (!wasForceLoaded) {
                                Bukkit.getScheduler().runTaskLater(this, () -> chunk.setForceLoaded(false), 40L);
                            }
                        }, 5L); // Reduced delay to 5 ticks since we're handling armor stands properly now
                        
                        mob.setPersistent(true);
                        mob.setRemoveWhenFarAway(false);
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
                    }
                }
            }
        });
    }

    public void savePreferences() {
        if (preferencesConfig == null || preferencesFile == null) {
            getLogger().severe("preferencesConfig is null! Skipping save.");
            return;
        }
    
        for (Map.Entry<UUID, Preferences> entry : playerPreferences.entrySet()) {
            Preferences preferences = entry.getValue();
            preferencesConfig.set(entry.getKey() + ".sound", preferences.getSoundSetting().name());
            preferencesConfig.set(entry.getKey() + ".messages", preferences.getMessageSetting().name());
        }
    
        try {
            preferencesConfig.save(preferencesFile);
            //getLogger().info("Saved player preferences.");
        } catch (IOException e) {
            getLogger().severe("Could not save data/preferences.yml!");
        }
    }
    

    public void addInventory(UUID mobId, DealerInventory inv) {
        inventories.putIfAbsent(mobId, inv);
    }

    // Load currency material and name from config
    public void loadCurrencyFromConfig() {
        String currencyMaterialName = getConfig().getString("currency.material", "EMERALD").toUpperCase();
        currency = Material.matchMaterial(currencyMaterialName);

        if (currency == null) {
            getLogger().warning("Invalid currency material specified in config. Defaulting to EMERALD.");
            currency = Material.EMERALD;
        }
        currencyName = getConfig().getString("currency.name", "Emerald");
    }

    public Material getCurrency() {
        return currency;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    private void reinitializeDealers() {
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob) {
                    if (Dealer.isDealer(mob)) {
                        String internalName = Dealer.getInternalName(mob);

                        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
                        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
                        int timer = getConfig().getInt("dealers." + internalName + ".timer", 30);
                        String animationMessage = getConfig().getString("dealers." + internalName + ".animation-message");
                        List<Integer> chipSizes = new ArrayList<>();
                        ConfigurationSection chipSizeSection = getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
                        if (chipSizeSection != null) {
                            for (String key : chipSizeSection.getKeys(false)) {
                                chipSizes.add(getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
                            }
                        }
                        chipSizes.sort(Integer::compareTo); // Ensure chip sizes are in ascending order
                
                        String currencyMaterial = getConfig().getString("dealers." + internalName + ".currency.material");
                        String currencyName = getConfig().getString("dealers." + internalName + ".currency.name");
                        Dealer.updateGameType(mob, gameType, timer, animationMessage, name, chipSizes, currencyMaterial, currencyName);
                        Dealer.setName(mob, gameType + " Dealer");
                        Dealer.setAnimationMessage(mob, animationMessage);
                    }
                }
            }
        });
    }

    public void reloadDealer(Mob mob) {
        if (!Dealer.isDealer(mob)) {
            return;
        }
    
        //getLogger().info("[Debug] Reloading dealer: " + mob.getType());
    
        // Delete the associated inventory for this dealer
        deleteAssociatedInventories(mob);
    
        String internalName = Dealer.getInternalName(mob);
        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
        int timer = getConfig().getInt("dealers." + internalName + ".timer", 30);
        String anmsg = getConfig().getString("dealers." + internalName + ".animation-message");
        
        List<Integer> chipSizes = new ArrayList<>();
        ConfigurationSection chipSizeSection = getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
        if (chipSizeSection != null) {
            for (String key : chipSizeSection.getKeys(false)) {
                chipSizes.add(getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
            }
        }
        chipSizes.sort(Integer::compareTo); // Ensure chip sizes are in ascending order
        
        String currencyMaterial = getConfig().getString("dealers." + internalName + ".currency.material");
        String currencyName = getConfig().getString("dealers." + internalName + ".currency.name");
        
        Dealer.updateGameType(mob, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
        new JockeyManager(mob);
    }
    
    private void reloadDealers() {
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob) {
                    reloadDealer(mob); // Now calls the refactored function
                }
            }
        });
    }
        
    public void deleteAssociatedInventories(Mob mob) {
        UUID dealerId = Dealer.getUniqueId(mob);
    
        DealerInventory inv = DealerInventory.getInventory(dealerId);
        if (inv != null) {
            inv.delete();
            DealerInventory.inventories.remove(dealerId);
        }
    
        // Close inventories on the main thread
        Bukkit.getScheduler().runTask(this, () -> {
            // Close all player inventories linked to this dealer
            List<Player> menuPlayers = Menu.getOpenInventories(dealerId);
            for (Player player : menuPlayers) {
                player.closeInventory();
            }

            String internalName = Dealer.getInternalName(mob);

            List<Player> clientPlayers = Client.getOpenInventories(internalName);
            for (Player player : clientPlayers) {
                player.closeInventory();
            }

            // Close all animation inventories linked to this dealer
            List<Player> animationPlayers = AnimationMessage.getOpenInventories(dealerId);
            for (Player player : animationPlayers) {
                player.closeInventory();
            }

            // Close any players with an open inventory of MinesTable, RouletteInventory, BettingTable, or BlackjackInventory
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
                    continue; // Skip if the player has no open inventory
                }

                Inventory holderInventory = player.getOpenInventory().getTopInventory();

                if (holderInventory.getHolder() instanceof MinesTable minesTable) {
                    if (minesTable.dealerId.equals(dealerId)) {
                        player.closeInventory();
                    }
                } else if (holderInventory.getHolder() instanceof RouletteInventory rouletteInventory) {
                    if (rouletteInventory.dealerId.equals(dealerId)) {
                        player.closeInventory();
                    }
                } else if (holderInventory.getHolder() instanceof BettingTable bettingTable) {
                    if (bettingTable.dealerId.equals(dealerId)) {
                        player.closeInventory();
                    }
                } else if (holderInventory.getHolder() instanceof BlackjackInventory blackjackInventory) {
                    if (blackjackInventory.dealerId.equals(dealerId)) {
                        player.closeInventory();
                    }
                }
            }
        });


    }

    // Utility method to create NamespacedKey instances
    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }

    public void saveDefaultDealerConfig(String internalName) {
        String path = "dealers." + internalName;
        if (!getConfig().contains(path)) {
            getConfig().set(path + ".display-name", "Dealer");
            getConfig().set(path + ".game", "Menu");
            getConfig().set(path + ".timer", 30);
            getConfig().set(path + ".animation-message", "NCCASINO");
            getConfig().set(path + ".currency.material", "EMERALD");
            getConfig().set(path + ".currency.name", "Emerald");
            getConfig().set(path + ".chip-sizes.size1", 1);
            getConfig().set(path + ".chip-sizes.size2", 5);
            getConfig().set(path + ".chip-sizes.size3", 10);
            getConfig().set(path + ".chip-sizes.size4", 25);
            getConfig().set(path + ".chip-sizes.size5", 50);
            saveConfig();
        }
    }

    public Material getCurrency(String internalName) {
        String materialName = getConfig().getString("dealers." + internalName + ".currency.material", "EMERALD").toUpperCase();
        return Material.matchMaterial(materialName);
    }

    public String getCurrencyName(String internalName) {
        return getConfig().getString("dealers." + internalName + ".currency.name", "Emerald");
    }

    public double getChipValue(String internalName, int index) {
        return getConfig().getDouble("dealers." + internalName + ".chip-sizes.size" + index, 0);
    }

    public String getChipName(String internalName, int index) {
        double value = getChipValue(internalName, index);
        return (int) value + " " + getCurrencyName(internalName);
    }

    public int getTimer(String internalName) {
        String timerValue = getConfig().getString("dealers." + internalName + ".timer", "30").trim();
    
        long timer;
        try {
            timer = Long.parseLong(timerValue);
        } catch (NumberFormatException e) {
            return 30; 
        }
    
        if (timer < 0) return 0;      
        if (timer > 10000) return 10000; 
    
        return (int) timer;
    }

    // Dragon Descent specific configuration methods
    public int getDragonDescentColumns(String internalName) {
        int columns;
        if (!getConfig().contains("dealers." + internalName + ".default-columns")) {
            // If the key doesn't exist, set default value
            getConfig().set("dealers." + internalName + ".default-columns", "7");
            columns = 7;
        } else {
            // Retrieve current value
            String value = getConfig().getString("dealers." + internalName + ".default-columns", "7").trim();
            try {
                columns = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                columns = 7; // Default value if parsing fails
            }
        }
        return columns;
    }

    public int getDragonDescentVines(String internalName) {
        int vines;
        if (!getConfig().contains("dealers." + internalName + ".default-vines")) {
            // If the key doesn't exist, set default value
            getConfig().set("dealers." + internalName + ".default-vines", "5");
            vines = 5;
        } else {
            // Retrieve current value
            String value = getConfig().getString("dealers." + internalName + ".default-vines", "5").trim();
            try {
                vines = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                vines = 5; // Default value if parsing fails
            }
        }
        return vines;
    }

    public int getDragonDescentFloors(String internalName) {
        int floors;
        if (!getConfig().contains("dealers." + internalName + ".default-floors")) {
            // If the key doesn't exist, set default value
            getConfig().set("dealers." + internalName + ".default-floors", "4");
            floors = 4;
        } else {
            // Retrieve current value
            String value = getConfig().getString("dealers." + internalName + ".default-floors", "4").trim();
            try {
                floors = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                floors = 4; // Default value if parsing fails
            }
        }
        return floors;
    }

    public void reinitializeDealerConfigurations() {
        getLogger().info("Reinitializing dealer configurations...");
        reinitializeDealers();
    }

    public void reloadDealerConfigurations() {
        reloadDealers();
    }

    public static void sendErrorMessage(Player player, String msg){
        player.sendMessage("§c" + msg);
    }

    public Mob getDealerByInternalName(String internalName) {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob) {
                    PersistentDataContainer dataContainer = mob.getPersistentDataContainer();
                    String storedInternalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
                    if (internalName.equals(storedInternalName)) {
                        UUID dealerId = Dealer.getUniqueId(mob);
                        if (dealerId != null) {
                            return Dealer.findDealer(dealerId, mob.getLocation());
                        }
                        return mob;
                    }
                }
            }
        }
        return null;
    }

    public void checkForUpdates() {
        try {
            String pageContent = fetchPageContent("https://dev.bukkit.org/projects/nccasino");
            String latestVersion = parseVersionFromHtml(pageContent);
            String currentVersion = getDescription().getVersion();
    
            if (latestVersion != null && isVersionBehind(currentVersion, latestVersion)) {
                getLogger().log(Level.WARNING, "A new version of the plugin is available: {0}", latestVersion);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to check for updates: {0}", e.getMessage());
        }
    }
    
    private boolean isVersionBehind(String currentVersion, String latestVersion) {
        int[] currentParts = parseVersionNumbers(currentVersion);
        int[] latestParts = parseVersionNumbers(latestVersion);
    
        for (int i = 0; i < 3; i++) {
            if (currentParts[i] < latestParts[i]) return true;  // Current version is behind
            if (currentParts[i] > latestParts[i]) return false; // Current version is ahead
        }
        return false; // Versions are equal
    }
    
    private int[] parseVersionNumbers(String version) {
        String[] parts = version.split("\\.");
        int[] numbers = new int[3]; // major.minor.build
        for (int i = 0; i < parts.length && i < 3; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                numbers[i] = 0; // Fallback to 0 if parsing fails
            }
        }
        return numbers;
    }
    
    private String fetchPageContent(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL(); 
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    private String parseVersionFromHtml(String htmlContent) {
        Pattern pattern = Pattern.compile("nccasino-(\\d+\\.\\d+\\.\\d+)\\.jar");
        Matcher matcher = pattern.matcher(htmlContent);
        return matcher.find() ? matcher.group(1) : null;
    }

    public void executeOnDealer(String internalName, Runnable action) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File dealersFile = new File(getDataFolder(), "data/dealers.yaml");
            FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile); // Reload fresh config
        
            if (!dealersConfig.contains("dealers." + internalName)) {
                getLogger().warning("Dealer '" + internalName + "' not found in YAML.");
                return;
            }
        
            String worldName = dealersConfig.getString("dealers." + internalName + ".world");
            double x = dealersConfig.getDouble("dealers." + internalName + ".X");
            double z = dealersConfig.getDouble("dealers." + internalName + ".Z");
        
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World '" + worldName + "' not found for dealer '" + internalName + "'.");
                return;
            }
        
            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
        
            world.setChunkForceLoaded(chunkX, chunkZ, true);
        
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    Mob mob = getDealerByInternalName(internalName);
                    if (mob != null) {
                        action.run();
                    } else {
                        getLogger().warning("Dealer '" + internalName + "' could not be found in chunk.");
                    }
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (world.isChunkForceLoaded(chunkX, chunkZ)) {
                            world.setChunkForceLoaded(chunkX, chunkZ, false);
                        }
                    }, 100L);
                
                } else {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (world.isChunkLoaded(chunkX, chunkZ)) {
                            Mob mob = getDealerByInternalName(internalName);
                            if (mob != null) {
                                action.run();
                            } else {
                                getLogger().warning("Dealer '" + internalName + "' could not be found in chunk.");
                            }
                        
                        
                        } else {
                            getLogger().warning("Chunk could not be loaded for dealer '" + internalName + "'. Skipping.");
                        }
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (world.isChunkForceLoaded(chunkX, chunkZ)) {
                                world.setChunkForceLoaded(chunkX, chunkZ, false);
                            }
                        }, 40L);
                    
                    }, 10L);
                }
            
            
            }, 1L);
        });
    }
    
    public void executeOnAllDealers(CommandSender sender, boolean sendMessageOnCompletion) {
        File dealersFile = new File(getDataFolder(), "data/dealers.yaml");
        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
    
        if (!dealersConfig.contains("dealers")||dealersConfig.getConfigurationSection("dealers").getKeys(false).isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No dealers found.");
            return;
        }
    
        Map<String, List<String>> dealersByChunk = new HashMap<>();
        for (String internalName : dealersConfig.getConfigurationSection("dealers").getKeys(false)) {
            String worldName = dealersConfig.getString("dealers." + internalName + ".world");
            double x = dealersConfig.getDouble("dealers." + internalName + ".X");
            double z = dealersConfig.getDouble("dealers." + internalName + ".Z");
        
            String chunkKey = worldName + ":" + ((int) x >> 4) + "," + ((int) z >> 4);
            dealersByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(internalName);
        }
    
        int totalChunks = dealersByChunk.size();
        int[] processedChunks = {0};
        int[] totalDeleted = {0}; // Track total dealers removed
    
        for (Map.Entry<String, List<String>> entry : dealersByChunk.entrySet()) {
            String[] chunkParts = entry.getKey().split(":");
            String worldName = chunkParts[0];
            int chunkX = Integer.parseInt(chunkParts[1].split(",")[0]);
            int chunkZ = Integer.parseInt(chunkParts[1].split(",")[1]);
        
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World '" + worldName + "' not found for chunk " + entry.getKey());
                continue;
            }
        
            List<String> internalNames = entry.getValue();
            addDeletingChunk(world, chunkX, chunkZ);
            world.setChunkForceLoaded(chunkX, chunkZ, true);
        
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            getLogger().warning("Chunk (" + chunkX + ", " + chunkZ + ") did not load in time.");
                            removeDeletingChunk(world, chunkX, chunkZ); // Cleanup after deletion
                            return;
                        }
                        deleteDealersInChunk(world, chunkX, chunkZ, internalNames, sender, totalChunks, processedChunks, sendMessageOnCompletion, totalDeleted);
                    }, 10L);
                } else {
                    deleteDealersInChunk(world, chunkX, chunkZ, internalNames, sender, totalChunks, processedChunks, sendMessageOnCompletion, totalDeleted);
                }
            }, 1L);
        }
    }
    
    private void deleteDealersInChunk(World world, int chunkX, int chunkZ, List<String> internalNames, CommandSender sender, int totalChunks, int[] processedChunks, boolean sendMessageOnCompletion, int[] totalDeleted) {
        for (String internalName : internalNames) {
            Mob mob = getDealerByInternalName(internalName);
            if (mob != null) {
                // First clean up all associated inventories
                deleteAssociatedInventories(mob);

                // Create a JockeyManager to handle stack cleanup
                JockeyManager jockeyManager = new JockeyManager(mob);
                
                // Clean up all jockeys in the stack
                jockeyManager.cleanup();
                
                // First check and remove any armor stand passengers from ALL mobs in the stack
                List<JockeyNode> jockeys = jockeyManager.getJockeys();
                for (JockeyNode jockey : jockeys) {
                    Mob currentMob = jockey.getMob();
                    for (Entity passenger : new ArrayList<>(currentMob.getPassengers())) {
                        if (passenger instanceof ArmorStand) {
                            passenger.remove();
                        }
                    }
                }
                
                // Now remove all jockeys and vehicles from top down
                for (int i = jockeys.size() - 1; i > 0; i--) {
                    JockeyNode jockey = jockeys.get(i);
                    // First unmount to prevent any issues
                    jockey.unmount();
                    // Then remove the physical entity
                    jockey.getMob().remove();
                }
                
                // Remove the dealer and all its data
                if (Dealer.removeDealer(mob)) {
                    DealerInventory.unregisterAllListeners(mob);
                    removeDealerData(internalName);
                    totalDeleted[0]++;
                }
                
                // Clear any remaining references
                AdminMenu.clearAllEditModes(mob);
                
                // Remove from jockey manager cache
                DealerEventListener.clearJockeyManagerCache(mob.getUniqueId());
            } else {
                getLogger().warning("Dealer '" + internalName + "' could not be found in chunk.");
            }
        }
    
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (world.isChunkForceLoaded(chunkX, chunkZ)) {
                world.setChunkForceLoaded(chunkX, chunkZ, false);
            }
            removeDeletingChunk(world, chunkX, chunkZ); // Cleanup after deletion
        }, 40L);
    
        synchronized (processedChunks) {
            processedChunks[0]++;
            if (processedChunks[0] == totalChunks && sendMessageOnCompletion) {
                Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage(ChatColor.GREEN + "Deleted " + totalDeleted[0] + " dealers.")
                );
            }
        }
    }
    
    
    private void removeDealerData(String internalName) {
        File dealersFile = new File(getDataFolder(), "data/dealers.yaml");
        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile); // Reload fresh config
    
        internalName = internalName.trim(); // Sanitize input
        String path = "dealers." + internalName;
    
        if (!dealersConfig.contains(path)) {
            return;
        }
    
        dealersConfig.set(path, null); // Remove dealer
    
        try {
            dealersConfig.save(dealersFile); // Save updated YAML
        } catch (IOException e) {
            getLogger().severe("Failed to save dealers.yaml while removing dealer: " + internalName);
            e.printStackTrace();
        }
    }
    
    public boolean isChunkBeingDeleted(World world, int chunkX, int chunkZ) {
        return currentlyDeletingChunks.contains(world.getName() + ":" + chunkX + "," + chunkZ);
    }
    
    public void addDeletingChunk(World world, int chunkX, int chunkZ) {
        currentlyDeletingChunks.add(world.getName() + ":" + chunkX + "," + chunkZ);
    }
    
    public void removeDeletingChunk(World world, int chunkX, int chunkZ) {
        currentlyDeletingChunks.remove(world.getName() + ":" + chunkX + "," + chunkZ);
    }
    
    
    
}
