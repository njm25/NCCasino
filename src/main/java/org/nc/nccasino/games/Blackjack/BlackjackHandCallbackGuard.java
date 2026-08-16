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
     * The exact expected-state a delayed callback was scheduled for --
     * replaces an earlier ambiguous {@code requireActionable} boolean whose
     * {@code false} value wrongly accepted both states. {@code ACTIONABLE}
     * (the turn timer's ticks/timeout) must be rejected while processing;
     * {@code PROCESSING} (Hit/Double's own render/evaluation callbacks) must
     * equally be rejected once the decision has already become actionable
     * again (e.g. a stale render step from an earlier, now-superseded Hit).
     */
    public enum ExpectedHandState {
        ACTIONABLE,
        PROCESSING
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

    /**
     * The complete expected-state contract for a delayed hand-mutation
     * callback: {@code candidateActiveHand} must match by id+generation
     * (see {@link #matches}) <em>and</em> genuinely be the table's current
     * active hand -- the player must still be seated, still be the table's
     * current player, the table's phase must still be the actionable
     * player-turn phase, and (when {@code requireActionable} is true) the
     * decision must still genuinely be actionable, not mid-processing.
     *
     * <p>{@code expectedState} distinguishes two legitimate callback shapes,
     * and requires an exact match rather than merely tolerating one of
     * them: Hit/Double's own render/evaluation callbacks fire while
     * {@code playerTurnActive} is deliberately false (the action is
     * "processing" -- pass {@link ExpectedHandState#PROCESSING}, which is
     * rejected if the decision has since become actionable again), while
     * the turn timer's ticks/timeout only ever run while a decision is
     * genuinely actionable (pass {@link ExpectedHandState#ACTIONABLE},
     * which is rejected while processing).
     *
     * <p>{@code candidateActiveHand} must already be resolved as
     * {@code activeHandIndex}'s own hand, never merely a hand present
     * somewhere else in the player's queue -- {@code BlackjackInventory}'s
     * own {@code resolveExpectedHand} is responsible for that lookup; this
     * method only judges the already-resolved candidate.
     */
    public static boolean isExpectedActiveHand(
        BlackjackHand candidateActiveHand,
        long expectedHandId,
        int expectedHandGeneration,
        boolean isSeated,
        boolean isCurrentPlayer,
        boolean isActivePhase,
        boolean playerTurnActive,
        ExpectedHandState expectedState
    ) {
        if (!isSeated || !isCurrentPlayer || !isActivePhase) {
            return false;
        }
        boolean stateMatches = switch (expectedState) {
            case ACTIONABLE -> playerTurnActive;
            case PROCESSING -> !playerTurnActive;
        };
        if (!stateMatches) {
            return false;
        }
        return matches(candidateActiveHand, expectedHandId, expectedHandGeneration);
    }
}
