package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.GameOptionsInventory;

public class CreateCommand implements CasinoCommand {
    private final Nccasino plugin;

    public CreateCommand(Nccasino plugin) {
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
        if (plugin.getConfig().contains("dealers." + internalName)) {
            sender.sendMessage(ChatColor.RED + "A dealer with the internal name '" +
                    ChatColor.YELLOW + internalName + ChatColor.RED + "' already exists.");
            return true;
        }

        // Open the Game Options Inventory
        GameOptionsInventory inventory = new GameOptionsInventory(plugin, internalName);
        player.openInventory(inventory.getInventory());

        sender.sendMessage(ChatColor.GREEN + "Choose a game type for the dealer '" +
                ChatColor.YELLOW + internalName + ChatColor.GREEN + "'.");

        return true;
    }
}
