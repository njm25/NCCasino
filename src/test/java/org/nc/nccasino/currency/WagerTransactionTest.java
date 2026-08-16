package org.nc.nccasino.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Game-neutral twin of {@code BlackjackWagerTransactionTest} -- exercises
 * the extracted {@link WagerTransaction#tryWithdraw} used by Slots (and any
 * future game) without depending on Blackjack at all.
 */
class WagerTransactionTest {

    private static final class FakeCurrencyProvider implements CurrencyProvider {
        private final CurrencyMode mode;
        private int balance;
        private Integer forcedWithdrawResult;
        int depositedTotal = 0;
        int withdrawCallCount = 0;

        FakeCurrencyProvider(CurrencyMode mode, int balance) {
            this.mode = mode;
            this.balance = balance;
        }

        void forceNextWithdrawResult(int amount) {
            this.forcedWithdrawResult = amount;
        }

        @Override
        public CurrencyMode getMode() {
            return mode;
        }

        @Override
        public int getBalance(Player player, String internalName) {
            return balance;
        }

        @Override
        public boolean has(Player player, String internalName, int amount) {
            return balance >= amount;
        }

        @Override
        public int withdraw(Player player, String internalName, int amount) {
            withdrawCallCount++;
            if (forcedWithdrawResult != null) {
                int result = forcedWithdrawResult;
                balance -= result;
                return result;
            }
            int actual = Math.min(amount, balance);
            balance -= actual;
            return actual;
        }

        @Override
        public boolean deposit(Player player, String internalName, int amount) {
            balance += amount;
            depositedTotal += amount;
            return true;
        }

        @Override
        public boolean isCurrencyItem(ItemStack stack, String internalName) {
            return false;
        }

        @Override
        public ItemStack createCurrencyStack(String internalName, int amount) {
            return null;
        }
    }

    private static final class FakeVaultCurrencyProvider extends VaultCurrencyProvider {
        private BigDecimal balance;
        private boolean nextWithdrawSucceeds;
        int withdrawCallCount = 0;

        FakeVaultCurrencyProvider(double balance) {
            super(null);
            this.balance = BigDecimal.valueOf(balance);
        }

        @Override
        public boolean hasAtLeastDecimal(Player player, String internalName, BigDecimal amount) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdrawDecimal(Player player, String internalName, BigDecimal amount) {
            withdrawCallCount++;
            if (!nextWithdrawSucceeds) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }

        void setNextWithdrawSucceeds(boolean succeeds) {
            this.nextWithdrawSucceeds = succeeds;
        }
    }

    @Test
    void successfulVaultDecimalWithdrawal() {
        FakeVaultCurrencyProvider provider = new FakeVaultCurrencyProvider(25.0);
        provider.setNextWithdrawSucceeds(true);
        assertTrue(WagerTransaction.tryWithdraw(provider, null, "table", 12.5));
        assertEquals(1, provider.withdrawCallCount);
    }

    @Test
    void balanceCheckSucceedsButVaultWithdrawalFails() {
        FakeVaultCurrencyProvider provider = new FakeVaultCurrencyProvider(25.0);
        assertTrue(provider.hasAtLeastDecimal(null, "table", BigDecimal.valueOf(12.5)));
        provider.setNextWithdrawSucceeds(false);
        assertFalse(WagerTransaction.tryWithdraw(provider, null, "table", 12.5));
    }

    @Test
    void successfulStandardWithdrawalDebitsExactlyOnce() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        assertTrue(WagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(80, provider.getBalance(null, "table"));
        assertEquals(1, provider.withdrawCallCount);
        assertEquals(0, provider.depositedTotal);
    }

    @Test
    void partialStandardWithdrawalIsRolledBackAndReportedAsFailure() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        provider.forceNextWithdrawResult(12);
        assertFalse(WagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(12, provider.depositedTotal);
        assertEquals(100, provider.getBalance(null, "table"));
    }

    @Test
    void zeroWithdrawalIsAFailureWithNoRollbackNeeded() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 5);
        provider.forceNextWithdrawResult(0);
        assertFalse(WagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(0, provider.depositedTotal);
    }

    @Test
    void nonPositiveAmountNeverAttemptsAWithdrawal() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        assertFalse(WagerTransaction.tryWithdraw(provider, null, "table", 0.0));
        assertFalse(WagerTransaction.tryWithdraw(provider, null, "table", -5.0));
        assertEquals(0, provider.withdrawCallCount);
    }

    @Test
    void nullProviderNeverThrows() {
        assertFalse(WagerTransaction.tryWithdraw(null, null, "table", 20.0));
    }
}
