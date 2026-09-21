package org.nc.nccasino.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exact-decimal arithmetic and a stable on-disk representation for dealer
 * economic state.
 *
 * <p>Everything the budget system stores or compares goes through here. The
 * rule is narrow and worth stating plainly: <strong>no economic quantity is
 * ever held or compared as a {@code double}</strong>. A Vault currency can be
 * fractional, and repeated credit/debit of {@code double} balances drifts --
 * a dealer would eventually hold {@code 999.9999999999999} and refuse a
 * payout it can obviously afford, or accept one it cannot.
 *
 * <p>Values are persisted with {@link BigDecimal#toPlainString()} so a
 * reloaded balance is bit-identical to the one written, with no exponent
 * notation for a YAML parser to mangle and no locale-dependent formatting.
 */
public final class Money {

    /**
     * Scale kept for stored balances. Two places covers real Vault currencies;
     * the extra places absorb intermediate arithmetic (a house edge, a 3:2
     * blackjack payout) without a rounding step per operation.
     */
    public static final int SCALE = 6;

    /**
     * Refuses absurd configured values before they reach the wager path. Well
     * above any real casino balance and far below the point where BigDecimal
     * arithmetic becomes slow enough to matter on the main thread.
     */
    public static final BigDecimal MAX = new BigDecimal("1000000000000000");

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);

    private Money() {
    }

    /** Normalizes to the stored scale, rounding down so nothing is invented. */
    public static BigDecimal of(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, RoundingMode.FLOOR);
    }

    public static BigDecimal of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    /**
     * @param value a {@code double} arriving from an older API surface. Uses
     *     {@link BigDecimal#valueOf(double)} so {@code 0.1} becomes exactly
     *     {@code 0.1} rather than its binary expansion.
     */
    public static BigDecimal of(double value) {
        if (!Double.isFinite(value)) {
            return ZERO;
        }
        return of(BigDecimal.valueOf(value));
    }

    /**
     * Parses a stored value. Returns {@code null} for anything unparseable so
     * the caller can decide -- a malformed balance is a reason to refuse to
     * operate on that dealer, never a reason to silently substitute zero and
     * quietly destroy its funds.
     */
    public static BigDecimal parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return of(new BigDecimal(stored.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The stable on-disk form. */
    public static String store(BigDecimal value) {
        return of(value).toPlainString();
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(of(a).add(of(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(of(a).subtract(of(b)));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return of(of(a).multiply(of(b)));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return of(a).compareTo(of(b)) >= 0 ? of(a) : of(b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return of(a).compareTo(of(b)) <= 0 ? of(a) : of(b);
    }

    /** Clamps at zero. A dealer balance is never negative, by construction. */
    public static BigDecimal clampNonNegative(BigDecimal value) {
        return max(value, ZERO);
    }

    public static boolean isPositive(BigDecimal value) {
        return of(value).compareTo(ZERO) > 0;
    }

    public static boolean isZero(BigDecimal value) {
        return of(value).compareTo(ZERO) == 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return of(value).compareTo(ZERO) < 0;
    }

    /** {@code a >= b}, at stored scale. */
    public static boolean atLeast(BigDecimal a, BigDecimal b) {
        return of(a).compareTo(of(b)) >= 0;
    }

    /**
     * Whether a value is safe to use as economic state at all: present,
     * non-negative, and within {@link #MAX}. Guards the configured baseline
     * and every incoming stake/payout, so a nonsense number is refused with a
     * distinct reason instead of quietly poisoning a stored balance.
     */
    public static boolean isSafe(BigDecimal value) {
        return value != null
            && value.compareTo(BigDecimal.ZERO) >= 0
            && value.compareTo(MAX) <= 0;
    }

    /**
     * Divides for the guaranteed-worst-case-rounds calculation. Rounds down,
     * so a risk tier is never overstated by a rounding step.
     */
    public static BigDecimal divideFloor(BigDecimal value, int divisor) {
        if (divisor <= 0) {
            return ZERO;
        }
        return of(value).divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.FLOOR);
    }
}
