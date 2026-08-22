package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.session.ExitReason;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the staged split visual
 * sequence in {@code BlackjackInventory#runSplitAnimation}: B slides out to
 * a temporary slot, C deals beside A, D deals beside temp-B, the inactive
 * [B][D] pair slides one step left, then both temporary slots clear. See
 * {@link BlackjackSplitAnimationPlan}'s own class doc for the full visual
 * story this pins down at the actual controller/scheduler level (the pure
 * plan's own shape is covered by {@code BlackjackSplitAnimationPlanTest}).
 *
 * <p>Cards render as a stained-glass pane whose color encodes suit (red for
 * hearts/diamonds, black for spades/clubs) and whose stack amount encodes
 * rank value ({@code BlackjackRules#cardStackSize}) -- distinguishing a
 * plain background pane (green) from a dealt card, and one dealt card from
 * another by (material, amount), needs no further UI internals.
 */
class BlackjackSplitVisualSequenceIntegrationTest {

    private static final Material BACKGROUND = Material.GREEN_STAINED_GLASS_PANE;
    private static final Material BLACK_CARD = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material RED_CARD = Material.RED_STAINED_GLASS_PANE;

    private static final int SEAT_SLOT = BlackjackSlotLayout.SEAT_SLOTS[0];
    private static final int SLOT_A = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
    private static final int SLOT_ORIG_B = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 1);
    private static final int SLOT_GAP = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 2);
    private static final int SLOT_TEMP_B = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 3);
    private static final int SLOT_TEMP_D = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 4);

    /**
     * Alice: 8(spades)/8(clubs) -- a splittable pair, both black-suited so A
     * and B render identically ({@code BLACK_CARD}, amount 8); that's fine,
     * A never moves so its slot alone identifies it. Dealer 7/7 (no
     * insurance, no natural). Split replacements: C = 2(diamonds) --
     * {@code RED_CARD} amount 2 -- and D = 8(diamonds) -- {@code RED_CARD}
     * amount 8, distinguishable from A/B by color alone. Rest flat 2s so
     * nothing busts or exhausts the shoe.
     */
    private static List<Card> splittableHandDeck() {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // A
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer up
        stack.add(new Card(Suit.CLUBS, Rank.EIGHT));     // B
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer hole
        stack.add(new Card(Suit.DIAMONDS, Rank.TWO));    // C (original replacement)
        stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT));  // D (sibling replacement)
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        return stack;
    }

    private static Player setUpAndSplit(BlackjackControllerTestSupport.Harness h) {
        h.inventory.stackDeckForTest(splittableHandDeck());
        h.currencyProvider.setBalance(1000);
        Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
        h.click(alice, SEAT_SLOT);
        h.inventory.commitWagerForTest(alice, 15.0);
        h.inventory.beginStartTransitionForTest();
        // Fine-grained (1-tick) polling: a coarser step can overshoot well
        // past the moment the decision actually becomes actionable, eating
        // into the turn timer's own budget before the test ever acts on it.
        h.advanceToActionableTurn(1, 800);
        h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
        return alice;
    }

    private static ItemStack item(BlackjackControllerTestSupport.Harness h, Player viewer, int slot) {
        return h.inventory.getOrCreateView(viewer).getItem(slot);
    }

    private static List<String> rowSignature(Inventory inventory) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            ItemStack item = inventory.getItem(BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, i));
            result.add(item == null ? "AIR" : item.getType() + ":" + item.getAmount());
        }
        return result;
    }

    @Test
    void actingPlayerLeavingMidSplitSynchronouslyClearsEveryOwnedTransientFrame() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);
            UUID aliceId = alice.getUniqueId();
            h.scheduler.advance(BlackjackTiming.SPLIT_SLIDE_HOP_TICKS);

            h.inventory.onSessionTerminated(aliceId, ExitReason.KICKED);
            assertTrue(!h.inventory.isSeatedForTest(aliceId));
            assertTrue(h.inventory.playerHandsForTest(aliceId).isEmpty());
            for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                assertEquals(BACKGROUND, item(h, alice, BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, i)).getType(),
                    "the cancelled split may not strand a parked, staging, or flight card");
            }

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory reopened = h.inventory.getOrCreateView(spectator);
            assertEquals(rowSignature(h.inventory.getOrCreateView(alice)), rowSignature(reopened));

            h.scheduler.advance(300);
            assertTrue(h.inventory.playerHandsForTest(aliceId).isEmpty(),
                "stale split/deal callbacks must never resurrect the departed hand");
        }
    }

    @Test
    void phase1BVacatesItsOriginalSlotAndEntersTheGapInTheSameInstant() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            // B visibly picks up and leaves its original slot immediately --
            // before any scheduler advance -- and, in that very same
            // instant, appears at the gap cell. B must never be rendered
            // nowhere at all (not at the origin, not at the gap) even for
            // one tick -- that gap-vacate-then-later-arrive ordering used to
            // produce a real, visible blank flicker.
            assertEquals(BACKGROUND, item(h, alice, SLOT_ORIG_B).getType(), "B's original slot must already be vacated");
            ItemStack bAtGap = item(h, alice, SLOT_GAP);
            assertEquals(BLACK_CARD, bAtGap.getType(), "B must already be visible at the gap cell -- never invisible even briefly");
            assertEquals(8, bAtGap.getAmount());
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_B).getType(), "B hasn't landed at its temporary slot yet");
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_D).getType());
        }
    }

    @Test
    void phase1BLandsAtItsTemporarySlotAfterOneMoreHop() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            // One hop later: B lands at its temporary slot, the gap cell clears again.
            h.scheduler.advance(BlackjackTiming.SPLIT_SLIDE_HOP_TICKS);
            ItemStack tempB = item(h, alice, SLOT_TEMP_B);
            assertEquals(BLACK_CARD, tempB.getType());
            assertEquals(8, tempB.getAmount(), "B (8 of clubs) must land at its temporary slot");
            assertEquals(BACKGROUND, item(h, alice, SLOT_GAP).getType(), "the gap cell must clear again once B has passed through it");
        }
    }

    @Test
    void phase2DealsCBesideAAfterOneStep() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            h.scheduler.advance(BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS);

            ItemStack c = item(h, alice, SLOT_ORIG_B);
            assertEquals(RED_CARD, c.getType());
            assertEquals(2, c.getAmount(), "C (2 of diamonds) must land beside A");
            // B is still visible at its temporary slot -- only C changed.
            ItemStack tempB = item(h, alice, SLOT_TEMP_B);
            assertEquals(BLACK_CARD, tempB.getType());
            assertEquals(8, tempB.getAmount());
        }
    }

    /** Advances the scheduler one tick at a time (bounded) until {@code condition} holds, so tests don't have to hardcode exact ticks against the now-per-seat-dynamic D landing/tuck-under timing. */
    private static void advanceUntil(BlackjackControllerTestSupport.Harness h, java.util.function.BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks && !condition.getAsBoolean(); i++) {
            h.scheduler.advance(1);
        }
        assertTrue(condition.getAsBoolean(), "condition never became true within " + maxTicks + " ticks");
    }

    @Test
    void phase3DealsDBesideTempB() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            advanceUntil(h, () -> item(h, alice, SLOT_TEMP_D).getType() == RED_CARD, 200);

            ItemStack d = item(h, alice, SLOT_TEMP_D);
            assertEquals(8, d.getAmount(), "D (8 of diamonds) must land beside temp-B");
            // Both [B][D] are visible together before parking.
            ItemStack tempB = item(h, alice, SLOT_TEMP_B);
            assertEquals(BLACK_CARD, tempB.getType());
            assertEquals(8, tempB.getAmount());
        }
    }

    @Test
    void phase4SlidesTheInactivePairOneStepLeft() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            advanceUntil(h, () -> item(h, alice, SLOT_TEMP_D).getType() == RED_CARD, 200); // D has landed
            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == BLACK_CARD, 200); // phase 4's own slide

            ItemStack bAtGap = item(h, alice, SLOT_GAP);
            assertEquals(8, bAtGap.getAmount(), "B must have slid into the gap cell");
            ItemStack dAtOldTempB = item(h, alice, SLOT_TEMP_B);
            assertEquals(RED_CARD, dAtOldTempB.getType());
            assertEquals(8, dAtOldTempB.getAmount(), "D must have slid into B's old temporary slot");
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_D).getType(), "D's old slot must already be clear");

            // The active hand is completely undisturbed throughout.
            ItemStack c = item(h, alice, SLOT_ORIG_B);
            assertEquals(RED_CARD, c.getType());
            assertEquals(2, c.getAmount());
        }
    }

    /**
     * Phase 5: B tucks away first -- the gap cell briefly shows D alone
     * (having taken B's old spot), with B's own old slot (temp-B) already
     * clear. This is the intermediate state the old single-step "both
     * clear together" park used to skip straight past.
     */
    @Test
    void phase5BTucksAwayFirstLeavingDAloneAtTheGap() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == BLACK_CARD, 200); // phase 4: B at gap
            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == RED_CARD, 200); // phase 5: B gone, D takes its place

            ItemStack dAtGap = item(h, alice, SLOT_GAP);
            assertEquals(8, dAtGap.getAmount(), "D (8 of diamonds) must now be the sole visible card, at the gap");
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_B).getType(), "B's old slot must already be clear -- B tucked away first");

            // The active hand is still completely undisturbed.
            ItemStack c = item(h, alice, SLOT_ORIG_B);
            assertEquals(RED_CARD, c.getType());
            assertEquals(2, c.getAmount());
            assertTrue(h.inventory.isGameActiveForTest());
        }
    }

    @Test
    void reopeningMidSplitReconstructsTheExactParkedCardLayout() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);
            UUID aliceId = alice.getUniqueId();

            advanceUntil(h, () -> item(h, alice, SLOT_TEMP_D).getType() == RED_CARD, 200);
            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == BLACK_CARD, 200);
            Inventory original = h.inventory.getOrCreateView(alice);
            List<String> beforeClose = rowSignature(original);

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(aliceId));
            Inventory reopened = h.inventory.getOrCreateView(alice);

            assertEquals(beforeClose, rowSignature(reopened),
                "bootstrap must paint the split's parked B/D frame, not the canonical active-hand endpoint");
            assertEquals(BLACK_CARD, reopened.getItem(SLOT_GAP).getType());
            assertEquals(RED_CARD, reopened.getItem(SLOT_TEMP_B).getType());
            assertEquals(BACKGROUND, reopened.getItem(SLOT_TEMP_D).getType());
        }
    }

    @Test
    void phase6ParksTheLastRemainingCardLeavingOnlyActiveHandVisible() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == RED_CARD, 200); // phase 5: D alone at the gap
            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == BACKGROUND, 200); // phase 6: D tucks away too

            assertEquals(BACKGROUND, item(h, alice, SLOT_GAP).getType());
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_B).getType());
            assertEquals(BACKGROUND, item(h, alice, SLOT_TEMP_D).getType());

            // Final visible state: only [A][C].
            ItemStack a = item(h, alice, SLOT_A);
            assertEquals(BLACK_CARD, a.getType());
            assertEquals(8, a.getAmount());
            ItemStack c = item(h, alice, SLOT_ORIG_B);
            assertEquals(RED_CARD, c.getType());
            assertEquals(2, c.getAmount());

            assertTrue(h.inventory.isGameActiveForTest());
        }
    }

    @Test
    void canonicalSiblingHandStillHoldsBAndDAfterParking() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);
            UUID aliceId = alice.getUniqueId();

            advanceUntil(h, () -> item(h, alice, SLOT_GAP).getType() == BACKGROUND
                && h.inventory.playerHandsForTest(aliceId).size() == 2
                && h.inventory.playerHandsForTest(aliceId).get(1).getCards().size() == 2, 300);

            List<BlackjackHand> hands = h.inventory.playerHandsForTest(aliceId);
            assertEquals(2, hands.size(), "the split must still produce exactly two canonical hands");
            BlackjackHand sibling = hands.get(1);
            assertEquals(2, sibling.getCards().size());
            assertEquals(Rank.EIGHT, sibling.getCards().get(0).getRank(), "B");
            assertEquals(Suit.CLUBS, sibling.getCards().get(0).getSuit());
            assertEquals(Rank.EIGHT, sibling.getCards().get(1).getRank(), "D");
            assertEquals(Suit.DIAMONDS, sibling.getCards().get(1).getSuit());

            BlackjackHand active = hands.get(0);
            assertEquals(2, active.getCards().size());
            assertEquals(Rank.EIGHT, active.getCards().get(0).getRank(), "A");
            assertEquals(Suit.SPADES, active.getCards().get(0).getSuit());
            assertEquals(Rank.TWO, active.getCards().get(1).getRank(), "C");
            assertEquals(Suit.DIAMONDS, active.getCards().get(1).getSuit());
        }
    }

    @Test
    void actionsAreUnavailableDuringTheAnimationAndReturnOnceItCompletes() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplit(h);

            // Mid-animation: the action row must be empty (playerTurnActive false).
            h.scheduler.advance(2 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS);
            for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
                Material type = item(h, alice, slot) == null ? null : item(h, alice, slot).getType();
                assertTrue(type == null || type == BACKGROUND, "no action control may render mid-animation, slot " + slot);
            }

            advanceUntil(h, () -> item(h, alice, BlackjackSlotLayout.ACTION_STAND_SLOT) != null, 300);
            // The still-active (original) hand is actionable again once the animation completes.
            assertNotNull(item(h, alice, BlackjackSlotLayout.ACTION_STAND_SLOT));
        }
    }

    @Test
    void aRoundResetMidAnimationCancelsTheSharedRunAndLeavesNoStaleTemporarySlots() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            setUpAndSplit(h);

            h.scheduler.advance(2 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS); // mid-animation: B/D both visible
            assertTrue(h.inventory.hasSharedAnimationForTest());

            h.inventory.resetGameForTest();
            // resetGame() immediately starts its own game-reset white-tile
            // sweep (a LOBBY-phase shared animation) -- what must be gone is
            // the split sequence specifically, not shared animations
            // altogether.
            assertTrue(h.inventory.sharedAnimationPhaseForTest() != BlackjackFrame.Phase.ACTIVE);

            // Advancing further must not let a stale callback repaint anything --
            // no exception, and the reset's own canonical (unseated) state stands.
            h.scheduler.advance(4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS);
        }
    }

    private static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
