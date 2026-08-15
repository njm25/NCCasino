package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The slide-out/park/reactivate sequence for a split: the acting card slides
 * out into its own hand, a replacement card deals into each of the two
 * hands, the pending sibling hand parks (no visible slot -- see the table
 * redesign plan's "Split rendering" section), and the next pending hand (if
 * any) reactivates into the seat's visible row.
 *
 * <p>This is a <b>shared/table-owned</b> animation per the plan -- every
 * viewer at the table sees the same canonical timeline, not just the
 * acting player -- but that scoping is a {@link BlackjackAnimationRun}
 * concern at schedule time, not something this pure plan class needs to
 * know about. Real split-eligibility/hand-queue mechanics (matching rules,
 * max-hands, ace-resplit) are a later phase; this class only captures the
 * animation step shape/ordering/timing so the runtime has something to
 * schedule against once that logic exists.
 */
public final class BlackjackSplitAnimationPlan {

    private BlackjackSplitAnimationPlan() {
    }

    /**
     * @param splitCardFromSlot         the card cell the split-off card currently occupies
     * @param firstHandReplacementSlot  where the original hand's replacement card lands
     * @param secondHandReplacementSlot where the newly-split hand's replacement card lands
     * @param pendingHandParkSlot       the seat's head/marker slot, used only to identify which seat parked (no card cell is ever written for a parked hand)
     * @param nextHandReactivateSlot    the first card cell the next-activated hand renders into
     * @param stepTicks                 delay between successive steps
     */
    public static List<BlackjackAnimationStep> build(
        int splitCardFromSlot,
        int firstHandReplacementSlot,
        int secondHandReplacementSlot,
        int pendingHandParkSlot,
        int nextHandReactivateSlot,
        long stepTicks
    ) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;

        steps.add(new BlackjackAnimationStep(splitCardFromSlot, delay, BlackjackAnimationStep.Kind.SLIDE_OUT));
        delay += stepTicks;

        steps.add(new BlackjackAnimationStep(firstHandReplacementSlot, delay, BlackjackAnimationStep.Kind.DEAL));
        delay += stepTicks;

        steps.add(new BlackjackAnimationStep(secondHandReplacementSlot, delay, BlackjackAnimationStep.Kind.DEAL));
        delay += stepTicks;

        steps.add(new BlackjackAnimationStep(pendingHandParkSlot, delay, BlackjackAnimationStep.Kind.PARK));
        delay += stepTicks;

        steps.add(new BlackjackAnimationStep(nextHandReactivateSlot, delay, BlackjackAnimationStep.Kind.REACTIVATE));

        return steps;
    }
}
