package org.nc.nccasino.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StandardItemCurrencyProviderTest {

    private Nccasino plugin;
    private Player player;
    private PlayerInventory inventory;
    private StandardItemCurrencyProvider provider;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(plugin.getCurrency("table")).thenReturn(Material.EMERALD);
        provider = new StandardItemCurrencyProvider(plugin);
    }

    @Test
    void completeDepositReturnsTrue() {
        ItemStack[] storage = new ItemStack[36];
        ItemStack partialStack = stack(Material.EMERALD, 60, 64);
        storage[0] = partialStack;
        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

        assertTrue(provider.deposit(player, "table", 4));
        verify(inventory).addItem(any(ItemStack.class));
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
    }

    @Test
    void insufficientCapacityReturnsFalseWithoutPartiallyAddingAnything() {
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < storage.length; i++) {
            storage[i] = new ItemStack(Material.DIRT, 64);
        }
        when(inventory.getStorageContents()).thenReturn(storage);

        assertFalse(provider.deposit(player, "table", 1));
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void unexpectedBukkitLeftoversRestoreTheSnapshotAndReportFailure() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = stack(Material.EMERALD, 50, 64);
        when(inventory.getStorageContents()).thenReturn(storage);
        Map<Integer, ItemStack> leftovers = Map.of(0, mock(ItemStack.class));
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>(leftovers));

        assertFalse(provider.deposit(player, "table", 1));
        verify(inventory).setStorageContents(any(ItemStack[].class));
    }

    @Test
    void invalidRecipientOrCurrencyReportsFailure() {
        assertFalse(provider.deposit(null, "table", 1));
        assertFalse(provider.deposit(player, null, 1));
        when(plugin.getCurrency("missing")).thenReturn(null);
        assertFalse(provider.deposit(player, "missing", 1));
    }

    @Test
    void nonPositiveAmountIsTriviallySuccessful() {
        assertTrue(provider.deposit(null, null, 0));
        assertTrue(provider.deposit(null, null, -1));
    }

    private static ItemStack stack(Material type, int amount, int maxStackSize) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.getMaxStackSize()).thenReturn(maxStackSize);
        when(stack.clone()).thenReturn(mock(ItemStack.class));
        return stack;
    }
}
