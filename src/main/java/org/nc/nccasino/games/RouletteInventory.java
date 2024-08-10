package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final int bettingTimeSeconds = 30;

    public RouletteInventory(UUID dealerId, Nccasino plugin) {
        //super(dealerId, 54, "Wheel - Dealer: " + DealerVillager.getInternalName((Villager) Bukkit.getEntity(dealerId)));
        super(dealerId, 54, "Roulette Wheel");
        this.plugin = plugin;
        this.pageNum = 1;
        this.Bets = new HashMap<>();
        this.Tables=new HashMap<>();
        initializeStartMenu();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

   
    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear();
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette", 1), 22);
    }


    // Set up items for the game menu
    private void setupGameMenu() {
        inventory.clear();
        
        /*ItemStack discItem = new ItemStack(Material.MUSIC_DISC_PIGSTEP, 1);
        ItemMeta discMeta = discItem.getItemMeta();
        if (discMeta != null) {
            discMeta.setDisplayName("Back to Roulette");
            discMeta.setLore(new ArrayList<>());
            discItem.setItemMeta(discMeta);
        }
        inventory.setItem(52, discItem);
        addItem(createCustomItem(Material.MUSIC_DISC_PIGSTEP, "Open Betting Table", 1), 20);*/
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1),52);
        addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 53); // Add an exit button
      
    
        
        startBettingTimer();
    }

 // Bet Management Functions
 public void addBet(UUID playerId, String betType, int wager) {
    Bets.computeIfAbsent(playerId, k -> new Stack<>()).add(new Pair<>(betType, wager));
    updateAllLore(playerId); // Update lore after adding a bet
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


public void loadPlayerBets(UUID playerId, Villager dealer) {
    if (dealer == null) {
        plugin.getLogger().warning("Attempted to load player bets, but dealer is null.");
        return;
    }

    PersistentDataContainer container = dealer.getPersistentDataContainer();
    NamespacedKey key = new NamespacedKey(plugin, playerId.toString());
    Stack<Pair<String, Integer>> betStack = new Stack<>();

    if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
        PersistentDataContainer playerContainer = container.get(key, PersistentDataType.TAG_CONTAINER);

        for (NamespacedKey betKey : playerContainer.getKeys()) {
            String betData = playerContainer.get(betKey, PersistentDataType.STRING);
            if (betData != null) {
                String[] parts = betData.split(":");
                String betType = parts[0];
                int wager = Integer.parseInt(parts[1]);
                betStack.push(new Pair<>(betType, wager));
            }
        }
        Bets.put(playerId, betStack);
        updateAllLore(playerId); // Update lore after loading bets
    }
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

    


    private void startBettingTimer() {

        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }

        inventory.clear();
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
                    addItem(createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown), 1);
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
        Stack<Pair<String, Integer>> playerBets = Bets.get(player.getUniqueId());
        return (playerBets != null && !playerBets.isEmpty()) || Tables.containsKey(player) || player.getOpenInventory().getTopInventory().getHolder() == this;
    }


    private void handleBetClosure() {
        betsClosed = true;
        for (Player player : Tables.keySet()) {
            BettingTable bettingTable = Tables.get(player);
            if (bettingTable != null) {
                bettingTable.sendFinalBetsToRoulette();
            }
        }

        List<Player> playersWithBetsOrActive = new ArrayList<>();

        // Collect all players who are considered active
        for (Player player : Tables.keySet()) {
            if (isActivePlayer(player)) {
                playersWithBetsOrActive.add(player);
            }
        }
        if (playersWithBetsOrActive.isEmpty()) {
            resetToStartState();
        } else {
            for (Player player : playersWithBetsOrActive) {
                if (player.isOnline()) {
                    player.sendMessage("Bets Locked!");
                }
                BettingTable bettingTable = Tables.get(player);
                if (bettingTable != null) {
                    bettingTable.updateCountdown(0, betsClosed);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                startWheelSpin();
            }, 65L);
        }
    }
    



    private void startWheelSpin() {
        Random random = new Random();
        int result = random.nextInt(37); // Random number between 0 and 36 to simulate a roulette wheel
    
        // Notify all players about the wheel spin
        for (Player tplayer : Tables.keySet()) {
            tplayer.sendMessage("The wheel is spinning!");
        }
    
        // Display a placeholder or visual effect during the spin
      //  Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 54; i++) {
                addItem(createCustomItem(Material.CYAN_STAINED_GLASS_PANE, "Spinning...", 1), i);
            }
       // }, 10L); // Example delay for spin effect
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 54; i++) {
                if (isRed(result)){
                    addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "HOLY CRAP A RED "+result, result), i);
                }
                else if (isBlack(result)){
                addItem(createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "HOLY CRAP A BLACK "+result, result), i);}
                else   addItem(createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "HOLY CRAP A "+result, result), i);}
            // Simulate the end of the wheel spin






            Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player tplayer : Tables.keySet()) {
                if(isRed(result)){
                    tplayer.sendMessage("The wheel landed on Red " + result + "!");
                }
                else if (isBlack(result)){tplayer.sendMessage("The wheel landed on Black " + result + "!");}
                else {tplayer.sendMessage("The wheel landed on GREEN " + result + "!"); }
              
                BettingTable bettingTable = Tables.get(tplayer);
                if (bettingTable != null) {
                    bettingTable.processSpinResult(result,getPlayerBets(tplayer.getUniqueId()));
                    tplayer.openInventory(bettingTable.getInventory());
                }
           

            }
        }, 90L);


////^^^^animation, finishes with tplayer.openInventory

        
        }, 55L); // Example delay for revealing the result
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetGameForNextSpin(); // Reset game state for the next spin
            setupGameMenu();    // Restart the betting timer
        }, 150L); // Allow time for processing results before resetting





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
    
  

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (pageNum == 1) {
            if (slot == 22) {
                player.sendMessage("Starting Roulette...");
                setupGameMenu();
                pageNum = 2;
                player.openInventory(this.getInventory());
            }
        } else if (pageNum == 2) {
            handleGameMenuClick(slot, player);
        }
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
        } else if (slot == 53) {

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




    }
    

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
        Villager dealer = (Villager) player.getWorld().getEntity(this.dealerId);
        //savePlayerBets(player.getUniqueId());
    }

    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets, Player player) {
        if (bets == null) {
            bets = new Stack<>();
        }
        Bets.put(playerId, bets);
    
       // Villager dealer = (Villager) player.getWorld().getEntity(this.dealerId);
      //  savePlayerBets(playerId);
    }
    
  
}
