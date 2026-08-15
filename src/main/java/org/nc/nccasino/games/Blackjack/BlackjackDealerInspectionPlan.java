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
}
