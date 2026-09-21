package org.nc.nccasino.currency;

import org.bukkit.entity.Player;

/**
 * Game-neutral wager debit helper -- decimal-aware for Vault (exact
 * fractional stakes), whole-unit for every other {@link CurrencyProvider}
 * mode, and, critically, never treats a preceding balance check as proof a
 * debit succeeded. The transaction's own result is authoritative: a Vault
 * economy call can report a sufficient balance and then still fail the
 * actual withdrawal, and {@code StandardItemCurrencyProvider#withdraw} can
 * itself partially remove items before reporting a shortfall, with no
 * rollback of its own.
 *
 * <p>This is the extracted, game-neutral twin of Blackjack's
 * {@code BlackjackWagerTransaction} -- any game that needs the same
 * debit-with-rollback contract (Slots included) should use this rather than
 * depending on a Blackjack-specific class.
 */
public final class WagerTransaction {

    private WagerTransaction() {
    }

    /**
     * @return whether exactly {@code amount} was actually debited from {@code player}'s balance via {@code provider}
     */
    public static boolean tryWithdraw(CurrencyProvider provider, Player player, String internalName, double amount) {
        if (provider == null || amount <= 0.0) {
            return false;
        }
        if (provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
            return vaultProvider.withdrawDecimal(player, internalName, MoneyHelper.clampNonNegative(MoneyHelper.bd(amount)));
        }

        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) {
            return false;
        }

        int withdrawn = provider.withdraw(player, internalName, requiredAmount);
        if (withdrawn == requiredAmount) {
            return true;
        }
        if (withdrawn > 0) {
            // A partial withdrawal already happened inside the provider
            // (e.g. StandardItemCurrencyProvider pulls whatever it can find
            // with no rollback of its own) -- give it back so a failed
            // debit never leaves the player short.
            provider.deposit(player, internalName, withdrawn);
        }
        return false;
    }
}
