package org.nc.nccasino.games.Roulette;

import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.objects.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.nc.VSE.*;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.AnimationTable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class RouletteInventory extends DealerInventory implements Listener {
    private final MultiChannelEngine mce;
    private final List<Integer> wheelLayout = Arrays.asList(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );
    private final Set<Player> switchingPlayers = new HashSet<>();
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;
    public final Map<Player, BettingTable> Tables;
    private Map<Player, Integer> activeAnimations;
    private int frameCounter;
    private int bettingCountdownTaskId = -1;
    private boolean betsClosed = false;
    private int bettingTimeSeconds = 25;
    private int globalCountdown=bettingTimeSeconds;
    private String internalName;
    private Boolean firstopen = true;
    private Boolean firstFin = true;
    private int spinTaskId;
    private int fastSpinTaskId;
    private int bfastSpinTaskId;
    private int regTaskId;
    private int reg2TaskId;
    private int ballTaskId;
    private int wheelOffset = 0;
    private int currentQuadrant = 1; // 1=Top-Right, 2=Top-Left, 3=Bottom-Left, 4=Bottom-Right
    private boolean ballMovementStarted = false;
    private boolean spinAnimationOver = false;
    private int ballSpinDirection;
    private boolean foundfirstquadrant =false;
    private Boolean eightflag=false;
    private Boolean sevflag=false;
    private Boolean nextflag=false;
    // Add these variables at the class level
    private static final long INITIAL_BALL_SPEED = 1L;
    private static final long MIN_BALL_SPEED = 6L;

    // Quadrant-specific slot mappings for main and extra slots
    private final Map<Integer, int[]> extraSlotsMapTopRight = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapTopLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomRight = new HashMap<>();

    ///////////vvvvvvBall Movement Variables/////////////////////////////////////////
    private final Map<Integer, List<Integer>> tracksTopRight = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksTopLeft = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksBottomLeft = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksBottomRight = new HashMap<>();
    // Ball movement spin ranges per track (in spins)
    private final double track1MinSpins = 1.5;
    private final double track1MaxSpins = 3;
    private final double track2MinSpins = .5;
    private final double track2MaxSpins = 1;
    private final double track3MinSpins = 1/36.0;
    private final double track3MaxSpins = 1/36.0;
    private final double track4MinSpins = 1/18.0;
    private final double track4MaxSpins =1/18.0;
    private int slotsPerSpinTrack1;
    private int slotsPerSpinTrack2;
    private int slotsPerSpinTrack3;
    private int slotsPerSpinTrack4;
    private int minSlotsTrack1;
    private int maxSlotsTrack1;
    private int minSlotsTrack2;
    private int maxSlotsTrack2;
    private int minSlotsTrack3;
    private int maxSlotsTrack3;
    private int minSlotsTrack4;
    private int maxSlotsTrack4;
    private int[] slotsToMovePerTrack = new int[5]; // 5 tracks in the sequence
    private final int[] trackSequence = {1, 2, 3/*, 4, 3*/};
    private int trackSequenceIndex = 0;
    private int ballCurrentTrack;
    private int ballCurrentIndex;
    private int ballPreviousSlot = -1;
    private boolean isSwitchingQuadrant = false;
    private boolean finalpicked;
    private int winningNumber;
    private boolean flip2=true;
    private boolean flip4=true;
    private int wheelSpinDirection = 1; // 1 for clockwise, -1 for counter-clockwise
    private int lastDisplayedOffset = 0;
    
    /////////////////////////////////////////////////////////////////////////////////////////
    private final Map<Integer, ItemStack> originalSlotItems = new HashMap<>();

    public RouletteInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 54, "Roulette Wheel");
        this.plugin = plugin;
        this.Bets = new HashMap<>();
        this.Tables = new HashMap<>();
        this.firstopen=true;
        this.activeAnimations = new HashMap<>();
        this.internalName = internalName;
        this.mce = new MultiChannelEngine(plugin);
        initializeTracks();
        initializeExtraSlots();
        registerListener();
        plugin.addInventory(dealerId, this);
    }

    public MultiChannelEngine getMCE() {
        return mce;
    }

    private void initializeTracks() {
        // Existing track initialization code...
    
        // Quadrant 1 (Top-Right)
        tracksTopRight.put(1, Arrays.asList(17, 7, 6, 5, 4, 3, 2, 1, 0));
        tracksTopRight.put(2, Arrays.asList(26, 16, 15, 14, 13, 12, 11, 10, 9));
        tracksTopRight.put(3, Arrays.asList(44,34, 24,23, 22, 21, 20, 19, 18));
        tracksTopRight.put(4, Arrays.asList(53,43, 33, 32, 31, 30, 29, 28, 27));
    
        // Quadrant 2 (Top-Left)
        tracksTopLeft.put(1, Arrays.asList(9, 1, 2, 3, 4, 5, 6, 7, 8));
        tracksTopLeft.put(2, Arrays.asList(18, 10, 11, 12, 13, 14, 15, 16, 17));
        tracksTopLeft.put(3, Arrays.asList(36, 28, 20, 21, 22, 23, 24, 25, 26));
        tracksTopLeft.put(4, Arrays.asList(45,37,29, 30, 31, 32, 33, 34, 35));
    
        // Quadrant 3 (Bottom-Left)
        tracksBottomLeft.put(1, Arrays.asList(36, 46, 47, 48, 49, 50, 51, 52, 53));
        tracksBottomLeft.put(2, Arrays.asList(27, 37, 38, 39, 40, 41, 42, 43, 44));
        tracksBottomLeft.put(3, Arrays.asList(9,19, 29, 30, 31, 32, 33, 34, 35));
        tracksBottomLeft.put(4, Arrays.asList(0,10, 20, 21, 22, 23, 24, 25, 26));
    
        // Quadrant 4 (Bottom-Right)
        tracksBottomRight.put(1, Arrays.asList(44, 52, 51, 50, 49, 48, 47, 46, 45));
        tracksBottomRight.put(2, Arrays.asList(35, 43, 42, 41, 40, 39, 38, 37, 36));
        tracksBottomRight.put(3, Arrays.asList(17,25, 33, 32, 31, 30, 29, 28, 27));
        tracksBottomRight.put(4, Arrays.asList(8, 16, 24, 23, 22, 21, 20, 19, 18));
    
        // Calculate total slots per spin per track
        slotsPerSpinTrack1 = tracksTopRight.get(1).size() + tracksTopLeft.get(1).size() + tracksBottomLeft.get(1).size() + tracksBottomRight.get(1).size();
        slotsPerSpinTrack2 = tracksTopRight.get(2).size() + tracksTopLeft.get(2).size() + tracksBottomLeft.get(2).size() + tracksBottomRight.get(2).size();
        slotsPerSpinTrack3 = tracksTopRight.get(3).size() + tracksTopLeft.get(3).size() + tracksBottomLeft.get(3).size() + tracksBottomRight.get(3).size();
        slotsPerSpinTrack4 = tracksTopRight.get(4).size() + tracksTopLeft.get(4).size() + tracksBottomLeft.get(4).size() + tracksBottomRight.get(4).size();
    
        // Calculate min and max slots per track
        minSlotsTrack1 = (int)(track1MinSpins * slotsPerSpinTrack1);
        maxSlotsTrack1 = (int)(track1MaxSpins * slotsPerSpinTrack1);
        minSlotsTrack2 = (int)(track2MinSpins * slotsPerSpinTrack2);
        maxSlotsTrack2 = (int)(track2MaxSpins * slotsPerSpinTrack2);
        minSlotsTrack3 = (int)(track3MinSpins * slotsPerSpinTrack3);
        maxSlotsTrack3 = (int)(track3MaxSpins * slotsPerSpinTrack3);
        minSlotsTrack4 = (int)(track4MinSpins * slotsPerSpinTrack4);
        maxSlotsTrack4 = (int)(track4MaxSpins * slotsPerSpinTrack4);
    }

    private void initializeExtraSlots() {
        // Top-right quadrant main and extra slots
        extraSlotsMapTopRight.put(27, new int[]{18});
        extraSlotsMapTopRight.put(28, new int[]{19});
        extraSlotsMapTopRight.put(29, new int[]{20});
        extraSlotsMapTopRight.put(30, new int[]{21});
        extraSlotsMapTopRight.put(31, new int[]{22});
        extraSlotsMapTopRight.put(32, new int[]{23});
        extraSlotsMapTopRight.put(33, new int[]{24, 25});
        extraSlotsMapTopRight.put(43, new int[]{34, 35});
        extraSlotsMapTopRight.put(53, new int[]{44});

        // Top-left quadrant main and extra slots
        extraSlotsMapTopLeft.put(45, new int[]{36});
        extraSlotsMapTopLeft.put(37, new int[]{28, 27});
        extraSlotsMapTopLeft.put(29, new int[]{20, 19});
        extraSlotsMapTopLeft.put(30, new int[]{21});
        extraSlotsMapTopLeft.put(31, new int[]{22});
        extraSlotsMapTopLeft.put(32, new int[]{23});
        extraSlotsMapTopLeft.put(33, new int[]{24});
        extraSlotsMapTopLeft.put(34, new int[]{25});
        extraSlotsMapTopLeft.put(35, new int[]{26});

        // Bottom-left quadrant main and extra slots
        extraSlotsMapBottomLeft.put(0, new int[]{9});
        extraSlotsMapBottomLeft.put(10, new int[]{19, 18});
        extraSlotsMapBottomLeft.put(20, new int[]{29, 28});
        extraSlotsMapBottomLeft.put(21, new int[]{30});
        extraSlotsMapBottomLeft.put(22, new int[]{31});
        extraSlotsMapBottomLeft.put(23, new int[]{32});
        extraSlotsMapBottomLeft.put(24, new int[]{33});
        extraSlotsMapBottomLeft.put(25, new int[]{34});
        extraSlotsMapBottomLeft.put(26, new int[]{35});

        // Bottom-right quadrant main and extra slots
        extraSlotsMapBottomRight.put(18, new int[]{27});
        extraSlotsMapBottomRight.put(19, new int[]{28});
        extraSlotsMapBottomRight.put(20, new int[]{29});
        extraSlotsMapBottomRight.put(21, new int[]{30});
        extraSlotsMapBottomRight.put(22, new int[]{31});
        extraSlotsMapBottomRight.put(23, new int[]{32});
        extraSlotsMapBottomRight.put(24, new int[]{33,34});
        extraSlotsMapBottomRight.put(16, new int[]{25,26});
        extraSlotsMapBottomRight.put(8, new int[]{17});
    }

   

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void delete() {
        super.delete();
        if (spinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(spinTaskId);
            spinTaskId = -1;
        }
        if (fastSpinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(fastSpinTaskId);
            fastSpinTaskId = -1;
        }
        if (bfastSpinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
            bfastSpinTaskId = -1;
        }
        if (ballTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ballTaskId);
            ballTaskId = -1;
        }
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
            bettingCountdownTaskId = -1;
        }
    
        // Also clear any data references if you like
        Bets.clear();
        Tables.clear();
        playersWithBets.clear();
        newtry.clear(); 
        activeAnimations.clear();
        unregisterListener();
        
    }

    private void startAnimation(Player player) {
        String animationMessage = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isSneaking() && player.hasPermission("nccasino.use")) {
                // Open the admin inventory immediately without animation
                AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                player.openInventory(adminInventory.getInventory());
            } else {
                // Proceed with the animation for the regular inventory
                activeAnimations.put(player, 1);
                AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
                player.openInventory(animationTable.getInventory());

                // Start animation and return to MinesTable after completion
                animationTable.animateMessage(player, () -> afterAnimationComplete(player));
            }
        }, 1L); // Delay by 1 tick to ensure smooth inventory opening

    }

    private void afterAnimationComplete(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null&&player.isOnline()) {
               // System.out.println("Adding"+player+"to master and roulette"); 
                mce.addPlayerToChannel("Master", player);
                mce.addPlayerToChannel("RouletteWheel", player);
                player.openInventory(this.getInventory());
                this.bettingTimeSeconds = plugin.getTimer(internalName);

                if (firstFin) {
                    firstFin = false;
                    startBettingTimer();
                }

                
            }
        }, 1L);
    }

    @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            if (!firstopen) {
                startAnimation(player);
            }
        }
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.getInventory() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == this.getInventory().getHolder()) {
                    if (firstopen) {
                        firstopen = false;
                        startAnimation(player);
                    }
                }
            }, 2L);
        }
    }

    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        switchingPlayers.remove(event.getPlayer());
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // Check if the player is switching inventories
        if (switchingPlayers.contains(player)) {
            return; // Ignore this close event if the player is switching
        }
    
        // Check if the inventory being closed matches this specific RouletteInventory
        InventoryView closedInventory = event.getView();
        if (closedInventory != null && closedInventory.getTopInventory().getHolder() == this) {
            // Properly remove the player from all channels
            mce.removePlayerFromAllChannels(player);
        }
    }
    
    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();
        handleGameMenuClick(slot, player);
    }


private void handleGameMenuClick(int slot, Player player) {
    if(!betsClosed){
    switch (currentQuadrant) {
        case 1: // Top-right quadrant (initial view)
            switch (slot) {
                /* 
                case 46: // -1 Betting Timer
                    adjustBettingTimer(-1,1);
                    break;
                case 47: // +1 Betting Timer
                    adjustBettingTimer(1,1);
                    break;
*/
                case 46: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 47: // View Betting Info
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN,SoundCategory.MASTER, 1.0f, 1.0f);
                    exitGame(player);
                    break;
                default:
                    break;
            }
            break;

        case 2: // Top-left quadrant
            switch (slot) {
 /* 
                case 49: // -1 Betting Timer
                    adjustBettingTimer(-1,2);
                    break;
                case 50: // +1 Betting Timer
                    adjustBettingTimer(1,2);
                    break;
 */

                case 52: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 53: // Exit
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 3: // Bottom-left quadrant
            switch (slot) {
 /* 
                case 4: // -1 Betting Timer
                    adjustBettingTimer(-1,3);
                    break;
                case 5: // +1 Betting Timer
                    adjustBettingTimer(1,3);
                    break;
*/

                case 7: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 8: // Exit
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 4: // Bottom-right quadrant
            switch (slot) {
 /* 
                case 0: // -1 Betting Timer
                    adjustBettingTimer(-1,4);
                    break;
                case 1: // +1 Betting Timer
                    adjustBettingTimer(1,4);
                    break;
*/
                case 1: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 2: // Exit
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        default:
            // Handle invalid quadrants if needed
            break;
    }
}
}

private void openBettingTable(Player player) {
    switchingPlayers.add(player); // Mark the player as switching inventories

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
            .filter(entity -> entity instanceof Villager)
            .map(entity -> (Villager) entity)
            .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
            .findFirst().orElse(null);
        if (dealer != null) {
            Stack<Pair<String, Integer>> bets = getPlayerBets(player.getUniqueId());
            String internalName = DealerVillager.getInternalName(dealer);
            BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets, internalName, this, globalCountdown);
            Tables.put(player, bettingTable);
            player.openInventory(bettingTable.getInventory());
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 5.0f, 1.0f); 
            mce.addPlayerToChannel("BettingTable", player);
            mce.removePlayerFromChannel("RouletteWheel", player);
            //System.out.println("removed from RouletteWheel added to BettingTable");
        } else {
            player.sendMessage("§cError: Dealer not found. Unable to open betting table.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switchingPlayers.remove(player); // Remove the flag after the switch
    }, 1L); // Small delay to allow the inventory to switch
}


private void exitGame(Player player) {
    BettingTable bt = Tables.get(player);
    if (bt != null) {
        bt.clearAllBetsAndRefund(player);
    }
    player.closeInventory();
    player.sendMessage("§cYou have left the game.");
    Tables.remove(player);
    removeAllBets(player.getUniqueId());

    if (Tables.isEmpty()) {
        resetToStartState();
    }
}


   
    public void addBet(UUID playerId, String betType, int wager) {
        Bets.computeIfAbsent(playerId, k -> new Stack<>()).add(new Pair<>(betType, wager));
        updateAllLore(playerId);
    }

    public void removeFromBets(UUID playId) {
        Bets.remove(playId);
    }

    public void removeLastBet(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null && !betStack.isEmpty()) {
            betStack.pop();
            updateAllLore(playerId);
        }
    }

    public void removeAllBets(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null) {
            betStack.clear();
            updateAllLore(playerId);
        }
    }

    public Stack<Pair<String, Integer>> getPlayerBets(UUID playerId) {
        return Bets.getOrDefault(playerId, new Stack<>());
    }

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
                    lore.add("Total Bet: " + (int)totalBet + " " + plugin.getCurrencyName(internalName)+ (Math.abs(totalBet) == 1 ? "" : "s") + "\n");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }


    private void resetToStartState() {
        Tables.clear();
        firstFin = true;
    }

    

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<UUID,Stack<Pair<String, Integer>>> newtry=new HashMap();
    private List<Player> playersWithBets = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private void handleBetClosure() {
        newtry.clear();
        betsClosed = true;
        List<Player> activePlayers = new ArrayList<>();
        playersWithBets.clear();
        //List<Player> playersWithBets = new ArrayList<>();

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
                    newtry.put(player.getUniqueId(), (Stack<Pair<String, Integer>>) playerBets.clone());
                    activePlayers.add(player);
                    playersWithBets.add(player);
                    Bets.put(player.getUniqueId(), playerBets);
                }
                
            }
        }

        if (playersWithBets.isEmpty() && activePlayers.isEmpty()) {
            resetToStartState();
        } else {
            /*
            for (Player player : playersWithBets) {
                if (player.isOnline()) {
                    player.sendMessage("§dBets locked, spinning!");
                }
            }*/

            Bukkit.getScheduler().runTaskLater(plugin, () -> 
            mce.playSong("RouletteWheel", RouletteSongs.getBallLaunch(), false, "Ball Launch")
            , 20L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> startBallMovement(false), 100L);
            // Transition wheel to slower spin after bets close
            // Update to spinning ball and wheel
            startSpinAnimation(activePlayers);
        }
    }
    
private void startBettingTimer() {
    if (bettingCountdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
    }

    // Start the slow spin as soon as the betting phase begins
    startSlowSpinAnimation(6L); 

    betsClosed = false;

    // Initialize the menu buttons in their proper quadrant locations
    updateMenuButtonsForQuadrant(currentQuadrant);

    bettingCountdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        int countdown = bettingTimeSeconds;
        
        @Override
        public void run() {
            if (countdown > 0) {
                for (BettingTable bettingTable : Tables.values()) {
                    bettingTable.updateCountdown(countdown, betsClosed);
                }

                if(countdown==5){
                    mce.playSong("Master", RouletteSongs.getDynamicFastTick(), false, "DynamicFastTick");

                }
                if (countdown < bettingTimeSeconds&&countdown>5) { // Avoid double-playing on first tick
                    mce.playSong("Master", RouletteSongs.getTimerTick(), false, "TimerTick");
                }
                // Update the timer item in the appropriate slot based on the current quadrant
                int countdownSlot = getCountdownSlotForQuadrant(currentQuadrant);
                ItemStack countdownItem = createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", countdown);
                inventory.setItem(countdownSlot, countdownItem);  // Update the item in the correct slot
                
                countdown--;
                globalCountdown=countdown;
                   switch (currentQuadrant) {
        case 1: // Top-right quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 46);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 47);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 46);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 47);
            break;
        case 2: // Top-left quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 52);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 53);
            break;
        case 3: // Bottom-left quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 5);
           // addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 6);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 7);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 8);
            break;
        case 4: // Bottom-right quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 1);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 2);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 1);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 2);
            break;
    }
            } else if (countdown == 0) {
                globalCountdown=countdown;
                betsClosed = true;

                for (BettingTable bettingTable : Tables.values()) {
                    bettingTable.updateCountdown(countdown, betsClosed);
                }

                int countdownSlot = getCountdownSlotForQuadrant(currentQuadrant);
                ItemStack countdownItem = createCustomItem(Material.CLOCK, "BETS CLOSED", 1);
                inventory.setItem(countdownSlot, countdownItem);

                clearMenuButtonsForQuadrant(currentQuadrant);
                initializeDecorativeSlotsForQuadrant(currentQuadrant);

                handleBetClosure();
                Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                bettingCountdownTaskId = -1;
            }
        }
    }, 0L, 20L);
}



private void startSlowSpinAnimation(long initialSpeed) {

//////////////////////////////////////
    frameCounter = 0;
    int spinDirection = wheelSpinDirection; // Set counter-clockwise

    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }
    if (fastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(fastSpinTaskId);
    }
    if (bfastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
    }
    spinTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        @Override
        public void run() {
            if (betsClosed) {
                Bukkit.getScheduler().cancelTask(spinTaskId); // Stop slow spin when bets are closed
            } else {
                mce.playSong("RouletteWheel", RouletteSongs.getSlowSpinTick(), false, "Basd");
                updateWheelView(frameCounter * spinDirection); // Adjust direction here
                frameCounter++; // Increment frameCounter for the next position
            }
        }
    }, 0L, initialSpeed); // Initial slow speed
}

private void startSpinAnimation(List<Player> activePlayers) {

    if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }

    frameCounter = 0;
    boolean reverseDirection = (frameCounter + 1) % 2 == 0;
    int spinDirection = reverseDirection ? -1 : 1;

    final int totalSpinFrames = 600;
    final long initialWheelSpeed = 1L;
    final long minWheelSpeed = 6L; // Minimum delay (6L) once reached
    final int spinAccelerationFrames = 20;
    final int spinDecelerationFrames = 200;
    long[] currentWheelDelay = {initialWheelSpeed};

    Runnable spinTask = new Runnable() {
        @Override
        public void run() {
            if (!spinAnimationOver) {
                mce.playSong("RouletteWheel", RouletteSongs.getSpinTick(), false, "otherig");
                updateWheel(frameCounter * spinDirection);
    
                if (frameCounter < spinAccelerationFrames) {
                    // Acceleration phase
                    double accelerationProgress = (double) frameCounter / spinAccelerationFrames;
                    currentWheelDelay[0] = (long) Math.max(0.75, initialWheelSpeed - (accelerationProgress * 0.5));
                } else if (frameCounter >= totalSpinFrames - spinDecelerationFrames) {
                    // Deceleration phase: limit delay to minimum of 6L once reached
                    int framesSinceDecelerationStart = frameCounter - (totalSpinFrames - spinDecelerationFrames);
                    double decelerationProgress = Math.pow((double) framesSinceDecelerationStart / spinDecelerationFrames, 2);
                    currentWheelDelay[0] = Math.min(6L, 1L + (long) (decelerationProgress * (minWheelSpeed - 1L)));
                }
                bfastSpinTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this, currentWheelDelay[0]);
                frameCounter++;
            }
        }
    };
    
    fastSpinTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, spinTask, 0L);
    
}

private void startBallMovement(boolean reverseDirection) {
    if (ballMovementStarted) return;
    ballMovementStarted = true;

    wheelSpinDirection *= -1; // Reverse the wheel spin direction
    int ballSpinDirection = wheelSpinDirection; // Ball spins opposite to the wheel

    // Initialize ball movement variables
    ballCurrentTrack = trackSequence[0];
    ballCurrentIndex = 0; // Start at the first position in track
    ballPreviousSlot = -1;
    trackSequenceIndex = 0;

    Random random = new Random();

    // Generate random slots to move per track within specified ranges
    slotsToMovePerTrack[0] = minSlotsTrack1 + random.nextInt(maxSlotsTrack1 - minSlotsTrack1 + 1);
    slotsToMovePerTrack[1] = minSlotsTrack2 + random.nextInt(maxSlotsTrack2 - minSlotsTrack2 + 1);
    slotsToMovePerTrack[2] = minSlotsTrack3 + random.nextInt(maxSlotsTrack3 - minSlotsTrack3 + 1);
    slotsToMovePerTrack[3] = minSlotsTrack4 + random.nextInt(maxSlotsTrack4 - minSlotsTrack4 + 1);
    slotsToMovePerTrack[4] = 0; // Final track 3, stays there

    // Total slots to move
    int totalSlotsToMove = slotsToMovePerTrack[0] + slotsToMovePerTrack[1] + slotsToMovePerTrack[2] + slotsToMovePerTrack[3];

    final int ballAccelerationSlots = totalSlotsToMove / 4;
    final int ballDecelerationSlots = totalSlotsToMove / 2;
    long[] currentBallDelay = {INITIAL_BALL_SPEED};
    
    int[] slotsMovedTotal = {0}; // Wrap in array to allow modification

    // Start moving the ball
    moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);

}

private void moveBall(int ballSpinDirection, long[] currentBallDelay, int[] slotsMovedTotal, int totalSlotsToMove, int ballAccelerationSlots, int ballDecelerationSlots) {

    if (!ballMovementStarted) return;

    if (isSwitchingQuadrant) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
        }, currentBallDelay[0]);
        return;
    }

    long[] tempBallDelay = {1L};    
     Map<Integer, List<Integer>> currentTracks = getTracksForCurrentQuadrant();
    List<Integer> currentTrackSlots = currentTracks.get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) return;

    int nextIndex = (ballCurrentIndex + ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int nextSlot = currentTrackSlots.get(nextIndex);
    int tempSTMPTb=slotsToMovePerTrack[trackSequenceIndex]-1;
    if (tempSTMPTb <= 0) {
        int tempTSIb=trackSequenceIndex+1;
        if (tempTSIb < trackSequence.length) {
        // lookaheadby1 is not final number
        }
        else{
            if(eightflag&&nextIndex==8){
                nextflag=true;

            }
            if(nextIndex==7){
                sevflag=true;

            }
        }

    }

    int lookaheadSlots = 2; // Fixed number of slots to look ahead
    int futureIndex = (ballCurrentIndex + lookaheadSlots * ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int futureSlot = currentTrackSlots.get(futureIndex);
    int tempSTMPT=slotsToMovePerTrack[trackSequenceIndex]-lookaheadSlots;
    if (tempSTMPT == -1) {
        int tempTSI=trackSequenceIndex+lookaheadSlots;
        if (tempTSI < trackSequence.length) {
        // lookaheadslots is not final number
        }
        else{
              // lookaheadslots is !! final number  
            if (isQuadrantBoundary(futureSlot)&&futureIndex==0&&!(ballCurrentIndex==2&&ballCurrentTrack==3)){
                eightflag=true;
            }

        }
    }
    int soundLookaheadSlots = Math.max(4, 7 - (slotsMovedTotal[0] / 15)); 
    int soundFutureIndex = (ballCurrentIndex + soundLookaheadSlots * ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int soundFutureSlot = currentTrackSlots.get(soundFutureIndex);

   ///change below to improve whoosh sound delays. redo with a different lookaheadSlots
    if (isQuadrantBoundary(soundFutureSlot)) {
        int pitch = Math.max(3, 10 - (slotsMovedTotal[0] / 10)); 
        mce.stopSong("RouletteWheel", "BallScraping");
        mce.playSong("RouletteWheel", RouletteSongs.getBallScraping(pitch), false, "BallScraping");
        mce.stopSong("RouletteWheel", "Skibidi");
       mce.playSong("RouletteWheel", RouletteSongs.getSkibidi(pitch), false, "Skibidi");
        mce.stopSong("RouletteWheel", "scas");
       mce.playSong("RouletteWheel",RouletteSongs.getWhoosh(pitch),false, "scas");
    } 
    if (isQuadrantBoundary(nextSlot)&&!eightflag&&!sevflag&&trackSequenceIndex!=2) { 
        isSwitchingQuadrant = true;

        // Show ball in the final slot of the current track
        updateBallPosition(ballSpinDirection);

        // Schedule delay for ball to disappear, then switch quadrant view
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            // Remove ball from current slot
            if (ballPreviousSlot != -1 && originalSlotItems.containsKey(ballPreviousSlot)) {
                inventory.setItem(ballPreviousSlot, originalSlotItems.remove(ballPreviousSlot));
            }
            int nextquadnow=0;
            if (wheelSpinDirection == -1) { // Clockwise
                nextquadnow = (currentQuadrant % 4) + 1; // Move to next quadrant in order
            } else { // Counterclockwise
                nextquadnow = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
            }
            switch (currentQuadrant) {
                case 1:
                    if(nextquadnow==4){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 2:
                if(nextquadnow==3){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 3:
                if(nextquadnow==2){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 4:
                if(nextquadnow==1){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                default:
                    break;
            }
    
            // Delay to simulate ball going off-screen before quadrant switch
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                switchQuadrant();
               
                // Delay to simulate ball still off-screen after quadrant switch
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    ballCurrentIndex = getStartingIndexForNewQuadrant();
                    updateBallPosition(ballSpinDirection);
                   
                    isSwitchingQuadrant = false;
                    moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
                }, tempBallDelay[0]); // Delay after switching quadrants
            }, tempBallDelay[0]); // Delay before switching quadrants
        }, 3L); // Delay before ball disappears
        return;
    }

    // Normal ball movement if not at boundary
    if(!nextflag&&!sevflag){
        updateBallPosition(ballSpinDirection);
        slotsMovedTotal[0]++;
        adjustBallSpeed(currentBallDelay, slotsMovedTotal[0], ballAccelerationSlots, totalSlotsToMove, ballDecelerationSlots);
        slotsToMovePerTrack[trackSequenceIndex]--;
    }
   else{
    if(sevflag){
        updateBallPosition(ballSpinDirection);
        slotsMovedTotal[0]++;
        slotsToMovePerTrack[trackSequenceIndex]--;
        ballMovementStarted = false;
        handleWinningNumber();
        sevflag=false;
        eightflag=false;
        nextflag=false;
        return;
    }
    updateBallPosition(0);
    slotsMovedTotal[0]++;
    slotsToMovePerTrack[trackSequenceIndex]--;
    ballMovementStarted = false;
    handleWinningNumber();
    sevflag=false;
    eightflag=false;
    nextflag=false;
    return;
   }
    if (slotsToMovePerTrack[trackSequenceIndex] <= 0) {
        trackSequenceIndex++;
        if (trackSequenceIndex < trackSequence.length) {
            ballCurrentTrack = trackSequence[trackSequenceIndex];

            int adjustment=1;
    ballCurrentIndex = (ballCurrentIndex + adjustment * wheelSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
        } else {
            ballMovementStarted = false;
            handleWinningNumber();
            sevflag=false;
            eightflag=false;
            nextflag=false;
            return;
        }
    }

    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
        moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
    }, currentBallDelay[0]);
}

private void adjustBallSpeed(long[] currentBallDelay, int slotsMoved, int ballAccelerationSlots, int totalSlots, int ballDecelerationSlots) {
    if (slotsMoved < ballAccelerationSlots) {
        double accelerationProgress = (double) slotsMoved / ballAccelerationSlots;
        currentBallDelay[0] = (long) Math.max(1L, INITIAL_BALL_SPEED - (accelerationProgress * 0.5));
    } else if (slotsMoved >= totalSlots - ballDecelerationSlots) {
        int slotsSinceDecelerationStart = slotsMoved - (totalSlots - ballDecelerationSlots);
        double decelerationProgress = Math.pow((double) slotsSinceDecelerationStart / ballDecelerationSlots, 2);
        currentBallDelay[0] = Math.min(6L, 1L + (long) (decelerationProgress * (MIN_BALL_SPEED - 1L)));
    }
}



private void updateBallPosition(int ballSpinDirection) {
    Map<Integer, List<Integer>> currentTracks = getTracksForCurrentQuadrant();
    List<Integer> currentTrackSlots = currentTracks.get(ballCurrentTrack);

    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return; // No slots in this track
    }
    
    // Restore the item in the previous slot
    if (ballPreviousSlot != -1 && originalSlotItems.containsKey(ballPreviousSlot)) {
        inventory.setItem(ballPreviousSlot, originalSlotItems.remove(ballPreviousSlot));
    }

    // Move the ball index according to the spin direction
    ballCurrentIndex = (ballCurrentIndex + ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();

    int nextSlot = currentTrackSlots.get(ballCurrentIndex);
    //System.out.println("New ball position: slot " + nextSlot +"ballprevi:"+ballPreviousSlot+"track: "+trackSequenceIndex);

    // Store the current item in the slot, then set the ball item
    if (!originalSlotItems.containsKey(nextSlot)) {
        originalSlotItems.put(nextSlot, inventory.getItem(nextSlot));
    }

    ItemStack ballItem = new ItemStack(Material.ENDER_PEARL);
    ItemMeta meta = ballItem.getItemMeta();
    meta.setDisplayName("Ball");
    ballItem.setItemMeta(meta);
    inventory.setItem(nextSlot, ballItem);

    // Update previous slot
    ballPreviousSlot = nextSlot;
}



private int getStartingIndexForNewQuadrant() {
    List<Integer> currentTrackSlots = getTracksForCurrentQuadrant().get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return 0;
    }

    // Determine the correct starting index based on the ball's spin direction
    if (ballSpinDirection == -1) { // Counterclockwise
        return currentTrackSlots.size() - 1;
    } else { // Clockwise
        return 0;
    }
}
private int getStartingIndexForNewQuaadrant() {
    List<Integer> currentTrackSlots = getTracksForCurrentQuadrant().get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return 0;
    }
    // Determine the correct starting index based on the ball's spin direction
    if (wheelSpinDirection == -1&&currentQuadrant==4) { // Counterclockwise
        return 0;
    } else if(wheelSpinDirection == -1) {
        return currentTrackSlots.size() - 1;}
        else{ // Clockwise
        return 0;}
    }

private void switchQuadrant() {
    if (wheelSpinDirection == -1) { // Clockwise
        currentQuadrant = (currentQuadrant % 4) + 1; // Move to next quadrant in order
    } else { // Counterclockwise
        currentQuadrant = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
    }

    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    ballCurrentIndex = getStartingIndexForNewQuadrant();
}

private void switchQauadrant() {
  
    if (wheelSpinDirection == 1) { // Clockwise
        currentQuadrant = (currentQuadrant % 4) + 1; // Move to next quadrant in order
    } else { // Counterclockwise
        currentQuadrant = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
        if(currentQuadrant==2){
        flip4=true;
        if(flip2){
            frameCounter+=7;  
          flip2=false;
          }
          else{
              flip2=true;
          }
        }
        if(currentQuadrant==4){
            flip2=true;
            if(flip4){
          frameCounter-=9;  
        flip4=false;
        }
        else{
            flip4=true;
        }
        }
    }
    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    ballCurrentIndex = getStartingIndexForNewQuaadrant();
}

private boolean isQuadrantBoundary(int slot) {
    // Retrieve current track configuration for the quadrant
    Map<Integer, List<Integer>> trackMap = getTracksForCurrentQuadrant();
    List<Integer> trackSlots = trackMap.get(ballCurrentTrack);

    // Ensure the track slots are defined and the slot exists within the current track
    if (trackSlots == null || !trackSlots.contains(slot)) {
        return false; // Not a boundary if it's not part of the track
    }

    // Define boundaries based on slot positions for each quadrant and direction
    int firstSlot = trackSlots.get(0);
    int lastSlot = trackSlots.get(trackSlots.size() - 1);

    switch (currentQuadrant) {
        case 1: // Top-Right Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) ||  // clockwise to Quadrant 2
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 4
        case 2: // Top-Left Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 3
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 1
        case 3: // Bottom-Left Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 4
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 2
        case 4: // Bottom-Right Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 1
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 3
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}

private void handleWinningNumber() {
    mce.stopSong("RouletteWheel", "BallScraping");
    mce.stopSong("RouletteWheel", "Skibidi");
    mce.stopSong("RouletteWheel", "skibidi");
    // Get the number corresponding to the final slot
    winningNumber = getNumberForSlot(ballPreviousSlot, currentQuadrant);
    finalpicked = true;
    mce.playSong("RouletteWheel", RouletteSongs.getFinalSpot(), false, "Final spot");

    // Loop over players who have placed bets
    for (Player player : playersWithBets) {
        if (player.isOnline()) {
            // Schedule the processing to run after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BettingTable bettingTable = Tables.get(player);
                if (bettingTable != null) {
                    // Get the bet stack directly from the BettingTable
                    Stack<Pair<String, Integer>> playerBets =newtry.get(player.getUniqueId());
  
                    if (!playerBets.isEmpty()) {
                        // Notify the player of the winning number
                        if (isRed(winningNumber)) {
                            player.sendMessage("§cHit Red " + winningNumber + "!");
                        } else if (isBlack(winningNumber)) {
                            player.sendMessage("§fHit Black " + winningNumber + "!");
                        } else {
                            player.sendMessage("§aHit Green " + winningNumber + ", WOW!");
                        }

                        // Process the bets
                        bettingTable.processSpinResult(winningNumber, playerBets);
                    } else {
                        plugin.getLogger().warning(player.getName() + " has no bets to process.");
                    }
                } else {
                    plugin.getLogger().warning("No betting table found for player: " + player.getName());
                }
            }, 30L);
        }
    }

    // Reset for the next round
    Bukkit.getScheduler().runTaskLater(plugin, this::prepareNextRound, 75L);
}

private void prepareNextRound() {

    betsClosed = false;
    for (BettingTable bettingTable : Tables.values()) {
        bettingTable.resetTable();
    }

    // Remove the ball from the slot if present
    if (ballPreviousSlot != -1 && originalSlotItems.containsKey(ballPreviousSlot)) {
        inventory.setItem(ballPreviousSlot, originalSlotItems.remove(ballPreviousSlot));
    }

    // Reset movement and state variables
    ballMovementStarted = false;
    spinAnimationOver = false;
    finalpicked = false;
    trackSequenceIndex = 0;
    ballCurrentTrack = 0;
    ballCurrentIndex = 0;
    ballPreviousSlot = -1;
    //countdown=bettingTimeSeconds;
    //globalCountdown=countdown

    // Optionally reverse the wheel direction to add variety
    wheelSpinDirection *= -1;
  if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }
    if (regTaskId != -1) {
        Bukkit.getScheduler().cancelTask(regTaskId);
    }
    if (reg2TaskId != -1) {
        Bukkit.getScheduler().cancelTask(reg2TaskId);
    }
    //wheelOffset = currentOffset;

    if (fastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(fastSpinTaskId);
    }
    if (bfastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
    }
    wheelOffset = lastDisplayedOffset;
    // Start the betting timer again, which also resets bets and shows menu buttons
    startBettingTimer();

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

private int getNumberForSlot(int mainSlot, int quadrant) {
    Integer number = slotToNumber.get(mainSlot);
    if (number != null) {
        return number;
    } else {
        //plugin.getLogger().warning("Error: No number associated with slot " + mainSlot + " in quadrant " + quadrant);
        return 0;
    }
}  
   

private Map<Integer, List<Integer>> getTracksForCurrentQuadrant() {
    switch (currentQuadrant) {
        case 1: // Top-Right
            return (ballSpinDirection == -1) ? tracksTopRight : reverseTrack(tracksTopRight);
        case 2: // Top-Left
            return (ballSpinDirection == 1) ? reverseTrack(tracksTopLeft) : tracksTopLeft;
        case 3: // Bottom-Left
            return (ballSpinDirection == -1) ? tracksBottomLeft : reverseTrack(tracksBottomLeft);
        case 4: // Bottom-Right
            return (ballSpinDirection == 1) ?  reverseTrack(tracksBottomRight) :tracksBottomRight;
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}


private Map<Integer, List<Integer>> reverseTrack(Map<Integer, List<Integer>> tracks) {
    Map<Integer, List<Integer>> reversedTracks = new HashMap<>();

    for (Map.Entry<Integer, List<Integer>> entry : tracks.entrySet()) {
        List<Integer> reversedList = new ArrayList<>(entry.getValue());
        Collections.reverse(reversedList);
        reversedTracks.put(entry.getKey(), reversedList);
    }

    return reversedTracks;
}


private void updateWheelView(long frameOffset) {
    int currentOffset = Math.floorMod(wheelOffset - (int) frameOffset, wheelLayout.size());
    updateQuadrantDisplay(currentOffset);
    lastDisplayedOffset = currentOffset;
}

    private void updateWheel(int frame) {
        int currentOffset = Math.floorMod(wheelOffset - frame, wheelLayout.size());
        updateQuadrantDisplay(currentOffset);
        lastDisplayedOffset = currentOffset;
    }
    
// Determine the correct slot for the countdown based on the quadrant
private int getCountdownSlotForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            return 45;
        case 2: // Top-left quadrant
            return 51;  // You can change this to match your design
        case 3: // Bottom-left quadrant
            return 6;
        case 4: // Bottom-right quadrant
            return 0;
        default:
            return 45;  // Default to top-right
    }
}

// Update the menu buttons for the given quadrant
private void updateMenuButtonsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 46);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 47);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 46);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 47);
            break;
        case 2: // Top-left quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 52);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 53);
            break;
        case 3: // Bottom-left quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 5);
           // addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 6);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 7);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 8);
            break;
        case 4: // Bottom-right quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 1);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 2);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 1);
            addItem(createCustomItem(Material.SPRUCE_DOOR, "EXIT (Refund and Exit)", 1), 2);
            break;
    }
}

// Remove the menu buttons when bets are closed
private void clearMenuButtonsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            clearSlots(45, 49);
            break;
        case 2: // Top-left quadrant
            clearSlots(48, 52);
            break;
        case 3: // Bottom-left quadrant
            clearSlots(4, 8);
            break;
        case 4: // Bottom-right quadrant
            clearSlots(0, 4);
            break;
    }
}

    // Clear a range of slots
private void clearSlots(int fromSlot, int toSlot) {
    for (int i = fromSlot; i <= toSlot; i++) {
        inventory.setItem(i, null);  // Set the slot to null to clear it
    }
}



private Map<Integer, Integer> slotToNumber = new HashMap<>();


private void switchStayToQuadrant(int quad){
    currentQuadrant=quad;
    //inventory.clear();
    initializeDecorativeSlotsForQuadrant(currentQuadrant);
}

    private void updateQuadrantDisplay(int globalOffset) {
        int[] quadrantSlots;
        int startPosition;
        Map<Integer, int[]> currentExtraSlotsMap;
        slotToNumber.clear();
        // Define the correct slot ranges and starting positions for each quadrant
        switch (currentQuadrant) {
            case 1: // Top-Right Quadrant
                quadrantSlots = new int[]{27, 28, 29, 30, 31, 32, 33, 43, 53};
                startPosition = Math.floorMod(globalOffset + 27, wheelLayout.size());
                currentExtraSlotsMap = extraSlotsMapTopRight;
                break;
            case 2: // Top-Left Quadrant
                quadrantSlots = new int[]{45, 37, 29, 30, 31, 32, 33, 34, 35};
                startPosition = Math.floorMod(globalOffset + 18, wheelLayout.size());
                currentExtraSlotsMap = extraSlotsMapTopLeft;
                break;
            case 3: // Bottom-Left Quadrant
                quadrantSlots = new int[]{0, 10, 20, 21, 22, 23, 24, 25, 26};
                startPosition = Math.floorMod(globalOffset + 9, wheelLayout.size());
                currentExtraSlotsMap = extraSlotsMapBottomLeft;
                break;
            case 4: // Bottom-Right Quadrant
                quadrantSlots = new int[]{18, 19, 20, 21, 22, 23, 24, 16, 8};
                startPosition = Math.floorMod(globalOffset, wheelLayout.size());
                currentExtraSlotsMap = extraSlotsMapBottomRight;
                break;
            default:
                throw new IllegalArgumentException("Invalid quadrant index");
        }
        boolean newflag=false;


        if(finalpicked&&!foundfirstquadrant){
            for (int i = 0; i < quadrantSlots.length; i++) {
             
                int wheelPosition;
                if (currentQuadrant == 1 || currentQuadrant == 2) {
                    // For quadrants 1 and 2, add i to startPosition
                    wheelPosition = Math.floorMod(startPosition + i, wheelLayout.size());
                } else {
                    // For quadrants 3 and 4, subtract i from startPosition
                    wheelPosition = Math.floorMod(startPosition - i, wheelLayout.size());
                }
                int number = wheelLayout.get(wheelPosition);
                if(number==winningNumber){newflag=true;}
                
        }
        if(!newflag){
            int targetquad;
            targetquad=findWinningNumberQuadrant(winningNumber,globalOffset);
            if(currentQuadrant!=targetquad){
        switchStayToQuadrant(targetquad);}
            }
        }


        // Loop through each slot in the quadrant and assign the correct number
        for (int i = 0; i < quadrantSlots.length; i++) {
                
            int wheelPosition;
            if (currentQuadrant == 1 || currentQuadrant == 2) {
                // For quadrants 1 and 2, add i to startPosition
                wheelPosition = Math.floorMod(startPosition + i, wheelLayout.size());
            } else {
                // For quadrants 3 and 4, subtract i from startPosition
                wheelPosition = Math.floorMod(startPosition - i, wheelLayout.size());
            }
            int number = wheelLayout.get(wheelPosition);
            
            // Create the item with the correct number and place it in the quadrant slot
            ItemStack item = createCustomItem(getMaterialForNumber(number),  ""+number, (number == 0) ? 1 : number);
            inventory.setItem(quadrantSlots[i], item);
    
            // Handle the extra slots associated with the main number slot
            if (currentExtraSlotsMap.containsKey(quadrantSlots[i])) {
               
                int[] extraSlots = currentExtraSlotsMap.get(quadrantSlots[i]);
                ItemStack extraItem = createCustomItem(getMaterialForNumber(number), ""+number, 1);
                boolean first=true;
                for (int extraSlot : extraSlots) {
                    
                    slotToNumber.put(extraSlot, number);

                    if(finalpicked&& number==winningNumber&&extraSlot==extraSlots[0]&&first){
                        foundfirstquadrant=true;
                        long[] tempBallDelay ={1L};
                        if(isQuadrantBBoundary(extraSlot)){

                        ItemStack ballItem = new ItemStack(Material.ENDER_PEARL);
                        ItemMeta meta = ballItem.getItemMeta();
                        meta.setDisplayName("Ball");
                        ballItem.setItemMeta(meta);
                        inventory.setItem(extraSlot, ballItem);
                        regTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                // Remove ball from current slot
                                if (ballPreviousSlot != -1 && originalSlotItems.containsKey(ballPreviousSlot)) {
                                    inventory.setItem(ballPreviousSlot, originalSlotItems.remove(ballPreviousSlot));
                                }
                                int nextquadnow=0;
                                if (wheelSpinDirection == -1) { // Clockwise
                                    nextquadnow = (currentQuadrant % 4) + 1; // Move to next quadrant in order
                                } else { // Counterclockwise
                                    nextquadnow = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
                                }
                                switch (currentQuadrant) {
                                    case 1:
                                        if(nextquadnow==4){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 2:
                                    if(nextquadnow==3){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 3:
                                    if(nextquadnow==2){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 4:
                                    if(nextquadnow==1){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            
                                // Delay to simulate ball going off-screen before quadrant switch
                                reg2TaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {

                                    switchQauadrant();

                                    // Delay to simulate ball still off-screen after quadrant switch
                                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                       //?something goes here?
                                    }, 2L); // Delay after switching quadrants
                                }, 2L); // Delay before switching quadrants
                            }, 3L); // Delay before ball disappears 
                            }
                            else{
                            ItemStack ballItem = new ItemStack(Material.ENDER_PEARL);
                            ItemMeta meta = ballItem.getItemMeta();
                            meta.setDisplayName("Ball");
                            ballItem.setItemMeta(meta);
                            inventory.setItem(extraSlot, ballItem);
                            
                            }

                   
                    }
                    else{inventory.setItem(extraSlot, extraItem);}
                }
            }
        }
    }
  
    private int findWinningNumberQuadrant(int winningNumber, int globalOffset) {
        int winningIndex = wheelLayout.indexOf(winningNumber);
    
        // Adjust the winningIndex based on the current globalOffset (wheel rotation)
        int adjustedWinningIndex = (winningIndex - globalOffset + wheelLayout.size()) % wheelLayout.size();
    
        // Each quadrant displays 9 numbers
        int numbersPerQuadrant = 9;
    
        if (adjustedWinningIndex >= 0 && adjustedWinningIndex < numbersPerQuadrant) {
            return 4; // Bottom-Right Quadrant
        } else if (adjustedWinningIndex >= numbersPerQuadrant && adjustedWinningIndex < numbersPerQuadrant * 2) {
            return 3; // Bottom-Left Quadrant
        } else if (adjustedWinningIndex >= numbersPerQuadrant * 2 && adjustedWinningIndex < numbersPerQuadrant * 3) {
            return 2; // Top-Left Quadrant
        } else {
            return 1; // Top-Right Quadrant
        }
    }
    
    
private boolean isQuadrantBBoundary(int slot) {
    switch (currentQuadrant) {
        case 1: // Top-Right Quadrant
            return (wheelSpinDirection == 1 && slot == 18) ||  // clockwise to Quadrant 2
                   (wheelSpinDirection == -1 && slot == 44); // counterclockwise to Quadrant 4
        case 2: // Top-Left Quadrant
            return (wheelSpinDirection == 1 && slot == 36) || // clockwise to Quadrant 3
                   (wheelSpinDirection == -1 && slot == 26); // counterclockwise to Quadrant 1
        case 3: // Bottom-Left Quadrant
            return (wheelSpinDirection == 1 && slot == 35) || // clockwise to Quadrant 4
                   (wheelSpinDirection == -1 && slot == 9); // counterclockwise to Quadrant 2
        case 4: // Bottom-Right Quadrant
            return (wheelSpinDirection == 1 && slot == 17) || // clockwise to Quadrant 1
                   (wheelSpinDirection == -1 && slot == 27); // counterclockwise to Quadrant 3
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}

    
private void initializeDecorativeSlotsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1:
            // Quadrant 1: Brown slots (0-17, 26), Green slots (36-42, 45-52)
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 26}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 48, 49, 50, 51, 52}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 2:
            // Quadrant 2: Brown slots (0-18), Green slots (38-44, 46-53)
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50, 51, 52, 53}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 3:
            // Quadrant 3: Brown slots (27, 36-53), Green slots (1-8, 11-17)
            fillDecorativeSlots(new int[]{27, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 14, 15, 16, 17}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 4:
            // Quadrant 4: Brown slots (35-53), Green slots (0-7, 9-15)
            fillDecorativeSlots(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + quadrant);
    }
}


private void fillDecorativeSlots(int[] slots, Material material) {
    for (int slot : slots) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(material == Material.BROWN_STAINED_GLASS_PANE ? "Outer Rim" : "Inside Rim");
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
}

    private Material getMaterialForNumber(int number) {
        if (number == 0) {
            return Material.LIME_STAINED_GLASS_PANE;
        } else if (isRed(number)) {
            return Material.RED_STAINED_GLASS_PANE;
        } else {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    private boolean isRed(int number) {
        int[] redNumbers = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int n : redNumbers) {
            if (n == number) {
                return true;
            }
        }
        return false;
    }
    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets, Player player) {
        if (bets == null) {
            bets = new Stack<>();
        }
        Bets.put(playerId, bets);
    }
}

   