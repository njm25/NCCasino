package org.nc.nccasino.commands;

import org.bukkit.Bukkit;
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
        if (((Nccasino) plugin).hasConfigProblem()) {
            // The settings in use were kept. Reload nothing else, so the
            // plugin never runs half on old settings and half on new ones.
            sender.sendMessage(((Nccasino) plugin).getLocalization().text(sender, "commands.reload-config-unreadable"));
            return true;
        }
        ((Nccasino) plugin).reloadLocalization();
        ((Nccasino) plugin).reloadServiceSettings();
        // Reinitialize dealer configurations
        ((Nccasino) plugin).reloadDealerConfigurations();
        // Rebuilding the dealers settled every table's players and saved what
        // they are owed; hand it over now rather than at their next login.
        // Two ticks, so the rebuilt tables' windows (closed next tick) are
        // gone first.
        Bukkit.getScheduler().runTaskLater(plugin, ((Nccasino) plugin)::deliverOwedWinningsToOnlinePlayers, 2L);
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
