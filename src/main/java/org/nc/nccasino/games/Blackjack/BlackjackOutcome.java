package org.nc.nccasino.games.Blackjack;

/**
 * Settlement outcome for a player's hand against the dealer, and the wager
 * multiplier that outcome pays. PUSH's 1.0 is documentation only -- the
 * inventory refunds the wager directly rather than routing it through the
 * payout multiplier, and that call path is unchanged by this enum existing.
 */
public enum BlackjackOutcome {
    BLACKJACK(2.5),
    WIN(2.0),
    PUSH(1.0),
    LOSS(0.0),
    BUST(0.0);

    private final double multiplier;

    BlackjackOutcome(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
