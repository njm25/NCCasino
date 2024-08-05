package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CommandExecutor {

    private final Map<String, CasinoCommand> commands = new HashMap<>();

    public CommandExecutor() {
        commands.put("help", new HelpCommand());
        commands.put("create", new CreateCommand());
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (args.length == 0) { // Sender only typed '/hello' and nothing else
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
