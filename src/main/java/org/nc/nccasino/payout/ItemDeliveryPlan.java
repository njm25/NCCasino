package org.nc.nccasino.payout;

/**
 * The decided split of one item payout across the three possible
 * destinations. Purely a value object -- nothing here has moved yet.
 *
 * <p>{@code toInventory + toDrop + toBank} always equals the requested
 * amount: the plan never loses units, which is the whole point of the
 * overflow system.
 */
public record ItemDeliveryPlan(long toInventory, long toDrop, long toBank) {

    public static final ItemDeliveryPlan NOTHING = new ItemDeliveryPlan(0L, 0L, 0L);

    public long total() {
        return toInventory + toDrop + toBank;
    }

    public boolean hasOverflow() {
        return toDrop > 0 || toBank > 0;
    }
}
