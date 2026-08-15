package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlackjackChairGuidancePlanTest {

    private static final long ON_TICKS = 20L;

    @Test
    void emptyTableGuidesEveryChairTopToBottom() {
        List<BlackjackAnimationStep> steps = BlackjackChairGuidancePlan.build(Set.of(), ON_TICKS);

        assertEquals(10, steps.size()); // 5 seats * (on + off)
        for (int i = 0; i < BlackjackSlotLayout.SEAT_SLOTS.length; i++) {
            int seat = BlackjackSlotLayout.SEAT_SLOTS[i];
            BlackjackAnimationStep on = steps.get(i * 2);
            BlackjackAnimationStep off = steps.get(i * 2 + 1);
            assertEquals(seat, on.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_ON, on.getKind());
            assertEquals(i * ON_TICKS, on.getDelayTicks());
            assertEquals(seat, off.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_OFF, off.getKind());
            assertEquals(i * ON_TICKS + ON_TICKS, off.getDelayTicks());
        }
    }

    @Test
    void filledSeatsAreSkippedEntirely() {
        int filled = BlackjackSlotLayout.SEAT_SLOTS[1];
        List<BlackjackAnimationStep> steps = BlackjackChairGuidancePlan.build(Set.of(filled), ON_TICKS);

        assertEquals(8, steps.size()); // 4 remaining seats * (on + off)
        for (BlackjackAnimationStep step : steps) {
            assertTrue(step.getSlot() != filled, "filled seat must never appear in the guidance cycle");
        }
    }

    @Test
    void everySeatFilledProducesAnEmptyPlan() {
        Set<Integer> allFilled = Set.of(
            BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[1], BlackjackSlotLayout.SEAT_SLOTS[2],
            BlackjackSlotLayout.SEAT_SLOTS[3], BlackjackSlotLayout.SEAT_SLOTS[4]
        );
        assertEquals(List.of(), BlackjackChairGuidancePlan.build(allFilled, ON_TICKS));
        assertEquals(0L, BlackjackChairGuidancePlan.cycleDurationTicks(allFilled, ON_TICKS));
    }

    @Test
    void cycleDurationMatchesTheNumberOfUnfilledSeats() {
        assertEquals(5 * ON_TICKS, BlackjackChairGuidancePlan.cycleDurationTicks(Set.of(), ON_TICKS));
        assertEquals(4 * ON_TICKS, BlackjackChairGuidancePlan.cycleDurationTicks(Set.of(BlackjackSlotLayout.SEAT_SLOTS[0]), ON_TICKS));
    }

    @Test
    void eachOnStepIsImmediatelyFollowedByItsOwnOffStep() {
        List<BlackjackAnimationStep> steps = BlackjackChairGuidancePlan.build(Set.of(), ON_TICKS);
        for (int i = 0; i < steps.size(); i += 2) {
            assertEquals(steps.get(i).getSlot(), steps.get(i + 1).getSlot(), "on/off pair must target the same slot");
            assertEquals(BlackjackAnimationStep.Kind.GLOW_ON, steps.get(i).getKind());
            assertEquals(BlackjackAnimationStep.Kind.GLOW_OFF, steps.get(i + 1).getKind());
        }
    }
}
