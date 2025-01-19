package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;

public class CommandExecution implements CommandExecutor {

    private final Map<String, CasinoCommand> commands = new HashMap<>();

    public CommandExecution(JavaPlugin plugin) {
        // Register each subcommand and its handler
        commands.put("help", new HelpCommand());
        commands.put("create", new CreateCommand(plugin));
        commands.put("reload", new ReloadCommand(plugin));
        commands.put("list", new ListDealersCommand((Nccasino) plugin));
        commands.put("delete", new DeleteCommand((Nccasino) plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("nccasino.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // No subcommand provided -> show usage
        if (args.length == 0) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Use " +
                               ChatColor.AQUA + "/ncc help" +
                               ChatColor.LIGHT_PURPLE + " for help!");
            return true;
        }

        // The first argument is the subcommand name
        String commandName = args[0].toLowerCase();
        CasinoCommand commandHandler = commands.get(commandName);

        if (commandHandler != null) {
            return commandHandler.execute(sender, args);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown command. Use " +
                               ChatColor.AQUA + "/ncc help" +
                               ChatColor.RED + " for help!");
            return true;
        }
    }
}
