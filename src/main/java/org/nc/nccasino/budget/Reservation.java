package org.nc.nccasino.budget;

import org.nc.nccasino.payout.BankedCurrency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Money a dealer has promised to one live commitment and may not promise
 * again.
 *
 * <h2>The identifier is the whole safety mechanism</h2>
 *
 * <p>{@link #id} is supplied by the caller and must be stable for the
 * commitment it represents -- the same Slots spin, the same Blackjack hand,
 * the same Roulette portfolio. Everything that makes this system safe against
 * duplicate money follows from that: reserving twice under one id credits the
 * stake once, settling twice under one id debits the payout once, and a
 * reconnect, a retried callback, a duplicated Bukkit event or a restart replay
 * all collapse onto the same record instead of creating a second one.
 *
 * <p>A caller that generates a fresh {@link UUID} per <em>attempt</em> rather
 * than per commitment defeats this entirely, which is why
 * {@link #forCommitment} takes the parts of the commitment's identity and
 * derives the id from them.
 *
 * <p>{@link #currency} is snapshotted rather than re-read from the dealer for
 * the same reason a {@link org.nc.nccasino.payout.PendingPayout} snapshots it:
 * an administrator may change or delete the dealer between the wager and the
 * settlement, and what the dealer owes must not change because of a config
 * edit.
 */
public record Reservation(
    String id,
    String dealerInternalName,
    UUID playerId,
    String gameType,
    BankedCurrency currency,
    BigDecimal amount,
    long createdAtEpochSeconds
) {

    public Reservation {
        amount = Money.clampNonNegative(amount);
    }

    /**
     * Derives a stable id from a commitment's identity.
     *
     * @param commitmentKey something that identifies this commitment and
     *     nothing else -- a round id, a hand id, a spin sequence number. Not a
     *     timestamp, and not a fresh random value per call.
     */
    public static String forCommitment(String dealerInternalName, UUID playerId, String commitmentKey) {
        return (dealerInternalName == null ? "?" : dealerInternalName)
            + "|" + playerId
            + "|" + (commitmentKey == null ? "?" : commitmentKey);
    }

    public Reservation withAmount(BigDecimal newAmount) {
        return new Reservation(
            id, dealerInternalName, playerId, gameType, currency,
            Money.clampNonNegative(newAmount), createdAtEpochSeconds);
    }
}
