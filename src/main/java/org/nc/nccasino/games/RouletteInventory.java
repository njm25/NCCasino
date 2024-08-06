package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.entities.DealerVillager;

import java.util.UUID;

public class RouletteInventory extends DealerInventory {
    private int pageNum; // To track which page is currently active

    public RouletteInventory(UUID dealerId) {
        super(dealerId, 54, "Roulette Start Menu"); // Initialize with 54 slots
        this.pageNum = 1;
        initializeStartMenu();
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear(); // Clear the inventory before setting up the page
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette", 1), 22); // Central button
    }

    // Set up items for the game menu
    private void setupGameMenu() {
        inventory.clear(); // Clear the inventory before setting up the page
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 20); // Betting Table button
        addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1), 24); // Leave Game button
    }

    // Create an item stack with a custom display name and stack size
    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    // Refresh the inventory for the player
    @Override
    public void refresh(Player player) {
        // Use a delayed task to avoid immediate recursion
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("nccasino"), () -> {
            player.closeInventory();
            player.openInventory(this.inventory);
        }, 1L);
    }

    // Handle click events in the roulette inventory
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Cancel the event to prevent item movement

        if (pageNum == 1) { // Start menu logic
            if (slot == 22) { // "Start Roulette" button clicked
                player.sendMessage("Starting Roulette...");
                setupGameMenu(); // Switch to game menu
                pageNum = 2; // Set page number to game menu
                refresh(player); // Refresh the player's inventory
            }
        } else if (pageNum == 2) { // Game menu logic
            handleGameMenuClick(slot, player);
        }
    }

    // Handle game menu interactions
    private void handleGameMenuClick(int slot, Player player) {
        if (slot == 20) { // "Open Betting Table" button clicked
            // Use a delayed task to open the betting table to avoid recursion
            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("nccasino"), () -> {
                // Find the dealer associated with the player
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(DealerVillager::isDealerVillager)
                        .findFirst().orElse(null);

                if (dealer != null) {
                    // Ensure the betting table is associated with the correct dealer
                    BettingTable bettingTable = DealerVillager.getOrCreateBettingTable(dealer, player);
                    player.openInventory(bettingTable.getInventory());
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open betting table.");
                }
            }, 1L);
        } else if (slot == 24) { // "Leave Game" button clicked
            player.closeInventory();
            player.sendMessage("You have left the game.");
        }
    }
}
