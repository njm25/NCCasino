package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsLineFlashPlanTest {

    @Test
    void framesStartColoredAndEndClean() {
        List<SlotsLineFlashPlan.Frame> frames = SlotsLineFlashPlan.frames();
        assertEquals(SlotsLineFlashPlan.Frame.COLORED, frames.get(0));
        assertEquals(SlotsLineFlashPlan.Frame.CLEAN, frames.get(frames.size() - 1));
    }

    @Test
    void framesAlternateBetweenColoredAndClean() {
        List<SlotsLineFlashPlan.Frame> frames = SlotsLineFlashPlan.frames();
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i) != frames.get(i - 1),
                "frame " + i + " must differ from the previous frame to read as a blink, not a hold");
        }
    }

    @Test
    void sequenceHasSeveralClearlyPerceptiblePhases() {
        // At least two of each so a viewer perceives blinking motion, not a single flash.
        long coloredCount = SlotsLineFlashPlan.frames().stream().filter(f -> f == SlotsLineFlashPlan.Frame.COLORED).count();
        long cleanCount = SlotsLineFlashPlan.frames().stream().filter(f -> f == SlotsLineFlashPlan.Frame.CLEAN).count();
        assertTrue(coloredCount >= 2, "expected at least two colored phases");
        assertTrue(cleanCount >= 2, "expected at least two clean phases");
    }

    @Test
    void delaysAreEvenlySpacedByStepTicks() {
        List<SlotsLineFlashPlan.Frame> frames = SlotsLineFlashPlan.frames();
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(i * SlotsLineFlashPlan.STEP_TICKS, SlotsLineFlashPlan.delayForFrame(i));
        }
    }

    @Test
    void totalTicksIsCloseToThePriorTwentyTickFlash() {
        assertTrue(SlotsLineFlashPlan.totalTicks() > 0);
        assertTrue(SlotsLineFlashPlan.totalTicks() <= 25L,
            "opening animation is a separate concern -- this flash must stay close to the ~20-tick duration");
    }

    @Test
    void delayForFrameRejectsOutOfRangeIndex() {
        assertThrows(IllegalArgumentException.class, () -> SlotsLineFlashPlan.delayForFrame(-1));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsLineFlashPlan.delayForFrame(SlotsLineFlashPlan.frames().size()));
    }

    @Test
    void addUsesNormalGreenNeverLime() {
        assertEquals(Material.GREEN_STAINED_GLASS_PANE, SlotsLineFlashPlan.materialForChange(true));
        assertTrue(SlotsLineFlashPlan.materialForChange(true) != Material.LIME_STAINED_GLASS_PANE);
    }

    @Test
    void removeUsesBlack() {
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, SlotsLineFlashPlan.materialForChange(false));
    }
}
