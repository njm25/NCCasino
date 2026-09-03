package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the Auto Spin stop safeguards (control redesign Section 8). */
class SlotsAutoSpinLifecycleTest {

    @Test
    void everyRejectReasonStopsAutoSpin() {
        for (SlotsSpinController.RejectReason reason : SlotsSpinController.RejectReason.values()) {
            assertTrue(SlotsAutoSpinLifecycle.stopsOn(reason),
                reason + " must stop Auto Spin");
        }
    }

    @Test
    void onlyFailedSettlementStopsAutoSpin() {
        assertTrue(SlotsAutoSpinLifecycle.stopsOn(SlotsSettlementResult.FAILED));
        assertFalse(SlotsAutoSpinLifecycle.stopsOn(SlotsSettlementResult.DELIVERED));
        assertFalse(SlotsAutoSpinLifecycle.stopsOn(SlotsSettlementResult.QUEUED));
    }
}
