package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The dealer's start-transition U-path walk ({@link BlackjackSlotLayout#dealerUPath()}),
 * with slowdown weighting at the five row-2 checkpoints (each seat's own
 * first card cell -- 2, 11, 20, 29, 38) but only for seats that actually
 * have a committed wager, so the dealer visibly "inspects" real bets and
 * glides past empty seats.
 */
public final class BlackjackDealerInspectionPlan {

    private BlackjackDealerInspectionPlan() {
    }

    /** The five row-2 checkpoint slots along the U-path, in seat order -- each is a seat's own first card cell. */
    public static final List<Integer> CHECKPOINT_SLOTS = List.copyOf(
        BlackjackSlotLayout.orderedSeatSlots().stream()
            .map(BlackjackSlotLayout::pregameCountdownSlot)
            .toList()
    );

    /**
     * @param seatSlotsWithCommittedWager seat head slots (0/9/18/27/36) whose player has a committed wager
     * @param baseStepTicks                normal per-slot travel time
     * @param slowdownExtraTicks           extra time added on top of baseStepTicks when passing a wagered seat's checkpoint
     */
    public static List<BlackjackAnimationStep> build(Set<Integer> seatSlotsWithCommittedWager, long baseStepTicks, long slowdownExtraTicks) {
        List<Integer> path = BlackjackSlotLayout.dealerUPath();
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;
        for (int slot : path) {
            steps.add(new BlackjackAnimationStep(slot, delay, BlackjackAnimationStep.Kind.MOVE));
            long stepDuration = baseStepTicks;
            Integer checkpointSeat = checkpointSeatSlot(slot);
            if (checkpointSeat != null && seatSlotsWithCommittedWager.contains(checkpointSeat)) {
                stepDuration += slowdownExtraTicks;
            }
            delay += stepDuration;
        }
        return steps;
    }

    /** The seat slot a checkpoint corresponds to, or null if {@code slot} isn't one of the five checkpoints. */
    public static Integer checkpointSeatSlot(int slot) {
        if (!CHECKPOINT_SLOTS.contains(slot)) {
            return null;
        }
        return slot - 2; // pregameCountdownSlot(seat) == seat + 2
    }

    /**
     * Shifts every bottom-row step (slot &gt;= {@link BlackjackSlotLayout#DEALER_CARD_ROW_FIRST_SLOT}) of an
     * already-{@link #build built} path by the <b>minimum</b> constant amount needed so the first such step's
     * elapsed time is no earlier than {@code requiredBottomRowNotBeforeTicks} -- typically the private
     * wager-bar conceal's own duration, since both animations want that same slot range (see the table redesign
     * plan's "Start-transition sequencing" section). Every top/side step (before the bottom row) is left
     * completely untouched, and every bottom-row step is shifted by the exact same constant, preserving the
     * path's own internal ordering, relative spacing, and visual continuity.
     *
     * <p>If the dealer would naturally arrive at the bottom row on or after the required time anyway -- e.g.
     * because one or more committed-player checkpoint pauses already pushed the top/side leg long enough -- no
     * shift is applied at all. This must never add the full coordination duration on top of an already-late
     * natural arrival; doing so would introduce an unnecessary extra gap that delays the whole round for no
     * reason.
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
     * coordinated) path, including any committed-player pauses and any {@link #withBottomRowCoordination}
     * shift. 0 for an empty path. Callers must derive total inspection duration from this, never from a
     * separately-added constant that could drift from the schedule actually emitted.
     */
    public static long totalDurationTicks(List<BlackjackAnimationStep> steps) {
        return steps.isEmpty() ? 0L : steps.get(steps.size() - 1).getDelayTicks();
    }
}
