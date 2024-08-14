package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class RouletteInventory extends DealerInventory implements Listener {

    private int pageNum;
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;
    private final Map<Player,BettingTable>Tables;
    private static final NamespacedKey BETS_KEY = new NamespacedKey(Nccasino.getPlugin(Nccasino.class), "bets");

    private int bettingCountdownTaskId = -1;
    private boolean betsClosed = false;
    private int bettingTimeSeconds = 30;
    private String internalName;

    public RouletteInventory(UUID dealerId, Nccasino plugin, String internalName) {
        //super(dealerId, 54, "Wheel - Dealer: " + DealerVillager.getInternalName((Villager) Bukkit.getEntity(dealerId)));
        super(dealerId, 54, "Roulette Wheel");
        this.plugin = plugin;
        this.pageNum = 1;
        this.Bets = new HashMap<>();
        this.Tables=new HashMap<>();
        this.internalName= internalName;
        initializeStartMenu();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear();
        Bets.clear();
        Tables.clear();
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette", 1), 22);

    }

    

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (pageNum == 1) {
            if (slot == 22) {
               // player.sendMessage("Starting Roulette...");
              this.bettingTimeSeconds =  plugin.getTimer(internalName);
                setupGameMenu();
                pageNum = 2;
                player.openInventory(this.getInventory());
            }
        } else if (pageNum == 2) {
            handleGameMenuClick(slot, player);
        }
    }

    // Set up items for the game menu
    private void setupGameMenu() {
        startBettingTimer();
    }

    private void handleGameMenuClick(int slot, Player player) {
        if (slot == 52) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                        .findFirst().orElse(null);
                if (dealer != null) {
                    Stack<Pair<String, Integer>> bets = getPlayerBets(player.getUniqueId());
                    String internalName = DealerVillager.getInternalName(dealer);
                    BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets, internalName, this);
                    Tables.put(player, bettingTable);
                    player.openInventory(bettingTable.getInventory());
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open betting table.");
                }
            }, 1L);
        } 
        
        else if (slot == 53) {
            BettingTable bt = Tables.get(player);
            if (bt != null) {
                bt.clearAllBetsAndRefund(player);
                }
            player.closeInventory();
            player.sendMessage("You have left the game.");
            
            // Remove player from the Tables map
            Tables.remove(player);
            
            // Remove all bets associated with the player
            removeAllBets(player.getUniqueId());
    
            // If no players are active, reset the game
            if (Tables.isEmpty()) {
                resetToStartState();
            }
        }

        else if (slot==50){
            if(bettingTimeSeconds>5) 
            
            
            bettingTimeSeconds--;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            plugin.saveConfig();  // Save the configuration to persist changes
            addItem(createCustomItem(Material.CLOCK, "-1s Betting Timer (Will take effect next round)",bettingTimeSeconds),50);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds),51);
        }
        
        else if (slot==51){
            if(bettingTimeSeconds<64) bettingTimeSeconds++;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)",bettingTimeSeconds),50);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds),51);
        }
    }



   
    

 // Bet Management Functions
 public void addBet(UUID playerId, String betType, int wager) {
    Bets.computeIfAbsent(playerId, k -> new Stack<>()).add(new Pair<>(betType, wager));
    updateAllLore(playerId); // Update lore after adding a bet
}

public void removeFromBets(UUID playId){
    Bets.remove(playId);
}

public void removeLastBet(UUID playerId) {
    Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
    if (betStack != null && !betStack.isEmpty()) {
        betStack.pop();
        updateAllLore(playerId); // Update lore after removing a bet
    }
}

public void removeAllBets(UUID playerId) {
    Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
    if (betStack != null) {
        betStack.clear();
        updateAllLore(playerId); // Update lore after removing all bets
 
    }
}

public Stack<Pair<String, Integer>> getPlayerBets(UUID playerId) {
    return Bets.getOrDefault(playerId, new Stack<>());
}



// Update the lore for all betting slots based on the player's bets
private void updateAllLore(UUID playerId) {
    Map<String, Integer> betTotals = new HashMap<>();
    Stack<Pair<String, Integer>> betStack = Bets.get(playerId);

    if (betStack != null) {
        for (Pair<String, Integer> bet : betStack) {
            betTotals.put(bet.getFirst(), betTotals.getOrDefault(bet.getFirst(), 0) + bet.getSecond());
        }

        for (Map.Entry<String, Integer> entry : betTotals.entrySet()) {
            updateItemLoreForBet(entry.getKey(), entry.getValue());
        }
    }
}

private void updateItemLoreForBet(String betType, int totalBet) {
    for (int i = 0; i < inventory.getSize(); i++) {
        ItemStack item = inventory.getItem(i);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && betType.equals(meta.getDisplayName())) {
                List<String> lore = new ArrayList<>();
                lore.add("Current Bet: " + totalBet + " " + plugin.getCurrencyName(dealerId.toString()));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }
}


    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void resetToStartState() {
        Tables.clear();
        initializeStartMenu();
        pageNum = 1;
    }

    
    private void setTimer(int set){
        bettingTimeSeconds=set;
    }

    
    private void startBettingTimer() {

        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }

        inventory.clear();

if(bettingTimeSeconds==0){
    addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)",1),50);
    addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", 1),51);
}
    else{    addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)",bettingTimeSeconds),50);
        addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds),51);}

          addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1),52);
        addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 53); // Add an exit button
     
        betsClosed = false;
        bettingCountdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int countdown = bettingTimeSeconds;

            @Override
            public void run() {
                if (countdown > 0) {
                    for (BettingTable bettingTable : Tables.values()) {
                        bettingTable.updateCountdown(countdown, betsClosed);
                    }
                    addItem(createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown), 45);
                    countdown--;
                } else {
                
                    


                    handleBetClosure();
                    Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                    bettingCountdownTaskId = -1;
                    
                }
            }
        }, 0L, 20L); // Run every second
    }

    private boolean isActivePlayer(Player player) {
        InventoryView openInventoryView = player.getOpenInventory();
        Inventory topInventory = openInventoryView.getTopInventory();
        return (topInventory.getHolder() == this || topInventory.getHolder() instanceof BettingTable);
    }
    
    
    private void handleBetClosure() {
        betsClosed = true;
        List<Player> activePlayers = new ArrayList<>();
        List<Player> playersWithBets = new ArrayList<>();
        
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory != null && openInventory.getTopInventory().getHolder() == this) {
                activePlayers.add(player);
            }
        }
    
        for (Player player : Tables.keySet()) {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory != null && (openInventory.getTopInventory().getHolder() == this || openInventory.getTopInventory().getHolder() == Tables.get(player))) {
                activePlayers.add(player);
            }
    
            if (player != null && player.isOnline()) {
                Stack<Pair<String, Integer>> playerBets = getPlayerBets(player.getUniqueId());
                if (!playerBets.isEmpty()) {
                    activePlayers.add(player);
                    playersWithBets.add(player);
                }
            }
        }
    
        if (playersWithBets.isEmpty() && activePlayers.isEmpty()) {
            resetToStartState();
        } else {
            for (Player player : playersWithBets) {
                if (player.isOnline()) {
                    player.sendMessage("Bets Locked!");
                }
            }
    
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Random random = new Random();
                int result = random.nextInt(37); // Random number between 0 and 36 to simulate a roulette wheel
    
                for (Player player : activePlayers) {
                    InventoryView openInventory = player.getOpenInventory();
                    if (openInventory != null && (openInventory.getTopInventory().getHolder() == this || openInventory.getTopInventory().getHolder() == Tables.get(player))) {
                        player.openInventory(this.getInventory());
                    }
                }
    
                for (int i = 0; i < 54; i++) {
                    addItem(createCustomItem(Material.CYAN_STAINED_GLASS_PANE, "Spinning...", 1), i);
                }
    
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (int i = 0; i < 54; i++) {
                        if (isRed(result)) {
                            addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "HOLY CRAP A RED " + result, result), i);
                        } else if (isBlack(result)) {
                            addItem(createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "HOLY CRAP A BLACK " + result, result), i);
                        } else {
                            addItem(createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "HOLY CRAP A " + result, 1), i);
                        }
                    }
    
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Player tplayer : Tables.keySet()) {
                            Stack<Pair<String, Integer>> playerBets = getPlayerBets(tplayer.getUniqueId());
    
                            if (!playerBets.isEmpty()) {
                                if (isRed(result)) {
                                    tplayer.sendMessage("Hit Red " + result + "!");
                                } else if (isBlack(result)) {
                                    tplayer.sendMessage("Hit Black " + result + "!");
                                } else {
                                    tplayer.sendMessage("Hit " + result + ", WOW!");
                                }
    
                                BettingTable bettingTable = Tables.get(tplayer);
                                if (bettingTable != null) {
                                    bettingTable.processSpinResult(result, playerBets);
    
                                    InventoryView openInventory = tplayer.getOpenInventory();
                                    if (openInventory != null && openInventory.getTopInventory().getHolder() instanceof RouletteInventory) {
                                        tplayer.openInventory(bettingTable.getInventory());
                                    }
                                }
                            }
                        }
                    }, 90L);
    
                }, 55L);
    
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    resetGameForNextSpin();
                    setupGameMenu();
                }, 150L);
    
            }, 65L);
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
    
    
    private void resetGameForNextSpin() {
        betsClosed = false;
        for (BettingTable bettingTable : Tables.values()) {
            bettingTable.resetBets();
        }
    }
    
  

   
    

    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets, Player player) {
        if (bets == null) {
            bets = new Stack<>();
        }
        Bets.put(playerId, bets);
    }
    
  
}
