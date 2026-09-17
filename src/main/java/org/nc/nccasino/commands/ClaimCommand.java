package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.payout.OverflowBankService;

/**
 * {@code /ncc claim} -- the manual one of the four bank-delivery
 * opportunities (the other three are join, opening a dealer, and the
 * automatic attempt immediately before a wager).
 *
 * <p>Deliberately the only player-facing way to move money out of the
 * overflow bank on demand. There is no deposit counterpart: the bank is a
 * delivery buffer for winnings that would not fit, never a wallet a player
 * can put money into.
 */
public class ClaimCommand implements CasinoCommand {

    private final Nccasino plugin;

    public ClaimCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.player-only"));
            return true;
        }

        OverflowBankService bank = plugin.getOverflowBankService();
        if (bank == null || !bank.isBlocked(player.getUniqueId())) {
            player.sendMessage(plugin.getLocalization().text(player, "payout.bank-empty"));
            return true;
        }

        long before = bank.bankedUnits(player.getUniqueId());
        long remaining = bank.claimAll(player);
        long delivered = before - remaining;

        if (delivered > 0) {
            player.sendMessage(plugin.getLocalization().text(
                player, "payout.bank-claimed", "amount", delivered));
        }
        if (remaining > 0) {
            player.sendMessage(plugin.getLocalization().text(
                player, "payout.bank-still-blocked", "amount", remaining));
        } else {
            player.sendMessage(plugin.getLocalization().text(player, "payout.bank-cleared"));
        }
        return true;
    }
}
