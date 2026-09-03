package org.nc.nccasino.payout;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.games.Slots.SlotsSettlementResult;
import org.nc.nccasino.games.Slots.SlotsSpinController;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
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
 * The double-payment hazard, cornered deliberately.
 *
 * <p>The dangerous shape is: part of a payout physically reaches the player,
 * the durable write for the rest fails, and the caller then retries the
 * <em>whole</em> original amount -- paying the delivered portion twice.
 *
 * <p>{@link OverflowBankService} closes this by reserving the entire overflow
 * in the bank before a single item moves. These tests inject write failures at
 * each point and assert the two properties that matter: nothing is ever lost,
 * and no retry can ever re-pay something already handed over.
 */
class OverflowBankFailureInjectionTest {

    private static final Material CURRENCY = Material.EMERALD;
    private static final int STACK = OverflowTestSupport.STACK_SIZE;
    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");

    /**
     * A fresh scripted stop sequence each call (one stop per reel, plus the
     * probabilistic-rounding draw) verified once, by direct outcome
     * evaluation, to commit a positive win at COLUMNS=3/LINES=5 under
     * BALANCED variance. A single constant roll cannot guarantee this under
     * the strip-based generator: differently-rotated reels landing on the
     * same numeric stop show different symbols.
     */
    private static org.nc.nccasino.games.Slots.SlotsRandomSource guaranteedWinRng() {
        java.util.Deque<Integer> queue = new java.util.ArrayDeque<>(java.util.List.of(42, 42, 43, 0));
        return bound -> queue.poll();
    }

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
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OverflowBankFailureInjectionTest"));
        when(plugin.getConfig()).thenReturn(config);
        config.set(OverflowSettings.PATH_MODE, "BANK");
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

    /** Replaces overflow-bank.yml with a directory so every save fails. */
    private void breakBankFile() {
        File bankFile = new File(tempDir.toFile(), "data/overflow-bank.yml");
        if (bankFile.isFile()) {
            assertTrue(bankFile.delete());
        }
        if (!bankFile.isDirectory()) {
            assertTrue(bankFile.mkdirs());
        }
    }

    private void repairBankFile() {
        File bankFile = new File(tempDir.toFile(), "data/overflow-bank.yml");
        if (bankFile.isDirectory()) {
            assertTrue(bankFile.delete());
        }
    }

    // ---- the core hazard --------------------------------------------------

    @Test
    void aFailedBankWriteDeliversNothingAtAllSoARetryCannotDoublePay() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(2); // room for 128 of the 10,000 owed
        breakBankFile();

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 10_000L);

        assertFalse(outcome.settled());
        assertEquals(10_000L, outcome.unsettled(), "the caller must still owe the whole payout");
        assertFalse(outcome.movedAnythingPhysical(),
            "nothing may be handed over when the remainder cannot be durably recorded");
        assertEquals(0L, fixture.unitsHeld(CURRENCY));
        assertTrue(fixture.world.dropped.isEmpty());
        assertFalse(store.hasAnyBalance(fixture.playerId));
    }

    @Test
    void retryingAfterTheBankRecoversPaysTheAmountExactlyOnce() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(2);
        breakBankFile();

        ItemDeliveryOutcome first = service.deliver(fixture.player, EMERALDS, 10_000L);
        assertEquals(10_000L, first.unsettled());

        repairBankFile();
        ItemDeliveryOutcome retry = service.deliver(fixture.player, EMERALDS, first.unsettled());

        assertTrue(retry.settled());
        long totalHandedOver = fixture.unitsHeld(CURRENCY)
            + fixture.world.droppedUnits()
            + store.totalUnits(fixture.playerId);
        assertEquals(10_000L, totalHandedOver,
            "across the failed attempt and the retry the player receives the payout exactly once");
        assertEquals(2L * STACK, fixture.unitsHeld(CURRENCY));
        assertEquals(10_000L - 2L * STACK, store.balanceOf(fixture.playerId, EMERALDS));
    }

    @Test
    void aPartialInsertAfterASuccessfulReservationLeavesOnlyTheShortfallUnsettled() {
        // The inventory claims room but Bukkit refuses half of it -- the one
        // case that can strand units after the reservation already succeeded.
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(4);
        stubInsertToAcceptOnly(fixture, STACK); // accepts one stack, rejects the rest

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 10_000L);

        assertEquals(STACK, outcome.toInventory());
        assertEquals(10_000L - STACK, outcome.banked(),
            "the reservation plus the re-banked shortfall must cover everything not inserted");
        assertEquals(0L, outcome.unsettled());
        assertTrue(outcome.settled());
        assertEquals(10_000L,
            outcome.toInventory() + outcome.dropped() + outcome.banked() + outcome.unsettled());
    }

    @Test
    void everyOutcomeAlwaysAccountsForEveryUnit() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(3);
        config.set(OverflowSettings.PATH_MODE, "DROP");

        ItemDeliveryOutcome outcome = service.deliver(fixture.player, EMERALDS, 25_000L);

        assertEquals(25_000L,
            outcome.toInventory() + outcome.dropped() + outcome.banked() + outcome.unsettled(),
            "inventory + dropped + banked + unsettled must always reconstruct the request");
        assertEquals(3L * STACK, outcome.toInventory());
        assertEquals(36L * STACK, outcome.dropped());
    }

    // ---- the equivalent Slots settlement path -----------------------------

    @Test
    void slotsRetainsOnlyTheUndeliveredRemainderSoARetryNeverDoublePays() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.trySpin(10, 3, 5, false,
            org.nc.nccasino.games.Slots.SlotsPaytable.forConfig(
                3, org.nc.nccasino.games.Slots.SlotsPaytable.DEFAULT_HOUSE_EDGE),
            guaranteedWinRng(), amount -> true);
        long committed = controller.pendingPayoutAmount();
        assertTrue(committed > 4, "fixture needs a payout big enough to split");

        long deliveredFirstAttempt = committed / 2;

        // First settlement hands over half and cannot record the rest.
        SlotsSettlementResult first = controller.settle(
            owed -> owed - deliveredFirstAttempt,
            owed -> false);

        assertEquals(SlotsSettlementResult.FAILED, first);
        assertEquals(committed - deliveredFirstAttempt, controller.pendingPayoutAmount(),
            "the controller must retain ONLY the undelivered remainder, never the original total");

        // The retry is offered exactly the remainder, not the whole win.
        long[] retriedWith = {-1L};
        SlotsSettlementResult retry = controller.retrySettlement(
            owed -> {
                retriedWith[0] = owed;
                return 0L;
            },
            owed -> false);

        assertEquals(SlotsSettlementResult.DELIVERED, retry);
        assertEquals(committed - deliveredFirstAttempt, retriedWith[0]);
        assertEquals(0L, controller.pendingPayoutAmount());
        assertEquals(committed, deliveredFirstAttempt + retriedWith[0],
            "the two attempts together pay the committed amount exactly once");
    }

    @Test
    void slotsQueuesOnlyTheRemainderWhenLiveDeliveryIsPartial() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.trySpin(10, 3, 5, false,
            org.nc.nccasino.games.Slots.SlotsPaytable.forConfig(
                3, org.nc.nccasino.games.Slots.SlotsPaytable.DEFAULT_HOUSE_EDGE),
            guaranteedWinRng(), amount -> true);
        long committed = controller.pendingPayoutAmount();
        long handedOver = committed / 2;

        long[] queuedWith = {-1L};
        SlotsSettlementResult result = controller.settle(
            owed -> owed - handedOver,
            owed -> {
                queuedWith[0] = owed;
                return true;
            });

        assertEquals(SlotsSettlementResult.QUEUED, result);
        assertEquals(committed - handedOver, queuedWith[0],
            "the durable queue must receive only what was not already delivered");
        assertEquals(committed, controller.lastWinAmount(),
            "the displayed win stays the amount actually won, not the remainder");
    }

    @Test
    void slotsStillTreatsAWhollyFailedDeliveryAsTheFullObligation() {
        SlotsSpinController controller = new SlotsSpinController();
        controller.trySpin(10, 3, 5, false,
            org.nc.nccasino.games.Slots.SlotsPaytable.forConfig(
                3, org.nc.nccasino.games.Slots.SlotsPaytable.DEFAULT_HOUSE_EDGE),
            guaranteedWinRng(), amount -> true);
        long committed = controller.pendingPayoutAmount();

        SlotsSettlementResult result = controller.settle(owed -> owed, owed -> false);

        assertEquals(SlotsSettlementResult.FAILED, result);
        assertEquals(committed, controller.pendingPayoutAmount(),
            "when nothing moved the whole amount is still owed");
    }

    /**
     * Makes the fake inventory accept only {@code acceptUnits} and bounce the
     * rest, regardless of the free space it advertises.
     */
    private void stubInsertToAcceptOnly(OverflowTestSupport.Fixture fixture, long acceptUnits) {
        long[] budget = {acceptUnits};
        when(fixture.inventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack incoming = invocation.getArgument(0);
            int wanted = incoming.getAmount();
            int accepted = (int) Math.min(wanted, Math.max(0L, budget[0]));
            budget[0] -= accepted;

            int toPlace = accepted;
            for (int i = 0; i < fixture.storage.length && toPlace > 0; i++) {
                if (fixture.storage[i] == null) {
                    int moved = Math.min(STACK, toPlace);
                    fixture.storage[i] = new ItemStack(incoming.getType(), moved);
                    toPlace -= moved;
                }
            }

            Map<Integer, ItemStack> leftovers = new HashMap<>();
            int rejected = wanted - accepted;
            if (rejected > 0) {
                leftovers.put(0, new ItemStack(incoming.getType(), rejected));
            }
            return leftovers;
        });
    }
}
