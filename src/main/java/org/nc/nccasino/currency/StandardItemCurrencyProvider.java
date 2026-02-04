package org.nc.nccasino.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.nc.nccasino.Nccasino;

public class StandardItemCurrencyProvider implements CurrencyProvider {
	private final Nccasino plugin;

	public StandardItemCurrencyProvider(Nccasino plugin) {
		this.plugin = plugin;
	}

	@Override
	public CurrencyMode getMode() {
		return CurrencyMode.STANDARD;
	}

	@Override
	public int getBalance(Player player, String internalName) {
		if (player == null || internalName == null) {
			return 0;
		}

		Material mat = plugin.getCurrency(internalName);
		if (mat == null) {
			return 0;
		}

		int total = 0;
		for (ItemStack stack : player.getInventory().getContents()) {
			if (stack != null && stack.getType() == mat) {
				total += stack.getAmount();
			}
		}
		return total;
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
		if (player == null || internalName == null || amount <= 0) {
			return 0;
		}

		Material mat = plugin.getCurrency(internalName);
		if (mat == null) {
			return 0;
		}

		PlayerInventory inv = player.getInventory();
		int remaining = amount;

		for (int slot = 0; slot < inv.getSize() && remaining > 0; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack == null || stack.getType() != mat) {
				continue;
			}

			int stackAmount = stack.getAmount();
			int take = Math.min(stackAmount, remaining);
			int newAmount = stackAmount - take;

			if (newAmount <= 0) {
				inv.setItem(slot, null);
			} else {
				stack.setAmount(newAmount);
				inv.setItem(slot, stack);
			}

			remaining -= take;
		}

		return amount - remaining;
	}

	@Override
	public void deposit(Player player, String internalName, int amount) {
		if (player == null || internalName == null || amount <= 0) {
			return;
		}

		Material mat = plugin.getCurrency(internalName);
		if (mat == null) {
			return;
		}

		player.getInventory().addItem(new ItemStack(mat, amount));
	}

	@Override
	public boolean isCurrencyItem(ItemStack stack, String internalName) {
		if (stack == null || internalName == null) {
			return false;
		}
		Material mat = plugin.getCurrency(internalName);
		return mat != null && stack.getType() == mat;
	}

	@Override
	public ItemStack createCurrencyStack(String internalName, int amount) {
		if (internalName == null || amount <= 0) {
			return new ItemStack(Material.AIR);
		}
		Material mat = plugin.getCurrency(internalName);
		if (mat == null) {
			return new ItemStack(Material.AIR);
		}
		return new ItemStack(mat, amount);
	}
}

