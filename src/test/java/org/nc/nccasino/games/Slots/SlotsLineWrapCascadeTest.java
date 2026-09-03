package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Paylines 1&lt;-&gt;max wrap cascade's step order, independent of any
 * live Bukkit inventory or scheduler -- exactly what the class's own javadoc
 * says it is built for.
 */
class SlotsLineWrapCascadeTest {

    @Test
    void droppingFromMaxToOneWalksDownwardAndLandsOnLineOneAdded() {
        List<SlotsLineWrapCascade.Step> steps = SlotsLineWrapCascade.stepsFor(9, 1);
        assertEquals(9, steps.size());
        // 9, 8, ..., 2 as removed, then 1 as added.
        for (int i = 0; i < 8; i++) {
            SlotsLineWrapCascade.Step step = steps.get(i);
            assertEquals(9 - i, step.lineNumber());
            assertTrue(!step.added(), "line " + step.lineNumber() + " must be shown as removed");
        }
        SlotsLineWrapCascade.Step last = steps.get(8);
        assertEquals(1, last.lineNumber());
        assertTrue(last.added(), "the cascade must land on line 1 shown as added");
    }

    @Test
    void climbingFromOneToMaxWalksUpwardAddingEachNewLine() {
        List<SlotsLineWrapCascade.Step> steps = SlotsLineWrapCascade.stepsFor(1, 9);
        assertEquals(8, steps.size());
        // 2, 3, ..., 9 as added; line 1 was already active and is not re-announced.
        for (int i = 0; i < steps.size(); i++) {
            SlotsLineWrapCascade.Step step = steps.get(i);
            assertEquals(i + 2, step.lineNumber());
            assertTrue(step.added(), "line " + step.lineNumber() + " must be shown as added");
        }
    }

    @Test
    void aNonWrapPairYieldsNoSteps() {
        assertTrue(SlotsLineWrapCascade.stepsFor(3, 5).isEmpty());
        assertTrue(SlotsLineWrapCascade.stepsFor(5, 3).isEmpty());
        assertTrue(SlotsLineWrapCascade.stepsFor(1, 1).isEmpty());
        assertTrue(SlotsLineWrapCascade.stepsFor(9, 9).isEmpty());
    }

    @Test
    void dropToOneFromASmallerMaxOnlyWalksTheLinesThatExisted() {
        List<SlotsLineWrapCascade.Step> steps = SlotsLineWrapCascade.stepsFor(3, 1);
        assertEquals(3, steps.size());
        assertEquals(3, steps.get(0).lineNumber());
        assertEquals(2, steps.get(1).lineNumber());
        assertEquals(1, steps.get(2).lineNumber());
        assertTrue(!steps.get(0).added());
        assertTrue(!steps.get(1).added());
        assertTrue(steps.get(2).added());
    }

    @Test
    void delayForStepIsZeroBasedAndEvenlySpaced() {
        assertEquals(0L, SlotsLineWrapCascade.delayForStep(0));
        assertEquals(SlotsLineWrapCascade.STEP_TICKS, SlotsLineWrapCascade.delayForStep(1));
        assertEquals(SlotsLineWrapCascade.STEP_TICKS * 4, SlotsLineWrapCascade.delayForStep(4));
    }

    @Test
    void delayForStepRejectsANegativeIndex() {
        try {
            SlotsLineWrapCascade.delayForStep(-1);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    void totalTicksIsZeroForNoStepsAndIncludesTheFinalSettlePause() {
        assertEquals(0L, SlotsLineWrapCascade.totalTicks(0));
        List<SlotsLineWrapCascade.Step> steps = SlotsLineWrapCascade.stepsFor(9, 1);
        long expected = SlotsLineWrapCascade.delayForStep(steps.size() - 1) + SlotsLineWrapCascade.SETTLE_TICKS;
        assertEquals(expected, SlotsLineWrapCascade.totalTicks(steps.size()));
    }

    @Test
    void everyStepHasAStrictlyLaterDelayThanTheOneBeforeIt() {
        List<SlotsLineWrapCascade.Step> steps = SlotsLineWrapCascade.stepsFor(9, 1);
        long previous = -1L;
        for (int i = 0; i < steps.size(); i++) {
            long delay = SlotsLineWrapCascade.delayForStep(i);
            assertTrue(delay > previous, "step " + i + " must be scheduled strictly after the previous one");
            previous = delay;
        }
    }
}
