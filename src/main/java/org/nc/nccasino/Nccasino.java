package org.nc.nccasino;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.commands.CommandExecution;
import org.nc.nccasino.commands.CommandTabCompleter;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.listeners.DealerDeathHandler;
import org.nc.nccasino.listeners.DealerEventListener;
import org.nc.nccasino.listeners.DealerInteractListener;
import java.util.logging.Level;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Nccasino extends JavaPlugin implements Listener {
    private NamespacedKey INTERNAL_NAME_KEY; // Declare it here

    private Material currency;    // Material used for betting currency
    private String currencyName;  // Display name for the currency

    /**
     * This local map was used in your code. If you still need it,
     * ensure you also clean it up. If not needed, consider removing.
     */
    public Map<UUID, DealerInventory> inventories = new HashMap<>();

    @Override
    public void onEnable() {
        INTERNAL_NAME_KEY = new NamespacedKey(this, "internal_name");
        checkForUpdates();
        saveDefaultConfig();

        // Load currency from config
        loadCurrencyFromConfig();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new DealerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DealerDeathHandler(this), this);
        getServer().getPluginManager().registerEvents(new DealerEventListener(), this);

        // Register the command executor
        if (this.getCommand("ncc") != null) {
            this.getCommand("ncc").setExecutor(new CommandExecution(this));
            this.getCommand("ncc").setTabCompleter(new CommandTabCompleter(this));
        }

        // Load any pre-existing dealer villagers from config
        loadDealerVillagers();

        getLogger().info("NCcasino plugin enabled!");
    }

    private void loadDealerVillagers() {
        File dealersFile = new File(getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.exists()) {
            getLogger().warning("The dealers.yaml file does not exist at " + dealersFile.getPath());
            return;
        }

        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

        if (dealersConfig.contains("dealers")) {
            dealersConfig.getConfigurationSection("dealers").getKeys(false).forEach(internalName -> {
                String path = "dealers." + internalName;
                int chunkX = dealersConfig.getInt(path + ".chunkX");
                int chunkZ = dealersConfig.getInt(path + ".chunkZ");
                String worldName = dealersConfig.getString(path + ".world");
                var world = Bukkit.getWorld(worldName);

                if (world == null) {
                    return;
                }

                // Force-load the chunk if not already
                if (!world.isChunkForceLoaded(chunkX, chunkZ)) {
                    world.setChunkForceLoaded(chunkX, chunkZ, true);
                }
            });
        }

        // After ensuring chunks are loaded, update existing Villagers
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        villager.setCollidable(false);
                        String internalName = DealerVillager.getInternalName(villager);
                        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
                        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
                        int timer = getConfig().getInt("dealers." + internalName + ".timer", 0);
                        String anmsg = getConfig().getString("dealers." + internalName + ".animation-message");
                        DealerVillager.updateGameType(villager, gameType, timer, anmsg, name);
                    }
                }
            }
        });
    }

    public void addInventory(UUID villagerId, DealerInventory inv) {
        inventories.putIfAbsent(villagerId, inv);
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

    private void reinitializeDealerVillagers() {
        getLogger().info("Entered reinitializeDealerVillagers.");
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        String internalName = DealerVillager.getInternalName(villager);

                        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
                        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
                        int timer = getConfig().getInt("dealers." + internalName + ".timer", 0);
                        String animationMessage = getConfig().getString("dealers." + internalName + ".animation-message");

                        DealerVillager.updateGameType(villager, gameType, timer, animationMessage, name);
                        DealerVillager.setName(villager, gameType + " Dealer");
                        DealerVillager.setAnimationMessage(villager, animationMessage);
                    }
                }
            }
        });
    }

    public void reloadDealerVillager(Villager villager) {
        if (!DealerVillager.isDealerVillager(villager)) {
            return;
        }

        UUID dealerId = DealerVillager.getUniqueId(villager);
        DealerInventory inventory = DealerInventory.getInventory(dealerId);
        if (inventory != null) {
            inventory.delete();
        }

        String internalName = DealerVillager.getInternalName(villager);
        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
        int timer = getConfig().getInt("dealers." + internalName + ".timer", 0);
        String anmsg = getConfig().getString("dealers." + internalName + ".animation-message");

        // Example special case for Blackjack
        if (!getConfig().contains("dealers." + internalName + ".stand-on-17") && "Blackjack".equals(gameType)) {
            getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        } else {
            int hasStand17Config = getConfig().getInt("dealers." + internalName + ".stand-on-17");
            if (hasStand17Config > 100 || hasStand17Config < 0) {
                getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            }
        }

        DealerVillager.updateGameType(villager, gameType, timer, anmsg, name);
    }

    private void reloadDealerVillagers() {
        // Collect dealer IDs first
        List<UUID> dealerIdsToDelete = new ArrayList<>(DealerInventory.inventories.keySet());

        // Delete them
        for (UUID dealerId : dealerIdsToDelete) {
            DealerInventory inv = DealerInventory.getInventory(dealerId);
            if (inv != null) {
                inv.delete();
            }
        }

        // Refresh each dealer from config
        Bukkit.getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        String internalName = DealerVillager.getInternalName(villager);
                        String name = getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
                        String gameType = getConfig().getString("dealers." + internalName + ".game", "Menu");
                        int timer = getConfig().getInt("dealers." + internalName + ".timer", 0);
                        String anmsg = getConfig().getString("dealers." + internalName + ".animation-message");

                        // Example special-case check
                        if (!getConfig().contains("dealers." + internalName + ".stand-on-17")
                            && "Blackjack".equals(gameType)) {
                            getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                        } else {
                            int hasStand17Config = getConfig().getInt("dealers." + internalName + ".stand-on-17");
                            if (hasStand17Config > 100 || hasStand17Config < 0) {
                                getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                            }
                        }

                        DealerVillager.updateGameType(villager, gameType, timer, anmsg, name);
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
            getConfig().set(path + ".timer", 0);
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
        return getConfig().getInt("dealers." + internalName + ".timer", 0);
    }

    public void reinitializeDealerConfigurations() {
        getLogger().info("Reinitializing dealer configurations...");
        reinitializeDealerVillagers();
    }

    public void reloadDealerConfigurations() {
        reloadDealerVillagers();
    }

    public static void sendErrorMessage(Player player, String msg){
        player.sendMessage("§c" + msg);
    }

    public Villager getDealerByInternalName(String internalName) {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
                    String storedInternalName = dataContainer.get(INTERNAL_NAME_KEY, PersistentDataType.STRING);
                    if (internalName.equals(storedInternalName)) {
                        return villager;
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
                getLogger().log(Level.WARNING, "[NCCasino] A new version of the plugin is available: {0}", latestVersion);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "[NCCasino] Failed to check for updates: {0}", e.getMessage());
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
}
