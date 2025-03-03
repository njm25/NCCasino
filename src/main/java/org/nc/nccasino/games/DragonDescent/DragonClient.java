package org.nc.nccasino.games.DragonDescent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.helpers.SoundHelper;

public class DragonClient extends Client{
    private int numColumns = 7; 
    private int numSafeSpots = 5;
    private int numRows = 4; 
    private int currentFloor = 1;
    private int playerX;
    private int[][] gameGrid;
    private boolean moveLocked = false; 
    private final List<Integer> taskIDs = new ArrayList<>();
    private boolean gameOverTriggered = false;
    private int displayOffset = 0; 

    public DragonClient(DragonServer server, Player player, Nccasino plugin, String internalName) {
    super(server, player, "Dragon Descent", plugin, internalName);
          
    }

    @Override
    public void initializeUI(boolean switchRebet, boolean betSlip,boolean deafultRebet) {
        super.initializeUI(switchRebet, betSlip,deafultRebet);
        setupPregame();
    }

    private void setupPregame() {
        bettingEnabled=true;
        Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String rebetName = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
        inventory.setItem(53, createCustomItem(rebetMat, rebetName, 1));
        // Table layout
        int[] tableSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15,16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,28,29,30,31,32,33,34, 35, 36,37,38,39,40,41,42,43, 44};
        for (int slot : tableSlots) {
            ItemStack item = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                meta.setLore(null); // Ensure no lore
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
        inventory.setItem(4, createCustomItem(Material.DRAGON_HEAD, "Start Your Descent", 1));

        setupGameSettingRow(9, Material.WHITE_STAINED_GLASS_PANE, "Columns", numColumns, 2, 9);
        setupGameSettingRow(18, Material.VINE, "Vines (Safe Spots per Floor)", numSafeSpots, 1, numColumns - 1);
        setupGameSettingRow(27, Material.BLACK_STAINED_GLASS_PANE, "Floors", numRows, 1, 100); // Unlimited Floors (for now)
    
        // Dealer slot
        //inventory.setItem(4, createDealerSkull("Dealer"));
        //inventory.setItem(40, createPlayerHead(player.getUniqueId(), player.getName()+"Click to Place Bet"));
        updatePlayerHead();
    }

    private void updatePlayerHead() {
        double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
    
        if (bettingEnabled) {
            inventory.setItem(40, createPlayerHead(player.getUniqueId(), "§ePlace Bet", "§7Current Bet: §a" + (int) totalBet));
        } else {
            // Allow cashing out as long as the player is on a safe spot
            int startCol = (9 - numColumns) / 2;
            int gridCol = (playerX % 9) - startCol;
            System.out.println("placing in"+startCol+"|"+gridCol+"|"+playerX);

            boolean isSafeSpot = gridCol >= 0 && gridCol < numColumns && gameGrid[currentFloor - 1][gridCol] == 1;
    
            if (isSafeSpot) {
                inventory.setItem(playerX, createPlayerHead(player.getUniqueId(), "§eCash Out", "§7Cashout Value: §a" + (int) totalBet));
            }
        }
    }
    

    private void setupGameSettingRow(int startSlot, Material material, String settingName, int value, int min, int max) {
        inventory.setItem(startSlot, createCustomItem(Material.RED_STAINED_GLASS_PANE, "§c-1 " + settingName, 1));
        inventory.setItem(startSlot + 8, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "§a+1 " + settingName, 1));

        for (int i = 1; i <= 7; i++) {
            int slot = startSlot + i;
            int stackSize = (i == 4) ? value : 1; // Only the middle slot (slot + 4) gets the changing stack size
            inventory.setItem(slot, createCustomItem(material, settingName + ": " + value, stackSize));
        }
    }

    private void handleGameSettingClick(int slot) {
        if (slot == 9) updateSetting("Columns", -1, 2, 9);
        if (slot == 17) updateSetting("Columns", 1, 2, 9);

        if (slot == 18) updateSetting("Vines (Safe Spots per Floor)", -1, 1, numColumns - 1);
        if (slot == 26) updateSetting("Vines (Safe Spots per Floor)", 1, 1, numColumns - 1);

        if (slot == 27) updateSetting("Floors", -1, 1, 100);
        if (slot == 35) updateSetting("Floors", 1, 1, 100);
    }

    private void updateSetting(String setting, int change, int min, int max) {
        int newValue;
        
        if (setting.equals("Columns")) {
            newValue = Math.max(min, Math.min(max, numColumns + change));
            numColumns = newValue;
            
            // Ensure numSafeSpots is within valid range
            if (numSafeSpots >= numColumns) {
                numSafeSpots = numColumns - 1;
            }
        } else if (setting.equals("Vines (Safe Spots per Floor)")) {
            newValue = Math.max(min, Math.min(numColumns - 1, numSafeSpots + change)); // Enforce col - 1
            numSafeSpots = newValue;
        } else {
            newValue = Math.max(min, Math.min(max, numRows + change));
            numRows = newValue;
        }
    
        initializeUI(true, true, false);
    }
    

    private void setupGame() {
        bettingEnabled = false;

        generateGameGrid();
        setupTopRow();
        setupGameBoard();
        updatePlayerHead();
    }

     private void generateGameGrid() {
        gameGrid = new int[numRows][numColumns];
        //Random random = new Random();

        for (int row = 0; row < numRows; row++) {
            List<Integer> columnIndexes = new ArrayList<>();
            for (int col = 0; col < numColumns; col++) {
                columnIndexes.add(col);
            }
            Collections.shuffle(columnIndexes);

            // Set safe spots randomly
            for (int i = 0; i < numSafeSpots; i++) {
                gameGrid[row][columnIndexes.get(i)] = 1; // 1 = Safe (VINE)
            }
            // The rest are implicitly 0 (unsafe spots)
        }
        System.out.println("Generated Game Grid:");
    for (int r = 0; r < numRows; r++) {
        StringBuilder rowString = new StringBuilder("Row " + r + ": ");
        for (int c = 0; c < numColumns; c++) {
            rowString.append(gameGrid[r][c]).append(" ");
        }
        System.out.println(rowString.toString().trim());
    }
    }

    private void setupTopRow() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
        }
        // Move player head to slot 4
        inventory.setItem(4, createPlayerHead(player.getUniqueId(), "§eCash Out", "§7Current Value: §a0"));
        playerX = 4; // Player starts at center column
    }

    private void setupGameBoard() {
        int effectiveColumns = numColumns % 2 == 0 ? numColumns + 1 : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = numColumns % 2 == 0 ? (effectiveColumns / 2) + startCol : -1;
    
        for (int row = 0; row < 5; row++) { // Always display 5 visible rows
            int gridRow = displayOffset + row; // Adjust based on display offset
            int rowStart = (row + 1) * 9;
    
            for (int col = 0; col < 9; col++) {
                int slot = rowStart + col;
    
                if (gridRow >= numRows) {
                    inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§fInactive Area", 1));
                } else if (col < startCol || col >= startCol + effectiveColumns) {
                    inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§fNon-Playable Area", 1));
                } else if (col == middleCol) {
                    inventory.setItem(slot, createCustomItem(Material.BROWN_STAINED_GLASS_PANE, "§cUnplayable Zone", 1));
                } else {
                    int gridCol = col - startCol;
                    if (numColumns % 2 == 0 && col > middleCol) {
                        gridCol--; // Adjust for even column layouts
                    }
    
                    if (gridCol >= 0 && gridCol < numColumns) {
                        if (gameGrid[gridRow][gridCol] == 1) {
                            inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "§r", 1));
                        } else {
                            inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "§r", 1));
                        }
                    }
                }
            }
        }
    }
    
    private void revealRow(int floor) {
        int actualFloor = displayOffset + (floor - 1); // Convert UI floor to actual grid floor
        if (actualFloor >= numRows) return;
    
        int effectiveColumns = numColumns % 2 == 0 ? numColumns + 1 : numColumns;
        int rowStart = floor * 9;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = numColumns % 2 == 0 ? (effectiveColumns / 2) + startCol : -1;
    
        for (int col = 0; col < 9; col++) {
            int slot = rowStart + col;
    
            if (col < startCol || col >= startCol + effectiveColumns) {
                inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§fNon-Playable Area", 1));
            } else if (col == middleCol) {
                inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnplayable Zone", 1));
            } else {
                int gridCol = col - startCol;
                if (numColumns % 2 == 0 && col > middleCol) {
                    gridCol--;
                }
    
                if (gridCol >= 0 && gridCol < numColumns) {
                    if (gameGrid[actualFloor][gridCol] == 1) {
                        inventory.setItem(slot, createCustomItem(Material.VINE, "§aSafe Spot!", 1));
                    } else {
                        inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnsafe!", 1));
                    }
                }
            }
        }
    }
    
    
    private void restoreTile(int slot, int floor) {
        if (gameGrid == null) return; 
        int startCol = (9 - numColumns) / 2;
        int gridCol = slot % 9 - startCol;
    
        if (gridCol >= 0 && gridCol < numColumns) {
            // Only restore if it's not the player's current position
            if (slot != playerX) {
                if (gameGrid[floor - 1][gridCol] == 1) {
                    inventory.setItem(slot, createCustomItem(Material.VINE, "§aSafe Spot!", 1)); // Properly restore vine
                } else {
                    inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnsafe!", 1)); // Properly restore air
                }
            }
        }
    }
    
    private void animateDragonSweep(int floor, boolean gameOver) {
        if (gameOverTriggered) return; // Stop further sweeps if game over has already started
    
        int rowStart = floor * 9;
        int effectiveColumns = numColumns % 2 == 0 ? numColumns + 1 : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = numColumns % 2 == 0 ? (effectiveColumns / 2) + startCol : -1;
    
        AtomicInteger lastPlacedSlot = new AtomicInteger(-1);
        AtomicInteger unsafeTilesRemaining = new AtomicInteger(numColumns - numSafeSpots);
    
        moveLocked = true;
    
        for (int col = 0; col < numColumns; col++) {
            int slot = rowStart + (startCol + col);
            int gridCol = col;
            if (numColumns % 2 == 0 && col >= (middleCol - startCol)) {
                gridCol++;
            }
    
            if (gridCol >= numColumns) continue;
    
            boolean isBrownColumn = (numColumns % 2 == 0 && col + startCol == middleCol);
    
            if (isBrownColumn) {
                int taskID = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (gameOverTriggered) return; // Stop processing if game is over
    
                    inventory.setItem(slot, createCustomItem(Material.DRAGON_HEAD, "§cThe Dragon sweeps through!", 1));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.MASTER, 1.0f, 1.0f);
    
                    int restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!gameOverTriggered) inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnplayable Zone", 1));
                    }, 5L).getTaskId();
                    taskIDs.add(restoreTask);
                }, col * 2L).getTaskId();
    
                taskIDs.add(taskID);
                continue;
            }
    
            if (gameGrid[floor - 1][gridCol] == 0) { // Unsafe spot
                int finalCol = col;
                int taskID = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (gameOverTriggered) return; // Stop processing if game is over
    
                    if (lastPlacedSlot.get() != -1) {
                        restoreTile(lastPlacedSlot.get(), floor);
                    }
    
                    inventory.setItem(slot, createCustomItem(Material.DRAGON_HEAD, "§cThe Dragon sweeps through!", 1));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.MASTER, 1.0f, 1.0f);
    
                    int restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!gameOverTriggered) {
                            restoreTile(slot, floor);
                            unsafeTilesRemaining.decrementAndGet();
                            if (unsafeTilesRemaining.get() == 0) {
                                moveLocked = false;
                            }
                        }
                    }, 5L).getTaskId();
                    taskIDs.add(restoreTask);
    
                    lastPlacedSlot.set(slot);
    
                }, finalCol * 2L).getTaskId();
    
                taskIDs.add(taskID);
            }
        }
    
        if (gameOver) {
            triggerGameOver();
        }
    }
    
    private void triggerGameOver() {
        if (gameOverTriggered) return; // Prevent multiple triggers
    
        gameOverTriggered = true;
        moveLocked = true;
    
        // Cancel all scheduled tasks
        for (int taskID : taskIDs) {
            Bukkit.getScheduler().cancelTask(taskID);
        }
        taskIDs.clear();
    
        // Set dragon head where the player is
        inventory.setItem(playerX, createCustomItem(Material.DRAGON_HEAD, "§4The Dragon got you!", 1));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 1.0f, 0.8f);
    
        // Delay reset slightly to let player see the result
        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 60L); // 60 ticks = 3 seconds
    }
    
    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (moveLocked) {
            return;
        }
    
        if (bettingEnabled) {
            if (slot == 4) {
                setupGame();
                return;
            }
            handleGameSettingClick(slot);
            return;
        }

        if (gameGrid == null) {
            return;
        }
    
        int floor = slot / 9;
        int col = slot % 9;
        int effectiveColumns = numColumns % 2 == 0 ? numColumns + 1 : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = numColumns % 2 == 0 ? (effectiveColumns / 2) + startCol : -1;
    
        if (col < startCol || col >= startCol + effectiveColumns) {
            player.sendMessage("§cYou cannot select this zone!");
            return;
        }
    
        // Allow cashing out if the player is on a safe spot
        if (slot == playerX) {
            int gridCol = col - startCol;
            boolean isSafeSpot = gridCol >= 0 && gridCol < numColumns && gameGrid[currentFloor - 1][gridCol] == 1;
    
            if (isSafeSpot) {
                cashOut();
                return;
            }
        }
    
        if (col == middleCol) {
            player.sendMessage("§cThis column is unplayable!");
            return;
        }
    
        int gridCol = col - startCol;
    
        if (floor == (currentFloor - displayOffset)) { 
            moveLocked = true;
            revealRow(floor);
        
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                inventory.setItem(playerX, createCustomItem(Material.VINE, "§aSafe Spot!", 1));
                playerX = slot;
                updatePlayerHead();
        
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    animateDragonSweep(floor, gameGrid[displayOffset + floor - 1][gridCol] == 0);
                }, 10L);
        
                if (gameGrid[displayOffset + floor - 1][gridCol] == 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, this::gameOver, 40L);
                } else {
                    if (currentFloor == numRows) {
                        moveLocked = true; 
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        cashOut(); // Auto cash-out if at last floor
                    }, 40L);
                    } else if (floor == 5) { // Reached last visible row
                        Bukkit.getScheduler().runTaskLater(plugin, this::shiftDisplayUp, 60L); // 1.5s delay before shifting
                    } else {
                        currentFloor++;
                    }
                }
            }, 10L);
        }
        
        
    }
    
    private void shiftDisplayUp() {
        // If we don’t have enough rows left to scroll further, just return
        if (displayOffset + 5 >= numRows) return;
    
        moveLocked = true; 
        long delayPerStep = 10L; // Ticks between each mini‐step
    
        // We only need 4 steps for row4 to end up at row0:
        // Step 1: row1→row0, row2→row1, row3→row2, row4→row3, row5→row4
        // Step 2: row2→row1, row3→row2, row4→row3, row5→row4, row6→row5
        // Step 3: row3→row2, row4→row3, row5→row4, row6→row5, row7→row6
        // Step 4: row4→row3, row5→row4, row6→row5, row7→row6, row8→row7
        //  -> after the 4th step, the original row4 is now row0, discarding old rows 0..3.
        for (int step = 1; step <= 5; step++) {
            final int currentStep = step;
    
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
    
                // 1) Shift each row up by 1 (row i+1 ⇒ row i)
                for (int i = 0; i < 5; i++) {  // rows 0..4 in the inventory “board” area
                    for (int col = 0; col < 9; col++) {
                        int fromSlot = ((i + 1) * 9) + col; // row i+1
                        int toSlot   = (i * 9) + col;       // row i
                        inventory.setItem(toSlot, inventory.getItem(fromSlot));
                    }
                }
    
                // 2) If the player was on row i+1, move them up to row i
                //    (Check via integer division by 9)
                int playerRow = playerX / 9;
                if (playerRow >= 1 && playerRow <= 5) {
                    // If player is anywhere from row1..row5, shift them up by 1 row
                    int oldSlot = playerX;
                    int oldCol = oldSlot % 9;
                    int newRow = playerRow - 1;
                    int newSlot = (newRow * 9) + oldCol;
    
                    // If that exactly matches the “step” in question, do the move
                    // (Optional: you can make it less strict if you like.)
                    if (playerRow == currentStep) {
                        inventory.setItem(oldSlot, null); // clear old
                        playerX = newSlot;
                        playerX-=36;
                        //updatePlayerHead();
                    }
                }
    
                displayOffset++;

                fillRowWithFloor(5, displayOffset + 4); 

                // 4) After the final step, increment offset & redraw
                if (currentStep == 5) {
                    currentFloor = displayOffset + 1;

                    // Now the old row4 is at row0, old row5 is at row1, etc.
                    // “Discarded” the original top 4 rows.
                    //displayOffset++;
                    // Re‐render so the bottom row is newly revealed in row4
                    //setupGameBoard();
                    
                    moveLocked = false;
                }
    
            }, delayPerStep * step);
        }
    }
    
    private void fillRowWithFloor(int row, int floorIndex) {
        int rowStart = row * 9;
        int effectiveColumns = (numColumns % 2 == 0) ? (numColumns + 1) : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = (numColumns % 2 == 0) ? (effectiveColumns / 2) + startCol : -1;
    
        // If we’re out of floors, fill with white
        if (floorIndex >= numRows) {
            for (int col = 0; col < 9; col++) {
                inventory.setItem(rowStart + col,
                    createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§fInactive Area", 1));
            }
            return;
        }
    
        // Otherwise, fill row from the specified floor in gameGrid
        for (int col = 0; col < 9; col++) {
            int slot = rowStart + col;
    
            // Non-playable columns => white glass
            if (col < startCol || col >= startCol + effectiveColumns) {
                inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE,
                                                         "§fNon-Playable Area", 1));
                continue;
            }
    
            // Middle col if even => brown
            if (col == middleCol) {
                inventory.setItem(slot, createCustomItem(Material.BROWN_STAINED_GLASS_PANE,
                                                         "§cUnplayable Zone", 1));
                continue;
            }
    
            // Convert to gameGrid col
            int gridCol = col - startCol;
            if (numColumns % 2 == 0 && col > middleCol) {
                gridCol--;
            }
    
            if (gridCol < 0 || gridCol >= numColumns) {
                // Safety check
                inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE,
                                                         "§fNon-Playable Area", 1));
            } else {
                // Show “safe” or “unsafe” placeholders
                // If you want to reveal them only when the player steps there,
                // just use black glass until revealRow() is called.
                boolean isSafe = (gameGrid[floorIndex][gridCol] == 1);
                if (isSafe) {
                    inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE,
                                                             "§rHidden Safe", 1));
                } else {
                    inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE,
                                                             "§rHidden Unsafe", 1));
                }
            }
        }
    }

    
    
    private void cashOut() {
        double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
        creditPlayer(player, totalBet);
        player.sendMessage("§aYou cashed out with " + (int)totalBet + "!");
        moveLocked = true; // Prevent actions while waiting
        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 40L); // 40 ticks = 2 seconds
    }

    private void resetGame() {
        // Cancel all delayed tasks to prevent conflicts
        for (int taskID : taskIDs) {
            Bukkit.getScheduler().cancelTask(taskID);
        }
        taskIDs.clear();
    
        gameOverTriggered = false; // Reset game state
        moveLocked = false;
    
        currentFloor = 1;
        playerX = 4;
        gameGrid = null; // Clear old game grid safely
        bettingEnabled = true;
        initializeUI(true, true, false);
    }
    
    
    
    private void gameOver() {
        player.sendMessage("§cThe dragon caught you! You lose.");
        for (int taskID : taskIDs) {
            Bukkit.getScheduler().cancelTask(taskID);
        }
        taskIDs.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetGame();
        }, 60L); // 60 ticks = 3 seconds delay before resetting
    }

    
    @Override
    protected boolean isBetSlot(int slot) {
        return bettingEnabled && (slot == 40 || (slot >= 45 && slot <= 53)); // Only clickable during betting
    }

    @Override
    protected void handleBet(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
 
        // Handle Wager & All In Selection
        if (slot >= 47 && slot <= 52) {
            updateSelectedWager(slot);
            return;
        }
        // Rebet
        if (slot == 53) {
            rebetEnabled = !rebetEnabled;
            updateRebetToggle(slot);
            return;
        }

        // Undo all 
        if (slot == 45) {
            undoAllBets();
            updatePlayerHead();
            return;
        }

        // Undo last
        if (slot == 46) {
            undoLastBet();
            updatePlayerHead();
            return;
        }

        // All In
        if (slot == 52) {
            placeAllInBet();
            updatePlayerHead();
            return;
        }

        // slot == 53 => "Click here to place bet"
        if (slot == 40) {
            handleBetPlacement();
            //double total = betStack.stream().mapToDouble(Double::doubleValue).sum();
            updatePlayerHead();
        }

   
    }

    protected void handleBetPlacement() {
        // Check if user is holding the currency item
        ItemStack heldItem = player.getItemOnCursor();
        Material currencyMat = getCurrencyMaterial();
        double wagerAmount = 0;
        boolean usedHeldItem = false;

        if (heldItem != null && heldItem.getType() == currencyMat) {
            wagerAmount = heldItem.getAmount();
            usedHeldItem = true;
        } else {
            wagerAmount = selectedWager; 
        }

        if (selectedWager <= 0 && !usedHeldItem) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage("§cInvalid action.");
                    break;
                case VERBOSE:
                    player.sendMessage("§cSelect a wager amount first.");
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        if (!hasEnoughWager(player, selectedWager) && !usedHeldItem) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage("§cInvalid action.");
                    break;
                case VERBOSE:
                    player.sendMessage("§cNot enough currency to place bet.");
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        if (wagerAmount <= 0) {
            // Possibly send a message to the user "Invalid action" or "Select a wager first."
            return;
        }

        // Remove currency from inventory or from cursor
        if (usedHeldItem) {
            player.setItemOnCursor(null);
        } else {
            removeCurrencyFromInventory(player, (int)wagerAmount);
        }
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        betStack.push(wagerAmount);
    }

    @Override
    public void onServerUpdate(String eventType, Object data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onServerUpdate'");
    }
    
}
