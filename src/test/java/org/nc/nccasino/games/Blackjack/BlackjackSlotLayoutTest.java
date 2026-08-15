package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;

class BlackjackSlotLayoutTest {

    // --- Five seat/head slots ---

    @Test
    void thereAreExactlyFiveSeatsInTableOrder() {
        assertArrayEquals(new int[] {0, 9, 18, 27, 36}, BlackjackSlotLayout.SEAT_SLOTS);
        assertEquals(List.of(0, 9, 18, 27, 36), BlackjackSlotLayout.orderedSeatSlots());
    }

    @Test
    void seatSlotsAreRecognizedAndNothingElseIs() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertTrue(BlackjackSlotLayout.isSeatSlot(seat));
        }
        assertFalse(BlackjackSlotLayout.isSeatSlot(BlackjackSlotLayout.betSlipSlot(BlackjackSlotLayout.SEAT_SLOTS[0])));
        assertFalse(BlackjackSlotLayout.isSeatSlot(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT));
    }

    // --- Pregame bet-spot positions ---

    @Test
    void betSlipIsImmediatelyAfterEachSeatsHead() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertEquals(seat + 1, BlackjackSlotLayout.betSlipSlot(seat));
            assertTrue(BlackjackSlotLayout.isBetSlipSlot(seat + 1));
        }
    }

    @Test
    void nonSlipSlotsAreNotRecognizedAsBetSlips() {
        assertFalse(BlackjackSlotLayout.isBetSlipSlot(BlackjackSlotLayout.SEAT_SLOTS[0]));
        assertFalse(BlackjackSlotLayout.isBetSlipSlot(BlackjackSlotLayout.SEAT_SLOTS[0] + 2));
    }

    // --- Active player card positions beginning two slots after each head ---

    @Test
    void playerCardSlotsStartTwoAfterTheHeadAndCountUp() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertEquals(seat + 2, BlackjackSlotLayout.playerCardSlot(seat, 0));
            assertEquals(seat + 3, BlackjackSlotLayout.playerCardSlot(seat, 1));
            assertEquals(seat + 8, BlackjackSlotLayout.playerCardSlot(seat, 6));
        }
    }

    // --- Card rendering never leaves its seven-slot row ---

    @Test
    void playerCardSlotRejectsIndexesOutsideTheSevenSlotRow() {
        int seat = BlackjackSlotLayout.SEAT_SLOTS[0];
        assertThrows(IllegalArgumentException.class, () -> BlackjackSlotLayout.playerCardSlot(seat, -1));
        assertThrows(IllegalArgumentException.class, () -> BlackjackSlotLayout.playerCardSlot(seat, 7));
    }

    // --- Dealer head positions (lobby vs. in-play) ---

    @Test
    void dealerHeadHasALobbyPositionAndAnInPlayPosition() {
        assertEquals(8, BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT);
        assertEquals(53, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT);
    }

    // --- Dealer card positions, growing leftward from the up-card ---

    @Test
    void dealerCardSlotsGrowLeftwardFromTheUpCard() {
        assertEquals(52, BlackjackSlotLayout.DEALER_UP_CARD_SLOT);
        assertEquals(51, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT);
        assertEquals(BlackjackSlotLayout.DEALER_UP_CARD_SLOT, BlackjackSlotLayout.dealerCardSlot(0));
        assertEquals(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT, BlackjackSlotLayout.dealerCardSlot(1));
        assertEquals(47, BlackjackSlotLayout.dealerCardSlot(5));
    }

    @Test
    void dealerCardSlotRejectsIndexesOutsideItsSixSlotRow() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackSlotLayout.dealerCardSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> BlackjackSlotLayout.dealerCardSlot(6));
    }

    // --- No active card slot collides with another row or the bottom bar ---

    @Test
    void noSeatsCardRowEverOverlapsAnotherSeatsRowOrTheBottomBar() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            for (int cardIndex = 0; cardIndex < BlackjackSlotLayout.SEAT_CARD_CAPACITY; cardIndex++) {
                int cardSlot = BlackjackSlotLayout.playerCardSlot(seat, cardIndex);

                // Stays within this seat's own nine-wide row.
                assertTrue(cardSlot / BlackjackSlotLayout.SEAT_ROW_WIDTH == seat / BlackjackSlotLayout.SEAT_ROW_WIDTH,
                    "card slot " + cardSlot + " escaped seat " + seat + "'s row");

                // Never lands on another seat's head.
                for (int otherSeat : BlackjackSlotLayout.SEAT_SLOTS) {
                    assertFalse(cardSlot == otherSeat, "card slot " + cardSlot + " collides with seat head " + otherSeat);
                }
            }
        }
    }

    @Test
    void dealerCardRowNeverOverlapsBelowItsSixSlots() {
        for (int cardIndex = 0; cardIndex < BlackjackSlotLayout.DEALER_CARD_CAPACITY; cardIndex++) {
            int cardSlot = BlackjackSlotLayout.dealerCardSlot(cardIndex);
            assertTrue(cardSlot >= BlackjackSlotLayout.DEALER_CARD_ROW_FIRST_SLOT && cardSlot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT,
                "dealer card slot " + cardSlot + " escaped the dealer row");
        }
    }

    // --- Lobby/countdown wager controls remain unchanged ---

    @Test
    void pregameWagerControlsAreUnchanged() {
        assertEquals(45, BlackjackSlotLayout.UNDO_ALL_SLOT);
        assertEquals(46, BlackjackSlotLayout.UNDO_LAST_SLOT);
        assertEquals(47, ChipSlots.FIRST_SLOT);
        assertEquals(51, ChipSlots.LAST_SLOT);
        assertEquals(52, BlackjackSlotLayout.ALL_IN_SLOT);
        assertEquals(53, BlackjackSlotLayout.PREGAME_EXIT_SLOT);
    }

    // --- Active play: exit stays at 45, dealer occupies the far right ---

    @Test
    void activeExitIsAtFortyFiveAndDealerOccupiesTheFarRight() {
        assertEquals(45, BlackjackSlotLayout.ACTIVE_EXIT_SLOT);
        assertEquals(53, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT);
        assertEquals(BlackjackSlotLayout.PREGAME_EXIT_SLOT, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT);
    }

    // --- Fixed-identity action row ---

    @Test
    void actionRowSpansFortySevenToFifty() {
        assertEquals(47, BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT);
        assertEquals(50, BlackjackSlotLayout.ACTION_ROW_LAST_SLOT);
        assertTrue(BlackjackSlotLayout.isActionRowSlot(49));
        assertFalse(BlackjackSlotLayout.isActionRowSlot(45));
        assertFalse(BlackjackSlotLayout.isActionRowSlot(53));
    }

    @Test
    void actionSlotsAreFixedIdentityNotDynamic() {
        assertEquals(47, BlackjackSlotLayout.ACTION_HIT_SLOT);
        assertEquals(48, BlackjackSlotLayout.ACTION_STAND_SLOT);
        assertEquals(49, BlackjackSlotLayout.ACTION_DOUBLE_SLOT);
        assertEquals(50, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
    }

    // --- Pregame countdown / insurance timer helpers ---

    @Test
    void pregameCountdownSlotOverlaysTheFirstCardCell() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertEquals(BlackjackSlotLayout.playerCardSlot(seat, 0), BlackjackSlotLayout.pregameCountdownSlot(seat));
        }
    }

    @Test
    void insuranceTimerSlotIsSeatPlusFour() {
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertEquals(seat + 4, BlackjackSlotLayout.insuranceTimerSlot(seat));
        }
    }

    // --- Dealer U-path ---

    @Test
    void dealerUPathStartsAtLobbyHeadAndEndsAtInPlayHead() {
        List<Integer> path = BlackjackSlotLayout.dealerUPath();
        assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, path.get(0));
        assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, path.get(path.size() - 1));
        assertEquals(18, path.size());
    }
}
