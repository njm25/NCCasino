package org.nc.nccasino.games.DragonDescent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.helpers.AttributeHelper;
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
    private boolean playerLost = false;
    private int displayOffset = 0; 
    private int floorsCleared = 0; 
    private boolean cashOutTriggered = false; 
    private boolean shiftlock = false;
    public DragonClient(DragonServer server, Player player, Nccasino plugin, String internalName) {
        super(server, player, "Dragon Descent", plugin, internalName);

        if (!plugin.getConfig().contains("dealers." + internalName + ".default-columns")) {
            plugin.getConfig().set("dealers." + internalName + ".default-columns", 7);
        }
        if (!plugin.getConfig().contains("dealers." + internalName + ".default-vines")) {
            plugin.getConfig().set("dealers." + internalName + ".default-vines", 5);
        }
        if (!plugin.getConfig().contains("dealers." + internalName + ".default-floors")) {
            plugin.getConfig().set("dealers." + internalName + ".default-floors", 4);
        }
        plugin.saveConfig();
        numColumns = plugin.getDragonDescentColumns(internalName);
        numSafeSpots = plugin.getDragonDescentVines(internalName);
        numRows = plugin.getDragonDescentFloors(internalName);
        
        // Ensure vines are valid for column count
        numSafeSpots = Math.min(numSafeSpots, numColumns - 1);
        
        initializeUI(true, true,false,40,53);
        setupPregame();
    }

    @SuppressWarnings("removal")
    private void setupPregame() {
        cashOutTriggered=false;
        bettingEnabled=true;
        //updateRebetToggle(44);
        // Table layout
        int[] tableSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15,16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,28,29,30,31,32,33,34, 35, 36,37,38,39,41,42,43,44};
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
        inventory.setItem(3, createCustomItem(Material.DRAGON_HEAD, "Drungus The Dragon", 1));
        addItem(createCustomItem(Material.DIAMOND_SWORD, "Begin Your Descent?"), 4); // Hit
        ItemStack item = inventory.getItem(4);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.addAttributeModifier(AttributeHelper.getAttributeSafely("ATTACK_DAMAGE"), 
            new AttributeModifier("foo", 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        for(ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        inventory.setItem(5, createPlayerHeadItem(player, 1));

        setupGameSettingRow(9, Material.WHITE_STAINED_GLASS_PANE, "Columns", numColumns, 2, 9);
        setupGameSettingRow(18, Material.VINE, "Vines (Safe Spots per Floor)", numSafeSpots, 1, numColumns - 1);
        setupGameSettingRow(27, Material.BLACK_STAINED_GLASS_PANE, "Floors", numRows, 1, 100); // Unlimited Floors (for now)
    }

    private void updatePlayerHead() {
        double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
        double payoutMultiplier = calculatePayoutMultiplier();
        double potentialWinnings = totalBet * payoutMultiplier;
        potentialWinnings = Math.round(potentialWinnings * 100.0) / 100.0;
        inventory.setItem(playerX, createPlayerHead(player.getUniqueId(), "§e§oCash Out", 
            "§7§oCashout Value: §a" + potentialWinnings)); // Show exact double value
    }
    
    private void setupGameSettingRow(int startSlot, Material material, String settingName, int value, int min, int max) {
        inventory.setItem(startSlot, createCustomItem(Material.RED_STAINED_GLASS_PANE, "§c§o-1 " + settingName, 1));
        inventory.setItem(startSlot + 8, createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "§a§o+1 " + settingName, 1));

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
        
        if (SoundHelper.getSoundSafely("block.metal_pressure_plate.click_on", player) != null)
            player.playSound(player.getLocation(), Sound.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, SoundCategory.MASTER, 1.0f, 1.0f);
        setupPregame();
    }
    
    private void setupGame() {
        bettingEnabled = false;

        generateGameGrid();
        setupTopRow();
        setupGameBoard();
        //updatePlayerHead();
    }

     private void generateGameGrid() {
        gameGrid = new int[numRows][numColumns];
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
        }
    }

    private void setupTopRow() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
        }
        playerX = 4; // Player starts at center column
        double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
        double payoutMultiplier = calculatePayoutMultiplier();
        double potentialWinnings = totalBet * payoutMultiplier;
        potentialWinnings = Math.round(potentialWinnings * 100.0) / 100.0;
        inventory.setItem(playerX, createPlayerHead(player.getUniqueId(), "§e§oCash Out", 
            "§7§oCashout Value: §a" + potentialWinnings)); // Show exact double value
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
                    inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
                } else if (col < startCol || col >= startCol + effectiveColumns) {
                    inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
                } else if (col == middleCol) {
                    inventory.setItem(slot, createCustomItem(Material.BROWN_STAINED_GLASS_PANE, "§r", 1));
                } else {
                    int gridCol = col - startCol;
                    if (numColumns % 2 == 0) {
                        if (col == middleCol) {
                            gridCol = -1; // Ensure brown column is unplayable
                        } else if (col > middleCol) {
                            gridCol--; // Adjust only for valid columns
                        }
                    }
                    
                    // Prevent index out of bounds
                    if (gridCol < 0 || gridCol >= numColumns) return;
                    if (numColumns % 2 == 0 && col == middleCol) return; // Skip brown column

    
                    if (gridCol >= 0 && gridCol < numColumns) {
                        if(row==0){
                            inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, "Click Here to Move", 1));
                        }
                        else {
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
                inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
            } else if (col == middleCol) {
                inventory.setItem(slot, createCustomItem(Material.AIR, "§r", 1));
            } else {
                int gridCol = col - startCol;
                if (numColumns % 2 == 0 && col > middleCol) {
                    gridCol--;
                }
    
                if (gridCol >= 0 && gridCol < numColumns) {
                    if (gameGrid[actualFloor][gridCol] == 1) {
                        inventory.setItem(slot, createCustomItem(Material.VINE, "§aSafe!", 1));
                    } else {
                        inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnsafe!", 1));
                    }
                }
            }
        }
    }
    
    private void restoreTile(int slot, int floor) {
        if (gameGrid == null || floor < 1 || floor > numRows) {
            return;
        }
    
        // Same references as in animateDragonSweep / setupGameBoard
        int effectiveColumns = (numColumns % 2 == 0) ? (numColumns + 1) : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = (numColumns % 2 == 0) ? (startCol + (effectiveColumns / 2)) : -1;
    
        int col = slot % 9;
    
        // If this slot is outside the playable area, just skip
        if (col < startCol || col >= startCol + effectiveColumns) {
            return;
        }
        // If this slot is the "brown column", restore it to air
        if (numColumns % 2 == 0 && col == middleCol) {
            inventory.setItem(slot, createCustomItem(Material.AIR, "§r", 1));
            return;
        }
    
        // Convert GUI col → gameGrid col
        int gridCol = col - startCol;
        if (numColumns % 2 == 0 && col > middleCol) {
            gridCol--; 
        }
    
        // If out of valid grid range, skip
        if (gridCol < 0 || gridCol >= numColumns) {
            return;
        }
    
        // If this tile is where the player currently is, don't overwrite them
        if (slot == playerX) {
            return;
        }
    
        // Now read whether it's safe (1) or unsafe (0) from the gameGrid
        if (gameGrid[floor - 1][gridCol] == 1) {
            // It's safe => restore vine (or "Start" if you prefer)
            inventory.setItem(slot, createCustomItem(Material.VINE, "§aStart", 1));
        } else {
            // It's unsafe => restore air
            inventory.setItem(slot, createCustomItem(Material.AIR, "§cUnsafe!", 1));
        }
    }
    
    private void animateDragonSweep(int floor, boolean gameOver) {
        if (gameGrid == null) return;
        int actualFloor = displayOffset + (floor - 1);

        int rowStart = floor * 9;
        int effectiveColumns = (numColumns % 2 == 0) ? (numColumns + 1) : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = (numColumns % 2 == 0)
                ? (startCol + (effectiveColumns / 2))
                : -1;
    
        java.util.concurrent.atomic.AtomicInteger lastPlacedSlot = new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicInteger maxDelay = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger tilesToRestore = new java.util.concurrent.atomic.AtomicInteger(
                (numColumns - numSafeSpots) + ((numColumns % 2 == 0) ? 1 : 0)
        );
    
        moveLocked = true; // Prevent moving while dragon sweeps
            for (int col = 0; col < 9; col++) {
            if (col < startCol || col >= (startCol + effectiveColumns)) {
                continue;
            }
    
            int slot = rowStart + col;
            boolean isBrownColumn = (col == middleCol);
            int gridCol = col - startCol;
            if (numColumns % 2 == 0 && col > middleCol) {
                gridCol--;
            }
            if (gridCol < 0 || gridCol >= numColumns) {
                continue;
            }
    
            boolean isUnsafe = (gameGrid[actualFloor][gridCol] == 0);
            if (!isBrownColumn && !isUnsafe) {
                continue;
            }
    
            int thisDelay = col * 2; // each col is 2 ticks later
            maxDelay.set(Math.max(maxDelay.get(), thisDelay));
            if (this.inventory == null|| gameGrid == null) return;
            int taskID = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (gameOverTriggered|| gameGrid == null) return;
                    if (lastPlacedSlot.get() != -1) {
                    restoreTile(lastPlacedSlot.get(), actualFloor+1);
                }
                inventory.setItem(slot, createCustomItem(Material.DRAGON_HEAD, "§cThe Dragon sweeps through!", 1));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.MASTER, 1.0f, 1.0f);
    
                if (slot == playerX) {
                    triggerGameOver();
                    return;
                }
                int restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!gameOverTriggered&& gameGrid != null) {
                        if (isBrownColumn) {
                            inventory.setItem(slot, createCustomItem(Material.AIR, "§r", 1));
                        } else {
                            restoreTile(slot, actualFloor+1);
                        }
                            if (tilesToRestore.decrementAndGet() == 0) {
                            moveLocked = false;
                        }
                    }
                }, 5L).getTaskId();
                taskIDs.add(restoreTask);
    
                lastPlacedSlot.set(slot);
    
            }, thisDelay).getTaskId();
    
            taskIDs.add(taskID);
        }
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameOver) {
                moveLocked = false;
            }
        }, numColumns * 3L);
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameOverTriggered) {
                int lastSlot = lastPlacedSlot.get();
                if (lastSlot != -1) {
                    ItemStack item = inventory.getItem(lastSlot);
                    if (item != null && item.getType() == Material.DRAGON_HEAD) {
                        // Check if it's brown or not
                        int colOfSlot = lastSlot % 9;
                        boolean isBrownHere = (colOfSlot == middleCol && (numColumns % 2 == 0));
    
                        if (isBrownHere) {
                            inventory.setItem(lastSlot, createCustomItem(Material.AIR, "§r", 1));
                        } else {
                            restoreTile(lastSlot, actualFloor+1);
                        }
                    }
                }
            }
        }, maxDelay.get() + 10L);
    
        // If we already know it's gameOver, do it right now
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
        
        server.applyLoseEffects(player);

        // Delay reset slightly to let player see the result
        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 30L); // 60 ticks = 3 seconds
    }
    
    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (moveLocked|| cashOutTriggered||shiftlock) {
            return;
        }
    
        if (bettingEnabled) {
            if (slot == 4) {
                if(!betStack.isEmpty()){
                    if (SoundHelper.getSoundSafely("entity.ender_dragon.ambient", player) != null)
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.MASTER, 0.6f, 1.0f);
                    setupGame();
                }
                else {
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            player.sendMessage("§cInvalid action.");
                            break;
                        case VERBOSE:
                            player.sendMessage("§cNo bet placed.");
                            break;
                        case NONE:
                            break;
                    }
                    if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                }
                return;
            }
            else if (slot==3){
                if (SoundHelper.getSoundSafely("entity.ender_dragon.hurt", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, SoundCategory.MASTER, 1.0f, 1.0f); 
            }
            else if (slot==5){
                Random random = new Random();
                float randomPitch = 0.5f + (random.nextFloat() * 1.5f);
                if (SoundHelper.getSoundSafely("entity.player.burp", player) != null)
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.MASTER, 1.0f, randomPitch); 
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
            return;
        }
        // Allow cashing out if the player is on a safe spot
        if (slot == playerX) {

            if (!moveLocked && !cashOutTriggered && !playerLost) {
                moveLocked=true;
                cashOut(true);
            }
                return;
        }
    
        if (col == middleCol) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage("§cInvalid Action.");
                break;
                case VERBOSE:
                    player.sendMessage("§cYou cannot select this zone!");
                    break;
                case NONE:
                    break;
            }
            return;
        }
    
        int gridCol = col - startCol;
                // Skip brown column (unplayable)
        if (numColumns % 2 == 0) {
            if (col == middleCol&& slot != playerX) return; // Don't allow clicks on brown column
            if (col > middleCol) gridCol--; // Adjust index to ignore the brown column
        }
        
        // Prevent index out of bounds
        if (gridCol < 0 || gridCol >= numColumns) return;
        final int safeGridCol = gridCol; // Create a final copy

        if (floor == (currentFloor - displayOffset)) { 
            moveLocked = true;
            revealRow(floor);
            if(this.inventory == null||gameGrid==null) return;
            boolean gameOver = (gameGrid[displayOffset + floor - 1][safeGridCol] == 0);
            if (gameOver) playerLost = true; 
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if(this.inventory == null) return;
                if (SoundHelper.getSoundSafely("block.cave_vines.step", player) != null)
                    player.playSound(player.getLocation(), Sound.BLOCK_CAVE_VINES_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
                inventory.setItem(playerX, createCustomItem(Material.VINE, "§aSafe!", 1));
                playerX = slot;
                if (gameGrid[displayOffset + floor - 1][safeGridCol] == 1) { 
                    floorsCleared++; // Move this BEFORE updatePlayerHead
                }
                updatePlayerHead();
        
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    animateDragonSweep(floor, gameOver);
                }, 10L);
        
                if (gameOver) {
                    renameAllExcept(playerX, "§cOof.");
                } else {
                    if (currentFloor == numRows) {
                        renameAllExcept(playerX, "§aYayyy!");
                        moveLocked = true; 
                        int taskID =Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        cashOut(true); // Auto cash-out if at last floor
                    }, 40L).getTaskId();
                    taskIDs.add(taskID);
                    } else if (floor == 5) { // Reached last visible row
                        shiftlock=true;
                        Bukkit.getScheduler().runTaskLater(plugin, this::shiftDisplayUp, 60L); // 1.5s delay before shifting
                    } else {
                        setNextClickableRow(currentFloor+1);
                        currentFloor++;
                    }
                }
            }, 10L);
        }
    }

    private double calculatePayoutMultiplier() {
        if (floorsCleared <= 0) return 1.0;
        
        double probability = 1.0;
        for (int i = 0; i < floorsCleared; i++) {
            probability *= (double) numSafeSpots / numColumns;
        }
        return 1.0 / probability;
    }
    
    
    private double applyProbabilisticRounding(double value, Player player) {
        int integerPart = (int) value;
        double fractionalPart = value - integerPart;
    
        Random random = new Random();
        return (random.nextDouble() <= fractionalPart) ? integerPart + 1 : integerPart;
    }
    private void setNextClickableRow(int nextFloor) {
        if (nextFloor > numRows) return; // Prevent out-of-bounds
    
        int effectiveColumns = numColumns % 2 == 0 ? numColumns + 1 : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = numColumns % 2 == 0 ? (effectiveColumns / 2) + startCol : -1;
        int rowStart = (nextFloor - displayOffset) * 9;
    
        for (int col = 0; col < 9; col++) {
            int slot = rowStart + col;
            int gridCol = col - startCol;
            
            if (numColumns % 2 == 0) {
                if (col == middleCol) continue; // Skip brown column
                if (col > middleCol) gridCol--; // Adjust for removed brown column
            }

            if (gridCol >= 0 && gridCol < numColumns) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    Material material = item.getType(); // Keep material
                    inventory.setItem(slot, createCustomItem(material, "Click Here to Move", 1));
                }
            }
        }
    }
    
    private void renameAllExcept(int excludeSlot, String newName) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == excludeSlot) continue; // Skip the excluded slot
    
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(newName);
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                }
            }
        }
    }
    
    private void shiftDisplayUp() {
               if (gameGrid == null ) {
        return;
    }
        if (displayOffset + 5 >= numRows) return;
    
        moveLocked = true; 
        long delayPerStep = 10L; // Ticks between each mini‐step
        for (int step = 1; step <= 5; step++) {
            final int currentStep = step;
    
                int taskID = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (gameGrid == null) return; 

    
                // 1) Shift each row up by 1 (row i+1 ⇒ row i)
                for (int i = 0; i < 5; i++) {  // rows 0..4 in the inventory “board” area
                    for (int col = 0; col < 9; col++) {
                        int fromSlot = ((i + 1) * 9) + col; // row i+1
                        int toSlot   = (i * 9) + col;       // row i
                        inventory.setItem(toSlot, inventory.getItem(fromSlot));
                    }
                }
                int playerRow = playerX / 9;
                if (playerRow >= 1 && playerRow <= 5) {
                    // If player is anywhere from row1..row5, shift them up by 1 row
                    int oldSlot = playerX;
                    int oldCol = oldSlot % 9;
                    int newRow = playerRow - 1;
                    int newSlot = (newRow * 9) + oldCol;
                    if (playerRow == currentStep) {
                        inventory.setItem(oldSlot, null); // clear old
                        playerX = newSlot;
                        playerX-=36;
                    }
                }
    
                displayOffset++;

                fillRowWithFloor(5, displayOffset + 4); 
                if (currentStep == 5) {
                    currentFloor = displayOffset + 1;
                    setNextClickableRow(currentFloor);
                    moveLocked = false;
                    shiftlock=false;
                }
    
            }, delayPerStep * step).getTaskId();
                    taskIDs.add(taskID);

        }
    }
    
    private void fillRowWithFloor(int row, int floorIndex) {
          if (gameGrid == null ) {
        return;
    }
        int rowStart = row * 9;
        int effectiveColumns = (numColumns % 2 == 0) ? (numColumns + 1) : numColumns;
        int startCol = (9 - effectiveColumns) / 2;
        int middleCol = (numColumns % 2 == 0) ? (effectiveColumns / 2) + startCol : -1;
    
        // If we’re out of floors, fill with white
        if (floorIndex >= numRows) {
            for (int col = 0; col < 9; col++) {
                inventory.setItem(rowStart + col,
                    createCustomItem(Material.WHITE_STAINED_GLASS_PANE, "§r", 1));
            }
            return;
        }
    
        // Otherwise, fill row from the specified floor in gameGrid
        for (int col = 0; col < 9; col++) {
            int slot = rowStart + col;
    
            // Non-playable columns => white glass
            if (col < startCol || col >= startCol + effectiveColumns) {
                inventory.setItem(slot, createCustomItem(Material.WHITE_STAINED_GLASS_PANE,
                                                         "§r", 1));
                continue;
            }
    
            // Middle col if even => brown
            if (col == middleCol) {
                inventory.setItem(slot, createCustomItem(Material.BROWN_STAINED_GLASS_PANE,
                                                         "§r", 1));
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
                                                         "§r", 1));
            } else {
                boolean isSafe = (gameGrid[floorIndex][gridCol] == 1);
                if (isSafe) {
                    inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE,
                                                             "§r", 1));
                } else {
                    inventory.setItem(slot, createCustomItem(Material.BLACK_STAINED_GLASS_PANE,
                                                             "§r", 1));
                }
            }
        }
    }

    private void cashOut(boolean resetGame) {
        if (cashOutTriggered) return; 
        cashOutTriggered = true;
        moveLocked = true;
        double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
        double payoutMultiplier = calculatePayoutMultiplier();
        double winnings = totalBet * payoutMultiplier;

        winnings = applyProbabilisticRounding(winnings, player);
        // Notify the player
        server.sendPayoutMessage(player, winnings, true, (winnings - totalBet));
    
        if (winnings > 0) {
            creditPlayer(player, winnings);
            server.applyWinEffects(player);
        }
        
        playerLost=true;

        if(resetGame){
            Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 40L); }
        else{

            betStack.clear(); // If rebet is off, clear the stack.
            for (int taskID : taskIDs) {
                Bukkit.getScheduler().cancelTask(taskID);
            }
            taskIDs.clear();
            floorsCleared=0;
            playerLost=false;
            gameOverTriggered = false; // Reset game state
            moveLocked = false;
            displayOffset = 0;
            currentFloor = 1;
            playerX = 4;
            gameGrid = null; // Clear old game grid safely
            bettingEnabled = true;
        }

    }

    private void resetGame() {
        // Cancel all delayed tasks to prevent conflicts
        if (this.inventory == null) {
            return;
        }
        for (int taskID : taskIDs) {
            Bukkit.getScheduler().cancelTask(taskID);
        }
        taskIDs.clear();
        floorsCleared=0;
        playerLost=false;
        gameOverTriggered = false; // Reset game state
        moveLocked = false;
        displayOffset = 0;
        currentFloor = 1;
        playerX = 4;
        gameGrid = null; // Clear old game grid safely
        bettingEnabled = true;
        double totalRebetAmount = betStack.stream().mapToDouble(Double::doubleValue).sum();

        if (rebetEnabled && totalRebetAmount > 0) {
            if (hasEnoughWager(player, (int) totalRebetAmount)) {
                // Deduct the total amount needed for rebet
                removeCurrencyFromInventory(player, (int) totalRebetAmount);
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                    case VERBOSE:
                        player.sendMessage("§dRebet of " + (int) totalRebetAmount + " " +
                            plugin.getCurrencyName(internalName).toLowerCase() + " placed.");
                        break;
                    case NONE:
                        break;
                }
            } else {
                // Not enough funds for rebet, clear the bet stack
                player.sendMessage("§cNot enough currency for rebet. Wager reset.");
                betStack.clear();
            }
        } else {
            betStack.clear(); // If rebet is off, clear the stack.
        }

        initializeUI(true, true,rebetEnabled,40,53);
        setupPregame();
    }

    @Override
    protected void handleClientInventoryClose() {
        if(!bettingEnabled && !playerLost){
            moveLocked=true;
            cashOut(false);
        }
        else if (bettingEnabled && !betStack.isEmpty()) {
            undoAllBets();
        }
        super.handleClientInventoryClose();
    }
    
    @Override
    public void onServerUpdate(String eventType, Object data) {
        throw new UnsupportedOperationException("Unimplemented method 'onServerUpdate'");
    }
    
}
