package org.nc.nccasino.currency;

import org.nc.nccasino.Nccasino;

public final class DealerCurrencySettings {
	private DealerCurrencySettings() {
	}

	public static CurrencyMode getMode(Nccasino plugin, String internalName) {
		if (plugin == null || internalName == null || internalName.isBlank()) {
			return CurrencyMode.STANDARD;
		}

		String path = "dealers." + internalName + ".currency.mode";
		String raw = plugin.getConfig().getString(path, "STANDARD");
		if (raw == null) {
			return CurrencyMode.STANDARD;
		}

		String normalized = raw.trim().toUpperCase();

		// Legacy alias support: Admin UI previously wrote "VANILLA" for item currency.
		if (normalized.equals("VANILLA")) {
			return CurrencyMode.STANDARD;
		}

		try {
			return CurrencyMode.valueOf(normalized);
		} catch (IllegalArgumentException ignored) {
			return CurrencyMode.STANDARD;
		}
	}
}

