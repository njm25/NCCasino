package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.VaultCurrencyProvider;

/**
 * BlackjackInventory#tryRemoveWager genuinely delegates to
 * {@link BlackjackWagerTransaction#tryWithdraw} for every provider-backed
 * debit path (Vault and generic {@link CurrencyProvider} modes) -- this is
 * the regression coverage for the audited critical defect: a Vault (or any
 * other provider) transaction can report a sufficient balance and then
 * still fail the actual withdrawal, and the old code discarded that
 * result, letting the controller record/enlarge a wager that was never
 * actually debited.
 */
class BlackjackWagerTransactionTest {

    /** Configurable generic provider double -- simulates STANDARD/CUSTOM-style whole-unit withdrawal, including a provider that partially withdraws before reporting failure (StandardItemCurrencyProvider's own real behavior). */
    private static final class FakeCurrencyProvider implements CurrencyProvider {
        private final CurrencyMode mode;
        private int balance;
        private Integer forcedWithdrawResult; // null = normal (withdraw min(amount, balance)); non-null = exactly this, ignoring balance
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
        public void deposit(Player player, String internalName, int amount) {
            balance += amount;
            depositedTotal += amount;
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

    /** Overrides only the decimal-aware Vault methods so the real class's provider-dispatch (instanceof VaultCurrencyProvider) is exercised without needing a real Nccasino plugin/Vault Economy. */
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
                return false; // simulates a failed EconomyResponse despite a sufficient balance
            }
            balance = balance.subtract(amount);
            return true;
        }

        void setNextWithdrawSucceeds(boolean succeeds) {
            this.nextWithdrawSucceeds = succeeds;
        }
    }

    // --- Vault: successful and failed decimal withdrawal ---

    @Test
    void successfulVaultDecimalWithdrawal() {
        FakeVaultCurrencyProvider provider = new FakeVaultCurrencyProvider(25.0);
        provider.setNextWithdrawSucceeds(true);
        assertTrue(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 12.5));
        assertEquals(1, provider.withdrawCallCount);
    }

    @Test
    void balanceCheckSucceedsButVaultWithdrawalFails() {
        // Exactly the audited reproduction: a sufficient balance does not
        // guarantee the transaction itself succeeds.
        FakeVaultCurrencyProvider provider = new FakeVaultCurrencyProvider(25.0);
        assertTrue(provider.hasAtLeastDecimal(null, "table", BigDecimal.valueOf(12.5)), "balance check reports sufficient funds");
        provider.setNextWithdrawSucceeds(false); // but the transaction itself fails
        assertFalse(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 12.5));
    }

    @Test
    void vaultDecimalInsuranceStakeWithdrawsExactHalfWager() {
        FakeVaultCurrencyProvider provider = new FakeVaultCurrencyProvider(50.0);
        provider.setNextWithdrawSucceeds(true);
        double insuranceCost = BlackjackInsuranceRules.cost(25.0); // 12.5
        assertTrue(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", insuranceCost));
    }

    // --- Generic CurrencyProvider (STANDARD/CUSTOM): whole-unit, with rollback on partial withdrawal ---

    @Test
    void successfulStandardWithdrawalDebitsExactlyOnce() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        assertTrue(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(80, provider.getBalance(null, "table"));
        assertEquals(1, provider.withdrawCallCount);
        assertEquals(0, provider.depositedTotal, "a successful withdrawal must never trigger a rollback deposit");
    }

    @Test
    void partialStandardWithdrawalIsRolledBackAndReportedAsFailure() {
        // Mirrors StandardItemCurrencyProvider's real behavior: it takes
        // whatever it can find and only reports the shortfall.
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        provider.forceNextWithdrawResult(12); // asked for 20, provider only actually took 12
        assertFalse(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(12, provider.depositedTotal, "the partially-withdrawn amount must be refunded");
        assertEquals(100, provider.getBalance(null, "table"), "net balance must be unchanged after the rollback");
    }

    @Test
    void zeroWithdrawalIsAFailureWithNoRollbackNeeded() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 5);
        provider.forceNextWithdrawResult(0); // insufficient funds inside the provider
        assertFalse(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 20.0));
        assertEquals(0, provider.depositedTotal);
    }

    @Test
    void nonPositiveAmountNeverAttemptsAWithdrawal() {
        FakeCurrencyProvider provider = new FakeCurrencyProvider(CurrencyMode.STANDARD, 100);
        assertFalse(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", 0.0));
        assertFalse(BlackjackWagerTransaction.tryWithdraw(provider, null, "table", -5.0));
        assertEquals(0, provider.withdrawCallCount);
    }

    @Test
    void nullProviderNeverThrows() {
        assertFalse(BlackjackWagerTransaction.tryWithdraw(null, null, "table", 20.0));
    }
}
