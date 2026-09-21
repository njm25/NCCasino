package org.nc.nccasino.games.Slots;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the actual production orchestration sequence {@link SlotsMachine}
 * delegates to -- not just {@link SlotsLastWinState} in isolation -- for the
 * post-audit correction Section 1 fix: settling a new win must never
 * immediately cancel the very animation it just started.
 */
class SlotsWinMeterAnimationTest {

    @Test
    void settlementProducesAnInitialZeroFrameThenMonotonicTicksToExactTarget() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(250);

        // Reproduces the exact bug: the caller must be able to schedule and
        // run the first tick without anything having cancelled the state in
        // between settle() and the first tick() call.
        assertEquals(0L, anim.displayedWin(), "settle() must render the initial zero frame, not the full value");
        assertTrue(anim.isAnimating());

        long last = -1L;
        long shown;
        int guard = 0;
        do {
            shown = anim.tick(generation, 11);
            assertTrue(shown >= 0, "a tick for the current generation must never return the stale sentinel");
            assertTrue(shown > last, "each tick must move strictly forward");
            last = shown;
            guard++;
            assertTrue(guard < 1000, "runaway animation -- did not converge");
        } while (anim.isAnimating());

        assertEquals(250L, shown, "the animation must land exactly on the payout");
        assertEquals(250L, anim.displayedWin());
        assertEquals(250L, anim.lastCompletedWin());
    }

    @Test
    void startingTheSchedulerDoesNotCancelTheStateItIsMeantToAnimate() {
        // This is the literal reproduction of the reported defect: settle()
        // followed immediately by whatever the caller does to "start"
        // scheduling ticks must not itself wipe out the just-started
        // animation before a single tick runs.
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(100);

        // Simulate SlotsMachine.animateWinMeter's own setup work (computing
        // steps/increment) happening between settle() and the first tick --
        // none of that must touch state.
        long steps = SlotsWinMeterMath.steps(SlotsTiming.WIN_METER_MAX_TICKS, SlotsTiming.WIN_METER_STEP_TICKS);
        long increment = SlotsWinMeterMath.increment(100, steps);

        assertTrue(anim.isAnimating(), "setup work alone must not cancel the animation");
        long firstTick = anim.tick(generation, increment);
        assertTrue(firstTick > 0, "the first tick must actually advance the meter, not see a already-cancelled state");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
    void cancellingAtEveryIntermediatePointSnapsToTheTarget(long ticksBeforeCancel) {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(777);
        for (long i = 0; i < ticksBeforeCancel; i++) {
            anim.tick(generation, 13);
            if (!anim.isAnimating()) {
                break;
            }
        }
        anim.interrupt();
        assertFalse(anim.isAnimating());
        assertEquals(777L, anim.displayedWin());
        assertEquals(777L, anim.lastCompletedWin());
    }

    @Test
    void startingALaterRealSpinPreservesThePriorExactCompletedPayout() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long firstGeneration = anim.settle(50);
        // First spin's animation runs to completion naturally.
        long shown;
        do {
            shown = anim.tick(firstGeneration, 7);
        } while (anim.isAnimating());
        assertEquals(50L, shown);
        assertEquals(50L, anim.lastCompletedWin());

        // A later real spin (a genuine interruption, then a new settlement)
        // must never resurrect or corrupt the value the previous spin
        // finished on -- it simply becomes the new authoritative result.
        anim.interrupt();
        assertEquals(50L, anim.lastCompletedWin(), "interrupting after natural completion must not alter the completed value");

        long secondGeneration = anim.settle(0);
        assertEquals(0L, anim.lastCompletedWin());
        assertEquals(0L, anim.displayedWin());
        assertFalse(anim.isAnimating());
        assertTrue(secondGeneration > firstGeneration);
    }

    @Test
    void staleTicksAfterCompletionAreIgnoredAndNeverYieldARenderableNegativeValue() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(20);
        while (anim.isAnimating()) {
            anim.tick(generation, 6);
        }
        // Extra ticks against the same (now-finished) generation.
        assertEquals(-1L, anim.tick(generation, 6));
        assertEquals(-1L, anim.tick(generation, 6));
        assertEquals(20L, anim.displayedWin(), "a stale/extra tick must never move the displayed value");
    }

    @Test
    void aTickPresentingASupersededGenerationIsIgnoredEvenWhileANewAnimationIsRunning() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long staleGeneration = anim.settle(999);
        // A later settlement supersedes the first before it ever ticks --
        // exactly the race a stale scheduled Bukkit task could hit.
        long currentGeneration = anim.settle(40);

        long staleResult = anim.tick(staleGeneration, 5);
        assertEquals(-1L, staleResult, "a tick from a superseded generation must never repaint over the newer result");
        assertEquals(0L, anim.displayedWin(), "the stale tick must not have touched the current animation's display");

        long currentResult = anim.tick(currentGeneration, 5);
        assertTrue(currentResult >= 0);
        assertEquals(currentResult, anim.displayedWin());
    }

    @Test
    void zeroPayoutSchedulesNoAnimation() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        anim.settle(0);
        assertFalse(anim.isAnimating(), "a loss has nothing to animate");
        assertEquals(0L, anim.displayedWin());
        assertEquals(0L, anim.lastCompletedWin());
    }

    @Test
    void retryNeverRestartsTheCountUp() {
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(60);
        // Animation runs partway, exactly as it would while delivery is
        // being retried in the background.
        anim.tick(generation, 6);
        anim.tick(generation, 6);
        assertTrue(anim.isAnimating());

        anim.retrySettled(60);

        assertFalse(anim.isAnimating(), "a settlement retry must never leave (or start) a count-up running");
        assertEquals(60L, anim.displayedWin(), "a retry must snap straight to the payout, never re-animate from zero");
        assertEquals(60L, anim.lastCompletedWin());

        // And a tick still in flight from the pre-retry animation must be
        // treated as stale, not allowed to resume the count-up.
        assertEquals(-1L, anim.tick(generation, 6));
        assertEquals(60L, anim.displayedWin());
    }

    @Test
    void retryAfterPartialDeliverySnapsToTheFullAwardNeverTheOutstandingRemainder() {
        // Reproduces the exact production defect: SlotsMachine used to pass
        // controller.pendingPayoutAmount() (reduced to the remainder by an
        // earlier partial delivery) into retrySettled(), not
        // controller.lastWinAmount() (the full award). This pins the fixed
        // call: retrySettled() must always be given the full award, and Last
        // Win must never be overwritten with the smaller remainder.
        long fullAward = 250L;
        long remainderStillOwed = 90L; // e.g. after a 160-unit partial delivery

        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(fullAward);
        anim.tick(generation, 13);
        anim.tick(generation, 13);
        assertTrue(anim.isAnimating());

        // The fixed call site: retrySettled(controller.lastWinAmount()), NOT
        // retrySettled(controller.pendingPayoutAmount()).
        anim.retrySettled(fullAward);

        assertFalse(anim.isAnimating(), "a retry must never leave a count-up running");
        assertEquals(fullAward, anim.displayedWin(),
            "Last Win must show the full award, never the outstanding remainder (" + remainderStillOwed + ")");
        assertEquals(fullAward, anim.lastCompletedWin());
        assertTrue(anim.displayedWin() != remainderStillOwed);
    }

    // ---- win-meter UI lifecycle fix: exact production sequences ----------
    //
    // SlotsMachine.cancelWinMeterTask() calls exactly SlotsWinMeterAnimation
    // .interrupt() (plus stopping the Bukkit task, which has no pure-model
    // equivalent). Both fixed call sites -- handleSpin's accepted-spin
    // branch, and the modal view transition in switchView() -- now call it
    // BEFORE painting any controls, which is the actual defect: the pure
    // transition below is exactly what production calls, in the order
    // production now calls it.

    @Test
    void midCountInterruptionByAnAcceptedSpinSnapsBeforeTheNextControlRenderSamplesIt() {
        // Reproduces Section 1's exact defect scenario: a prior spin's Last
        // Win is still mid-count when a new spin is accepted. The fix's
        // required ordering is "snap, THEN render" -- so the value any
        // control render samples immediately after the snap must already be
        // the exact target, never the partial amount that was on screen an
        // instant before.
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(250);
        anim.tick(generation, 11);
        anim.tick(generation, 11);
        long midCount = anim.displayedWin();
        assertTrue(midCount > 0 && midCount < 250, "test setup: must actually be mid-count, was " + midCount);

        // The new spin is accepted -- cancelWinMeterTask()'s state-mutating
        // half runs here, before renderControls() (i.e. before anything
        // reads displayedWin() for painting).
        anim.interrupt();

        // This is exactly what the next control render (refreshSpinControl(),
        // called from renderControls()) samples -- it must be the exact
        // completed target, never the mid-count value observed above.
        long sampledForRender = anim.displayedWin();
        assertEquals(250L, sampledForRender);
        assertTrue(sampledForRender != midCount);
        assertFalse(anim.isAnimating());
    }

    @Test
    void midCountThenOpeningAModalViewSnapsToExactAuthoritativeStateAndIgnoresTheStaleTick() {
        // Reproduces Section 2's exact defect scenario: opening a modal view
        // must stop and snap the meter before that view's controls are drawn
        // (the Spin lever's Last Result lore is repainted by the same
        // renderControls() pass), and any tick already scheduled from before
        // the view change must be inert afterward.
        SlotsWinMeterAnimation anim = new SlotsWinMeterAnimation();
        long generation = anim.settle(180);
        anim.tick(generation, 9);
        assertTrue(anim.isAnimating());

        // switchView()'s cancelLineFlashTask()/cancelWinMeterTask() half.
        anim.interrupt();
        assertEquals(180L, anim.displayedWin(), "opening a modal view must expose the exact authoritative payout");
        assertEquals(180L, anim.lastCompletedWin());
        assertFalse(anim.isAnimating());

        // A tick scheduled before the mode change, delivered late (the
        // in-flight Bukkit task the generation guard in SlotsMachine also
        // defends against) -- must be ignored, never resume the count-up or
        // move the display.
        assertEquals(-1L, anim.tick(generation, 9));
        assertEquals(180L, anim.displayedWin());

        // Returning to Game View later exposes the exact same completed
        // payout -- nothing about the modal view altered it.
        assertEquals(180L, anim.displayedWin());
        assertEquals(180L, anim.lastCompletedWin());
    }
}
