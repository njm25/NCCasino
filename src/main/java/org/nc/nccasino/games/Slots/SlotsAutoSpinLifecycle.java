package org.nc.nccasino.games.Slots;

/**
 * Pure Auto Spin stop decisions (Section 8), extracted so the safeguard list
 * is testable without a live Bukkit inventory.
 *
 * <p>Every one of these stops the automatic loop from starting its next
 * wager; none of them ever cancels or refunds a spin whose outcome is
 * already committed -- that distinction is enforced by {@code SlotsMachine}
 * itself (stopping the loop is a separate act from tearing down the running
 * animation/settlement), not by this class.
 */
public final class SlotsAutoSpinLifecycle {

    private SlotsAutoSpinLifecycle() {
    }

    /** Any rejected spin attempt (insufficient funds, unsafe wager, dealer cannot cover, ...) stops Auto Spin. */
    public static boolean stopsOn(SlotsSpinController.RejectReason reason) {
        return reason != null;
    }

    /** Only a settlement/payout blockage stops Auto Spin; a normal delivery or a durable queue does not. */
    public static boolean stopsOn(SlotsSettlementResult result) {
        return result == SlotsSettlementResult.FAILED;
    }
}
