package org.nc.nccasino.payout;

/**
 * What ultimately happened to an amount a game owed a player.
 *
 * <p>Exists to stop the "helper retains, caller retains again" bug: a delivery
 * helper that both records a {@link PendingPayout} itself <em>and</em> reports
 * plain failure invites its caller to record a second one for the same
 * obligation, paying it twice. Returning a disposition instead of a boolean
 * forces exactly one owner for the retention and tells the caller which of the
 * three genuinely different outcomes occurred.
 *
 * <p>Every unit of a payout ends in exactly one of these states.
 */
public enum PayoutDisposition {

    /** Every unit reached the player -- inventory, a capped drop, the bank, or a balance credit. */
    DELIVERED,

    /**
     * Some part could not be handed over, but it is durably recorded as a
     * {@link PendingPayout} and will be retried automatically. The money is
     * safe; the player simply does not have it yet.
     */
    RETAINED,

    /**
     * Some part reached neither the player nor durable storage. This is the
     * only state that must never be reported with success wording -- it needs
     * manual reconciliation.
     */
    UNRESOLVED;

    /** Whether the obligation is safe: delivered outright or durably retained. */
    public boolean isAccountedFor() {
        return this != UNRESOLVED;
    }

    /** Whether the player actually has the money now. */
    public boolean isInHand() {
        return this == DELIVERED;
    }

    /**
     * Combines the outcome of a delivery attempt with whether the leftover was
     * successfully retained -- the single place that mapping is made, so no
     * caller has to re-derive (or re-retain) it.
     */
    public static PayoutDisposition of(boolean fullyDelivered, boolean retainedRemainder) {
        if (fullyDelivered) {
            return DELIVERED;
        }
        return retainedRemainder ? RETAINED : UNRESOLVED;
    }
}
