package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link SlotsLineFlashGuard}'s supersession/invalidation contract
 * (Section 12) -- the integration cases {@link SlotsLineFlashPlan}'s own
 * pure blink-frame test can't reach, since they're about the sequence of
 * events across multiple Paylines inputs rather than one flash's frames.
 */
class SlotsLineFlashGuardTest {

    private static final Object CONFIG_A = new Object();
    private static final Object CONFIG_B = new Object();
    private static final Object MODE_PLAY = new Object();
    private static final Object MODE_INFO = new Object();

    @Test
    void freshGuardHasNoStaleTokenAtGenerationZero() {
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        assertEquals(0L, guard.currentGeneration());
    }

    @Test
    void aTokenCapturedBeforeCancellationGoesStaleAfterIt() {
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long token = guard.supersede();
        assertFalse(guard.isStale(token, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "a token still current must not be stale");

        guard.cancel();

        assertTrue(guard.isStale(token, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "a token invalidated by a later cancellation must be stale, even with everything else unchanged");
    }

    @Test
    void rapidSameDirectionChangesCannotCombinePaths() {
        // Two ADD (or two REMOVE) inputs in a row: each supersede() call
        // must invalidate the previous flash's token before the new one's
        // frames are ever scheduled, so the first flash's colored path can
        // never repaint alongside the second's.
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long firstFlashToken = guard.supersede();
        long secondFlashToken = guard.supersede();

        assertTrue(secondFlashToken != firstFlashToken, "each supersede must mint a new token");
        assertTrue(guard.isStale(firstFlashToken, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "the first (superseded) flash's frames must never paint");
        assertFalse(guard.isStale(secondFlashToken, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "the second (current) flash's frames must still be allowed to paint");
    }

    @Test
    void rapidOppositeDirectionChangesCannotCombinePaths() {
        // An ADD immediately followed by a REMOVE (or vice versa) uses the
        // exact same supersede-then-flash sequence as two same-direction
        // changes -- the guard has no notion of "direction", which is
        // exactly why it can't accidentally let an add-path and a
        // remove-path coexist.
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long addToken = guard.supersede();
        long removeToken = guard.supersede();

        assertTrue(guard.isStale(addToken, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "the superseded add-flash must never paint its green path after a remove supersedes it");
        assertFalse(guard.isStale(removeToken, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY));
    }

    @Test
    void aWrapClearsAnActiveFlashWithoutStartingANewOne() {
        // A wrap cancels/invalidates whatever was active (via cancel(), the
        // same primitive supersede() uses) but -- unlike an ordinary
        // add/remove -- never captures a new token for a single-line flash,
        // since SlotsMachine routes a wrap to the count-based notice instead
        // of flashLineChange. Modeled here as: cancel() runs, no new token
        // is ever captured, so nothing can ever be "current" again except
        // an eventual future flash's own new token.
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long priorFlashToken = guard.supersede();

        guard.cancel(); // the wrap's cancellation -- no accompanying supersede()/new flash

        assertTrue(guard.isStale(priorFlashToken, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "the flash active before the wrap must be invalidated");
        // No token was captured for the wrap itself, so there is nothing
        // that could paint a single-line path for it -- there is simply no
        // valid token in play until the next real flash starts.
    }

    @Test
    void staleCallbacksCannotRepaintAfterCancellation() {
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long token = guard.supersede();
        guard.cancel();
        assertTrue(guard.isStale(token, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY));
    }

    @Test
    void staleCallbacksCannotRepaintAfterTheSessionCloses() {
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long token = guard.supersede();
        assertTrue(guard.isStale(token, true, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_PLAY),
            "closeFlag alone must make any token stale, even the current one");
    }

    @Test
    void aGeometryChangeInvalidatesEvenAMatchingGenerationToken() {
        // Defense in depth: even if two calls somehow shared a generation,
        // a config identity change alone must still mark the token stale --
        // this is what stops a scheduled frame from repainting a path that
        // belonged to a since-replaced reel width/height.
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long token = guard.supersede();
        assertTrue(guard.isStale(token, false, CONFIG_A, CONFIG_B, MODE_PLAY, MODE_PLAY));
    }

    @Test
    void aModeChangeInvalidatesEvenAMatchingGenerationToken() {
        SlotsLineFlashGuard guard = new SlotsLineFlashGuard();
        long token = guard.supersede();
        assertTrue(guard.isStale(token, false, CONFIG_A, CONFIG_A, MODE_PLAY, MODE_INFO));
    }
}
