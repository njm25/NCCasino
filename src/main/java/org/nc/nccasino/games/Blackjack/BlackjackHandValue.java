package org.nc.nccasino.games.Blackjack;

/**
 * Result of evaluating a hand: the hard total (every Ace counted as 1) and
 * the best total (Aces promoted to 11 where it doesn't bust), mirroring the
 * dual soft/hard display the GUI has always shown as "soft/hard".
 */
public final class BlackjackHandValue {
    private final int hardTotal;
    private final int bestTotal;

    public BlackjackHandValue(int hardTotal, int bestTotal) {
        this.hardTotal = hardTotal;
        this.bestTotal = bestTotal;
    }

    public int getHardTotal() {
        return hardTotal;
    }

    public int getBestTotal() {
        return bestTotal;
    }

    public boolean isSoft() {
        return bestTotal != hardTotal;
    }

    public boolean isBust() {
        return bestTotal > 21;
    }
}
