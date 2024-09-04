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

            Tables.remove(player);
            removeAllBets(player.getUniqueId());

            if (Tables.isEmpty()) {
                resetToStartState();
            }
        } else if (slot == 50) {
            if (bettingTimeSeconds > 5)
                bettingTimeSeconds--;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            plugin.saveConfig();
            updateTimerItems();
        } else if (slot == 51) {
            if (bettingTimeSeconds < 64) bettingTimeSeconds++;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            plugin.saveConfig();
            updateTimerItems();
        }
    }

    private void updateTimerItems() {
        addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
        addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
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

    private void startBettingTimer() {
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }

        inventory.clear();

        // Initialize menu items
        addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
        addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 52);
        addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 53);

        betsClosed = false;
        bettingCountdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int countdown = bettingTimeSeconds;

            @Override
            public void run() {
                if (countdown > 0) {
                    for (BettingTable bettingTable : Tables.values()) {
                        bettingTable.updateCountdown(countdown, betsClosed);
                    }
                    addItem(createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", 1), 45);
                    countdown--;
                } else {
                    handleBetClosure();
                    Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                    bettingCountdownTaskId = -1;
                }
            }
        }, 0L, 20L);
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

            // Update to spinning ball and wheel
            startSpinAnimation(activePlayers);
        }
    }

    private void startSpinAnimation(List<Player> activePlayers) {
        frameCounter = 0;

        final int fastSpinDuration = 60; 
        final int gradualSlowDuration = 180;
        final int extendedSlowSpins = 80;
        final long fastSpeed = 1L;
        final long slowSpeed = 15L;
        
        spinTaskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateWheel(frameCounter);
                frameCounter++;
        
                if (frameCounter == 60) {
                    startBallMovement();
                }
        
                if (frameCounter < fastSpinDuration) {
                } else if (frameCounter < fastSpinDuration + gradualSlowDuration) {
                    long newDelay;
                    
                    if (frameCounter < fastSpinDuration + gradualSlowDuration * 0.3) {
                        newDelay = 2L;
                    } else {
                        newDelay = (long) Math.min(slowSpeed, 2L + (frameCounter - fastSpinDuration - gradualSlowDuration * 0.3) / 25);
                    }

                    Bukkit.getScheduler().cancelTask(spinTaskId);
                    spinTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this, newDelay, newDelay).getTaskId();
                } else if (frameCounter < fastSpinDuration + gradualSlowDuration + extendedSlowSpins) {
                } else {
                    Bukkit.getScheduler().cancelTask(spinTaskId);
                }
            }
        }, 0L, fastSpeed).getTaskId();
    }

    private void updateWheel(int frame) {
        final int currentOffset = (wheelOffset - frame + wheelLayout.size()) % wheelLayout.size();
        
        Map<Integer, int[]> currentExtraSlotsMap = getCurrentExtraSlotsMap();
    
        for (Map.Entry<Integer, int[]> entry : currentExtraSlotsMap.entrySet()) {
            int mainSlot = entry.getKey();
            int[] extras = entry.getValue();
    
            // Ensure each mainSlot has a unique number
            int wheelPosition = (currentOffset + getMainSlotIndex(mainSlot) + wheelLayout.size()) % wheelLayout.size();
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
    

    private void startBallMovement() {
        ballPosition = 8;
        currentQuadrant = 1;
        
        ballTaskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateBallPosition();
                
                if (isQuadrantBoundary(ballPosition)) {
                    switchQuadrant();
                }
            }
        }, 20L, 5L).getTaskId();
    }
    private void switchQuadrant() {
        currentQuadrant = (currentQuadrant % 4) + 1; // Cycle between 1 and 4 (quadrants)
        
        switch (currentQuadrant) {
            case 1:
                openTopRightQuadrant();
                break;
            case 2:
                openTopLeftQuadrant();
                break;
            case 3:
                openBottomLeftQuadrant();
                break;
            case 4:
                openBottomRightQuadrant();
                break;
            default:
                throw new IllegalStateException("Invalid quadrant: " + currentQuadrant);
        }
    }
    
    
    private void updateBallPosition() {
        if (currentQuadrant == 1) {
            if (ballPosition > 0) {
                ballPosition--;
            } else {
                switchQuadrant();
            }
        } else if (currentQuadrant == 2) {
            if (ballPosition > 9) {
                ballPosition--;
            } else {
                switchQuadrant();
            }
        } else if (currentQuadrant == 3) {
            if (ballPosition < 44) {
                ballPosition++;
            } else {
                switchQuadrant();
            }
        } else if (currentQuadrant == 4) {
            if (ballPosition < 53) {
                ballPosition++;
            } else {
                ballPosition = 8;
                switchQuadrant();
            }
        }
    
        updateInventoryWithBall();
    }

    private boolean isQuadrantBoundary(int ballPosition) {
        if (currentQuadrant == 1 && ballPosition == 0) {
            return true;
        } else if (currentQuadrant == 2 && ballPosition == 9) {
            return true;
        } else if (currentQuadrant == 3 && ballPosition == 45) {
            return true;
        } else if (currentQuadrant == 4 && ballPosition == 53) {
            return true;
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

    private void openTopRightQuadrant() {
        displayQuadrant(0);
    }
    
    private void openTopLeftQuadrant() {
        displayQuadrant(1);
    }
    
    private void openBottomLeftQuadrant() {
        displayQuadrant(2);
    }
    
    private void openBottomRightQuadrant() {
        displayQuadrant(3);
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
            case 0: return extraSlotsMapTopRight;
            case 1: return extraSlotsMapTopLeft;
            case 2: return extraSlotsMapBottomLeft;
            case 3: return extraSlotsMapBottomRight;
            default: throw new IllegalArgumentException("Invalid quadrant index");
        }
    }
    
    

    private void updateInventoryWithBall() {
        for (int i = 0; i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.SNOWBALL) {
                inventory.setItem(i, null);
            }
        }
    
        ItemStack ball = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = ball.getItemMeta();
        meta.setDisplayName("Ball");
        ball.setItemMeta(meta);
        inventory.setItem(ballPosition, ball);
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
            return Material.GREEN_STAINED_GLASS_PANE;
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
