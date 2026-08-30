package org.nc.nccasino.payout;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A small fake player/inventory good enough to exercise real delivery
 * behavior rather than just verifying that a mock was called.
 *
 * <p>{@link #addItem} implements Bukkit's actual contract closely enough to
 * matter here: it tops up existing partial stacks of the same material
 * first, then fills empty slots, and returns whatever would not fit. That
 * makes "partial delivery" a genuine outcome of the fake inventory's state
 * instead of something the test stubs into existence.
 */
final class OverflowTestSupport {

    private OverflowTestSupport() {
    }

    static final int STORAGE_SLOTS = 36;

    /**
     * Unit tests run without the item registry that backs
     * {@code Material#getMaxStackSize()}, so the fake inventory uses the
     * vanilla default directly -- the same value the service falls back to.
     */
    static final int STACK_SIZE = 64;

    /** Tracks everything dropped so a test can assert the drop cap exactly. */
    static final class FakeWorld {
        final List<ItemStack> dropped = new ArrayList<>();

        long droppedUnits() {
            long total = 0;
            for (ItemStack stack : dropped) {
                total += stack.getAmount();
            }
            return total;
        }
    }

    static final class Fixture {
        final Player player;
        final PlayerInventory inventory;
        final ItemStack[] storage;
        final FakeWorld world;
        final UUID playerId;

        Fixture(Player player, PlayerInventory inventory, ItemStack[] storage, FakeWorld world, UUID playerId) {
            this.player = player;
            this.inventory = inventory;
            this.storage = storage;
            this.world = world;
            this.playerId = playerId;
        }

        /** Total units of {@code material} actually sitting in the fake inventory. */
        long unitsHeld(Material material) {
            long total = 0;
            for (ItemStack stack : storage) {
                if (stack != null && stack.getType() == material) {
                    total += stack.getAmount();
                }
            }
            return total;
        }

        /** Fills every storage slot with an unrelated material, leaving no room at all. */
        void fillCompletely() {
            for (int i = 0; i < storage.length; i++) {
                storage[i] = new ItemStack(Material.COBBLESTONE, STACK_SIZE);
            }
        }

        /** Leaves exactly {@code freeSlots} empty slots; the rest are unrelated full stacks. */
        void leaveFreeSlots(int freeSlots) {
            for (int i = 0; i < storage.length; i++) {
                storage[i] = i < storage.length - freeSlots
                    ? new ItemStack(Material.COBBLESTONE, STACK_SIZE)
                    : null;
            }
        }
    }

    static Fixture fixture() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        FakeWorld fakeWorld = new FakeWorld();
        ItemStack[] storage = new ItemStack[STORAGE_SLOTS];

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);

        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.addItem(any(ItemStack.class)))
            .thenAnswer(invocation -> addItem(storage, invocation.getArgument(0)));

        when(world.dropItemNaturally(any(Location.class), any(ItemStack.class)))
            .thenAnswer(invocation -> {
                fakeWorld.dropped.add(invocation.getArgument(1));
                return null;
            });

        return new Fixture(player, inventory, storage, fakeWorld, playerId);
    }

    /** Mirrors Bukkit's fill-partials-then-empty-slots behavior. */
    private static Map<Integer, ItemStack> addItem(ItemStack[] storage, ItemStack incoming) {
        Material material = incoming.getType();
        int stackSize = STACK_SIZE;
        int remaining = incoming.getAmount();

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack slot = storage[i];
            if (slot == null || slot.getType() != material) {
                continue;
            }
            int room = stackSize - slot.getAmount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            slot.setAmount(slot.getAmount() + moved);
            remaining -= moved;
        }

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            if (storage[i] != null) {
                continue;
            }
            int moved = Math.min(stackSize, remaining);
            storage[i] = new ItemStack(material, moved);
            remaining -= moved;
        }

        Map<Integer, ItemStack> leftovers = new HashMap<>();
        if (remaining > 0) {
            leftovers.put(0, new ItemStack(material, remaining));
        }
        return leftovers;
    }
}
