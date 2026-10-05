package org.nc.nccasino.commands;

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
        ((Nccasino) plugin).reloadLocalization();
        // Reinitialize dealer configurations
        ((Nccasino) plugin).reloadDealerConfigurations();
        sender.sendMessage(
            ((Nccasino) plugin).getLocalization().text(sender, "commands.reload-success")
        );
        int ignored = ((Nccasino) plugin).getLocalization().overrideProblemCount();
        if (ignored > 0) {
            sender.sendMessage(((Nccasino) plugin).getLocalization().text(
                sender, "commands.reload-override-warnings", "count", ignored
            ));
        }

        return true;
    }
}
