package org.nc.nccasino.games.Blackjack;

import java.util.List;

/**
 * Pure depth-first hand-queue ordering for splits -- no Bukkit types.
 * Operates on the caller's own mutable {@code List<BlackjackHand>} (the
 * per-player hand queue already lives in the controller as
 * {@code Map<UUID, List<BlackjackHand>>}); this class only supplies the
 * ordering rule so it stays independently testable and never duplicated.
 *
 * <p>Splitting inserts the new sibling hand immediately after the current
 * hand's position; the current hand finishes fully before the queue
 * advances to the next pending hand. Repeated splits (including
 * resplitting a resplit) preserve this traversal order because each new
 * sibling is always inserted directly after whichever hand it was split
 * from, never appended to the end.
 */
public final class BlackjackSplitQueue {

    private BlackjackSplitQueue() {
    }

    /** Inserts {@code sibling} immediately after {@code currentIndex} in {@code hands} (mutates the list in place). */
    public static void insertSiblingAfterCurrent(List<BlackjackHand> hands, int currentIndex, BlackjackHand sibling) {
        hands.add(currentIndex + 1, sibling);
    }

    /**
     * The index of the next not-done hand strictly after {@code currentIndex},
     * or -1 if every remaining hand in the queue is already done (the
     * player's whole turn is over).
     */
    public static int nextActionableIndex(List<BlackjackHand> hands, int currentIndex) {
        for (int i = currentIndex + 1; i < hands.size(); i++) {
            if (!hands.get(i).isDone()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Resolves a hand by its stable {@code handId} rather than a captured
     * list index -- required for any scheduled callback that spans ticks,
     * since indexes shift as sibling hands are inserted mid-round.
     *
     * @return the matching hand, or null if {@code hands} is null or no hand with that id is present (e.g. the owning player has since left)
     */
    public static BlackjackHand findById(List<BlackjackHand> hands, long handId) {
        if (hands == null) {
            return null;
        }
        for (BlackjackHand hand : hands) {
            if (hand.getHandId() == handId) {
                return hand;
            }
        }
        return null;
    }
}
