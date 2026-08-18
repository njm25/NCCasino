package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;

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
 * into the dealer's row.
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
}
