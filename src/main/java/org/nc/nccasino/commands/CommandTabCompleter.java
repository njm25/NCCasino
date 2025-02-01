package org.nc.nccasino.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nc.nccasino.Nccasino;

import java.util.ArrayList;
import java.util.List;

public class CommandTabCompleter implements TabCompleter {

    private final Nccasino plugin;

    public CommandTabCompleter(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions; // Only players receive tab completions.
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // Suggest only the subcommands the player has permission for.
            if (player.hasPermission("nccasino.commands.help")) completions.add("help");
            if (player.hasPermission("nccasino.commands.create")) completions.add("create");
            if (player.hasPermission("nccasino.commands.reload")) completions.add("reload");
            if (player.hasPermission("nccasino.commands.list")) completions.add("list");
            if (player.hasPermission("nccasino.commands.delete")) completions.add("delete");
        } 
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete") && player.hasPermission("nccasino.commands.delete")) {
                // Allow deleting all dealers using "*"
                completions.add("*");

                // Suggest dealer names for "/ncc delete <dealer>"
                if (plugin.getConfig().contains("dealers")) {
                    completions.addAll(plugin.getConfig().getConfigurationSection("dealers").getKeys(false));
                }
            }
            else if (args[0].equalsIgnoreCase("list") && player.hasPermission("nccasino.commands.list")) {
                // Suggest valid page numbers dynamically for "/ncc list [page]"
                if (plugin.getConfig().contains("dealers")) {
                    int totalDealers = plugin.getConfig().getConfigurationSection("dealers").getKeys(false).size();
                    int dealersPerPage = 6;
                    int totalPages = (int) Math.ceil((double) totalDealers / dealersPerPage);

                    if (totalPages < 1) totalPages = 1; // Ensure at least one page

                    for (int i = 1; i <= totalPages; i++) {
                        completions.add(String.valueOf(i));
                    }
                } else {
                    completions.add("1"); // No dealers, default to page 1
                }
            }
        }

        return completions;
    }
}
