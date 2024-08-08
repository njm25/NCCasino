package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CommandExecutor {

    private final Map<String, CasinoCommand> commands = new HashMap<>();

    public CommandExecutor(JavaPlugin plugin) {
        commands.put("help", new HelpCommand());
        commands.put("create", new CreateCommand(plugin));
        commands.put("reload", new ReloadCommand(plugin));  // Register the reload command
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("nccasino.use")) { // Check if the sender has the required permission
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) { // Sender only typed '/ncc' and nothing else
            sender.sendMessage("Use ncc help for help!");
            return true;
        }

        String commandName = args[0].toLowerCase();
        CasinoCommand command = commands.get(commandName);

        if (command != null) {
            return command.execute(sender, args);
        } else {
            sender.sendMessage("Unknown command. Use ncc help for help!");
            return true;
        }
    }
}
