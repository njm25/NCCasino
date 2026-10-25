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

		BigDecimal bal = balanceOf(economy, player, internalName);
		if (bal == null) {
			return 0;
		}
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

		BigDecimal bal = balanceOf(economy, player, internalName);
		if (bal == null || MoneyHelper.floorToLong(bal) < amount) {
			return 0;
		}

		EconomyResponse resp;
		try {
			resp = economy.withdrawPlayer(player, (double) amount);
		} catch (RuntimeException e) {
			warnOnce(internalName, "withdraw-error", "Vault withdraw threw for dealer '" + internalName + "': " + e);
			return 0;
		}
		if (resp != null && resp.transactionSuccess()) {
			return amount;
		}

		warnOnce(internalName, "withdraw", "Vault withdraw failed for dealer '" + internalName + "': " + (resp != null ? resp.errorMessage : "unknown error"));
		return 0;
	}

	@Override
	public boolean deposit(Player player, String internalName, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (player == null) {
			return false;
		}

		return deposit(player, internalName, MoneyHelper.bd((long) amount));
	}

	/**
	 * @return whether the deposit was actually confirmed by Vault -- callers that owe this
	 *         amount unconditionally (payouts/refunds) must treat {@code false} as "not
	 *         delivered" and durably queue the exact amount rather than assuming it landed.
	 */
	public boolean deposit(Player player, String internalName, BigDecimal amount) {
		if (player == null || amount == null) {
			return false;
		}

		BigDecimal safeAmount = MoneyHelper.clampNonNegative(amount);
		if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
			// Nothing was actually owed -- trivially "delivered", same
			// convention PendingPayout itself uses for amount() <= 0.
			return true;
		}

		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null) {
			return false;
		}

		double depositValue = MoneyHelper.toVaultDouble(safeAmount);
		EconomyResponse resp;
		try {
			resp = economy.depositPlayer(player, depositValue);
		} catch (RuntimeException e) {
			// Reported as not delivered, so the caller queues the payout instead of losing it.
			warnOnce(internalName, "deposit-error", "Vault deposit threw for dealer '" + internalName + "': " + e);
			return false;
		}
		if (resp != null && resp.transactionSuccess()) {
			return true;
		}

		warnOnce(internalName, "deposit", "Vault deposit failed for dealer '" + internalName + "': " + (resp != null ? resp.errorMessage : "unknown error"));
		return false;
	}

	/**
	 * Decimal-aware balance check, bypassing the {@link CurrencyProvider}
	 * interface's whole-unit-only {@link #has(Player, String, int)} --
	 * Vault economies support exact fractional balances (e.g. checking a
	 * 12.5 insurance stake off an odd wager), and flooring that down to an
	 * int first would wrongly report insufficient funds for a player who
	 * can actually afford it.
	 */
	public boolean hasAtLeastDecimal(Player player, String internalName, BigDecimal amount) {
		if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null) {
			return false;
		}
		BigDecimal bal = balanceOf(economy, player, internalName);
		return bal != null && bal.compareTo(amount) >= 0;
	}

	/**
	 * Decimal-aware withdrawal, the debit-side counterpart to
	 * {@link #hasAtLeastDecimal}. Returns whether the withdrawal actually
	 * happened.
	 */
	public boolean withdrawDecimal(Player player, String internalName, BigDecimal amount) {
		if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		Economy economy = getEconomyOrWarn(internalName);
		if (economy == null) {
			return false;
		}
		BigDecimal bal = balanceOf(economy, player, internalName);
		if (bal == null || bal.compareTo(amount) < 0) {
			return false;
		}
		EconomyResponse resp;
		try {
			resp = economy.withdrawPlayer(player, amount.doubleValue());
		} catch (RuntimeException e) {
			warnOnce(internalName, "withdraw-error", "Vault withdraw threw for dealer '" + internalName + "': " + e);
			return false;
		}
		if (resp != null && resp.transactionSuccess()) {
			return true;
		}
		warnOnce(internalName, "withdraw", "Vault withdraw failed for dealer '" + internalName + "': " + (resp != null ? resp.errorMessage : "unknown error"));
		return false;
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
			warnOnce(internalName, "no-hook", "Vault mode selected for dealer '" + internalName + "', but VaultHook is not available.");
			return null;
		}

		Economy economy = plugin.getVaultHook().getEconomy();
		if (economy == null) {
			warnOnce(internalName, "no-economy", "Vault mode selected for dealer '" + internalName + "', but no Vault Economy provider is available (install an economy plugin).");
			return null;
		}

		return economy;
	}

	/** The player's balance, or null when the economy throws (treated as no funds). */
	private BigDecimal balanceOf(Economy economy, Player player, String internalName) {
		try {
			return MoneyHelper.clampNonNegative(MoneyHelper.bd(economy.getBalance(player)));
		} catch (RuntimeException e) {
			warnOnce(internalName, "balance-error", "Vault balance check threw for dealer '" + internalName + "': " + e);
			return null;
		}
	}

	/** Logs each kind of problem once per dealer, so a later, different failure is still reported. */
	private void warnOnce(String internalName, String kind, String message) {
		String key = ((internalName == null || internalName.isBlank()) ? "<unknown>" : internalName) + "|" + kind;
		if (warnedDealers.add(key)) {
			plugin.getLogger().warning(message);
		}
	}
}
