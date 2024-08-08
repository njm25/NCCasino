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
    private final String internalName;
    private double selectedWager;
    private int pageNum;

    private final Map<String, Double> chipValues;
    private final Map<Integer, Double> playerBets; // Map to store the player's bets by slot number
    private boolean clickAllowed = true;

    public BettingTable(Player player, Villager dealer, Nccasino plugin, Map<Integer, Double> existingBets, String internalName) {
        this.playerId = player.getUniqueId();
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.inventory = Bukkit.createInventory(this, 54, "Your Betting Table");
        this.pageNum = 1;

        this.chipValues = new HashMap<>();
        loadChipValuesFromConfig();

        this.playerBets = new HashMap<>(existingBets); // Initialize with existing bets

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
        clearAllLore(); // Clear all lore before setting up the page

        // Setting up straight-up bets
        addStraightUpBetsPageOne();

        // Setting up dozens and other bets
        addDozensAndOtherBetsPageOne();

        // Adding common components
        addCommonComponents();
    }

    private void setupPageTwo() {
        inventory.clear();
        clearAllLore(); // Clear all lore before setting up the page

        // Setting up straight-up bets
        addStraightUpBetsPageTwo();

        // Setting up dozens and other bets
        addDozensAndOtherBetsPageTwo();

        // Adding common components
        addCommonComponents();
    }

    private void addStraightUpBetsPageOne() {
        // Positions based on standard roulette layout
        int[] numbersPageOne = {1,3, 6, 9, 12, 15, 18,21,24, 0, 2, 5, 8, 11, 14, 17,20,23,1, 1, 4, 7, 10, 13, 16, 19, 22};
        String[] colorsPageOne = {"BLUE","RED", "BLACK", "RED", "RED","BLACK", "RED","RED", "BLACK", "GREEN", "BLACK", "RED", "BLACK","BLACK", "RED", "BLACK","BLACK", "RED","BLUE", "RED","BLACK","RED","BLACK","BLACK","RED", "RED", "BLACK"};

        for (int i = 0; i < 27; i++) {
            if(!(i==0||i==18)){
            if(numbersPageOne[i]==0){
                inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageOne[i], 1));
            }    
            else inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageOne[i], numbersPageOne[i]));
        }}
    }

    private void addStraightUpBetsPageTwo() {
       // Positions based on standard roulette layout
       int[] numbersPageTwo = {15, 18, 21, 24, 27, 30, 33, 36, 3,14, 17, 20, 23, 26, 29, 32, 35,2, 13, 16, 19, 22, 25, 28, 31, 34,1};
       String[] colorsPageTwo = {"BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "RED","GREEN", "RED", "BLACK", "BLACK", "RED", "BLACK","BLACK", "RED", "BLACK", "GREEN","BLACK", "RED","RED", "BLACK", "RED", "BLACK","BLACK", "RED","GREEN"};

       for (int i = 0; i < numbersPageTwo.length; i++) {
          if(!(i==8||i==17||i==26)) inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageTwo[i] + "_STAINED_GLASS_PANE"), "straight up " + numbersPageTwo[i], numbersPageTwo[i]));
       }

       // Adding row bets
       inventory.setItem(8, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Top Row-2:1", 1));
       inventory.setItem(17, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Middle Row-2:1", 1));
       inventory.setItem(26, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Bottom Row-2:1", 1));
    }

    private void addDozensAndOtherBetsPageOne() {

        inventory.setItem(28, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(29, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(30, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(31, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1st Dozen", 1));
        inventory.setItem(32, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(33, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(34, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(35, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));

        // Adding other bets
        inventory.setItem(37, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1-18", 1));
        inventory.setItem(38, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "1-18", 1));
        inventory.setItem(39, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Even", 1));
        inventory.setItem(40, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Even", 1));
        inventory.setItem(41, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(42, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(43, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(44, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
    }

    private void addDozensAndOtherBetsPageTwo() {
        // Adding dozens
        inventory.setItem(27, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(28, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(29, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(30, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "2nd Dozen", 1));
        inventory.setItem(31, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(32, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(33, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "3rd Dozen", 1));
        inventory.setItem(34, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "3rd Dozen", 1));

        // Adding other bets
        inventory.setItem(36, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(37, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red", 1));
        inventory.setItem(38, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(39, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black", 1));
        inventory.setItem(40, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Odd", 1));
        inventory.setItem(41, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Odd", 1));
        inventory.setItem(42, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "19-36", 1));
        inventory.setItem(43, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "19-36", 1));
    }

    private void addCommonComponents() {
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Undo Last Bet", 1));
        inventory.setItem(52, createCustomItem(Material.MUSIC_DISC_PIGSTEP, "Back to Roulette", 1));
       
        int slot = 47;
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(chipValues.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());
        
        for (Map.Entry<String, Double> entry : sortedEntries) {
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            slot++;
        }
       
        inventory.setItem(53, createCustomItem(Material.ARROW, "Switch Page", 1));
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

        if ((pageNum == 1 && slot >= 0 && slot <= 18) || (pageNum == 2 && slot >= 0 && slot <= 17)) {
            int actualNum = (pageNum == 1) ? slot : slot + 18;
            int actualSlot = (pageNum == 1) ? actualNum : slot;
            if (selectedWager > 0) {
                if (hasEnoughWager(player, selectedWager)) {
                    removeWagerFromInventory(player, selectedWager);
                    player.sendMessage("Placed bet on " + itemName + " with " + selectedWager + " " + plugin.getCurrencyName(internalName) + "s.");

                    if (pageNum == 1 && slot == 18) {
                        if (playerBets.containsKey(-1)) {
                            double currentBet = playerBets.get(-1);
                            double newBet = currentBet + selectedWager;
                            playerBets.put(-1, newBet);
                            updateItemLore(slot, newBet);
                        } else {
                            playerBets.put(-1, selectedWager);
                            updateItemLore(slot, selectedWager);
                        }
                    } else {
                        if (playerBets.containsKey(actualNum)) {
                            playerBets.replace(actualNum, playerBets.get(actualNum) + selectedWager);
                            updateItemLore(actualSlot, playerBets.get(actualNum));
                        } else {
                            playerBets.put(actualNum, selectedWager);
                            updateItemLore(actualSlot, selectedWager);
                        }
                    }
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
            player.sendMessage("All bets cleared and refunded.");
            return;
        }

        if (slot == 52) {
            saveBetsToRoulette();
            player.sendMessage("Returning to Roulette...");
            openRouletteInventory(dealer, player);
            DealerVillager.savePlayerBets(dealer, player, playerBets);
        }
    }

    private void clearAllBetsAndRefund(Player player) {
        double totalAmount = playerBets.values().stream().mapToDouble(Double::doubleValue).sum();
        int totalRefund = (int) Math.ceil(totalAmount);

        int fullStacks = totalRefund / 64;
        int remainder = totalRefund % 64;

        Material currencyMaterial = plugin.getCurrency(internalName);

        if (currencyMaterial != null) {
            int filledStacks = fillPartialStacks(player, totalRefund, currencyMaterial);

            int remainingRefund = totalRefund - filledStacks;

            fullStacks = remainingRefund / 64;
            remainder = remainingRefund % 64;

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

        playerBets.clear();
        restoreBets(); // Update the inventory to remove lore information
    }

    private int fillPartialStacks(Player player, int totalRefund, Material currencyMaterial) {
        int totalFilled = 0;

        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() == currencyMaterial && item.getAmount() < 64) {
                int spaceLeft = 64 - item.getAmount();
                int amountToFill = Math.min(spaceLeft, totalRefund - totalFilled);
                item.setAmount(item.getAmount() + amountToFill);
                totalFilled += amountToFill;
                if (totalFilled >= totalRefund) {
                    break;
                }
            }
        }

        return totalFilled;
    }

    private void handleLeftoverItems(Player player, HashMap<Integer, ItemStack> leftover, int refundAmount) {
        int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        player.sendMessage("Inventory full. Couldn't refund " + leftoverAmount + " of " + refundAmount + " " + plugin.getCurrencyName(internalName) + "s!");

        leftover.forEach((slot, itemStack) -> {
            plugin.getLogger().warning("Leftover Item at Slot " + slot + ": " + itemStack.getType() + " x " + itemStack.getAmount());
        });
    }

    private void restoreBetsForPage(int page) {
        if (page == 1 && playerBets.containsKey(-1)) {
            updateItemLore(18, playerBets.get(-1));
        }
        playerBets.forEach((slot, bet) -> {
            if ((page == 1 && slot >= 0 && slot <= 17) || (page == 2 && slot >= 18 && slot <= 36)) {
                updateItemLore(slot - (page == 1 ? 0 : 18), bet);
            }
        });
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();

        player.sendMessage("Exited betting table. All bets still active, undoable until bets are closed.");
        DealerVillager.savePlayerBets(dealer, player, playerBets);
    }

    private void updateItemLore(int slot, double wager) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("Current Bet: " + wager + " " + plugin.getCurrencyName(internalName));
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
            rouletteInventory.updatePlayerBets(playerId, playerBets);

            player.openInventory(rouletteInventory.getInventory());
        } else {
            player.sendMessage("Error: Unable to find Roulette inventory.");
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
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

    private void restoreBets() {
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
            rouletteInventory.updatePlayerBets(playerId, playerBets);
        } else {
            plugin.getLogger().warning("Failed to save bets: Roulette inventory not found.");
        }
    }
}
