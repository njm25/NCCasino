package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackWagerRevealPlanTest {

    private static final long STEP_TICKS = 4L;

    @Test
    void revealWalksFortyFiveToFiftyThreeAscending() {
        List<BlackjackAnimationStep> steps = BlackjackWagerRevealPlan.reveal(STEP_TICKS);

        assertEquals(9, steps.size()); // 45..53 inclusive
        for (int i = 0; i < steps.size(); i++) {
            BlackjackAnimationStep step = steps.get(i);
            assertEquals(BlackjackSlotLayout.UNDO_ALL_SLOT + i, step.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.REVEAL, step.getKind());
            assertEquals(i * STEP_TICKS, step.getDelayTicks());
        }
    }

    @Test
    void concealWalksFiftyThreeToFortyFiveDescending() {
        List<BlackjackAnimationStep> steps = BlackjackWagerRevealPlan.conceal(STEP_TICKS);

        assertEquals(9, steps.size());
        for (int i = 0; i < steps.size(); i++) {
            BlackjackAnimationStep step = steps.get(i);
            assertEquals(BlackjackSlotLayout.PREGAME_EXIT_SLOT - i, step.getSlot());
            assertEquals(BlackjackAnimationStep.Kind.CONCEAL, step.getKind());
            assertEquals(i * STEP_TICKS, step.getDelayTicks());
        }
    }

    // --- reveal()/conceal() are exact mirrors: same slots (reversed order), same per-step timing pattern ---

    @Test
    void revealAndConcealAreExactMirrorsInSlotOrderAndTiming() {
        List<BlackjackAnimationStep> reveal = BlackjackWagerRevealPlan.reveal(STEP_TICKS);
        List<BlackjackAnimationStep> conceal = BlackjackWagerRevealPlan.conceal(STEP_TICKS);

        assertEquals(reveal.size(), conceal.size());

        List<Integer> revealSlotsReversed = new ArrayList<>();
        for (BlackjackAnimationStep step : reveal) {
            revealSlotsReversed.add(step.getSlot());
        }
        Collections.reverse(revealSlotsReversed);

        List<Integer> concealSlots = new ArrayList<>();
        for (BlackjackAnimationStep step : conceal) {
            concealSlots.add(step.getSlot());
        }

        assertEquals(revealSlotsReversed, concealSlots, "conceal must visit the exact reverse slot order of reveal");

        // Same per-step delay pattern (0, stepTicks, 2*stepTicks, ...) in both directions.
        for (int i = 0; i < reveal.size(); i++) {
            assertEquals(reveal.get(i).getDelayTicks(), conceal.get(i).getDelayTicks());
        }
    }

    @Test
    void revealAndConcealDifferOnlyInKind() {
        List<BlackjackAnimationStep> reveal = BlackjackWagerRevealPlan.reveal(STEP_TICKS);
        for (BlackjackAnimationStep step : reveal) {
            assertEquals(BlackjackAnimationStep.Kind.REVEAL, step.getKind());
        }
        List<BlackjackAnimationStep> conceal = BlackjackWagerRevealPlan.conceal(STEP_TICKS);
        for (BlackjackAnimationStep step : conceal) {
            assertEquals(BlackjackAnimationStep.Kind.CONCEAL, step.getKind());
        }
    }

    // --- duration helpers, used by the start-transition sequencing guard ---

    @Test
    void revealDurationTicksMatchesTheStepCountTimesStepTicks() {
        assertEquals(9 * STEP_TICKS, BlackjackWagerRevealPlan.revealDurationTicks(STEP_TICKS));
    }

    @Test
    void concealDurationTicksMatchesTheStepCountTimesStepTicks() {
        assertEquals(9 * STEP_TICKS, BlackjackWagerRevealPlan.concealDurationTicks(STEP_TICKS));
    }

    @Test
    void durationHelpersStayInSyncWithTheActualStepListSizeRatherThanAHardcodedCount() {
        // Guards against the duration helpers drifting from reveal()/conceal()'s
        // real shape if the bottom row's slot range ever changes.
        assertEquals(BlackjackWagerRevealPlan.reveal(STEP_TICKS).size() * STEP_TICKS, BlackjackWagerRevealPlan.revealDurationTicks(STEP_TICKS));
        assertEquals(BlackjackWagerRevealPlan.conceal(STEP_TICKS).size() * STEP_TICKS, BlackjackWagerRevealPlan.concealDurationTicks(STEP_TICKS));
    }
}
