package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlackjackDealerInspectionPlanTest {

    private static final long BASE_TICKS = 5L;
    private static final long SLOWDOWN_TICKS = 15L;

    @Test
    void fullPathMatchesBlackjackSlotLayoutsUPathInOrder() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        List<Integer> expectedPath = BlackjackSlotLayout.dealerUPath();

        assertEquals(expectedPath.size(), steps.size());
        for (int i = 0; i < expectedPath.size(); i++) {
            assertEquals(expectedPath.get(i), steps.get(i).getSlot());
            assertEquals(BlackjackAnimationStep.Kind.MOVE, steps.get(i).getKind());
        }
    }

    @Test
    void noWageredSeatsMeansEveryStepUsesTheBaseDuration() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        for (int i = 1; i < steps.size(); i++) {
            long gap = steps.get(i).getDelayTicks() - steps.get(i - 1).getDelayTicks();
            assertEquals(BASE_TICKS, gap, "with no committed wagers, every leg of the path should take the base step duration");
        }
    }

    @Test
    void theFiveCheckpointSlotsAreEachSeatsFirstCardCell() {
        List<Integer> checkpoints = BlackjackDealerInspectionPlan.CHECKPOINT_SLOTS;
        assertEquals(5, checkpoints.size());
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            assertTrue(checkpoints.contains(BlackjackSlotLayout.pregameCountdownSlot(seatSlot)));
        }
    }

    @Test
    void checkpointSeatSlotResolvesEachCheckpointBackToItsSeatAndNullElsewhere() {
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            int checkpoint = BlackjackSlotLayout.pregameCountdownSlot(seatSlot);
            assertEquals(seatSlot, BlackjackDealerInspectionPlan.checkpointSeatSlot(checkpoint));
        }
        assertNull(BlackjackDealerInspectionPlan.checkpointSeatSlot(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT));
        assertNull(BlackjackDealerInspectionPlan.checkpointSeatSlot(BlackjackSlotLayout.DEALER_UP_CARD_SLOT));
    }

    @Test
    void onlyWageredSeatsCheckpointsGetTheSlowdown() {
        int wageredSeat = BlackjackSlotLayout.SEAT_SLOTS[2]; // seat slot 18 -> checkpoint 20
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(wageredSeat), BASE_TICKS, SLOWDOWN_TICKS);
        List<Integer> path = BlackjackSlotLayout.dealerUPath();

        for (int i = 1; i < steps.size(); i++) {
            int arrivingSlot = path.get(i - 1); // the leg *into* steps.get(i) departs from the previous checkpoint's slot
            long gap = steps.get(i).getDelayTicks() - steps.get(i - 1).getDelayTicks();
            Integer checkpointSeat = BlackjackDealerInspectionPlan.checkpointSeatSlot(arrivingSlot);
            if (checkpointSeat != null && checkpointSeat == wageredSeat) {
                assertEquals(BASE_TICKS + SLOWDOWN_TICKS, gap, "the wagered seat's checkpoint leg must include the slowdown");
            } else {
                assertEquals(BASE_TICKS, gap, "every other leg must use the base duration");
            }
        }
    }

    @Test
    void everySeatWageredSlowsDownAllFiveCheckpoints() {
        Set<Integer> allSeats = Set.of(
            BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[1], BlackjackSlotLayout.SEAT_SLOTS[2],
            BlackjackSlotLayout.SEAT_SLOTS[3], BlackjackSlotLayout.SEAT_SLOTS[4]
        );
        List<BlackjackAnimationStep> withAll = BlackjackDealerInspectionPlan.build(allSeats, BASE_TICKS, SLOWDOWN_TICKS);
        List<BlackjackAnimationStep> withNone = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);

        long lastWithAll = withAll.get(withAll.size() - 1).getDelayTicks();
        long lastWithNone = withNone.get(withNone.size() - 1).getDelayTicks();
        assertEquals(5 * SLOWDOWN_TICKS, lastWithAll - lastWithNone, "all five checkpoints slowed down must add exactly five slowdown increments to the total");
    }
}
