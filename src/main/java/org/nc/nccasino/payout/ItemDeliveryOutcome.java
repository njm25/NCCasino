package org.nc.nccasino.payout;

/**
 * What actually happened to one item payout.
 *
 * <p>{@link #settled()} is the only question a paying game should ask. It is
 * {@code false} exactly when some part of the payout could neither be
 * delivered nor durably banked, which means the game still owes it and must
 * keep its own obligation intact -- a failed bank write must never be
 * reported as a completed settlement.
 */
public record ItemDeliveryOutcome(
    long requested,
    long toInventory,
    long dropped,
    long banked,
    long unsettled
) {

    public static ItemDeliveryOutcome nothing() {
        return new ItemDeliveryOutcome(0L, 0L, 0L, 0L, 0L);
    }

    /** Everything owed reached the player, the ground, or the bank. */
    public boolean settled() {
        return unsettled <= 0;
    }

    /** Whether the player should be told something went to the bank. */
    public boolean hasBanked() {
        return banked > 0;
    }
}
