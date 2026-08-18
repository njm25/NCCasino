package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * The visible split sequence's pure step shape -- staged so a viewer can
 * actually see two hands come into being, rather than the second card
 * silently vanishing while a replacement appears in its place. Using
 * zero-based card-cell indices within the acting seat's row (0 = the
 * original hand's first card, "A"; 1 = its original second card, "B"):
 *
 * <pre>
 * Initial:              [A][B]
 * Phase 1 (slide B out): [A]      [B]        -- B moves to its temporary slot (index 3)
 * Phase 2 (deal C):      [A][C]   [B]        -- the still-active hand's replacement card
 * Phase 3 (deal D):      [A][C]   [B][D]     -- the pending sibling's replacement card
 * Phase 4 (park, step 1):[A][C] [B][D]       -- the inactive pair slides one cell left, into the gap (index 2/3)
 * Phase 5 (park, step 2):[A][C]              -- temporary slots clear; only the active hand remains visible
 * </pre>
 *
 * Phase 6 (returning control / split-ace auto-resolution) is not a rendering
 * step at all -- it's {@code BlackjackInventory#resolveHandAfterSplitAnimation},
 * which reuses {@code activateSplitHand}'s own activation logic once the
 * sibling hand is later actually reached, exactly as before this redesign.
 *
 * <p>The sibling hand is never actually shown "parked" anywhere -- like
 * before this redesign, a pending hand has no visible slot at all (see the
 * table redesign plan's "Split rendering" section); this plan only makes
 * the moment it becomes pending, and the moment its own replacement card
 * lands, genuinely visible before it disappears from view.
 */
public final class BlackjackSplitAnimationPlan {

    private BlackjackSplitAnimationPlan() {
    }

    /**
     * @param seatSlot  the acting player's own seat slot (0/9/18/27/36)
     * @param stepTicks delay between successive phases
     */
    public static List<BlackjackAnimationStep> build(int seatSlot, long stepTicks) {
        int slotOrigB = BlackjackSlotLayout.playerCardSlot(seatSlot, 1); // also becomes C's slot in phase 2
        int slotGap = BlackjackSlotLayout.playerCardSlot(seatSlot, 2);
        int slotTempB = BlackjackSlotLayout.playerCardSlot(seatSlot, 3);
        int slotTempD = BlackjackSlotLayout.playerCardSlot(seatSlot, 4);

        List<BlackjackAnimationStep> steps = new ArrayList<>();
        long delay = 0;

        // Phase 1: B slides out to its temporary right-hand position --
        // this is the moment the split genuinely becomes two hands.
        steps.add(new BlackjackAnimationStep(slotOrigB, delay, BlackjackAnimationStep.Kind.SLIDE_OUT));
        steps.add(new BlackjackAnimationStep(slotTempB, delay, BlackjackAnimationStep.Kind.MOVE));
        delay += stepTicks;

        // Phase 2: C deals in beside A, into the slot B just vacated.
        steps.add(new BlackjackAnimationStep(slotOrigB, delay, BlackjackAnimationStep.Kind.DEAL));
        delay += stepTicks;

        // Phase 3: D deals in beside temp-B.
        steps.add(new BlackjackAnimationStep(slotTempD, delay, BlackjackAnimationStep.Kind.DEAL));
        delay += stepTicks;

        // Phase 4: the inactive [B][D] pair slides one visible step left,
        // into the unused gap cell -- never touching A/C's real slots.
        steps.add(new BlackjackAnimationStep(slotGap, delay, BlackjackAnimationStep.Kind.MOVE));   // B -> gap
        steps.add(new BlackjackAnimationStep(slotTempB, delay, BlackjackAnimationStep.Kind.MOVE)); // D -> old temp-B slot
        delay += stepTicks;

        // Phase 5: park -- clear the temporary slots. Two ItemStacks can't
        // share one Minecraft slot, so "parked under the active hand"
        // resolves to simply no longer being rendered anywhere, exactly
        // like every other pending hand.
        steps.add(new BlackjackAnimationStep(slotGap, delay, BlackjackAnimationStep.Kind.PARK));
        steps.add(new BlackjackAnimationStep(slotTempB, delay, BlackjackAnimationStep.Kind.PARK));
        steps.add(new BlackjackAnimationStep(slotTempD, delay, BlackjackAnimationStep.Kind.PARK));

        return steps;
    }

    /** Total ticks the full phase 1-5 sequence takes -- derived from {@link #build}'s own shape, per phase count. */
    public static long durationTicks(long stepTicks) {
        return 4 * stepTicks;
    }
}
