package org.nc.nccasino.commands;

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
    private final Map<String, String> commandPermissions = new HashMap<>();
    private final Nccasino plugin;

    public CommandExecution(JavaPlugin plugin) {
        this.plugin = (Nccasino) plugin;
        // Register each subcommand and its handler
        commands.put("help", new HelpCommand(this.plugin));
        commands.put("create", new CreateCommand(plugin));
        commands.put("reload", new ReloadCommand(plugin));
        commands.put("list", new ListDealersCommand((Nccasino) plugin));
        commands.put("delete", new DeleteCommand((Nccasino) plugin));
        commands.put("claim", new ClaimCommand(this.plugin));

        // Define required permissions for each command
        commandPermissions.put("help", "nccasino.commands.help");
        commandPermissions.put("create", "nccasino.commands.create");
        commandPermissions.put("reload", "nccasino.commands.reload");
        commandPermissions.put("list", "nccasino.commands.list");
        commandPermissions.put("delete", "nccasino.commands.delete");
        commandPermissions.put("claim", "nccasino.commands.claim");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("nccasino.use")) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.no-permission"));
            return true;
        }

        // No subcommand provided -> show usage
        if (args.length == 0) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-hint"));
            return true;
        }

        // The first argument is the subcommand name
        String commandName = args[0].toLowerCase();
        CasinoCommand commandHandler = commands.get(commandName);
        String requiredPermission = commandPermissions.get(commandName);

        if (commandHandler == null) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.unknown"));
            return true;
        }

        // Check if the sender has permission for the specific command
        if (requiredPermission != null && !sender.hasPermission(requiredPermission)) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.no-permission"));
            return true;
        }

        return commandHandler.execute(sender, args);
    }
}
