package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

import org.bukkit.Location;
import org.nc.nccasino.entities.DealerVillager;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.Nccasino;

public class CreateCommand implements CasinoCommand {
    private final JavaPlugin plugin;

    public CreateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /ncc create " + ChatColor.YELLOW + "<name>");
            return true;
        }

        Player player = (Player) sender;
        String internalName = args[1];

        // Check if the internal name already exists
        Nccasino nccasino = (Nccasino) plugin;
        if (nccasino.getConfig().contains("dealers." + internalName)) {
            sender.sendMessage(ChatColor.RED + "A dealer with the internal name '" +
                    ChatColor.YELLOW + internalName + ChatColor.RED + "' already exists.");
            return true;
        }

        // Store the dealer configuration with default values
        nccasino.saveDefaultDealerConfig(internalName);

        // Spawn the dealer
        Location location = player.getLocation();
        DealerVillager.spawnDealer(plugin, location, "Dealer Villager", internalName);

        // Save dealer location and chunk data
        File dealersFile = new File(nccasino.getDataFolder(), "data/dealers.yaml");

        // Ensure the parent directory exists
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }

        // Load the custom configuration
        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

        // Set the dealer's data
        var chunk = location.getChunk();
        String path = "dealers." + internalName;
        dealersConfig.set(path + ".world", location.getWorld().getName());
        dealersConfig.set(path + ".chunkX", chunk.getX());
        dealersConfig.set(path + ".chunkZ", chunk.getZ());

        // Save the configuration to file
        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            nccasino.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
            e.printStackTrace();
        }
        nccasino.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Dealer with internal name '" +
                ChatColor.YELLOW + internalName + ChatColor.GREEN + "' has been created.");

        return true;
    }
}
