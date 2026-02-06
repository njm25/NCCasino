package org.nc.nccasino.currency;

import org.nc.nccasino.Nccasino;

public class CurrencyManager {
	private final Nccasino plugin;
	private final CurrencyProvider standardProvider;
	/** Only created when Vault + economy are available; otherwise VAULT mode falls back to standardProvider. */
	private final CurrencyProvider vaultProvider;
	// CUSTOM not used this release: no customProvider instance; CUSTOM config falls back to standardProvider.

	public CurrencyManager(Nccasino plugin) {
		this.plugin = plugin;
		this.standardProvider = new StandardItemCurrencyProvider(plugin);
		boolean vaultAvailable = plugin.getVaultHook() != null && plugin.getVaultHook().isEconomyAvailable();
		this.vaultProvider = vaultAvailable ? new VaultCurrencyProvider(plugin) : null;
	}

	public CurrencyProvider getProvider(String internalName) {
		CurrencyMode mode = DealerCurrencySettings.getMode(plugin, internalName);
		return switch (mode) {
			case VAULT -> vaultProvider != null ? vaultProvider : standardProvider;
			case CUSTOM -> standardProvider; // CUSTOM not in this release; use standard item currency
			case STANDARD -> standardProvider;
		};
	}
}

