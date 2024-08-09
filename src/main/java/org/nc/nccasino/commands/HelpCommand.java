package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HelpCommand implements CasinoCommand {

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        player.sendMessage(Component.text("NCCASINO HELP"));
        player.sendMessage(Component.text("/ncc create <name> - Spawns Dealer where user is standing"));
        player.sendMessage(Component.text("/ncc list (page) - Lists the dealers"));
        player.sendMessage(Component.text("/ncc reload - Reloads the config"));

        return true;
    }
}
