package org.nc.nccasino.payout;

import java.util.List;

/**
 * Summarizes what happened when {@link PendingPayoutStore#attemptDeliver}
 * tried to settle every pending record for a player, so the caller (join
 * handling) can build appropriate messages without re-deriving state.
 */
public record DeliveryResult(List<PendingPayout> delivered, List<PendingPayout> stillPending) {

    public boolean isEmpty() {
        return delivered.isEmpty() && stillPending.isEmpty();
    }
}
