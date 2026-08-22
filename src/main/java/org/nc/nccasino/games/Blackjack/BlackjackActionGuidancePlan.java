package org.nc.nccasino.games.Blackjack;

import java.util.List;

/**
 * The action-guidance glow set for one phase: every currently-available
 * action slot (callers pass {@code BlackjackActionLayout.layout(actions).values()}
 * in canonical Hit/Stand/Double/Split order). The runtime alternates
 * rendering this whole set GLOW then PLAIN, {@link BlackjackTiming#ACTION_GUIDANCE_STEP_TICKS}
 * apart, until the player acts or their turn ends.
 */
public final class BlackjackActionGuidancePlan {

    private BlackjackActionGuidancePlan() {
    }

    /** The complete current set is already computed by the caller -- this simply names the contract. */
    public static List<Integer> applicableSlots(List<Integer> availableActionSlots) {
        return availableActionSlots;
    }
}
