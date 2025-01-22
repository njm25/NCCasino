package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GameOptionsInventory extends DealerInventory implements Listener {

    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();
    private final Nccasino plugin;
    private final String internalName;
    private final Boolean editing;
    private final Villager dealer;

    public GameOptionsInventory(Nccasino plugin, String internalName) {
        super(UUID.randomUUID(), 9, "Select Game Type");
        this.plugin = plugin;
        this.internalName = internalName;
        this.editing = false;
        this.dealer = null;
        initializeMenu();
    }

    public GameOptionsInventory(Nccasino plugin, Villager dealer) {
        super(UUID.randomUUID(), 9, "Edit Game Type");
        this.plugin = plugin;
        this.internalName = DealerVillager.getInternalName(dealer);
        this.editing = true;
        this.dealer = dealer;
       initializeMenu();
    }

    private void initializeMenu() {
        addItem(createCustomItem(Material.DIAMOND_BLOCK, "Blackjack"), 0);
        addItem(createCustomItem(Material.EMERALD_BLOCK, "Roulette"), 1);
        addItem(createCustomItem(Material.TNT, "Mines"), 2);
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement

        UUID playerId = player.getUniqueId();

        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

            String gameType;
            switch (slot) {
                case 0:
                    gameType = "Blackjack";
                    break;
                case 1:
                    gameType = "Roulette";
                    break;
                case 2:
                    gameType = "Mines";
                    break;
                default:
                    player.sendMessage("§cInvalid option selected.");
                    return;
            }
            if (!editing){
                createDealer(player, gameType);
            }
            else{
                editDealer(player, gameType);
            }
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
    }

    private void createDealer(Player player, String gameType) {
        Location location = player.getLocation();
        plugin.saveDefaultDealerConfig(internalName);
        DealerVillager.spawnDealer(plugin, location, "Dealer Villager", internalName, gameType);

        // Save dealer data
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }

        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

        var chunk = location.getChunk();
        String path = "dealers." + internalName;
        dealersConfig.set(path + ".world", location.getWorld().getName());
        dealersConfig.set(path + ".chunkX", chunk.getX());
        dealersConfig.set(path + ".chunkZ", chunk.getZ());
        dealersConfig.set(path + ".gameType", gameType);

        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
            e.printStackTrace();
        }

        player.sendMessage("§aDealer with game type '" + ChatColor.YELLOW + gameType + ChatColor.GREEN + "' created successfully!");

        player.closeInventory();
        this.delete();
    }

    private void editDealer(Player player, String gameType){
        if (!DealerVillager.isDealerVillager(dealer)) {
            return;
        }
    
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory inventory = DealerInventory.inventories.get(dealerId);
        if (inventory != null) {
            inventory.delete();
        }

        String internalName = DealerVillager.getInternalName(dealer);

        if (!plugin.getConfig().contains("dealers." + internalName + ".stand-on-17") && gameType.equals("Blackjack")) {
            plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        } else {
            int hasStand17Config = plugin.getConfig().getInt("dealers." + internalName + ".stand-on-17");
            if (hasStand17Config > 100 || hasStand17Config < 0) {
                plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            }
        }

        DealerVillager.switchGame(dealer, gameType, player);
        player.closeInventory();
        this.delete();
    }

}
