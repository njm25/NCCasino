package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The pregame wager bar's slide-in/slide-out sequence: {@link #reveal} walks
 * the door outward from 45 to 53, "pulling" the wager controls into
 * existence as it passes; {@link #conceal} is the exact reverse -- same
 * slots, same per-step timing, walking 53 back to 45. Kept as two static
 * methods on purpose (rather than one parameterized direction) so a test
 * can assert they really are exact mirrors of each other.
 */
public final class BlackjackWagerRevealPlan {

    private BlackjackWagerRevealPlan() {
    }

    public static List<BlackjackAnimationStep> reveal(long stepTicks) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.REVEAL));
            delay += stepTicks;
        }
        return steps;
    }

    public static List<BlackjackAnimationStep> conceal(long stepTicks) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot = BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot >= BlackjackSlotLayout.UNDO_ALL_SLOT; slot--) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.CONCEAL));
            delay += stepTicks;
        }
        return steps;
    }

    /**
     * Total ticks a full {@link #reveal} pass takes -- derived from
     * {@code reveal}'s own step count rather than a separately-maintained
     * constant, so it stays correct if that method's shape ever changes.
     */
    public static long revealDurationTicks(long stepTicks) {
        return (long) reveal(stepTicks).size() * stepTicks;
    }

    /**
     * Total ticks a full {@link #conceal} pass takes -- the start-transition
     * sequencing guard uses this as the fixed worst-case delay before the
     * dealer's bottom-row U-path leg (47-53) may begin, per the table
     * redesign plan's "Start-transition sequencing" section. Derived from
     * {@code conceal}'s own step count so it stays correct if that method's
     * shape ever changes.
     */
    public static long concealDurationTicks(long stepTicks) {
        return (long) conceal(stepTicks).size() * stepTicks;
    }
}
