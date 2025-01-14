package org.nc.nccasino.commands;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
            sender.sendMessage(Component.text("Usage: /ncc delete ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text("<name>").color(NamedTextColor.YELLOW)));
            return true;
        }

        String internalName = args[1];

        if (internalName.equals("*")) {
            // Delete all known dealers
            int count = killAllDealerVillagers();
            // Clear all entries in the YAML file
            clearAllDealerData();
            sender.sendMessage(Component.text("Deleted " + count)
                    .color(NamedTextColor.GREEN));
            return true;
        }

        Villager villager = plugin.getDealerByInternalName(internalName);

        if (villager == null) {
            sender.sendMessage(Component.text("Dealer with internal name '")
                    .color(NamedTextColor.RED)
                    .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                    .append(Component.text("' not found.").color(NamedTextColor.RED)));
            return true;
        }

        DealerVillager.removeDealer(villager);
        // Remove dealer data from YAML
        removeDealerData(internalName);
        
        sender.sendMessage(Component.text("Dealer '")
                .color(NamedTextColor.GREEN)
                .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                .append(Component.text("' has been deleted.").color(NamedTextColor.GREEN)));

        return true;
    }

    private int killAllDealerVillagers() {
        int deletedCount = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        DealerVillager.removeDealer(villager);
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