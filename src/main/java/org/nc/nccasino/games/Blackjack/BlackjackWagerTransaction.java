package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.currency.VaultCurrencyProvider;

/**
 * The provider-backed half of a wager debit -- decimal-aware for Vault
 * (exact fractional stakes, e.g. a 12.5 insurance cost), whole-unit for
 * every other {@link CurrencyProvider} mode, and, critically, never
 * treats a preceding balance check as proof a debit succeeded. The
 * transaction's own result is authoritative: a Vault economy call can
 * report a sufficient balance and then still fail the actual withdrawal
 * (a plugin hook error, a race with another transaction, etc.), and
 * {@code StandardItemCurrencyProvider#withdraw} can itself partially
 * remove items before reporting a shortfall, with no rollback of its own.
 * {@code BlackjackInventory#tryRemoveWager} genuinely delegates to
 * {@link #tryWithdraw} for this -- it is not a parallel simulation.
 *
 * <p>Only the raw no-provider physical-material fallback (no
 * {@link CurrencyProvider} configured for this dealer at all) stays
 * inline in the controller, since it needs the controller's own
 * {@code Material}/{@code Inventory} lookups.
 */
public final class BlackjackWagerTransaction {

    private BlackjackWagerTransaction() {
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
