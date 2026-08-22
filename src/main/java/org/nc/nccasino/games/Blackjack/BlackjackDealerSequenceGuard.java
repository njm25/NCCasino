package org.nc.nccasino.games.Blackjack;

/**
 * Pure staleness check for the dealer's own turn sequence (reveal,
 * recursive draws, the shoe-abort decision, and the delayed
 * {@code finishGame} calls at the end of
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
 *
 * <p>{@code roundGeneration} alone isn't quite enough: it's possible in
 * principle for two dealer sequences to be initiated within the very same
 * round generation (e.g. some future code path re-entering
 * {@code startDealerTurn} without an intervening round change). A
 * dedicated dealer-sequence token, bumped every time a dealer sequence
 * begins, distinguishes those two sequences even though they'd otherwise
 * share an identical round generation -- the first sequence's callbacks
 * become stale the instant the second begins.
 */
public final class BlackjackDealerSequenceGuard {

    private BlackjackDealerSequenceGuard() {
    }

    /**
     * @param capturedRoundGeneration    the round generation captured once when {@code startDealerTurn} began the whole sequence
     * @param currentRoundGeneration     the table's live round generation
     * @param capturedDealerSequenceToken the dealer-sequence token captured at that same moment
     * @param currentDealerSequenceToken  the table's live dealer-sequence token, bumped every time a new dealer sequence begins
     * @param currentlyGameActive        the table's live {@code gameActive} flag
     * @return true if a callback belonging to this dealer sequence is no longer valid and must no-op
     */
    public static boolean isStale(
        long capturedRoundGeneration,
        long currentRoundGeneration,
        int capturedDealerSequenceToken,
        int currentDealerSequenceToken,
        boolean currentlyGameActive
    ) {
        return capturedRoundGeneration != currentRoundGeneration
            || capturedDealerSequenceToken != currentDealerSequenceToken
            || !currentlyGameActive;
    }
}
