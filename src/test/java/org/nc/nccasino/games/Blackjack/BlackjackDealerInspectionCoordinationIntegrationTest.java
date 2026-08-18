package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the start-transition dealer
 * inspection's timing coordination with the private per-viewer wager-bar
 * conceal, and for committed-player checkpoint pauses actually reaching the
 * real controller (not just the pure {@link BlackjackDealerInspectionPlan}).
 *
 * <p>Every expected tick below is derived by hand from the real production
 * constants ({@link BlackjackTiming#DEALER_INSPECTION_STEP_TICKS} = 5,
 * {@link BlackjackTiming#DEALER_INSPECTION_SLOWDOWN_EXTRA_TICKS} = 15,
 * {@link BlackjackTiming#WAGER_REVEAL_STEP_TICKS} = 4) and the fixed
 * 18-step {@link BlackjackSlotLayout#dealerUPath()}: 11 top/side legs (55
 * base ticks) land the dealer at the first bottom-row slot (47) well after
 * the conceal's own 36-tick worst-case window, so under real constants the
 * old, buggy "always add the full 36-tick gate to every bottom-row step"
 * behavior would have delayed the whole round by an entirely unnecessary
 * 36 ticks -- these tests assert the exact tighter tick boundaries that
 * only the fixed (minimum-required-shift) coordination satisfies.
 */
class BlackjackDealerInspectionCoordinationIntegrationTest {

    private static BlackjackControllerTestSupport.Harness newTable() {
        return BlackjackControllerTestSupport.newHarness();
    }

    private static Player seatAndCommit(BlackjackControllerTestSupport.Harness h, UUID id, String name, int seatSlot, double wager) {
        Player player = h.seatOnlinePlayer(id, name);
        h.click(player, seatSlot);
        if (wager > 0) {
            h.inventory.commitWagerForTest(player, wager);
        }
        return player;
    }

    // ==================================================================
    // Timing model: top/side overlap, bottom-row gate, no unnecessary gap
    // ==================================================================

    @Test
    void topSideStepsOverlapConcealAndTheBottomRowStartsAtItsNaturalTimeWithNoExtraGap() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            seatAndCommit(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);

            h.inventory.beginStartTransitionForTest();

            // Tick 20: the dealer has already moved three-quarters of the way
            // through the top/side leg (slot 4, the 5th step) while the
            // private door-conceal (36-tick worst-case window) is still
            // wide open -- proving the two run concurrently, not gated.
            h.scheduler.advance(20);
            assertEquals(4, h.inventory.dealerHeadSlotForTest(), "the dealer must already be moving along the top/side leg");
            assertFalse(h.inventory.isGameActiveForTest());

            // Tick 69: one tick before the bottom row's natural (uncoordinated)
            // arrival -- the dealer must still be on the last top/side
            // checkpoint (slot 38), never having touched slot 47 yet.
            h.scheduler.advance(49); // cumulative tick 69
            assertEquals(38, h.inventory.dealerHeadSlotForTest());

            // Tick 70: the bottom row begins here, its true natural time --
            // well after the conceal's own 36-tick window, and not one tick
            // later than necessary (the old bug would have pushed this to 106).
            h.scheduler.advance(1); // cumulative tick 70
            assertEquals(47, h.inventory.dealerHeadSlotForTest(), "the first bottom-row step must fire at its natural, uncoordinated time");

            // Tick 99: the dealer has not yet reached its final in-play slot.
            h.scheduler.advance(29); // cumulative tick 99
            assertFalse(h.inventory.isGameActiveForTest(), "the round must not activate before the dealer truly finishes");

            // Tick 100: the real, coordinated completion -- the old bug would
            // have delayed this all the way to tick 136 (100 + the full,
            // unnecessary 36-tick gate).
            h.scheduler.advance(1); // cumulative tick 100
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
            assertTrue(h.inventory.isGameActiveForTest(), "activation must happen at the real coordinated completion time (100), not the old inflated one (136)");
        }
    }

    @Test
    void occupiedSeatWithNoCommittedWagerAddsNoPauseAndReceivesNoHand() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);
            UUID bobId = UUID.randomUUID();
            seatAndCommit(h, bobId, "Bob", BlackjackSlotLayout.SEAT_SLOTS[1], 0.0); // occupied, never commits

            h.inventory.beginStartTransitionForTest();

            // Identical boundary to the single-committed-player case above --
            // bob's occupied-but-uncommitted seat must contribute zero pause.
            h.scheduler.advance(99);
            assertFalse(h.inventory.isGameActiveForTest());
            h.scheduler.advance(1); // cumulative tick 100
            assertTrue(h.inventory.isGameActiveForTest(), "an uncommitted occupied seat must add no checkpoint pause");

            h.scheduler.advance(200); // let dealing actually finish
            assertTrue(h.inventory.activeHandCardCountForTest(aliceId) > 0, "the committed player must receive a hand");
            assertEquals(0, h.inventory.activeHandCardCountForTest(bobId), "a player without a committed wager must receive no hand");
        }
    }

    @Test
    void multipleCommittedPlayersEachAddTheirOwnCumulativePause() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);
            UUID carolId = UUID.randomUUID();
            seatAndCommit(h, carolId, "Carol", BlackjackSlotLayout.SEAT_SLOTS[2], 10.0);

            h.inventory.beginStartTransitionForTest();

            // Two committed checkpoints (seats 0 and 2) add two full
            // slowdown increments (15 each) on top of the single-committed
            // baseline of 100: 100 + 15 = 115.
            h.scheduler.advance(114);
            assertFalse(h.inventory.isGameActiveForTest());
            h.scheduler.advance(1); // cumulative tick 115
            assertTrue(h.inventory.isGameActiveForTest(), "two committed checkpoints must add exactly two cumulative pauses (115), not one (100) or none (85)");
        }
    }

    // ==================================================================
    // Cancellation and ownership
    // ==================================================================

    @Test
    void individualViewerCloseDoesNotCancelTheSharedInspectionForOthers() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);
            UUID carolId = UUID.randomUUID();
            seatAndCommit(h, carolId, "Carol", BlackjackSlotLayout.SEAT_SLOTS[2], 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(20); // mid-inspection, well before completion

            // Alice's own view closes -- a purely private/per-viewer event.
            // She still has a committed wager, so this rides to result
            // (RIDE_TO_RESULT) rather than being refunded-and-removed -- she
            // stays seated and genuinely gets dealt into this same round, so
            // the dealer inspection's committed-seat checkpoint pause still
            // applies to her seat too, same as if she'd stayed online. That
            // legitimately pushes completion later than a fixed-tick bound
            // calibrated to her being excluded would assume -- poll instead
            // of asserting one hardcoded cumulative tick.
            h.inventory.onViewClosed(alice, h.inventory.viewForTest(aliceId));
            assertTrue(h.inventory.hasSharedAnimationForTest(), "one viewer closing must never cancel the shared dealer inspection");

            for (int i = 0; i < 60 && h.inventory.activeHandCardCountForTest(carolId) == 0; i++) {
                h.scheduler.advance(5);
            }
            assertTrue(h.inventory.isGameActiveForTest(), "the shared inspection must complete on schedule regardless of alice's closed view");
            assertTrue(h.inventory.activeHandCardCountForTest(carolId) > 0, "carol must still be dealt in");
            assertTrue(h.inventory.isSeatedForTest(aliceId), "alice's committed wager must ride to result, not be refunded-and-removed");
            assertTrue(h.inventory.activeHandCardCountForTest(aliceId) > 0, "alice must still be dealt into the very round her wager rode into");
        }
    }

    @Test
    void individualPlayerLeaveDuringTransitionDoesNotCancelInspectionForRemainingPlayers() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = seatAndCommit(h, aliceId, "Alice", aliceSeat, 10.0);
            UUID carolId = UUID.randomUUID();
            seatAndCommit(h, carolId, "Carol", BlackjackSlotLayout.SEAT_SLOTS[2], 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(20);

            // Alice leaves mid-transition (clicking her own occupied seat).
            h.click(alice, aliceSeat);
            assertFalse(h.inventory.isSeatedForTest(aliceId));
            assertTrue(h.inventory.hasSharedAnimationForTest(), "one player leaving must never cancel the shared dealer inspection");

            // The schedule was already baked in when the transition began --
            // alice's own checkpoint pause still counts even though she's
            // since left, so completion is still at 115.
            h.scheduler.advance(95); // cumulative tick 115
            assertTrue(h.inventory.isGameActiveForTest(), "inspection must still reach the dealer's in-play slot for the remaining player");
            assertTrue(h.inventory.activeHandCardCountForTest(carolId) > 0, "carol must still be dealt in after alice's mid-transition leave");
        }
    }

    @Test
    void lastPlayerLeavingDuringTransitionCancelsInspectionAndNoStaleCallbackActivatesTheGame() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = seatAndCommit(h, aliceId, "Alice", aliceSeat, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(20);

            h.click(alice, aliceSeat); // the only seated player leaves -- cancelGame() fires
            assertEquals(0, h.inventory.playerSeatsSizeForTest());
            assertFalse(h.inventory.hasSharedAnimationForTest(), "the table emptying must cancel the shared inspection immediately");
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());

            // Advance well past what would have been the original completion
            // tick (115) -- no stale callback may repaint the dealer or
            // activate the game.
            h.scheduler.advance(300);
            assertFalse(h.inventory.isGameActiveForTest(), "no stale callback may activate the game after the table emptied");
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(), "no stale callback may repaint the dealer's slot");
        }
    }

    @Test
    void resetDuringInspectionInvalidatesStaleCallbacks() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(20);
            assertTrue(h.inventory.hasSharedAnimationForTest());

            h.inventory.resetGameForTest();
            assertFalse(h.inventory.hasSharedAnimationForTest(), "a genuine round reset must cancel the in-flight dealer inspection");
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());

            h.scheduler.advance(300); // past the original completion tick (100)
            assertFalse(h.inventory.isGameActiveForTest(), "no stale callback from the reset round may activate the game");
            assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
        }
    }

    @Test
    void noCrossRoundStaleCallbackCanRepaintOrActivateALaterRound() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);

            // Round 1: alice commits, the transition begins, then gets
            // aborted (and refunded) mid-inspection -- her round-1 dealer
            // callbacks are still sitting in the fake scheduler's queue,
            // targeting round-1's absolute ticks.
            UUID aliceId = UUID.randomUUID();
            seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);
            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(20);
            h.inventory.abortRoundAndRefundForTest("blackjack.shoe-exhausted-refunded");
            assertFalse(h.inventory.hasSharedAnimationForTest());

            // Round 2: a different player, bob, seats and commits; the
            // transition begins again from a fresh generation.
            UUID bobId = UUID.randomUUID();
            seatAndCommit(h, bobId, "Bob", BlackjackSlotLayout.SEAT_SLOTS[1], 10.0);
            h.inventory.beginStartTransitionForTest();

            // Advance far enough that round 1's original (now stale) dealer
            // callbacks -- originally targeting ticks up to ~100 relative to
            // round 1's own start -- would already have fired if the
            // roundGeneration guard didn't stop them, before round 2's own
            // completion tick (100, single committed player) is reached.
            h.scheduler.advance(100);

            assertTrue(h.inventory.isGameActiveForTest(), "round 2 must activate on its own schedule");
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
            assertTrue(h.inventory.activeHandCardCountForTest(bobId) > 0, "round 2's own committed player must be dealt in");
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId), "round 1's aborted player must never receive a hand from a stale round-1 callback");
        }
    }
}
