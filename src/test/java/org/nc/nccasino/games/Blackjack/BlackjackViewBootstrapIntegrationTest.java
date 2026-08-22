package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
 * Controller-level regression coverage for slot 46 (the Action Timer)
 * bootstrap behavior. Two defects are covered here:
 *
 * <p>1. A freshly-created view always painted slot 46 as idle brown glass
 * during active play, even when a canonical actionable deadline was already
 * running for the viewer themselves -- corrected only ~1 second later, on
 * the running task's own next tick. Fixed by reading the same canonical
 * {@code turnTimer*} state the running task itself uses (via
 * {@code isTurnTimerCanonicallyActive}), so the acting player's own new view
 * shows the exact remaining time immediately.
 *
 * <p>2. Every viewer bootstrapping mid-decision -- not just the acting
 * player -- was shown that same clock, leaking one player's countdown to
 * every other seated player and spectator. Fixed by additionally gating on
 * {@code view.getPlayerId().equals(turnTimerPlayerId)}: only the acting
 * player's own view may render the clock; every other freshly-bootstrapped
 * view gets the canonical brown glass instead.
 */
class BlackjackViewBootstrapIntegrationTest {

    private static List<String> boardSignature(Inventory inventory) {
        List<String> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            result.add(item == null ? "AIR" : item.getType() + ":" + item.getAmount());
        }
        return result;
    }

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void newActingPlayerViewOpenedMidDecisionImmediatelyShowsExactRemainingTime() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            // Some other player has to open the table first -- seating relies
            // on the shared legacy inventory's seat item, which is only
            // painted once ever, the very first time anyone opens the table
            // (see initializeGameMenu). Bystander never sits, just triggers it.
            Player bystander = h.seatOnlinePlayer(UUID.randomUUID(), "Bystander");

            UUID aliceId = UUID.randomUUID();
            // Register and seat Alice WITHOUT opening her own table view,
            // so her own view is only bootstrapped after her decision has
            // already become actionable -- the exact "late/reopened view"
            // scenario this test covers.
            Player alice = h.registerOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertEquals(aliceId, h.inventory.currentPlayerIdForTest());
            int canonicalSecondsRemaining = h.inventory.turnTimerSecondsRemainingForTest();
            assertTrue(canonicalSecondsRemaining > 0);

            Inventory aliceView = h.inventory.getOrCreateView(alice);

            ItemStack turnTimerItem = aliceView.getItem(BlackjackSlotLayout.TURN_TIMER_SLOT);
            assertNotNull(turnTimerItem);
            assertEquals(Material.CLOCK, turnTimerItem.getType(), "the acting player's own freshly-bootstrapped view must render the clock immediately, not the idle brown-glass fallback");
            assertEquals(Math.max(canonicalSecondsRemaining, 1), turnTimerItem.getAmount(), "the exact canonical remaining seconds, not a guess or a reset value");
        }
    }

    @Test
    void newSpectatorViewOpenedMidDecisionNeverShowsAnotherPlayersClock() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertNotNull(h.inventory.currentPlayerIdForTest());
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > 0);

            // A spectator who has never opened the table before now does so
            // mid-decision -- getOrCreateView bootstraps a brand-new view.
            // They are not the acting player, so they must never see the
            // clock, immediately or otherwise.
            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory spectatorView = h.inventory.getOrCreateView(spectator);

            ItemStack turnTimerItem = spectatorView.getItem(BlackjackSlotLayout.TURN_TIMER_SLOT);
            assertNotNull(turnTimerItem);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, turnTimerItem.getType(), "a non-acting viewer must never see another player's countdown, on bootstrap or otherwise");
        }
    }

    @Test
    void openingAViewNeverResetsOrExtendsTheDeadline() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            int before = h.inventory.turnTimerSecondsRemainingForTest();

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            h.inventory.getOrCreateView(spectator);
            // A second bootstrap for good measure (e.g. a different spectator).
            Player spectator2 = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator2");
            h.inventory.getOrCreateView(spectator2);

            assertEquals(before, h.inventory.turnTimerSecondsRemainingForTest(), "merely opening/bootstrapping views must never mutate the canonical deadline");
        }
    }

    @Test
    void reopeningDuringAHitFlightReconstructsTheCurrentFaceDownHop() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            Inventory original = h.inventory.getOrCreateView(alice);
            h.click(alice, BlackjackSlotLayout.ACTION_HIT_SLOT);
            for (int tick = 0; tick < BlackjackTiming.CARD_DEAL_DELAY_TICKS; tick++) {
                h.scheduler.advance(1);
                long hidden = boardSignature(original).stream()
                    .filter(value -> value.startsWith(Material.WHITE_STAINED_GLASS_PANE.name()))
                    .count();
                if (hidden >= 3) {
                    break; // deck token + dealer hole card + flying card
                }
            }
            long hiddenBefore = boardSignature(original).stream()
                .filter(value -> value.startsWith(Material.WHITE_STAINED_GLASS_PANE.name()))
                .count();
            assertTrue(hiddenBefore >= 3, "setup must stop while the hit card is visibly in flight");
            List<String> beforeClose = boardSignature(original);

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(aliceId));
            Inventory reopened = h.inventory.getOrCreateView(alice);

            assertEquals(beforeClose, boardSignature(reopened),
                "the current hit-flight hop must be present immediately on reopen");
        }
    }

}
