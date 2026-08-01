package org.nc.nccasino.commands;

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
                sender.sendMessage(plugin.getLocalization().text(sender, "commands.invalid-page"));
            }
        }

        if (plugin.getConfig().contains("dealers")) {
            List<String> dealerNames = new ArrayList<>(plugin.getConfig().getConfigurationSection("dealers").getKeys(false));
            int totalPages = (int) Math.ceil((double) dealerNames.size() / DEALERS_PER_PAGE);

            if (totalPages == 0) {
                sender.sendMessage(plugin.getLocalization().text(sender, "commands.no-dealers"));
                return true;
            }

            if (page < 1 || page > totalPages) {
                sender.sendMessage(plugin.getLocalization().text(sender, "commands.page-missing"));
                return true;
            }

            int start = (page - 1) * DEALERS_PER_PAGE;
            int end = Math.min(start + DEALERS_PER_PAGE, dealerNames.size());

            sender.sendMessage(plugin.getLocalization().text(
                sender,
                "commands.list-header",
                "page",
                page,
                "pages",
                totalPages
            ));
            for (int i = start; i < end; i++) {
                String dealerName = dealerNames.get(i);
                String gameType = plugin.getConfig().getString("dealers." + dealerName + ".game", "Menu"); // Default to "Menu" if not found
                sender.sendMessage(plugin.getLocalization().text(
                    sender,
                    "commands.list-entry",
                    "name",
                    dealerName,
                    "game",
                    localizedGameType(sender, gameType)
                ));
            }
        } else {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.no-dealers"));
        }

        return true;
    }

    private String localizedGameType(CommandSender sender, String gameType) {
        if (gameType == null) {
            return String.valueOf(gameType);
        }
        return switch (gameType) {
            case "Blackjack" -> plugin.getLocalization().text(sender, "game-options.blackjack");
            case "Roulette" -> plugin.getLocalization().text(sender, "game-options.roulette");
            case "Mines" -> plugin.getLocalization().text(sender, "game-options.mines");
            case "Baccarat" -> plugin.getLocalization().text(sender, "game-options.baccarat");
            case "Coin Flip" -> plugin.getLocalization().text(sender, "game-options.coin-flip");
            case "Dragon Descent" -> plugin.getLocalization().text(sender, "game-options.dragon-descent");
            case "Test Game" -> plugin.getLocalization().text(sender, "game-options.test-game");
            default -> gameType;
        };
    }
}
