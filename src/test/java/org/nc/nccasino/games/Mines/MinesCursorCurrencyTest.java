package org.nc.nccasino.games.Mines;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.StandardItemCurrencyProvider;
import org.nc.nccasino.currency.VaultCurrencyProvider;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stack held on the cursor over Place Bet becomes the wager only if it is the
 * dealer's currency item. A Vault dealer has none: its {@code currency.material}
 * is only the chip icon, so a stack of that material used to be recorded as a
 * Vault bet and refunded or paid in Vault money.
 */
class MinesCursorCurrencyTest {

    private static ItemStack stack(Material material) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        return stack;
    }

    private static Nccasino pluginWithCurrency(Material material) {
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MinesCursorCurrencyTest"));
        when(plugin.getCurrency("mines")).thenReturn(material);
        return plugin;
    }

    @Test
    void aVaultDealerNeverTakesItsIconMaterialFromTheCursor() {
        Nccasino plugin = pluginWithCurrency(Material.EMERALD);
        CurrencyProvider vault = new VaultCurrencyProvider(plugin);

        assertFalse(MinesTable.isCursorCurrency(vault, Material.EMERALD, stack(Material.EMERALD), "mines"));
    }

    @Test
    void anItemDealerTakesOnlyItsOwnCurrencyFromTheCursor() {
        Nccasino plugin = pluginWithCurrency(Material.EMERALD);
        CurrencyProvider items = new StandardItemCurrencyProvider(plugin);

        assertTrue(MinesTable.isCursorCurrency(items, Material.EMERALD, stack(Material.EMERALD), "mines"));
        assertFalse(MinesTable.isCursorCurrency(items, Material.EMERALD, stack(Material.DIAMOND), "mines"));
    }

    @Test
    void anEmptyCursorIsNeverAWager() {
        assertFalse(MinesTable.isCursorCurrency(null, Material.EMERALD, null, "mines"));
        assertFalse(MinesTable.isCursorCurrency(null, Material.EMERALD, stack(Material.AIR), "mines"));
    }
}
