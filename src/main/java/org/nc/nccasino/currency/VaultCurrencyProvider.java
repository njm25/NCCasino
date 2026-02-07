package org.nc.nccasino.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vault-backed currency provider. Currency is not represented by items.
 */
public class VaultCurrencyProvider implements CurrencyProvider {
	private final Nccasino plugin;
	private final Set<String> warnedDealers = ConcurrentHashMap.newKeySet();

	public VaultCurrencyProvider(Nccasino plugin) {
		this.plugin = plugin;
	}

	@Override
	public CurrencyMode getMode() {
		return CurrencyMode.VAULT;
	}

	@Override
	public int getBalance(Player player, String internalName) {
		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null || player == null) {
			return 0;
		}

		BigDecimal bal = MoneyHelper.clampNonNegative(MoneyHelper.bd(economy.getBalance(player)));
		long floored = MoneyHelper.floorToLong(bal);
		if (floored <= 0) {
			return 0;
		}

		if (floored > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}

		return (int) floored;
	}

	@Override
	public boolean has(Player player, String internalName, int amount) {
		if (amount <= 0) {
			return true;
		}
		return getBalance(player, internalName) >= amount;
	}

	@Override
	public int withdraw(Player player, String internalName, int amount) {
		if (player == null || amount <= 0) {
			return 0;
		}

		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null) {
			return 0;
		}

		BigDecimal bal = MoneyHelper.clampNonNegative(MoneyHelper.bd(economy.getBalance(player)));
		if (MoneyHelper.floorToLong(bal) < amount) {
			return 0;
		}

		EconomyResponse resp = economy.withdrawPlayer(player, (double) amount);
		if (resp != null && resp.transactionSuccess()) {
			return amount;
		}

		warnOnce(internalName, "Vault withdraw failed for dealer '" + internalName + "': " + (resp != null ? resp.errorMessage : "unknown error"));
		return 0;
	}

	@Override
	public void deposit(Player player, String internalName, int amount) {
		if (player == null || amount <= 0) {
			return;
		}

		deposit(player, internalName, MoneyHelper.bd((long) amount));
	}

	public void deposit(Player player, String internalName, BigDecimal amount) {
		if (player == null || amount == null) {
			return;
		}

		BigDecimal safeAmount = MoneyHelper.clampNonNegative(amount);
		if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}

		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null) {
			return;
		}

		double depositValue = MoneyHelper.toVaultDouble(safeAmount);
		EconomyResponse resp = economy.depositPlayer(player, depositValue);
		if (resp != null && resp.transactionSuccess()) {
			return;
		}

		warnOnce(internalName, "Vault deposit failed for dealer '" + internalName + "': " + (resp != null ? resp.errorMessage : "unknown error"));
	}

	@Override
	public boolean isCurrencyItem(ItemStack stack, String internalName) {
		return false;
	}

	@Override
	public ItemStack createCurrencyStack(String internalName, int amount) {
		return new ItemStack(Material.AIR);
	}

	private Economy getEconomyOrWarn(String internalName) {
		if (plugin == null || plugin.getVaultHook() == null) {
			warnOnce(internalName, "Vault mode selected for dealer '" + internalName + "', but VaultHook is not available.");
			return null;
		}

		Economy economy = plugin.getVaultHook().getEconomy();
		if (economy == null) {
			warnOnce(internalName, "Vault mode selected for dealer '" + internalName + "', but no Vault Economy provider is available (install an economy plugin).");
			return null;
		}

		return economy;
	}

	private void warnOnce(String internalName, String message) {
		String key = (internalName == null || internalName.isBlank()) ? "<unknown>" : internalName;
		if (warnedDealers.add(key)) {
			plugin.getLogger().warning(message);
		}
	}
}

