package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackRoundEndInteractionIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.HEARTS, rank));
        }
        return cards;
    }

    @Test
    void everyTableClickIsInertUntilTheRoundEndBoardWipeThenWageringWorksAgain() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);
            h.click(alice, ChipSlots.FIRST_SLOT + 2); // persistent 10-unit selection
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            h.inventory.resetGameForTest();
            assertEquals(BlackjackFrame.Phase.ROUND_END, h.inventory.capturePhaseForTest());

            int balance = h.currencyProvider.getBalance(alice, h.internalName);
            int withdrawals = h.currencyProvider.withdrawAttempts.size();
            int seats = h.inventory.playerSeatsSizeForTest();
            BlackjackWagerSelection selection = h.inventory.selectedWagerForTest(aliceId);
            double ledger = h.inventory.totalRoundRefundForPlayerForTest(aliceId);

            h.click(alice, aliceSeat); // own head / leave seat
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[1]); // another chair
            h.click(alice, BlackjackSlotLayout.betSlipSlot(aliceSeat));
            h.click(alice, ChipSlots.FIRST_SLOT);
            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT);
            h.click(alice, BlackjackSlotLayout.UNDO_LAST_SLOT);
            h.click(alice, BlackjackSlotLayout.UNDO_ALL_SLOT);
            h.click(alice, BlackjackSlotLayout.PREGAME_EXIT_SLOT);

            assertEquals(balance, h.currencyProvider.getBalance(alice, h.internalName));
            assertEquals(withdrawals, h.currencyProvider.withdrawAttempts.size());
            assertEquals(seats, h.inventory.playerSeatsSizeForTest());
            assertTrue(h.inventory.isSeatedForTest(aliceId));
            assertEquals(selection, h.inventory.selectedWagerForTest(aliceId));
            assertEquals(ledger, h.inventory.totalRoundRefundForPlayerForTest(aliceId));
            assertEquals(BlackjackFrame.Phase.ROUND_END, h.inventory.capturePhaseForTest(),
                "no click may start a pregame countdown while cards are returning");

            h.scheduler.advance(BlackjackControllerTestSupport.ROUND_END_ANIMATION_TOTAL_TICKS);
            assertEquals(BlackjackFrame.Phase.LOBBY, h.inventory.capturePhaseForTest());
            h.click(alice, BlackjackSlotLayout.betSlipSlot(aliceSeat));
            assertNotEquals(balance, h.currencyProvider.getBalance(alice, h.internalName));
            assertEquals(withdrawals + 1, h.currencyProvider.withdrawAttempts.size());
            assertTrue(h.inventory.totalRoundRefundForPlayerForTest(aliceId) > 0);
            assertEquals(BlackjackFrame.Phase.COUNTDOWN, h.inventory.capturePhaseForTest());
        }
    }
}
