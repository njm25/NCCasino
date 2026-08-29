package org.nc.nccasino.games.Slots;

/**
 * The explicit, mutually exclusive outcome of attempting to pay out a
 * committed spin. Replaces a single ambiguous boolean so that a durably
 * queued payout is never mistaken for one that was actually delivered.
 */
public enum SlotsSettlementResult {
    /** Live-credited to the player (or the payout was zero -- a completed loss needs no delivery). */
    DELIVERED,
    /** Live delivery failed but the exact amount was durably persisted for later delivery. */
    QUEUED,
    /** Neither live delivery nor durable persistence succeeded; the amount is retained, unresolved. */
    FAILED
}
