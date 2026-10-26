package org.nc.nccasino;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nc.nccasino.listeners.PlayerSessionListener;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.payout.PendingPayoutStore;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A dealer deleted or rebuilt mid-round settles its players into saved
 * (pending) payouts. Those used to wait for the player's next login; now each
 * online player who gained one is paid a moment later, and nobody else is
 * touched, so a dealer chunk reloading never re-runs delivery for everyone.
 */
class NewlyOwedDeliveryTest {

    private MockedStatic<Bukkit> bukkit;
    private Nccasino plugin;
    private PendingPayoutStore store;
    private PlayerSessionListener listener;
    private BukkitScheduler scheduler;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = player();
        bob = player();
        scheduler = mock(BukkitScheduler.class);
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of(alice, bob));
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);
        bukkit.when(() -> Bukkit.getPlayer(bob.getUniqueId())).thenReturn(bob);

        plugin = mock(Nccasino.class);
        when(plugin.isEnabled()).thenReturn(true);
        doCallRealMethod().when(plugin).owedPayoutCountsOfOnlinePlayers();
        doCallRealMethod().when(plugin).deliverNewlyOwedSoon(any());
        doCallRealMethod().when(plugin).deliverOwedWinningsSoon(any());
        store = mock(PendingPayoutStore.class);
        listener = mock(PlayerSessionListener.class);
        set("pendingPayoutStore", store);
        set("playerSessionListener", listener);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void onlyThePlayerWhoGainedAPayoutIsPaidAMomentLater() {
        PendingPayout owed = mock(PendingPayout.class);
        when(store.getPending(alice.getUniqueId())).thenReturn(List.of(), List.of(owed));
        when(store.getPending(bob.getUniqueId())).thenReturn(List.of(owed));

        Map<UUID, Integer> before = plugin.owedPayoutCountsOfOnlinePlayers();
        // ...the dealer is torn down here: Alice's stake is saved as owed...
        plugin.deliverNewlyOwedSoon(before);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(Plugin.class), task.capture(), anyLong());
        task.getValue().run();
        verify(listener, times(1)).deliverOwedWinnings(alice);
        verify(listener, never()).deliverOwedWinnings(bob);
    }

    @Test
    void nothingIsScheduledWhenNobodyGainedAPayout() {
        when(store.getPending(any())).thenReturn(List.of());

        plugin.deliverNewlyOwedSoon(plugin.owedPayoutCountsOfOnlinePlayers());

        verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
    }

    @Test
    void aPlayerWhoLeftBeforeTheDeliveryIsPaidAtTheirNextLoginInstead() {
        PendingPayout owed = mock(PendingPayout.class);
        when(store.getPending(alice.getUniqueId())).thenReturn(List.of(), List.of(owed));
        when(store.getPending(bob.getUniqueId())).thenReturn(List.of());

        plugin.deliverNewlyOwedSoon(plugin.owedPayoutCountsOfOnlinePlayers());
        when(alice.isOnline()).thenReturn(false);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(Plugin.class), task.capture(), anyLong());
        task.getValue().run();
        verify(listener, never()).deliverOwedWinnings(any());
    }

    @Test
    void tearingDownADealerMidRoundPaysItsOnlinePlayerAMomentLater() {
        // The path /ncc delete, an admin rebuild and a dying dealer all share.
        doCallRealMethod().when(plugin).deleteAssociatedInventories(any(UUID.class), any());
        PendingPayout owed = mock(PendingPayout.class);
        java.util.List<PendingPayout> alicesRecords = new java.util.ArrayList<>();
        when(store.getPending(alice.getUniqueId())).thenAnswer(call -> List.copyOf(alicesRecords));
        when(store.getPending(bob.getUniqueId())).thenReturn(List.of());
        UUID dealerId = UUID.randomUUID();
        org.nc.nccasino.entities.DealerInventory table = mock(org.nc.nccasino.entities.DealerInventory.class);
        // Tearing the table down settles Alice's riding stake into a saved payout.
        Mockito.doAnswer(call -> alicesRecords.add(owed)).when(table).delete();
        org.nc.nccasino.entities.DealerInventory.inventories.put(dealerId, table);
        try {
            plugin.deleteAssociatedInventories(dealerId, "table");
        } finally {
            org.nc.nccasino.entities.DealerInventory.inventories.remove(dealerId);
        }

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(Plugin.class), task.capture(), anyLong());
        task.getValue().run();
        verify(listener, times(1)).deliverOwedWinnings(alice);
        verify(listener, never()).deliverOwedWinnings(bob);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private void set(String name, Object value) throws Exception {
        Field field = Nccasino.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
