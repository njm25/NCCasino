package org.nc.nccasino.games.Blackjack;

import java.util.Deque;

/**
 * Pure math over a player's committed wager-increment ledger (the plan's
 * {@code pregameWagerIncrements} deque). Selection (picking a chip/All In)
 * never touches this -- only a bet-spot click ("commit") pushes an
 * increment here, after debiting the player's balance elsewhere. Undo Last
 * pops exactly the most recently committed increment; Undo All drains
 * every one. No Bukkit types -- operates on a plain {@link Deque}, so a
 * test can exercise it with a real {@code java.util.ArrayDeque} directly.
 */
public final class BlackjackWagerLedger {

    private BlackjackWagerLedger() {
    }

    /** Sum of every committed increment currently in the ledger. */
    public static double total(Deque<Double> increments) {
        double sum = 0;
        for (double value : increments) {
            sum += value;
        }
        return sum;
    }

    /** Pushes a newly-committed increment onto the ledger. {@code amount} must be positive. */
    public static void commit(Deque<Double> increments, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("committed amount must be positive: " + amount);
        }
        increments.addLast(amount);
    }

    /** Pops and returns the most recently committed increment, or null if the ledger is empty. */
    public static Double undoLast(Deque<Double> increments) {
        return increments.pollLast();
    }

    /** Drains every committed increment, returning the total that was refunded (0 if the ledger was already empty). */
    public static double undoAll(Deque<Double> increments) {
        double refund = total(increments);
        increments.clear();
        return refund;
    }
}
