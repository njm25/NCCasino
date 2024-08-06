package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.nc.nccasino.entities.DealerVillager;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

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

        Player player = (Player) sender;
        Location location = player.getLocation();

        // Creating and spawning a dealer villager at the player's location
        Villager villager = DealerVillager.spawnDealer(plugin, location, "Dealer Villager");

        // Get the unique ID of the DealerVillager
        UUID uniqueId = DealerVillager.getUniqueId(villager);
        if (uniqueId != null) {
            player.sendMessage("Created Dealer Villager with ID: " + uniqueId);
        } else {
            player.sendMessage("Failed to create Dealer Villager.");
        }

        return true;
    }
}
