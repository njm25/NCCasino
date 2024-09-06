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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
import java.util.Arrays;

public class RouletteInventory extends DealerInventory implements Listener {
    private final List<Integer> wheelLayout = Arrays.asList(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );
    private int currentBallPosition = 0;
    private int pageNum;
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;
    private final Map<Player, BettingTable> Tables;
    private Map<Player, Integer> activeAnimations;
    private static final NamespacedKey BETS_KEY = new NamespacedKey(Nccasino.getPlugin(Nccasino.class), "bets");
    private int frameCounter;
    private int bettingCountdownTaskId = -1;
    private boolean betsClosed = false;
    private int bettingTimeSeconds = 10;
    private String internalName;
    private Boolean closeFlag = false;
    private Boolean firstopen = true;
    private Boolean firstFin = true;
    private int spinTaskId;
    private int ballTaskId;
    private int wheelOffset = 0;
    private int ballPosition = 8; // Start at top-right quadrant
    private int currentQuadrant = 1; // 1=Top-Right, 2=Top-Left, 3=Bottom-Left, 4=Bottom-Right
    private int ballFrameCounter=0;

    // Quadrant-specific slot mappings for main and extra slots
    private final Map<Integer, int[]> extraSlotsMapTopRight = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapTopLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomRight = new HashMap<>();

    public RouletteInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 54, "Roulette Wheel");
        this.plugin = plugin;
        this.pageNum = 1;
        this.Bets = new HashMap<>();
        this.Tables = new HashMap<>();
        this.activeAnimations = new HashMap<>();
        this.internalName = internalName;

        initializeExtraSlots();
        registerListener();
        plugin.addInventory(dealerId, this);
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
        extraSlotsMapTopLeft.put(37, new int[]{27, 28});
        extraSlotsMapTopLeft.put(29, new int[]{19, 20});
        extraSlotsMapTopLeft.put(30, new int[]{21});
        extraSlotsMapTopLeft.put(31, new int[]{22});
        extraSlotsMapTopLeft.put(32, new int[]{23});
        extraSlotsMapTopLeft.put(33, new int[]{24});
        extraSlotsMapTopLeft.put(34, new int[]{25});
        extraSlotsMapTopLeft.put(35, new int[]{26});

        // Bottom-left quadrant main and extra slots
        extraSlotsMapBottomLeft.put(0, new int[]{9});
        extraSlotsMapBottomLeft.put(10, new int[]{18, 19});
        extraSlotsMapBottomLeft.put(20, new int[]{28, 29});
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
        extraSlotsMapBottomRight.put(24, new int[]{33, 34});
        extraSlotsMapBottomRight.put(16, new int[]{25, 26});
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
        unregisterListener();
    }

    private void startAnimation(Player player) {
        String animationMessage = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeAnimations.put(player, 1);
            AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
            player.openInventory(animationTable.getInventory());

            animationTable.animateMessage(player, () -> afterAnimationComplete(player));
        }, 1L);
    }

    private void afterAnimationComplete(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeFlag = true;
            if (player != null) {
                if (firstFin) {
                    firstFin = false;
                    this.bettingTimeSeconds = plugin.getTimer(internalName);
                    startBettingTimer();
                }

                player.openInventory(this.getInventory());
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
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();
        handleGameMenuClick(slot, player);
    }

    private void setupGameMenu() {
        startBettingTimer();
    }


private void handleGameMenuClick(int slot, Player player) {
    switch (currentQuadrant) {
        case 1: // Top-right quadrant (initial view)
            switch (slot) {
                case 46: // -1 Betting Timer
                    adjustBettingTimer(-1,1);
                    break;
                case 47: // +1 Betting Timer
                    adjustBettingTimer(1,1);
                    break;
                case 48: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 49: // View Betting Info
                    showBettingInfo(player);
                    break;
                case 50: // Exit
                    exitGame(player);
                    break;
                default:
                    // Handle any other cases if needed
                    break;
            }
            break;

        case 2: // Top-left quadrant
            switch (slot) {
                case 49: // -1 Betting Timer
                    adjustBettingTimer(-1,2);
                    break;
                case 50: // +1 Betting Timer
                    adjustBettingTimer(1,2);
                    break;
                case 51: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 52: // View Betting Info
                    showBettingInfo(player);
                    break;
                case 53: // Exit
                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 3: // Bottom-left quadrant
            switch (slot) {
                case 4: // -1 Betting Timer
                    adjustBettingTimer(-1,3);
                    break;
                case 5: // +1 Betting Timer
                    adjustBettingTimer(1,3);
                    break;
                case 6: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 7: // View Betting Info
                    showBettingInfo(player);
                    break;
                case 8: // Exit
                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 4: // Bottom-right quadrant
            switch (slot) {
                case 0: // -1 Betting Timer
                    adjustBettingTimer(-1,4);
                    break;
                case 1: // +1 Betting Timer
                    adjustBettingTimer(1,4);
                    break;
                case 2: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 3: // View Betting Info
                    showBettingInfo(player);
                    break;
                case 4: // Exit
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

private void adjustBettingTimer(int adjustment,int quad) {
    if ((bettingTimeSeconds > 5 || adjustment > 0) && (bettingTimeSeconds < 64 || adjustment < 0)) {
        bettingTimeSeconds += adjustment;
        plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
        plugin.saveConfig();
        updateTimerItems(quad,bettingTimeSeconds);
    }
}

private void openBettingTable(Player player) {
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

private void exitGame(Player player) {
    BettingTable bt = Tables.get(player);
    if (bt != null) {
        bt.clearAllBetsAndRefund(player);
    }
    player.closeInventory();
    player.sendMessage("You have left the game.");
    Tables.remove(player);
    removeAllBets(player.getUniqueId());

    if (Tables.isEmpty()) {
        resetToStartState();
    }
}

private void showBettingInfo(Player player) {
    // Logic to show current bets or other information to the player
    player.sendMessage("Your current bets are...");
}



private void updateTimerItems(int quadrant, int time) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", time), 46);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", time), 47);
            break;

        case 2: // Top-left quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", time), 50);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", time), 51);
            break;

        case 3: // Bottom-left quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", time), 5);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", time), 6);
            break;

        case 4: // Bottom-right quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", time), 1);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", time), 2);
            break;

        default:
            break;
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
        firstFin = true;
    }

    private void setTimer(int set) {
        bettingTimeSeconds = set;
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
            startBallMovement();

            // Transition wheel to slower spin after bets close
            // Update to spinning ball and wheel
            startSpinAnimation(activePlayers);
        }
    }
    


    private void startSpinAnimation(List<Player> activePlayers) {
        frameCounter = 0;
    
        // Initialize the decorative slots for the wheel
        initializeDecorativeSlots();
    
        final int totalSpinFrames = 200; // Reduced total spin frames for faster spin duration
        final long initialWheelSpeed = 1L; // Start the wheel with a very fast speed (1 tick per update)
        final long maxWheelSlowSpeed = 10L; // Max slow speed reduced for a quicker slowdown
        final int fastSpinPhaseFrames = 50; // Number of frames for the fast spin phase (increased)
        long[] currentWheelDelay = {initialWheelSpeed}; // Mutable delay for the wheel
    
        Runnable spinTask = new Runnable() {
            @Override
            public void run() {
                if (frameCounter < totalSpinFrames) {
                    // Update wheel position based on frame count
                    updateWheel(frameCounter);
    
                    // Fast spin phase: maintain 1 tick delay for the first 50 frames
                    if (frameCounter < fastSpinPhaseFrames) {
                        currentWheelDelay[0] = 1L;
                    } else {
                        // Smooth and quicker slowdown
                        int framesSinceSlowPhaseStart = frameCounter - fastSpinPhaseFrames;
                        double slowPhaseProgress = Math.pow((double) framesSinceSlowPhaseStart / (totalSpinFrames - fastSpinPhaseFrames), 0.5);
    
                        // Increase delay from 1L to a maximum of 10L, but more quickly
                        currentWheelDelay[0] = (long) (1L + slowPhaseProgress * (maxWheelSlowSpeed - 1L));
                    }
    
                    System.out.println("Current delay: " + currentWheelDelay[0]);
    
                    // Schedule the next spin with the updated delay
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this, currentWheelDelay[0]);
    
                    frameCounter++;
                } else {
                    System.out.println("Spin ended");
                    // Ensure the wheel stops at 22
                }
            }
        };
    
        // Start the first task immediately
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, spinTask, 0L);
    
        // Start ball movement shortly after the wheel starts spinning
        Bukkit.getScheduler().runTaskLater(plugin, this::startBallMovement, 20L); // Delay by 20 ticks to match the wheel's fast spin phase
    }



private void startBettingTimer() {
    if (bettingCountdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
    }

    // Start the slow spin as soon as the betting phase begins
    startSlowSpinAnimation(10L); // Start the wheel spinning at 10 ticks per frame

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

                // Update the timer item in the appropriate slot based on the current quadrant
                int countdownSlot = getCountdownSlotForQuadrant(currentQuadrant);
                ItemStack countdownItem = createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", 1);
                inventory.setItem(countdownSlot, countdownItem);  // Update the item in the correct slot
                
                countdown--;
            } else {
                // When bets close, remove the menu buttons
                clearMenuButtonsForQuadrant(currentQuadrant);

                // Start ball movement after bets are closed
                handleBetClosure();
                Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                bettingCountdownTaskId = -1;
            }
        }
    }, 0L, 20L);
}


// Determine the correct slot for the countdown based on the quadrant
private int getCountdownSlotForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            return 45;
        case 2: // Top-left quadrant
            return 49;  // You can change this to match your design
        case 3: // Bottom-left quadrant
            return 4;
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
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 46);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 47);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 48);
            addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 49);
            break;
        case 2: // Top-left quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 52);
            addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 53);
            break;
        case 3: // Bottom-left quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 5);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 6);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 7);
            addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 8);
            break;
        case 4: // Bottom-right quadrant
            addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 1);
            addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 2);
            addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 3);
            addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 4);
            break;
    }
}

// Remove the menu buttons when bets are closed
private void clearMenuButtonsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            clearSlots(50, 53);
            break;
        case 2: // Top-left quadrant
            clearSlots(49, 52);
            break;
        case 3: // Bottom-left quadrant
            clearSlots(4, 7);
            break;
        case 4: // Bottom-right quadrant
            clearSlots(0, 3);
            break;
    }
}

    // Clear a range of slots
private void clearSlots(int fromSlot, int toSlot) {
    for (int i = fromSlot; i <= toSlot; i++) {
        inventory.setItem(i, null);  // Set the slot to null to clear it
    }
}



    /**
     * Start the slow wheel spin animation when the inventory first opens.
     */
    private void startSlowSpinAnimation(long initialSpeed) {
        frameCounter = 0;
    
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (betsClosed) {
                    Bukkit.getScheduler().cancelTask(spinTaskId);  // Stop slow spin when bets are closed
                } else {
                    updateWheelView(initialSpeed);
                    frameCounter++;
                }
            }
        }, 0L, initialSpeed);  // Initial slow speed
    }
    
    /**
     * Update the wheel view with the current frame. This is for both slow and fast spins.
     */
    private void updateWheelView(long speed) {
        int currentOffset = (wheelOffset + frameCounter) % wheelLayout.size();
        updateQuadrantDisplay(currentOffset);
    }
    
   
    

    /**
     * Start the ball movement when the wheel speed reaches 4 ticks per frame.
     */
    private void startBallMovement() {
        ballPosition = 8;
        currentQuadrant = 1;
        
        final long initialBallSpeed = 5L;  // Ball starts at a moderate speed
        long[] currentBallDelay = {initialBallSpeed};
    
        ballTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                updateBallPosition();
                
                // Gradually slow the ball
                if (currentBallDelay[0] < 40L) {
                    currentBallDelay[0] += 2L;  // Increase delay for ball movement
                }
            }
        }, 0L, currentBallDelay[0]);
    }
    
    
    private void updateWheel(int frame) {
        // Calculate the current global offset
        int currentOffset = (wheelOffset + frame) % wheelLayout.size();
    
        // Update all quadrants to reflect the global wheel position
        updateQuadrantDisplay(currentOffset);
    }
    
    private void updateQuadrantDisplay(int globalOffset) {
        Map<Integer, int[]> currentExtraSlotsMap = getCurrentExtraSlotsMap();
    
        for (Map.Entry<Integer, int[]> entry : currentExtraSlotsMap.entrySet()) {
            int mainSlot = entry.getKey();
            int[] extras = entry.getValue();
    
            // Calculate the wheel position based on the global offset
            int wheelPosition = (globalOffset + getMainSlotIndex(mainSlot)) % wheelLayout.size();
            int number = wheelLayout.get(wheelPosition);
    
            // Assign the main slot and extra slots
            ItemStack mainItem = createCustomItem(getMaterialForNumber(number), "Number: " + number, (number == 0) ? 1 : number);
            inventory.setItem(mainSlot, mainItem);
    
            // Set unique numbers for the extra slots
            ItemStack extraItem = createCustomItem(getMaterialForNumber(number), "Number: " + number, 1);
            for (int extraSlot : extras) {
                inventory.setItem(extraSlot, extraItem);
            }
        }
    }
    


    


private void switchQuadrant() {
    // Cycle between quadrants: 1 -> 2 -> 3 -> 4 (top-right -> top-left -> bottom-left -> bottom-right)
    currentQuadrant = (currentQuadrant % 4) + 1;

    initializeDecorativeSlotsForQuadrant(currentQuadrant);

    switch (currentQuadrant) {
        case 1:
            ballPosition = 8; // Top-right quadrant starts from slot 8
            break;
        case 2:
            ballPosition = 17; // Top-left quadrant starts from slot 17
            break;
        case 3:
            ballPosition = 45; // Bottom-left quadrant starts from slot 45
            break;
        case 4:
            ballPosition = 45; // Bottom-right quadrant starts from slot 45
            break;
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


private void updateBallPosition() {
    // Switch to the new quadrant if the ball reaches the boundary
    if (isQuadrantBoundary(ballPosition)) {
        switchQuadrant(); // This will also refresh the decorations and numbers for the new quadrant
    }

    switch (currentQuadrant) {
        case 1: // Top-right quadrant
            if (ballPosition > 0) {
                ballPosition--;
            }
            break;
        case 2: // Top-left quadrant
            if (ballPosition > 9) {
                ballPosition--;
            }
            break;
        case 3: // Bottom-left quadrant
            if (ballPosition < 53) {
                ballPosition++;
            }
            break;
        case 4: // Bottom-right quadrant
            if (ballPosition < 53) {
                ballPosition++;
            }
            break;
    }

    updateInventoryWithBall(); // Move the ball and restore the previous decoration
}


private boolean isQuadrantBoundary(int ballPosition) {
    switch (currentQuadrant) {
        case 1: return ballPosition == 0; // Boundary for top-right quadrant
        case 2: return ballPosition == 9; // Boundary for top-left quadrant
        case 3: return ballPosition == 53; // Boundary for bottom-left quadrant
        case 4: return ballPosition == 53; // Boundary for bottom-right quadrant
        default: return false;
    }
}

private void updateInventoryWithBall() {
    // Clear previous ball position
    for (int i = 0; i < 54; i++) {
        ItemStack item = inventory.getItem(i);
        if (item != null && item.getType() == Material.HEART_OF_THE_SEA) {
            // Restore the original decorative slot (green/brown stained glass)
            restoreDecorativeSlot(i);
        }
    }

    // Place ball in new position
    ItemStack ball = new ItemStack(Material.HEART_OF_THE_SEA);
    ItemMeta meta = ball.getItemMeta();
    meta.setDisplayName("Ball");
    ball.setItemMeta(meta);
    inventory.setItem(ballPosition, ball);
}

// Restore the appropriate decorative slot (green or brown glass pane)
private void restoreDecorativeSlot(int slot) {
    Material material = getDecorativeMaterialForSlot(slot);
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(material == Material.BROWN_STAINED_GLASS_PANE ? "Outer Rim" : "Inside Rim");
    item.setItemMeta(meta);
    inventory.setItem(slot, item);
}

private void initializeDecorativeSlots() {
 
        // Regular coloring for Quadrant 1 (subsequent updates)
        fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 26}, Material.BROWN_STAINED_GLASS_PANE);
        fillDecorativeSlots(new int[]{36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 48, 49, 50, 51, 52}, Material.GREEN_STAINED_GLASS_PANE);

    
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

private Material getDecorativeMaterialForSlot(int slot) {
    // Adjusted logic for bottom-left and bottom-right quadrants to ensure the correct color trail
    if (isInRange(slot, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15}) || 
        isInRange(slot, new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53})) {
        return Material.BROWN_STAINED_GLASS_PANE;
    } else if (isInRange(slot, new int[]{36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 48, 49, 50, 51, 52})) {
        return Material.GREEN_STAINED_GLASS_PANE;
    }

    // Default to brown if no match
    return Material.BROWN_STAINED_GLASS_PANE;
}

private boolean isInRange(int slot, int[] validSlots) {
    for (int validSlot : validSlots) {
        if (slot == validSlot) return true;
    }
    return false;
}


    private Map<Integer, int[]> getCurrentExtraSlotsMap() {
        switch (currentQuadrant) {
            case 1: return extraSlotsMapTopRight;
            case 2: return extraSlotsMapTopLeft;
            case 3: return extraSlotsMapBottomLeft;
            case 4: return extraSlotsMapBottomRight;
            default: return extraSlotsMapTopRight;
        }
    }

   
    private void displayQuadrant(int quadrantIndex) {
        int[] quadrantSlots;
        int startPosition;
    
        // Define slot ranges for each quadrant
        switch (quadrantIndex) {
            case 0: // Top-Right Quadrant
                quadrantSlots = new int[]{8, 7, 6, 5, 4, 3, 2, 1, 0}; 
                startPosition = (wheelOffset + 27) % 37; // Start from the 27th global position
                break;
            case 1: // Top-Left Quadrant
                quadrantSlots = new int[]{17, 16, 15, 14, 13, 12, 11, 10, 9};
                startPosition = (wheelOffset + 18) % 37; // Start from the 18th global position
                break;
            case 2: // Bottom-Left Quadrant
                quadrantSlots = new int[]{26, 25, 24, 23, 22, 21, 20, 19, 18};
                startPosition = (wheelOffset + 9) % 37; // Start from the 9th global position
                break;
            case 3: // Bottom-Right Quadrant
                quadrantSlots = new int[]{35, 34, 33, 32, 31, 30, 29, 28, 27};
                startPosition = wheelOffset; // Start from the 0th global position
                break;
            default:
                throw new IllegalArgumentException("Invalid quadrant index");
        }
    
        // Loop through each slot in the quadrant and assign a unique number
        for (int i = 0; i < quadrantSlots.length; i++) {
            int wheelPosition = (startPosition + i) % 37; // Ensure the position wraps around if needed
            int number = wheelLayout.get(wheelPosition); // Get the number from the global wheel layout
    
            // Create the item with the correct number and place it in the quadrant slot
            ItemStack item = createCustomItem(getMaterialForNumber(number), "Number: " + number, (number == 0) ? 1 : number);
            inventory.setItem(quadrantSlots[i], item);
    
            // Handle the extra slots associated with the main number slot
            handleExtraSlots(quadrantSlots[i], number, quadrantIndex);
        }
    }
    
    private void handleExtraSlots(int mainSlot, int number, int quadrantIndex) {
        Map<Integer, int[]> currentExtraSlotsMap = getExtraSlotsMapForQuadrant(quadrantIndex);
        
        if (currentExtraSlotsMap.containsKey(mainSlot)) {
            int[] extraSlots = currentExtraSlotsMap.get(mainSlot);
            ItemStack extraItem = createCustomItem(getMaterialForNumber(number), "Number: " + number, 1);
            
            for (int extraSlot : extraSlots) {
                inventory.setItem(extraSlot, extraItem);
            }
        }
    }
    
    private Map<Integer, int[]> getExtraSlotsMapForQuadrant(int quadrantIndex) {
        switch (quadrantIndex) {
            case 1: return extraSlotsMapTopRight;
            case 2: return extraSlotsMapTopLeft;
            case 3: return extraSlotsMapBottomLeft;
            case 4: return extraSlotsMapBottomRight;
            default:
                throw new IllegalArgumentException("Invalid quadrant index: " + quadrantIndex);
        }
    }
    
    
    



    private int getMainSlotIndex(int mainSlot) {
        return wheelLayout.indexOf(getNumberForSlot(mainSlot, currentQuadrant));
    }

    private int getNumberForSlot(int mainSlot, int quadrant) {
        // Get the extra slot mapping for the current quadrant
        Map<Integer, int[]> currentExtraSlotsMap = getExtraSlotsMapForQuadrant(quadrant);
    
        if (currentExtraSlotsMap.containsKey(mainSlot)) {
            // Calculate the wheel position based on the main slot and current quadrant
            int mainSlotIndex = currentExtraSlotsMap.keySet().stream().toList().indexOf(mainSlot);
            
            if (mainSlotIndex != -1) {
                // Calculate the offset for the wheel layout
                int wheelPosition = (wheelOffset + mainSlotIndex) % wheelLayout.size();
                return wheelLayout.get(wheelPosition);
            }
        }
        return 0; // Default fallback in case the slot doesn't match
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
