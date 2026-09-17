package org.nc.nccasino.payout;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nc.nccasino.Nccasino;

/**
 * The periodic "you still have winnings waiting" notice.
 *
 * <p>Strictly informational, by design. This class must never call
 * {@link OverflowBankService#claimAll} or otherwise touch a player's
 * inventory: a delivery that fires on a timer would move items underneath a
 * player who is mid-build, mid-trade, or sorting a chest, at a moment they
 * did not ask for and cannot predict. Delivery only ever happens at the four
 * moments the player caused: joining, opening a dealer, attempting a wager,
 * and {@code /ncc claim}.
 *
 * <p>Consequently this holds no reference to the service's delivery methods
 * at all -- it reads the store and sends chat, nothing else.
 */
public class OverflowBankReminder {

    private final Nccasino plugin;
    private final OverflowBankStore store;
    private BukkitTask task;

    public OverflowBankReminder(Nccasino plugin, OverflowBankStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Starts (or restarts) the reminder at the configured period. */
    public void start() {
        stop();

        long periodSeconds = OverflowSettings.load(plugin).reminderPeriodSeconds();
        if (periodSeconds <= 0) {
            return;
        }

        long periodTicks = periodSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::remindAll, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Sends one reminder to every online player holding a balance. Reads
     * only -- see the class note.
     */
    void remindAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remindPlayer(player);
        }
    }

    /**
     * Reminds one player, if they have anything banked. Reads the store and
     * sends chat -- deliberately nothing else. Split out from
     * {@link #remindAll()} so this exact behavior is directly testable
     * without a running server supplying an online-player list.
     */
    void remindPlayer(Player player) {
        long banked = store.totalUnits(player.getUniqueId());
        if (banked <= 0) {
            return;
        }
        player.sendMessage(plugin.getLocalization().text(
            player, "payout.bank-reminder", "amount", banked));
    }
}
