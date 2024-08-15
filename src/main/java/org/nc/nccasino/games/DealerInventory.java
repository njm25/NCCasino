package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DealerInventory implements InventoryHolder {

    protected static final Map<UUID, DealerInventory> inventories = new HashMap<>();
    protected Inventory inventory;
    protected final UUID dealerId;

    // Protected constructor to allow subclassing
    protected DealerInventory(UUID dealerId, int size, String title) {
        this.dealerId = dealerId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }
    public static void removeInventory(UUID dealerId) {
        inventories.remove(dealerId);
    }
    // Factory method to create or get existing inventory (defaults to game menu)
    public static DealerInventory getOrCreateInventory(UUID dealerId) {
        return inventories.computeIfAbsent(dealerId, id -> new GameMenuInventory(id));
    }
    public void delete() {
        inventories.remove(dealerId);

        this.inventory.clear();

    }
    // Method to get a dealer inventory if it exists
    public static DealerInventory getInventory(UUID dealerId) {
        return inventories.get(dealerId);
    }

    // Method to update the inventory map, allowing other classes to replace entries
    public static void updateInventory(UUID dealerId, DealerInventory newInventory) {
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
        // Default implementation calls the original method
        //handleClick(slot, player);
    }

    // Add item with a custom name to the inventory
    protected void addItem(ItemStack itemStack, int slot) {
        inventory.setItem(slot, itemStack);
    }
        // Update a player's inventory (specific slot)
    public void setPlayerInventoryItem(Inventory playerInventory, Material material, int slot) {
        ItemStack itemStack = new ItemStack(material, 1);
        playerInventory.setItem(slot, itemStack);
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

    // Custom item creation helper (used by subclasses)
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
