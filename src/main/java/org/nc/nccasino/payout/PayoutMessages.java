package org.nc.nccasino.payout;

import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.session.ExitReason;

/**
 * Centralizes player-facing pending-payout/result text in one place so it
 * stays consistent and is easy to localize later. This is not a
 * localization system — just a single home for these strings for now.
 */
public final class PayoutMessages {

    private static final String CONTEXT_PREFIX = "@nccasino:v1|";

    /** Set while a dealer's tables settle for a currency change: the label of the currency they settle in. */
    private static final ThreadLocal<String> currencyChangeLabel = new ThreadLocal<>();

    private PayoutMessages() {
    }

    /**
     * Runs {@code settle} with refunds attributed to a currency change rather
     * than a restart, naming {@code oldCurrencyLabel} as what they were paid in.
     */
    public static void duringCurrencyChange(String oldCurrencyLabel, Runnable settle) {
        String previous = currencyChangeLabel.get();
        currencyChangeLabel.set(oldCurrencyLabel);
        try {
            settle.run();
        } finally {
            if (previous == null) {
                currencyChangeLabel.remove();
            } else {
                currencyChangeLabel.set(previous);
            }
        }
    }

    /**
     * The standard context line stored on a pending record created because
     * a player disconnected mid-game, e.g. "You disconnected during an
     * active Roulette game. The game finished while you were offline."
     */
    public static String disconnectedMidGameContext(String gameType) {
        return encodeContext("disconnected", gameType);
    }

    /**
     * The context line stored when a player who is still online closed a
     * game before its already-decided result was paid, e.g. "You left your
     * Slots game before it finished, so it was settled for you."
     */
    public static String leftMidGameContext(String gameType) {
        return encodeContext("left-mid-game", gameType);
    }

    /**
     * The context for a decided result paid out because the session ended
     * for {@code reason}: a shutdown, reload or currency change (plugin
     * disable) keeps the committed-result wording, an online close says the
     * player left, and only a real disconnect says they disconnected.
     */
    public static String endedMidGameContext(ExitReason reason, String gameType) {
        if (reason == ExitReason.PLUGIN_DISABLE) {
            return committedResultContext(gameType);
        }
        if (reason == ExitReason.VOLUNTARY_INVENTORY_CLOSE) {
            return leftMidGameContext(gameType);
        }
        return disconnectedMidGameContext(gameType);
    }

    /**
     * The context line stored on a pending record created because the
     * server shut down while a round was still in flight — the game
     * couldn't be resolved to a real outcome, so the wager is refunded
     * rather than played out. Inside {@link #duringCurrencyChange} the
     * refund is attributed to the currency change instead.
     */
    public static String serverRestartRefundContext(String gameType) {
        String currency = currencyChangeLabel.get();
        if (currency != null) {
            return encodeContext("currency-change", gameType) + "|" + currency;
        }
        return encodeContext("server-restart", gameType);
    }

    /**
     * The context line stored on a pending record created because the
     * server shut down after a round's winner was already committed (the
     * decisive reveal/forfeit happened, but final settlement -- payout,
     * multiplier, loss notice -- hadn't run yet). Unlike
     * {@link #serverRestartRefundContext}, the round's outcome is honored
     * rather than refunded. Inside {@link #duringCurrencyChange} the payout
     * is attributed to the currency change instead.
     */
    public static String committedResultContext(String gameType) {
        String currency = currencyChangeLabel.get();
        if (currency != null) {
            return encodeContext("currency-change-committed", gameType) + "|" + currency;
        }
        return encodeContext("committed-result", gameType);
    }

    /**
     * Decodes new localization-aware records. Plain text from older plugin
     * versions returns {@code null} and is displayed unchanged.
     */
    public static StoredContext decodeContext(String storedContext) {
        if (storedContext == null || !storedContext.startsWith(CONTEXT_PREFIX)) {
            return null;
        }
        String[] parts = storedContext.substring(CONTEXT_PREFIX.length()).split("\\|", 3);
        if (parts.length < 2 || parts[1].isBlank()) {
            return null;
        }
        String currency = parts.length == 3 ? parts[2] : null;
        String localizationKey = switch (parts[0]) {
            case "disconnected" -> "payout.context-disconnected";
            case "left-mid-game" -> "payout.context-left-mid-game";
            case "server-restart" -> "payout.context-server-restart";
            case "committed-result" -> "payout.context-committed-result";
            case "currency-change" -> currency == null ? null : "payout.context-currency-change";
            case "currency-change-committed" -> currency == null ? null : "payout.context-currency-change-committed";
            default -> null;
        };
        return localizationKey == null ? null : new StoredContext(localizationKey, parts[1], currency);
    }

    public static String gameLocalizationKey(String gameType) {
        if (gameType == null) {
            return null;
        }
        return switch (gameType) {
            case "Blackjack" -> "game-options.blackjack";
            case "Roulette" -> "game-options.roulette";
            case "Mines" -> "game-options.mines";
            case "Baccarat" -> "game-options.baccarat";
            case "Coin Flip" -> "game-options.coin-flip";
            case "Rock Paper Scissors" -> "game-options.rock-paper-scissors";
            case "Dragon Descent" -> "game-options.dragon-descent";
            case "Slots" -> "game-options.slots";
            case "Test Game" -> "game-options.test-game";
            default -> null;
        };
    }

    private static String encodeContext(String reason, String gameType) {
        return CONTEXT_PREFIX + reason + "|" + (gameType == null ? "" : gameType);
    }

    /** {@code currency} is the old currency's label for a currency-change refund, null otherwise. */
    public record StoredContext(String localizationKey, String gameType, String currency) {
    }

    public static String formatAmount(PendingPayout payout) {
        if (payout.currencyMode() == CurrencyMode.VAULT) {
            return "$" + MoneyHelper.roundDisplay(MoneyHelper.bd(payout.amount())).toPlainString();
        }
        int whole = (int) payout.amount();
        String name = payout.currencyName() != null && !payout.currencyName().isBlank()
            ? payout.currencyName().toLowerCase(java.util.Locale.ROOT)
            : "currency";
        return whole + " " + name + (whole != 1 ? "s" : "");
    }
}
