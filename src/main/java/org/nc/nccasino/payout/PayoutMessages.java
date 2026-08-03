package org.nc.nccasino.payout;

import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.MoneyHelper;

/**
 * Centralizes player-facing pending-payout/result text in one place so it
 * stays consistent and is easy to localize later. This is not a
 * localization system — just a single home for these strings for now.
 */
public final class PayoutMessages {

    private static final String CONTEXT_PREFIX = "@nccasino:v1|";

    private PayoutMessages() {
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
     * The context line stored on a pending record created because the
     * server shut down while a round was still in flight — the game
     * couldn't be resolved to a real outcome, so the wager is refunded
     * rather than played out.
     */
    public static String serverRestartRefundContext(String gameType) {
        return encodeContext("server-restart", gameType);
    }

    /**
     * Decodes new localization-aware records. Plain text from older plugin
     * versions returns {@code null} and is displayed unchanged.
     */
    public static StoredContext decodeContext(String storedContext) {
        if (storedContext == null || !storedContext.startsWith(CONTEXT_PREFIX)) {
            return null;
        }
        String[] parts = storedContext.substring(CONTEXT_PREFIX.length()).split("\\|", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            return null;
        }
        String localizationKey = switch (parts[0]) {
            case "disconnected" -> "payout.context-disconnected";
            case "server-restart" -> "payout.context-server-restart";
            default -> null;
        };
        return localizationKey == null ? null : new StoredContext(localizationKey, parts[1]);
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
            case "Test Game" -> "game-options.test-game";
            default -> null;
        };
    }

    private static String encodeContext(String reason, String gameType) {
        return CONTEXT_PREFIX + reason + "|" + (gameType == null ? "" : gameType);
    }

    public record StoredContext(String localizationKey, String gameType) {
    }

    public static String formatAmount(PendingPayout payout) {
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
