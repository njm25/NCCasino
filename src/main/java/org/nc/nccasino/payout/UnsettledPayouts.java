package org.nc.nccasino.payout;

import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;

import java.util.UUID;

/**
 * Turns a payout remainder that could not be delivered <em>or</em> banked into
 * a durable, retryable obligation.
 *
 * <p>This is the required counterpart to {@link OverflowBankService#deliver}
 * for callers that have no settlement state machine of their own. When the
 * overflow bank cannot record a remainder, the money must not be dropped
 * without limit (that defeats the configured drop cap) and must not be merely
 * logged (that loses it at restart). Instead it becomes a
 * {@link PendingPayout}, which the existing join-time delivery already retries
 * and which itself re-routes through the overflow bank when it is finally
 * paid.
 *
 * <p>The remainder handed here is by construction money that reached neither
 * the player nor the bank, so retaining it can never double-pay.
 */
public final class UnsettledPayouts {

    private UnsettledPayouts() {
    }

    /**
     * Retains a remainder under the caller's own reason. Use this whenever the
     * reason is known -- a refund retained as a "committed result" tells the
     * player the wrong story when they eventually collect it.
     *
     * @param context the encoded {@link PayoutMessages} context to store; when
     *     {@code null} the remainder is recorded as a committed result, which
     *     is the correct reason for an ordinary winning that could not be
     *     handed over
     * @param amount the genuinely unsettled remainder, in whole currency units
     * @return whether the obligation is now durably retained. A {@code false}
     *     return means the money could not be delivered, banked, or recorded;
     *     it is logged for manual reconciliation and must never be reported to
     *     the player as a completed payout.
     */
    public static boolean retain(
        Nccasino plugin,
        UUID playerId,
        String gameType,
        String dealerInternalName,
        CurrencyMode currencyMode,
        String currencyMaterial,
        String currencyName,
        long amount,
        String context
    ) {
        if (amount <= 0) {
            return true;
        }
        if (plugin == null || playerId == null || plugin.getPendingPayoutStore() == null) {
            return false;
        }

        PendingPayout payout = PendingPayout.create(
            playerId,
            gameType,
            dealerInternalName,
            currencyMode,
            currencyMaterial,
            currencyName,
            amount,
            context == null ? PayoutMessages.committedResultContext(gameType) : context
        );

        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().severe("[NCCasino] " + gameType + " payout remainder of " + amount
                + " for " + playerId + " could not be delivered, banked, or durably retained."
                + " This requires manual reconciliation -- the money is genuinely at risk.");
        }
        return persisted;
    }

    /** Retains an ordinary undeliverable winning, recorded as a committed result. */
    public static boolean retain(
        Nccasino plugin,
        UUID playerId,
        String gameType,
        String dealerInternalName,
        CurrencyMode currencyMode,
        String currencyMaterial,
        String currencyName,
        long amount
    ) {
        return retain(plugin, playerId, gameType, dealerInternalName, currencyMode,
            currencyMaterial, currencyName, amount, null);
    }
}
