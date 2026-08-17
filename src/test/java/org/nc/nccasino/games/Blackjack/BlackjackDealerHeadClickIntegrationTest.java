package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the confirmed dealer-head-click
 * defect (audit finding 4): {@code handlePlayerAction} invoked the dealer
 * easter egg for the current player's own click on {@code dealerHeadSlot}
 * but then fell through into ordinary action-slot validation, which always
 * produced a spurious "invalid action" message/sound/repaint on top of the
 * easter egg's own feedback, since the dealer-head slot is never one of the
 * four fixed action slots. Fixed by returning immediately after the easter
 * egg. Non-current players never went through this path at all (a separate
 * branch in the outer dispatcher) and are covered here too, to confirm
 * their behavior is unaffected.
 */
class BlackjackDealerHeadClickIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void currentPlayerDealerHeadClickNeverProducesInvalidActionFeedback() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);

            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            assertNotNull(h.inventory.currentPlayerIdForTest(), "test setup must actually reach an actionable turn");
            assertTrue(alice.getUniqueId().equals(h.inventory.currentPlayerIdForTest()));

            h.click(alice, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT);

            verify(alice, never()).sendMessage(eq("blackjack.invalid-action-spaced"));
            verify(alice, never()).sendMessage(eq("blackjack.invalid-action"));
            // The turn must remain exactly as actionable as it was -- a
            // dealer-head click is not a game action and must not consume
            // or otherwise disturb the current decision.
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > 0);
        }
    }

    @Test
    void nonCurrentPlayerDealerHeadClickIsUnaffected() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);

            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            UUID currentPlayerId = h.inventory.currentPlayerIdForTest();
            assertNotNull(currentPlayerId);
            Player nonCurrent = currentPlayerId.equals(alice.getUniqueId()) ? bob : alice;

            // Never reaches handlePlayerAction at all (a separate dispatch
            // branch in handleClick) -- must behave exactly as before.
            h.click(nonCurrent, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT);

            verify(nonCurrent, never()).sendMessage(eq("blackjack.invalid-action-spaced"));
            verify(nonCurrent, never()).sendMessage(eq("blackjack.invalid-action"));
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > 0, "the current player's own turn must be untouched by another viewer's click");
        }
    }
}
