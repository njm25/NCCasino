package org.nc.nccasino.games.Blackjack;

/**
 * Pure hand-identity check for a delayed per-hand callback (Hit's render/
 * eval steps, Double's render/completion steps, the turn timer's ticks and
 * timeout) -- validates a live {@link BlackjackHand}, already resolved by
 * its stable {@code handId} (see {@link BlackjackSplitQueue#findById}),
 * still carries exactly the {@code handGeneration} that was captured when
 * the callback was scheduled. {@code BlackjackInventory#resolveExpectedHand}
 * genuinely delegates to {@link #matches} for this part of its check (round
 * generation, {@code gameActive}, and seat membership are checked
 * separately, since those require live controller state this class
 * deliberately doesn't touch) -- this is not a parallel simulation.
 *
 * <p>This is what makes an earlier Hit's callback correctly stale once a
 * later action (another Hit, a Stand, a Double, a Split, or a timeout) has
 * superseded the same hand: any of those bump the hand's own
 * {@code handGeneration}, so a callback still holding the earlier-captured
 * value no longer matches.
 */
public final class BlackjackHandCallbackGuard {

    private BlackjackHandCallbackGuard() {
    }

    /**
     * @param hand                    the live hand resolved by id, or null if it no longer exists
     * @param expectedHandId          the handId captured when the callback was scheduled
     * @param expectedHandGeneration  the handGeneration captured at that same moment (or produced by this callback's own prior step)
     * @return true only if {@code hand} is non-null, its id matches, and its generation is still exactly the expected one
     */
    public static boolean matches(BlackjackHand hand, long expectedHandId, int expectedHandGeneration) {
        return hand != null
            && hand.getHandId() == expectedHandId
            && hand.getHandGeneration() == expectedHandGeneration;
    }
}
