package org.nc.nccasino.currency;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface CurrencyProvider {
	CurrencyMode getMode();

	int getBalance(Player player, String internalName);

	boolean has(Player player, String internalName, int amount);

	int withdraw(Player player, String internalName, int amount);

	/**
	 * @return whether the credit was actually confirmed delivered -- callers that owe this
	 *         amount unconditionally (e.g. a payout) must not treat a {@code false} return as
	 *         success and must queue/retry the exact amount instead of silently dropping it.
	 */
	boolean deposit(Player player, String internalName, int amount);

	boolean isCurrencyItem(ItemStack stack, String internalName);

	ItemStack createCurrencyStack(String internalName, int amount);
}

