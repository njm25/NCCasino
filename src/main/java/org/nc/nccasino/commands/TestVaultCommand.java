package org.nc.nccasino.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.economy.VaultHook;
import net.milkbowl.vault.economy.Economy;

public class TestVaultCommand implements CasinoCommand {

    private final Nccasino plugin;

    public TestVaultCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        VaultHook vaultHook = plugin.getVaultHook();

        // Check if Vault is available
        if (vaultHook == null || !vaultHook.isEconomyAvailable()) {
            player.sendMessage(ChatColor.RED + "Vault economy is not available. Install Vault and an economy plugin.");
            return true;
        }

        Economy economy = vaultHook.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy provider is not available.");
            return true;
        }

        // If no amount argument, show balance
        if (args.length == 1) {
            double balance = economy.getBalance(player);
            String formattedBalance = economy.format(balance);
            player.sendMessage(ChatColor.GREEN + "Your balance: " + ChatColor.YELLOW + formattedBalance);
            return true;
        }

        // If amount argument provided, add that amount to balance
        if (args.length == 2) {
            try {
                double amount = Double.parseDouble(args[1]);
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "Amount must be positive.");
                    return true;
                }

                economy.depositPlayer(player, amount);
                double newBalance = economy.getBalance(player);
                String formattedAmount = economy.format(amount);
                String formattedBalance = economy.format(newBalance);
                player.sendMessage(ChatColor.GREEN + "Added " + ChatColor.YELLOW + formattedAmount + 
                                 ChatColor.GREEN + " to your account.");
                player.sendMessage(ChatColor.GREEN + "New balance: " + ChatColor.YELLOW + formattedBalance);
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
                return true;
            }
        }

        // Too many arguments
        player.sendMessage(ChatColor.RED + "Usage: /ncc testvault [amount]");
        return true;
    }
}
