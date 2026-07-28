package org.nc.nccasino.payout;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.economy.VaultHook;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingPayoutStoreTest {

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private VaultHook vaultHook;
    private Economy economy;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        vaultHook = mock(VaultHook.class);
        economy = mock(Economy.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PendingPayoutStoreTest"));
        when(plugin.getVaultHook()).thenReturn(vaultHook);
        when(vaultHook.isEconomyAvailable()).thenReturn(true);
        when(vaultHook.getEconomy()).thenReturn(economy);
    }

    @Test
    void addedPayoutSurvivesStoreReloadWithFullCurrencySnapshot() {
        UUID playerId = UUID.randomUUID();
        PendingPayout payout = PendingPayout.create(
            playerId, "Roulette", "roulette-a", CurrencyMode.CUSTOM,
            "EMERALD", "Casino Token", 37, "won on red");
        PendingPayoutStore first = new PendingPayoutStore(plugin);

        assertTrue(first.addPendingPayout(payout));
        PendingPayoutStore reloaded = new PendingPayoutStore(plugin);

        assertEquals(java.util.List.of(payout), reloaded.getPending(playerId));
        assertTrue(new File(tempDir.toFile(), "data/pending-payouts.yml").isFile());
    }

    @Test
    void zeroValueOutcomeIsDeliveredAndRemovedWithoutTouchingEconomy() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PendingPayout payout = payout(playerId, UUID.randomUUID(), 0);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult result = store.attemptDeliver(player);

        assertEquals(java.util.List.of(payout), result.delivered());
        assertTrue(result.stillPending().isEmpty());
        assertFalse(store.hasPending(playerId));
        verify(economy, never()).depositPlayer(any(Player.class), any(Double.class));
    }

    @Test
    void successfulVaultPayoutIsDepositedOnceAndDurablyRemoved() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PendingPayout payout = payout(playerId, UUID.randomUUID(), 50);
        when(economy.depositPlayer(player, 50.0)).thenReturn(success(50));
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult first = store.attemptDeliver(player);
        DeliveryResult second = store.attemptDeliver(player);

        assertEquals(java.util.List.of(payout), first.delivered());
        assertTrue(second.isEmpty());
        verify(economy, times(1)).depositPlayer(player, 50.0);
        assertFalse(new PendingPayoutStore(plugin).hasPending(playerId));
    }

    @Test
    void failedVaultDepositStaysPendingAndCanSucceedOnRetry() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PendingPayout payout = payout(playerId, UUID.randomUUID(), 25);
        when(economy.depositPlayer(player, 25.0))
            .thenReturn(failure(25))
            .thenReturn(success(25));
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult failed = store.attemptDeliver(player);
        assertEquals(java.util.List.of(payout), failed.stillPending());
        assertTrue(store.hasPending(playerId));
        assertTrue(new PendingPayoutStore(plugin).hasPending(playerId));

        DeliveryResult retried = store.attemptDeliver(player);
        assertEquals(java.util.List.of(payout), retried.delivered());
        assertFalse(store.hasPending(playerId));
        verify(economy, times(2)).depositPlayer(player, 25.0);
    }

    @Test
    void addingSameRecordTwiceCannotProduceDuplicatePayment() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PendingPayout payout = payout(playerId, UUID.randomUUID(), 10);
        when(economy.depositPlayer(player, 10.0)).thenReturn(success(10));
        PendingPayoutStore store = new PendingPayoutStore(plugin);

        assertTrue(store.addPendingPayout(payout));
        assertTrue(store.addPendingPayout(payout));
        assertEquals(1, store.getPending(playerId).size());
        store.attemptDeliver(player);

        verify(economy, times(1)).depositPlayer(player, 10.0);
    }

    @Test
    void collidingIdWithDifferentContentsIsRejectedWithoutReplacingOriginal() {
        UUID playerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        PendingPayout original = payout(playerId, payoutId, 10);
        PendingPayout collision = payout(playerId, payoutId, 999);
        PendingPayoutStore store = new PendingPayoutStore(plugin);

        assertTrue(store.addPendingPayout(original));
        assertFalse(store.addPendingPayout(collision));

        assertEquals(java.util.List.of(original), store.getPending(playerId));
    }

    @Test
    void payoutForDifferentPlayerIsNotDeliveredOnThisJoin() {
        UUID owedPlayer = UUID.randomUUID();
        PendingPayout payout = payout(owedPlayer, UUID.randomUUID(), 15);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult result = store.attemptDeliver(player(UUID.randomUUID()));

        assertTrue(result.isEmpty());
        assertTrue(store.hasPending(owedPlayer));
        verify(economy, never()).depositPlayer(any(Player.class), eq(15.0));
    }

    @Test
    void standardItemPayoutAddsSnapshottedMaterialToInventory() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        PendingPayout payout = itemPayout(playerId, "EMERALD", 32);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult result = store.attemptDeliver(player);

        assertEquals(java.util.List.of(payout), result.delivered());
        verify(inventory).addItem(argThat((ItemStack item) ->
            item.getType() == Material.EMERALD && item.getAmount() == 32));
        assertFalse(store.hasPending(playerId));
    }

    @Test
    void itemOverflowDropsRemainderAtPlayerLocationInsteadOfLosingIt() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, new ItemStack(Material.EMERALD, 7));
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(leftover);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(itemPayout(playerId, "EMERALD", 32)));

        store.attemptDeliver(player);

        verify(world).dropItemNaturally(eq(location), argThat(item ->
            item.getType() == Material.EMERALD && item.getAmount() == 7));
    }

    @Test
    void invalidSnapshottedMaterialLeavesPayoutPending() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PendingPayout payout = itemPayout(playerId, "NOT_A_REAL_MATERIAL", 5);
        PendingPayoutStore store = new PendingPayoutStore(plugin);
        assertTrue(store.addPendingPayout(payout));

        DeliveryResult result = store.attemptDeliver(player);

        assertEquals(java.util.List.of(payout), result.stillPending());
        assertTrue(store.hasPending(playerId));
    }

    @Test
    void failedDiskWriteRollsBackInMemoryAddition() throws Exception {
        File blockedDataFolder = Files.createFile(tempDir.resolve("not-a-directory")).toFile();
        when(plugin.getDataFolder()).thenReturn(blockedDataFolder);
        UUID playerId = UUID.randomUUID();
        PendingPayoutStore store = new PendingPayoutStore(plugin);

        assertFalse(store.addPendingPayout(payout(playerId, UUID.randomUUID(), 10)));
        assertFalse(store.hasPending(playerId));
    }

    private Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private PendingPayout payout(UUID playerId, UUID payoutId, double amount) {
        return new PendingPayout(
            payoutId, playerId, "Roulette", "roulette-a", CurrencyMode.VAULT,
            null, "Dollar", amount, 123456789L, "test outcome");
    }

    private PendingPayout itemPayout(UUID playerId, String material, double amount) {
        return new PendingPayout(
            UUID.randomUUID(), playerId, "Mines", "mines-a", CurrencyMode.STANDARD,
            material, "Emerald", amount, 123456789L, "test item payout");
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(amount, amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(double amount) {
        return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "test failure");
    }
}
