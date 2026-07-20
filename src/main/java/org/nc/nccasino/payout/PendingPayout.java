package org.nc.nccasino.payout;

import org.nc.nccasino.currency.CurrencyMode;

import java.util.UUID;

/**
 * A durable record of money (or just an outcome message) owed to a player
 * because a game finished while they were offline. Immutable — presence in
 * {@link PendingPayoutStore} means "still pending"; once delivered, the
 * record is removed rather than mutated.
 *
 * <p>{@code amount() <= 0} is a valid, expected case: a loss still gets a
 * record so the player receives a "here's what happened" message on
 * reconnect, without needing a separate data structure for pure-message
 * results.
 *
 * <p>Currency identity ({@code currencyMode}, {@code currencyMaterial},
 * {@code currencyName}) is snapshotted at creation time rather than
 * re-resolved from the originating dealer at delivery time, since the
 * dealer's config can change — or the dealer can be deleted entirely —
 * between when a disconnected player's outcome is calculated and when they
 * reconnect, possibly after a server restart.
 */
public record PendingPayout(
    UUID id,
    UUID playerId,
    String gameType,
    String dealerInternalName,
    CurrencyMode currencyMode,
    String currencyMaterial,
    String currencyName,
    double amount,
    long createdAtEpochMillis,
    String context
) {

    public static PendingPayout create(
        UUID playerId,
        String gameType,
        String dealerInternalName,
        CurrencyMode currencyMode,
        String currencyMaterial,
        String currencyName,
        double amount,
        String context
    ) {
        return new PendingPayout(
            UUID.randomUUID(),
            playerId,
            gameType,
            dealerInternalName,
            currencyMode,
            currencyMaterial,
            currencyName,
            amount,
            System.currentTimeMillis(),
            context
        );
    }
}
