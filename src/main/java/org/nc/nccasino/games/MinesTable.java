package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.*;
import java.util.stream.Collectors;

public class MinesTable implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final UUID playerId;
    private final Player player;
    private final UUID dealerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final MinesInventory minesInventory;
    private final Map<Player, Integer> animationTasks;
    private final Map<String, Double> chipValues;
    private final Map<Player, Boolean> animationCompleted;
    private Boolean clickAllowed = true;
    private double selectedWager;
    private final Map<UUID, Double> currentBets = new HashMap<>();
    private Boolean closeFlag = false;

    // Game state management
    private enum GameState {
        PLACING_WAGER,
        WAITING_TO_START,
        PLAYING,
        GAME_OVER
    }

    private GameState gameState;
    private int gridSize = 5;
    private int totalTiles = gridSize * gridSize;
    private int minesCount = 3; // default number of mines is 3
    private boolean[][] mineGrid;      // [5][5]
    private boolean[][] revealedGrid;  // [5][5]
    private int safePicks;
    private boolean gameOver;
    private boolean wagerPlaced = false;
    private boolean minesSelected = true; // Default to true since default minesCount is set
    private double wager;
    private double previousWager = 0.0; // New field to store previous wager
    private boolean[][] fireGrid; // [6][9] To track which tiles are on fire
    private final List<Integer> scheduledTasks = new ArrayList<>();
    private boolean rebetEnabled = true; 

    // Adjusted fields for grid mapping
    private final int[] gridSlots = {
        2, 3, 4, 5, 6,
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24,
        29, 30, 31, 32, 33,
        38, 39, 40, 41, 42
    };
    private final List<Integer> gridSlotList = Arrays.stream(gridSlots).boxed().collect(Collectors.toList());

    // Field to keep track of selected mine count slot
    private int selectedMineSlot = -1;

    public MinesTable(Player player, Villager dealer, Nccasino plugin, String internalName, MinesInventory minesInventory) {
        this.playerId = player.getUniqueId();
        this.player = player;
        this.dealerId = dealer.getUniqueId();
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.minesInventory = minesInventory;
        this.inventory = Bukkit.createInventory(this, 54, "Mines");
        this.chipValues = new LinkedHashMap<>();
        this.animationTasks = new HashMap<>();
        this.animationCompleted = new HashMap<>();

        // Initialize game state
        this.gameState = GameState.PLACING_WAGER;
        this.safePicks = 0;
        this.gameOver = false;

        loadChipValuesFromConfig();

        // Start the animation first, then return to this table once animation completes
        startAnimation(player);

        registerListener();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void startAnimation(Player player) {
        // Retrieve the animation message from the config for the current dealer
        String animationMessage = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        // Delaying the animation inventory opening to ensure it displays properly
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Pass the animation message from the config
            AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
            player.openInventory(animationTable.getInventory());

            // Start animation and pass a callback to return to MinesTable after animation completes
            animationTable.animateMessage(player, this::afterAnimationComplete);
        }, 1L); // Delay by 1 tick to ensure smooth opening of inventory
    }

    private void afterAnimationComplete() {
        // Add a slight delay to ensure smooth transition from the animation to the table
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeFlag = true;
            initializeTable();
            if (player != null) {
                player.openInventory(inventory);
                // Inform the player about the default number of mines
                player.sendMessage("# of mines: " + minesCount + ".");
            }
        }, 1L); // Delay by 1 tick to ensure clean transition between inventories
    }

    private void loadChipValuesFromConfig() {
        // Load chip values from the config
        Map<String, Double> tempChipValues = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String chipName = plugin.getChipName(internalName, i);
            double chipValue = plugin.getChipValue(internalName, i);
            if (chipValue > 0) {
                tempChipValues.put(chipName, chipValue);
            }
        }
        // Sort chip values in ascending order
        tempChipValues.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEachOrdered(entry -> chipValues.put(entry.getKey(), entry.getValue()));
    }

    private void initializeTable() {
        inventory.clear();

        if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
            initializeWagerPlacement();
        } else if (gameState == GameState.PLAYING) {
            initializeGameGrid();
        }
    }

    private void initializeWagerPlacement() {
        inventory.clear();
    
        // Set the rainbow sides (red, orange, yellow, lime, blue)
        setRainbowBorders();
    
        // Add wager options (chips)
        addWagerOptions();
    
        // Add mine count selection options
        addMineSelectionOptions();
    
        // Fill remaining slots with placeholders
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createCustomItem(Material.GRAY_STAINED_GLASS_PANE, " ", 1));
            }
        }
        updateRebetToggle();

        // Add "Start Game" lever if mines are selected (which they are by default)
        if (minesSelected) {
            updateStartGameLever(true);
        } else {
            updateStartGameLever(false);
        }
    
        // Add undo buttons
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Undo Last Bet", 1));
    }
    
   // Method to toggle rebet on/off
   private void updateRebetToggle() {
    Material material = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
    String name = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
    inventory.setItem(44, createCustomItem(material, name, 1));  // Slot 48
}

    // Method to set rainbow border colors
    private void setRainbowBorders() {
        // Red Glass Pane for slots 0-1, 7-8
        setGlassPane(Material.RED_STAINED_GLASS_PANE, new int[]{0, 1, 7, 8});
    
        // Orange Glass Pane for slots 9-10, 16-17
        setGlassPane(Material.ORANGE_STAINED_GLASS_PANE, new int[]{9, 10, 16, 17});
    
        // Yellow Glass Pane for slots 18-19, 25-26
        setGlassPane(Material.YELLOW_STAINED_GLASS_PANE, new int[]{18, 19, 25, 26});
    
        // Lime Glass Pane for slots 27-28, 34-35
        setGlassPane(Material.LIME_STAINED_GLASS_PANE, new int[]{27, 28, 34, 35});
    
        // Blue Glass Pane for slots 36-37, 43-44
        setGlassPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, new int[]{36, 37, 43, 44});
    }
    
    // Utility method to place stained glass panes in specific slots
    private void setGlassPane(Material material, int[] slots) {
        for (int slot : slots) {
            inventory.setItem(slot, createCustomItem(material, " ", 1));
        }
    }

    private void addWagerOptions() {
        // Add sorted currency chips (slots 47-51)
        int slot = 47;
        for (Map.Entry<String, Double> entry : chipValues.entrySet()) {
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            slot++;
        }

        // Add a single betting option - Paper labeled "Click here to place bet" in slot 52
        inventory.setItem(52, createCustomItem(Material.PAPER, "Click here to place bet", 1));

        // If there is a current bet, update the lore
        double currentBet = currentBets.getOrDefault(player.getUniqueId(), 0.0);
        if (currentBet > 0) {
            updateBetLore(52, currentBet);
        }
    }

    private void addMineSelectionOptions() {
        // Middle 5 columns (columns 2-6), top 5 rows (rows 0-4)
        int[] slots = {
            2, 3, 4, 5, 6,
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
        };

        int mineCountOption = 1;
        for (int slot : slots) {
            if (slot == 22) {
                // Place instruction item in slot 22
                ItemStack instructions = createCustomItem(Material.BOOK, "Select Number of Mines", 1);
                inventory.setItem(slot, instructions);
                continue;
            }
            if (mineCountOption <= 24) {
                if (mineCountOption == minesCount) {
                    // Selected mine count, show stack of red glass panes
                    ItemStack selectedMineOption = createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "Mines: " + mineCountOption, mineCountOption);
                    inventory.setItem(slot, selectedMineOption);
                    selectedMineSlot = slot;
                } else {
                    ItemStack mineOption = createCustomItem(Material.TNT, "Mines: " + mineCountOption, mineCountOption);
                    inventory.setItem(slot, mineOption);
                }
                mineCountOption++;
            }
        }
    }

    private void initializeGameGrid() {
        inventory.clear();
        setRainbowBorders();
        setGlassPane(Material.PURPLE_STAINED_GLASS_PANE, new int[]{45,46,47,48,50,51,52,53});
        for (int i = 0; i < gridSlots.length; i++) {
            int slot = gridSlots[i];
            ItemStack tile = createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Hidden", 1);
            inventory.setItem(slot, tile);
        }

        // Add cash out button with potential winnings in lore
        ItemStack cashOutButton = createCustomItem(Material.EMERALD, "Cash Out", 1);
        updateCashOutLore(cashOutButton);
        inventory.setItem(49, cashOutButton);
    }

    private void updateCashOutLore(ItemStack cashOutButton) {
        double payoutMultiplier = calculatePayoutMultiplier(safePicks);
        double potentialWinnings = wager * payoutMultiplier;
        potentialWinnings = Math.round(potentialWinnings * 100.0) / 100.0;

        ItemMeta meta = cashOutButton.getItemMeta();
        if (meta != null) {
            meta.setLore(Collections.singletonList("Potential Winnings: " + potentialWinnings));
            cashOutButton.setItemMeta(meta);
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinesTable)) return;
        event.setCancelled(true);  // Prevent default click actions

        if (!event.getWhoClicked().getUniqueId().equals(playerId)) return;

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();

        if (event.getClickedInventory() != inventory) return;

        // Handle fast click prevention
        if (!clickAllowed) {
            player.sendMessage("Please wait before clicking again!");
            return;
        }

        clickAllowed = false;
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed = true, 5L);  // 5 ticks delay

        if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
            handleWagerPlacement(clickedItem, slot);
        } else if (gameState == GameState.PLAYING) {
            // Adjusted to handle grid slots
            int index = gridSlotList.indexOf(slot);
            if (index != -1) {
                int x = index % gridSize;
                int y = index / gridSize;
                handleTileSelection(x, y);
            } else if (clickedItem != null && clickedItem.getType() == Material.EMERALD) {
                cashOut();
            }
        }

        // Handle rebet toggle
        if (slot == 44) {
            rebetEnabled = !rebetEnabled;
            player.sendMessage(rebetEnabled ? "Rebet is now ON." : "Rebet is now OFF.");
            updateRebetToggle();
        }
    }

    private void handleWagerPlacement(ItemStack clickedItem, int slot) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();

        if (slot >= 47 && slot <= 51) {
            // Handle currency chips (slots 47-51)
            selectedWager = chipValues.getOrDefault(itemName, 0.0);
            player.sendMessage("Selected wager: " + selectedWager + " " + plugin.getCurrencyName(internalName));
            return;
        }

        if (slot == 52) {
            // Handle bet placement (slot 52 - Paper item)
            if (selectedWager > 0) {
                // Check if the player has enough currency to place the bet
                if (hasEnoughCurrency(player, (int) selectedWager)) {
                    double newBetAmount = currentBets.getOrDefault(player.getUniqueId(), 0.0) + selectedWager;
                    currentBets.put(player.getUniqueId(), newBetAmount);

                    // Deduct currency from player inventory
                    removeWagerFromInventory(player, (int) selectedWager);

                    // Update the lore of the item in slot 52 with the cumulative bet amount
                    updateBetLore(52, newBetAmount);

                    player.sendMessage("Bet placed: " + newBetAmount);

                    wager = newBetAmount;
                    wagerPlaced = true;

                    // Update "Start Game" lever visibility
                    updateStartGameLever(true);
                } else {
                    player.sendMessage("Not enough " + plugin.getCurrencyName(internalName) + "s.");
                }
            } else {
                player.sendMessage("Select a wager amount first.");
            }
            return;
        }

        if (itemName.startsWith("Mines: ")) {
            // Handle mine selection
            String[] parts = itemName.split(": ");
            if (parts.length == 2) {
                try {
                    int selectedMines = Integer.parseInt(parts[1]);
                    if (selectedMines >= 1 && selectedMines <= (totalTiles - 1)) {
                        // Update the previous selected mine count slot back to default
                        if (selectedMineSlot != -1 && selectedMineSlot != slot) {
                            // Reset previous selection
                            int prevMineCountOption = minesCount; // Previous selected mines count
                            ItemStack prevMineOption = createCustomItem(Material.TNT, "Mines: " + prevMineCountOption, prevMineCountOption);
                            inventory.setItem(selectedMineSlot, prevMineOption);
                        }

                        // Update the new selected slot
                        minesCount = selectedMines;
                        player.sendMessage("Mines selected: " + minesCount);
                        minesSelected = true;

                        // Update the selected mine slot
                        selectedMineSlot = slot;

                        // Change the selected slot to stack of red glass panes
                        ItemStack selectedMineOption = createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "Mines: " + minesCount, minesCount);
                        inventory.setItem(slot, selectedMineOption);

                        // Update "Start Game" lever visibility
                        updateStartGameLever(true);
                    } else {
                        player.sendMessage("Invalid number of mines.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("Error parsing number of mines.");
                }
            }
            return;
        }

        if (itemName.equals("Start Game") && slot == 53) {
            // Start the game
            if (minesSelected) {
                if (wager > 0) {
                    startGame();
                } else {
                    player.sendMessage("Place a wager first.");
                }
            } else {
                player.sendMessage("Select number of mines.");
            }
            return;
        }

        // Undo all bets
        if (slot == 45) {
            player.sendMessage("All bets undone.");
            refundAllBets(player);
            currentBets.remove(player.getUniqueId());
            updateBetLore(52, 0);  // Reset the lore on the bet option after clearing bets
            wager = 0;
            wagerPlaced = false;
            return;
        }

        // Undo last bet
        if (slot == 46) {
            if (currentBets.containsKey(player.getUniqueId()) && selectedWager > 0) {
                double newBetAmount = currentBets.get(player.getUniqueId()) - selectedWager;
                if (newBetAmount < 0) newBetAmount = 0;
                currentBets.put(player.getUniqueId(), newBetAmount);

                refundBet(player, (int) selectedWager);
                updateBetLore(52, newBetAmount);
                wager = newBetAmount;
                wagerPlaced = newBetAmount > 0;

                player.sendMessage("Last bet undone. Total bet: " + newBetAmount);
            } else {
                player.sendMessage("No bets to undo.");
            }
            return;
        }
    }

    private void startGame() {
        this.mineGrid = new boolean[gridSize][gridSize];       // [5][5]
        this.revealedGrid = new boolean[gridSize][gridSize];   // [5][5]
        this.safePicks = 0;
        this.gameState = GameState.PLAYING;
        this.gameOver = false;
        this.fireGrid = new boolean[6][9];  // [6 rows][9 columns] for the full inventory grid

        // Place mines randomly
        placeMines();

        // Initialize the game grid in the inventory
        initializeTable(); // This will call initializeGameGrid

        // Send message to the player indicating the game has started with the wager amount
        player.sendMessage(wager + " $ game started");

        // Set previous wager
        previousWager = wager;
    }

    private void placeMines() {
        Random random = new Random();
        int minesPlaced = 0;

        while (minesPlaced < minesCount) {
            int x = random.nextInt(gridSize);
            int y = random.nextInt(gridSize);
            if (!mineGrid[x][y]) {
                mineGrid[x][y] = true;
                minesPlaced++;
            }
        }
    }

   private void handleTileSelection(int x, int y) {
    if (gameOver) {
        player.sendMessage("Game over.");
        return;
    }

    if (revealedGrid[x][y]) {
        player.sendMessage("Tile already revealed.");
        return;
    }

    if (mineGrid[x][y]) {
   // Change the cash-out button to a barrier immediately
   updateCashOutToBarrier();
    
   // Start the new animation sequence
   startMineHitAnimation(x, y); // Pass the coordinates of the mine hit

   // Trigger a visual explosion at the player's location without causing damage
   player.getWorld().createExplosion(player.getLocation(), 0F, false, false); // Creates explosion, no damage or block break

   gameOver = true;
   gameState = GameState.GAME_OVER;
   player.sendMessage("You hit a mine!");
    } else {
        // Safe tile clicked
        revealedGrid[x][y] = true;
        safePicks++;
        updateTile(x, y, false);  // Reveal a safe tile

        // Optionally update cash out button with updated potential winnings
        updateCashOutLore(inventory.getItem(49));

        player.sendMessage("Safe pick!");

        // Optionally check if the game is won (all safe tiles cleared)
        if (safePicks == (totalTiles - minesCount)) {
            player.sendMessage("All safe tiles cleared!");
            cashOut();  // End game and cash out the player
        }
    }
}

    
    private void updateCashOutToBarrier() {
        ItemStack barrierItem = createCustomItem(Material.BARRIER, "Game Over", 1);
        inventory.setItem(49, barrierItem);
    }
    
    private void updateTile(int x, int y, boolean isMine) {
        int index = y * gridSize + x;
        int slot = gridSlots[index];
        ItemStack tile;
        if (isMine) {
            tile = createCustomItem(Material.TNT, "Mine", 1); 
        } else {
            tile = createCustomItem(Material.EMERALD, "Safe", 1);
        }
        inventory.setItem(slot, tile);
    }

    private void startMineHitAnimation(int mineX, int mineY) {
        // Convert mineX and mineY to inventory coordinates
        int centerSlot = gridSlots[mineY * gridSize + mineX];
        int centerX = centerSlot % 9;
        int centerY = centerSlot / 9;

        // Step 1: After a short delay, reveal all tiles
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            revealAllTiles();

            // Step 2: After another short delay, change the clicked mine tile to fire
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                setTileToFire(centerX, centerY);

                // Step 3: Start spreading the fire from the hit mine
                spreadFireFrom(centerX, centerY, 1);

            }, 10L); // Reduced delay for faster fire spread (0.25 seconds)
        }, 5L); // Reduced delay before revealing all tiles (0.25 seconds)
    }

    private void setTileToFire(int x, int y) {
        int index = y * 9 + x;
        if (index >= 0 && index < inventory.getSize()) {
            setTileAtSlot(index, Material.BLAZE_POWDER, "Burning");
            fireGrid[y][x] = true; // Mark the tile as on fire
    
            // After a quick delay, turn the tile into smoke (wind charge/bone meal)
            BukkitTask smokeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                setTileAtSlot(index, Material.BONE_MEAL, "Smoke");
    
                // After a slower delay, turn the tile into air (empty)
                BukkitTask airTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    setTileAtSlot(index, Material.AIR, "");
                }, 20L); // Smoke lingers for 1 second (20 ticks) before disappearing
                scheduledTasks.add(airTask.getTaskId()); // Add air task ID to the list
    
            }, 5L); // Smoke appears after 0.25 seconds
            scheduledTasks.add(smokeTask.getTaskId()); // Add smoke task ID to the list
        }
    }
    

    private void setTileAtSlot(int slot, Material material, String name) {
        ItemStack tile = createCustomItem(material, name, 1);
        inventory.setItem(slot, tile);
    }

    private void spreadFireFrom(int centerX, int centerY, int currentRadius) {
        // Fire spreads quickly with minimal delay
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean anyTilesSetOnFire = false;
    
            for (int y = 0; y < 6; y++) { // 6 rows
                for (int x = 0; x < 9; x++) { // 9 columns
                    int distance = Math.max(Math.abs(centerX - x), Math.abs(centerY - y));
                    if (distance == currentRadius && !fireGrid[y][x]) { // If tile is not on fire
                        int index = y * 9 + x;
                        if (index >= 0 && index < inventory.getSize()) {
                            // Set the tile to fire
                            setTileAtSlot(index, Material.BLAZE_POWDER, "Burning");
                            fireGrid[y][x] = true; // Mark as on fire
                            anyTilesSetOnFire = true;
    
                            // After a quick delay, turn the tile into smoke (wind charge/bone meal)
                            BukkitTask smokeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                setTileAtSlot(index, Material.BONE_MEAL, "Smoke");
    
                                // After a slower delay, turn the tile into air (empty)
                                BukkitTask airTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    setTileAtSlot(index, Material.AIR, "");
                                }, 20L); // Smoke lingers for 1 second (20 ticks) before disappearing
                                scheduledTasks.add(airTask.getTaskId()); // Add air task ID to the list
    
                            }, 5L); // Smoke appears after 0.25 seconds
                            scheduledTasks.add(smokeTask.getTaskId()); // Add smoke task ID to the list
                        }
                    }
                }
            }
    
            if (anyTilesSetOnFire) {
                // Continue spreading fire to the next ring
                spreadFireFrom(centerX, centerY, currentRadius + 1);
            } else {
                // After all tiles are on fire and smoke fades, reset the game
                BukkitTask resetTask = Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 5L);
                scheduledTasks.add(resetTask.getTaskId()); // Add reset task ID to the list
            }
    
        }, 3L); // Fire spreads quickly (1 tick delay between rings)
        scheduledTasks.add(task.getTaskId()); // Add the task ID to the list
    }
    
    
    
    private void revealAllTiles() {
        for (int y = 0; y < gridSize; y++) { // 5 rows
            for (int x = 0; x < gridSize; x++) { // 5 columns
                if (!revealedGrid[x][y]) {
                    if (mineGrid[x][y]) {
                        updateTile(x, y, true);
                    } else {
                        updateTile(x, y, false);
                    }
                }
            }
        }
    }

    private void cashOut() {
        if (gameOver) {
            player.sendMessage("Game over.");
            return;
        }
    
        // Step 1: Reveal all tiles (mines and safes)
        revealAllTiles();
    
        // Step 2: Start emerald expansion from the cash-out button (slot 49)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startEmeraldExpansion(49); // Slot 49 is the cash-out button position
            player.sendMessage("Cashing out...");
    
            double winnings;
    
            // If no tiles were clicked, return the exact wager
            if (safePicks == 0) {
                winnings = wager; // No multiplier, return exact wager
            } else {
                // Give the winnings to the player with payout multiplier applied
                double payoutMultiplier = calculatePayoutMultiplier(safePicks);
                winnings = wager * payoutMultiplier;
            }
    
            winnings = Math.round(winnings * 100.0) / 100.0; // Round to 2 decimal places
            player.sendMessage("Cashed out: " + winnings);
    
            if (winnings > 0) {
                giveWinningsToPlayer(winnings);
            }
    
            gameOver = true;
            gameState = GameState.GAME_OVER;
    
            // Reset the game after a delay
            Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 20L); // Wait 5 seconds before resetting
    
        }, 10L); // Wait 2 seconds before starting the emerald expansion
    }
    
    

    private void closeCashOut() {
        if (gameOver) {
            player.sendMessage("Game over.");
            return;
        }
     
        // Step 2: Start emerald expansion from the cash-out button (slot 49)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Slot 49 is the cash-out button position
            player.sendMessage("Cashing out...");
            double winnings;
    
            // If no tiles were clicked, return the exact wager
            if (safePicks == 0) {
                winnings = wager; // No multiplier, return exact wager
            } else {
                // Give the winnings to the player with payout multiplier applied
                double payoutMultiplier = calculatePayoutMultiplier(safePicks);
                winnings = wager * payoutMultiplier;
            }
            winnings = Math.round(winnings * 100.0) / 100.0; // Round to 2 decimal places
            player.sendMessage("Cashed out: " + winnings);
    
            if (winnings > 0) {
                giveWinningsToPlayer(winnings);
            }
    
            gameOver = true;
            gameState = GameState.GAME_OVER;
    
            //check if properly closed?
        }, 10L); // Wait 2 seconds before starting the emerald expansion

       
    }

    // Step 2: Emerald expansion animation from the cash-out button (slot 49)
    private void startEmeraldExpansion(int centerSlot) {
        int centerX = centerSlot % 9;
        int centerY = centerSlot / 9;
        spreadEmeraldsFrom(centerX, centerY, 1); // Start with a radius of 1
    }
    
    private void spreadEmeraldsFrom(int centerX, int centerY, int currentRadius) {
        // Expansion of emeralds from the center
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean anyTilesSetToEmerald = false;
    
            for (int y = 0; y < 6; y++) { // 6 rows
                for (int x = 0; x < 9; x++) { // 9 columns
                    int distance = Math.max(Math.abs(centerX - x), Math.abs(centerY - y));
                    if (distance == currentRadius) {
                        int index = y * 9 + x;
                        if (index >= 0 && index < inventory.getSize()) {
                            // Overwrite every tile with an emerald
                            setTileAtSlot(index, Material.EMERALD, "MMMMMoney!!");
                            anyTilesSetToEmerald = true;
                        }
                    }
                }
            }
    
            if (anyTilesSetToEmerald) {
                // Continue expanding if any tiles were set to emerald
                spreadEmeraldsFrom(centerX, centerY, currentRadius + 1);
            }
    
        }, 3L); // Delay of 0.5 seconds between each expansion ring
    }
    
    
    private boolean isMineOrRevealed(int slot) {
        // Check if the slot is already revealed or is a mine
        int gridIndex = gridSlotList.indexOf(slot);
        if (gridIndex == -1) return false;
    
        int x = gridIndex % gridSize;
        int y = gridIndex / gridSize;
    
        return mineGrid[x][y] || revealedGrid[x][y]; // If it's a mine or already revealed, skip it
    }
    

    private void turnAllUnrevealedTilesGreen() {
        for (int y = 0; y < gridSize; y++) { // 5 rows
            for (int x = 0; x < gridSize; x++) { // 5 columns
                if (!revealedGrid[x][y]) {
                    updateTile(x, y, false);
                }
            }
        }
    }

    
    private void resetGame() {
        // Cancel ongoing tasks
        for (int taskId : scheduledTasks) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        scheduledTasks.clear();

        // Reset game variables
        this.gameOver = false;
        this.safePicks = 0;
        this.wagerPlaced = false;
        this.gameState = GameState.PLACING_WAGER;

        // Keep the number of mines the same
        player.sendMessage("# of mines: " + minesCount + ".");

        // Handle rebet logic
        if (rebetEnabled && previousWager > 0) {
            if (hasEnoughCurrency(player, (int) previousWager)) {
                removeWagerFromInventory(player, (int) previousWager);
                wager = previousWager;
                currentBets.put(player.getUniqueId(), wager);
                updateBetLore(52, wager);
                player.sendMessage("Rebet placed: " + wager);
                wagerPlaced = true;
            } else {
                player.sendMessage("Not enough currency for previous bet. Wager reset to 0.");
                wager = 0;
                currentBets.remove(player.getUniqueId());
                updateBetLore(52, wager);
                wagerPlaced = false;
            }
        } else {
            player.sendMessage("Rebet is off. Wager reset to 0.");
            wager = 0;
            currentBets.remove(player.getUniqueId());
            updateBetLore(52, wager);
            wagerPlaced = false;
        }

        // Re-initialize the table
        initializeTable();
    }
    

    private double calculatePayoutMultiplier(int picks) {
        double probability = 1.0;
        for (int i = 0; i < picks; i++) {
            probability *= (double) (totalTiles - minesCount - i) / (totalTiles - i);
        }
    
        // Calculate a dynamic house edge based on the rounding factor
        double baseEdge = 0.005; // Base house edge (0.5%)
        double roundingCompensation = 0.005; // Adjust this based on rounding impact
    
        double effectiveEdge = baseEdge - roundingCompensation; // Adjust the house edge to make it fairer
        effectiveEdge = Math.max(0, effectiveEdge); // Ensure house edge doesn't go negative
    
        double payoutMultiplier = ((1.0 - effectiveEdge) / probability);
        return payoutMultiplier;
    }
    

    private void updateBetLore(int slot, double totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (totalBet > 0) {
                    List<String> lore = new ArrayList<>();
                    lore.add("Total Bet: " + totalBet + " " + plugin.getCurrencyName(internalName));
                    meta.setLore(lore);
                } else {
                    meta.setLore(new ArrayList<>());  // Clear lore if no wager
                }
                item.setItemMeta(meta);
            }
        }
    }

    private void updateStartGameLever(boolean showLever) {
        if (showLever) {
            inventory.setItem(53, createCustomItem(Material.LEVER, "Start Game", 1)); // Slot 53
        } else {
            inventory.setItem(53, null); // Remove the lever if conditions not met
        }
    }

    private boolean hasEnoughCurrency(Player player, int amount) {
        if (amount == 0) return true; // Allow zero wager
        ItemStack currencyItem = new ItemStack(plugin.getCurrency(internalName));
        return player.getInventory().containsAtLeast(currencyItem, amount);
    }

    private void removeWagerFromInventory(Player player, int amount) {
        if (amount == 0) return; // No need to remove currency for zero wager
        player.getInventory().removeItem(new ItemStack(plugin.getCurrency(internalName), amount));
    }

    private void refundAllBets(Player player) {
        double totalRefund = currentBets.getOrDefault(player.getUniqueId(), 0.0);
        refundBet(player, (int) totalRefund);
    }

    private void refundBet(Player player, int amount) {
        if (amount <= 0) return; // No refund needed for zero amount
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);

        for (int i = 0; i < fullStacks; i++) {
            player.getInventory().addItem(new ItemStack(currencyMaterial, 64));
        }

        if (remainder > 0) {
            player.getInventory().addItem(new ItemStack(currencyMaterial, remainder));
        }
    }

    private void giveWinningsToPlayer(double amount) {
        if (amount <= 0) return; // No winnings to give
        int totalAmount = (int) Math.floor(amount);
        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);

        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(currencyMaterial, 64);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                // Drop the item at the player's location if inventory is full
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }

        if (remainder > 0) {
            ItemStack stack = new ItemStack(currencyMaterial, remainder);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                // Drop the item at the player's location if inventory is full
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private void endGame() {
        if (player != null) {
            if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
                refundAllBets(player);  // Refund any remaining bets
            }
            currentBets.remove(player.getUniqueId());
        }
    
        // Notify minesInventory to remove the player's table
        minesInventory.removeTable(playerId);
    
        cleanup();  // Clean up game state
    }
    

    // Method to unregister event listener
    private void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    // Clean up method to unregister listeners and clear data
    private void cleanup() {
        unregisterListener();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // Handle inventory close event
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (event.getInventory().getHolder() instanceof MinesTable && event.getPlayer().getUniqueId().equals(playerId) && closeFlag) {
            if (gameState == GameState.PLAYING) {
                closeCashOut();  // Automatically cash out the player
            }
            endGame();  // Call the end game logic when the inventory is closed
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
}
