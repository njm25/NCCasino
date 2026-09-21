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
import java.util.List;
import java.util.Map;
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
 * What happens to money when the overflow bank cannot be written.
 *
 * <p>Two rules are being pinned down here. First, the physical drop cap is
 * absolute: the old code deliberately bypassed it "as a last resort" when
 * persistence failed, which turned a bounded overflow into an unbounded entity
 * burst. Second, an unsettled remainder must become a durable, retryable
 * obligation rather than a log line -- so nothing is lost and nothing is ever
 * reported as a completed payout when it is not.
 */
class UnsettledRemainderTest {

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
        when(plugin.getLogger()).thenReturn(Logger.getLogger("UnsettledRemainderTest"));
        when(plugin.getConfig()).thenReturn(config);
        config.set(OverflowSettings.PATH_MODE, "DROP");
        config.set(OverflowSettings.PATH_MAX_DROP_STACKS, 36);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(null);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(Player.class), anyString(), any(Object[].class))).thenReturn("msg");
        when(plugin.getLocalization()).thenReturn(localization);

        store = new OverflowBankStore(plugin);
        service = new OverflowBankService(plugin, store);
        when(plugin.getOverflowBankStore()).thenReturn(store);
        when(plugin.getOverflowBankService()).thenReturn(service);
    }

    private void breakBankFile() {
        File bankFile = new File(tempDir.toFile(), "data/overflow-bank.yml");
        if (bankFile.isFile()) {
            assertTrue(bankFile.delete());
        }
        if (!bankFile.isDirectory()) {
            assertTrue(bankFile.mkdirs());
        }
    }

    // ---- the drop cap is absolute, including during a write failure -------

    @Test
    void aFailedBankWriteDropsNothingAtAllRatherThanBypassingTheCap() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        breakBankFile();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 5_000_000L);

        assertEquals(0L, outcome.dropped(),
            "a persistence failure must never license an uncapped drop");
        assertTrue(fixture.world.dropped.isEmpty());
        assertFalse(outcome.settled(), "a failed write is never a completed settlement");
        assertEquals(5_000_000L, outcome.unsettled());
    }

    @Test
    void theDropCapStillBoundsAHugePayoutOnTheSuccessPath() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 5_000_000L);

        assertEquals(36L * STACK, outcome.dropped());
        assertEquals(36L * STACK, fixture.world.droppedUnits());
        assertEquals(5_000_000L - 36L * STACK, outcome.banked());
        assertTrue(outcome.settled());
    }

    @Test
    void noDropEverExceedsTheConfiguredCapAtAnyCapSetting() {
        for (int capStacks : new int[] {0, 1, 5, 36}) {
            config.set(OverflowSettings.PATH_MAX_DROP_STACKS, capStacks);
            OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
            fixture.fillCompletely();

            ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 1_000_000L);

            assertEquals((long) capStacks * STACK, outcome.dropped(),
                "cap of " + capStacks + " stacks must bound the drop exactly");
            assertEquals(outcome.dropped(), fixture.world.droppedUnits());
        }
    }

    // ---- an unsettled remainder becomes a retryable obligation ------------

    @Test
    void anUnsettledRemainderIsRetainedAsARetryablePendingPayout() {
        UUID playerId = UUID.randomUUID();
        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(payouts);

        assertTrue(UnsettledPayouts.retain(plugin, playerId, "Mines", "mines-a",
            CurrencyMode.STANDARD, "EMERALD", "Casino Token", 4_200L));

        assertTrue(payouts.hasPending(playerId));
        assertEquals(4_200d, payouts.getPending(playerId).get(0).amount());
        // Durable: it survives a restart and will retry on join.
        assertTrue(new PendingPayoutStore(plugin).hasPending(playerId));
    }

    @Test
    void aRetainedRemainderReportsFailureWhenItCannotBePersistedEither() {
        UUID playerId = UUID.randomUUID();
        PendingPayoutStore payouts = new PendingPayoutStore(plugin);
        when(plugin.getPendingPayoutStore()).thenReturn(payouts);

        File pendingFile = new File(tempDir.toFile(), "data/pending-payouts.yml");
        if (pendingFile.isFile()) {
            assertTrue(pendingFile.delete());
        }
        assertTrue(pendingFile.mkdirs());

        assertFalse(UnsettledPayouts.retain(plugin, playerId, "Mines", "mines-a",
            CurrencyMode.STANDARD, "EMERALD", "Casino Token", 4_200L),
            "an unrecordable remainder must be reported as a failure, never as settled");
    }

    @Test
    void retainingNothingIsTriviallySuccessful() {
        assertTrue(UnsettledPayouts.retain(plugin, UUID.randomUUID(), "Mines", "mines-a",
            CurrencyMode.STANDARD, "EMERALD", "Token", 0L));
    }

    // ---- Roulette: settlement pays an above-clamp payout in full ---------
    // (the placement half lives in BettingTableItemPayoutPolicyTest, where the
    // package-private ceiling API is visible)

    @Test
    void aCommittedPayoutAboveTheOldMillionClampIsDeliveredInFull() {
        long committedPayout = 3_600_000L;
        assertTrue(committedPayout > 1_000_000L, "fixture must exceed the removed clamp");

        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(2);
        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, committedPayout);

        assertTrue(outcome.settled());
        assertEquals(committedPayout,
            outcome.toInventory() + outcome.dropped() + outcome.banked(),
            "every unit of an accepted payout above 1,000,000 must be delivered, dropped or banked");
        assertEquals(2L * STACK, outcome.toInventory());
    }

    @Test
    void theRouletteClampConstantIsGoneEntirely() throws IOException {
        String source = Files.readString(
            Paths.get("src/main/java/org/nc/nccasino/games/Roulette/BettingTable.java"));
        assertFalse(source.contains("DEFENSIVE_ITEM_DELIVERY_CEILING"),
            "the post-result clamp that silently discarded the remainder must not come back");
    }

    // ---- every money-bearing caller retains rather than drops -------------

    @Test
    void everyBankFailingPayoutCallerRetainsADurableObligation() throws IOException {
        // Each of these hands money to a player and has no settlement machine
        // of its own, so each must convert an unsettled remainder into a
        // retryable record instead of dropping or merely logging it.
        //
        // Each is paired with the retention entry point it legitimately owns.
        // Naming one symbol for all of them would be wrong: Blackjack retains
        // through its own durable helper (and through deliverAndRetain, which
        // calls UnsettledPayouts internally), so demanding a literal
        // "UnsettledPayouts.retain" in that file asserts an implementation
        // detail it does not have rather than the property that matters.
        Map<String, List<String>> retentionOwners = Map.of(
            "entities/Client.java", List.of("UnsettledPayouts.retain"),
            "entities/Server.java", List.of("UnsettledPayouts.retain"),
            "games/Mines/MinesTable.java", List.of("UnsettledPayouts.retain"),
            "games/Blackjack/BlackjackInventory.java",
                List.of("queueBlackjackPendingPayout", "deliverAndRetain"));

        for (Map.Entry<String, List<String>> entry : retentionOwners.entrySet()) {
            String relative = entry.getKey();
            String source = Files.readString(
                Paths.get("src/main/java/org/nc/nccasino").resolve(relative));
            for (String owner : entry.getValue()) {
                assertTrue(source.contains(owner),
                    relative + " must retain an unsettled remainder via " + owner);
            }
            assertFalse(source.contains("deliverOrDrop"),
                relative + " must not use the removed uncapped-drop fallback");
        }

        // Roulette retains through its own pre-existing pending-payout helper.
        String roulette = Files.readString(
            Paths.get("src/main/java/org/nc/nccasino/games/Roulette/BettingTable.java"));
        assertTrue(roulette.contains("queueFailedDepositPayout(player.getUniqueId(), outcome.unsettled()"),
            "Roulette must durably queue whatever the bank could not record");
    }

    @Test
    void theUncappedDropFallbackNoLongerExistsAnywhere() throws IOException {
        Path root = Paths.get("src/main/java/org/nc/nccasino");
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            List<Path> offenders = walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try {
                        return Files.readString(p).contains("deliverOrDrop");
                    } catch (IOException e) {
                        return false;
                    }
                })
                .toList();
            assertTrue(offenders.isEmpty(), "deliverOrDrop must be gone: " + offenders);
        }
    }
}
