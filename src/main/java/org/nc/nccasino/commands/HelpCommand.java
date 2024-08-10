package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HelpCommand implements CasinoCommand {

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                    .color(NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        player.sendMessage(Component.text("NCCASINO HELP")
                .color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("/ncc create ")
                .color(NamedTextColor.AQUA)
                .append(Component.text("<name>").color(NamedTextColor.YELLOW))
                .append(Component.text(" - Spawns Dealer where user is standing").color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("/ncc list ")
                .color(NamedTextColor.AQUA)
                .append(Component.text("(page)").color(NamedTextColor.YELLOW))
                .append(Component.text(" - Lists the dealers").color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("/ncc reload - Reloads the config")
                .color(NamedTextColor.AQUA));

        return true;
    }
}
