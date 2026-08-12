package org.nc.nccasino.games.Blackjack;

/**
 * Locale-neutral slot constants and slot math for the 54-slot Blackjack
 * board, extracted so BlackjackInventory and BlackjackView agree on layout
 * without either duplicating magic numbers. Mirrors the layout that has
 * always been hard-coded across BlackjackInventory -- this class does not
 * change any slot position, it only names the ones production already uses.
 */
public final class BlackjackSlotLayout {

    private BlackjackSlotLayout() {
    }

    public static final int DEALER_HEAD_SLOT = 0;
    public static final int LEVER_SLOT = 1;
    public static final int DEALER_FIRST_CARD_SLOT = 2;
    public static final int DEALER_HIDDEN_CARD_SLOT = 3;

    public static final int[] CHAIR_SLOTS = {9, 18, 27};

    public static final int HIT_SLOT = 36;
    public static final int STAND_SLOT = 37;
    public static final int DOUBLE_DOWN_SLOT = 38;
    public static final int UNDO_ALL_SLOT = 45;
    public static final int UNDO_LAST_SLOT = 46;
    public static final int ALL_IN_SLOT = 52;
    public static final int LEAVE_EXIT_SLOT = 53;

    /** The betting-paper slot immediately after a seat's chair. */
    public static int betSlot(int seatSlot) {
        return seatSlot + 1;
    }

    /** The Nth (0-indexed) card slot in a seated player's row. */
    public static int seatCardSlot(int seatSlot, int cardIndex) {
        return seatSlot + 2 + cardIndex;
    }

    /** The Nth (0-indexed) dealer card slot, starting at DEALER_FIRST_CARD_SLOT. */
    public static int dealerCardSlot(int cardIndex) {
        return DEALER_FIRST_CARD_SLOT + cardIndex;
    }

    public static boolean isChairSlot(int slot) {
        for (int chair : CHAIR_SLOTS) {
            if (chair == slot) {
                return true;
            }
        }
        return false;
    }
}
