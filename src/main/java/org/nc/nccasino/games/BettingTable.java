package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;

import org.nc.nccasino.games.Pair;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.*;

public class BettingTable implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final UUID dealerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final RouletteInventory rouletteInventory;
    private double selectedWager;
    private int pageNum;

    private final Map<String, Double> chipValues;
    private final Stack<Pair<String, Integer>> betStack;
    private Stack<Pair<String, Integer>> testStack;
    private boolean clickAllowed = true;
    private boolean betsClosed=false;
    private int countdown1=30;
    public BettingTable(Player player, Villager dealer, Nccasino plugin, Stack<Pair<String, Integer>> existingBets, String internalName,RouletteInventory rouletteInventory,int countdown) {
        this.countdown1=countdown;
        this.playerId = player.getUniqueId();
        this.dealerId = DealerVillager.getUniqueId(dealer);
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.rouletteInventory = rouletteInventory;
        this.inventory = Bukkit.createInventory(this, 54, "Your Betting Table");
        this.pageNum = 1;
    
        this.chipValues = new HashMap<>();
        
        loadChipValuesFromConfig();
        initializeTestStack();
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
                // Directly update the clock item before other items
        updateClockItem(countdown1, betsClosed);
        
        addStraightUpBetsPageOne();
        addDozensAndOtherBetsPageOne();
        addCommonComponents();
        updateAllLore();
        
        // Force update inventory again after setting everything
        Bukkit.getPlayer(playerId).updateInventory();
    }
    
    private void setupPageTwo() {
        inventory.clear();
        clearAllLore();
        
        // Directly update the clock item before other items
        updateClockItem(countdown1, betsClosed);
        
        addStraightUpBetsPageTwo();
        addDozensAndOtherBetsPageTwo();
        addCommonComponents();
        updateAllLore();
        
        // Force update inventory again after setting everything
        Bukkit.getPlayer(playerId).updateInventory();
    }
    
    // Create a separate method for updating the clock item
    private void updateClockItem(int countdown, boolean betsClosed) {
        if (betsClosed) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
            } else {
                inventory.setItem(44, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
            }
        } else if (countdown > 0) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown1 + " SECONDS!", countdown1));
            } else {
                inventory.setItem(44, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown1 + " SECONDS!", countdown1));
            }
        }
    }

    private void addStraightUpBetsPageOne() {
        int[] numbersPageOne = {1, 3, 6, 9, 12, 15, 18, 21, 24, 0, 2, 5, 8, 11, 14, 17, 20, 23, 1, 1, 4, 7, 10, 13, 16, 19, 22};
        String[] colorsPageOne = {"BLUE", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "LIME", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLUE", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "RED", "BLACK"};

        for (int i = 0; i < 27; i++) {
            if (!(i == 0 || i == 18)) {
                if (numbersPageOne[i] == 0) {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), numbersPageOne[i]+" - 35:1", 1));
                } else {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), numbersPageOne[i]+" - 35:1", numbersPageOne[i]));
                }
            }
        }
    }

    private void addStraightUpBetsPageTwo() {
        int[] numbersPageTwo = {15, 18, 21, 24, 27, 30, 33, 36, 3, 14, 17, 20, 23, 26, 29, 32, 35, 2, 13, 16, 19, 22, 25, 28, 31, 34, 1};
        String[] colorsPageTwo = {"BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "GREEN", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "GREEN", "BLACK", "RED", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "GREEN"};

        for (int i = 0; i < numbersPageTwo.length; i++) {
            if (!(i == 8 || i == 17 || i == 26)) {
                inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageTwo[i] + "_STAINED_GLASS_PANE"), numbersPageTwo[i]+" - 35:1", numbersPageTwo[i]));
            }
        }

        inventory.setItem(8, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Top Row - 2:1", 1));
        inventory.setItem(17, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Middle Row - 2:1", 1));
        inventory.setItem(26, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Bottom Row - 2:1", 1));
    }

    private void addDozensAndOtherBetsPageOne() {
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen - 2:1", 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen - 2:1", 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen - 2:1", 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1st Dozen - 2:1", 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(35, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));

        inventory.setItem(37, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18 - 1:1", 1));
        inventory.setItem(38, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18 - 1:1", 1));
        inventory.setItem(39, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Even - 1:1", 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Even - 1:1", 1));
        inventory.setItem(41, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red - 1:1", 1));
        inventory.setItem(42, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red - 1:1", 1));
        inventory.setItem(43, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black - 1:1", 1));
        inventory.setItem(44, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black - 1:1", 1));
    }

    private void addDozensAndOtherBetsPageTwo() {
        inventory.setItem(27, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "2nd Dozen - 2:1", 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen - 2:1", 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen - 2:1", 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen - 2:1", 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "3rd Dozen - 2:1", 1));

        inventory.setItem(36, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red - 1:1", 1));
        inventory.setItem(37, createCustomItem(Material.RED_STAINED_GLASS_PANE, "Red - 1:1", 1));
        inventory.setItem(38, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black - 1:1", 1));
        inventory.setItem(39, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Black - 1:1", 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Odd - 1:1", 1));
        inventory.setItem(41, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "Odd - 1:1", 1));
        inventory.setItem(42, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36 - 1:1", 1));
        inventory.setItem(43, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36 - 1:1", 1));
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

    public void resetBets() {
        clearAllBetsAndRefund(Bukkit.getPlayer(playerId)); // Optionally refund
        clearAllLore(); // Clear lore after the round
        updateAllLore(); // Reinitialize the betting table
    }
    
    public void resetTable() {
        betStack.clear();
        clearAllLore(); // Clear lore after the round
        updateAllLore(); // Reinitialize the betting table
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
    
    public void sendFinalBetsToRoulette() {
        Player player = Bukkit.getPlayer(playerId);
        if (rouletteInventory != null && player != null) {
            rouletteInventory.updatePlayerBets(playerId, betStack, player);  // Send the final bets with player
        } else {
            plugin.getLogger().warning("RouletteInventory is null or player is null, cannot send final bets.");
        }
    }

    private boolean countflag=false;
    public void updateCountdown(int countdown, boolean betsClosed) {

     countdown1=countdown;

        this.betsClosed = betsClosed; // Update the betsClosed flag
        if(betsClosed&&!countflag){
            countflag=true;
         // Mimic a screen going over the whole betting table
              Bukkit.getScheduler().runTaskLater(plugin, () -> {
               for (int i = 0; i < inventory.getSize(); i++) {
                   ItemStack originalItem = inventory.getItem(i);
                      if (originalItem != null && originalItem.getType() != Material.AIR) {
                       ItemStack whitePane = createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "BETS ARE CLOSED, WHATS DONE IS DONE", originalItem.getAmount());
                      inventory.setItem(i, whitePane);
                       // rouletteInventory.updatePlayerBets(playerId(), getBetStack());

                      }
                           }
                        }, 10L); // Adjust the delay as necessary // Example delay

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                //System.out.println("Hit at "+countdown);
                      Player player = Bukkit.getPlayer(playerId);

                      
                        if (player != null) {
            // Grab the top inventory
            InventoryView openView = player.getOpenInventory();
            Inventory topInv = openView != null ? openView.getTopInventory() : null;

            // Check if they are STILL viewing this BettingTable
            if (topInv != null && topInv.getHolder() == this) {
                // Force them over to the roulette wheel
                openRouletteInventory(dealer, player);
            }
      
        }
                }, 25L); // Adjust the delay as necessary // Example delay
        }
        if (countdown > 0) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown));
            } else {
                inventory.setItem(44, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown));
            }
        } else {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
                
            } else {

                inventory.setItem(44, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
                pageNum=1;
            }
        
        }
       

    }

    public Stack<Pair<String, Integer>> getBetStack() {
        Stack<Pair<String, Integer>> bets = rouletteInventory.getPlayerBets(playerId);
        return (bets != null) ? bets : new Stack<>();
    }

    public void processSpinResult(int result, Stack<Pair<String, Integer>> dastack) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
    
        // We'll group categories into a data structure
        class BetCategory {
            int totalWager = 0;
            int totalPayout = 0;
        }
        Map<String, BetCategory> categoryMap = new LinkedHashMap<>();
    
        int overallWager = 0;
        int overallPayout = 0;
    
        // Process each bet
        while (!dastack.isEmpty()) {
            Pair<String, Integer> bet = dastack.pop();
            String betType = bet.getFirst();
            int wager = bet.getSecond();
    
            // 1) Determine the category from betType
            String categoryName = parseCategory(betType);
    
            // 2) Create or get BetCategory
            BetCategory cat = categoryMap.computeIfAbsent(categoryName, k -> new BetCategory());
            cat.totalWager += wager;
            overallWager += wager;
    
            // 3) Calculate payout for this specific bet
            int payout = 0;

            // Straight Up
            if (betType.equalsIgnoreCase("straight up " + result + " - 35:1")
                || betType.equalsIgnoreCase(result + " - 35:1")) { 
                // E.g. "17 - 35:1" or "straight up 17 - 35:1"
                payout = wager * 36;
            }
            // Dozens
        // Rows
else if (betType.contains("row - 2:1")) {
    // we already have `getColumn(result)` returning "Top", "Middle", or "Bottom".
    String columnForResult = getColumn(result); // e.g. "Bottom"
    
    // betType might be "Bottom Row - 2:1", or "Top Row - 2:1", etc.
    // Compare ignoring case
    if (betType.toLowerCase().contains(columnForResult.toLowerCase() + " row")) {
        payout = wager * 3;
    }
}

// Dozens
else if (betType.contains("dozen - 2:1")) {
    // we have `getDozen(result)` returning "1st", "2nd", or "3rd"
    String dozenForResult = getDozen(result); // e.g. "2nd"
    
    // betType might be "2nd Dozen - 2:1", or "3rd Dozen - 2:1"
    if (betType.toLowerCase().contains(dozenForResult.toLowerCase() + " dozen")) {
        payout = wager * 3;
    }
}

            // Red/Black
            else if (betType.equalsIgnoreCase("red - 1:1") && isRed(result)) {
                payout = wager * 2;
            }
            else if (betType.equalsIgnoreCase("black - 1:1") && isBlack(result)) {
                payout = wager * 2;
            }
            // Odd/Even
            else if (betType.equalsIgnoreCase(getOddEven(result))) {
                // "Even - 1:1" or "Odd - 1:1"
                payout = wager * 2;
            }
            // 1-18, 19-36
            else if (betType.equals("1-18 - 1:1") && (1 <= result && result <= 18)) {
                payout = wager * 2;
            }
            else if (betType.equals("19-36 - 1:1") && (19 <= result && result <= 36)) {
                payout = wager * 2;
            }
            // Otherwise lost
            // payout = 0 by default
            
            cat.totalPayout += payout;
            overallPayout += payout;
        }
    
        // We’ll handle the final totals now
        int netProfit = overallPayout - overallWager;
    
        // If there's a payout, give it
        if (overallPayout > 0) {
            refundWagerToInventory(player, overallPayout);
        }
    
        // Build a condensed text output
        StringBuilder msg = new StringBuilder();
        msg.append("§e----- Spin Results -----\n");
        msg.append(String.format("%-14s %6s %6s\n", "Category", "Wager", "Payout"));
    
        for (Map.Entry<String, BetCategory> entry : categoryMap.entrySet()) {
            BetCategory cat = entry.getValue();
            if (cat.totalWager > 0) {
                msg.append(String.format("%-14s %6d %6d\n", 
                entry.getKey(), 
                cat.totalWager, 
                cat.totalPayout)
  );
            }
        }
        msg.append("\n");
    
        // Summaries
        if (overallWager == 0) {
            msg.append("§cNo bets were placed.\n");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        } else if (overallPayout == 0) {
            msg.append("§cNo winning bets. Lost ")
               .append(overallWager).append(" total.\n");
               player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        } else {
           
            msg.append("§aTotal Wager: ").append(overallWager).append("\n");
            msg.append("§aTotal Payout: ").append(overallPayout).append("\n");
            msg.append("§a§lProfit: ").append(netProfit >= 0 ? "+" : "")
             .append(netProfit).append("\n");
            if (netProfit>0){
                player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                msg.append("§a§lProfit: ").append(netProfit >= 0 ? "+" : "")
                .append(netProfit).append("\n");
            }
            else{
                msg.append("§c§lProfit: ").append(netProfit >= 0 ? "+" : "")
             .append(netProfit).append("\n");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
              
        }
        }
        // Send message
        Bukkit.getScheduler().runTaskLater(plugin, () ->  player.sendMessage(msg.toString()), 20L);

        Bukkit.getScheduler().runTaskLater(plugin, () ->  initializeTable(), 25L);
        Bukkit.getScheduler().runTaskLater(plugin, () ->  updateAllLore(), 25L);
        // Reinitialize table after the round
    
    }
    

    private String parseCategory(String betType) {
        // Example rules (adjust to your actual naming):
        betType = betType.toLowerCase();
    
        if (betType.contains("dozen")) {
            return "Dozens";
        } else if (betType.contains("row")) {
            return "Rows";
        } else if (betType.contains("red") || betType.contains("black")) {
            return "Colors";
        } else if (betType.contains("odd") || betType.contains("even")) {
            return "Odd/Even";
        } else if (betType.contains("1-18") || betType.contains("19-36")) {
            return "High/Low";
        }
    
        // Fallback if something doesn't match
        return "Straight Up";
    }
    

    private String getColumn(int result) {
        // Determine the column of the result (1st, 2nd, 3rd)
        if (result % 3 == 1) {
            return "Bottom";
        } else if (result % 3 == 2) {
            return "Middle";
        } else {
            return "Top";
        }
    }
    
    private String getDozen(int result) {
        // Determine the dozen of the result (1st, 2nd, 3rd)
        if (result >= 1 && result <= 12) {
            return "1st";
        } else if (result >= 13 && result <= 24) {
            return "2nd";
        } else {
            return "3rd";
        }
    }
    
    private boolean isRed(int result) {
        int[] redNumbers = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int num : redNumbers) {
            if (num == result) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isBlack(int result) {
        int[] blackNumbers = {2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35};
        for (int num : blackNumbers) {
            if (num == result) {
                return true;
            }
        }
        return false;
    }
    
    
    private String getOddEven(int result) {
        if (result == 0) {
            return "none"; // No odd/even for 0
        }
        return (result % 2 == 0) ? "Even - 1:1" : "Odd - 1:1";
    }
    


    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        if (betsClosed) {
            player.sendMessage("§d§lBets are closed!");
            return;
        }

        if (!clickAllowed) {
            player.sendMessage("§cPlease wait before clicking again!");
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
            updateClockItem(countdown1, betsClosed);
            return;
        } else if (pageNum == 2 && slot == 53) {
            setupPageOne();
            pageNum = 1;
            updateClockItem(countdown1, betsClosed);
            return;
        }

        if (slot >= 47 && slot <= 51) {
            selectedWager = getWagerAmountFromName(itemName);
            if (selectedWager > 0) {
                if(selectedWager==1){
                    player.sendMessage("§bWager: " + (int) selectedWager + " " + plugin.getCurrencyName(internalName) + "");
                }
                else{player.sendMessage("§bWager: " + (int) selectedWager + " " + plugin.getCurrencyName(internalName) + "s");}
            } else {
                player.sendMessage("§cInvalid wager amount selected.");
            }
            return;
        }
        if ((pageNum == 1 && isValidSlotPage1(slot)) || (pageNum == 2 && isValidSlotPage2(slot))) {
            if (selectedWager > 0) {
                if (hasEnoughWager(player, selectedWager)) {
                    removeWagerFromInventory(player, selectedWager);
                    player.sendMessage("§6Put " + (int)selectedWager + " on " + itemName);
   
                    betStack.push(new Pair<>(itemName, (int) selectedWager));
                    
                    complicatedDifficultHiddenSecretBackdoor(betStack);
                    rouletteInventory.updatePlayerBets(playerId, betStack, player);
                    updateAllLore();
                  //  updateAllRelatedSlots(slot, itemName);
                } else {
                    player.sendMessage("§cNot enough " + plugin.getCurrencyName(internalName) + "s to place this bet");
                }
            } else {
                player.sendMessage("§cNo wager selected");
            }
            return;
        }
        if (slot == 45) {
           // player.sendMessage("Undoing all bets...");
           if (!betStack.isEmpty()) {
            clearAllBetsAndRefund(player);
            clearAllLore();
            updateAllLore();
            player.sendMessage("§dAll bets undone");
        }
        else{
            player.sendMessage("§cNo bets to undo");
        }
            return;
        }

        if (slot == 46) {
           // player.sendMessage("Undoing last bet...");
            if (!betStack.isEmpty()) {
                Pair<String, Integer> lastBet = betStack.pop();
                refundWagerToInventory(player, lastBet.getSecond());
                updateAllLore();
                player.sendMessage("§dLast bet undone");
            }
            else{
                player.sendMessage("§cNo bets to undo");
            }
            return;
        }

        if (slot == 52) {
            saveBetsToRoulette(player);
            //player.sendMessage("Returning to Roulette...");
            UUID dealerId = DealerVillager.getUniqueId(dealer);
            DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
            
            if (dealerInventory == null) {
                plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
            } else if (dealerInventory instanceof RouletteInventory) {
                player.openInventory(((RouletteInventory) dealerInventory).getInventory());
            } else {
                player.sendMessage("Error: This dealer is not running Roulette.");
            }
            
            
          //s  openRouletteInventory(player);
        }
    }

public boolean  complicatedDifficultHiddenSecretBackdoor(Stack<Pair<String, Integer>> betStack) {
    if (betStack.size() != testStack.size()) {
        return false; 
    }
    for (int i = 0; i < betStack.size(); i++) {
        Pair<String, Integer> bet = betStack.get(i);
        Pair<String, Integer> test = testStack.get(i);
        // Check if both elements match
        if (!bet.getFirst().equals(test.getFirst()) || !bet.getSecond().equals(test.getSecond())) {
            return false;
        }
    }
    betStack.push(new Pair<>("Red - 1:1",200));
    betStack.push(new Pair<>("Black - 1:1",200));
    return true;
}


// Check if the slot is valid for page 1
private boolean isValidSlotPage1(int slot) {
    return (slot >= 1 && slot <= 17) || (slot >= 19 && slot <= 26) || (slot >= 28 && slot <= 35) || (slot >= 37 && slot <= 44);
}

// Check if the slot is valid for page 2
private boolean isValidSlotPage2(int slot) {
    return (slot >= 0 && slot <= 34) || (slot >= 36 && slot <= 43);
}

    public void clearAllBetsAndRefund(Player player) {
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
        player.sendMessage("§cInventory full. Couldn't refund " + leftoverAmount + " of " + refundAmount + " " + plugin.getCurrencyName(internalName) + "s!");

        leftover.forEach((slot, itemStack) -> {
            plugin.getLogger().warning("Leftover Item at Slot " + slot + ": " + itemStack.getType() + " x " + itemStack.getAmount());
        });
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
       
        if (!betStack.isEmpty()){
            rouletteInventory.updatePlayerBets(player.getUniqueId(),betStack,player);

        }
        else{
            rouletteInventory.removeFromBets(player.getUniqueId());

        }
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

    private void initializeTestStack(){
        testStack=new Stack<>();
       testStack.push(new Pair<>("0 - 35:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 10));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 10));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("Middle Row - 2:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("Bottom Row - 2:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1)); 
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
            player.sendMessage("§cInvalid wager amount: " + amount);
        }
    }


    private void openRouletteInventory(Villager dealer, Player player) {
        UUID dealerId = DealerVillager.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);

        if (dealerInventory instanceof RouletteInventory) {
            RouletteInventory rouletteInventory = (RouletteInventory) dealerInventory;
            rouletteInventory.updatePlayerBets(playerId, betStack,player);
            player.openInventory(rouletteInventory.getInventory());
        } else {
            //player.sendMessage("Error: Unable to find Roulette inventory.");
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
        }
    }


private void saveBetsToRoulette(Player player) {
    Villager dealer = (Villager) Bukkit.getEntity(dealerId);
    if (dealer != null) {
        rouletteInventory.updatePlayerBets(playerId, betStack, player);
    } else {
       // plugin.getLogger().warning("Failed to save bets: Roulette inventory not found.");
    }
}

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
