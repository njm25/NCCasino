package org.nc.nccasino.payout;

/**
 * Decides how one item payout is split between the player's inventory, the
 * ground, and the overflow bank. Deliberately pure and Bukkit-free so the
 * money-splitting rule can be tested exhaustively without a server.
 *
 * <p>The order is fixed by the design and is not configurable: inventory
 * first (always, regardless of preference), then the configured preference
 * for the remainder, then the bank for whatever is still left. Dropping is
 * capped; banking never is, because the bank is the one destination that
 * cannot lose winnings.
 */
public final class ItemDeliveryPlanner {

    private ItemDeliveryPlanner() {
    }

    /**
     * @param amount total units owed; non-positive yields
     *     {@link ItemDeliveryPlan#NOTHING}
     * @param freeCapacityUnits how many units the inventory can actually
     *     accept right now, counting empty slots and room left on existing
     *     partial stacks of this material
     * @param preference what to do with the part that does not fit
     * @param dropCapUnits the fixed server safety limit on how much may be
     *     dropped in one delivery; only consulted for
     *     {@link OverflowPreference#DROP}
     */
    public static ItemDeliveryPlan plan(
        long amount,
        long freeCapacityUnits,
        OverflowPreference preference,
        long dropCapUnits
    ) {
        if (amount <= 0) {
            return ItemDeliveryPlan.NOTHING;
        }

        long capacity = Math.max(0L, freeCapacityUnits);
        long toInventory = Math.min(amount, capacity);
        long remainder = amount - toInventory;

        long toDrop = 0L;
        if (remainder > 0 && preference == OverflowPreference.DROP) {
            toDrop = Math.min(remainder, Math.max(0L, dropCapUnits));
            remainder -= toDrop;
        }

        return new ItemDeliveryPlan(toInventory, toDrop, remainder);
    }
}
