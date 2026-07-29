package org.nc.nccasino.payout;

import org.bukkit.ChatColor;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.MoneyHelper;

/**
 * Centralizes player-facing pending-payout/result text in one place so it
 * stays consistent and is easy to localize later. This is not a
 * localization system — just a single home for these strings for now.
 */
public final class PayoutMessages {

    private PayoutMessages() {
    }

    /**
     * The standard context line stored on a pending record created because
     * a player disconnected mid-game, e.g. "You disconnected during an
     * active Roulette game. The game finished while you were offline."
     */
    public static String disconnectedMidGameContext(String gameType) {
        return "You disconnected during an active " + gameType
            + " game. The game finished while you were offline.";
    }

    /**
     * The context line stored on a pending record created because the
     * server shut down while a round was still in flight — the game
     * couldn't be resolved to a real outcome, so the wager is refunded
     * rather than played out.
     */
    public static String serverRestartRefundContext(String gameType) {
        return "The server restarted while your " + gameType
            + " bet was still awaiting resolution, so it has been refunded.";
    }

    /** The chat message shown when a pending payout/result is delivered on join. */
    public static String formatDelivered(PendingPayout payout) {
        return ChatColor.YELLOW + payout.context()
            + "\n" + ChatColor.GREEN + "Payout: " + formatAmount(payout);
    }

    /** Shown on join when one or more pending payouts exist but couldn't be delivered yet. */
    public static String formatPendingRetryNotice(int count) {
        return ChatColor.GOLD + "You have " + count + " pending payout" + (count == 1 ? "" : "s")
            + " that could not be delivered yet. It will be delivered automatically once possible.";
    }

    private static String formatAmount(PendingPayout payout) {
        if (payout.currencyMode() == CurrencyMode.VAULT) {
            return "$" + MoneyHelper.roundDisplay(MoneyHelper.bd(payout.amount())).toPlainString();
        }
        int whole = (int) payout.amount();
        String name = payout.currencyName() != null && !payout.currencyName().isBlank()
            ? payout.currencyName().toLowerCase()
            : "currency";
        return whole + " " + name + (whole != 1 ? "s" : "");
    }
}
