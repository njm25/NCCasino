package org.nc.nccasino.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

public class DeleteCommand implements CasinoCommand {

    private final Nccasino plugin;
    private final File dealersFile;
    private final YamlConfiguration dealersConfig;

    public DeleteCommand(Nccasino plugin) {
        this.plugin = plugin;
        this.dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        this.dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /ncc delete " + ChatColor.YELLOW + "<name>");
            return true;
        }
        AdminInventory.deleteAssociatedAdminInventories((Player) sender);

        String internalName = args[1];    
        
        Player player = (Player) sender;

        List<String> occupations = AdminInventory.playerOccupations(player.getUniqueId());
        List<Villager> villagers = AdminInventory.getOccupiedVillagers(player.getUniqueId())
            .stream()
            .filter(v -> v != null && !v.isDead() && v.isValid()) // Ensure valid villagers
            .toList();

        if (!occupations.isEmpty() && !villagers.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                if (i >= villagers.size()) {
                    break; // Prevent index mismatch
                }
                String occupation = occupations.get(i);
                Villager villager = villagers.get(i);
                
                String villagerName = (villager != null) ? DealerVillager.getInternalName(villager) : "unknown villager";
                Nccasino.sendErrorMessage(player, "Please finish editing " + occupation + " for '" +
                    ChatColor.YELLOW + villagerName + ChatColor.RED + "'.");
            }
            return true;
        }

        if (internalName.equals("*")) {
            // Delete all known dealers
            int count = killAllDealerVillagers();
            // Clear all entries in the YAML file
            clearAllDealerData();
            sender.sendMessage(ChatColor.GREEN + "Deleted " + count + " dealers.");
            return true;
        }

        Villager villager = plugin.getDealerByInternalName(internalName);

        if (villager == null) {
            sender.sendMessage(ChatColor.RED + "Dealer with internal name '" + ChatColor.YELLOW + internalName + ChatColor.RED + "' not found.");
            return true;
        }

        DealerVillager.removeDealer(villager);
        DealerInventory.unregisterAllListeners(villager);
        // Remove dealer data from YAML
        removeDealerData(internalName);
        
        sender.sendMessage(ChatColor.GREEN + "Dealer '" + ChatColor.YELLOW + internalName + ChatColor.GREEN + "' has been deleted.");

        return true;
    }

    private int killAllDealerVillagers() {
        int deletedCount = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        DealerVillager.removeDealer(villager);
                        DealerInventory.unregisterAllListeners(villager);
                        deletedCount++;
                    }
                }
            }
        }
        return deletedCount;
    }

    private void removeDealerData(String internalName) {
        internalName = internalName.trim(); // Sanitize input
        String path = "dealers." + internalName;

        try {
            dealersConfig.load(dealersFile); // Reload the YAML file
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Failed to load dealers.yaml: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (dealersConfig.contains(path)) {
            dealersConfig.set(path, null); // Remove the specific dealer
            try {
                dealersConfig.save(dealersFile); // Save the updated configuration
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save dealers.yaml while removing dealer: " + internalName);
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().warning("Attempted to remove a non-existent dealer: " + internalName);
        }
    }

    private void clearAllDealerData() {
        Set<String> keys = dealersConfig.getKeys(false);
        for (String key : keys) {
            dealersConfig.set(key, null);
        }
        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dealers.yaml while clearing all dealer data");
            e.printStackTrace();
        }
    }
}
