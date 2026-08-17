package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * Controller-level regression coverage for the seat-leave wager-bar conceal:
 * before this fix, {@code handleLeaveChair} (the own-head-click leave that
 * keeps the GUI open) cleared seat/selection state but never repainted the
 * bottom bar, leaving a leaving player staring at a stale seated wager bar
 * (chips/Undo/All In/door) until they happened to close and reopen the
 * inventory. {@link BlackjackWagerRevealPlan} already guarantees reveal/
 * conceal are exact structural mirrors (see its own test); this suite checks
 * that {@code startWagerBarConceal} is actually wired to every seat-leave
 * path that needs it, is cancellation-safe under rapid interaction, and
 * never regresses the already-completed selection/guidance/economics
 * invariants.
 */
class BlackjackWagerBarTransitionIntegrationTest {

    /** Full reveal or conceal pass: 8 frame transitions (CLOSED to OPEN, one tick per frame), plus slack for the chained follow-up call. */
    private static final long FULL_TRANSITION_TICKS = (BlackjackWagerRevealPlan.OPEN - BlackjackWagerRevealPlan.CLOSED) * BlackjackTiming.WAGER_REVEAL_STEP_TICKS + 2;

    private static BlackjackControllerTestSupport.Harness newTable() {
        return BlackjackControllerTestSupport.newHarness();
    }

    private static Player openUnseated(BlackjackControllerTestSupport.Harness h, UUID id, String name) {
        return h.seatOnlinePlayer(id, name);
    }

    private static Inventory viewInv(BlackjackControllerTestSupport.Harness h, Player p) {
        return h.inventory.viewForTest(p.getUniqueId()).getInventory();
    }

    private static void assertCanonicalUnseatedBar(Inventory inv) {
        assertEquals(Material.SPRUCE_DOOR, inv.getItem(BlackjackSlotLayout.UNSEATED_EXIT_SLOT).getType(), "45 must be the unseated door");
        assertEquals(Material.BROWN_STAINED_GLASS_PANE, inv.getItem(BlackjackSlotLayout.UNSEATED_EDGE_GLASS_SLOT).getType(), "46 must be the brown edge glass");
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT + 2; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, inv.getItem(slot).getType(), "slot " + slot + " must be plain background once unseated");
        }
    }

    private static void assertCanonicalSeatedBar(Inventory inv) {
        assertEquals(Material.BARRIER, inv.getItem(BlackjackSlotLayout.UNDO_ALL_SLOT).getType());
        assertEquals(Material.WIND_CHARGE, inv.getItem(BlackjackSlotLayout.UNDO_LAST_SLOT).getType());
        for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
            assertEquals(Material.EMERALD, inv.getItem(slot).getType(), "chip slot " + slot);
        }
        assertEquals(Material.SNIFFER_EGG, inv.getItem(BlackjackSlotLayout.ALL_IN_SLOT).getType());
        assertEquals(Material.SPRUCE_DOOR, inv.getItem(BlackjackSlotLayout.PREGAME_EXIT_SLOT).getType());
    }

    private static List<Card> flatSevenStack(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, Rank.SEVEN));
        }
        return cards;
    }

    // ==================================================================
    // Ordinary lifecycle
    // ==================================================================

    @Test
    void sittingRevealsTheCompleteSeatedWagerBar() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            Player alice = openUnseated(h, UUID.randomUUID(), "Alice");
            assertCanonicalUnseatedBar(viewInv(h, alice));

            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertCanonicalSeatedBar(viewInv(h, alice));
        }
    }

    @Test
    void leavingThroughTheGuiOpenChairPathRemovesEveryStaleSeatedControl() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS); // fully revealed

            h.click(alice, seatSlot); // own head -- leave, GUI stays open
            h.scheduler.advance(FULL_TRANSITION_TICKS); // fully concealed

            assertFalse(h.inventory.isSeatedForTest(id));
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void leavingClearsSelectedFixedAndAllInStateAndTheBarStillEndsCanonical() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(100);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT);
            assertTrue(h.inventory.selectedWagerForTest(id).isAllIn());

            h.click(alice, seatSlot); // leave
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertNull(h.inventory.selectedWagerForTest(id), "leaving must clear the selection");
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    /**
     * The other GUI-open-vs-closing leave path: a seated pregame player
     * clicking the door at PREGAME_EXIT_SLOT (53) -- routed to
     * {@code handleLeaveChair(player, false)} specifically because the
     * inventory closes right behind it, so the conceal must be an
     * immediate repaint rather than an animation (see that call site's own
     * comment in {@code handleClick}). This is functionally distinct from
     * the own-head-click path covered above, which passes
     * {@code animateConceal = true} and is exercised by
     * {@link #leavingThroughTheGuiOpenChairPathRemovesEveryStaleSeatedControl}
     * and {@link #leavingClearsSelectedFixedAndAllInStateAndTheBarStillEndsCanonical}.
     */
    @Test
    void seatedPlayerClickingPregameExitSlotRefundsOnceClearsSelectionRepaintsImmediatelyAndClosesWithNoStaleCallbacks() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS); // fully revealed

            h.click(alice, ChipSlots.FIRST_SLOT + 2); // select 10.0
            h.click(alice, BlackjackSlotLayout.betSlipSlot(seatSlot)); // commit it
            assertEquals(1, h.currencyProvider.withdrawAttempts.size());
            assertTrue(h.inventory.selectedWagerForTest(id).isFixed(), "selection persists across a commit");

            h.click(alice, BlackjackSlotLayout.PREGAME_EXIT_SLOT); // the door -- leaves AND closes

            // Refunds committed wagers exactly once.
            assertEquals(1, h.currencyProvider.depositAttempts.size(), "the committed 10.0 must be refunded exactly once");
            assertEquals(0, java.math.BigDecimal.valueOf(10.0).compareTo(h.currencyProvider.depositAttempts.get(0)));
            assertFalse(h.inventory.isSeatedForTest(id));

            // Clears the fixed selection.
            assertNull(h.inventory.selectedWagerForTest(id), "the door-exit leave must clear the selection like any other leave");

            // Immediately (synchronously, before any scheduler tick) paints
            // the canonical unseated bar rather than scheduling an animation.
            assertCanonicalUnseatedBar(viewInv(h, alice));
            assertFalse(h.inventory.hasPrivateAnimationForTest(id), "no conceal animation may be registered for the closing-inventory path");

            // Closes the view.
            verify(alice).closeInventory();

            // Leaves no stale scheduled reveal/conceal callback capable of
            // altering a reopened view: advancing time must not resurrect
            // any seated item, and a genuine close+reopen must bootstrap
            // the same canonical unseated state fresh.
            h.scheduler.advance(FULL_TRANSITION_TICKS * 2);
            assertCanonicalUnseatedBar(viewInv(h, alice));

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(id));
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            h.scheduler.advance(2);
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    /** All-In selection variant of the same door-exit path, isolating that no selected-but-uncommitted amount is ever refunded through it. */
    @Test
    void seatedPlayerClickingPregameExitSlotClearsAnUncommittedAllInSelectionWithoutRefundingIt() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(250);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT); // selected, never committed

            h.click(alice, BlackjackSlotLayout.PREGAME_EXIT_SLOT);

            assertTrue(h.currencyProvider.depositAttempts.isEmpty(), "an uncommitted All In selection must never be refunded");
            assertNull(h.inventory.selectedWagerForTest(id));
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void sameRoundReseatingDoesNotRestartCompletedChairOrWagerGuidance() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, ChipSlots.FIRST_SLOT);
            assertTrue(h.inventory.isChairGuidanceCompletedForTest(id));
            assertTrue(h.inventory.isWagerGuidanceCompletedForTest(id));

            h.click(alice, seatSlot); // leave
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // reseat
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertTrue(h.inventory.isChairGuidanceCompletedForTest(id), "reseating must not restart completed chair guidance");
            assertTrue(h.inventory.isWagerGuidanceCompletedForTest(id), "reseating must not restart completed wager guidance");
        }
    }

    @Test
    void reseatingStartsAFreshRevealAndEndsInCanonicalSeatedState() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave
            h.scheduler.advance(FULL_TRANSITION_TICKS); // fully concealed

            h.click(alice, seatSlot); // reseat
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertTrue(h.inventory.isSeatedForTest(id));
            assertCanonicalSeatedBar(viewInv(h, alice));
        }
    }

    @Test
    void leavingDuringActivePlayCannotLeaveActionOrWagerControlsInTheUnseatedViewersBar() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.inventory.stackDeckForTest(flatSevenStack(40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(300);
            assertTrue(h.inventory.isGameActiveForTest());

            // Whichever of the two isn't the current actionable player
            // leaves via their own head slot -- the GUI-open active-game
            // leave path.
            UUID currentId = h.inventory.currentPlayerIdForTest();
            Player leaver = currentId.equals(alice.getUniqueId()) ? bob : alice;
            int leaverSeat = leaver == alice ? BlackjackSlotLayout.SEAT_SLOTS[0] : BlackjackSlotLayout.SEAT_SLOTS[1];

            h.click(leaver, leaverSeat);
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertFalse(h.inventory.isSeatedForTest(leaver.getUniqueId()));
            Inventory inv = viewInv(h, leaver);
            // Active phase never shows the pregame wager bar at all -- the
            // leave must not have fabricated one either.
            assertEquals(Material.SPRUCE_DOOR, inv.getItem(BlackjackSlotLayout.ACTIVE_EXIT_SLOT).getType());
            for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
                Material type = inv.getItem(slot) == null ? null : inv.getItem(slot).getType();
                assertTrue(type != Material.OAK_SIGN, "no leaked action control at slot " + slot);
            }
        }
    }

    // ==================================================================
    // Cancellation
    // ==================================================================

    @Test
    void leaveDuringRevealPreventsAllRemainingRevealCallbacksAndEndsCanonicalUnseated() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(BlackjackTiming.WAGER_REVEAL_STEP_TICKS * 2); // interrupt mid-reveal

            h.click(alice, seatSlot); // leave mid-reveal
            h.scheduler.advance(FULL_TRANSITION_TICKS * 2);

            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void reseatDuringConcealPreventsStaleConcealCallbacksAndEndsCanonicalSeated() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave -- starts conceal
            h.scheduler.advance(BlackjackTiming.WAGER_REVEAL_STEP_TICKS * 2); // interrupt mid-conceal

            h.click(alice, seatSlot); // reseat mid-conceal
            h.scheduler.advance(FULL_TRANSITION_TICKS * 2);

            assertTrue(h.inventory.isSeatedForTest(id));
            assertCanonicalSeatedBar(viewInv(h, alice));
        }
    }

    @Test
    void rapidLeaveReseatLeaveFinishesCanonicalUnseated() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot); // sit
            h.click(alice, seatSlot); // leave, immediately
            h.click(alice, seatSlot); // reseat, immediately
            h.click(alice, seatSlot); // leave again, immediately

            h.scheduler.advance(FULL_TRANSITION_TICKS * 3);

            assertFalse(h.inventory.isSeatedForTest(id));
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void closingTheViewCancelsPendingPrivateConcealSteps() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            // A second player stays seated so alice leaving isn't the
            // table's last seat -- otherwise removePlayerData's own
            // cancelGame() (last-seat teardown) would already resync her
            // bar to canonical CLOSED synchronously, leaving nothing
            // in-flight for this test to actually exercise.
            Player bob = openUnseated(h, UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave -- starts conceal
            assertTrue(h.inventory.hasPrivateAnimationForTest(id));

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(id));

            assertFalse(h.inventory.hasPrivateAnimationForTest(id), "closing the view must cancel the in-flight conceal");
        }
    }

    @Test
    void reopeningAfterACloseDuringConcealBootstrapsTheCorrectCanonicalState() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave -- starts conceal
            h.scheduler.advance(BlackjackTiming.WAGER_REVEAL_STEP_TICKS * 2); // mid-conceal

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(id));
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            h.scheduler.advance(2);

            assertCanonicalUnseatedBar(viewInv(h, alice));

            // Any stale step from the pre-close conceal run must not fire
            // into the newly (re)created view either.
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void roundResetInvalidatesPendingConcealSteps() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            // Keep a second seat filled -- see closingTheViewCancelsPendingPrivateConcealSteps's own comment.
            Player bob = openUnseated(h, UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave -- starts conceal
            assertTrue(h.inventory.hasPrivateAnimationForTest(id));

            h.inventory.resetGameForTest();

            assertFalse(h.inventory.hasPrivateAnimationForTest(id), "a genuine round reset must cancel any in-flight private conceal");
        }
    }

    @Test
    void deletingTheControllerInvalidatesPendingConcealSteps() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            // Keep a second seat filled -- see closingTheViewCancelsPendingPrivateConcealSteps's own comment.
            Player bob = openUnseated(h, UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, seatSlot); // leave -- starts conceal
            assertTrue(h.inventory.hasPrivateAnimationForTest(id));

            h.inventory.delete();

            assertFalse(h.inventory.hasPrivateAnimationForTest(id), "delete() must cancel any in-flight private conceal");
        }
    }

    @Test
    void otherViewersInventoriesRemainUnchangedByOnePlayersConceal() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID aliceId = UUID.randomUUID();
            UUID bobId = UUID.randomUUID();
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            Player alice = openUnseated(h, aliceId, "Alice");
            Player bob = openUnseated(h, bobId, "Bob");
            h.click(alice, aliceSeat);
            h.click(bob, bobSeat);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            assertCanonicalSeatedBar(viewInv(h, bob));

            h.click(alice, aliceSeat); // Alice leaves
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertCanonicalUnseatedBar(viewInv(h, alice));
            // Bob never left -- his own seated bar must be completely untouched.
            assertTrue(h.inventory.isSeatedForTest(bobId));
            assertCanonicalSeatedBar(viewInv(h, bob));
        }
    }

    // ==================================================================
    // Idempotency
    // ==================================================================

    /**
     * There is no click that can make an already-seated viewer's own
     * {@code startWagerBarReveal} run a second time: clicking their own
     * (now head-occupied) seat slot routes to {@code isPlayerHeadSlot} ->
     * {@code handleLeaveChair}, never back to {@code handleChairClick}. The
     * only other click that could even reach {@code handleChairClick} for
     * that same slot is a *different* player trying to sit in it, which
     * {@code handleChairClick}'s own already-occupied guard (the clicked
     * item isn't a "_STAIRS" item once a head sits there) rejects before
     * ever calling {@code startWagerBarReveal}. This test exercises that
     * actual rejection, rather than merely asserting nothing happens when
     * nothing is clicked at all.
     */
    @Test
    void clickingAnAlreadyOccupiedSeatIsRejectedAndCannotStartAnOverlappingRevealChain() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID aliceId = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, aliceId, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            assertCanonicalSeatedBar(viewInv(h, alice));

            UUID bobId = UUID.randomUUID();
            Player bob = openUnseated(h, bobId, "Bob");
            h.click(bob, seatSlot); // Bob tries to sit in Alice's occupied seat

            assertFalse(h.inventory.isSeatedForTest(bobId), "handleChairClick must reject an already-occupied seat");
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            // Alice's own bar must be completely unaffected -- no second,
            // overlapping reveal chain was ever started for her.
            assertCanonicalSeatedBar(viewInv(h, alice));
        }
    }

    /**
     * Symmetric case: there is no click that can make an unseated viewer's
     * {@code startWagerBarConceal} run at all, let alone twice. The door at
     * PREGAME_EXIT_SLOT (53) is the one click that reaches
     * {@code handleLeaveChair} regardless of the clicking player's seated
     * status (routing is purely slot-number-based for that branch -- see
     * {@code handleClick}'s pregame dispatch), so it's the real path that
     * exercises {@code handleLeaveChair}'s own
     * {@code !playerSeats.containsKey(playerId)} guard as a genuine no-op,
     * unlike the previous version of this test which clicked the unseated
     * door at slot 45 -- a slot whose click handler never calls
     * {@code handleLeaveChair} at all.
     */
    @Test
    void unseatedPlayerClickingPregameExitSlotHitsHandleLeaveChairsGuardAndStartsNoConceal() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            Player alice = openUnseated(h, id, "Alice");
            assertCanonicalUnseatedBar(viewInv(h, alice));

            h.click(alice, BlackjackSlotLayout.PREGAME_EXIT_SLOT); // door -- reaches handleLeaveChair(player, false) even though unseated

            assertFalse(h.inventory.hasPrivateAnimationForTest(id), "an unseated leave attempt must never register a conceal animation");
            verify(alice).closeInventory();
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertCanonicalUnseatedBar(viewInv(h, alice));
        }
    }

    @Test
    void repeatedCanonicalRepaintingDoesNotDuplicateLoreOrCorruptItems() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(100);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, ChipSlots.FIRST_SLOT);

            for (int i = 0; i < 3; i++) {
                h.click(alice, seatSlot); // leave
                h.scheduler.advance(FULL_TRANSITION_TICKS);
                h.click(alice, seatSlot); // reseat
                h.scheduler.advance(FULL_TRANSITION_TICKS);
            }

            assertCanonicalSeatedBar(viewInv(h, alice));
            // No selected-wager lore ever leaks onto a fresh seat's chips --
            // this player has no selection after the last leave/reseat.
            assertNull(h.inventory.selectedWagerForTest(id));
            java.util.List<String> lore = viewInv(h, alice).getItem(ChipSlots.FIRST_SLOT).getItemMeta().getLore();
            assertTrue(lore == null || lore.isEmpty(), "an unselected chip must carry no stray 'currently selected' lore");
        }
    }

    // ==================================================================
    // Economics
    // ==================================================================

    @Test
    void pregameLeaveRefundsExactlyOnce() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, ChipSlots.FIRST_SLOT + 2); // 10.0
            h.click(alice, BlackjackSlotLayout.betSlipSlot(seatSlot)); // commit
            assertEquals(1, h.currencyProvider.withdrawAttempts.size());

            h.click(alice, seatSlot); // leave -- concurrently starts the conceal animation
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertEquals(1, h.currencyProvider.depositAttempts.size(), "the committed wager must be refunded exactly once");
            assertEquals(0, java.math.BigDecimal.valueOf(10.0).compareTo(h.currencyProvider.depositAttempts.get(0)));
        }
    }

    @Test
    void activeLeavePreservesTheExistingForfeitPolicy() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.inventory.stackDeckForTest(flatSevenStack(40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(300);
            assertTrue(h.inventory.isGameActiveForTest());
            h.currencyProvider.depositAttempts.clear(); // isolate this leave's own economics

            UUID currentId = h.inventory.currentPlayerIdForTest();
            Player leaver = currentId.equals(alice.getUniqueId()) ? bob : alice;
            int leaverSeat = leaver == alice ? BlackjackSlotLayout.SEAT_SLOTS[0] : BlackjackSlotLayout.SEAT_SLOTS[1];

            h.click(leaver, leaverSeat);
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertTrue(h.currencyProvider.depositAttempts.isEmpty(), "an active-game leave must forfeit the committed wager, not refund it");
        }
    }

    @Test
    void noSelectedButUncommittedWagerIsEverRefundedDuringAConcealedLeave() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(250);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = openUnseated(h, id, "Alice");
            h.click(alice, seatSlot);
            h.scheduler.advance(FULL_TRANSITION_TICKS);
            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT); // selected, never committed

            h.click(alice, seatSlot); // leave
            h.scheduler.advance(FULL_TRANSITION_TICKS);

            assertTrue(h.currencyProvider.depositAttempts.isEmpty(), "a selected-but-uncommitted amount must never be refunded, even mid-conceal");
        }
    }
}
