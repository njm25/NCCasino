package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * One commitment's worst case, as the budget system sees it.
 *
 * <p>Every game reduces its own rules -- reels and paylines, a Roulette
 * portfolio evaluated against all 37 pockets, a Blackjack split, the next
 * Mines tile -- to exactly these two numbers before the shared budget ever
 * sees it. That is the whole seam between game logic and money logic.
 *
 * @param stake the total the player posts, which the dealer keeps if the
 *     player loses
 * @param maxGrossPayout the largest total the dealer could owe back for this
 *     commitment, <em>including</em> the returned stake. Gross rather than net
 *     because that is what actually has to leave the dealer's inventory at
 *     settlement; the net figure is derived here so no caller can get the
 *     subtraction backwards.
 */
public record Exposure(BigDecimal stake, BigDecimal maxGrossPayout) {

    public Exposure {
        stake = Money.of(stake);
        maxGrossPayout = Money.of(maxGrossPayout);
    }

    public static Exposure of(BigDecimal stake, BigDecimal maxGrossPayout) {
        return new Exposure(stake, maxGrossPayout);
    }

    public static Exposure of(long stake, long maxGrossPayout) {
        return new Exposure(Money.of(stake), Money.of(maxGrossPayout));
    }

    /** Nothing at risk -- used for a cancellation or a zero-value commitment. */
    public static Exposure none() {
        return new Exposure(Money.ZERO, Money.ZERO);
    }

    /**
     * The most this commitment can actually cost the house: what it may have
     * to pay out, less the stake it is holding. Clamped at zero, because a
     * commitment that cannot pay more than its stake costs the house nothing
     * and must never be refused as if it did.
     */
    public BigDecimal maxHouseLoss() {
        return Money.clampNonNegative(Money.subtract(maxGrossPayout, stake));
    }

    /** Whether both figures are usable as economic state. */
    public boolean isNumericallySafe() {
        return Money.isSafe(stake)
            && Money.isSafe(maxGrossPayout)
            && Money.isSafe(maxHouseLoss());
    }

    /** Combines two commitments held at the same time, e.g. a Blackjack split hand. */
    public Exposure plus(Exposure other) {
        if (other == null) {
            return this;
        }
        return new Exposure(
            Money.add(stake, other.stake()),
            Money.add(maxGrossPayout, other.maxGrossPayout()));
    }
}
