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
}
