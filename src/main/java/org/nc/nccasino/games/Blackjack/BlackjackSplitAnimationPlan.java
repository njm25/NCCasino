package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The slide-out/deal/deal/park/reactivate step <em>shape</em> for a split:
 * the acting card slides out into its own hand, a replacement card deals
 * into each of the two hands, the pending sibling hand parks (no visible
 * slot -- see the table redesign plan's "Split rendering" section), and the
 * next pending hand (if any) reactivates into the seat's visible row.
 *
 * <p>Real split mechanics (matching rules, max-hands, ace-resplit,
 * depth-first hand-queue ordering, per-hand wagers/payout) are implemented
 * in {@link BlackjackSplitEligibility}, {@link BlackjackSplitQueue}, and
 * {@link BlackjackHand}, wired into gameplay by
 * {@code BlackjackInventory#handleSplit}. That runtime uses this class's
 * first three steps' shape (SLIDE_OUT/DEAL/DEAL, each on a fixed delay,
 * scheduled as a shared/table-owned {@link BlackjackAnimationRun} per the
 * plan -- every viewer sees the same canonical timeline) but deliberately
 * does <b>not</b> call {@link #build} to get PARK/REACTIVATE too: a parked
 * hand needs no actual step (it's simply never rendered, since pending
 * hands have no visible slot at all -- see the "Split rendering" section),
 * and reactivation cannot live on this plan's fixed timeline at all -- it
 * must fire whenever the currently-active hand naturally finishes (which
 * may be many further Hits, or another split, later), never a fixed delay
 * after the original split. The runtime's own
 * {@code BlackjackInventory#activateSplitHand}/{@code resolveHandAfterSplitAnimation}
 * implement that data-driven reactivation instead, reusing PARK/REACTIVATE's
 * exact semantics (full card-row repaint from scratch, since a pending
 * hand's cards were never rendered) without a fixed step delay.
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
