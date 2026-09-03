package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure step schedule for the Paylines 1&lt;-&gt;max wrap flash, extracted so the
 * cascade can be unit-tested without a live Bukkit inventory or scheduler.
 *
 * <p>The wrap is the one Paylines change that flips every line's active status
 * at once, so it can never be shown as a single-line add/remove blink -- but
 * the plain repaint it used to get said nothing at all about what had just
 * happened. This walks the affected lines instead, one quick step each:
 * dropping from max to 1 flashes every line that just went inactive, highest
 * first, and finishes on line 1 in the "added" colour so the eye lands on the
 * one line still live; climbing from 1 to max flashes each newly active line
 * in ascending order.
 *
 * <p>Deliberately built from the same per-line path painting the ordinary
 * single-line flash already uses ({@link SlotsLineFlashPlan#materialForChange}),
 * rather than a bespoke sweep animation -- one visual vocabulary for "this
 * line changed", played once per line.
 */
public final class SlotsLineWrapCascade {

    private SlotsLineWrapCascade() {
    }

    /** Ticks between successive lines -- quicker than a single-line blink, so the whole run reads as one gesture. */
    public static final long STEP_TICKS = 3L;

    /** Ticks the last step holds before the canvas is repainted clean. */
    public static final long SETTLE_TICKS = 8L;

    /**
     * One line's moment in the cascade.
     *
     * @param lineNumber the 1-based payline to paint
     * @param added whether to paint it in the "added" colour rather than the "removed" one
     */
    public record Step(int lineNumber, boolean added) {
    }

    /**
     * The ordered steps for a wrap from {@code oldLines} to {@code newLines}.
     *
     * <p>Dropping to 1: every line from the old maximum down to 2 as removed,
     * then line 1 as added. Climbing to the maximum: every line from 2 up to
     * the new maximum as added -- line 1 was already active and is not
     * re-announced. Any non-wrap pair yields no steps at all, since the
     * ordinary single-line flash owns those.
     */
    public static List<Step> stepsFor(int oldLines, int newLines) {
        List<Step> steps = new ArrayList<>();
        if (oldLines > 1 && newLines == 1) {
            for (int line = oldLines; line >= 2; line--) {
                steps.add(new Step(line, false));
            }
            steps.add(new Step(1, true));
        } else if (oldLines == 1 && newLines > 1) {
            for (int line = 2; line <= newLines; line++) {
                steps.add(new Step(line, true));
            }
        }
        return steps;
    }

    /** The scheduler delay, in ticks, for playing {@code stepsFor(..).get(index)}. */
    public static long delayForStep(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative; got " + index);
        }
        return index * STEP_TICKS;
    }

    /** Total span, in ticks, from the first step to the clean repaint that ends the cascade. */
    public static long totalTicks(int stepCount) {
        if (stepCount <= 0) {
            return 0L;
        }
        return delayForStep(stepCount - 1) + SETTLE_TICKS;
    }
}
