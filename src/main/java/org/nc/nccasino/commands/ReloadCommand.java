package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

public class ReloadCommand implements CasinoCommand {

    private final JavaPlugin plugin;

    public ReloadCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        plugin.reloadConfig();
        // Reinitialize dealer configurations
        ((Nccasino) plugin).reloadDealerConfigurations();
        sender.sendMessage(ChatColor.GREEN + "NCCASINO configuration reloaded successfully.");

        return true;
    }
}
