package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.entities.DealerVillager;

import java.util.UUID;

public class BettingTable implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final Villager dealer;  // Change to Villager object

    private double selectedWager;
    private int pageNum;

    // Constructor to create a betting table for a player and a dealer
    public BettingTable(Player player, Villager dealer) {
        this.playerId = player.getUniqueId();
        this.dealer = dealer;
        this.inventory = Bukkit.createInventory(this, 54, "Your Betting Table");
        this.pageNum = 1;
        initializeTable();

        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("nccasino"));
    }

    // Method to initialize the betting table items
    private void initializeTable() {
        setupPageOne();
    }

    // Set up items for the first page
    private void setupPageOne() {
        inventory.clear(); // Clear inventory before setting up page
        for (int i = 1; i <= 18; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 1, createCustomItem(Material.valueOf(color + "_WOOL"), "Bet on " + i, i));
        }
        inventory.setItem(27, createCustomItem(Material.GREEN_WOOL, "Bet on 0", 1)); // Move green up one row to the fourth row
        inventory.setItem(45, createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1)); // Bottom left
        inventory.setItem(46, createCustomItem(Material.ARROW, "Back to Roulette", 1)); // New back button
        inventory.setItem(47, createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP", 1));
        inventory.setItem(48, createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP", 5));
        inventory.setItem(49, createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP", 10));
        inventory.setItem(50, createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP", 25));
        inventory.setItem(51, createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP", 50));
        inventory.setItem(53, createCustomItem(Material.ARROW, "Next Page", 1));
    }

    // Set up items for the second page
    private void setupPageTwo() {
        inventory.clear(); // Clear inventory before setting up page
        for (int i = 19; i <= 36; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 19, createCustomItem(Material.valueOf(color + "_WOOL"), "Bet on " + i, i));
        }
        inventory.setItem(45, createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1)); // Bottom left
        inventory.setItem(46, createCustomItem(Material.ARROW, "Back to Roulette", 1)); // New back button
        inventory.setItem(47, createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP", 1));
        inventory.setItem(48, createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP", 5));
        inventory.setItem(49, createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP", 10));
        inventory.setItem(50, createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP", 25));
        inventory.setItem(51, createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP", 50));
        inventory.setItem(52, createCustomItem(Material.ARROW, "Previous Page", 1));
    }

    // Create an item stack with a custom display name and stack size
    private ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }

        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    // Handle click events in the betting table
    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; // Ensure this is the correct inventory

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true); // Cancel the event to prevent item movement

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return; // Exit if the clicked item is null or has no meta
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();

        // Handle page navigation
        if (pageNum == 1 && slot == 53) {
            setupPageTwo();
            pageNum = 2;
            return;
        } else if (pageNum == 2 && slot == 52) {
            setupPageOne();
            pageNum = 1;
            return;
        }

        // Handle wager selection
        if (slot >= 47 && slot <= 51) {
            selectedWager = getWagerAmountFromName(itemName);
            if (selectedWager > 0) {
                player.sendMessage("Selected wager: " + selectedWager + " Diamonds");
            } else {
                player.sendMessage("Invalid wager amount selected.");
            }
            return;
        }

        // Handle bet placement
        if (slot >= 0 && slot <= 36) {
            if (selectedWager > 0) {
                if (hasEnoughWager(player, selectedWager)) {
                    removeWagerFromInventory(player, selectedWager);
                    player.sendMessage("Placed bet on " + itemName + " with " + selectedWager + " Diamonds.");
                    // Implement logic to store the bet here
                } else {
                    player.sendMessage("Not enough Diamonds to place this bet.");
                }
            } else {
                player.sendMessage("Please select a wager amount first.");
            }
            return;
        }

        // Handle leaving the game
        if (slot == 45) { // Updated slot for Leave Game
            player.closeInventory();
            player.sendMessage("You have left the game. Bets are closed.");

            // Remove player from the dealer's betting table
            DealerVillager.removePlayerBettingTable(dealer, player);
        }

        // Handle going back to the dealer's roulette inventory
        if (slot == 46) { // Slot for "Back to Roulette"
            openRouletteInventory(dealer, player);
        }
    }

    // New method to open the dealer's roulette inventory
    private void openRouletteInventory(Villager dealer, Player player) {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);

        // Check if the inventory is the specific type you're interested in
        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.refresh(player); 
        }
    }

    private double getWagerAmountFromName(String name) {
        switch (name) {
            case "1 DIAMOND CHIP":
                return 1;
            case "5 DIAMOND CHIP":
                return 5;
            case "10 DIAMOND CHIP":
                return 10;
            case "25 DIAMOND CHIP":
                return 25;
            case "50 DIAMOND CHIP":
                return 50;
            default:
                return 0;
        }
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredDiamonds = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), requiredDiamonds);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredDiamonds = (int) Math.ceil(amount);
        if (requiredDiamonds > 0) {
            player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));
        } else {
            player.sendMessage("Invalid wager amount: " + amount);
        }
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Villager getDealer() {
        return dealer;
    }
}
