package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class BlackjackResetSweepPlanTest {

    private static final long STEP_TICKS = 2L;
    private static final long HOLD_DIAGONALS = 5L;

    @Test
    void everySlotGetsExactlyOneConcealAndOneRevealStep() {
        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(STEP_TICKS, HOLD_DIAGONALS);
        assertEquals(BlackjackSlotLayout.TOTAL_SLOTS * 2, steps.size());
        for (int slot = 0; slot < BlackjackSlotLayout.TOTAL_SLOTS; slot++) {
            long concealTicks = -1;
            long revealTicks = -1;
            for (BlackjackAnimationStep step : steps) {
                if (step.getSlot() != slot) {
                    continue;
                }
                if (step.getKind() == BlackjackAnimationStep.Kind.CONCEAL) {
                    assertEquals(-1, concealTicks, "slot " + slot + " must have exactly one conceal step");
                    concealTicks = step.getDelayTicks();
                } else if (step.getKind() == BlackjackAnimationStep.Kind.REVEAL) {
                    assertEquals(-1, revealTicks, "slot " + slot + " must have exactly one reveal step");
                    revealTicks = step.getDelayTicks();
                }
            }
            assertTrue(concealTicks >= 0, "slot " + slot + " must have a conceal step");
            assertTrue(revealTicks > concealTicks, "slot " + slot + " must reveal strictly after it conceals");
        }
    }

    @Test
    void topLeftCoversFirstAndBottomRightCoversLast() {
        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(STEP_TICKS, HOLD_DIAGONALS);
        assertEquals(0L, concealTickFor(steps, 0), "the top-left slot must be the very first covered");
        assertEquals(13 * STEP_TICKS, concealTickFor(steps, BlackjackSlotLayout.TOTAL_SLOTS - 1),
            "the bottom-right slot sits on the last (13th) diagonal of a 6x9 board");
    }

    @Test
    void revealFollowsConcealByExactlyTheHoldDuration() {
        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(STEP_TICKS, HOLD_DIAGONALS);
        for (int slot = 0; slot < BlackjackSlotLayout.TOTAL_SLOTS; slot++) {
            assertEquals(concealTickFor(steps, slot) + HOLD_DIAGONALS * STEP_TICKS, revealTickFor(steps, slot));
        }
    }

    /** The wavefront's width per diagonal must widen 1, 2, 3... from the top-left corner, mirroring the board's own 6x9 shape. */
    @Test
    void wavefrontWidensNaturallyFromTheTopLeftCorner() {
        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(STEP_TICKS, HOLD_DIAGONALS);
        Map<Long, Integer> slotsPerConcealTick = new TreeMap<>();
        for (BlackjackAnimationStep step : steps) {
            if (step.getKind() == BlackjackAnimationStep.Kind.CONCEAL) {
                slotsPerConcealTick.merge(step.getDelayTicks(), 1, Integer::sum);
            }
        }
        List<Integer> widths = List.copyOf(slotsPerConcealTick.values());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 6, 6, 6, 5, 4, 3, 2, 1), widths,
            "diagonal widths of a 6-row by 9-column board must grow 1,2,3... then shrink symmetrically");
    }

    @Test
    void totalDurationTicksMatchesTheLastRevealStep() {
        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(STEP_TICKS, HOLD_DIAGONALS);
        long expected = 13 * STEP_TICKS + HOLD_DIAGONALS * STEP_TICKS;
        assertEquals(expected, BlackjackResetSweepPlan.totalDurationTicks(steps));
    }

    @Test
    void totalDurationTicksIsZeroForAnEmptyPlan() {
        assertEquals(0L, BlackjackResetSweepPlan.totalDurationTicks(List.of()));
    }

    private static long concealTickFor(List<BlackjackAnimationStep> steps, int slot) {
        return steps.stream()
            .filter(s -> s.getSlot() == slot && s.getKind() == BlackjackAnimationStep.Kind.CONCEAL)
            .findFirst().orElseThrow().getDelayTicks();
    }

    private static long revealTickFor(List<BlackjackAnimationStep> steps, int slot) {
        return steps.stream()
            .filter(s -> s.getSlot() == slot && s.getKind() == BlackjackAnimationStep.Kind.REVEAL)
            .findFirst().orElseThrow().getDelayTicks();
    }
}
