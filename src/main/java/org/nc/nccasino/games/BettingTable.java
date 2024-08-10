package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;

import org.nc.nccasino.games.Pair;

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

import java.util.*;

public class BettingTable implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private final String internalName;
    private double selectedWager;
    private int pageNum;

    private final Map<String, Double> chipValues;
    private final Stack<Pair<String, Integer>> betStack;
    private boolean clickAllowed = true;

    public BettingTable(Player player, Villager dealer, Nccasino plugin, Stack<Pair<String, Integer>> existingBets, String internalName) {
        this.playerId = player.getUniqueId();
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.inventory = Bukkit.createInventory(this, 54, "Your Betting Table");
        this.pageNum = 1;

        this.chipValues = new HashMap<>();
        loadChipValuesFromConfig();

        this.betStack = existingBets != null ? existingBets : new Stack<>();

        initializeTable();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadChipValuesFromConfig() {
        Nccasino nccasino = (Nccasino) plugin;
        for (int i = 1; i <= 5; i++) {
            String chipName = nccasino.getChipName(internalName, i);
            double chipValue = nccasino.getChipValue(internalName, i);
            this.chipValues.put(chipName, chipValue);
        }
    }

    private void initializeTable() {
        setupPageOne();
    }

    private void setupPageOne() {
        inventory.clear();
        clearAllLore(); 
       
        addStraightUpBetsPageOne();
        addDozensAndOtherBetsPageOne();
        addCommonComponents();
        updateAllLore();
       //   restoreBetsForPage(1);
    }

    private void setupPageTwo() {
        inventory.clear();
        clearAllLore(); 

        updateAllLore();
        addStraightUpBetsPageTwo();
        addDozensAndOtherBetsPageTwo();
        addCommonComponents();
        updateAllLore();
      //  restoreBetsForPage(2);
    }

    private void addStraightUpBetsPageOne() {
        int[] numbersPageOne = {1, 3, 6, 9, 12, 15, 18, 21, 24, 0, 2, 5, 8, 11, 14, 17, 20, 23, 1, 1, 4, 7, 10, 13, 16, 19, 22};
        String[] colorsPageOne = {"BLUE", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "LIME", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLUE", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "RED", "BLACK"};

        for (int i = 0; i < 27; i++) {
            if (!(i == 0 || i == 18)) {
                if (numbersPageOne[i] == 0) {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageOne[i], 1));
                } else {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageOne[i], numbersPageOne[i]));
                }
            }
        }
    }

    private void addStraightUpBetsPageTwo() {
        int[] numbersPageTwo = {15, 18, 21, 24, 27, 30, 33, 36, 3, 14, 17, 20, 23, 26, 29, 32, 35, 2, 13, 16, 19, 22, 25, 28, 31, 34, 1};
        String[] colorsPageTwo = {"BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "GREEN", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "GREEN", "BLACK", "RED", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "GREEN"};

        for (int i = 0; i < numbersPageTwo.length; i++) {
            if (!(i == 8 || i == 17 || i == 26)) {
                inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageTwo[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageTwo[i], numbersPageTwo[i]));
            }
        }

        inventory.setItem(8, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Top Row-2:1", 1));
        inventory.setItem(17, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Middle Row-2:1", 1));
        inventory.setItem(26, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Bottom Row-2:1", 1));
    }

    private void addDozensAndOtherBetsPageOne() {
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(35, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));

        inventory.setItem(37, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18", 1));
        inventory.setItem(38, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18", 1));
        inventory.setItem(39, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Even", 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Even", 1));
        inventory.setItem(41, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(42, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(43, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(44, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
    }

    private void addDozensAndOtherBetsPageTwo() {
        inventory.setItem(27, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen", 1));

        inventory.setItem(36, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(37, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(38, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(39, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Odd", 1));
        inventory.setItem(41, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Odd", 1));
        inventory.setItem(42, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36", 1));
        inventory.setItem(43, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36", 1));
    }

    private void addCommonComponents() {
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Undo Last Bet", 1));

        ItemStack discItem = new ItemStack(Material.MUSIC_DISC_PIGSTEP, 1);
        ItemMeta discMeta = discItem.getItemMeta();
        if (discMeta != null) {
            discMeta.setDisplayName("Back to Roulette");
            discMeta.setLore(new ArrayList<>());
            discItem.setItemMeta(discMeta);
        }
        inventory.setItem(52, discItem);

        int slot = 47;
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(chipValues.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Double> entry : sortedEntries) {
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            slot++;
        }

        inventory.setItem(53, createCustomItem(Material.ARROW, "Switch Page", 1));
    }
    private void updateAllLore() {
        // Map to store the total bet for each bet type
        Map<String, Integer> betTotals = new HashMap<>();
    
        // Iterate through the bet stack and sum the totals for each bet type
        for (Pair<String, Integer> bet : betStack) {
            betTotals.put(bet.getFirst(), betTotals.getOrDefault(bet.getFirst(), 0) + bet.getSecond());
        }
    
        // Iterate over all possible slots for both pages and update the lore
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                String itemName = item.getItemMeta().getDisplayName();
                if (betTotals.containsKey(itemName)) {
                    updateItemLore(slot, betTotals.get(itemName));
                } else {
                    // If no bets remain for this item, clear the lore
                    clearItemLore(slot);
                }
            }
        }
    }
    
    private void clearItemLore(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasLore()) {
                meta.setLore(new ArrayList<>());
                item.setItemMeta(meta);
            }
        }
    }

    private void clearAllLore() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    meta.setLore(new ArrayList<>());
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
        clickAllowed = false;

        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed = true, 5L);

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
        } else if (pageNum == 2 && slot == 53) {
            setupPageOne();
            pageNum = 1;
            return;
        }

        if (slot >= 47 && slot <= 51) {
            selectedWager = getWagerAmountFromName(itemName);
            if (selectedWager > 0) {
                player.sendMessage("Selected wager: " + selectedWager + " " + plugin.getCurrencyName(internalName) + "s");
            } else {
                player.sendMessage("Invalid wager amount selected.");
            }
            return;
        }
        if ((pageNum == 1 && isValidSlotPage1(slot)) || (pageNum == 2 && isValidSlotPage2(slot))) {
            if (selectedWager > 0) {
                if (hasEnoughWager(player, selectedWager)) {
                    removeWagerFromInventory(player, selectedWager);
                    player.sendMessage("Placed bet on " + itemName + " with " + selectedWager + " " + plugin.getCurrencyName(internalName) + "s.");
    
                    betStack.push(new Pair<>(itemName, (int) selectedWager));
                    updateAllLore();
                  //  updateAllRelatedSlots(slot, itemName);
                } else {
                    player.sendMessage("Not enough " + plugin.getCurrencyName(internalName) + "s to place this bet.");
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
            updateAllLore();
            player.sendMessage("All bets cleared and refunded.");
            return;
        }

        if (slot == 46) {
            player.sendMessage("Undoing last bet...");
            if (!betStack.isEmpty()) {
                Pair<String, Integer> lastBet = betStack.pop();
                refundWagerToInventory(player, lastBet.getSecond());
                updateAllLore();
            }
            return;
        }

        if (slot == 52) {
            saveBetsToRoulette();
            player.sendMessage("Returning to Roulette...");
            openRouletteInventory(dealer, player);
        }
    }
/* 
    private void updateAllRelatedSlots(int slot, String itemName) {
        for(int i=0;i<44;i++){


        }
        if (pageNum == 1) {
            // Logic to update all related slots on page 1
        } else if (pageNum == 2) {
            // Logic to update all related slots on page 2
        }
    }
*/

// Check if the slot is valid for page 1
private boolean isValidSlotPage1(int slot) {
    return (slot >= 1 && slot <= 17) || (slot >= 19 && slot <= 26) || (slot >= 28 && slot <= 35) || (slot >= 37 && slot <= 44);
}

// Check if the slot is valid for page 2
private boolean isValidSlotPage2(int slot) {
    return (slot >= 0 && slot <= 34) || (slot >= 36 && slot <= 43);
}

    private void clearAllBetsAndRefund(Player player) {
        int totalRefund = betStack.stream().mapToInt(Pair::getSecond).sum();
        refundWagerToInventory(player, totalRefund);
        betStack.clear();
    }

    private void refundWagerToInventory(Player player, int amount) {
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);

        if (currencyMaterial != null) {
            for (int i = 0; i < fullStacks; i++) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(currencyMaterial, 64));
                if (!leftover.isEmpty()) {
                    handleLeftoverItems(player, leftover, 64);
                    break;
                }
            }

            if (remainder > 0) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(currencyMaterial, remainder));
                if (!leftover.isEmpty()) {
                    handleLeftoverItems(player, leftover, remainder);
                }
            }
        } else {
            player.sendMessage("Error: Currency material is not set. Unable to refund bets.");
        }
    }

    private void handleLeftoverItems(Player player, HashMap<Integer, ItemStack> leftover, int refundAmount) {
        int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        player.sendMessage("Inventory full. Couldn't refund " + leftoverAmount + " of " + refundAmount + " " + plugin.getCurrencyName(internalName) + "s!");

        leftover.forEach((slot, itemStack) -> {
            plugin.getLogger().warning("Leftover Item at Slot " + slot + ": " + itemStack.getType() + " x " + itemStack.getAmount());
        });
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
        saveBetsToRoulette();
    }

    private void updateItemLore(int slot, int totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("Current Bet: " + totalBet + " " + plugin.getCurrencyName(internalName));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    private double getWagerAmountFromName(String name) {
        return chipValues.getOrDefault(name, 0.0);
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(plugin.getCurrency(internalName)), requiredAmount);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        if (requiredAmount > 0) {
            player.getInventory().removeItem(new ItemStack(plugin.getCurrency(internalName), requiredAmount));
        } else {
            player.sendMessage("Invalid wager amount: " + amount);
        }
    }

    private void openRouletteInventory(Villager dealer, Player player) {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);

        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.updatePlayerBets(playerId, betStack);
            player.openInventory(rouletteInventory.getInventory());
        } else {
            player.sendMessage("Error: Unable to find Roulette inventory.");
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
        }
    }

    private void saveBetsToRoulette() {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);

        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.updatePlayerBets(playerId, betStack);
        } else {
            plugin.getLogger().warning("Failed to save bets: Roulette inventory not found.");
        }
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
