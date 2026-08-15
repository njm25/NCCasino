package org.nc.nccasino.games.Blackjack;

import java.util.List;

/**
 * Locale-neutral slot constants and slot math for the 54-slot, 5-seat
 * Blackjack board, extracted so BlackjackInventory and BlackjackView agree
 * on layout without either duplicating magic numbers.
 *
 * <p>The board has phase-dependent layouts sharing the same 54 slots. Each
 * seat is a 9-wide row: head, then a permanent bet spot immediately after
 * it, then up to {@link #SEAT_CARD_CAPACITY} card cells. The bottom row
 * (45-53) is phase-dependent -- see the "Slot Map" section of the table
 * redesign plan for the full picture:
 * <ul>
 *   <li>Lobby/unseated: door (45), brown edge glass (46), 47-52 unused, 53
 *       empty (the dealer's idle position is slot {@link #DEALER_LOBBY_HEAD_SLOT},
 *       not the bottom row).</li>
 *   <li>Seated wager phase: Undo All (45), Undo Last (46), chip
 *       denominations (47-51, see {@code ChipSlots}), All In (52), door (53).</li>
 *   <li>Active play: door (45, steady), brown edge glass/turn-timer (46),
 *       dealer's 3rd+ cards OR the fixed action row (47-50, mutually
 *       exclusive -- the dealer never draws until every player's turn has
 *       resolved), dealer's up-card (52), dealer's head (53).</li>
 * </ul>
 * Callers must pick the helper that matches the current phase rather than
 * assume a slot is simultaneously, say, a bet spot and a card slot.
 */
public final class BlackjackSlotLayout {

    private BlackjackSlotLayout() {
    }

    // --- Seats (5), table order ---
    public static final int[] SEAT_SLOTS = {0, 9, 18, 27, 36};
    public static final int SEAT_ROW_WIDTH = 9;
    /** How many visible card cells exist in a seat's row, immediately after its head+bet-spot. */
    public static final int SEAT_CARD_CAPACITY = 7;

    // --- Dealer position-as-state: canonical current slot is tracked by the
    // caller (BlackjackFrame#dealerHeadSlot / BlackjackInventory's own
    // field), these are just the two endpoints. ---
    /** The dealer's lobby/idle head position -- top-right of the dealer row, matching the U-path's own start. */
    public static final int DEALER_LOBBY_HEAD_SLOT = 8;
    /** The dealer's permanent head position once the start-transition animation delivers it there. */
    public static final int DEALER_INPLAY_HEAD_SLOT = 53;
    /** Active play only: dealer's first face-up card. */
    public static final int DEALER_UP_CARD_SLOT = 52;
    /** Active play only: dealer's hidden/second card. */
    public static final int DEALER_HOLE_CARD_SLOT = 51;
    /** Leftmost slot a dealer card can ever occupy (52 down to 47, six cells total). */
    public static final int DEALER_CARD_ROW_FIRST_SLOT = 47;
    /** How many dealer card cells exist (up-card + hole + four more, 52 down to 47). */
    public static final int DEALER_CARD_CAPACITY = 6;

    // --- Bottom row (45-53), pregame/seated-wager phase (viewer is seated) ---
    public static final int UNDO_ALL_SLOT = 45;
    public static final int UNDO_LAST_SLOT = 46;
    public static final int ALL_IN_SLOT = 52;
    public static final int PREGAME_EXIT_SLOT = 53;

    // --- Bottom row (45-53), lobby/unseated (viewer has not sat down yet) ---
    // Numerically the same slots as the active-phase exit/edge-glass below
    // (45/46) -- named separately since they're conceptually a different
    // phase's items, per the table redesign plan's slot map.
    public static final int UNSEATED_EXIT_SLOT = 45;
    public static final int UNSEATED_EDGE_GLASS_SLOT = 46;

    // --- Bottom row (45-53), active play ---
    public static final int ACTIVE_EXIT_SLOT = 45;
    /** Brown edge glass outside a player decision; the configurable turn timer renders here while one is active. */
    public static final int TURN_TIMER_SLOT = 46;
    /** Fixed-identity action slots -- never dynamically centered. Doubles as the dealer's 3rd+ card cells (mutually exclusive, see class doc). */
    public static final int ACTION_HIT_SLOT = 47;
    public static final int ACTION_STAND_SLOT = 48;
    public static final int ACTION_DOUBLE_SLOT = 49;
    public static final int ACTION_SPLIT_SLOT = 50;
    public static final int ACTION_ROW_FIRST_SLOT = 47;
    public static final int ACTION_ROW_LAST_SLOT = 50;

    // --- Insurance decision (reuses the action-row slots -- mutually
    // exclusive with normal turn actions, since insurance is always
    // decided before any player's turn begins) ---
    /** Barrier "No" -- numerically the same slot as ACTION_STAND_SLOT (48), never live at the same time. */
    public static final int INSURANCE_NO_SLOT = 48;
    /** Totem of Undying "Yes" -- numerically the same slot as ACTION_DOUBLE_SLOT (49), never live at the same time. */
    public static final int INSURANCE_YES_SLOT = 49;

    /** The permanent bet-spot slot immediately after a seat's head. */
    public static int betSlipSlot(int seatSlot) {
        return seatSlot + 1;
    }

    /**
     * The Nth (0-indexed) card slot in a seated player's row, beginning
     * immediately after the head+bet-spot. Bounded to
     * {@link #SEAT_CARD_CAPACITY} visible slots -- callers with a longer
     * hand must stop rendering new cards into the row rather than escape
     * into the next row (see BlackjackInventory's bounded card rendering).
     */
    public static int playerCardSlot(int seatSlot, int cardIndex) {
        if (cardIndex < 0 || cardIndex >= SEAT_CARD_CAPACITY) {
            throw new IllegalArgumentException("cardIndex out of the seat's visible row: " + cardIndex);
        }
        return seatSlot + 2 + cardIndex;
    }

    /**
     * The Nth (0-indexed) dealer card slot. Cards grow leftward starting at
     * the up-card (52): index 0 = up-card, 1 = hole card, 2-5 continue
     * through 50, 49, 48, 47.
     */
    public static int dealerCardSlot(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= DEALER_CARD_CAPACITY) {
            throw new IllegalArgumentException("cardIndex out of the dealer's card row: " + cardIndex);
        }
        return DEALER_UP_CARD_SLOT - cardIndex;
    }

    /** Pregame countdown clock slot for a seated player -- overlays that seat's first card cell (mutual exclusion, no cards dealt yet). */
    public static int pregameCountdownSlot(int seatSlot) {
        return seatSlot + 2;
    }

    /** Private per-seat insurance decision countdown slot. */
    public static int insuranceTimerSlot(int seatSlot) {
        return seatSlot + 4;
    }

    public static boolean isSeatSlot(int slot) {
        for (int seat : SEAT_SLOTS) {
            if (seat == slot) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBetSlipSlot(int slot) {
        for (int seat : SEAT_SLOTS) {
            if (betSlipSlot(seat) == slot) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActionRowSlot(int slot) {
        return slot >= ACTION_ROW_FIRST_SLOT && slot <= ACTION_ROW_LAST_SLOT;
    }

    /** Seats in table order (0, 9, 18, 27, 36), for deterministic dealing/turn iteration. */
    public static List<Integer> orderedSeatSlots() {
        return List.of(SEAT_SLOTS[0], SEAT_SLOTS[1], SEAT_SLOTS[2], SEAT_SLOTS[3], SEAT_SLOTS[4]);
    }

    /**
     * The dealer's start-transition U-path: lobby head (8) across the top of
     * the dealer/seat-0 row, down the left edge past every seat head, then
     * along the bottom row into the in-play head slot (53). Pure slot
     * sequence only -- timing/weighting is BlackjackDealerInspectionPlan's
     * job (a later phase).
     */
    public static List<Integer> dealerUPath() {
        return List.of(8, 7, 6, 5, 4, 3, 2, 11, 20, 29, 38, 47, 48, 49, 50, 51, 52, 53);
    }
}
