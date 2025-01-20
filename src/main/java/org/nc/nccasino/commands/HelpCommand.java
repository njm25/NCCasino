package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HelpCommand implements CasinoCommand {

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "NCCASINO HELP");
        player.sendMessage(ChatColor.AQUA + "/ncc create " + ChatColor.YELLOW + "<name> " + 
                           ChatColor.AQUA + "- Spawns dealer where user is standing");
        player.sendMessage(ChatColor.AQUA + "/ncc list " + ChatColor.YELLOW + "(page) " + 
                           ChatColor.AQUA + "- Lists the dealers");
        player.sendMessage(ChatColor.AQUA + "/ncc delete " + ChatColor.YELLOW + "<name> " + 
                           ChatColor.AQUA + "- Deletes the specified dealer");
        player.sendMessage(ChatColor.AQUA + "/ncc reload - Reloads the config");

        return true;
    }
}
