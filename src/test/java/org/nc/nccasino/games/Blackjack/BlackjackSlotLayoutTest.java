package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlackjackSlotLayoutTest {

    @Test
    void betSlotIsImmediatelyAfterTheChair() {
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            assertEquals(chair + 1, BlackjackSlotLayout.betSlot(chair));
        }
    }

    @Test
    void seatCardSlotsStartTwoAfterTheChairAndCountUp() {
        int chair = BlackjackSlotLayout.CHAIR_SLOTS[0];
        assertEquals(chair + 2, BlackjackSlotLayout.seatCardSlot(chair, 0));
        assertEquals(chair + 3, BlackjackSlotLayout.seatCardSlot(chair, 1));
        assertEquals(chair + 6, BlackjackSlotLayout.seatCardSlot(chair, 4));
    }

    @Test
    void dealerCardSlotsStartAtTheDealerFirstCardSlot() {
        assertEquals(BlackjackSlotLayout.DEALER_FIRST_CARD_SLOT, BlackjackSlotLayout.dealerCardSlot(0));
        assertEquals(BlackjackSlotLayout.DEALER_HIDDEN_CARD_SLOT, BlackjackSlotLayout.dealerCardSlot(1));
        assertEquals(BlackjackSlotLayout.DEALER_FIRST_CARD_SLOT + 5, BlackjackSlotLayout.dealerCardSlot(5));
    }

    @Test
    void chairSlotsAreRecognizedAndNothingElseIs() {
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            assertTrue(BlackjackSlotLayout.isChairSlot(chair));
        }
        assertFalse(BlackjackSlotLayout.isChairSlot(BlackjackSlotLayout.betSlot(BlackjackSlotLayout.CHAIR_SLOTS[0])));
        assertFalse(BlackjackSlotLayout.isChairSlot(BlackjackSlotLayout.HIT_SLOT));
    }

    @Test
    void seatAndDealerCardSlotsNeverCollideWithFixedControlSlots() {
        // The three seats' card rows (up to 7 hit cards deep, generously)
        // must stay clear of the fixed button/chip/lever slots they sit
        // between -- this is exactly the layout BlackjackView must agree
        // with the controller on.
        int[] fixedSlots = {
            BlackjackSlotLayout.DEALER_HEAD_SLOT,
            BlackjackSlotLayout.LEVER_SLOT,
            BlackjackSlotLayout.HIT_SLOT,
            BlackjackSlotLayout.STAND_SLOT,
            BlackjackSlotLayout.DOUBLE_DOWN_SLOT,
            BlackjackSlotLayout.UNDO_ALL_SLOT,
            BlackjackSlotLayout.UNDO_LAST_SLOT,
            BlackjackSlotLayout.ALL_IN_SLOT,
            BlackjackSlotLayout.LEAVE_EXIT_SLOT
        };
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            for (int cardIndex = 0; cardIndex < 6; cardIndex++) {
                int cardSlot = BlackjackSlotLayout.seatCardSlot(chair, cardIndex);
                for (int fixed : fixedSlots) {
                    assertFalse(cardSlot == fixed, "seat card slot " + cardSlot + " collides with fixed slot " + fixed);
                }
            }
        }
    }
}
