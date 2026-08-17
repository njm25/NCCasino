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
 * Controller-level regression coverage for the confirmed stale-turn-timer-
 * on-bootstrap defect (audit finding 3): {@code bootstrapView} always
 * painted slot 46 as idle brown glass during active play, even when a
 * canonical actionable turn-timer deadline was already running -- a
 * freshly-created view (a late viewer, or any reopen) only got corrected up
 * to ~1 second later, on the running task's own next tick. Fixed by reading
 * the same canonical {@code turnTimer*} state the running task itself uses
 * (via {@code isTurnTimerCanonicallyActive}), so a new view shows the exact
 * remaining time immediately.
 */
class BlackjackViewBootstrapIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void newViewOpenedMidDecisionImmediatelyShowsExactRemainingTime() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertNotNull(h.inventory.currentPlayerIdForTest());
            int canonicalSecondsRemaining = h.inventory.turnTimerSecondsRemainingForTest();
            assertTrue(canonicalSecondsRemaining > 0);

            // A spectator who has never opened the table before now does so
            // mid-decision -- getOrCreateView bootstraps a brand-new view.
            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory spectatorView = h.inventory.getOrCreateView(spectator);

            ItemStack turnTimerItem = spectatorView.getItem(BlackjackSlotLayout.TURN_TIMER_SLOT);
            assertNotNull(turnTimerItem);
            assertEquals(Material.CLOCK, turnTimerItem.getType(), "a live actionable deadline must render as the clock immediately, not the idle brown-glass fallback");
            assertEquals(Math.max(canonicalSecondsRemaining, 1), turnTimerItem.getAmount(), "the exact canonical remaining seconds, not a guess or a reset value");
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
    void idleBrownGlassShowsWhenTurnTimerIsDisabled() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(
            java.util.Map.of("turn-timer.enabled", false)
        )) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();

            // No actionable-turn wait needed -- with the timer disabled,
            // turnTimerSecondsRemainingForTest() never becomes positive, so
            // advanceToActionableTurn would spin for nothing; just advance
            // enough to reach ACTIVE directly.
            for (int i = 0; i < 40 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(20);
            }
            assertTrue(h.inventory.isGameActiveForTest());

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory spectatorView = h.inventory.getOrCreateView(spectator);
            ItemStack turnTimerItem = spectatorView.getItem(BlackjackSlotLayout.TURN_TIMER_SLOT);
            assertNotNull(turnTimerItem);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, turnTimerItem.getType(), "a disabled turn timer must never render the clock, on bootstrap or otherwise");
        }
    }
}
