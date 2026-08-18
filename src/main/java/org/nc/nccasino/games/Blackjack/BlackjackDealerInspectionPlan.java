package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The dealer's start-transition slide ({@link BlackjackSlotLayout#dealerStartTransitionPath()}) --
 * a smooth, uniform-speed walk straight down the board's right edge, with no
 * per-seat pauses.
 */
public final class BlackjackDealerInspectionPlan {

    private BlackjackDealerInspectionPlan() {
    }

    /** @param stepTicks per-slot travel time, uniform for every leg of the slide */
    public static List<BlackjackAnimationStep> build(long stepTicks) {
        List<Integer> path = BlackjackSlotLayout.dealerStartTransitionPath();
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot : path) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.MOVE));
            delay += stepTicks;
        }
        return steps;
    }

    /**
     * Shifts every bottom-row step (slot &gt;= {@link BlackjackSlotLayout#DEALER_CARD_ROW_FIRST_SLOT}) of an
     * already-{@link #build built} path by the <b>minimum</b> constant amount needed so the first such step's
     * elapsed time is no earlier than {@code requiredBottomRowNotBeforeTicks} -- typically the private
     * wager-bar conceal's own duration, since both animations want that same slot range (see the table redesign
     * plan's "Start-transition sequencing" section). Every step before the bottom row is left completely
     * untouched, and every bottom-row step is shifted by the exact same constant, preserving the path's own
     * internal ordering, relative spacing, and visual continuity.
     *
     * <p>If the dealer would naturally arrive at the bottom row on or after the required time anyway, no shift
     * is applied at all. This must never add the full coordination duration on top of an already-late natural
     * arrival; doing so would introduce an unnecessary extra gap that delays the whole round for no reason.
     *
     * @return {@code steps} itself (never copied) when no shift is needed, so callers/tests can rely on
     *     reference equality to confirm "no unnecessary gap" cheaply
     */
    public static List<BlackjackAnimationStep> withBottomRowCoordination(List<BlackjackAnimationStep> steps, long requiredBottomRowNotBeforeTicks) {
        long naturalFirstBottomRowDelay = -1;
        for (BlackjackAnimationStep step : steps) {
            if (step.getSlot() >= BlackjackSlotLayout.DEALER_CARD_ROW_FIRST_SLOT) {
                naturalFirstBottomRowDelay = step.getDelayTicks();
                break;
            }
        }
        long shift = naturalFirstBottomRowDelay < 0 ? 0L : Math.max(0L, requiredBottomRowNotBeforeTicks - naturalFirstBottomRowDelay);
        if (shift == 0L) {
            return steps;
        }
        List<BlackjackAnimationStep> coordinated = new ArrayList<>(steps.size());
        for (BlackjackAnimationStep step : steps) {
            if (step.getSlot() >= BlackjackSlotLayout.DEALER_CARD_ROW_FIRST_SLOT) {
                coordinated.add(new BlackjackAnimationStep(step.getSlot(), step.getDelayTicks() + shift, step.getKind()));
            } else {
                coordinated.add(step);
            }
        }
        return coordinated;
    }

    /**
     * The elapsed tick of {@code steps}' final entry -- the real, actual completion time of a (possibly
     * coordinated) path, including any {@link #withBottomRowCoordination} shift. 0 for an empty path. Callers
     * must derive total inspection duration from this, never from a separately-added constant that could drift
     * from the schedule actually emitted.
     */
    public static long totalDurationTicks(List<BlackjackAnimationStep> steps) {
        return steps.isEmpty() ? 0L : steps.get(steps.size() - 1).getDelayTicks();
    }
}
