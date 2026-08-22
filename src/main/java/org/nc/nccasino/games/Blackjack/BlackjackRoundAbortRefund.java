package org.nc.nccasino.games.Blackjack;

import java.util.List;

/**
 * Pure computation of a full round-abort refund for one player -- no
 * Bukkit types, no currency movement of its own (the controller performs
 * the actual deposit). A shoe-exhaustion round abort must refund every
 * debit of that round: the original wager, any split wagers, any double
 * wagers, and any insurance stake -- since a {@link BlackjackHand}'s own
 * {@link BlackjackHand#getWager()} already reflects doubling in place, and
 * every hand a split produced (each carrying its own wager equal to
 * exactly one matching debit) lives in the player's own hand list, summing
 * every hand's current wager plus any insurance stake covers the complete
 * set without double- or under-counting.
 */
public final class BlackjackRoundAbortRefund {

    private BlackjackRoundAbortRefund() {
    }

    /**
     * @param hands               every hand currently in this player's queue (original plus any splits), or null/empty if they never got dealt in
     * @param insuranceStakeOrZero the amount this player staked on insurance this round, or 0
     */
    public static double totalRefundForPlayer(List<BlackjackHand> hands, double insuranceStakeOrZero) {
        double total = Math.max(insuranceStakeOrZero, 0.0);
        if (hands != null) {
            for (BlackjackHand hand : hands) {
                total += hand.getWager();
            }
        }
        return total;
    }
}
