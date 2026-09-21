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

    /**
     * Nothing moved at all: no items were delivered or dropped and nothing was
     * banked, so the caller still owes the full amount and a retry pays it
     * exactly once.
     */
    public static ItemDeliveryOutcome allUnsettled(long amount) {
        return new ItemDeliveryOutcome(amount, 0L, 0L, 0L, amount);
    }

    /** Whether any part of this payout physically reached the player or the ground. */
    public boolean movedAnythingPhysical() {
        return toInventory > 0 || dropped > 0;
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
