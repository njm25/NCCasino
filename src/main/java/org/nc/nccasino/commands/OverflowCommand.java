package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.payout.OverflowMode;
import org.nc.nccasino.payout.OverflowPreference;
import org.nc.nccasino.payout.OverflowSettings;

import java.util.Locale;

/**
 * {@code /ncc overflow <bank|drop>} -- the player's choice of what happens to
 * winnings that do not fit in their inventory.
 *
 * <p>Deliberately a command rather than a menu entry: adding it to the player
 * or preferences GUI would change an existing inventory layout, which is a
 * separate decision that needs sign-off.
 *
 * <p>The choice is always stored, even while the server is forcing BANK or
 * DROP for everyone. A forced mode overrides which behavior is used but never
 * erases what the player picked, so returning the server to player choice
 * restores each player's own setting rather than resetting the world.
 */
public class OverflowCommand implements CasinoCommand {

    private final Nccasino plugin;

    public OverflowCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.player-only"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getLocalization().text(player, "payout.overflow-usage"));
            return true;
        }

        OverflowPreference chosen = OverflowPreference.parse(args[1], null);
        if (chosen == null) {
            player.sendMessage(plugin.getLocalization().text(player, "payout.overflow-usage"));
            return true;
        }

        plugin.getPreferences(player.getUniqueId()).setOverflowPreference(chosen);

        player.sendMessage(plugin.getLocalization().text(
            player,
            chosen == OverflowPreference.BANK
                ? "payout.overflow-bank-selected"
                : "payout.overflow-drop-selected"));

        OverflowMode mode = OverflowSettings.load(plugin).mode();
        if (mode != OverflowMode.PLAYER_CHOICE) {
            // Stored anyway -- say so plainly rather than letting the player
            // believe a setting took effect when the server is overriding it.
            player.sendMessage(plugin.getLocalization().text(player, "payout.overflow-forced"));
        }
        return true;
    }

    /** The accepted arguments, for tab completion. */
    public static String[] choices() {
        return new String[] {
            OverflowPreference.BANK.name().toLowerCase(Locale.ROOT),
            OverflowPreference.DROP.name().toLowerCase(Locale.ROOT)
        };
    }
}
