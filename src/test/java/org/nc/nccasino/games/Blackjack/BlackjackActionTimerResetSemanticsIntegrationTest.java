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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the Action Timer's per-decision
 * reset semantics: every genuinely new actionable decision gets a fresh full
 * timeout, while a decision merely ending, or an action failing/being
 * invalid, must never grant a fresh window or lose the remaining one. All of
 * this already lived in {@code beginActionableDecision} (starts fresh),
 * {@code repaintActionsForCurrentPlayer} (pure repaint, stops the timer only
 * when the decision genuinely ended), and {@code resumeTurnTimerAfterFailedAction}
 * (resumes the exact same remaining time) -- these tests pin that contract
 * down at the controller level.
 *
 * <p>Note on exact numbers: the fake scheduler's repeating timer task runs
 * with an immediate (0-tick-delay) first execution, so by the time any
 * polling helper observes "the deadline just started", the canonical field
 * has typically already ticked down once from the configured timeout (e.g.
 * 20 -&gt; 19), and exactly how many periods elapse before that first
 * observation is sensitive to incidental scheduling (card-deal delays,
 * animation lengths). Tests here therefore assert freshness with
 * {@link #assertFreshTimeout} (a small tolerance band near the configured
 * maximum) rather than an exact literal, while "unchanged" assertions --
 * where no scheduler time passes between the two reads -- stay exact.
 */
class BlackjackActionTimerResetSemanticsIntegrationTest {

    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    /** How many ticked-down seconds of slack to allow before a reading no longer counts as "freshly started" -- see the class doc. */
    private static final int FRESH_TOLERANCE_SECONDS = 3;

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    private static ItemStack timerItem(BlackjackControllerTestSupport.Harness h, Player viewer) {
        return h.inventory.getOrCreateView(viewer).getItem(BlackjackSlotLayout.TURN_TIMER_SLOT);
    }

    private static void assertFreshTimeout(BlackjackControllerTestSupport.Harness h, String message) {
        int remaining = h.inventory.turnTimerSecondsRemainingForTest();
        assertTrue(
            remaining > DEFAULT_TIMEOUT_SECONDS - FRESH_TOLERANCE_SECONDS && remaining <= DEFAULT_TIMEOUT_SECONDS,
            message + " (expected within " + FRESH_TOLERANCE_SECONDS + "s of the configured " + DEFAULT_TIMEOUT_SECONDS + "s timeout, was " + remaining + ")"
        );
    }

    @Test
    void initialActionableTurnStartsTheFullConfiguredTimeout() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(alice.getUniqueId(), h.inventory.currentPlayerIdForTest());
            assertFreshTimeout(h, "the initial actionable turn must start at the full configured timeout");
        }
    }

    @Test
    void successfulNonBustingHitResetsTheTimerToTheFullTimeout() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 40)); // 2+2=4 initial, +2 per hit -- never busts, never hits 21
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertFreshTimeout(h, "setup: the initial turn must start fresh");

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS); // let the deadline tick down some, well short of a Hit's own resolution delay
            int beforeHit = h.inventory.turnTimerSecondsRemainingForTest();
            assertTrue(beforeHit < DEFAULT_TIMEOUT_SECONDS);

            h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);

            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > beforeHit,
                "a successful Hit that leaves the hand actionable must reset to a fresh timeout, not merely continue counting down");
            assertFreshTimeout(h, "a successful Hit that leaves the hand actionable must reset to a fresh full timeout");
            assertEquals(Material.CLOCK, timerItem(h, alice).getType());
        }
    }

    @Test
    void hitThatBustsEndsTheDecisionWithoutStartingAnotherTimer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // 10+10=20 initial (no ace on either side -- no natural blackjack), hitting always draws another 10 -> 30, a guaranteed bust.
            h.inventory.stackDeckForTest(flatStack(Rank.TEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertFreshTimeout(h, "setup: the initial turn must start fresh");

            h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);

            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, alice).getType(),
                "a bust ends the decision -- no fresh (or continued) clock for this same hand");
        }
    }

    @Test
    void successfulSplitCreatesAFreshTimerAtThePostAnimationDecision() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Alice: 8/8 (splittable), dealer 7 up/hole (no insurance, no natural). Split replacements 2 and 8; rest flat 2s so nothing busts or exhausts the shoe.
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
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertFreshTimeout(h, "setup: the initial turn must start fresh");

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            // Poll fine-grained through the split animation rather than
            // jumping far past it -- a coarse blind jump risks the
            // now-active post-animation deadline ticking down several
            // periods before this test ever observes it.
            h.advanceToActionableTurn(20, 40);

            assertFreshTimeout(h, "the first split hand's post-animation decision must get a fresh full timeout, not the leftover pre-split time");

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() < DEFAULT_TIMEOUT_SECONDS);

            // Resolve hand 1 (Stand) -- hand 2 becomes active and must also get a fresh timeout.
            h.click(alice, BlackjackSlotLayout.ACTION_STAND_SLOT);
            h.advanceToActionableTurn(20, 40);

            assertFreshTimeout(h, "advancing from one split hand to the next actionable split hand must also start a fresh timeout");
        }
    }

    @Test
    void standEndsTheDecisionWithoutExtendingAndTheNextPlayerGetsAFreshTimeout() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            UUID firstId = h.inventory.currentPlayerIdForTest();
            assertNotNull(firstId);
            Player first = firstId.equals(alice.getUniqueId()) ? alice : bob;
            Player second = first == alice ? bob : alice;

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() < DEFAULT_TIMEOUT_SECONDS);

            h.click(first, BlackjackSlotLayout.ACTION_STAND_SLOT);
            // Immediately after Stand's own repaint (before the turn-advance delay fires), the decision must already read as ended.
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, first).getType(),
                "Stand ends the decision immediately -- no continued or extended clock");

            // stopTurnTimerTask never clears the stale turnTimer* fields, only
            // cancels the task -- advanceToActionableTurn's own polling would
            // otherwise mistake that still-positive leftover value for an
            // already-actionable decision before the turn genuinely advances.
            // Force the actual turn-advance delay to elapse first.
            h.scheduler.advance(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            h.advanceToActionableTurn(20, 40);

            assertEquals(second.getUniqueId(), h.inventory.currentPlayerIdForTest());
            assertFreshTimeout(h, "the next player must get a fresh full timeout, not the first player's leftover time");
        }
    }

    @Test
    void successfulDoubleDownEndsTheDecisionWithoutExtendingAndTheNextPlayerGetsAFreshTimeout() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            UUID firstId = h.inventory.currentPlayerIdForTest();
            assertNotNull(firstId);
            Player first = firstId.equals(alice.getUniqueId()) ? alice : bob;
            Player second = first == alice ? bob : alice;

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() < DEFAULT_TIMEOUT_SECONDS);

            h.click(first, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, first).getType(),
                "a successful Double Down ends the decision immediately -- no continued or extended clock");

            // See the identical note in the Stand test above -- Double Down
            // additionally waits out its own card-resolution delay before
            // the ordinary turn-advance delay even gets scheduled.
            h.scheduler.advance(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS + BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            h.advanceToActionableTurn(20, 40);

            assertEquals(second.getUniqueId(), h.inventory.currentPlayerIdForTest());
            assertFreshTimeout(h, "the next player must get a fresh full timeout after the first player's Double Down");
        }
    }

    @Test
    void aFailedDoubleDownLeavesTheExistingDeadlineUnchanged() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            // Enough to cover the initial 10 wager but not a second matching one for Double.
            h.currencyProvider.setBalance(15);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertFreshTimeout(h, "setup: the initial turn must start fresh");

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            int beforeAttempt = h.inventory.turnTimerSecondsRemainingForTest();
            assertTrue(beforeAttempt < DEFAULT_TIMEOUT_SECONDS);

            // Double isn't affordable, so only Hit/Stand are offered -- which
            // now render centered at 48/49 (see BlackjackActionLayout's own
            // centering doc), leaving 47/50 genuinely empty. Clicking one of
            // those empty slots is exactly "attempting an action that isn't
            // currently available" -- must be denied, same as the old
            // literal Double-slot click before the layout could shift.
            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT); // no valid action at this slot -- denied

            assertEquals(beforeAttempt, h.inventory.turnTimerSecondsRemainingForTest(),
                "a failed/denied action attempt must never reset or extend the current deadline");
            assertEquals(Material.CLOCK, timerItem(h, alice).getType(), "the same decision resumes -- the clock must still be showing");
        }
    }

    @Test
    void aFailedSplitLeavesTheExistingDeadlineUnchanged() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT));
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));
            for (int i = 0; i < 40; i++) {
                stack.add(new Card(Suit.DIAMONDS, Rank.TWO));
            }
            h.inventory.stackDeckForTest(stack);
            // Enough to cover the initial 15 wager but not a second matching one for Split.
            h.currencyProvider.setBalance(20);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertFreshTimeout(h, "setup: the initial turn must start fresh");

            h.scheduler.advance(BlackjackTiming.CARD_DEAL_DELAY_TICKS);
            int beforeAttempt = h.inventory.turnTimerSecondsRemainingForTest();
            assertTrue(beforeAttempt < DEFAULT_TIMEOUT_SECONDS);

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT); // insufficient funds -- denied

            assertEquals(beforeAttempt, h.inventory.turnTimerSecondsRemainingForTest(),
                "a failed/denied Split must never reset or extend the current deadline");
            assertEquals(Material.CLOCK, timerItem(h, alice).getType(), "the same decision resumes -- the clock must still be showing");
        }
    }
}
