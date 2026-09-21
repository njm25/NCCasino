package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

import java.util.List;

/**
 * Pure blink-frame schedule for the Paylines add/remove flash, extracted so
 * the alternating colored/clean sequence can be unit-tested without a live
 * Bukkit inventory or scheduler.
 *
 * <p>{@link SlotsMachine} plays one {@link Frame} every {@link #STEP_TICKS}
 * ticks, in order, painting the changed line's exact path on {@code COLORED}
 * and repainting the ordinary clean canvas on {@code CLEAN} -- never holding
 * the colored path continuously, and always finishing on {@code CLEAN}.
 */
public final class SlotsLineFlashPlan {

    private SlotsLineFlashPlan() {
    }

    public enum Frame { COLORED, CLEAN }

    /** Two colored flashes separated and followed by clean frames -- perceptibly blinking, never a single hold. */
    private static final List<Frame> FRAMES =
        List.of(Frame.COLORED, Frame.CLEAN, Frame.COLORED, Frame.CLEAN);

    /** Ticks between successive frames. Four frames at this spacing span 15 ticks, close to the prior 20-tick flash. */
    public static final long STEP_TICKS = 5L;

    /** @return the ordered blink frames, always starting COLORED and ending CLEAN. */
    public static List<Frame> frames() {
        return FRAMES;
    }

    /** The scheduler delay, in ticks, for playing {@code frames().get(index)}. */
    public static long delayForFrame(int index) {
        if (index < 0 || index >= FRAMES.size()) {
            throw new IllegalArgumentException("index must be in [0, " + FRAMES.size() + "); got " + index);
        }
        return index * STEP_TICKS;
    }

    /** Total span, in ticks, from the first frame to the last. */
    public static long totalTicks() {
        return delayForFrame(FRAMES.size() - 1);
    }

    /**
     * The colored pane material for a Paylines add/remove flash -- green for
     * an added line, black for a removed one (the same two materials the
     * Paylines and Per-Line Wager controls themselves use). Never an
     * inherently-glinting material; the caller must never pass this to
     * {@code setGlowingItem}.
     */
    public static Material materialForChange(boolean added) {
        return added ? Material.GREEN_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
    }
}
