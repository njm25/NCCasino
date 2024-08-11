package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;

import java.util.List;
import java.util.ArrayList;

public class ListDealersCommand implements CasinoCommand {
    private static final int DEALERS_PER_PAGE = 6;
    private final Nccasino plugin;

    public ListDealersCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid page number. Showing page 1.")
                        .color(NamedTextColor.RED));
            }
        }

        if (plugin.getConfig().contains("dealers")) {
            List<String> dealerNames = new ArrayList<>(plugin.getConfig().getConfigurationSection("dealers").getKeys(false));
            int totalPages = (int) Math.ceil((double) dealerNames.size() / DEALERS_PER_PAGE);

            if (totalPages == 0) {
                sender.sendMessage(Component.text("No dealers found.")
                        .color(NamedTextColor.RED));
                return true;
            }

            if (page < 0 || page > totalPages) {
                sender.sendMessage(Component.text("Page does not exist.")
                        .color(NamedTextColor.RED));
                return true;
            }
            int start = (page - 1) * DEALERS_PER_PAGE;
            int end = Math.min(start + DEALERS_PER_PAGE, dealerNames.size());

            sender.sendMessage(Component.text("List of dealers (Page " + page + " of " + totalPages + "):")
                    .color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            for (int i = start; i < end; i++) {
                String dealerName = dealerNames.get(i);
                String gameType = plugin.getConfig().getString("dealers." + dealerName + ".game", "Menu"); // Default to "Menu" if not found
                sender.sendMessage(Component.text("- " + dealerName)
                        .color(NamedTextColor.AQUA)
                        .append(Component.text(" [" + gameType + "]").color(NamedTextColor.YELLOW)));
            }
        } else {
            sender.sendMessage(Component.text("No dealers found.")
                    .color(NamedTextColor.RED));
        }

        return true;
    }
}
