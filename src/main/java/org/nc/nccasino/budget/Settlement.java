package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * The outcome of settling, releasing or refunding one commitment.
 *
 * <p>{@link Status#ALREADY_SETTLED} is the interesting one and is a success,
 * not an error: it is what a replayed settlement gets. Duplicate settlement
 * attempts are normal in this codebase -- a disconnect handler and a round-end
 * handler can both fire, a payout delivery can be retried, a reconnect can
 * re-run resolution -- so the safe behavior is for the second attempt to move
 * no money and say so plainly, rather than to fail loudly or, far worse, pay
 * again.
 *
 * @param paid what the player is legitimately owed and what actually left
 *     the dealer's <em>economy</em> -- always the full requested payout on a
 *     {@link Status#SETTLED} result, never reduced. Zero for a release and
 *     for a replay.
 * @param exposureViolation whether the requested payout exceeded the
 *     reservation that was set aside for it. Always indicates a bug in the
 *     pre-commitment exposure calculation and is worth logging wherever it
 *     appears -- the player is still paid in full; only the dealer's stored
 *     {@code live-balance} is at risk of under-reflecting what really left it
 *     (see {@code insolvent}).
 * @param insolvent whether the dealer's live balance was insufficient to
 *     cover the full payout even after accounting for the reservation --
 *     i.e. money left the dealer's economy that was never truly backed. This
 *     can only happen alongside {@code exposureViolation} and means the
 *     stored balance was floored at zero rather than driven negative.
 */
public record Settlement(Status status, BigDecimal paid, boolean exposureViolation, boolean insolvent) {

    public enum Status {
        /** Money moved and the reservation was released. */
        SETTLED,
        /** No such live reservation: already settled, or never created. Not an error. */
        ALREADY_SETTLED,
        /** The write failed, so nothing changed. The caller must not report a payout. */
        FAILED,
        /** The requested amount was unusable as economic state. */
        NUMERIC_LIMIT
    }

    public static Settlement settled(BigDecimal paid, boolean exposureViolation, boolean insolvent) {
        return new Settlement(Status.SETTLED, Money.of(paid), exposureViolation, insolvent);
    }

    public static Settlement alreadySettled() {
        return new Settlement(Status.ALREADY_SETTLED, Money.ZERO, false, false);
    }

    public static Settlement failed() {
        return new Settlement(Status.FAILED, Money.ZERO, false, false);
    }

    public static Settlement numericLimit() {
        return new Settlement(Status.NUMERIC_LIMIT, Money.ZERO, false, false);
    }

    /**
     * Whether the dealer's books are now correct for this commitment --
     * either it was settled here, or it had already been settled.
     *
     * <p>This, not {@code status == SETTLED}, is what a caller should branch on
     * before delivering a payout to a player: a replay must still deliver, and
     * must still not debit twice.
     */
    public boolean isResolved() {
        return status == Status.SETTLED || status == Status.ALREADY_SETTLED;
    }
}
