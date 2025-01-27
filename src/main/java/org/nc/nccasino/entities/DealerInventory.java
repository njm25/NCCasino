package org.nc.nccasino.entities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DealerInventory implements InventoryHolder, Listener {

    // Static map of all DealerInventories by dealer ID
    public static final Map<UUID, DealerInventory> inventories = new HashMap<>();

    protected Inventory inventory;
    protected final UUID dealerId;

    // Protected constructor to allow subclassing
    protected DealerInventory(UUID dealerId, int size, String title) {
        this.dealerId = dealerId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /**
     * Completely remove *all* references and clear them from memory.
     * Called from onDisable() in the plugin or wherever you need a full cleanup.
     */
    public static void cleanupAll() {
        // Safely delete each inventory
        for (DealerInventory di : inventories.values()) {
            di.delete();  // each .delete() handles removing references
        }
        inventories.clear();
    }

    /**
     * Remove a single DealerInventory from the static map by ID.
     */
    public static void removeInventory(UUID dealerId) {
        inventories.remove(dealerId);
    }

    /**
     * Factory method to create or get existing inventory (defaults to game menu).
     */
    public static DealerInventory getOrCreateInventory(UUID dealerId) {
        return inventories.computeIfAbsent(dealerId, id -> new DealerInventory(id, 54, "Default Inventory"));
    }

    /**
     * Delete this inventory: remove from static map, clear items, drop references.
     */
    public void delete() {
        // Remove from the static map
        inventories.remove(dealerId);

        // Clear out the inventory
        if (this.inventory != null) {
            this.inventory.clear();
            this.inventory = null;
        }
    }

    /**
     * Get a DealerInventory by UUID if it exists.
     */
    public static DealerInventory getInventory(UUID dealerId) {
        return inventories.get(dealerId);
    }

    /**
     * Update (or set) the inventory for a given Dealer.
     */
    public static void updateInventory(UUID dealerId, DealerInventory newInventory) {
        // If an old inventory exists for the same ID, remove it
        DealerInventory existing = inventories.get(dealerId);
        if (existing != null) {
            existing.delete();
        }
        inventories.put(dealerId, newInventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    // Method to handle item click, can be overridden by subclasses
    public void handleClick(int slot, Player player) {
        // Default implementation does nothing
    }

    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        // Default implementation calls the original method if needed
        // handleClick(slot, player);
    }

    public static void unregisterAllListeners(Villager villager) {
        HandlerList.unregisterAll(inventories.get(villager.getUniqueId()));
    }

    // Add item with a custom name to the inventory
    protected void addItem(ItemStack itemStack, int slot) {
        if (inventory != null) {
            inventory.setItem(slot, itemStack);
        }
    }

    // Update a player's inventory (specific slot)
    public void setPlayerInventoryItem(Inventory playerInventory, Material material, int slot) {
        ItemStack itemStack = new ItemStack(material, 1);
        playerInventory.setItem(slot, itemStack);
    }

    // Create an item stack with a custom display name and amount
    public ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }
        ItemStack itemStack = new ItemStack(material, amount);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    private void setCustomItemMeta(ItemStack itemStack, String name) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>()); // Clear any existing lore
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); // Hide any relevant flags
            itemStack.setItemMeta(meta);
        }
    }

    // Update all players' inventories (specific slot)
    public void setAllPlayerInventoryItem(List<Inventory> playerInventories, Material material, int slot) {
        ItemStack itemStack = new ItemStack(material, 1);
        for (Inventory playerInventory : playerInventories) {
            playerInventory.setItem(slot, itemStack);
        }
    }

    // Update item lore for a specific player
    public void setPlayerItemLore(Inventory playerInventory, int slot, List<String> lore) {
        ItemStack item = playerInventory.getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    // Update item lore for all players
    public void setAllPlayerItemLore(List<Inventory> playerInventories, int slot, List<String> lore) {
        for (Inventory playerInventory : playerInventories) {
            ItemStack item = playerInventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    // Helper to create a custom item
    public ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>()); // Empty lore
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    // Example: Add an item to a player's inventory with custom lore
    public void addPlayerInventoryItemWithLore(Inventory playerInventory, Material material, String name, List<String> lore, int slot) {
        ItemStack itemStack = createCustomItem(material, name);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        playerInventory.setItem(slot, itemStack);
    }

    // Add the same item to all players' inventories with custom lore
    public void addAllPlayerInventoryItemWithLore(List<Inventory> playerInventories, Material material, String name, List<String> lore, int slot) {
        for (Inventory playerInventory : playerInventories) {
            addPlayerInventoryItemWithLore(playerInventory, material, name, lore, slot);
        }
    }
}
