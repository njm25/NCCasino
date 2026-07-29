package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

public class HelpCommand implements CasinoCommand {
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
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-title"));
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-create"));
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-list"));
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-delete"));
        sender.sendMessage(plugin.getLocalization().text(sender, "commands.help-reload"));

        return true;
    }
}
