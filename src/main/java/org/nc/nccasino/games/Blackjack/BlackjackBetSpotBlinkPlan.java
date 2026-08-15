package org.nc.nccasino.games.Blackjack;

import java.util.List;

/**
 * A single glow on/off pulse for one seat's own bet spot, shown after that
 * player selects a chip/all-in amount ("Click to add {selected amount}").
 * The runtime loops this repeatedly until the player commits (clicks the
 * bet spot) or closes their inventory.
 */
public final class BlackjackBetSpotBlinkPlan {

    private BlackjackBetSpotBlinkPlan() {
    }

    public static List<BlackjackAnimationStep> build(int betSpotSlot, long onDurationTicks) {
        return List.of(
            new BlackjackAnimationStep(betSpotSlot, 0, BlackjackAnimationStep.Kind.GLOW_ON),
            new BlackjackAnimationStep(betSpotSlot, onDurationTicks, BlackjackAnimationStep.Kind.GLOW_OFF)
        );
    }

    public static long cycleDurationTicks(long onDurationTicks) {
        return onDurationTicks;
    }
}
