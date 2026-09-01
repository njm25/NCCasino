package org.nc.nccasino.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyHelper {
	private MoneyHelper() {
	}

	public static BigDecimal bd(double v) {
		return BigDecimal.valueOf(v);
	}

	public static BigDecimal bd(long v) {
		return BigDecimal.valueOf(v);
	}

	public static BigDecimal roundDisplay(BigDecimal v) {
		if (v == null) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return v.setScale(2, RoundingMode.HALF_UP);
	}

	public static long floorToLong(BigDecimal v) {
		if (v == null) {
			return 0L;
		}
		BigDecimal floored = v.setScale(0, RoundingMode.FLOOR);
		if (floored.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
			return Long.MAX_VALUE;
		}
		if (floored.compareTo(BigDecimal.ZERO) < 0) {
			return 0L;
		}
		return floored.longValue();
	}

	public static BigDecimal clampNonNegative(BigDecimal v) {
		if (v == null) {
			return BigDecimal.ZERO;
		}
		return v.max(BigDecimal.ZERO);
	}

	public static double toVaultDouble(BigDecimal v) {
		return clampNonNegative(v).doubleValue();
	}

	public static int toWagerUnits(double amount) {
		// Chip wagers are integer-only. Use truncation plus a small epsilon check
		// to tolerate floating-point drift without ever rounding up to the next unit.
		if (amount <= 0.0D) {
			return 0;
		}
		int units = (int) amount; // truncate toward zero
		if (units <= 0) {
			return 0;
		}
		if (Math.abs(amount - units) > 1e-6D) {
			return 0;
		}
		return units;
	}

	public static int probabilisticItemAmount(double amount, double roll) {
		if (!Double.isFinite(amount) || amount <= 0.0D) {
			return 0;
		}
		if (!Double.isFinite(roll) || roll < 0.0D || roll >= 1.0D) {
			throw new IllegalArgumentException("roll must be in [0, 1)");
		}
		if (amount >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}

		int whole = (int) Math.floor(amount);
		double fraction = amount - whole;
		return roll < fraction ? whole + 1 : whole;
	}

	/**
	 * The reservation ceiling that must actually be promised for a fractional
	 * item-currency worst-case payout, given that probabilistic rounding (see
	 * {@link #probabilisticItemAmount}) can round the eventually realized
	 * payout <em>up</em> to the next whole item.
	 *
	 * <p>A reservation built from the raw fractional worst case (e.g. 300.5)
	 * does not cover the whole-item amount rounding can actually deliver for
	 * that same outcome (301) -- settling for 301 against a 300.5 reservation
	 * is an avoidable {@code exposureViolation}, not a genuine bug in the
	 * exposure math, purely because the reservation never accounted for the
	 * ceiling rounding can reach. Discrete currency modes (STANDARD, CUSTOM)
	 * must reserve {@code ceil(rawMaxPayout)}; VAULT and any other continuous
	 * mode must keep the exact fractional value, since Vault accounting stays
	 * exact and is never probabilistically rounded.
	 *
	 * @param rawMaxPayout the worst-case payout as computed by liability math,
	 *     before any rounding
	 * @param mode the currency mode the wager/payout is denominated in
	 * @return the amount that must actually be reserved
	 */
	public static BigDecimal reservationCeilingForMode(BigDecimal rawMaxPayout, CurrencyMode mode) {
		if (rawMaxPayout == null) {
			return BigDecimal.ZERO;
		}
		if (mode != CurrencyMode.STANDARD && mode != CurrencyMode.CUSTOM) {
			return rawMaxPayout;
		}
		return rawMaxPayout.setScale(0, RoundingMode.CEILING);
	}
}
