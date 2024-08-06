package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DealerInventory implements InventoryHolder {

    private static final Map<UUID, DealerInventory> inventories = new HashMap<>();
    protected final Inventory inventory;
    protected final UUID dealerId;

    // Protected constructor to allow subclassing
    protected DealerInventory(UUID dealerId, int size, String title) {
        this.dealerId = dealerId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    // Factory method to create or get existing inventory (defaults to game menu)
    public static DealerInventory getOrCreateInventory(UUID dealerId) {
        return inventories.computeIfAbsent(dealerId, GameMenuInventory::new);
    }

    // Method to get a dealer inventory if it exists
    public static DealerInventory getInventory(UUID dealerId) {
        return inventories.get(dealerId);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    // Method to handle item click, can be overridden by subclasses
    public void handleClick(int slot, Player player) {
        // Default implementation does nothing
    }

    // Add item with a custom name to the inventory
    protected void addItem(ItemStack itemStack, int slot) {
        inventory.setItem(slot, itemStack);
    }
}
