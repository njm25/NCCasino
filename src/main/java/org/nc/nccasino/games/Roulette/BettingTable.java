package org.nc.nccasino.games.Roulette;

import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.TableGenerator;
import org.nc.nccasino.objects.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.helpers.Preferences;
import java.util.*;

public class BettingTable extends DealerInventory {
    public static final Set<Player> switchingPlayers = new HashSet<>();
    private final UUID playerId;
    public final UUID dealerId;
    private final Mob dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final RouletteInventory rouletteInventory;
    private double selectedWager;
    private int pageNum;
    private Boolean allin=false;
    private final Map<String, Double> chipValues;
    private final Stack<Pair<String, Integer>> betStack;
    private Stack<Pair<String, Integer>> testStack;
    private boolean betsClosed=false;
    private int countdown1=30;
    public BettingTable(Player player, Mob dealer, Nccasino plugin, Stack<Pair<String, Integer>> existingBets, String internalName,RouletteInventory rouletteInventory,int countdown) {
        super(player.getUniqueId(), 54, "Your Betting Table");
        this.countdown1=countdown;
        this.playerId = player.getUniqueId();
        this.dealerId = Dealer.getUniqueId(dealer);
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.rouletteInventory = rouletteInventory;
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
                inventory.setItem(35, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
            }
        } else if (countdown > 0) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown1 + " SECONDS!", countdown1));
            } else {
                inventory.setItem(35, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown1 + " SECONDS!", countdown1));
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
        if(allin){
            inventory.setItem(52, createEnchantedItem(Material.SNIFFER_EGG, "All In (" + (int)selectedWager + ")", 1));
        }
        else{inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, "All In", 1));}

        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.WIND_CHARGE, "Undo Last Bet", 1));
        if(pageNum==1){
            inventory.setItem(36, createCustomItem(Material.ENDER_PEARL, "Back to Wheel", 1));

        }
        else{
            inventory.setItem(44, createCustomItem(Material.ENDER_PEARL, "Back to Wheel", 1));
        }
        int slot = 47;
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(chipValues.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Double> entry : sortedEntries) {
            if(entry.getValue()==selectedWager){
                inventory.setItem(slot, createEnchantedItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));

            }
            else{
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            }
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
            if(slot>=47&&slot<=51){
                break;
            }
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                String itemName = item.getItemMeta().getDisplayName();
                if (betTotals.containsKey(itemName)) {
                    int totalBet = betTotals.get(itemName);
                    updateItemLore(slot, totalBet);

                    int oldAmount = item.getAmount(); 
                    // Extract the current lore
                    List<String> currentLore = item.getItemMeta().getLore();
        
                    // Now build a brand new enchanted item...
                    ItemStack newItem = createEnchantedItem(
                        item.getType(),  // same Material
                        itemName,        // same name
                        oldAmount
                    );
        
                    // Re-apply the lore
                    ItemMeta newMeta = newItem.getItemMeta();
                    newMeta.setLore(currentLore);
                    newItem.setItemMeta(newMeta);
        
                    // Finally, place it back
                    inventory.setItem(slot, newItem);
                } else {
                    // If no bets remain for this item, clear the lore
                    clearItemLore(slot);
                    int oldAmount = item.getAmount();
                    inventory.setItem(slot, createCustomItem(item.getType(), itemName, oldAmount));
                
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
    

    
    public ItemStack createCustomItem(Material material, String name, int amount) {
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
                inventory.setItem(35, createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown));
            }
        } else {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
                
            } else {

                inventory.setItem(35, createCustomItem(Material.CLOCK, "BETS CLOSED", 1));
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
        int overallWager=0;
        // We'll group categories into a data structure
        class BetCategory {
            int totalWager = 0;
            int totalPayout = 0;
        }
        Map<String, BetCategory> categoryMap = new LinkedHashMap<>();
    
        int totalPayout = 0;
    
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
    
            // Determine if bet wins and calculate payout
            if (betType.equalsIgnoreCase(result + " - 35:1")) {
                payout = wager * 36;
            } else if (betType.contains("Row - 2:1") && betType.toLowerCase().contains(getColumn(result).toLowerCase() + " row")) {
                payout = wager * 3;
            } else if (betType.contains("Dozen - 2:1") && betType.toLowerCase().contains(getDozen(result).toLowerCase() + " dozen")) {
                payout = wager * 3;
            } else if (betType.equalsIgnoreCase("red - 1:1") && isRed(result)) {
                payout = wager * 2;
            } else if (betType.equalsIgnoreCase("black - 1:1") && isBlack(result)) {
                payout = wager * 2;
            } else if (betType.equalsIgnoreCase(getOddEven(result))) {
                payout = wager * 2;
            } else if (betType.equals("1-18 - 1:1") && (1 <= result && result <= 18)) {
                payout = wager * 2;
            } else if (betType.equals("19-36 - 1:1") && (19 <= result && result <= 36)) {
                payout = wager * 2;
            }
    
            // Only count payouts from winning bets
            if (payout > 0) {
                cat.totalPayout += payout;
                totalPayout += payout;
            }
        }
    
        // Build result message
        StringBuilder msg = new StringBuilder("§e----- Spin Results -----\n");
        TableGenerator table = new TableGenerator(TableGenerator.Alignment.LEFT, TableGenerator.Alignment.RIGHT, TableGenerator.Alignment.RIGHT);
        table.addRow("§eCategory", "§bWager", "§aPayout");
    
        for (Map.Entry<String, BetCategory> entry : categoryMap.entrySet()) {
            BetCategory cat = entry.getValue();
            table.addRow("§e" + entry.getKey(), "§b" + cat.totalWager, (cat.totalPayout > 0 ? "§a" + cat.totalPayout : "§c0"));
        }
    
        List<String> tableLines = table.generate(TableGenerator.Receiver.CLIENT, false, false);
        for (String line : tableLines) {
            msg.append(line).append("\n");
        }
    
        msg.append("\n");
        if (totalPayout > 0) {
            //msg.append("§bTotal Wager: ").append(overallWager+"§e | ");
            //msg.append("§aTotal Payout: ").append(totalPayout).append("\n");

            if(totalPayout-overallWager>0){
                msg.append("§a§lPaid ").append(totalPayout).append(" " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalPayout) == 1 ? "" : "s") + "\n §r§a§o(profit of "+(totalPayout-overallWager)+")");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
                    Random random = new Random();
                    float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
                    for (int i = 0; i < 3; i++) {
                        float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                         if (SoundHelper.getSoundSafely("entity.player.levelup", player)!= null)player.playSound(player.getLocation(),  Sound.ENTITY_PLAYER_LEVELUP,SoundCategory.MASTER,1.0f, chosenPitch);
                    }
    
                }, 20L);  
            }
            else if(totalPayout-overallWager==0){ 
                msg.append("§6§lPaid ").append(totalPayout).append(" " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalPayout) == 1 ? "" : "s") + "\n §r§6§o (broke even)");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                     if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.SCRAPE, player.getLocation(), 20); 
                }, 20L);  
            }
            else{
                msg.append("§c§lPaid ").append(totalPayout).append(" " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalPayout) == 1 ? "" : "s") + "\n§r§c§o  (loss of "+Math.abs(totalPayout-overallWager)+")");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                     if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20); 
                }, 20L);  
            }
        } else {
            msg.append("§c§lPaid ").append(totalPayout).append(" " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalPayout) == 1 ? "" : "s") + "\n§r§c§o  (loss of "+Math.abs(totalPayout-overallWager)+")");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                 if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20); 
            }, 20L);  
        }
    
        final int totalPayoutFinal = categoryMap.values().stream().mapToInt(cat -> cat.totalPayout).sum();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(msg.toString());
                    break;}
                case VERBOSE:{
                    player.sendMessage(msg.toString());
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            if (totalPayoutFinal > 0) {
                refundWagerToInventory(player, totalPayoutFinal);
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::initializeTable, 25L);
        Bukkit.getScheduler().runTaskLater(plugin, this::updateAllLore, 25L);
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
    


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;


        if (betsClosed) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return;
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();

        if (pageNum == 1 && slot == 53) {
             if (SoundHelper.getSoundSafely("item.trident.throw", player) != null)player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW,SoundCategory.MASTER, 1.0f, 1.2f); 
            pageNum = 2;
            setupPageTwo();
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aOpened page 2.");            
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            updateClockItem(countdown1, betsClosed);
            return;
        } else if (pageNum == 2 && slot == 53) {
             if (SoundHelper.getSoundSafely("item.trident.throw", player) != null)player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW,SoundCategory.MASTER, 1.0f, 0.8f); 
            pageNum = 1;
            setupPageOne();
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aOpened page 1.");            
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            updateClockItem(countdown1, betsClosed);
            return;
        }
        if(clickedItem.getType()==Material.SNIFFER_EGG){
            Material currencyMat = plugin.getCurrency(internalName);
            int count = Arrays.stream(player.getInventory().getContents())
                              .filter(Objects::nonNull)
                              .filter(it -> it.getType() == currencyMat)
                              .mapToInt(ItemStack::getAmount).sum();
            if (count <= 0) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cNo " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(count) == 1 ? "" : "s") + "\n");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f);
                return;
            }
            allin=true;
            selectedWager=count;

            for (int i = 47; i <= 51; i++) {
                resetChipAtSlot(i);
            }
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aAll in wager of " + count +" ready to place.");            
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            ItemStack updatedTotem = createEnchantedItem(Material.SNIFFER_EGG, "All In (" + count + ")", 1);
            inventory.setItem(slot, updatedTotem);
             if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER,1.0f, 1.0f);
        }
        if (slot >= 47 && slot <= 51) {
             if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE,SoundCategory.MASTER, 1.0f, 1.0f);  
            allin=false;
            // The player clicked on one of the chip slots
            selectedWager = getWagerAmountFromName(itemName); 
            if (selectedWager > 0) {
                // 1) Un-enchant all chips in 47..51
                for (int i = 47; i <= 51; i++) {
                    resetChipAtSlot(i);
                }
                inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG,"All In",1 ));
                // 2) Enchant only the clicked chip
                inventory.setItem(slot, createEnchantedItem(
                    plugin.getCurrency(internalName),
                    itemName,
                    (int) selectedWager
                ));
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aWager: " + selectedWager + " " + plugin.getCurrencyName(internalName));                     
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cInvalid wager amount selected.");                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 

            }
            return;
        }
        if ((pageNum == 1 && isValidSlotPage1(slot)) || (pageNum == 2 && isValidSlotPage2(slot))) {
            // Check if the player is holding the currency item
            ItemStack heldItem = player.getItemOnCursor();
            Material currencyType = plugin.getCurrency(internalName);
            double wagerAmount = 0;
            boolean usedHeldItem = false;
        
            if (heldItem != null && heldItem.getType() == currencyType) {
                wagerAmount = heldItem.getAmount();
                usedHeldItem = true;
            } else {
                wagerAmount = selectedWager;
            }
        
            // Ensure the player has selected a valid wager
            if (wagerAmount > 0) {
                boolean canBet = usedHeldItem || hasEnoughWager(player, wagerAmount);
        
                if (canBet) {
                    if (usedHeldItem) {
                        player.setItemOnCursor(null); // Remove the held stack
                    } else {
                        removeWagerFromInventory(player, wagerAmount);
                    }
        
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            break;
                        case VERBOSE:
                            player.sendMessage("§6Put " + (int) wagerAmount + " on " + itemName);
                            break;
                        case NONE:
                            break;
                    }
        
                    if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        
                    betStack.push(new Pair<>(itemName, (int) wagerAmount));
        
                    complicatedDifficultHiddenSecretBackdoor(betStack);
                    rouletteInventory.updatePlayerBets(playerId, betStack, player);
                    updateAllLore();
        
                    if (allin) {
                        allin = false;
                        inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, "All In", 1));
                    }
        
                } else {
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            player.sendMessage("§cInvalid action.");
                            break;
                        case VERBOSE:
                            player.sendMessage("§cNot enough " + plugin.getCurrencyName(internalName).toLowerCase() + "s");
                            break;
                        case NONE:
                            break;
                    }
                    if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                }
            } else {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage("§cInvalid action.");
                        break;
                    case VERBOSE:
                        player.sendMessage("§cNo wager selected");
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            return;
        }
                
        if (slot == 45) {
           if (!betStack.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aAll bets undone.");                   
                     break;     
                }
                    case NONE:{
                    break;
                }
            } 
            clearAllBetsAndRefund(player);
            clearAllLore();
            updateAllLore();
             if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
        }
        else{
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cInvalid action.");

                    break;}
                case VERBOSE:{
                    player.sendMessage("§cNo bets to undo");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
        }
            return;
        }

        if (slot == 46) {
            if (!betStack.isEmpty()) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aLast bet undone.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                Pair<String, Integer> lastBet = betStack.pop();
                refundWagerToInventory(player, lastBet.getSecond());
                updateAllLore();
                 if (SoundHelper.getSoundSafely("UI.TOAST.IN", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_IN,SoundCategory.MASTER, 3f, 1.0f);
                 if (SoundHelper.getSoundSafely("UI.TOAST.OUT", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_OUT,SoundCategory.MASTER, 3f, 1.0f);
                //player.sendMessage("§dLast bet undone");
            }
            else{
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
    
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cNo bets to undo");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
            }
            return;
        }

        if ((slot == 36&&pageNum==1)||(slot == 44&&pageNum==2)) {
            saveBetsToRoulette(player);
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aReturning to Roulette...");                    
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            UUID dealerId = Dealer.getUniqueId(dealer);
            DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
            
            if (dealerInventory == null) {
                plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
            } else if (dealerInventory instanceof RouletteInventory) {
                switchingPlayers.add(player);
                if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
                rouletteInventory.getMCE().addPlayerToChannel("RouletteWheel", player);
                rouletteInventory.getMCE().removePlayerFromChannel("BettingTable", player);
                }
                player.openInventory(((RouletteInventory) dealerInventory).getInventory());
                 if (SoundHelper.getSoundSafely("item.chorus_fruit.teleport", player) != null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
                 Bukkit.getScheduler().runTaskLater(plugin, () -> {

                switchingPlayers.remove(player);
                 },5L);
            } else {
                player.sendMessage("Error: This dealer is not running Roulette.");
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 

            }
        }
    }


private void resetChipAtSlot(int slot) {
    ItemStack chip = inventory.getItem(slot);
    if (chip == null || !chip.hasItemMeta()) return;

    String chipName = chip.getItemMeta().getDisplayName();
    // Ensure this is a valid chip from chipValues
    if (chipValues.containsKey(chipName)) {
        double value = chipValues.get(chipName);
        // Replace with the normal, unenchanted item
        inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), chipName, (int) value));
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
        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            player.sendMessage("Error: Currency material is not set. Unable to refund bets.");
            return;
        }
    
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        int totalLeftoverAmount = 0;
        HashMap<Integer, ItemStack> leftover;
    
        // Try adding full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(currencyMaterial, 64);
            leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }
    
        // Try adding remainder
        if (remainder > 0) {
            ItemStack remainderStack = new ItemStack(currencyMaterial, remainder);
            leftover = player.getInventory().addItem(remainderStack);
            if (!leftover.isEmpty()) {
                totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }
    
    
        if (totalLeftoverAmount > 0) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");

                    break;}
                case VERBOSE:{
                    player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            dropExcessItems(player, totalLeftoverAmount, currencyMaterial);
        }
    }
    
    // Drops all excess winnings in one batch
    private void dropExcessItems(Player player, int amount, Material currencyMaterial) {
        while (amount > 0) {
            int dropAmount = Math.min(amount, 64);
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(currencyMaterial, dropAmount));
            amount -= dropAmount;
        }
    }
    
    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        switchingPlayers.remove(event.getPlayer());
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
       
        if (switchingPlayers.contains(player)) {

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                switchingPlayers.remove(player);
                 },20L);
            return;
        }

        if (!betStack.isEmpty()){
            rouletteInventory.updatePlayerBets(player.getUniqueId(),betStack,player);

        }
        else{
            rouletteInventory.removeFromBets(player.getUniqueId());

        }
        InventoryView closedInventory = event.getView();
        if (closedInventory != null && closedInventory.getTopInventory().getHolder() == this) {
        rouletteInventory.getMCE().removePlayerFromAllChannels(player);
    }
    }

    private void updateItemLore(int slot, int totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("Wager: " + (int)totalBet + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalBet) == 1 ? "" : "s") + "\n");
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
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cInvalid action.");

                    break;}
                case VERBOSE:{
                    player.sendMessage("§cInvalid wager amount: " + amount);
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
        }
    }

    private void openRouletteInventory(Mob dealer, Player player) {
        saveBetsToRoulette(player);
        UUID dealerId = Dealer.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory == null) {
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
        } else if (dealerInventory instanceof RouletteInventory) {
            switchingPlayers.add(player);
            if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {

            rouletteInventory.getMCE().addPlayerToChannel("RouletteWheel", player);
            rouletteInventory.getMCE().removePlayerFromChannel("BettingTable", player);
            }
            player.openInventory(((RouletteInventory) dealerInventory).getInventory());
             if (SoundHelper.getSoundSafely("item.chorus_fruit.teleport", player) != null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
            switchingPlayers.remove(player);
        } else {
            player.sendMessage("Error: This dealer is not running Roulette.");
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 

        }
    }

private void saveBetsToRoulette(Player player) {
    Mob dealer = (Mob) Bukkit.getEntity(dealerId);
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

    public ItemStack createEnchantedItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            
            meta.setDisplayName(name);
           
            // Add a harmless enchantment to make the item glow
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            
            // Hide the enchantment's lore for a clean look
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
    /*
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    */
}
