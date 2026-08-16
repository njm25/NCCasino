package org.nc.nccasino.games.Blackjack;

/**
 * Pure staleness check for the dealer's own turn sequence (reveal,
 * recursive draws, and the delayed {@code finishGame} calls at the end of
 * {@code BlackjackInventory#dealDealerCardsUntilSeventeen}). The controller
 * genuinely delegates to {@link #isStale} from
 * {@code isStaleDealerSequenceCallback} -- this is not a parallel
 * simulation of its behavior, it is the behavior.
 *
 * <p>Exists specifically to prevent the reproduction the plan calls out: the
 * dealer schedules a delayed {@code finishGame}, the last player leaves
 * (cancelling/resetting the table, which bumps {@code roundGeneration}),
 * another player reseats and commits a new wager before the stale callback
 * fires -- without this check, that stale callback would settle/reset the
 * new round's fresh state.
 */
public final class BlackjackDealerSequenceGuard {

    private BlackjackDealerSequenceGuard() {
    }

    /**
     * @param capturedRoundGeneration the round generation captured once when {@code startDealerTurn} began the whole sequence
     * @param currentRoundGeneration  the table's live round generation
     * @param currentlyGameActive     the table's live {@code gameActive} flag
     * @return true if a callback belonging to this dealer sequence is no longer valid and must no-op
     */
    public static boolean isStale(long capturedRoundGeneration, long currentRoundGeneration, boolean currentlyGameActive) {
        return capturedRoundGeneration != currentRoundGeneration || !currentlyGameActive;
    }
}
