package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * Worst-case exposure for the games that raise the stakes one decision at a
 * time: Dragon Descent floors, and Coin Flip / Rock Paper Scissors chains.
 *
 * <h2>One shared calculator, because the shape is genuinely the same</h2>
 *
 * <p>All three compound a pot by a fixed, house-edge-adjusted multiplier each
 * time the player chooses to continue. Dragon Descent derives its multiplier
 * from the floor geometry; the chain games use a configured one. What matters
 * to the budget is identical in every case: <em>before</em> the next random
 * result is chosen, can the dealer cover the pot that a win would create?
 *
 * <p>Keeping this in one place is deliberate. Three near-identical copies of a
 * compounding calculation is exactly where a rounding rule or an edge factor
 * drifts apart between games and nobody notices until a dealer is short.
 *
 * <h2>Denial happens before the outcome exists</h2>
 *
 * <p>Every method here answers a question about a decision the player has not
 * yet committed to. A refusal therefore costs nothing: the player keeps the
 * pot they have already won, which the dealer is already covering, and no
 * coin, throw or floor has been decided.
 */
public final class ProgressiveLiability {

    /** The plugin-wide 1% edge shared by Mines, Dragon Descent, Coin Flip and RPS. */
    public static final double RETURN_FACTOR = 0.99;

    private ProgressiveLiability() {
    }

    /**
     * Dragon Descent's gross multiplier after {@code floorsCleared} floors.
     *
     * <p>Mirrors {@code DragonClient.calculatePayoutMultiplier}: the inverse of
     * the survival probability, less the house edge. Zero floors returns the
     * stake untouched.
     */
    public static double dragonMultiplier(int safeSpots, int columns, int floorsCleared) {
        if (floorsCleared <= 0) {
            return 1.0;
        }
        if (safeSpots <= 0 || columns <= 0) {
            return 0.0;
        }
        double probability = 1.0;
        for (int i = 0; i < floorsCleared; i++) {
            probability *= (double) safeSpots / columns;
        }
        if (probability <= 0.0) {
            return 0.0;
        }
        return RETURN_FACTOR / probability;
    }

    /**
     * The exposure Dragon Descent would carry if the next floor is cleared.
     *
     * <p>Checked before the floor's safe spots are chosen, so a dealer that
     * cannot cover the higher pot stops the descent rather than voiding a
     * win the player has already made.
     */
    public static Exposure dragonExposureAfterNextFloor(
        double wager, int safeSpots, int columns, int floorsCleared) {

        BigDecimal stake = Money.of(wager);
        return Exposure.of(stake,
            Money.multiply(stake, Money.of(dragonMultiplier(safeSpots, columns, floorsCleared + 1))));
    }

    /** Dragon Descent's exposure as it stands, before any further decision. */
    public static Exposure dragonCurrentExposure(
        double wager, int safeSpots, int columns, int floorsCleared) {

        BigDecimal stake = Money.of(wager);
        return Exposure.of(stake,
            Money.multiply(stake, Money.of(dragonMultiplier(safeSpots, columns, floorsCleared))));
    }

    /**
     * The exposure a Coin Flip or RPS chain would carry after one more winning
     * round.
     *
     * @param currentPot what the player would collect by cashing out now --
     *     already the dealer's obligation, and already reserved
     * @param originalStake what the player actually posted. The chain's stake
     *     never grows: only the dealer's side compounds, which is precisely
     *     why the exposure has to be rechecked every round.
     * @param chainMultiplier the configured compounding factor for one round
     */
    public static Exposure chainExposureAfterNextRound(
        double originalStake, long currentPot, double chainMultiplier) {

        return Exposure.of(
            Money.of(originalStake),
            Money.of(compound(currentPot, chainMultiplier)));
    }

    /** A chain's exposure as it stands: the pot the player could take right now. */
    public static Exposure chainCurrentExposure(double originalStake, long currentPot) {
        return Exposure.of(Money.of(originalStake), Money.of(Math.max(0L, currentPot)));
    }

    /**
     * Compounds a pot one round, without the precision clamp the game layer
     * applies.
     *
     * <p>Deliberately unclamped: the numeric ceiling is a <em>separate</em>
     * denial reason from the dealer being short, and the design requires the
     * two to stay distinct in player messaging. Clamping here would hide a
     * pot that has outrun the currency system behind a funding message, and a
     * player would be told the wrong thing about why they cannot continue.
     */
    public static long compound(long currentPot, double multiplier) {
        if (currentPot <= 0 || multiplier <= 0.0 || !Double.isFinite(multiplier)) {
            return 0L;
        }
        double compounded = Math.round((double) currentPot * multiplier);
        if (compounded >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) compounded;
    }
}
