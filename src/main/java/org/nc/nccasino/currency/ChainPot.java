package org.nc.nccasino.currency;

import java.math.BigDecimal;

/**
 * The pot of a player-vs-dealer chain (Coin Flip, Rock Paper Scissors) that a
 * win multiplies by 1.98, kept in the currency's smallest unit: one item for
 * an item currency, one cent for Vault.
 *
 * <p>1.98 times a pot is rarely a whole unit. Rounding it to the nearest one
 * paid small item stakes exactly double (25 x 1.98 = 49.5 became 50), so the
 * house edge was gone there. Instead the whole part is paid and the fraction
 * becomes one more unit with that probability: 51.48 pays 52 with
 * probability 0.48 and 51 otherwise. The expected pot after every win is then
 * exactly 1.98 times the pot before it, at any stake and over any chain.
 * Vault counts in cents, so a $10 win pays exactly $19.80.
 */
public final class ChainPot {

    /** The win multiplier, 1.98, in hundredths so the arithmetic stays exact. */
    public static final long MULTIPLIER_HUNDREDTHS = 198;

    /**
     * 2^53 -- the largest integral value a double can represent exactly.
     * Every payout crosses a double-typed boundary (creditPlayer,
     * PendingPayout's amount, Vault's Economy API), so a chain is not offered
     * a round that could take its pot past this.
     */
    public static final long MAX_SAFE_POT = 1L << 53;

    private ChainPot() {
    }

    /** Smallest units per whole unit of currency: 100 cents for Vault, 1 for items. */
    public static long unitsPerWhole(CurrencyMode mode) {
        return mode == CurrencyMode.VAULT ? 100L : 1L;
    }

    /** A whole-unit stake (chips are whole) as a pot. */
    public static long fromStake(long stake, CurrencyMode mode) {
        return stake * unitsPerWhole(mode);
    }

    /** The pot as an amount of currency: 1980 cents is 19.80. */
    public static BigDecimal toMoney(long pot, CurrencyMode mode) {
        return BigDecimal.valueOf(pot, mode == CurrencyMode.VAULT ? 2 : 0);
    }

    /** {@link #toMoney} as a double, for the APIs that take one (exact to the cent). */
    public static double toAmount(long pot, CurrencyMode mode) {
        return toMoney(pot, mode).doubleValue();
    }

    /**
     * The pot after a win: 1.98 times {@code pot}, its fraction paid as one
     * more unit when {@code roll} (uniform in [0, 1)) falls below it.
     */
    public static long compound(long pot, double roll) {
        if (pot <= 0) {
            return 0L;
        }
        if (!(roll >= 0.0 && roll < 1.0)) {
            throw new IllegalArgumentException("roll must be in [0, 1)");
        }
        if (pot > MAX_SAFE_POT) {
            return MAX_SAFE_POT;
        }
        long product = pot * MULTIPLIER_HUNDREDTHS; // at most 2^53 * 198, well inside a long
        long whole = product / 100;
        long hundredths = product % 100;
        long compounded = roll * 100 < hundredths ? whole + 1 : whole;
        return Math.min(compounded, MAX_SAFE_POT);
    }

    /** The most a win from {@code pot} can pay: the fraction rounded up. What a dealer's budget must cover. */
    public static long maxCompound(long pot) {
        if (pot <= 0) {
            return 0L;
        }
        if (pot > MAX_SAFE_POT) {
            return MAX_SAFE_POT;
        }
        long product = pot * MULTIPLIER_HUNDREDTHS;
        return product / 100 + (product % 100 == 0 ? 0 : 1);
    }

    /**
     * Whether a win from {@code pot} could take it past {@link #MAX_SAFE_POT}.
     * Checked before another round is offered, so a win is never cut short.
     */
    public static boolean wouldExceedSafeMax(long pot) {
        return pot > 0 && maxCompound(pot) > MAX_SAFE_POT;
    }
}
