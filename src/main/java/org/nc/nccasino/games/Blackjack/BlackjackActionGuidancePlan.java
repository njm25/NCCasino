package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * One cycle of action-guidance glow: a single on/off pulse at each
 * currently-available action slot, in the order supplied (callers pass
 * {@code BlackjackActionLayout.layout(actions).values()} in canonical
 * Hit/Stand/Double/Split order). The runtime loops this repeatedly until
 * the player acts or their turn ends.
 */
public final class BlackjackActionGuidancePlan {

    private BlackjackActionGuidancePlan() {
    }

    public static List<BlackjackAnimationStep> build(List<Integer> availableActionSlots, long onDurationTicks) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot : availableActionSlots) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.GLOW_ON));
            delay += onDurationTicks;
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.GLOW_OFF));
        }
        return steps;
    }

    public static long cycleDurationTicks(int availableActionCount, long onDurationTicks) {
        return (long) availableActionCount * onDurationTicks;
    }
}
