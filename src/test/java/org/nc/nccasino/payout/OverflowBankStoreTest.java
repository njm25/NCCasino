package org.nc.nccasino.payout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverflowBankStoreTest {

    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");
    private static final BankedCurrency DIAMONDS =
        new BankedCurrency(CurrencyMode.STANDARD, "DIAMOND", "High Roller Chip");

    @TempDir
    Path tempDir;

    private Nccasino plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OverflowBankStoreTest"));
    }

    @Test
    void aCreditedBalanceSurvivesAReload() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        assertTrue(store.credit(playerId, EMERALDS, 9_488L));

        OverflowBankStore reloaded = new OverflowBankStore(plugin);
        assertEquals(9_488L, reloaded.balanceOf(playerId, EMERALDS));
        assertTrue(new File(tempDir.toFile(), "data/overflow-bank.yml").isFile());
    }

    @Test
    void creditsToTheSameCurrencyAccumulateIntoOneBalance() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        assertTrue(store.credit(playerId, EMERALDS, 100L));
        assertTrue(store.credit(playerId, EMERALDS, 250L));

        assertEquals(350L, store.balanceOf(playerId, EMERALDS));
        assertEquals(1, store.entriesFor(playerId).size());
    }

    @Test
    void differentCurrenciesAreTrackedSeparatelyButBothBlock() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        store.credit(playerId, EMERALDS, 10L);
        store.credit(playerId, DIAMONDS, 5L);

        assertEquals(10L, store.balanceOf(playerId, EMERALDS));
        assertEquals(5L, store.balanceOf(playerId, DIAMONDS));
        assertEquals(15L, store.totalUnits(playerId));
        assertTrue(store.hasAnyBalance(playerId));
    }

    @Test
    void aDisplayNameChangeDoesNotSplitOneMaterialsBalance() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        store.credit(playerId, new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token"), 10L);
        store.credit(playerId, new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Renamed Chip"), 10L);

        assertEquals(1, store.entriesFor(playerId).size(),
            "one material must be one claimable pile regardless of the dealer's display name");
        assertEquals(20L, store.totalUnits(playerId));
    }

    @Test
    void materialLookupIsCaseInsensitive() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        store.credit(playerId, new BankedCurrency(CurrencyMode.STANDARD, "emerald", "Token"), 7L);

        assertEquals(7L, store.balanceOf(playerId, EMERALDS));
    }

    @Test
    void debitReducesAndFinallyClearsTheBalance() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);
        store.credit(playerId, EMERALDS, 100L);

        store.debit(playerId, EMERALDS, 40L);
        assertEquals(60L, store.balanceOf(playerId, EMERALDS));

        store.debit(playerId, EMERALDS, 60L);
        assertFalse(store.hasAnyBalance(playerId));
        assertFalse(new OverflowBankStore(plugin).hasAnyBalance(playerId));
    }

    @Test
    void debitingMoreThanIsBankedClearsRatherThanGoingNegative() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);
        store.credit(playerId, EMERALDS, 10L);

        store.debit(playerId, EMERALDS, 999L);

        assertEquals(0L, store.balanceOf(playerId, EMERALDS));
        assertFalse(store.hasAnyBalance(playerId));
    }

    @Test
    void aFailedWriteReportsFailureAndLeavesNoPhantomBalance() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);
        store.credit(playerId, EMERALDS, 50L);

        // Replace the target file with a directory so save() cannot succeed.
        File bankFile = new File(tempDir.toFile(), "data/overflow-bank.yml");
        assertTrue(bankFile.delete());
        assertTrue(bankFile.mkdirs());

        assertFalse(store.credit(playerId, EMERALDS, 25L),
            "a credit that did not reach disk must report failure");
        assertEquals(50L, store.balanceOf(playerId, EMERALDS),
            "the in-memory balance must roll back to what is actually persisted");
    }

    @Test
    void aFirstCreditThatFailsToPersistLeavesTheStoreCompletelyEmpty() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        File bankFile = new File(tempDir.toFile(), "data/overflow-bank.yml");
        if (bankFile.exists()) {
            assertTrue(bankFile.delete());
        }
        assertTrue(bankFile.mkdirs());

        assertFalse(store.credit(playerId, EMERALDS, 25L));
        assertFalse(store.hasAnyBalance(playerId));
        assertTrue(store.entriesFor(playerId).isEmpty());
    }

    @Test
    void anOverflowingCreditIsRefusedRatherThanWrappingNegative() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);
        store.credit(playerId, EMERALDS, Long.MAX_VALUE - 5L);

        assertFalse(store.credit(playerId, EMERALDS, 100L));
        assertEquals(Long.MAX_VALUE - 5L, store.balanceOf(playerId, EMERALDS));
    }

    @Test
    void nonPositiveCreditsAreTriviallySuccessfulAndChangeNothing() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);

        assertTrue(store.credit(playerId, EMERALDS, 0L));
        assertTrue(store.credit(playerId, EMERALDS, -10L));
        assertFalse(store.hasAnyBalance(playerId));
    }

    @Test
    void anUnknownPlayerHasNothingBankedAndBlocksNothing() {
        OverflowBankStore store = new OverflowBankStore(plugin);
        UUID stranger = UUID.randomUUID();

        assertFalse(store.hasAnyBalance(stranger));
        assertEquals(0L, store.totalUnits(stranger));
        assertTrue(store.entriesFor(stranger).isEmpty());
    }

    @Test
    void largeBalancesBeyondDoublePrecisionAreStoredExactly() {
        UUID playerId = UUID.randomUUID();
        OverflowBankStore store = new OverflowBankStore(plugin);
        // Above 2^53: a double-typed balance would silently lose this unit.
        long huge = 9_007_199_254_740_993L;

        assertTrue(store.credit(playerId, EMERALDS, huge));
        assertEquals(huge, new OverflowBankStore(plugin).balanceOf(playerId, EMERALDS));
    }
}
