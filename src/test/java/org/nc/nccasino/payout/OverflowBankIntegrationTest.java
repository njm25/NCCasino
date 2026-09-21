package org.nc.nccasino.payout;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.listeners.PlayerSessionListener;
import org.nc.nccasino.localization.LocalizationService;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the overflow bank behaves once it is wired to the rest of the plugin:
 * the hand-off from a pending payout, join-time delivery, and the deliberate
 * inertness of the periodic reminder.
 */
class OverflowBankIntegrationTest {

    private static final Material CURRENCY = Material.EMERALD;
    private static final int STACK = OverflowTestSupport.STACK_SIZE;
    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");

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
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OverflowBankIntegrationTest"));
        when(plugin.getConfig()).thenReturn(config);

        config.set(OverflowSettings.PATH_MODE, "BANK");
        config.set(OverflowSettings.PATH_MAX_DROP_STACKS, 36);
        config.set(OverflowSettings.PATH_CLEAR_BEFORE_WAGER, true);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(null);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        store = new OverflowBankStore(plugin);
        service = new OverflowBankService(plugin, store);
        when(plugin.getOverflowBankStore()).thenReturn(store);
        when(plugin.getOverflowBankService()).thenReturn(service);

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(Player.class), anyString(), any(Object[].class))).thenReturn("msg");
        when(plugin.getLocalization()).thenReturn(localization);
    }

    // ---- pending payout hand-off -----------------------------------------

    @Test
    void aPendingItemPayoutThatDoesNotFitOverflowsIntoTheBankAndIsSettled() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(1); // room for one stack only

        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        PendingPayout payout = PendingPayout.create(
            fixture.playerId, "Slots", "slots-a", CurrencyMode.STANDARD,
            "EMERALD", "Casino Token", 5_000, PayoutMessages.committedResultContext("Slots"));
        assertTrue(payouts.addPendingPayout(payout));

        DeliveryResult result = payouts.attemptDeliver(fixture.player);

        assertEquals(1, result.delivered().size(), "the pending record is fully resolved");
        assertTrue(result.stillPending().isEmpty());
        assertFalse(payouts.hasPending(fixture.playerId));

        assertEquals(STACK, fixture.unitsHeld(CURRENCY));
        assertEquals(5_000L - STACK, store.balanceOf(fixture.playerId, EMERALDS),
            "what could not fit becomes a bank balance, not a pile on the floor");
        assertTrue(fixture.world.dropped.isEmpty());
    }

    @Test
    void aPendingItemPayoutStaysPendingWhenTheBankCannotBePersisted() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        PendingPayout payout = PendingPayout.create(
            fixture.playerId, "Slots", "slots-a", CurrencyMode.STANDARD,
            "EMERALD", "Casino Token", 5_000, "ctx");
        assertTrue(payouts.addPendingPayout(payout));

        // Break the bank file so the overflow cannot be recorded.
        java.io.File bankFile = new java.io.File(tempDir.toFile(), "data/overflow-bank.yml");
        if (bankFile.exists()) {
            assertTrue(bankFile.delete());
        }
        assertTrue(bankFile.mkdirs());

        DeliveryResult result = payouts.attemptDeliver(fixture.player);

        assertTrue(result.delivered().isEmpty());
        assertEquals(1, result.stillPending().size(),
            "an unbankable remainder must leave the payout pending, never report it delivered");
        assertTrue(payouts.hasPending(fixture.playerId));
    }

    // ---- join delivery ----------------------------------------------------

    @Test
    void joiningDeliversBankedWinningsWhenThereIsNowRoom() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(36);
        store.credit(fixture.playerId, EMERALDS, 500L);
        // Built before stubbing: the constructor itself calls back into the
        // plugin mock, which Mockito forbids inside thenReturn(...).
        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(payouts);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(fixture.player);

        new PlayerSessionListener(plugin).onPlayerJoin(event);

        assertEquals(500L, fixture.unitsHeld(CURRENCY));
        assertFalse(store.hasAnyBalance(fixture.playerId));
    }

    @Test
    void joiningWithNoRoomLeavesTheBalanceBankedAndTellsThePlayer() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        store.credit(fixture.playerId, EMERALDS, 500L);
        // Built before stubbing: the constructor itself calls back into the
        // plugin mock, which Mockito forbids inside thenReturn(...).
        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(payouts);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(fixture.player);

        new PlayerSessionListener(plugin).onPlayerJoin(event);

        assertEquals(500L, store.balanceOf(fixture.playerId, EMERALDS));
        verify(fixture.player).sendMessage(anyString());
    }

    @Test
    void joiningWithAnEmptyBankTouchesNothing() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(36);
        // Built before stubbing: the constructor itself calls back into the
        // plugin mock, which Mockito forbids inside thenReturn(...).
        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(payouts);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(fixture.player);

        new PlayerSessionListener(plugin).onPlayerJoin(event);

        verify(fixture.inventory, never()).addItem(any());
        verify(fixture.player, never()).sendMessage(anyString());
    }

    // ---- the reminder is informational only -------------------------------

    @Test
    void thePeriodicReminderNotifiesWithoutDeliveringOrTouchingTheInventory() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(36); // plenty of room -- delivery WOULD succeed
        store.credit(fixture.playerId, EMERALDS, 500L);

        new OverflowBankReminder(plugin, store).remindPlayer(fixture.player);

        assertEquals(500L, store.balanceOf(fixture.playerId, EMERALDS),
            "a reminder must never deliver, even when everything would fit");
        assertEquals(0L, fixture.unitsHeld(CURRENCY));
        verify(fixture.inventory, never()).addItem(any());
        verify(fixture.player).sendMessage(anyString());
    }

    @Test
    void thePeriodicReminderSaysNothingToAPlayerWithAnEmptyBank() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();

        new OverflowBankReminder(plugin, store).remindPlayer(fixture.player);

        verify(fixture.player, never()).sendMessage(anyString());
        verify(fixture.inventory, never()).addItem(any());
    }
}
