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
 * @param paid what actually left the dealer, which is zero for a release and
 *     for a replay
 * @param clamped whether the requested payout exceeded the reservation and was
 *     reduced to it. Always indicates a bug in the pre-commitment exposure
 *     calculation and is worth logging wherever it appears.
 */
public record Settlement(Status status, BigDecimal paid, boolean clamped) {

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

    public static Settlement settled(BigDecimal paid, boolean clamped) {
        return new Settlement(Status.SETTLED, Money.of(paid), clamped);
    }

    public static Settlement alreadySettled() {
        return new Settlement(Status.ALREADY_SETTLED, Money.ZERO, false);
    }

    public static Settlement failed() {
        return new Settlement(Status.FAILED, Money.ZERO, false);
    }

    public static Settlement numericLimit() {
        return new Settlement(Status.NUMERIC_LIMIT, Money.ZERO, false);
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
