package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;

public class CommandExecutor {

    private final Map<String, CasinoCommand> commands = new HashMap<>();

    public CommandExecutor(JavaPlugin plugin) {
        commands.put("help", new HelpCommand());
        commands.put("create", new CreateCommand(plugin));
        commands.put("reload", new ReloadCommand(plugin));
        commands.put("list", new ListDealersCommand((Nccasino) plugin)); // Register the list command
        commands.put("delete", new DeleteCommand((Nccasino) plugin)); // Register the new delete command
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("nccasino.use")) { // Check if the sender has the required permission
            sender.sendMessage(Component.text("You do not have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { // Sender only typed '/ncc' and nothing else
            sender.sendMessage(Component.text("Use ")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("ncc help").color(NamedTextColor.AQUA))
                    .append(Component.text(" for help!").color(NamedTextColor.LIGHT_PURPLE)));
            return true;
        }

        String commandName = args[0].toLowerCase();
        CasinoCommand command = commands.get(commandName);

        if (command != null) {
            return command.execute(sender, args);
        } else {
            sender.sendMessage(Component.text("Unknown command. Use ")
                    .color(NamedTextColor.RED)
                    .append(Component.text("ncc help").color(NamedTextColor.AQUA))
                    .append(Component.text(" for help!").color(NamedTextColor.RED)));
            return true;
        }
    }
}
