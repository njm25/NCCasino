package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BettingTable implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private double selectedWager;
    private int pageNum;

    private final Map<String, Double> chipValues;
    private final Map<Integer, Double> playerBets; // Map to store the player's bets by slot number

    public BettingTable(Player player, Villager dealer, Nccasino plugin, Map<Integer, Double> existingBets) {
        this.playerId = player.getUniqueId();
        this.dealer = dealer;
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "Your Betting Table");
        this.pageNum = 1;

        this.chipValues = new HashMap<>();
        this.chipValues.put("1 " + plugin.getCurrencyName() + " CHIP", 1.0);
        this.chipValues.put("5 " + plugin.getCurrencyName() + " CHIP", 5.0);
        this.chipValues.put("10 " + plugin.getCurrencyName() + " CHIP", 10.0);
        this.chipValues.put("25 " + plugin.getCurrencyName() + " CHIP", 25.0);
        this.chipValues.put("50 " + plugin.getCurrencyName() + " CHIP", 50.0);

        this.playerBets = new HashMap<>(existingBets); // Initialize with existing bets

        initializeTable();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void initializeTable() {
        setupPageOne();
    }

    private void setupPageOne() {
        inventory.clear();
        clearAllLore(); // Clear all lore before setting up the page
    
        for (int i = 1; i <= 18; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 1, createCustomItem(Material.valueOf(color + "_WOOL"), "Bet on " + i, i));
        }
        inventory.setItem(18, createCustomItem(Material.GREEN_WOOL, "Bet on 0", 1));
        inventory.setItem(45, createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1));
        inventory.setItem(46, createCustomItem(Material.ARROW, "Back to Roulette", 1));
        inventory.setItem(47, createCustomItem(plugin.getCurrency(), "1 " + plugin.getCurrencyName() + " CHIP", 1));
        inventory.setItem(48, createCustomItem(plugin.getCurrency(), "5 " + plugin.getCurrencyName() + " CHIP", 5));
        inventory.setItem(49, createCustomItem(plugin.getCurrency(), "10 " + plugin.getCurrencyName() + " CHIP", 10));
        inventory.setItem(50, createCustomItem(plugin.getCurrency(), "25 " + plugin.getCurrencyName() + " CHIP", 25));
        inventory.setItem(51, createCustomItem(plugin.getCurrency(), "50 " + plugin.getCurrencyName() + " CHIP", 50));
        inventory.setItem(53, createCustomItem(Material.ARROW, "Next Page", 1));
    
        restoreBetsForPage(1); // Restore bets for page 1
    }
    
    private void setupPageTwo() {
        inventory.clear();
        clearAllLore(); // Clear all lore before setting up the page
    
        for (int i = 19; i <= 36; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 19, createCustomItem(Material.valueOf(color + "_WOOL"), "Bet on " + i, i));
        }
        inventory.setItem(45, createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1));
        inventory.setItem(46, createCustomItem(Material.ARROW, "Back to Roulette", 1));
        inventory.setItem(47, createCustomItem(plugin.getCurrency(), "1 " + plugin.getCurrencyName() + " CHIP", 1));
        inventory.setItem(48, createCustomItem(plugin.getCurrency(), "5 " + plugin.getCurrencyName() + " CHIP", 5));
        inventory.setItem(49, createCustomItem(plugin.getCurrency(), "10 " + plugin.getCurrencyName() + " CHIP", 10));
        inventory.setItem(50, createCustomItem(plugin.getCurrency(), "25 " + plugin.getCurrencyName() + " CHIP", 25));
        inventory.setItem(51, createCustomItem(plugin.getCurrency(), "50 " + plugin.getCurrencyName() + " CHIP", 50));
        inventory.setItem(52, createCustomItem(Material.ARROW, "Previous Page", 1));
    
        restoreBetsForPage(2); // Restore bets for page 2
    }

    private void clearAllLore() {
        // Iterate through each slot in the inventory and clear lore
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    meta.setLore(new ArrayList<>()); // Clear the lore
                    item.setItemMeta(meta);
                }
            }
        }
    }

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

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return;
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();

        if (pageNum == 1 && slot == 53) {
            setupPageTwo();
            pageNum = 2;
            return;
        } else if (pageNum == 2 && slot == 52) {
            setupPageOne();
            pageNum = 1;
            return;
        }

        if (slot >= 47 && slot <= 51) {
            selectedWager = getWagerAmountFromName(itemName);
            if (selectedWager > 0) {
                player.sendMessage("Selected wager: " + selectedWager + " " + plugin.getCurrencyName() + "s");
            } else {
                player.sendMessage("Invalid wager amount selected.");
            }
            return;
        }

       
    if ((pageNum == 1 && slot >= 0 && slot <= 18) || (pageNum == 2 && slot >= 0 && slot <= 17)) {
        int actualNum = (pageNum == 1) ? slot  : slot + 18; // Adjust for actual bet slots
        int actualSlot=(pageNum == 1) ? actualNum  : actualNum-19;
        if (selectedWager > 0) {
            if (hasEnoughWager(player, selectedWager)) {
                removeWagerFromInventory(player, selectedWager);
                player.sendMessage("Placed bet on " + itemName + " with " + selectedWager + " " + plugin.getCurrencyName() + "s.");

                // Store the bet
                playerBets.put(actualNum, selectedWager);

                // Update the lore of the item
                updateItemLore(slot, selectedWager);
            } else {
                player.sendMessage("Not enough " + plugin.getCurrencyName() + "s to place this bet.");
            }
        } else {
            player.sendMessage("Please select a wager amount first.");
        }
        return;
    }

        if (slot == 45) {
            player.closeInventory();
            player.sendMessage("You have left the game. Bets are closed.");
            cancelAllBets();
            DealerVillager.removePlayerBettingTable(dealer, player);
        }

        if (slot == 46) {
            saveBetsToRoulette();
            player.sendMessage("Returning to Roulette...");
            openRouletteInventory(dealer, player);
        
        }
    }

    private void restoreBetsForPage(int page) {
        // Iterate over the stored bets and re-apply them to the inventory
        playerBets.forEach((slot, bet) -> {
            if ((page == 1 && slot >= 0 && slot <= 18) || (page == 2 && slot >= 18 && slot <= 36)) {
                updateItemLore(slot - (page == 1 ? 0 : 18), bet);
            }
        });
    }
    
    // Handle closing the betting table to clear bets
    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
        cancelAllBets();
        player.sendMessage("Exiting Betting Table. All bets cleared.");
    }

    private void updateItemLore(int slot, double wager) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("Current Bet: " + wager + " " + plugin.getCurrencyName());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    private void openRouletteInventory(Villager dealer, Player player) {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);

        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.updatePlayerBets(playerId, playerBets); // Update the player's bets in the roulette inventory
            rouletteInventory.refresh(player);
        }
    }

    private double getWagerAmountFromName(String name) {
        return chipValues.getOrDefault(name, 0.0);
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(plugin.getCurrency()), requiredAmount);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        if (requiredAmount > 0) {
            player.getInventory().removeItem(new ItemStack(plugin.getCurrency(), requiredAmount));
        } else {
            player.sendMessage("Invalid wager amount: " + amount);
        }
    }

    private void cancelAllBets() {
        // Logic to cancel all bets and return wagers to players
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.clearPlayerBets(playerId); // Remove bets from RouletteInventory
        }
        playerBets.clear();
        // Also, update the inventory to remove lore information
        restoreBets(); // This will remove lore as bets are now cleared
    }

    private void restoreBets() {
        // Iterate over the stored bets and re-apply them to the inventory
        playerBets.forEach(this::updateItemLore);
    }

    public Map<Integer, Double> getPlayerBets() {
        return playerBets;
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

    private void saveBetsToRoulette() {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
    
        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.updatePlayerBets(playerId, playerBets); // Save the player's bets to the roulette inventory
        }
    }
}
