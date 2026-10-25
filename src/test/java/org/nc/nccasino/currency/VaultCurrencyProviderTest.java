package org.nc.nccasino.currency;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.economy.VaultHook;

import java.math.BigDecimal;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code VaultCurrencyProvider#deposit} is the exact method
 * {@code BlackjackInventory} (via {@code addWagerToInventory}/{@code payOut})
 * genuinely calls for every Vault credit -- this covers the audited defect
 * that it used to be {@code void}, silently discarding a failed
 * {@link EconomyResponse} and letting the round proceed as though the
 * player had actually been paid. The return value is now authoritative:
 * {@code true} only when Vault itself confirms the deposit (or nothing was
 * actually owed), {@code false} otherwise -- callers are responsible for
 * queuing a durable {@code PendingPayout} on {@code false}.
 */
class VaultCurrencyProviderTest {

    private Nccasino plugin;
    private VaultHook vaultHook;
    private Economy economy;
    private VaultCurrencyProvider provider;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        vaultHook = mock(VaultHook.class);
        economy = mock(Economy.class);
        player = mock(Player.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("VaultCurrencyProviderTest"));
        when(plugin.getVaultHook()).thenReturn(vaultHook);
        when(vaultHook.getEconomy()).thenReturn(economy);
        provider = new VaultCurrencyProvider(plugin);
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(0, amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "simulated Vault failure");
    }

    @Test
    void confirmedVaultDepositReturnsTrue() {
        when(economy.depositPlayer(player, 50.0)).thenReturn(success(50));
        assertTrue(provider.deposit(player, "table", BigDecimal.valueOf(50)));
        verify(economy, times(1)).depositPlayer(player, 50.0);
    }

    @Test
    void failedVaultDepositReturnsFalseRatherThanSilentlyDiscardingTheFailure() {
        when(economy.depositPlayer(eq(player), eq(25.0))).thenReturn(failure());
        assertFalse(provider.deposit(player, "table", BigDecimal.valueOf(25)));
    }

    @Test
    void nullEconomyResponseIsTreatedAsFailure() {
        when(economy.depositPlayer(player, 10.0)).thenReturn(null);
        assertFalse(provider.deposit(player, "table", BigDecimal.valueOf(10)));
    }

    @Test
    void unavailableEconomyIsTreatedAsFailureNotSuccess() {
        when(plugin.getVaultHook()).thenReturn(null);
        assertFalse(provider.deposit(player, "table", BigDecimal.valueOf(10)));
    }

    @Test
    void nonPositiveAmountIsTriviallySuccessfulAndNeverCallsVault() {
        assertTrue(provider.deposit(player, "table", BigDecimal.ZERO));
        assertTrue(provider.deposit(player, "table", BigDecimal.valueOf(-5)));
        verify(economy, times(0)).depositPlayer(eq(player), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void nullPlayerOrAmountIsAFailure() {
        assertFalse(provider.deposit(null, "table", BigDecimal.TEN));
        assertFalse(provider.deposit(player, "table", null));
    }

    // --- Exact decimal amounts must survive the deposit call unrounded ---

    @Test
    void oddHalfDollarAmountsArePreservedExactly() {
        when(economy.depositPlayer(player, 12.5)).thenReturn(success(12.5));
        when(economy.depositPlayer(player, 37.5)).thenReturn(success(37.5));

        assertTrue(provider.deposit(player, "table", BigDecimal.valueOf(12.5)));
        assertTrue(provider.deposit(player, "table", BigDecimal.valueOf(37.5)));

        verify(economy, times(1)).depositPlayer(player, 12.5);
        verify(economy, times(1)).depositPlayer(player, 37.5);
    }

    // --- The int-amount interface overload delegates to the same authoritative BigDecimal path ---

    @Test
    void intOverloadReportsTheSameFailureAsTheDecimalPath() {
        when(economy.depositPlayer(player, 20.0)).thenReturn(failure());
        assertFalse(provider.deposit(player, "table", 20));
    }

    @Test
    void intOverloadReportsSuccess() {
        when(economy.depositPlayer(player, 20.0)).thenReturn(success(20));
        assertTrue(provider.deposit(player, "table", 20));
    }

    @Test
    void intOverloadRejectsPositiveDepositForNullPlayer() {
        assertFalse(provider.deposit(null, "table", 20));
        verify(economy, times(0)).depositPlayer(eq(player), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void multipleFailedDepositsForDifferentPlayersEachReportFailureIndependently() {
        Player other = mock(Player.class);
        when(economy.depositPlayer(player, 10.0)).thenReturn(failure());
        when(economy.depositPlayer(other, 10.0)).thenReturn(success(10));

        assertFalse(provider.deposit(player, "table", BigDecimal.TEN));
        assertTrue(provider.deposit(other, "table", BigDecimal.TEN));
    }

    // --- An economy plugin that throws (a database outage, or one disabled behind a stale reference) ---

    @Test
    void aDepositThatThrowsIsReportedAsNotDeliveredSoTheCallerQueuesIt() {
        when(economy.depositPlayer(player, 10.0)).thenThrow(new IllegalStateException("database down"));
        assertFalse(provider.deposit(player, "table", BigDecimal.TEN));
        assertFalse(provider.deposit(player, "table", 10));
    }

    @Test
    void aWithdrawalThatThrowsTakesNothing() {
        when(economy.getBalance(player)).thenReturn(100.0);
        when(economy.withdrawPlayer(eq(player), org.mockito.ArgumentMatchers.anyDouble()))
            .thenThrow(new IllegalStateException("database down"));
        assertEquals(0, provider.withdraw(player, "table", 10));
        assertFalse(provider.withdrawDecimal(player, "table", BigDecimal.TEN));
    }

    @Test
    void aBalanceCheckThatThrowsMeansNoFunds() {
        when(economy.getBalance(player)).thenThrow(new IllegalStateException("database down"));
        assertEquals(0, provider.getBalance(player, "table"));
        assertFalse(provider.has(player, "table", 1));
        assertFalse(provider.hasAtLeastDecimal(player, "table", BigDecimal.ONE));
        assertEquals(0, provider.withdraw(player, "table", 10));
        verify(economy, times(0)).withdrawPlayer(eq(player), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void aLaterDifferentFailureOnTheSameDealerIsStillLogged() {
        java.util.List<String> logged = new java.util.ArrayList<>();
        Logger logger = Logger.getLogger("VaultCurrencyProviderTest.warnings");
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { logged.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        when(plugin.getLogger()).thenReturn(logger);
        when(economy.getBalance(player)).thenReturn(100.0);
        when(economy.withdrawPlayer(player, 10.0)).thenReturn(failure());
        when(economy.depositPlayer(player, 10.0)).thenReturn(failure());

        provider.withdraw(player, "table", 10);
        provider.withdraw(player, "table", 10);
        provider.deposit(player, "table", BigDecimal.TEN);

        assertEquals(2, logged.size(), "one withdraw warning, then the deposit failure: " + logged);
    }
}
