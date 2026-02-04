package org.nc.nccasino.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Phase 0 stub only. Vault integration is intentionally not implemented yet.
 */
public class VaultCurrencyProvider implements CurrencyProvider {
	@Override
	public CurrencyMode getMode() {
		return CurrencyMode.VAULT;
	}

	@Override
	public int getBalance(Player player, String internalName) {
		return 0;
	}

	@Override
	public boolean has(Player player, String internalName, int amount) {
		return false;
	}

	@Override
	public int withdraw(Player player, String internalName, int amount) {
		return 0;
	}

	@Override
	public void deposit(Player player, String internalName, int amount) {
	}

	@Override
	public boolean isCurrencyItem(ItemStack stack, String internalName) {
		return false;
	}

	@Override
	public ItemStack createCurrencyStack(String internalName, int amount) {
		return new ItemStack(Material.AIR);
	}
}

