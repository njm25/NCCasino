package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        sender.sendMessage(Component.text("NCCASINO configuration reloaded successfully.")
                .color(NamedTextColor.GREEN));

        return true;
    }
}
