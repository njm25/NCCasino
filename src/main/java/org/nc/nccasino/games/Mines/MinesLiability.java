package org.nc.nccasino.games.Mines;

import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;

/**
 * What a Mines board could owe, one tile at a time.
 *
 * <h2>The check happens before the tile is revealed</h2>
 *
 * <p>Mines exposure rises with every safe pick, and the rise is known in
 * advance: the cash-out after {@code n+1} safe tiles is a pure function of the
 * board. So the dealer is asked to cover the <em>next</em> cash-out before the
 * tile is turned over, per section 22 of the design. If it cannot, progression
 * is denied while the board is still undetermined -- the player keeps the
 * cash-out they have already earned, which the dealer is already covering, and
 * nothing random has been decided.
 *
 * <p>Denying after the reveal would be indefensible: the player would have hit
 * a safe tile and then be told the win does not count.
 *
 * <h2>Multiplier</h2>
 *
 * <p>Mirrors {@code MinesTable.calculatePayoutMultiplier}: the inverse of the
 * probability of surviving {@code picks} tiles, less the plugin-wide 1% house
 * edge. The result is a gross return -- the stake is not paid on top.
 *
 * <p>A zero-pick cash-out is the stake back, treated as a cancellation rather
 * than a win, so it costs the house nothing.
 */
public final class MinesLiability {

    /** The plugin-wide edge Mines, Dragon Descent, Coin Flip and RPS all use. */
    static final double RETURN_FACTOR = 0.99;

    private MinesLiability() {
    }

    /**
     * Gross return per unit staked after {@code picks} safe tiles.
     *
     * @param totalTiles tiles on the board
     * @param minesCount how many of them are mines
     * @param picks how many safe tiles have been revealed
     * @return the multiplier, or {@code 1.0} for zero picks -- the stake back
     */
    public static double payoutMultiplier(int totalTiles, int minesCount, int picks) {
        if (picks <= 0) {
            // A zero-pick cash-out returns the untouched stake. Not a win, and
            // not something the dealer can lose money on.
            return 1.0;
        }
        int safeTiles = totalTiles - minesCount;
        if (safeTiles <= 0 || picks > safeTiles) {
            return 0.0;
        }
        double probability = 1.0;
        for (int i = 0; i < picks; i++) {
            probability *= (double) (safeTiles - i) / (totalTiles - i);
        }
        if (probability <= 0.0) {
            return 0.0;
        }
        return RETURN_FACTOR / probability;
    }

    /** The cash-out a player would collect right now, after {@code picks} safe tiles. */
    public static BigDecimal cashOutValue(double wager, int totalTiles, int minesCount, int picks) {
        BigDecimal stake = Money.of(wager);
        if (!Money.isPositive(stake)) {
            return Money.ZERO;
        }
        return Money.multiply(stake, Money.of(payoutMultiplier(totalTiles, minesCount, picks)));
    }

    /**
     * The exposure the board currently carries: the stake posted, and the
     * cash-out the player could take right now.
     */
    public static Exposure currentExposure(double wager, int totalTiles, int minesCount, int picks) {
        return Exposure.of(Money.of(wager), cashOutValue(wager, totalTiles, minesCount, picks));
    }

    /**
     * The exposure that would exist if the next tile turns out to be safe.
     *
     * <p>This is what a dealer must be able to cover <em>before</em> the tile
     * is revealed. It is the whole obligation after the pick, not the
     * increment: the reservation is replaced, not added to.
     */
    public static Exposure exposureAfterNextSafePick(
        double wager, int totalTiles, int minesCount, int picksSoFar) {

        return currentExposure(wager, totalTiles, minesCount, picksSoFar + 1);
    }

    /** The stake returned by a zero-pick cash-out, which is a cancellation. */
    public static Exposure cancellation(double wager) {
        BigDecimal stake = Money.of(wager);
        return Exposure.of(stake, stake);
    }
}
