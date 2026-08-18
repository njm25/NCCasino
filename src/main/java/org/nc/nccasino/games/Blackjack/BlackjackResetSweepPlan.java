package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * A diagonal wipe across the whole 54-slot board: each slot's own
 * anti-diagonal (row + column) determines when it's briefly covered by a
 * white tile before being revealed again. The wavefront naturally starts as
 * a single top-left cell, widens as it crosses the board, then narrows back
 * down, purely as a consequence of the board's own 6x9 shape -- no per-cell
 * special-casing needed.
 *
 * <p>Used for BlackjackInventory's game-reset/restart transition (see
 * {@code startResetSweep}). Swappable: a different reset animation only
 * needs a new Plan class with this same {@code build(...)} shape and a
 * one-line call-site change, exactly like {@link BlackjackDealerInspectionPlan}.
 */
public final class BlackjackResetSweepPlan {

    private BlackjackResetSweepPlan() {
    }

    /**
     * @param stepTicks     ticks between one diagonal and the next joining the wavefront
     * @param holdDiagonals how many diagonals' worth of ticks a covered slot stays white before revealing again
     */
    public static List<BlackjackAnimationStep> build(long stepTicks, long holdDiagonals) {
        List<BlackjackAnimationStep> steps = new ArrayList<>();
        for (int slot = 0; slot < BlackjackSlotLayout.TOTAL_SLOTS; slot++) {
            int row = slot / BlackjackSlotLayout.SEAT_ROW_WIDTH;
            int col = slot % BlackjackSlotLayout.SEAT_ROW_WIDTH;
            int diagonal = row + col;
            long coverDelay = diagonal * stepTicks;
            long revealDelay = coverDelay + holdDiagonals * stepTicks;
            steps.add(new BlackjackAnimationStep(slot, coverDelay, BlackjackAnimationStep.Kind.CONCEAL));
            steps.add(new BlackjackAnimationStep(slot, revealDelay, BlackjackAnimationStep.Kind.REVEAL));
        }
        return steps;
    }

    /** The real completion time of a built sweep -- its latest scheduled step. 0 for an empty board. */
    public static long totalDurationTicks(List<BlackjackAnimationStep> steps) {
        long max = 0L;
        for (BlackjackAnimationStep step : steps) {
            max = Math.max(max, step.getDelayTicks());
        }
        return max;
    }
}
