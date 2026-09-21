package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the Last Win lifecycle contract (redesign audit Section 1). */
class SlotsLastWinStateTest {

    @Test
    void initialStateIsNoSpinsYet() {
        SlotsLastWinState state = new SlotsLastWinState();
        assertEquals(-1L, state.displayedWin());
        assertEquals(-1L, state.lastCompletedWin());
        assertFalse(state.isAnimating());
    }

    @Test
    void settlingToZeroShowsImmediatelyWithNoAnimation() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(0L);
        assertEquals(0L, state.displayedWin());
        assertEquals(0L, state.lastCompletedWin());
        assertFalse(state.isAnimating());
    }

    @Test
    void settlingToAPositivePayoutStartsAtZeroNeverAtTheFullAmount() {
        // The exact defect: the old code rendered the full amount before
        // the count-up started, so the very next tick dropped it back down.
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(500L);
        assertEquals(0L, state.displayedWin(), "must start the count-up at zero, never show the full amount first");
        assertEquals(500L, state.lastCompletedWin(), "the authoritative result is fixed immediately");
        assertTrue(state.isAnimating());
    }

    @Test
    void tickingNeverMovesBackwardAndLandsExactlyOnTheTarget() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(100L);
        long last = state.displayedWin();
        while (state.isAnimating()) {
            long shown = state.tick(7L);
            assertTrue(shown >= last, "displayed value must never move backward mid-animation");
            last = shown;
        }
        assertEquals(100L, state.displayedWin());
        assertEquals(100L, last);
    }

    @Test
    void cancellingMidAnimationSnapsToTheExactAuthoritativeValueNeverAPartialOne() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(1000L);
        state.tick(30L); // partial progress, well short of 1000
        assertTrue(state.displayedWin() < 1000L, "test setup must actually be mid-animation");

        state.cancelAnimation();

        assertEquals(1000L, state.displayedWin(), "cancellation must leave the exact target, never a partial amount");
        assertFalse(state.isAnimating());
    }

    @Test
    void cancellingAtEveryIntermediateStepLeavesTheExactTarget() {
        long target = 250L;
        long increment = 11L; // does not divide 250 evenly, so every intermediate step is genuinely partial
        int totalSteps = (int) SlotsWinMeterMath.ticksNeeded(target, increment);

        for (int cancelAfter = 1; cancelAfter <= totalSteps; cancelAfter++) {
            SlotsLastWinState state = new SlotsLastWinState();
            state.settle(target);
            for (int step = 0; step < cancelAfter; step++) {
                state.tick(increment);
            }
            state.cancelAnimation();
            assertEquals(target, state.displayedWin(),
                "cancelling after " + cancelAfter + " of " + totalSteps + " steps must show the exact target");
            assertFalse(state.isAnimating());
        }
    }

    @Test
    void cancellingWhenNothingIsAnimatingIsASafeNoOp() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(0L);
        state.cancelAnimation();
        assertEquals(0L, state.displayedWin());

        state.settle(42L);
        while (state.isAnimating()) {
            state.tick(42L);
        }
        long afterCompletion = state.displayedWin();
        state.cancelAnimation();
        assertEquals(afterCompletion, state.displayedWin(), "cancelling after natural completion must not change anything");
    }

    @Test
    void aSecondSpinPreservesTheFirstCompletedPayoutUntilItSettles() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(300L);
        while (state.isAnimating()) {
            state.tick(300L);
        }
        assertEquals(300L, state.displayedWin());

        // A new spin starts: nothing about SlotsLastWinState changes on its
        // own (SlotsMachine only calls cancelAnimation()/settle() at the
        // real trigger points), and cancelling an already-finished
        // animation is a no-op that preserves the prior result exactly.
        state.cancelAnimation();
        assertEquals(300L, state.displayedWin(), "starting a later spin must preserve the preceding completed payout");

        // Only once the second spin actually settles does the value change.
        state.settle(75L);
        assertEquals(75L, state.lastCompletedWin());
    }

    @Test
    void retrySettledNeverAnimatesAndNeverJumpsBackward() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(640L);
        while (state.isAnimating()) {
            state.tick(640L);
        }
        assertEquals(640L, state.displayedWin());

        // A settlement retry for the same already-fixed result must snap,
        // not re-animate -- re-animating would visibly drop the display
        // back to zero and count back up to the number it already showed.
        state.retrySettled(640L);
        assertEquals(640L, state.displayedWin());
        assertFalse(state.isAnimating());
    }

    @Test
    void retrySettledFromAMidAnimationStateAlsoNeverAnimates() {
        SlotsLastWinState state = new SlotsLastWinState();
        state.settle(500L);
        state.tick(10L); // interrupted mid-count, as a genuinely failed-then-retried settlement might be
        state.retrySettled(500L);
        assertEquals(500L, state.displayedWin());
        assertFalse(state.isAnimating());
    }
}
