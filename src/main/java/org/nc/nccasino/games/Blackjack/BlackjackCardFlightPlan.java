package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Pure slot-path geometry for a single dealt card's flight from the deck
 * token's resting slot to its final destination -- no timing, no rendering,
 * no Card selection, just the ordered sequence of intermediate slots the
 * face-down icon hops through.
 *
 * <p>A player's card shoots straight up the deck's own column until it
 * reaches the destination's row, then turns and slides left/right along
 * that row into the exact slot (a no-op vertical leg for the bottom seat,
 * whose row the deck already shares). The dealer's own cards do the mirror
 * image: they slide along the deck's row first, then drop straight down
 * into the dealer's row -- see {@link #path(int, int, boolean)}.
 *
 * <p>A dealer card can never fall back to a vertical-first leg the way a
 * player's card does: the deck's own column sits directly above the
 * dealer's own HEAD slot ({@link BlackjackSlotLayout#DEALER_INPLAY_HEAD_SLOT}
 * {@code == DECK_HOME_SLOT + SEAT_ROW_WIDTH}), so any drop straight down out
 * of the deck's column lands on the dealer's head first, and the flight
 * animation would then slide across it and erase it. Whenever the row-first
 * leg isn't safe -- see {@link #dealerRowLegClear} -- a dealer card must
 * instead use {@link #dealerDoorPath}, which never touches the deck's row
 * (or the head slot) at all.
 */
public final class BlackjackCardFlightPlan {

    private BlackjackCardFlightPlan() {
    }

    /**
     * @param originSlot the deck token's current resting slot
     * @param targetSlot the card's final destination
     * @param dealerCard true for a card landing in the dealer's own row (horizontal leg first), false for a player's card (vertical leg first)
     * @return the ordered path including both {@code originSlot} (index 0) and {@code targetSlot} (last) -- every intermediate slot actually hopped through
     */
    public static List<Integer> path(int originSlot, int targetSlot, boolean dealerCard) {
        List<Integer> path = new ArrayList<>();
        path.add(originSlot);

        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int originRow = originSlot / width;
        int originCol = originSlot % width;
        int targetRow = targetSlot / width;
        int targetCol = targetSlot % width;

        if (dealerCard) {
            int col = originCol;
            while (col != targetCol) {
                col += Integer.signum(targetCol - col);
                path.add(originRow * width + col);
            }
            int row = originRow;
            while (row != targetRow) {
                row += Integer.signum(targetRow - row);
                path.add(row * width + col);
            }
        } else {
            int row = originRow;
            while (row != targetRow) {
                row += Integer.signum(targetRow - row);
                path.add(row * width + originCol);
            }
            int col = originCol;
            while (col != targetCol) {
                col += Integer.signum(targetCol - col);
                path.add(targetRow * width + col);
            }
        }

        return path;
    }

    /**
     * Whether a dealer card's normal {@link #path} (row-first along the
     * deck's row, then drop into the dealer's row) is safe to use: neither
     * the deck's own origin slot nor any intermediate slot along that
     * row-first leg holds a real card. False whenever the bottom seat's
     * hand has grown far enough out to block the sweep (or swallow the
     * deck's own resting slot outright) -- the caller must use {@link
     * #dealerDoorPath} instead in that case, never a vertical-first detour
     * (see the class doc for why that's never safe for a dealer card).
     *
     * @param isOccupied reports whether a slot currently shows a real dealt card
     */
    public static boolean dealerRowLegClear(int originSlot, int targetSlot, IntPredicate isOccupied) {
        if (isOccupied.test(originSlot)) {
            return false;
        }
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int originRow = originSlot / width;
        int col = originSlot % width;
        int targetCol = targetSlot % width;
        while (col != targetCol) {
            col += Integer.signum(targetCol - col);
            if (isOccupied.test(originRow * width + col)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A dealer card's fallback path for whenever {@link #dealerRowLegClear}
     * is false -- there's no deck icon safely reachable, so the card
     * instead originates from the dealer's own row, right of the door
     * ({@link BlackjackSlotLayout#TURN_TIMER_SLOT}, always clear of player
     * cards and never the head slot), and slides straight across to the
     * target column. Never touches the deck's row, or the head slot, at
     * all.
     *
     * @param targetSlot the dealer card's destination -- must be in the same row as {@link BlackjackSlotLayout#TURN_TIMER_SLOT}
     * @return the ordered path including both the door-adjacent origin (index 0) and {@code targetSlot} (last)
     */
    public static List<Integer> dealerDoorPath(int targetSlot) {
        int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
        int originSlot = BlackjackSlotLayout.TURN_TIMER_SLOT;
        int row = originSlot / width;
        int targetCol = targetSlot % width;

        List<Integer> path = new ArrayList<>();
        path.add(originSlot);
        int col = originSlot % width;
        while (col != targetCol) {
            col += Integer.signum(targetCol - col);
            path.add(row * width + col);
        }
        return path;
    }
}
