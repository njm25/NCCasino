package org.nc.nccasino.payout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money-splitting rule itself, exercised without a server. Every case
 * asserts the conservation invariant as well as the split, because the whole
 * point of the overflow system is that no unit is ever lost.
 */
class ItemDeliveryPlannerTest {

    private static final long STACK = 64L;
    private static final long FULL_INVENTORY = 36 * STACK; // 2304
    private static final long DROP_CAP = 36 * STACK;

    @Test
    void everythingFittingGoesStraightToTheInventory() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(500, FULL_INVENTORY, OverflowPreference.BANK, DROP_CAP);

        assertEquals(500, plan.toInventory());
        assertEquals(0, plan.toDrop());
        assertEquals(0, plan.toBank());
        assertConserved(500, plan);
    }

    @Test
    void partialFitDeliversWhatFitsAndBanksTheRemainder() {
        // The design's worked example: 10,000 won, room for 512.
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(10_000, 512, OverflowPreference.BANK, DROP_CAP);

        assertEquals(512, plan.toInventory());
        assertEquals(0, plan.toDrop(), "BANK preference must never drop");
        assertEquals(9_488, plan.toBank());
        assertConserved(10_000, plan);
    }

    @Test
    void dropPreferenceDropsTheRemainderUpToTheCapAndBanksTheRest() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(10_000, 512, OverflowPreference.DROP, DROP_CAP);

        assertEquals(512, plan.toInventory());
        assertEquals(DROP_CAP, plan.toDrop());
        assertEquals(10_000 - 512 - DROP_CAP, plan.toBank());
        assertConserved(10_000, plan);
    }

    @Test
    void dropCapIsEnforcedExactlyAndNeverExceeded() {
        long huge = 1_000_000L;
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(huge, 0, OverflowPreference.DROP, DROP_CAP);

        assertEquals(0, plan.toInventory());
        assertEquals(DROP_CAP, plan.toDrop());
        assertTrue(plan.toDrop() <= DROP_CAP, "drop must never exceed the configured cap");
        assertEquals(huge - DROP_CAP, plan.toBank());
        assertConserved(huge, plan);
    }

    @Test
    void dropRemainderSmallerThanTheCapIsFullyDroppedAndNothingIsBanked() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(600, 100, OverflowPreference.DROP, DROP_CAP);

        assertEquals(100, plan.toInventory());
        assertEquals(500, plan.toDrop());
        assertEquals(0, plan.toBank());
        assertConserved(600, plan);
    }

    @Test
    void aZeroDropCapBanksEverythingEvenUnderDropPreference() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(5_000, 0, OverflowPreference.DROP, 0);

        assertEquals(0, plan.toDrop());
        assertEquals(5_000, plan.toBank());
        assertConserved(5_000, plan);
    }

    @Test
    void aFullInventoryBanksTheWholePayoutUnderBankPreference() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(2_048, 0, OverflowPreference.BANK, DROP_CAP);

        assertEquals(0, plan.toInventory());
        assertEquals(2_048, plan.toBank());
        assertConserved(2_048, plan);
    }

    @Test
    void nonPositiveAmountsPlanNothing() {
        assertEquals(ItemDeliveryPlan.NOTHING,
            ItemDeliveryPlanner.plan(0, FULL_INVENTORY, OverflowPreference.BANK, DROP_CAP));
        assertEquals(ItemDeliveryPlan.NOTHING,
            ItemDeliveryPlanner.plan(-5, FULL_INVENTORY, OverflowPreference.BANK, DROP_CAP));
    }

    @Test
    void negativeCapacityIsTreatedAsNoRoomRatherThanCreatingUnits() {
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(100, -50, OverflowPreference.BANK, DROP_CAP);

        assertEquals(0, plan.toInventory());
        assertEquals(100, plan.toBank());
        assertConserved(100, plan);
    }

    @Test
    void aPayoutFarBeyondAnyInventoryStillConservesEveryUnit() {
        // Well above the removed 10,000 Slots ceiling.
        long payout = 5_000_000_000L;
        ItemDeliveryPlan plan = ItemDeliveryPlanner.plan(payout, FULL_INVENTORY, OverflowPreference.DROP, DROP_CAP);

        assertEquals(FULL_INVENTORY, plan.toInventory());
        assertEquals(DROP_CAP, plan.toDrop());
        assertEquals(payout - FULL_INVENTORY - DROP_CAP, plan.toBank());
        assertConserved(payout, plan);
    }

    private static void assertConserved(long requested, ItemDeliveryPlan plan) {
        assertEquals(requested, plan.total(), "planner must never create or destroy units");
    }
}
