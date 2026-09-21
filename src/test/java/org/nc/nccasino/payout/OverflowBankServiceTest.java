package org.nc.nccasino.payout;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.helpers.Preferences;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end behavior of a real payout meeting a real (fake) inventory:
 * what fits is delivered, the preference decides the rest, drops are capped,
 * and the remainder is durably banked.
 */
class OverflowBankServiceTest {

    private static final Material CURRENCY = Material.EMERALD;
    private static final int STACK = OverflowTestSupport.STACK_SIZE;
    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");
    private static final BankedCurrency DIAMONDS =
        new BankedCurrency(CurrencyMode.STANDARD, "DIAMOND", "High Roller Chip");

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private FileConfiguration config;
    private OverflowBankStore store;
    private OverflowBankService service;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        config = new YamlConfiguration();
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OverflowBankServiceTest"));
        when(plugin.getConfig()).thenReturn(config);

        // Default server policy: player choice, Bank selected, 36-stack cap.
        config.set(OverflowSettings.PATH_MODE, "PLAYER_CHOICE");
        config.set(OverflowSettings.PATH_DEFAULT, "BANK");
        config.set(OverflowSettings.PATH_MAX_DROP_STACKS, 36);
        config.set(OverflowSettings.PATH_CLEAR_BEFORE_WAGER, true);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(null);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        store = new OverflowBankStore(plugin);
        service = new OverflowBankService(plugin, store);
    }

    private void usePreference(OverflowPreference preference) {
        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(preference);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);
    }

    @Test
    void partialDeliveryPutsWhatFitsInTheInventoryAndPersistsTheRemainder() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(2); // room for exactly two stacks

        long payout = 10_000L;
        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, payout);

        assertEquals(2L * STACK, outcome.toInventory());
        assertEquals(0, outcome.dropped(), "BANK preference must not drop");
        assertEquals(payout - 2L * STACK, outcome.banked());
        assertTrue(outcome.settled());
        assertEquals(2L * STACK, fixture.unitsHeld(CURRENCY));

        // The remainder is durable, not just in memory.
        OverflowBankStore reloaded = new OverflowBankStore(plugin);
        assertEquals(payout - 2L * STACK, reloaded.balanceOf(fixture.playerId, EMERALDS));
    }

    @Test
    void bankPreferenceNeverDropsAnything() {
        usePreference(OverflowPreference.BANK);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 5_000L);

        assertEquals(0, outcome.toInventory());
        assertEquals(0, outcome.dropped());
        assertEquals(5_000L, outcome.banked());
        assertTrue(fixture.world.dropped.isEmpty());
    }

    @Test
    void dropPreferenceDropsUpToTheCapAndBanksTheRest() {
        usePreference(OverflowPreference.DROP);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        long payout = 10_000L;
        long cap = 36L * STACK;
        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, payout);

        assertEquals(0, outcome.toInventory());
        assertEquals(cap, outcome.dropped());
        assertEquals(payout - cap, outcome.banked());
        assertEquals(cap, fixture.world.droppedUnits());
        assertTrue(outcome.settled());
    }

    @Test
    void dropCapIsHonoredExactlyWhenReconfigured() {
        usePreference(OverflowPreference.DROP);
        config.set(OverflowSettings.PATH_MAX_DROP_STACKS, 2);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 10_000L);

        assertEquals(2L * STACK, outcome.dropped());
        assertEquals(2L * STACK, fixture.world.droppedUnits());
        assertEquals(10_000L - 2L * STACK, outcome.banked());
    }

    @Test
    void serverForcedBankModeOverridesAPlayerWhoChoseDrop() {
        usePreference(OverflowPreference.DROP);
        config.set(OverflowSettings.PATH_MODE, "BANK");
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 1_000L);

        assertEquals(0, outcome.dropped());
        assertEquals(1_000L, outcome.banked());
    }

    @Test
    void nothingIsEverDeletedNoMatterHowLargeThePayout() {
        usePreference(OverflowPreference.DROP);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(1);

        long payout = 50_000_000L;
        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, payout);

        assertEquals(payout, outcome.toInventory() + outcome.dropped() + outcome.banked());
        assertTrue(outcome.settled());
    }

    // ---- claiming ---------------------------------------------------------

    @Test
    void claimDeliversWhatNowFitsAndLeavesTheRestBanked() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        service.deliver(fixture.player, EMERALDS, 10_000L);
        assertEquals(10_000L, store.balanceOf(fixture.playerId, EMERALDS));

        // Player frees up three slots and claims.
        fixture.leaveFreeSlots(3);
        long remaining = service.claimAll(fixture.player);

        assertEquals(3L * STACK, fixture.unitsHeld(CURRENCY));
        assertEquals(10_000L - 3L * STACK, remaining);
        assertEquals(remaining, store.balanceOf(fixture.playerId, EMERALDS));
    }

    @Test
    void claimNeverDropsEvenUnderDropPreference() {
        usePreference(OverflowPreference.DROP);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        store.credit(fixture.playerId, EMERALDS, 5_000L);

        service.claimAll(fixture.player);

        assertTrue(fixture.world.dropped.isEmpty(),
            "a claim must not scatter winnings on the ground -- that would clear the wager block unsafely");
        assertEquals(5_000L, store.balanceOf(fixture.playerId, EMERALDS));
    }

    @Test
    void aFullyClaimedBankIsRemovedFromDisk() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        store.credit(fixture.playerId, EMERALDS, 100L);
        fixture.leaveFreeSlots(36);

        assertEquals(0L, service.claimAll(fixture.player));
        assertFalse(store.hasAnyBalance(fixture.playerId));
        assertFalse(new OverflowBankStore(plugin).hasAnyBalance(fixture.playerId));
        assertEquals(100L, fixture.unitsHeld(CURRENCY));
    }

    // ---- the wagering gate ------------------------------------------------

    @Test
    void aWagerIsAutomaticallyClearedWhenTheBankNowFits() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        store.credit(fixture.playerId, EMERALDS, 200L);
        fixture.leaveFreeSlots(36);

        assertEquals(0L, service.clearForWager(fixture.player),
            "the pre-wager attempt should deliver the balance and let play continue");
        assertEquals(200L, fixture.unitsHeld(CURRENCY));
        assertFalse(service.isBlocked(fixture.playerId));
    }

    @Test
    void aWagerStaysBlockedWhileAnythingRemainsBanked() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        store.credit(fixture.playerId, EMERALDS, 5_000L);
        fixture.fillCompletely();

        assertEquals(5_000L, service.clearForWager(fixture.player));
        assertTrue(service.isBlocked(fixture.playerId));
    }

    @Test
    void bankedCurrencyBlocksAWagerInAnUnrelatedCurrency() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        // Banked diamonds, and no room for them.
        fixture.fillCompletely();
        store.credit(fixture.playerId, DIAMONDS, 900L);

        // A dealer paying emeralds is still blocked: the gate asks only
        // whether anything at all is banked.
        assertTrue(service.isBlocked(fixture.playerId));
        assertEquals(900L, service.clearForWager(fixture.player));
    }

    @Test
    void anEmptyBankNeverBlocksAWager() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        assertFalse(service.isBlocked(fixture.playerId));
        assertEquals(0L, service.clearForWager(fixture.player));
    }

    @Test
    void disablingTheAutomaticAttemptStillBlocksButDoesNotTouchTheInventory() {
        config.set(OverflowSettings.PATH_CLEAR_BEFORE_WAGER, false);
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(36);
        store.credit(fixture.playerId, EMERALDS, 100L);

        assertEquals(100L, service.clearForWager(fixture.player),
            "with the automatic attempt off the balance still blocks play");
        assertEquals(0L, fixture.unitsHeld(CURRENCY), "no delivery should have been attempted");
    }

    // ---- failure handling -------------------------------------------------

    @Test
    void aFailedBankWriteIsReportedUnsettledRatherThanAsACompletedPayout() {
        // Make the data directory unwritable by replacing it with a file, so
        // saving overflow-bank.yml genuinely fails.
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        File dataDir = new File(tempDir.toFile(), "data");
        File bankFile = new File(dataDir, "overflow-bank.yml");
        assertTrue(bankFile.getParentFile().exists());
        // A directory in place of the target file makes save() throw.
        assertTrue(bankFile.delete() || !bankFile.exists());
        assertTrue(bankFile.mkdirs());

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 1_000L);

        assertFalse(outcome.settled(), "a failed bank write must never be reported as settled");
        assertEquals(1_000L, outcome.unsettled());
        assertEquals(0L, outcome.banked());
    }
}
