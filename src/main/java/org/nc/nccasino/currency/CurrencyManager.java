package org.nc.nccasino.currency;

import org.nc.nccasino.Nccasino;

public class CurrencyManager {
	private final Nccasino plugin;
	private final CurrencyProvider standardProvider;
	private final CurrencyProvider vaultProvider;
	private final CurrencyProvider customProvider;

	public CurrencyManager(Nccasino plugin) {
		this.plugin = plugin;
		this.standardProvider = new StandardItemCurrencyProvider(plugin);
		this.vaultProvider = new VaultCurrencyProvider();
		this.customProvider = new CustomChipCurrencyProvider();
	}

	public CurrencyProvider getProvider(String internalName) {
		CurrencyMode mode = DealerCurrencySettings.getMode(plugin, internalName);
		return switch (mode) {
			case VAULT -> vaultProvider;
			case CUSTOM -> customProvider;
			case STANDARD -> standardProvider;
		};
	}
}

