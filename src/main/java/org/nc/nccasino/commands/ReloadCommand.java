package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
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
        ((Nccasino) plugin).loadCurrencyFromConfig();
        sender.sendMessage(Component.text("NCCASINO configuration reloaded successfully."));

        return true;
    }
}
