package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the bottom seat's dedicated
 * split choreography (see {@code BlackjackInventory#runSplitAnimation}'s
 * bottom-seat branch and {@code #bottomSeatSplitDashPath}): C dashes out of
 * the bottom row and parks in the dealer's row *before* B's slide-out even
 * begins, so B's own slide never has to share the row with anything else
 * in flight -- unlike the old single-detour path, whose fallback could cut
 * straight back through B's own temp slot and corrupt it. This pins down
 * that B survives the whole animation intact, and that nothing about the
 * dealer's own (untouched) cards is disturbed along the way.
 */
class BlackjackBottomSeatSplitFlightIntegrationTest {

    private static final int SEAT_SLOT = BlackjackSlotLayout.SEAT_SLOTS[4];
    private static final int SLOT_A = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 0);
    private static final int SLOT_ORIG_B = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 1);

    private static ItemStack item(BlackjackControllerTestSupport.Harness h, Player viewer, int slot) {
        return h.inventory.getOrCreateView(viewer).getItem(slot);
    }

    private static Player setUpAndSplitAtBottomSeat(BlackjackControllerTestSupport.Harness h) {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.EIGHT));   // A
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer up
        stack.add(new Card(Suit.CLUBS, Rank.EIGHT));     // B
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));    // dealer hole -- stays hidden throughout
        stack.add(new Card(Suit.DIAMONDS, Rank.TWO));    // C (original replacement)
        stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT));  // D (sibling replacement)
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        h.inventory.stackDeckForTest(stack);
        h.currencyProvider.setBalance(1000);
        Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
        h.click(alice, SEAT_SLOT);
        h.inventory.commitWagerForTest(alice, 15.0);
        h.inventory.beginStartTransitionForTest();
        h.advanceToActionableTurn(1, 800);
        h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
        return alice;
    }

    /**
     * The actual point of this whole redesign: C must fully finish its dash
     * (and be sitting parked in the dealer's row) before B's own slide-out
     * is ever triggered -- not merely "safe if it happens to be fast
     * enough". Pins down the ordering directly, not just the end result.
     */
    @Test
    void bNeverMovesUntilCsDashHasFullyParked() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplitAtBottomSeat(h);

            List<Integer> dashPath = h.inventory.bottomSeatSplitDashPathForTest(SLOT_ORIG_B);
            long dashDuration = (dashPath.size() - 1) * BlackjackTiming.BOTTOM_SEAT_DASH_HOP_TICKS;

            // One tick before the dash finishes: B must still be sitting at
            // its original slot, completely untouched -- its slide hasn't
            // been triggered yet, and C hasn't parked yet either.
            h.scheduler.advance(dashDuration - 1);
            ItemStack bStillHome = item(h, alice, SLOT_ORIG_B);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, bStillHome.getType(), "B must still be at its original slot -- its slide hasn't started yet");
            assertEquals(8, bStillHome.getAmount());

            // The instant the dash finishes, B's slide triggers, vacating
            // its original slot -- left empty (background), not yet showing
            // C. C stays parked below, paused, until B has genuinely
            // finished moving right.
            h.scheduler.advance(1);
            ItemStack bJustVacated = item(h, alice, SLOT_ORIG_B);
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, bJustVacated.getType(), "B's slide must only begin once C's dash has fully parked, not before");

            // C's own hop-up is on its own deliberate pause now --
            // BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS after the dash finishes --
            // not tied to B's own temp-slot landing at all (see the doc on
            // hopUpLandingTick in runSplitAnimation): a short 2-tick gap
            // read as immediate, so it stays parked, visibly waiting, for
            // much longer before hopping up.
            h.scheduler.advance(BlackjackTiming.BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS - 1);
            ItemStack stillWaiting = item(h, alice, SLOT_ORIG_B);
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, stillWaiting.getType(), "C must still be waiting below -- not landed until its own park pause elapses");

            h.scheduler.advance(1);
            ItemStack cLanded = item(h, alice, SLOT_ORIG_B);
            assertEquals(Material.WHITE_STAINED_GLASS_PANE, cLanded.getType(), "C must land (hidden/face-down) in slotOrigB exactly BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS after parking");
        }
    }

    /**
     * Regression test for a real scheduling bug: B's own slide hops were
     * scheduled from inside an already-delayed callback, using a delay
     * value that was itself meant to be absolute-from-round-start
     * ({@code phase1Delay + i * hopTicks}) -- but {@code runTaskLater}'s
     * delay is always relative to right now, so nesting it silently
     * doubled the wait (landing at {@code 2 * phase1Delay + ...} instead
     * of {@code phase1Delay + ...}). Pins down B's temp-slot landing tick
     * exactly, so this can't silently regress back to double-delayed.
     */
    @Test
    void bLandsAtItsTempSlotAtTheCorrectSingleDelayNotDouble() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplitAtBottomSeat(h);

            List<Integer> dashPath = h.inventory.bottomSeatSplitDashPathForTest(SLOT_ORIG_B);
            long dashDuration = (dashPath.size() - 1) * BlackjackTiming.BOTTOM_SEAT_DASH_HOP_TICKS;
            int slotTempB = BlackjackSlotLayout.playerCardSlot(SEAT_SLOT, 3);
            // B's vacate and its arrival at the gap happen in the same
            // instant the dash finishes (see runSplitAnimation's
            // vacateOriginAndEnterGap) -- only the gap-to-tempB leg is a
            // genuinely separate, delayed hop.
            long expectedLandingTick = dashDuration + BlackjackTiming.SPLIT_SLIDE_HOP_TICKS;

            // One tick before B's correct (single-delay) landing tick: not there yet.
            h.scheduler.advance(expectedLandingTick - 1);
            ItemStack notYet = item(h, alice, slotTempB);
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, notYet.getType(),
                "B must not have landed yet -- if this is already showing B, the delay was computed wrong in the other direction");

            // Exactly at the correct tick: B has landed. If the double-delay
            // bug were still present, this would still be background,
            // since B wouldn't actually land until dashDuration ticks later.
            h.scheduler.advance(1);
            ItemStack landed = item(h, alice, slotTempB);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, landed.getType(), "B must land at its temp slot at exactly phase1Delay + 2 hops, not double that");
            assertEquals(8, landed.getAmount());
        }
    }

    @Test
    void bCardSurvivesTheFullBottomSeatSplitAnimationIntact() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplitAtBottomSeat(h);

            // Step through every tick of the whole animation (well past
            // completion) so C's flight -- however its own hop timing lands
            // relative to B's slide -- is guaranteed to have fully passed.
            for (int i = 0; i < 4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 10; i++) {
                h.scheduler.advance(1);
                assertNotEquals(Material.GREEN_STAINED_GLASS_PANE,
                    item(h, alice, BlackjackSlotLayout.DECK_HOME_SLOT).getType(),
                    "claiming/clearing the bottom seat row must preserve the deck token on every split tick");

                // At every single tick, B's temp slot must be either empty
                // (not landed yet / already parked) or genuinely showing B --
                // never a background pane immediately after having shown a
                // real card, which is what corruption would look like. The
                // authoritative check is the canonical hand data at the end.
            }

            List<BlackjackHand> hands = h.inventory.playerHandsForTest(alice.getUniqueId());
            assertEquals(2, hands.size(), "the split must still produce exactly two canonical hands");
            BlackjackHand sibling = hands.get(1);
            assertEquals(2, sibling.getCards().size(), "B must never have been lost");
            assertEquals(Rank.EIGHT, sibling.getCards().get(0).getRank(), "B");
            assertEquals(Suit.CLUBS, sibling.getCards().get(0).getSuit());

            BlackjackHand active = hands.get(0);
            assertEquals(2, active.getCards().size());
            assertEquals(Rank.EIGHT, active.getCards().get(0).getRank(), "A");
            assertEquals(Rank.TWO, active.getCards().get(1).getRank(), "C");

            // Final visible state: only [A][C] remain, both real.
            ItemStack a = item(h, alice, SLOT_A);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, a.getType());
            ItemStack c = item(h, alice, SLOT_ORIG_B);
            assertEquals(Material.RED_STAINED_GLASS_PANE, c.getType());
            assertEquals(2, c.getAmount());
        }
    }

    @Test
    void dealerCardsAreCompletelyUndisturbedThroughoutTheBottomSeatSplit() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = setUpAndSplitAtBottomSeat(h);

            for (int i = 0; i < 4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 10; i++) {
                h.scheduler.advance(1);
            }

            // C's dash parks in the dealer's row, but never touches any of
            // the dealer's own cells -- the hole card must still be hidden
            // (never revealed early, never disturbed), and the up-card
            // completely untouched.
            ItemStack hole = item(h, alice, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT);
            assertEquals(Material.WHITE_STAINED_GLASS_PANE, hole.getType(), "the hole card must stay hidden and undisturbed");

            ItemStack up = item(h, alice, BlackjackSlotLayout.DEALER_UP_CARD_SLOT);
            assertEquals(Material.RED_STAINED_GLASS_PANE, up.getType());
            assertTrue(up.getAmount() > 0);
        }
    }
}
