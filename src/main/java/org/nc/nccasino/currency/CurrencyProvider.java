package org.nc.nccasino.currency;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface CurrencyProvider {
	CurrencyMode getMode();

	int getBalance(Player player, String internalName);

	boolean has(Player player, String internalName, int amount);

	int withdraw(Player player, String internalName, int amount);

	void deposit(Player player, String internalName, int amount);

	boolean isCurrencyItem(ItemStack stack, String internalName);

	ItemStack createCurrencyStack(String internalName, int amount);
}

