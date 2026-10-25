package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

public class HelpCommand implements CasinoCommand {
    /** Permission, then the help line it unlocks, in display order. */
    static final String[][] HELP_LINES = {
        {"nccasino.commands.create", "commands.help-create"},
        {"nccasino.commands.list", "commands.help-list"},
        {"nccasino.commands.delete", "commands.help-delete"},
        {"nccasino.commands.reload", "commands.help-reload"},
        {"nccasino.commands.claim", "commands.help-claim"},
    };

    private final Nccasino plugin;

    public HelpCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.player-only"));
            return true;
        }
        // Every player can ask for help, so list only the commands this one may use.
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-title"));
        for (String[] line : HELP_LINES) {
            if (sender.hasPermission(line[0])) {
                sender.sendMessage(plugin.getLocalization().text(sender, line[1]));
            }
        }

        return true;
    }
}
