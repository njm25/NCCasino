package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression coverage for the race between the dealer's own walk-down
 * animation and the shuffle it hands off to (see
 * BlackjackInventory#startDealerInspection's own doc): the dealer's per-step
 * MOVE callback sets dealerHeadSlot to its final value several ticks before
 * the "safety net" completion callback (which used to be the only place
 * shuffleInProgress got set) ever runs, and the readiness-check poll cadence
 * happened to land exactly on that gap tick -- letting activateGame() fire
 * before the shuffle had even started. A coarse scheduler.advance(200) never
 * catches this; only a tick-by-tick walk through the exact window does.
 */
class BlackjackShuffleSequenceIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void dealingNeverStartsWhileTheShuffleIsStillInFlight() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();

            // Tick-by-tick through the window where the dealer's own
            // walk-down has already landed but the shuffle it hands off to
            // is still going -- this is exactly the gap a coarse advance
            // would step right over.
            for (int i = 0; i < 30; i++) {
                h.scheduler.advance(1);
                if (h.inventory.isGameActiveForTest()) {
                    fail("dealing started at tick " + (i + 1) + ", while the shuffle should still be in flight");
                }
            }
        }
    }

    @Test
    void dealingEventuallyStartsOnceTheShuffleGenuinelyFinishes() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(200);

            assertTrue(h.inventory.isGameActiveForTest(), "dealing must eventually start once the shuffle finishes");
            assertTrue(h.inventory.activeHandCardCountForTest(id) > 0, "the committed player must actually receive a hand");
        }
    }

    @Test
    void temporaryShuffleDeckSpotIsClearedWhenDeckReturnsHome() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            for (int i = 0; i < 120 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }

            assertTrue(h.inventory.isGameActiveForTest(), "setup: shuffle must finish and dealing must begin");
            assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, h.inventory.dealerDeckTokenSlotForTest(),
                "the canonical deck token must be back at its ordinary home before dealing");
            assertEquals(Material.GREEN_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(alice).getItem(BlackjackShuffleAnimationPlan.CENTER_SLOT).getType(),
                "the temporary center shuffle spot must be plain felt, never a stuck duplicate deck");
        }
    }
}
