package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.Villager;
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
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /ncc create <name>");
            return true;
        }

        Player player = (Player) sender;
        String internalName = args[1];

        // Check if the internal name already exists
        Nccasino nccasino = (Nccasino) plugin;
        if (nccasino.getConfig().contains("dealers." + internalName)) {
            sender.sendMessage("A dealer with the internal name '" + internalName + "' already exists.");
            return true;
        }

        // Store the dealer configuration with default values
        nccasino.saveDefaultDealerConfig(internalName);

        // Spawn the dealer
        Location location = player.getLocation();
        DealerVillager.spawnDealer(plugin, location, "Dealer Villager", internalName);

        sender.sendMessage("Dealer with internal name '" + internalName + "' has been created.");

        return true;
    }
}
