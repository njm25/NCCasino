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
 * Controller-level regression coverage for making the Action Timer (slot 46)
 * private to the acting player. The timer is presentation over a single
 * canonical active-decision deadline ({@code turnTimer*} fields on
 * {@link BlackjackInventory}) -- these tests confirm the fan-out/rendering
 * layer never leaks that one deadline into a non-acting viewer's inventory,
 * and never lets rendering itself mutate the canonical deadline.
 */
class BlackjackActionTimerVisibilityIntegrationTest {

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

    /** Seats two players and advances to the point where exactly one of them has an actionable decision. */
    private static UUID seatTwoAndReachActionableTurn(BlackjackControllerTestSupport.Harness h, Player alice, Player bob) {
        h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
        h.inventory.commitWagerForTest(alice, 10.0);
        h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
        h.inventory.commitWagerForTest(bob, 10.0);
        h.inventory.beginStartTransitionForTest();
        h.advanceToActionableTurn(20, 40);
        UUID currentPlayerId = h.inventory.currentPlayerIdForTest();
        assertNotNull(currentPlayerId, "test setup must actually reach an actionable turn");
        return currentPlayerId;
    }

    @Test
    void actingPlayerSeesClockWithCanonicalRemainingTime() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID currentPlayerId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player current = currentPlayerId.equals(alice.getUniqueId()) ? alice : bob;

            ItemStack item = timerItem(h, current);
            assertNotNull(item);
            assertEquals(Material.CLOCK, item.getType(), "the acting player must see the clock");
            // The rendered amount reflects whatever the last tick painted,
            // which can be one ahead of the canonical field it just
            // decremented to (see the reset-semantics test class doc on the
            // render-then-decrement task ordering) -- assert a sane positive
            // countdown value rather than exact equality to the live field.
            assertTrue(item.getAmount() > 0 && item.getAmount() <= 20, "the clock must show a plausible remaining-time count");
        }
    }

    @Test
    void anotherSeatedPlayerSeesBrownNotTheClock() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID currentPlayerId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player nonCurrent = currentPlayerId.equals(alice.getUniqueId()) ? bob : alice;

            ItemStack item = timerItem(h, nonCurrent);
            assertNotNull(item);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, item.getType(),
                "a player must never see another player's countdown");
        }
    }

    @Test
    void spectatorSeesBrownNotTheClock() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertNotNull(h.inventory.currentPlayerIdForTest());

            Player spectator = h.seatOnlinePlayer(UUID.randomUUID(), "Spectator"); // opens the table but never sits

            ItemStack item = timerItem(h, spectator);
            assertNotNull(item);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, item.getType(), "a spectator must never see the acting player's countdown");
        }
    }

    @Test
    void advancingTheTurnRemovesTheFormerActorsClockAndShowsItOnlyToTheNewActor() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID firstPlayerId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player first = firstPlayerId.equals(alice.getUniqueId()) ? alice : bob;
            Player second = first == alice ? bob : alice;

            assertEquals(Material.CLOCK, timerItem(h, first).getType());

            // Stand ends the first player's decision and advances the turn.
            h.click(first, BlackjackSlotLayout.ACTION_STAND_SLOT);
            h.scheduler.advance(60);
            h.advanceToActionableTurn(20, 40);

            UUID newCurrentPlayerId = h.inventory.currentPlayerIdForTest();
            assertEquals(second.getUniqueId(), newCurrentPlayerId, "test setup must actually advance to the second player");

            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, first).getType(),
                "the former actor's slot must immediately return to brown");
            assertEquals(Material.CLOCK, timerItem(h, second).getType(),
                "only the new actor sees the fresh clock");
        }
    }

    @Test
    void disablingTheTimerPaintsBrownForEveryone() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(
            java.util.Map.of("turn-timer.enabled", false)
        )) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            for (int i = 0; i < 40 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(20);
            }
            assertTrue(h.inventory.isGameActiveForTest());

            Player spectator = h.seatOnlinePlayer(UUID.randomUUID(), "Spectator");

            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, alice).getType(),
                "even the current/acting player sees brown when the feature is disabled");
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, spectator).getType());
        }
    }

    @Test
    void renderingForAnotherViewerNeverMutatesTheCanonicalDeadline() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            int before = h.inventory.turnTimerSecondsRemainingForTest();
            UUID ownerBefore = h.inventory.turnTimerPlayerIdForTest();

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            timerItem(h, spectator); // bootstraps and reads a brand-new spectator view
            timerItem(h, spectator); // repaint/re-read must also be a no-op

            assertEquals(before, h.inventory.turnTimerSecondsRemainingForTest(), "opening/reading another viewer's inventory must never mutate the canonical deadline");
            assertEquals(ownerBefore, h.inventory.turnTimerPlayerIdForTest());
        }
    }

    @Test
    void liveDisablingTheTimerPatchesTheSameRunningControllerWithoutCancellingAnInFlightDecision() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID firstId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player first = firstId.equals(alice.getUniqueId()) ? alice : bob;
            Player second = first == alice ? bob : alice;

            assertEquals(Material.CLOCK, timerItem(h, first).getType());

            // This is exactly what BlackjackMenu's turn-timer toggle calls
            // on the already-running BlackjackInventory instance -- a true
            // live patch, never a reload/replace of the controller.
            h.inventory.setTurnTimerEnabledLive(false);

            // Per BlackjackInventory#setTurnTimerEnabledLive's own contract,
            // disabling mid-decision does not cancel an already-running
            // deadline -- it only takes effect the next time a fresh
            // deadline would otherwise start. The current decision (and
            // committed wagers) must be completely undisturbed.
            assertEquals(Material.CLOCK, timerItem(h, first).getType(),
                "a live disable must never cancel/repaint away an in-flight decision's own clock");
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > 0);
            assertTrue(h.inventory.isGameActiveForTest(), "the controller itself must remain the same running instance, never reloaded/destroyed");

            // The NEXT actionable decision (Stand advances to Bob) must now start with no timer at all --
            // turnTimerSecondsRemainingForTest() never becomes positive once disabled, so advanceToActionableTurn
            // would spin for nothing; poll on currentPlayerId instead (mirrors idleBrownGlassShowsWhenTurnTimerIsDisabled).
            h.click(first, BlackjackSlotLayout.ACTION_STAND_SLOT);
            for (int i = 0; i < 40 && !second.getUniqueId().equals(h.inventory.currentPlayerIdForTest()); i++) {
                h.scheduler.advance(20);
            }
            assertEquals(second.getUniqueId(), h.inventory.currentPlayerIdForTest());

            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, second).getType(),
                "once live-disabled, the next fresh decision must never start a timer");
        }
    }

    @Test
    void aNormalTimerTickDoesNotCreateSeparateViewerSpecificDeadlines() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID currentPlayerId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player current = currentPlayerId.equals(alice.getUniqueId()) ? alice : bob;
            Player other = current == alice ? bob : alice;

            int beforeTick = h.inventory.turnTimerSecondsRemainingForTest();
            h.scheduler.advance(60); // several ticks -- exactly how many periods land inside one advance() call is scheduling-sensitive (see the reset-semantics test class doc), so this only needs to strictly decrease, not hit an exact number

            // There is exactly one canonical deadline: after ticking, both
            // viewers' renders still agree with the single turnTimer* state
            // -- the acting player's clock decremented, the other viewer is
            // still brown, and neither view has "its own" separate figure.
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() < beforeTick, "the single canonical deadline must actually tick down over time");
            assertEquals(Material.CLOCK, timerItem(h, current).getType());
            assertTrue(timerItem(h, current).getAmount() > 0, "the acting player's clock must show a positive remaining time");
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, timerItem(h, other).getType());
        }
    }
}
