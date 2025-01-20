package org.nc.nccasino.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

        if (args.length == 1) {
            // Suggest main subcommands
            completions.add("help");
            completions.add("create");
            completions.add("reload");
            completions.add("list");
            completions.add("delete");
        } 
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                // Allow deleting all dealers using "*"
                completions.add("*");

                // Suggest dealer names for "/ncc delete <dealer>"
                if (plugin.getConfig().contains("dealers")) {
                    completions.addAll(plugin.getConfig().getConfigurationSection("dealers").getKeys(false));
                }
            }
            else if (args[0].equalsIgnoreCase("list")) {
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
