package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Exercises BlackjackAnimationRun's staleness/cancellation-scope logic
 * directly, without a live Bukkit server -- {@code isStale} is a pure
 * comparison against caller-supplied values (never reads live
 * BlackjackInventory state itself), and {@code cancel()}/{@code
 * isCancelled()} work correctly with no Bukkit task ever attached, which is
 * exactly the state every run is in during this phase (nothing schedules a
 * real task yet). This mirrors how BlackjackInventory itself has no direct
 * test -- the Bukkit-touching glue (actually scheduling/rendering a step)
 * isn't exercised here, only the plain-data validation logic.
 */
class BlackjackAnimationRunTest {

    @Test
    void sharedRunHasNullViewerId() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(null, 1L, 0, BlackjackFrame.Phase.ACTIVE);
        assertTrue(run.isShared());
        assertFalse(run.isPrivate());
    }

    @Test
    void privateRunHasANonNullViewerId() {
        UUID viewer = UUID.randomUUID();
        BlackjackAnimationRun run = new BlackjackAnimationRun(viewer, 1L, 0, BlackjackFrame.Phase.LOBBY);
        assertTrue(run.isPrivate());
        assertFalse(run.isShared());
    }

    // --- isStale: every one of roundGeneration/animationGeneration/phase must match, plus not cancelled ---

    @Test
    void notStaleWhenEverythingStillMatches() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(UUID.randomUUID(), 5L, 2, BlackjackFrame.Phase.COUNTDOWN);
        assertFalse(run.isStale(5L, 2, BlackjackFrame.Phase.COUNTDOWN));
    }

    @Test
    void staleWhenRoundGenerationHasMovedOn() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(UUID.randomUUID(), 5L, 2, BlackjackFrame.Phase.COUNTDOWN);
        assertTrue(run.isStale(6L, 2, BlackjackFrame.Phase.COUNTDOWN));
    }

    @Test
    void staleWhenAnimationGenerationHasMovedOn() {
        // Mirrors the "delayed callback from an earlier Hit superseded by a
        // later action on the same hand" scenario for hand callbacks --
        // here, an earlier guidance cycle superseded by a fresher one for
        // the same viewer.
        BlackjackAnimationRun run = new BlackjackAnimationRun(UUID.randomUUID(), 5L, 2, BlackjackFrame.Phase.COUNTDOWN);
        assertTrue(run.isStale(5L, 3, BlackjackFrame.Phase.COUNTDOWN));
    }

    @Test
    void staleWhenThePhaseNoLongerMatchesWhatWasExpected() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(UUID.randomUUID(), 5L, 2, BlackjackFrame.Phase.COUNTDOWN);
        assertTrue(run.isStale(5L, 2, BlackjackFrame.Phase.ACTIVE));
    }

    @Test
    void staleAfterExplicitCancelEvenIfEverythingElseStillMatches() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(UUID.randomUUID(), 5L, 2, BlackjackFrame.Phase.COUNTDOWN);
        run.cancel();
        assertTrue(run.isStale(5L, 2, BlackjackFrame.Phase.COUNTDOWN));
    }

    // --- cancel()/isCancelled() work correctly with no Bukkit task ever attached ---

    @Test
    void cancelIsSafeWithoutAnAttachedTask() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(null, 1L, 0, BlackjackFrame.Phase.ACTIVE);
        assertFalse(run.isCancelled());
        run.cancel();
        assertTrue(run.isCancelled());
    }

    @Test
    void cancelIsIdempotent() {
        BlackjackAnimationRun run = new BlackjackAnimationRun(null, 1L, 0, BlackjackFrame.Phase.ACTIVE);
        run.cancel();
        run.cancel();
        assertTrue(run.isCancelled());
    }

    // --- captured identity is stable/readable ---

    @Test
    void capturedIdentityIsExposedExactlyAsConstructed() {
        UUID viewer = UUID.randomUUID();
        BlackjackAnimationRun run = new BlackjackAnimationRun(viewer, 42L, 7, BlackjackFrame.Phase.INSURANCE);
        org.junit.jupiter.api.Assertions.assertEquals(viewer, run.getViewerId());
        org.junit.jupiter.api.Assertions.assertEquals(42L, run.getRoundGeneration());
        org.junit.jupiter.api.Assertions.assertEquals(7, run.getAnimationGeneration());
        org.junit.jupiter.api.Assertions.assertEquals(BlackjackFrame.Phase.INSURANCE, run.getExpectedPhase());
    }
}
