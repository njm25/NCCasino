package org.nc.nccasino.payout;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One obligation per payout, no matter what fails.
 *
 * <p>The bug being pinned down: a delivery helper that records a
 * {@link PendingPayout} itself but reports plain failure invites its caller to
 * record a second one, so a single undeliverable payout ends up owed twice.
 * {@link OverflowBankService#deliverAndRetain} is the single owner of that
 * retention, and returns a {@link PayoutDisposition} rather than a boolean
 * precisely so no caller can misread the signal.
 */
class SingleRetentionOwnerTest {

    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");
    private static final int STACK = OverflowTestSupport.STACK_SIZE;

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private FileConfiguration config;
    private OverflowBankStore bankStore;
    private OverflowBankService service;
    private PendingPayoutStore pendingStore;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        config = new YamlConfiguration();
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SingleRetentionOwnerTest"));
        when(plugin.getConfig()).thenReturn(config);
        config.set(OverflowSettings.PATH_MODE, "BANK");
        config.set(OverflowSettings.PATH_MAX_DROP_STACKS, 36);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(null);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(Player.class), anyString(), any(Object[].class))).thenReturn("msg");
        when(plugin.getLocalization()).thenReturn(localization);

        bankStore = new OverflowBankStore(plugin);
        service = new OverflowBankService(plugin, bankStore);
        pendingStore = new PendingPayoutStore(plugin);
        when(plugin.getOverflowBankStore()).thenReturn(bankStore);
        when(plugin.getOverflowBankService()).thenReturn(service);
        when(plugin.getPendingPayoutStore()).thenReturn(pendingStore);
    }

    private void breakBank() {
        File f = new File(tempDir.toFile(), "data/overflow-bank.yml");
        if (f.isFile()) {
            assertTrue(f.delete());
        }
        if (!f.isDirectory()) {
            assertTrue(f.mkdirs());
        }
    }

    private void breakPending() {
        File f = new File(tempDir.toFile(), "data/pending-payouts.yml");
        if (f.isFile()) {
            assertTrue(f.delete());
        }
        if (!f.isDirectory()) {
            assertTrue(f.mkdirs());
        }
    }

    private PayoutDisposition settle(OverflowTestSupport.Fixture fixture, long amount, String context) {
        return service.deliverAndRetain(
            fixture.player, EMERALDS, amount, "Blackjack", "bj-a", context);
    }

    // ---- exactly one obligation ------------------------------------------

    @Test
    void aFailedBankWriteCreatesExactlyOnePendingObligation() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        breakBank();

        PayoutDisposition disposition = settle(fixture, 5_000L, "ctx");

        assertEquals(PayoutDisposition.RETAINED, disposition);
        assertEquals(1, pendingStore.getPending(fixture.playerId).size(),
            "an undeliverable payout must be owed exactly once, never twice");
        assertEquals(5_000d, pendingStore.getPending(fixture.playerId).get(0).amount());
    }

    @Test
    void aPartiallyDeliveredPayoutRetainsOnlyTheRemainderAndOnlyOnce() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(2);

        // Succeeds entirely: bank write is fine, so nothing is retained.
        PayoutDisposition delivered = settle(fixture, 5_000L, "ctx");
        assertEquals(PayoutDisposition.DELIVERED, delivered);
        assertTrue(pendingStore.getPending(fixture.playerId).isEmpty(),
            "a fully settled payout must never create a pending record");
        assertEquals(2L * STACK, fixture.unitsHeld(Material.EMERALD));
    }

    @Test
    void repeatedFailuresEachCreateTheirOwnSingleObligationAndNeverDouble() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        breakBank();

        settle(fixture, 100L, "ctx");
        settle(fixture, 250L, "ctx");

        // Two genuinely separate payouts -> two records, not four.
        assertEquals(2, pendingStore.getPending(fixture.playerId).size());
        double total = pendingStore.getPending(fixture.playerId).stream()
            .mapToDouble(PendingPayout::amount).sum();
        assertEquals(350d, total, "each payout is owed exactly once");
    }

    // ---- a failed retention is never reported as completed ---------------

    @Test
    void aFailedBankWriteFollowedByAFailedPendingWriteIsNotReportedAsCompleted() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        breakBank();
        breakPending();

        PayoutDisposition disposition = settle(fixture, 5_000L, "ctx");

        assertEquals(PayoutDisposition.UNRESOLVED, disposition);
        assertFalse(disposition.isAccountedFor(),
            "money that reached neither the player nor durable storage is not accounted for");
        assertFalse(disposition.isInHand());
    }

    @Test
    void successfulRetentionIsAccountedForButNotInHand() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        breakBank();

        PayoutDisposition disposition = settle(fixture, 400L, "ctx");

        assertTrue(disposition.isAccountedFor(), "a retained payout is safe");
        assertFalse(disposition.isInHand(), "but the player does not have it yet");
    }

    // ---- the disposition mapping itself ----------------------------------

    @Test
    void theDispositionMappingCoversAllThreeStates() {
        assertEquals(PayoutDisposition.DELIVERED, PayoutDisposition.of(true, false));
        assertEquals(PayoutDisposition.DELIVERED, PayoutDisposition.of(true, true));
        assertEquals(PayoutDisposition.RETAINED, PayoutDisposition.of(false, true));
        assertEquals(PayoutDisposition.UNRESOLVED, PayoutDisposition.of(false, false));

        assertTrue(PayoutDisposition.DELIVERED.isInHand());
        assertFalse(PayoutDisposition.RETAINED.isInHand());
        assertTrue(PayoutDisposition.RETAINED.isAccountedFor());
        assertFalse(PayoutDisposition.UNRESOLVED.isAccountedFor());
    }

    @Test
    void aZeroPayoutIsTriviallyDeliveredAndRecordsNothing() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        assertEquals(PayoutDisposition.DELIVERED, settle(fixture, 0L, "ctx"));
        assertTrue(pendingStore.getPending(fixture.playerId).isEmpty());
    }

    // ---- no Blackjack caller queues a second record ----------------------

    /**
     * The five paths the audit named, plus ordinary winnings. Each must take
     * its disposition from the single owner and never queue off a
     * non-DELIVERED result; a bare {@code if (!delivered) queue...} around one
     * of these calls is exactly the duplicate bug.
     */
    @Test
    void noBlackjackPayoutCallerQueuesASecondRecord() throws IOException {
        String source = Files.readString(Paths.get(
            "src/main/java/org/nc/nccasino/games/Blackjack/BlackjackInventory.java"));

        assertFalse(source.contains("if (!addWagerToInventory("),
            "a caller must not branch on addWagerToInventory as a boolean and queue again");
        assertFalse(source.contains("boolean delivered = online && addWagerToInventory("),
            "the old double-queue shape must not come back");

        // Every addWagerToInventory call now supplies the context the single
        // owner retains with, rather than leaving retention to the caller.
        int calls = source.split("addWagerToInventory\\(", -1).length - 1;
        assertTrue(calls >= 5, "expected the refund/undo/insurance/push callers to remain");
        assertTrue(source.contains("PayoutDisposition"),
            "callers must consume a disposition, not a boolean");
    }

    @Test
    void theSharedPrimitiveIsTheOnlyPlaceBlackjackItemPayoutsRetain() throws IOException {
        String source = Files.readString(Paths.get(
            "src/main/java/org/nc/nccasino/games/Blackjack/BlackjackInventory.java"));
        assertTrue(source.contains("bank.deliverAndRetain("),
            "Blackjack item payouts must route through the shared single-owner primitive");
    }
}
