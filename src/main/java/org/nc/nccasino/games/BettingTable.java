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

import java.security.cert.PolicyQualifierInfo;
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
    private boolean clickAllowed = true; 

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
       /*  System.out.println("ppg1");
        playerBets.forEach((number, amount) -> {
            System.out.println("Number: " + number + ", Amount: " + amount);
        });*/
        inventory.clear();
        clearAllLore(); // Clear all lore before setting up the page
    
        for (int i = 1; i <= 18; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 1, createCustomItem(Material.valueOf(color + "_WOOL"), "straight up " + i, i));
        }
        inventory.setItem(18, createCustomItem(Material.GREEN_WOOL, "straight up 0", 1));
        inventory.setItem(45, createCustomItem(Material.TOTEM_OF_UNDYING, "Undo All Bets", 1));
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
        /* 
        System.out.println("pg2");
        playerBets.forEach((number, amount) -> {
            System.out.println("Number: " + number + ", Amount: " + amount);
        });*/
        inventory.clear();
        clearAllLore(); // Clear all lore before setting up the page
    
        for (int i = 19; i <= 36; i++) {
            String color = (i % 2 == 0) ? "BLACK" : "RED";
            inventory.setItem(i - 19, createCustomItem(Material.valueOf(color + "_WOOL"), "straight up " + i, i));
        }

        inventory.setItem(45, createCustomItem(Material.TOTEM_OF_UNDYING, "Undo All Bets", 1));
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

   if (!clickAllowed) {
            player.sendMessage("Please wait before clicking again!");
           
            return;
        
        
        }
clickAllowed=false;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clickAllowed =true; // Allow clicks again after 5 ticks (250ms)
        }, 5L);

      

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
        int actualSlot=(pageNum == 1) ? actualNum  : slot;
        if (selectedWager > 0) {
            if (hasEnoughWager(player, selectedWager)) {
                removeWagerFromInventory(player, selectedWager);
                player.sendMessage("Placed bet on " + itemName + " with " + selectedWager + " " + plugin.getCurrencyName() + "s.");

                // Store the bet
                if (pageNum==1 && slot==18){
                   
                        if (playerBets.containsKey(-1)) {
                            //System.out.println("0 bet key found");
                            double currentBet = playerBets.get(-1);
                            double newBet = currentBet + selectedWager;
                            playerBets.put(-1, newBet);
                            updateItemLore(slot, newBet);}
                    else{
                        //System.out.println("0 bet no key found");
                        playerBets.put(-1, selectedWager);
                        updateItemLore(slot, selectedWager);
                    }
                }
                else {

                    if(playerBets.containsKey(actualNum)){
                       // System.out.println("non0 bet key found");
                    playerBets.replace(actualNum, playerBets.get(actualNum)+selectedWager);
                    updateItemLore(actualSlot,playerBets.get(actualNum));}
                    else{
                        //System.out.println("non0 bet no key found");
                    playerBets.put(actualNum, selectedWager);
                    updateItemLore(actualSlot, selectedWager);
                    }
                    
            
                }
                // Update the lore of the item
               
            } else {
                player.sendMessage("Not enough " + plugin.getCurrencyName() + "s to place this bet.");
            }
        } else {
            player.sendMessage("Please select a wager amount first.");
        }
        return;
    }
    if (slot == 45) {
        player.sendMessage("Undoing all bets...");
        clearAllBetsAndRefund(player);
        clearAllLore();
        player.sendMessage("All bets cleared and refunded.");
        return;

    
    }

/* 
        if (slot == 45) {
            player.closeInventory();
            player.sendMessage("You have left the game. Bets are closed.");
            //cancelAllBets();
            DealerVillager.removePlayerBettingTable(dealer, player);
        }*/

        if (slot == 46) {

            saveBetsToRoulette();
            player.sendMessage("Returning to Roulette...");
            openRouletteInventory(dealer, player);
            DealerVillager.savePlayerBets(dealer, player, playerBets);
        }
        
    
    }



    private void clearAllBetsAndRefund(Player player) {
        // Calculate the total refund amount
        double totalAmount = playerBets.values().stream().mapToDouble(Double::doubleValue).sum();
    
        // Round up to the nearest whole number for refund
        int totalRefund = (int) Math.ceil(totalAmount);
    
        // Log the total refund amount for debugging
        System.out.println("Total Refund Amount: " + totalRefund);
    
        // Calculate the number of full stacks and the remainder
        int fullStacks = totalRefund / 64;
        int remainder = totalRefund % 64;
    
        // Log stack and remainder calculations
        System.out.println("Total Full Stacks: " + fullStacks + ", Total Remainder: " + remainder);
    
        Material currencyMaterial = plugin.getCurrency();
    
        if (currencyMaterial != null) {
            // Fill partial stacks first
            int filledStacks = fillPartialStacks(player, totalRefund, currencyMaterial);
            
            // Update remaining refund after filling partial stacks
            int remainingRefund = totalRefund - filledStacks;
            
            // Calculate remaining full stacks and remainder
            fullStacks = remainingRefund / 64;
            remainder = remainingRefund % 64;
    
            // Add remaining full stacks
            for (int i = 0; i < fullStacks; i++) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(currencyMaterial, 64));
                if (!leftover.isEmpty()) {
                    handleLeftoverItems(player, leftover, 64);
                    break; // Stop adding if inventory is full
                }
            }
    
            // Add remaining items
            if (remainder > 0) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(currencyMaterial, remainder));
                if (!leftover.isEmpty()) {
                    handleLeftoverItems(player, leftover, remainder);
                }
            }
        } else {
            System.out.println("Currency material is null. Cannot refund items.");
            player.sendMessage("Error: Currency material is not set. Unable to refund bets.");
        }
    
        // Clear the player's bets
        playerBets.clear();
        restoreBets(); // Update the inventory to remove lore information
    }
    
    private int fillPartialStacks(Player player, int totalRefund, Material currencyMaterial) {
        // Track the total number of items added by filling partial stacks
        int totalFilled = 0;
    
        // Check and fill any partial stacks in the player's inventory
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() == currencyMaterial && item.getAmount() < 64) {
                int spaceLeft = 64 - item.getAmount();
                int amountToFill = Math.min(spaceLeft, totalRefund - totalFilled); // Ensure we don't overfill
                item.setAmount(item.getAmount() + amountToFill);
                totalFilled += amountToFill;
                if (totalFilled >= totalRefund) {
                    break;
                }
            }
        }
    
        return totalFilled; // Return the total amount added by filling partial stacks
    }
    
    private void handleLeftoverItems(Player player, HashMap<Integer, ItemStack> leftover, int refundAmount) {
        // Notify the player about leftover items
        int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        player.sendMessage("Inventory full. Couldn't refund " + leftoverAmount + " of " + refundAmount + " " + plugin.getCurrency().toString() + "s!");
    
        // Log detailed information about leftover items
        leftover.forEach((slot, itemStack) -> {
            System.out.println("Leftover Item at Slot " + slot + ": " + itemStack.getType() + " x " + itemStack.getAmount());
        });
    }
    


    private void restoreBetsForPage(int page) {
        // Iterate over the stored bets and re-apply them to the inventory
        if(page==1&&playerBets.containsKey(-1)){
            updateItemLore(18, playerBets.get(-1));
        }
        playerBets.forEach((slot, bet) -> {
            if ((page == 1 && slot >= 0 && slot <= 17) || (page == 2 && slot >= 18 && slot <= 36)) {
                
                updateItemLore(slot - (page == 1 ? 0 : 18), bet);
            }
        });
    }
    
    // Handle closing the betting table to clear bets
    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();

        player.sendMessage("Exited betting table. All bets still active, undoable until bets are closed.");
            DealerVillager.savePlayerBets(dealer, player, playerBets);
        //cancelAllBets();
        
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
           // System.out.println("Thisisinopenrouletteinventory");
           // rouletteInventory.printPlayerBets(playerId);
            rouletteInventory.refresh(player);
        }else {
            player.sendMessage("Error: Unable to find Roulette inventory.");
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
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
           // System.out.println("Thisisinsavebetsto roulette");
            rouletteInventory.printPlayerBets(playerId);
        }
        else {
            plugin.getLogger().warning("Failed to save bets: Roulette inventory not found.");
        }
    }
}
