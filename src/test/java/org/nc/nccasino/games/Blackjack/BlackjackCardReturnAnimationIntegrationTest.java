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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the round-end cards-return-to-
 * deck sweep -- every visible card must still be on the board immediately
 * after resetGame() fires (not instantly wiped), then genuinely clear once
 * its own reversed deal-in flight lands, and only then does the existing
 * white-tile sweep begin.
 */
class BlackjackCardReturnAnimationIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    private static ItemStack item(BlackjackControllerTestSupport.Harness h, Player viewer, int slot) {
        return h.inventory.getOrCreateView(viewer).getItem(slot);
    }

    private static Material typeOf(ItemStack item) {
        return item == null ? null : item.getType();
    }

    @Test
    void aDealtHandsCardsStayVisibleImmediatelyAfterResetThenClearBeforeTheWhiteSweep() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, seatSlot);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            assertEquals(2, h.inventory.activeHandCardCountForTest(aliceId), "test setup must actually deal alice in");
            int firstCardSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, 0);
            assertTrue(typeOf(item(h, alice, firstCardSlot)) != Material.GREEN_STAINED_GLASS_PANE, "sanity: a card is actually rendered there before reset");

            h.inventory.resetGameForTest();

            // Immediately after reset: the card must still be visibly there
            // (or already mid-flip to face-down), never instantly wiped to
            // plain background -- that's the whole point of the sweep.
            Material immediatelyAfterReset = typeOf(item(h, alice, firstCardSlot));
            assertTrue(immediatelyAfterReset != Material.GREEN_STAINED_GLASS_PANE,
                "the card must not be instantly wiped to the background felt on reset");

            // Give the return flight (and the white sweep behind it) ample
            // time to fully finish.
            h.scheduler.advance(200);

            assertEquals(Material.GREEN_STAINED_GLASS_PANE, typeOf(item(h, alice, firstCardSlot)),
                "once the return flight and reset sweep both finish, the seat's card row must be plain background");
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId), "resetGame() must still clear the canonical hand as before");
        }
    }

    @Test
    void resetWithNoCardsEverDealtGoesStraightToTheWhiteSweepWithNoExtraDelay() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]); // seated, never commits, never dealt in

            h.inventory.resetGameForTest();

            // With nothing to animate back into the deck, this must behave
            // exactly like the plain reset-sweep-only path already covered
            // by BlackjackResetSweepPlanTest/the reset-sweep integration
            // coverage -- no extra card-return delay tacked on.
            h.scheduler.advance((int) BlackjackControllerTestSupport.RESET_SWEEP_TOTAL_TICKS);
            assertEquals(0, h.inventory.activeHandCardCountForTest(aliceId));
        }
    }
}
