package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the start-transition dealer
 * slide: a smooth, uninterrupted straight-down walk with no per-seat pauses,
 * and its timing coordination with the private per-viewer wager-bar
 * conceal.
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

    /** Records dealerHeadSlot once per tick, collapsing consecutive repeats, up to {@code maxTicks} or until the slide completes. */
    private static List<Integer> recordVisitedSlots(BlackjackControllerTestSupport.Harness h, int maxTicks) {
        List<Integer> visited = new ArrayList<>();
        visited.add(h.inventory.dealerHeadSlotForTest());
        for (int i = 0; i < maxTicks && h.inventory.dealerHeadSlotForTest() != BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT; i++) {
            h.scheduler.advance(1);
            int slot = h.inventory.dealerHeadSlotForTest();
            if (visited.get(visited.size() - 1) != slot) {
                visited.add(slot);
            }
        }
        return visited;
    }

    // ==================================================================
    // Straight slide: no stops, ends at the in-play head, activates promptly
    // ==================================================================

    @Test
    void dealerSlidesStraightDownTheStartTransitionPathWithNoStops() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            seatAndCommit(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);

            h.inventory.beginStartTransitionForTest();

            List<Integer> visited = recordVisitedSlots(h, 100);
            assertEquals(BlackjackSlotLayout.dealerStartTransitionPath(), visited,
                "the dealer must visit exactly the straight-down path, in order, with no extra stops");
        }
    }

    @Test
    void slideCompletesAndActivatesTheGamePromptly() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            seatAndCommit(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);

            h.inventory.beginStartTransitionForTest();

            for (int i = 0; i < 60 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }
            assertTrue(h.inventory.isGameActiveForTest(), "the round must activate shortly after the slide completes");
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
        }
    }

    @Test
    void occupiedSeatWithNoCommittedWagerReceivesNoHand() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            seatAndCommit(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0], 10.0);
            UUID bobId = UUID.randomUUID();
            seatAndCommit(h, bobId, "Bob", BlackjackSlotLayout.SEAT_SLOTS[1], 0.0); // occupied, never commits

            h.inventory.beginStartTransitionForTest();

            h.scheduler.advance(200); // let the slide and dealing both finish
            assertTrue(h.inventory.activeHandCardCountForTest(aliceId) > 0, "the committed player must receive a hand");
            assertEquals(0, h.inventory.activeHandCardCountForTest(bobId), "a player without a committed wager must receive no hand");
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
            h.scheduler.advance(10); // mid-inspection, well before completion

            // Alice's own view closes -- a purely private/per-viewer event.
            // She still has a committed wager, so this rides to result
            // (RIDE_TO_RESULT) rather than being refunded-and-removed -- she
            // stays seated and genuinely gets dealt into this same round.
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
            h.scheduler.advance(10);

            // Alice leaves mid-transition (clicking her own occupied seat).
            h.click(alice, aliceSeat);
            assertFalse(h.inventory.isSeatedForTest(aliceId));
            assertTrue(h.inventory.hasSharedAnimationForTest(), "one player leaving must never cancel the shared dealer inspection");

            for (int i = 0; i < 60 && h.inventory.activeHandCardCountForTest(carolId) == 0; i++) {
                h.scheduler.advance(5);
            }
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
            h.scheduler.advance(10);

            h.click(alice, aliceSeat); // the only seated player leaves -- cancelGame() fires
            assertEquals(0, h.inventory.playerSeatsSizeForTest());
            // cancelGame() immediately starts its own round-end animation (a
            // LOBBY-phase shared animation) -- what must be gone is the
            // dealer inspection specifically, not shared animations
            // altogether. The dealer/deck deliberately stay right where
            // they are (already at the in-play slot -- the inspection had
            // already finished by this point) until every card has
            // returned, so this must NOT be an immediate teleport back to
            // the lobby.
            assertNotEquals(BlackjackFrame.Phase.START_TRANSITION, h.inventory.sharedAnimationPhaseForTest(),
                "the table emptying must cancel the shared inspection immediately");
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the dealer must not teleport back to the lobby -- it only walks back up after cards finish returning");

            // Advance well past what would have been the original completion,
            // and past the full round-end animation chain -- no stale
            // callback may repaint the dealer or activate the game, and the
            // dealer must have finished its own walk back up to the lobby.
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
            h.scheduler.advance(10);
            assertTrue(h.inventory.hasSharedAnimationForTest());

            h.inventory.resetGameForTest();
            // resetGame() immediately starts its own round-end animation --
            // see the identical comment on lastPlayerLeavingDuringTransition...
            // above: the dealer/deck deliberately stay put until every card
            // has returned, so this must NOT be an immediate teleport.
            assertNotEquals(BlackjackFrame.Phase.START_TRANSITION, h.inventory.sharedAnimationPhaseForTest(),
                "a genuine round reset must cancel the in-flight dealer inspection");
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest(),
                "the dealer must not teleport back to the lobby -- it only walks back up after cards finish returning");

            h.scheduler.advance(300); // past the original completion time, and the full round-end animation chain
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
            h.scheduler.advance(10);
            h.inventory.abortRoundAndRefundForTest("blackjack.shoe-exhausted-refunded");
            // abortRoundAndRefund's resetGame() immediately starts its own
            // reset sweep -- see the identical comment further up this file.
            assertNotEquals(BlackjackFrame.Phase.START_TRANSITION, h.inventory.sharedAnimationPhaseForTest());

            // Round 2: a different player, bob, seats and commits; the
            // transition begins again from a fresh generation.
            UUID bobId = UUID.randomUUID();
            seatAndCommit(h, bobId, "Bob", BlackjackSlotLayout.SEAT_SLOTS[1], 10.0);
            h.inventory.beginStartTransitionForTest();

            for (int i = 0; i < 60 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }
            h.scheduler.advance(40); // let the first card's own deck-flight + flip actually land -- gameActive flips before any card data does

            assertTrue(h.inventory.isGameActiveForTest(), "round 2 must activate on its own schedule");
            assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, h.inventory.dealerHeadSlotForTest());
            assertTrue(h.inventory.activeHandCardCountForTest(bobId) > 0, "round 2's own committed player must be dealt in");
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId), "round 1's aborted player must never receive a hand from a stale round-1 callback");
        }
    }
}
