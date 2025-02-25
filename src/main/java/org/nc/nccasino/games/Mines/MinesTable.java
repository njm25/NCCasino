package org.nc.nccasino.games.Mines;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.SoundHelper;

public class MinesTable extends DealerInventory {
    // Game state management
    public enum GameState {
        PLACING_WAGER,
        WAITING_TO_START,
        PLAYING,
        GAME_OVER
    }
    private final UUID playerId;
    private final Player player;
    private final Nccasino plugin;
    private final String internalName;
    private final MinesInventory minesInventory;
    private final Map<String, Double> chipValues;
    private double selectedWager;
    private final Deque<Double> betStack = new ArrayDeque<>();
    public Boolean closeFlag = false;
    public GameState gameState;
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
    private boolean rebetEnabled = false; 
    // Adjusted fields for grid mapping
    private final int[] gridSlots = {
        2, 3, 4, 5, 6,
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24,
        29, 30, 31, 32, 33,
        38, 39, 40, 41, 42
    };
    private final List<Integer> gridSlotList = Arrays.stream(gridSlots).boxed().collect(Collectors.toList());

    private int instrumentIndex = 0;
    private int modeIndex = 0;

    private final Sound[] instruments = {
        Sound.BLOCK_NOTE_BLOCK_BANJO, 
        Sound.BLOCK_NOTE_BLOCK_BASEDRUM , 
         Sound.BLOCK_NOTE_BLOCK_BASS, 
         Sound.BLOCK_NOTE_BLOCK_BELL ,
         Sound.BLOCK_NOTE_BLOCK_BIT ,
         Sound.BLOCK_NOTE_BLOCK_CHIME,
         Sound.BLOCK_NOTE_BLOCK_COW_BELL,
         Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO ,
         Sound.BLOCK_NOTE_BLOCK_FLUTE ,
         Sound.BLOCK_NOTE_BLOCK_GUITAR ,
         Sound.BLOCK_NOTE_BLOCK_HARP ,
         Sound.BLOCK_NOTE_BLOCK_HAT,
         Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
         Sound.BLOCK_NOTE_BLOCK_PLING, 
         Sound.BLOCK_NOTE_BLOCK_SNARE, 
         Sound.BLOCK_NOTE_BLOCK_XYLOPHONE
    };

  private final String[] instrumentstrings = {
    "block.note_block.banjo",
    "block.note_block.basedrum",
    "block.note_block.bass",
    "block.note_block.bell",
    "block.note_block.bit",
    "block.note_block.chime",
    "block.note_block.cow_bell",
    "block.note_block.didgeridoo",
    "block.note_block.flute",
    "block.note_block.guitar",
    "block.note_block.harp",
    "block.note_block.hat",
    "block.note_block.iron_xylophone",
    "block.note_block.pling",
    "block.note_block.snare",
    "block.note_block.xylophone"};

    private final String[] modeNames = {"All", "Jazz", "Chords"};

    private final Map<Integer, float[]> tileNotes = new HashMap<>();



    // Field to keep track of selected mine count slot
    private int selectedMineSlot = -1;
    public MinesTable(UUID dealerId, Player player,  Nccasino plugin, String internalName, MinesInventory minesInventory) {
        super(player.getUniqueId(), 54, "Mines");
        this.playerId = player.getUniqueId();
        this.player = player;
        this.plugin = plugin;
        this.internalName = internalName;
        this.minesInventory = minesInventory;
        this.chipValues = new LinkedHashMap<>();
        // Initialize game state
        this.gameState = GameState.PLACING_WAGER;
        this.safePicks = 0;
        this.gameOver = false;
        int defMines;
        this.dealerId = dealerId;

        loadChipValuesFromConfig();
        if (!plugin.getConfig().contains("dealers." + internalName + ".default-mines")) {
            // If the key doesn't exist, set it to 100
            plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
            defMines=3;
        } else {
            // Retrieve the current value
            String value = plugin.getConfig().getString("dealers." + internalName + ".default-mines", "3").trim();
            
            try {
                defMines = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                defMines = 3; // Default
                plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
                plugin.saveConfig();
            }
        
            // Check if the value is greater than 100 or less than 0
            if (defMines > 24 ) {
                // Reset the value to 100
                plugin.getConfig().set("dealers." + internalName + ".default-mines", 24);
            }
            else if(defMines < 1){
                plugin.getConfig().set("dealers." + internalName + ".default-mines", 1);

            }
        }
        
        minesCount = defMines;
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage("§aWelcome to Mines.");
                break;     
            }
                case NONE:{
                break;
            }
        } 
        setMode(0);
        instrumentIndex=15;
        registerListener();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

    void initializeTable() {
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
                ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                    meta.setLore(null); // Ensure no lore
                    item.setItemMeta(meta);
                }
                inventory.setItem(i, item);
            }
        }
        updateRebetToggle();

        // Add "Start Game" lever if mines are selected (which they are by default)
        if (minesSelected) {
            updateStartGameLever(true);
        } else {
            updateStartGameLever(false);
        }
    
        inventory.setItem(36, createCustomItem(Material.SPRUCE_DOOR, "Refund/Exit", 1));
        // Add undo buttons
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.WIND_CHARGE, "Undo Last Bet", 1));
        inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, "All In", 1));
    }
    
   // Method to toggle rebet on/off
   private void updateRebetToggle() {
    Material material = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
    String name = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
    inventory.setItem(43, createCustomItem(material, name, 1));  // Slot 48
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
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                meta.setLore(null); // Ensure no lore
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
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
        inventory.setItem(53, createCustomItem(Material.PAPER, "Click here to place bet", 1));
        double currentBet=0;
        for(double t:betStack){
            currentBet+=t;
        }
        // If there is a current bet, update the lore
        if (currentBet > 0) {
            updateBetLore(53, currentBet);
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
                    ItemStack selectedMineOption = createEnchantedItem(Material.TNT, "Mines: " + mineCountOption, mineCountOption);
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
        ItemStack cashOutButton = createCustomItem(plugin.getCurrency(internalName), "Cash Out", 1);
        updateCashOutLore(cashOutButton);
        inventory.setItem(49, cashOutButton);
    }

    private void updateCashOutLore(ItemStack cashOutButton) {
        double payoutMultiplier = calculatePayoutMultiplier(safePicks);
        double totalBet = 0;
        for (double t : betStack) {
         totalBet += t;
        }
        double potentialWinnings = totalBet * payoutMultiplier;
        potentialWinnings = Math.round(potentialWinnings * 100.0) / 100.0;

        ItemMeta meta = cashOutButton.getItemMeta();
        if (meta != null) {
            meta.setLore(Collections.singletonList("Potential Winnings: " + potentialWinnings));
            cashOutButton.setItemMeta(meta);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinesTable)) return;

        if (!event.getWhoClicked().getUniqueId().equals(playerId)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        if (slot == 0) { // Instrument Button
            instrumentIndex = (instrumentIndex + 1) % instruments.length;
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§bInstrument set to: " + instruments[instrumentIndex].toString());
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
             if (SoundHelper.getSoundSafely("ui.button.click", player) != null)player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        
        if (slot == 1) { // Mode Button
            modeIndex = (modeIndex + 1) % modeNames.length;
            setMode(modeIndex);
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§eMode set to: " + modeNames[modeIndex]);
                                        break;     
                }
                    case NONE:{
                    break;
                }
            } 
            
             if (SoundHelper.getSoundSafely("ui.button.click", player) != null)player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        Boolean wow=false;
             if (tileNotes.containsKey(slot)) {
            if(slot==36||(slot>=43&&slot<=48)||(slot>=50&&slot<=53)){
                if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
                wow=true;
                }
            }
            if(!wow){
            Sound instrument = instruments[instrumentIndex];
            float[] pitches = tileNotes.get(slot);
        
            for (float pitch : pitches) {
                 if(SoundHelper.getSoundSafely(instrumentstrings[instrumentIndex],player)!=null)player.playSound(player.getLocation(), instrument, SoundCategory.MASTER, 1.0f, pitch);
            }}
           
        }
        
        if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
            handleWagerPlacement(clickedItem, slot, event);
        } else if (gameState == GameState.PLAYING) {
            // Adjusted to handle grid slots
            int index = gridSlotList.indexOf(slot);
            if (index != -1) {
                int x = index % gridSize;
                int y = index / gridSize;
                handleTileSelection(x, y);
            } else if (clickedItem != null && clickedItem.getType() == plugin.getCurrency(internalName)) {
                cashOut();
            }
        }

           // Handle Exit Button (Slot 36)
    if (slot == 36 && clickedItem != null && clickedItem.getType() == Material.SPRUCE_DOOR) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage("§cExiting the game...");                break;     
            }
                case NONE:{
                break;
            }
        } 
        
         if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.MASTER,1.0f, 1.0f);
        endGame(); // End the game and clean up resources
        player.closeInventory(); // Close the inventory
        return;
    }
        // Handle rebet toggle
        if (slot == 43&& clickedItem != null && clickedItem.getType() ==Material.RED_WOOL||clickedItem.getType() ==Material.GREEN_WOOL) {
            rebetEnabled = !rebetEnabled;
            if(clickedItem.getType() ==Material.RED_WOOL){
                 if (SoundHelper.getSoundSafely("entity.illusioner.prepare_mirror", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.MASTER,1.0f, 1.0f);
            }
            else{
                 if (SoundHelper.getSoundSafely("entity.illusioner.cast_spell", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.MASTER,1.0f, 1.0f);  
            }
           

            updateRebetToggle();
        }
        if (slot == 52&& clickedItem != null && clickedItem.getType() ==Material.SNIFFER_EGG) {
            // sum up how many currency items the player has
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
                 if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f);
                return;
            }
            // place that entire count as a single bet in the betStack
            betStack.push((double) count);
            removeWagerFromInventory(player, count);
            double totalBet = betStack.stream().mapToDouble(d -> d).sum();
            updateBetLore(53, totalBet);
            wager = count;
            wagerPlaced = true;
            updateStartGameLever(true);
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cAll in.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aAll in with " + (int)count + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(count) == 1 ? "" : "s") + "\n");                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
             if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER,1.0f, 1.0f);
            return;
        }
    }

    private void handleWagerPlacement(ItemStack clickedItem, int slot, InventoryClickEvent event) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();

        if (slot >= 47 && slot <= 51) {
            // Handle currency chips (slots 47-51)
             if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
            selectedWager = chipValues.getOrDefault(itemName, 0.0);
    
            // Update the display of all chips
            for (int i = 47; i <= 51; i++) {
                ItemStack chip = inventory.getItem(i);
                if (chip != null && chip.hasItemMeta()) {
                    String chipName = chip.getItemMeta().getDisplayName();
                    if (chipValues.containsKey(chipName)) {
                        // Enchant the clicked chip
                        if (i == slot) {
                            inventory.setItem(i, createEnchantedItem(plugin.getCurrency(internalName), chipName, (int) chipValues.get(chipName).doubleValue()));
                        } else {
                            // Reset others to their default state
                            inventory.clear(i);
                            inventory.setItem(i, createCustomItem(plugin.getCurrency(internalName), chipName, (int) chipValues.get(chipName).doubleValue()));
                        }
                    }
                }
            }
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
        }
        if (itemName.equals("Click here to place bet")) {
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
                boolean canBet = usedHeldItem || hasEnoughCurrency(player, (int) wagerAmount);
        
                if (canBet) {
                    // If the player was holding the item, remove it from the cursor
                    if (usedHeldItem) {
                        player.setItemOnCursor(null);
                    } else {
                        removeWagerFromInventory(player, (int) wagerAmount);
                    }
        
                    double newBetAmount = wagerAmount;
                    betStack.push(newBetAmount);
        
                    double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
                    updateBetLore(53, totalBet);
        
                    if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            break;
                        case VERBOSE:
                            player.sendMessage("§aBet placed: " + newBetAmount);
                            break;
                        case NONE:
                            break;
                    }
        
                    wager = newBetAmount;
                    wagerPlaced = true;
                    updateStartGameLever(true);
                } else {
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            player.sendMessage("§cInvalid action.");
                            break;
                        case VERBOSE:
                            player.sendMessage("§cNot enough " + plugin.getCurrencyName(internalName).toLowerCase() + "s.");
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
                        player.sendMessage("§cSelect a wager amount first.");
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
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
                        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                            case STANDARD:{
                                break;}
                            case VERBOSE:{
                                player.sendMessage("§d# of mines: " + minesCount);
                                break;     
                            }
                                case NONE:{
                                break;
                            }
                        } 
                        minesSelected = true;

                        // Update the selected mine slot
                        selectedMineSlot = slot;
                         if (SoundHelper.getSoundSafely("block.lantern.break", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.MASTER,1.0f, 1.0f);
                        // Change the selected slot to stack of red glass panes
                                   ItemStack selectedMineOption = createEnchantedItem(Material.TNT, "Mines: " + minesCount, minesCount);

                        inventory.setItem(slot, selectedMineOption);

                        // Update "Start Game" lever visibility
                        updateStartGameLever(true);
                    } else {
                        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                            case STANDARD:{
                                player.sendMessage("§cInvalid action.");
                                break;}
                            case VERBOSE:{
                                player.sendMessage("§cInvalid number of mines.");
                                break;     
                            }
                                case NONE:{
                                break;
                            }
                        } 
                         if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cError parsing number of mines.");
                     if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
                }
            }
            return;
        }

        if (itemName.equals("Start Game")) {
            // Start the gamet
            if (minesSelected) {
                if (wager > 0) {
                     if (SoundHelper.getSoundSafely("block.enchantment_table.use", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER,1.0f, 1.0f);
                    startGame();
                } else {
                    switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage("§cInvalid action.");
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§cPlace a wager first.");
                            break;     
                        }
                            case NONE:{
                            break;
                        }
                    } 
                     if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
                }
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cSelect number of mines.");
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

        // Undo all bets
        if (slot == 45) {
            if(!betStack.isEmpty()){
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§dAll bets undone.");                       
                         break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
            refundAllBets(player);
            betStack.clear();
             if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
            updateBetLore(53, 0);  // Reset the lore on the bet option after clearing bets
            wager = 0;
            wagerPlaced = false;
            return;}
            else{
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");

                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cNo bets to undo.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
            }
        }

        // Undo last bet
        if (slot == 46) {
            if (!betStack.isEmpty()) {
                double indBet=betStack.pop();
                refundBet(player, (int) indBet);
                double totalBet=0;
                for(double t:betStack){
                    totalBet+=t;
                }
                if (totalBet < 0) totalBet = 0;
                updateBetLore(53, totalBet);
                wagerPlaced = totalBet > 0;
                 if (SoundHelper.getSoundSafely("ui.toast.in", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
                 if (SoundHelper.getSoundSafely("ui.toast.out", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);
                 switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§dLast bet undone.");                         
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
 
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");

                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cNo bets to undo.");
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

        double totalBet = 0;
        for (double t : betStack) {
         totalBet += t;
        }
        // Set previous wager
        previousWager = totalBet;
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
        //player.sendMessage("§c§lGame over.");
        return;
    }

    if (revealedGrid[x][y]) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§cInvalid action.");

                break;}
            case VERBOSE:{
                player.sendMessage("§dTile already revealed.");
             break;     
            }
                case NONE:{
                break;
            }
        } 
        return;
    }

    if (mineGrid[x][y]) {
       // Change the cash-out button to a barrier immediately
       updateCashOutToBarrier();
        
       updateTile(x, y, true);
      // Pass the coordinates of the mine hit
     if (SoundHelper.getSoundSafely("entity.creeper.primed", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.MASTER,1.0f, 1.0f);
       // Trigger a visual explosion at the player's location without causing damage


       Bukkit.getScheduler().runTaskLater(plugin, () -> {
    
     startMineHitAnimation(x, y); 
                }, 15L); 

       gameOver = true;
       gameState = GameState.GAME_OVER;
       switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
        case STANDARD:{
            player.sendMessage("§c§lGame Over!");

            break;}
        case VERBOSE:{
            player.sendMessage("§c§lGame Over!");
            break;     
        }
            case NONE:{
            break;
        }
    } 
        } else {
            // Safe tile clicked
        revealedGrid[x][y] = true;
        safePicks++;
        updateTile(x, y, false);  // Reveal a safe tile

        // Optionally update cash out button with updated potential winnings
        updateCashOutLore(inventory.getItem(49));
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage("§aSafe pick!");                
                break;     
            }
                case NONE:{
                break;
            }
        } 

        float basePitch = 0.8f;
        float incremental = 0.05f * safePicks;  
        float finalPitch = basePitch + incremental;


        finalPitch = Math.min(finalPitch, 2.0f); // 2.0 is max in vanilla MC
         if (SoundHelper.getSoundSafely("entity.experience_orb.pickup", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, finalPitch);
            // Optionally check if the game is won (all safe tiles cleared)
            if (safePicks == (totalTiles - minesCount)) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§a§lAll safe tiles cleared!");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§a§lAll safe tiles cleared!");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
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
            tile = createCustomItem(plugin.getCurrency(internalName), "Safe", 1);
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
                 if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);  
                
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
            //player.sendMessage("§cGame over.");
            return;
        }
    
        // Step 1: Reveal all tiles (mines and safes)
        revealAllTiles();
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
        Random random = new Random();
  
        float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
        for (int i = 0; i < 3; i++) {
            float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
             if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,1.0f,chosenPitch);
        }

        // Step 2: Start emerald expansion from the cash-out button (slot 49)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startEmeraldExpansion(49);
            
            
            double totalBet = 0;
            for (double t : betStack) {
             totalBet += t;
            }
            double winnings;
            // If no tiles were clicked, return the exact wager
            if (safePicks == 0) {
                winnings = totalBet; // No multiplier, return exact wager
            } else {
                // Give the winnings to the player with payout multiplier applied
                double payoutMultiplier = calculatePayoutMultiplier(safePicks);
                winnings = totalBet * payoutMultiplier;
            }
    
            winnings = applyProbabilisticRounding(winnings,player); 
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§a§lPaid "+ (int)winnings+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(winnings) == 1 ? "" : "s"));
                    break;}
                case VERBOSE:{
                    player.sendMessage("§a§lPaid "+ (int)winnings+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(winnings) == 1 ? "" : "s")  + "\n §r§a§o(profit of "+(int)Math.abs(winnings-totalBet)+")");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            giveWinningsToPlayer(winnings);
       
            gameOver = true;
            gameState = GameState.GAME_OVER;
    
            // Reset the game after a delay
            Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 20L); // Wait 5 seconds before resetting
    
        }, 10L); // Wait 2 seconds before starting the emerald expansion
    }
    
    private double applyProbabilisticRounding(double value,Player  player) {
        int integerPart = (int) value;

        double fractionalPart = value - integerPart;

        Random random = new Random();
        if (random.nextDouble() <= fractionalPart) {
            return integerPart + 1; // Round up based on probability
        }
        return integerPart; // Otherwise, keep it rounded down
    }
    

    void closeCashOut() {
        if (gameOver) {
            //player.sendMessage("§cGame over.");
            return;
        }
     

            // Slot 49 is the cash-out button position
            double totalBet = 0;
            for (double t : betStack) {
             totalBet += t;
            }
            double winnings;
            // If no tiles were clicked, return the exact wager
            if (safePicks == 0) {
                winnings = totalBet; // No multiplier, return exact wager
            } else {
                // Give the winnings to the player with payout multiplier applied
                double payoutMultiplier = calculatePayoutMultiplier(safePicks);
                winnings = totalBet * payoutMultiplier;
            }
            winnings = applyProbabilisticRounding(winnings,player); 
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§a§lPaid "+(int)winnings+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(winnings) == 1 ? "" : "s"));
                    break;}
                case VERBOSE:{
                    player.sendMessage("§a§lPaid "+(int)winnings+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(winnings) == 1 ? "" : "s")  + " (profit of "+(int)Math.abs(winnings-totalBet)+")\n");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
    
            if (winnings > 0) {
                giveWinningsToPlayer(winnings);
            }
    
            gameOver = true;
            gameState = GameState.GAME_OVER;
    
            //check if properly closed?
    }

    // Step 2: Emerald expansion animation from the cash-out button (slot 49)
    private void startEmeraldExpansion(int centerSlot) {
        int centerX = centerSlot % 9;
        int centerY = centerSlot / 9;
        spreadEmeraldsFrom(centerX, centerY, 1); // Start with a radius of 1
    }
    
    private void spreadEmeraldsFrom(int centerX, int centerY, int currentRadius) {
        // Expansion of emeralds from the center
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean anyTilesSetToEmerald = false;
    
            for (int y = 0; y < 6; y++) { // 6 rows
                for (int x = 0; x < 9; x++) { // 9 columns
                    int distance = Math.max(Math.abs(centerX - x), Math.abs(centerY - y));
                    if (distance == currentRadius) {
                        int index = y * 9 + x;
                        if (index >= 0 && index < inventory.getSize()) {
                            // Overwrite every tile with an emerald
                            setTileAtSlot(index, plugin.getCurrency(internalName), "MMMMMoney!!");
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
        betStack.clear();
        // Keep the number of mines the same
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage("§d# of mines: " + minesCount );
                break;     
            }
                case NONE:{
                break;
            }
        } 
        

        // Handle rebet logic
        if (rebetEnabled && previousWager > 0) {
            if (hasEnoughCurrency(player, (int) previousWager)) {
                // Check if the player still has the MinesTable open before deducting the bet
                InventoryView openInventory = player.getOpenInventory();
                if (openInventory != null && openInventory.getTopInventory().getHolder() instanceof MinesTable) {
                    removeWagerFromInventory(player, (int) previousWager);
                    betStack.push(previousWager);
                    updateBetLore(53, previousWager);
                    switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage("§dRebet of " + (int) previousWager + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(previousWager) == 1 ? "" : "s") + " placed\n");
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§dRebet of " + (int) previousWager + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(previousWager) == 1 ? "" : "s") + " placed\n");
                            break;     
                        }
                            case NONE:{
                            break;
                        }
                    } 
                    wagerPlaced = true;
                } 
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§c2 broke 4 rebet.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§c2 broke 4 rebet.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                wager = 0;
                betStack.clear();
                updateBetLore(53, wager);
                wagerPlaced = false;
            }
        }

         else {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cRebet off, wager reset to 0.");
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            wager = 0;
            betStack.clear();
            updateBetLore(53, wager);
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
                    lore.add("Wager: " + (int)totalBet + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalBet) == 1 ? "" : "s") + "\n");
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
            inventory.setItem(44, createCustomItem(Material.LEVER, "Start Game", 1)); // Slot 53
        } else {
            inventory.setItem(44, null); // Remove the lever if conditions not met
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
        double totalBet=0;
        for(double t:betStack){
            totalBet+=t;
        }
        refundBet(player, (int) totalBet);
    }

    private void refundBet(Player player, int amount) {
        if (amount <= 0) return; // No refund needed for zero amount
    
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
    
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(currencyMaterial, 64);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
        if (remainder > 0) {
            ItemStack stack = new ItemStack(currencyMaterial, remainder);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
    }
    

    private void giveWinningsToPlayer(double amount) {
        if (amount <= 0) return; // No winnings to give
        int totalAmount = (int) Math.floor(amount);
        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
        int totalDropped = 0; // Track how many items were dropped
    
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(currencyMaterial, 64);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }
    
        if (remainder > 0) {
            ItemStack stack = new ItemStack(currencyMaterial, remainder);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }
    
        // Print total dropped if any items couldn't fit in inventory
        if (totalDropped > 0) {     
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§cNo room for " + (int) totalDropped + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalDropped) == 1 ? "" : "s") + ", dropping...");
                break;}
            case VERBOSE:{
                player.sendMessage("§cNo room for " + (int) totalDropped + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalDropped) == 1 ? "" : "s") + ", dropping...");
                break;     
            }
                case NONE:{
                break;
            }
        } 
        }
    }
    

    void endGame() {
        if (player != null) {
            if (gameState == GameState.PLACING_WAGER || gameState == GameState.WAITING_TO_START) {
                refundAllBets(player);  // Refund any remaining bets
            }
            betStack.clear();
        }
        
        InventoryView openInventory = player.getOpenInventory();
        boolean playerStillInMines = (openInventory != null && openInventory.getTopInventory().getHolder() instanceof MinesTable);

        if (wagerPlaced && rebetEnabled && previousWager > 0 && !playerStillInMines) {
            refundBet(player, (int) previousWager);
        }

        betStack.clear();
    

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

        if (event.getInventory().getHolder() instanceof MinesTable && event.getPlayer().getUniqueId().equals(playerId)) {
            if (gameState == GameState.PLAYING) {
                closeCashOut();  // Automatically cash out the player
            }
            endGame();  // Call the end game logic when the inventory is closed
        }
    }

    public ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
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
    
    
private void setMode(int modeIndex) {
    List<Integer> tileKeys = Arrays.asList(45,46,47,48,50,51,52,53,36,37,43,44,27,28,34,35,18,19,25,26,9,10,16,17,7,8);

    switch(modeIndex)
{
    case 0: { // Every Note Mode (Chromatic Scale)
        tileNotes.clear();
        float[] chromaticScale = {
            0.5f,  // G♭3
            0.53f, // G3
            0.56f, // A♭3
            0.6f,  // A3
            0.63f, // B♭3
            0.67f, // B3
            0.7f,  // C4
            0.75f, // D♭4
            0.8f,  // D4
            0.85f, // E♭4
            0.9f,  // E4
            0.95f, // F4
            1.0f,  // G♭4
            1.06f, // G4
            1.12f, // A♭4
            1.18f, // A4
            1.25f, // B♭4
            1.33f, // B4
            1.41f, // C5
            1.5f,  // D♭5
            1.6f,  // D5
            1.7f,  // E♭5
            1.8f,  // E5
            1.9f,  // F5
            2.0f, // G♭5
            0.5f   
        };
        
        for (int i = 0; i < tileKeys.size() && i < chromaticScale.length; i++) {
            tileNotes.put(tileKeys.get(i), new float[]{chromaticScale[i]});
        }
        break;
    }
case 1:{
    tileNotes.clear();
        float[] azzyScale = {
            0.5f,  // G♭3
            0.6f,  // A3
            0.67f, // B3
            0.7f,  // C4
            0.75f, // D♭4
            0.9f,  // E4
            1.0f,  // G♭4
            1.18f, // A4
            1.33f, // B4
            1.41f, // C5
            1.5f,  // D♭5
            1.8f,  // E5
            2.0f, // G♭5         
            0.67f, // B3       
            0.8f,  // D4       
            0.9f,  // E4
            0.95f, // F4
            1.0f,  // G♭4
            1.18f, // A4
            1.33f, // B4
            0.9f,  // E4
            1.06f, // G4
            1.18f, // A4
            1.25f, // B♭4
            1.33f, // B4
            1.6f,  // D5
        };
        
        for (int i = 0; i < tileKeys.size() && i < azzyScale.length; i++) {
            tileNotes.put(tileKeys.get(i), new float[]{azzyScale[i]});
        }
        break;
}
case 2: { // Unique Chords for Each Tile
    tileNotes.clear();

    float[][] chordPresets = {
        {0.60f, 0.91f, 1.33f},  // B♭ minor 7
        {1.12f, 0.75f, 1.50f},  // C dominant 9
        {0.67f, 1.27f, 1.06f},  // F# augmented 7
        {1.42f, 0.85f, 1.19f},  // A minor 11
        {0.81f, 1.61f, 0.54f},  // D# minor 7
        {1.31f, 0.56f, 0.88f},  // G diminished 7
        {0.79f, 1.44f, 0.95f},  // E♭ major 13
        {1.25f, 0.63f, 1.08f},  // B dominant 9
        {0.53f, 1.72f, 1.04f},  // C# minor 9
        {1.82f, 0.69f, 1.33f},  // F dominant 7
        {0.73f, 1.90f, 0.67f},  // A♭ major 7
        {1.47f, 0.51f, 1.61f},  // D minor 7♭5
        {0.50f, 1.56f, 0.85f},  // G♭ dominant 7♯9
        {1.88f, 0.79f, 1.11f},  // B♭ minor 9
        {0.94f, 1.35f, 1.71f},  // E dominant 7
        {1.53f, 0.67f, 1.99f},  // G major 9
        {0.59f, 1.23f, 1.42f},  // A diminished 7
        {1.04f, 0.75f, 1.79f},  // C dominant 11
        {0.91f, 1.18f, 1.65f},  // D# minor 7♭9
        {1.27f, 0.83f, 1.06f},  // F# augmented
        {1.99f, 0.50f, 1.47f},  // B dominant 7♭5
        {0.56f, 1.09f, 1.92f},  // G diminished 9
        {1.16f, 0.73f, 1.88f},  // A♭ major 13
        {0.81f, 1.35f, 1.22f},  // E♭ dominant 7♯11
        {1.74f, 0.69f, 1.50f},  // D major 7
        {0.60f, 1.44f, 0.95f}
    };

    
    for (int i = 0; i < tileKeys.size(); i++) {
        tileNotes.put(tileKeys.get(i), chordPresets[i]);
    }
    break;
}
default :{
    for (int i = 0; i < 25; i++) {
        tileNotes.put(tileKeys.get(i), new float[] {(1.0f)});
    }
    break;
}
}   
}
}
