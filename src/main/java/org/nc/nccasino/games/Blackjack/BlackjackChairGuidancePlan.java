package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One cycle of chair-guidance glow: a single on/off pulse at each unfilled
 * seat head, top to bottom in table order, skipping already-filled seats.
 * Begins {@link BlackjackTiming#CHAIR_GUIDANCE_START_DELAY_TICKS} after a
 * viewer opens the table; the runtime is responsible for looping this
 * repeatedly (rescheduling a fresh cycle once one finishes) until that
 * viewer sits or closes their inventory -- this class only describes one
 * pass.
 */
public final class BlackjackChairGuidancePlan {

    private BlackjackChairGuidancePlan() {
    }

    /**
     * @param filledSeatSlots  seat head slots currently occupied -- skipped entirely
     * @param onDurationTicks  how long each seat's glow stays on before moving to the next
     */
    public static List<BlackjackAnimationStep> build(Set<Integer> filledSeatSlots, long onDurationTicks) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            if (filledSeatSlots.contains(seatSlot)) {
                continue;
            }
            steps.add(new BlackjackAnimationStep(seatSlot, delay, BlackjackAnimationStep.Kind.GLOW_ON));
            delay += onDurationTicks;
            steps.add(new BlackjackAnimationStep(seatSlot, delay, BlackjackAnimationStep.Kind.GLOW_OFF));
        }
        return steps;
    }

    /** Total ticks one full cycle takes -- when the runtime should reschedule the next cycle. Zero if every seat is filled. */
    public static long cycleDurationTicks(Set<Integer> filledSeatSlots, long onDurationTicks) {
        long unfilledCount = 0;
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            if (!filledSeatSlots.contains(seatSlot)) {
                unfilledCount++;
            }
        }
        return unfilledCount * onDurationTicks;
    }
}
