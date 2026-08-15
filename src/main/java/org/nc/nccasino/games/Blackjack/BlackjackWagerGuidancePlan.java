package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

import org.nc.nccasino.currency.ChipSlots;

/**
 * One cycle of wager-guidance glow: a single on/off pulse at each chip
 * denomination slot, left to right. Mirrors {@link BlackjackChairGuidancePlan}'s
 * shape -- the runtime loops this repeatedly until the viewer selects a
 * chip/all-in or closes their inventory.
 */
public final class BlackjackWagerGuidancePlan {

    private BlackjackWagerGuidancePlan() {
    }

    public static List<BlackjackAnimationStep> build(long onDurationTicks) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.GLOW_ON));
            delay += onDurationTicks;
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.GLOW_OFF));
        }
        return steps;
    }

    public static long cycleDurationTicks(long onDurationTicks) {
        return (long) (ChipSlots.LAST_SLOT - ChipSlots.FIRST_SLOT + 1) * onDurationTicks;
    }
}
