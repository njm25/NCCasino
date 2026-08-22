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
 * Controller-level regression coverage for rendering the Split action with
 * {@link Material#WEEPING_VINES}. Every canonical rendering path (initial
 * action-row paint via {@code beginActionableDecision}/{@code repaintActionsForCurrentPlayer},
 * the action-guidance glow/plain cycle, and view close/reopen bootstrap)
 * funnels through the single {@code buildActionItem} builder in
 * {@code BlackjackInventory} -- these tests pin the material down at every
 * one of those call sites rather than just the builder itself, and confirm
 * the material change is purely cosmetic (slot, click routing, eligibility,
 * and economics are all untouched).
 */
class BlackjackSplitActionRenderingIntegrationTest {

    private static final Material SPLIT_MATERIAL = Material.WEEPING_VINES;

    /** Alice: 8/8 (splittable pair), dealer 7 up/hole (no insurance, no natural). Split replacements 2 and 8; rest flat 2s so nothing busts or exhausts the shoe. */
    private static List<Card> splittableHandDeck() {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.EIGHT));
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
        stack.add(new Card(Suit.CLUBS, Rank.EIGHT));
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
        stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT));
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        return stack;
    }

    /** Alice: 7/9 (not a pair -- ineligible for Split), dealer 7 up/hole. */
    private static List<Card> nonSplittableHandDeck() {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, Rank.SEVEN));
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
        stack.add(new Card(Suit.CLUBS, Rank.NINE));
        stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
        for (int i = 0; i < 40; i++) {
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
        }
        return stack;
    }

    private static ItemStack splitSlotItem(BlackjackControllerTestSupport.Harness h, Player viewer) {
        return h.inventory.getOrCreateView(viewer).getItem(BlackjackSlotLayout.ACTION_SPLIT_SLOT);
    }

    // ==================================================================
    // 1/2. The acting player sees WEEPING_VINES, through both guidance phases
    // ==================================================================

    @Test
    void eligibleActingPlayerSeesWeepingVinesInTheSplitSlot() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest(), "setup: alice must actually be the actionable player");
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType());
        }
    }

    @Test
    void splitItemRemainsWeepingVinesThroughBothGlowAndPlainGuidancePhases() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            // beginActionableDecision's own repaint, then the guidance
            // cycle's immediate first (glowing) phase -- both already
            // landed by the time advanceToActionableTurn returns.
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "glow phase");

            h.scheduler.advance(BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS); // flips to the plain phase
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "plain phase");

            h.scheduler.advance(BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS); // flips back to glow
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "glow phase again");
        }
    }

    // ==================================================================
    // 3/4. Private-view isolation: non-acting seated player, spectator
    // ==================================================================

    @Test
    void nonActingSeatedPlayerDoesNotSeeAnotherPlayersSplitControl() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));  // alice card 1
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // bob card 1
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer up-card
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));   // alice card 2
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // bob card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole card
            for (int i = 0; i < 40; i++) {
                stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
            }
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 15.0);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest(), "setup: alice (seat 0) acts first");
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "alice, the acting player, must see it");
            assertNotEquals(SPLIT_MATERIAL, splitSlotItem(h, bob).getType(), "bob, not currently acting, must never see alice's split control");
        }
    }

    @Test
    void spectatorDoesNotSeeTheSplitControl() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            h.inventory.getOrCreateView(spectator);
            h.inventory.onViewOpened(spectator);
            h.scheduler.advance(2);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest());
            assertNotEquals(SPLIT_MATERIAL, splitSlotItem(h, spectator).getType(), "a spectator who never sat must never see any seated player's split control");
        }
    }

    // ==================================================================
    // 5. Close/reopen bootstrap reconstruction
    // ==================================================================

    @Test
    void closingAndReopeningTheActingPlayersViewReconstructsSplitAsWeepingVines() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            h.currencyProvider.setBalance(1000);

            // A genuine mid-active-play InventoryCloseEvent now rides to
            // result rather than forfeiting (see handlePlayerClose/
            // onSessionTerminated/GameTerminationPolicy's RIDE_TO_RESULT
            // handling -- BlackjackRideToResultIntegrationTest covers that
            // directly). This test only cares about bootstrap reconstruction
            // though, so the simplest way to exercise "reopen reconstructs
            // the view from scratch while the player is still fully in the
            // hand" is still the same one BlackjackViewBootstrapIntegrationTest
            // already establishes: never create the view until after the
            // decision is actionable, so getOrCreateView must bootstrap it
            // fresh via the exact same code path a real reopen would use.
            // A bystander has to open the table first -- seating relies on
            // the shared legacy inventory's seat item, only painted the
            // very first time anyone opens the table (see initializeGameMenu).
            h.seatOnlinePlayer(UUID.randomUUID(), "Bystander");
            UUID aliceId = UUID.randomUUID();
            Player alice = h.registerOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(aliceId, h.inventory.currentPlayerIdForTest(), "setup: alice must actually be the actionable player");
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "a freshly (re)bootstrapped view for the acting player must reconstruct the split control as WEEPING_VINES");
        }
    }

    // ==================================================================
    // 6. Clicking the control still invokes the existing split behavior
    // ==================================================================

    @Test
    void clickingTheWeepingVinesSplitControlStillInvokesSplitBehavior() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType());
            assertEquals(15.0, h.inventory.totalRoundRefundForPlayerForTest(aliceId), 0.0001, "setup: one hand, one wager, before splitting");

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(100); // let the split animation/debit fully resolve

            assertEquals(30.0, h.inventory.totalRoundRefundForPlayerForTest(aliceId), 0.0001,
                "a genuine split must debit a second matching wager -- two hands' worth is now owed");
        }
    }

    // ==================================================================
    // 7. Ineligible hands never gain a Split control
    // ==================================================================

    @Test
    void ineligibleHandsDoNotGainASplitControl() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(nonSplittableHandDeck());
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest(), "setup: alice must actually be the actionable player");
            assertNotEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "a non-pair hand must never render a Split control at all");
        }
    }

    // ==================================================================
    // 8. Failed/rejected Split leaves economics and timing unchanged
    // ==================================================================

    @Test
    void aFailedSplitDoesNotAlterEconomicsOrTiming() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(splittableHandDeck());
            // Plenty of balance -- Split must be genuinely eligible and
            // rendered (splitEligibleForHand's own hasEnoughWager pre-filter
            // must pass), so the failure below is a real transaction
            // failure at debit time, not "never eligible in the first place".
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "setup: split control genuinely eligible and WEEPING_VINES before the failed attempt");

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            int beforeAttempt = h.inventory.turnTimerSecondsRemainingForTest();
            int withdrawsBefore = h.currencyProvider.withdrawAttempts.size();
            assertEquals(15.0, h.inventory.totalRoundRefundForPlayerForTest(aliceId), 0.0001);

            h.currencyProvider.setNextWithdrawSucceeds(false); // the matching-wager debit itself fails
            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);

            assertEquals(withdrawsBefore + 1, h.currencyProvider.withdrawAttempts.size(), "the failed attempt itself was genuinely made");
            assertEquals(beforeAttempt, h.inventory.turnTimerSecondsRemainingForTest(), "a failed Split must never reset or extend the current deadline");
            assertEquals(15.0, h.inventory.totalRoundRefundForPlayerForTest(aliceId), 0.0001, "a failed Split must never change the economically-owed total");
            assertTrue(h.inventory.isGameActiveForTest(), "the round must remain unaffected by the rejected attempt");

            // The decision must still be genuinely open (not consumed) --
            // the control must still be showing, and a retry with the debit
            // now succeeding must actually split.
            assertEquals(SPLIT_MATERIAL, splitSlotItem(h, alice).getType(), "the split control must still be showing after a failed attempt");
            h.currencyProvider.setNextWithdrawSucceeds(true);
            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(100);
            assertEquals(30.0, h.inventory.totalRoundRefundForPlayerForTest(aliceId), 0.0001, "a subsequent successful retry must still split normally");
        }
    }
}
