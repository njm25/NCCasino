package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DealerInventory implements InventoryHolder {

    private static final Map<UUID, DealerInventory> inventories = new HashMap<>();
    private final Inventory inventory;
    private final UUID dealerId;

    private DealerInventory(UUID dealerId) {
        this.dealerId = dealerId;
        this.inventory = Bukkit.createInventory(this, 54, "Dealer Inventory");

    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    // Method to get or create a persistent inventory for a dealer
    public static DealerInventory getOrCreateInventory(UUID dealerId) {
        return inventories.computeIfAbsent(dealerId, DealerInventory::new);
    }

    // Method to get a dealer inventory if it exists
    public static DealerInventory getInventory(UUID dealerId) {
        return inventories.get(dealerId);
    }
}
