package org.nc.nccasino.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;

public class BlackjackInventory extends DealerInventory implements Listener {

    private final Nccasino plugin; // Reference to the main plugin
    private final Map<String, Double> chipValues; // Track chip values
    private final String internalName; // Internal name for config lookup
    private final Map<UUID, Integer> playerSeats; // Track player seats
    private final Map<UUID, Map<Integer, Double>> playerBets; // Track player bets by slot number
    private final Map<UUID, List<Double>> lastBetAmounts; // Track the last bet amounts placed by the player
    private boolean gameStarted; // Track whether the game has started
    private boolean gameActive; // Track whether the game is active
    private double selectedWager; // Track the selected wager
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private int countdownTaskId; // Task ID for the countdown timer
    private UUID currentPlayerId; // Track the current player
    private Iterator<UUID> playerIterator; // Iterator for player turns
    private final Map<UUID, Integer> playerCardCounts = new HashMap<>(); // Track number of cards each player has
    private final Map<UUID, Boolean> playerDone = new HashMap<>(); // Track whether the player is done (stood or busted)
    private final Map<UUID, List<ItemStack>> playerHands = new HashMap<>();
    private final List<ItemStack> dealerHand = new ArrayList<>();
    private final Map<UUID, Double> selectedWagers = new HashMap<>();
    private final Object turnLock = new Object(); // Lock object for turn actionsactions
    private final Map<UUID, Boolean> playerTurnActive = new HashMap<>();
private Deck deck; // Declare the deck as a class variable
private Boolean firstopen=true;
private Boolean firstFin=true;
private String dealerName= "";

    public BlackjackInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 54, "Blackjack Table"); // Using 54 slots for start menu
        this.plugin = plugin; // Store the plugin reference
        this.chipValues = new HashMap<>(); // Initialize chip values storage
        this.internalName = internalName; // Store the internal name
        this.gameStarted = false; // Initialize game started flag
        this.gameActive = false; // Initialize game active flag
        this.playerSeats = new HashMap<>(); // Initialize player seats storage
        this.playerBets = new HashMap<>(); // Initialize player bets storage
        this.lastBetAmounts = new HashMap<>(); // Initialize last bet amounts storage
        this.selectedWager = 0; // Initialize selected wager
        this.countdownTaskId = -1; // Initialize countdown task ID
        this.deck = new Deck(1); // Initialize the deck
        loadChipValuesFromConfig(); // Load chip values from config
        // Initialize the start menu
        this.dealerName = dealerName;
        Nccasino nccasino = (Nccasino) plugin;

        // Check if the configuration key exists
        if (!nccasino.getConfig().contains("dealers." + internalName + ".stand-on-17")) {
            // If the key doesn't exist, set it to 100
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        } else {
            // Retrieve the current value
            int hasStand17Config = nccasino.getConfig().getInt("dealers." + internalName + ".stand-on-17");
        
            // Check if the value is greater than 100 or less than 0
            if (hasStand17Config > 100 || hasStand17Config < 0) {
                // Reset the value to 100
                nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            }
        }
        
        
       registerListener();
       plugin.addInventory(dealerId, this);
    }

private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

 @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            if(!firstopen){
            startAnimation(player);}
        }
    }

      @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event){
        Player player=(Player)event.getPlayer();
        if(player.getInventory() !=null){
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if(player.getOpenInventory().getTopInventory()== this.getInventory()){
                    if(firstopen){
                        firstopen=false;
                        startAnimation((Player)event.getPlayer());
                    }
                }
            }, 2L);    
    }
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
            animationTable.animateMessage(player, () -> afterAnimationComplete(player)); // Wrap the method call in a lambda
        }, 1L); // Delay by 1 tick to ensure smooth opening of inventory
    }
    
    private void afterAnimationComplete(Player player) {
        // Add a slight delay to ensure smooth transition from the animation to the table
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
           
            if (player != null) {
                if(firstFin){
            firstFin=false;
            initializeGameMenu();

                }

                player.openInventory(this.getInventory());
                // No need to register the listener here since it's handled in the constructor
            }
        }, 1L); // Delay by 1 tick to ensure clean transition between inventories
    }
 

    // Load chip values from the plugin config
    private void loadChipValuesFromConfig() {
        for (int i = 1; i <= 5; i++) {
            String chipName = plugin.getChipName(internalName, i);
            double chipValue = plugin.getChipValue(internalName, i);
            if (chipValue > 0) { // Ensure chip value is positive
                this.chipValues.put(chipName, chipValue);
            }
        }
    }

    // Initialize Blackjack-specific start menu
    private void initializeStartMenu() {
        inventory.clear(); // Clear the inventory before setting up the page
        addItem(createCustomItem(Material.BLACK_WOOL, "Start Blackjack"), 22); // Add start button at the center
    }

    // Initialize Blackjack-specific game menu
    private void initializeGameMenu() {
        inventory.clear(); // Clear the inventory before setting up the page

        for (UUID playerId : playerSeats.keySet()) {
            int seatSlot = playerSeats.get(playerId);
            inventory.setItem(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
        }
        // Add the necessary items for the game menu
        addItem(createCustomItem(Material.CREEPER_HEAD, "Dealer"), 0); // Dealer
        // The Game Info lever stack will be added when the timer starts.

        // Add chairs
        addItem(createCustomItem(Material.OAK_STAIRS, "Click to sit here"), 9); // Chair 1
        addItem(createCustomItem(Material.OAK_STAIRS, "Click to sit here"), 18); // Chair 2
        addItem(createCustomItem(Material.OAK_STAIRS, "Click to sit here"), 27); // Chair 3

        // Add betting papers
        addItem(createCustomItem(Material.PAPER, "Click here to place bet"), 10); // Bet 1
        addItem(createCustomItem(Material.PAPER, "Click here to place bet"), 19); // Bet 2
        addItem(createCustomItem(Material.PAPER, "Click here to place bet"), 28); // Bet 3

        // Add game action buttons
        addItem(createCustomItem(Material.DIAMOND_SWORD, "Hit"), 36); // Hit
        ItemStack item = inventory.getItem(36);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier("foo",0,AttributeModifier.Operation.MULTIPLY_SCALAR_1)); // This is necessary as of 1.20.6
        for(ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        addItem(createCustomItem(Material.SHIELD, "Stand"), 37); // Stand
        addItem(createCustomItem(Material.PAPER, "Double Down"), 38); // Double Down
        //addItem(createCustomItem(Material.SHEARS, "Split"), 39); // Split
        //addItem(createCustomItem(Material.TOTEM_OF_UNDYING, "Insurance"), 40); // Insurance

        // Add undo options and chip denominations
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Undo Last Bet", 1));

        int slot = 47;
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(chipValues.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Double> entry : sortedEntries) {
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            slot++;
        }

        // Add leave chair option with a wooden door
        inventory.setItem(53, createCustomItem(Material.OAK_DOOR, "Leave Chair", 1));
    }

    // Create an item stack with a custom display name
    public ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Create an item stack with a custom display name and amount
    private ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }
        ItemStack itemStack = new ItemStack(material, amount);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Set custom item metadata
    private void setCustomItemMeta(ItemStack itemStack, String name) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>()); // Clear any existing lore
            meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES
            ); // Hide all relevant item flags
            itemStack.setItemMeta(meta); // Set the item meta after making changes
        }
    }

// Create a player head item stack with the player's actual head and stack size
private ItemStack createPlayerHeadItem(Player player, int stackSize) {
    if (stackSize <= 0) {
        throw new IllegalArgumentException("Stack size must be greater than 0 for player head.");
    }

    ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, stackSize);
    SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
    if (skullMeta != null) {
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(player.getName());
        playerHead.setItemMeta(skullMeta);
    }
    return playerHead;
}

@Override
public void handleClick(int slot, Player player, InventoryClickEvent event) {
    event.setCancelled(true); // Ensure the event is cancelled to prevent unintended item movement

    UUID playerId = player.getUniqueId();

    if (clickAllowed.getOrDefault(playerId, true)) {
        clickAllowed.put(playerId, false); // Set click allowed to false for this player
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L); // Delay for click handling
        /* 
        if (!gameStarted) { // Handle clicks in the start menu
            if (slot == 22) { // Start Blackjack button clicked
                player.sendMessage("Starting Blackjack...");
                gameStarted = true; // Set game started flag
                initializeGameMenu(); // Switch to game menu
                player.openInventory(this.getInventory());
            }
        } else */
        
        if (gameActive) { // Game is active, handle player actions
            if (playerId.equals(currentPlayerId)) {
                handlePlayerAction(player, slot);
            } else if (slot == 53) { // Handle leave chair
                handleLeaveChairDuringGame(player);
            } else if (slot >= 10 && slot <= 28 && slot % 9 == 1) { // Bet slots
                player.sendMessage("§cInvalid action");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            }
        } else { // Handle clicks in the game menu before the game starts
            if (slot >= 9 && slot <= 27 && slot % 9 == 0) { // Chair slots (9, 18, 27)
                handleChairClick(slot, player);
            } else if (slot == 53) { // Leave chair
                handleLeaveChair(player);
            } else if (slot >= 10 && slot <= 28 && slot % 9 == 1) { // Bet slots (10, 19, 28)
                handleBetClick(slot, player);
            } else if (slot >= 47 && slot <= 51) { // Chip selection
                handleChipSelection(player, event.getCurrentItem());
            } else {
                switch (slot) {
                    case 45:
                        handleUndoAllBets(player);
                        break;
                    case 46:
                        handleUndoLastBet(player);
                        break;
                    default:
                        // Handle other slots if needed
                        break;
                }
            }
        }
    } else {
        player.sendMessage("§cPlease wait before clicking again!");
    }
}

private void handlePlayerAction(Player player, int slot) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();

        // Allow the player to leave during their turn
        if (slot == 53) {
            handleLeaveChairDuringGame(player);
            return;
        }

        // Check if the player's turn is still active
        if (!playerTurnActive.getOrDefault(playerId, false)) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }

        // Disable further actions until the current one is processed
        playerTurnActive.put(playerId, false);

        switch (slot) {
            case 36: // Hit
                handleHit(player);
                player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
                break;
            case 37: // Stand
                handleStand(player);
                break;
            case 38: // Double Down
                handleDoubleDown(player);
                break;
            //case 40: // Insurance
              //  handleInsurance(player);
                //break;
            default:
                player.sendMessage("§cInvalid action ");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                playerTurnActive.put(playerId, true); // Re-enable actions if the action was invalid
        }
    }
}

private void handleHit(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        int seatSlot = playerSeats.get(playerId);
        int cardCount = playerCardCounts.getOrDefault(playerId, 2); // Default to 2 because of the initial 2 cards dealt
        int nextCardSlot = seatSlot + 2 + cardCount; // Calculate the next slot based on the number of cards

        Card newCard = deck.dealCard();
        scheduleCardDealingWithDelay(nextCardSlot, newCard, 20L, playerId); // Deal the card with a delay

        cardCount++; // Increment the card count
        playerCardCounts.put(playerId, cardCount); // Update the card count

        // Delay the hand value calculation to ensure the card is fully added to the player's hand
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int handValue = calculateHandValue(playerHands.get(playerId));

            if (handValue == 21) {
                
                playerTurnActive.put(playerId, false); // Deactivate the player's turn
                startNextPlayerTurnWithDelay(20L); // Start next player's turn with delay
            } else if (handValue > 21) {


                playerDone.put(playerId, true); // Mark the player as done
                playerTurnActive.put(playerId, false); // Deactivate the player's turn
                startNextPlayerTurnWithDelay(20L); // Start next player's turn with delay
           
            } else {
                
                    playerTurnActive.put(playerId, true); // Allow more actions since the player hasn't busted
                    allowPlayerActions(player); // Continue player's turn with delay
               
            }
        }, 40L); // The delay should be enough to ensure that the card has been added
    }
}

private void handleStand(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        playerDone.put(playerId, true); // Mark the player as done
        playerTurnActive.put(playerId, false); // Deactivate the player's turn
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER,1.0f, 1.0f);
        player.sendMessage("§9You stood.");
        startNextPlayerTurnWithDelay(20L); // Start next player's turn with delay
    }
}

private void startNextPlayerTurnWithDelay(long delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> startNextPlayerTurn(), delay);
}

private void handleDoubleDown(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        double currentBet = playerBets.get(playerId).values().stream().mapToDouble(Double::doubleValue).sum();

        if (!hasEnoughWager(player, currentBet)) {
            player.sendMessage("§cNot enough funds.");
            playerTurnActive.put(playerId, true); // Allow more actions since the double down failed
            allowPlayerActions(player); // Continue player's turn
            return;
        }

        // Remove the additional wager from the player's inventory
        removeWagerFromInventory(player, currentBet);

        // Double the bet amount in the player's bets
        playerBets.get(playerId).replaceAll((k, v) -> v * 2);

        // Update the lore to reflect the doubled bet
        for (Map.Entry<Integer, Double> entry : playerBets.get(playerId).entrySet()) {
            int slot = entry.getKey();
            double updatedBet = entry.getValue();
            updateItemLore(slot, updatedBet);
        }

        // After doubling down, the player gets exactly one more card
        handleHit(player);
        
        playerDone.put(playerId, true); // Mark the player as done
        playerTurnActive.put(playerId, false); // Deactivate the player's turn after doubling down
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f); 
        player.sendMessage("§9You doubled down.");

        startNextPlayerTurnWithDelay(20L); // Start next player's turn with delay
    }
}

private void handleInsurance(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        double currentBet = playerBets.get(playerId).values().stream().mapToDouble(Double::doubleValue).sum();
        double insuranceBet = currentBet / 2;

        if (!hasEnoughWager(player, insuranceBet)) {
            player.sendMessage("§cNot enough funds.");
            allowPlayerActions(player); // Continue player's turn
            return;
        }

        if (inventory.getItem(2) != null && inventory.getItem(2).getItemMeta().getDisplayName().contains("ACE")) {
            removeWagerFromInventory(player, insuranceBet);
            player.sendMessage("§6Insurance taken. ");
        }

        allowPlayerActions(player); // Continue player's turn
    }
}

    // Handle chair click
    private void handleChairClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();

        // Check if the player is already sitting in a chair
        if (playerSeats.containsKey(playerId)) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE,SoundCategory.MASTER, 1.0f, 1.0f); 
        // Set the player's actual head at the chair's position
        inventory.setItem(slot, createPlayerHeadItem(player, 1));

        // Track the player's seat
        playerSeats.put(playerId, slot);
    }
    

// Handle leave chair during the countdown or active game
private void handleLeaveChair(Player player) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

    int chairSlot = playerSeats.remove(playerId);

    player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f); 
    // Reset the chair to its original state
    inventory.setItem(chairSlot, createCustomItem(Material.OAK_STAIRS, "Click to sit here"));

    // If the timer is still running, undo all bets
    if (countdownTaskId != -1 && !gameActive) {
        handleUndoAllBets(player);
    } else {
        removePlayerData(playerId);
    }

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        cancelGame();
    }
}

// Handle leave chair during an active game
private void handleLeaveChairDuringGame(Player player) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

    // If it's the player's turn, end their turn immediately
    if (playerId.equals(currentPlayerId)) {
        playerDone.put(playerId, true); // Mark the player as done
        startNextPlayerTurnWithDelay(20L); // Start next player's turn with delay
    }

    int chairSlot = playerSeats.remove(playerId);

    // Remove all the player's associated data
    removePlayerData(playerId);

    player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f); 
    // Reset the chair to its original state
    inventory.setItem(chairSlot, createCustomItem(Material.OAK_STAIRS, "Click to sit here"));

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        cancelGame();
    }
}

private void removePlayerData(UUID playerId) {
    // Retrieve the player's seat slot
    int seatSlot = playerSeats.getOrDefault(playerId, -1);

    // If the player has a valid seat slot
    if (seatSlot != -1) {
        // Clear the player's cards from the table
        List<ItemStack> hand = playerHands.get(playerId);
        if (hand != null) {
            for (int i = 0; i < hand.size(); i++) {
                inventory.setItem(seatSlot + 2 + i, new ItemStack(Material.AIR)); // Clear each card slot in the player's row
            }
        }

        // Remove the player's head from the seat
        inventory.setItem(seatSlot, createCustomItem(Material.OAK_STAIRS, "Click to sit here"));

        // Remove player's data from tracking maps
        playerHands.remove(playerId);
        playerCardCounts.remove(playerId);
        playerTurnActive.remove(playerId);
        playerDone.remove(playerId);

        // Remove player's bets and related lore
        clearPlayerBetLore(playerId);
        clearPlayerBets(playerId);

        // Remove player from seat map
        playerSeats.remove(playerId);

        // Ensure player is removed from active turns
        if (playerIterator != null) {
            List<UUID> remainingPlayers = new ArrayList<>();
            playerIterator.forEachRemaining(remainingPlayers::add);
            remainingPlayers.remove(playerId);
            playerIterator = remainingPlayers.iterator();
        }
    }

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        cancelGame();
    }
}

    // Handle chip selection
    private void handleChipSelection(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return;
        }
    
        UUID playerId = player.getUniqueId();
        String itemName = clickedItem.getItemMeta().getDisplayName();
        double selectedWager = chipValues.getOrDefault(itemName, 0.0);
        player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
    
        selectedWagers.put(playerId, selectedWager);
        
    }
    
    private void clearPlayerBetLore(UUID playerId) {
        Map<Integer, Double> bets = playerBets.get(playerId);
        if (bets != null) {
            for (int slot : bets.keySet()) {
                updateItemLore(slot, 0); // Clear the lore
            }
        }
    }
    
    // Handle bet click
    private void handleBetClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();
        double selectedWager = getSelectedWager(player.getUniqueId());
    
        // Ensure the player is sitting before placing a bet
        if (!playerSeats.containsKey(playerId)) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        // Ensure the player can only bet on their own paper
        int chairSlot = playerSeats.get(playerId);
        int betSlot = chairSlot + 1; // Paper is always right next to the chair
    
        if (slot != betSlot) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        // Place the bet if valid
        if (selectedWager > 0 && hasEnoughWager(player, selectedWager)) {
            removeWagerFromInventory(player, selectedWager);
            Map<Integer, Double> bets = playerBets.computeIfAbsent(playerId, k -> new HashMap<>());
    
            double currentBet = bets.getOrDefault(betSlot, 0.0);
            bets.put(betSlot, currentBet + selectedWager);
            updateItemLore(betSlot, bets.get(betSlot));
    
            lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(selectedWager); // Store the last bet amount
    
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f);
    
            if (countdownTaskId == -1) { // Start the countdown if it's not already started
                startCountdownTimer();
            }
        } else {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
        }
    }

    private void handleUndoAllBets(Player player) {
        if (gameActive) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        UUID playerId = player.getUniqueId();
        Map<Integer, Double> bets = playerBets.get(playerId);
    
        if (bets != null && !bets.isEmpty()) {
            double totalRefund = bets.values().stream().mapToDouble(Double::doubleValue).sum();
            addWagerToInventory(player, totalRefund);
            clearPlayerBetLore(playerId);  // Clear lore for items related to this player
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);
    
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
            // Check if there are no bets left for any player
            if (playerBets.isEmpty()) {
                stopCountdownTimer(); // Stop the timer if no bets are left for any player
            }
        }
    }
    
    private double getSelectedWager(UUID playerId) {
        return selectedWagers.getOrDefault(playerId, 0.0);
    }

    private void stopCountdownTimer() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
            // Clear the Game Info lever stack to reset the display
            inventory.setItem(1, createCustomItem(Material.CLOCK, "Game Info"));
        }
    }

    // Handle undo last bet
    private void handleUndoLastBet(Player player) {
        if (gameActive) {
            player.sendMessage("§cInvalid action");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        UUID playerId = player.getUniqueId();
        Map<Integer, Double> bets = playerBets.get(playerId);
        List<Double> lastBets = lastBetAmounts.get(playerId);
    
        if (bets != null && lastBets != null && !lastBets.isEmpty()) {
            double lastBet = lastBets.remove(lastBets.size() - 1); // Get the last bet amount
    
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);
            // Find the slot with the last bet and reduce it
            for (Map.Entry<Integer, Double> entry : bets.entrySet()) {
                int slot = entry.getKey();
                double currentBet = entry.getValue();
    
                if (currentBet >= lastBet) {
                    double newBet = currentBet - lastBet;
                    if (newBet > 0) {
                        bets.put(slot, newBet);
                        updateItemLore(slot, newBet);
                    } else {
                        bets.remove(slot);
                        updateItemLore(slot, 0);  // Clear the lore if the bet is removed
                    }
    
                    addWagerToInventory(player, lastBet);
    
                    // Check if there are no bets left for any player
                    if (bets.isEmpty()) {
                        stopCountdownTimer(); // Stop the timer if no bets are left for any player
                    }
                    return;
                }
            }
        }
    }
    
    private void updateItemLore(int slot, double wager) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (wager > 0) {
                    List<String> lore = new ArrayList<>();
                    lore.add("Current Bet: " + wager + " " + plugin.getCurrencyName(internalName));
                    meta.setLore(lore);
                } else {
                    meta.setLore(new ArrayList<>()); // Clear lore if no wager
                }
                item.setItemMeta(meta);
            }
        }
    }
    
    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(plugin.getCurrency(internalName)), requiredAmount);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        if (requiredAmount > 0) {
            player.getInventory().removeItem(new ItemStack(plugin.getCurrency(internalName), requiredAmount));
        }
    }

    private void addWagerToInventory(Player player, double amount) {
        int totalAmount = (int) Math.ceil(amount);
        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;

        for (int i = 0; i < fullStacks; i++) {
            player.getInventory().addItem(new ItemStack(plugin.getCurrency(internalName), 64));
        }
        if (remainder > 0) {
            player.getInventory().addItem(new ItemStack(plugin.getCurrency(internalName), remainder));
        }
    }

    private void clearPlayerBets(UUID playerId) {
        if (playerId == null) {
            playerBets.clear();
            lastBetAmounts.clear();
        } else {
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);
        }
    }

    private void clearAllLore() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    meta.setLore(new ArrayList<>()); // Clear lore
                    item.setItemMeta(meta);
                }
            }
        }
    }

    // Start the countdown timer and display it with a stack of levers
    private void startCountdownTimer() {
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            
            int countdown =  plugin.getTimer(internalName);
            @Override
            public void run() {
                if (countdown > 0) {
                    inventory.setItem(1, createCustomItem(Material.CLOCK, "Game starts in: " + countdown, countdown));
                    countdown--;
                } else {
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    activateGame();
                }
            }
        }, 0L, 20L); // Run every second
    }

    // Activate the game and set the dealer's turn
private void activateGame() {
    gameActive = true; // Mark the game as active

    // Initialize hands for players and dealer
    for (UUID playerId : playerSeats.keySet()) {
        playerHands.put(playerId, new ArrayList<>());
    }
    dealerHand.clear(); // Ensure the dealer's hand is empty

    // Find the dealer head item and make it glow
    ItemStack dealerHead = inventory.getItem(0);
    if (dealerHead != null && dealerHead.getType() == Material.CREEPER_HEAD) {
        ItemMeta meta = dealerHead.getItemMeta();
        if (meta != null) {
            dealerHead.setItemMeta(meta); // Update the item meta to apply the changes
        }
    }

    // Update the display name of the lever to say "Dealer's turn"
    ItemStack lever = inventory.getItem(1);
    if (lever != null && lever.getType() == Material.CLOCK) {
        ItemMeta leverMeta = lever.getItemMeta();
        if (leverMeta != null) {
            leverMeta.setDisplayName("Dealer's turn"); // Set the display name to "Dealer's turn"
            lever.setItemMeta(leverMeta); // Apply the updated meta to the lever
        }
    }

    // Ensure player heads are updated correctly
    for (UUID playerId : playerSeats.keySet()) {
        updatePlayerHead(playerId); // Ensure player heads are visible with the correct stack size
    }

    // Deal cards to all players and the dealer
    dealInitialCards();
}


private void dealInitialCards() {
    int delay = 0;

    // First round of dealing (one card to each player)
    for (int i = 0; i < 2; i++) { // Repeat for two rounds
        for (UUID playerId : new ArrayList<>(playerSeats.keySet())) {
            if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty()) {
                // Skip this player if they haven't placed any bets
                continue;
            }

            int seatSlot = playerSeats.get(playerId);
            scheduleCardDealing(seatSlot + 2 + i, deck.dealCard(), delay, playerId); // First and second card
            delay += 20; // 1-second delay between card deals
        }

        // Deal one card to the dealer
        if (i == 0) {
            scheduleCardDealing(2, deck.dealCard(), delay, null); // First card to dealer in slot 2
        } else {
            scheduleHiddenCardDealing(3, delay); // Second card to dealer remains hidden in slot 3
        }

        delay += 20;
    }

    // Check for initial blackjack right after dealing cards
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        for (UUID playerId : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                int handValue = calculateHandValue(playerHands.get(playerId));
                if (handValue == 21) {
                    playerDone.put(playerId, true); // Mark the player as done
                    playerTurnActive.put(playerId, false); // Deactivate the player's turn
                }
            }
        }

        // Initialize playerIterator to start turns after dealing cards
        playerIterator = playerSeats.keySet().stream()
            .filter(playerId -> playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty() && !playerDone.getOrDefault(playerId, false))
            .iterator();

        startNextPlayerTurn();
    }, delay + 20L); // Delay slightly longer to allow cards to be fully dealt
}

private ItemStack createEnchantedItem(Material material, String name, int amount) {
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

private void startNextPlayerTurn() {
    // Initialize playerIterator if it's null or the previous iteration has ended
    if (playerIterator == null || !playerIterator.hasNext()) {
        // Create a new iterator with players who have active bets and are not done
        playerIterator = playerSeats.keySet().stream()
            .filter(playerId -> playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty() && !playerDone.getOrDefault(playerId, false))
            .iterator();
    }

    // Now proceed with the turn if there are players left
    while (playerIterator.hasNext()) {
        UUID previousPlayerId = currentPlayerId;
        if(previousPlayerId!=null){
            ItemStack prevItem = inventory.getItem(playerSeats.get(previousPlayerId) + 1);
            ItemStack replacementItem = createCustomItem(Material.PAPER, "Your turn is over.", 1);
            replacementItem.setLore(prevItem.getLore());
            inventory.setItem(playerSeats.get(previousPlayerId) + 1, replacementItem);
        }
        currentPlayerId = playerIterator.next();
        if (!playerDone.getOrDefault(currentPlayerId, false)) { // Skip players who are done
            
            Player currentPlayer = Bukkit.getPlayer(currentPlayerId);
            ItemStack item = inventory.getItem(playerSeats.get(currentPlayerId) + 1);
            
            ItemStack enchantedItem = createEnchantedItem(Material.BOOK, "Your turn.", 1);
            enchantedItem.setLore(item.getLore());
            addItem(enchantedItem, playerSeats.get(currentPlayerId) + 1);
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.MASTER, 1.0f, 1.0f); 
            // Check if the player's hand value is 21
            int handValue = calculateHandValue(playerHands.get(currentPlayerId));
            if (handValue == 21) {
                playerDone.put(currentPlayerId, true); // Mark the player as done
                playerTurnActive.put(currentPlayerId, false); // Deactivate the player's turn
                startNextPlayerTurnWithDelay(20L); // Start the next player's turn with delay
                return; // Skip to the next player
            }

            // Update lever to show current player's turn
            updateLeverDisplayName(currentPlayer.getName() + "'s Turn");

            // Set player's turn as active
            playerTurnActive.put(currentPlayerId, true);

            // Allow the player to take actions (Hit, Stand, Double Down, Insurance)
            allowPlayerActions(currentPlayer);
            return;
        }
    }
    if(currentPlayerId!=null){
        ItemStack prevItem = inventory.getItem(playerSeats.get(currentPlayerId) + 1);
        ItemStack replacementItem = createCustomItem(Material.PAPER, "Your turn is over.", 1);
        replacementItem.setLore(prevItem.getLore());
        inventory.setItem(playerSeats.get(currentPlayerId) + 1, replacementItem);
    }
    // No more players left, proceed to the dealer's turn
    startDealerTurn();
}

private void allowPlayerActions(Player player) {
    // Enable relevant slots for actions
    clickAllowed.putIfAbsent(player.getUniqueId(), true);

}


private void updateLeverDisplayName(String displayName) {
    ItemStack lever = inventory.getItem(1);
    if (lever != null && lever.getType() == Material.CLOCK) {
        ItemMeta leverMeta = lever.getItemMeta();
        if (leverMeta != null) {
            leverMeta.setDisplayName(displayName);
            lever.setItemMeta(leverMeta);
        }
    }
}

private void startDealerTurn() {
    // Check if all players have busted
    boolean allPlayersBusted = true;
    for (UUID playerId : playerSeats.keySet()) {
        int playerCardSum = calculateHandValue(playerHands.get(playerId));
        if (playerCardSum <= 21) {  // If any player has not busted
            allPlayersBusted = false;
            break;
        }
    }
    if (allPlayersBusted) {
        finishGame(); // Directly finish the game if all players are busted
        return;
    }
    updateLeverDisplayName("Dealer's Turn");

    // Reveal the dealer's hidden card with delay
    revealDealerCardWithDelay(20L);

    // Dealer must hit until reaching at least 17
    Bukkit.getScheduler().runTaskLater(plugin, () -> dealDealerCardsUntilSeventeen(4, calculateHandValue(dealerHand), 20L), 40L); // Start dealer's turn after revealing with delay
}

private void revealDealerCardWithDelay(long delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        ItemStack hiddenCard = inventory.getItem(3);
        if (hiddenCard != null && hiddenCard.getType() == Material.WHITE_STAINED_GLASS_PANE) {
            Card revealedCard = deck.dealCard(); // Reveal the actual card
            dealCardToPlayer(3, revealedCard, null); // Replace the hidden card with the revealed card
            updateDealerHead(); // Update dealer's head with new card value
        }
    }, delay);
}

private void dealDealerCardsUntilSeventeen(int nextSlot, int dealerCardSum, long delay) {
    final int[] mutableDealerCardSum = {dealerCardSum}; // Wrap the dealerCardSum in an array to make it mutable
    Nccasino nccasino = (Nccasino) plugin;
    int standOn17Chance;
    // Ensure the stand-on-17 percentage is valid
    if (!nccasino.getConfig().contains("dealers." + internalName + ".stand-on-17")) {
        // If the key doesn't exist, set it to 100
        nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        standOn17Chance = 100;
    } else {
        // Retrieve the current value
        standOn17Chance = nccasino.getConfig().getInt("dealers." + internalName + ".stand-on-17");
    
        // Check if the value is greater than 100 or less than 0
        if (standOn17Chance > 100 || standOn17Chance < 0) {
            // Reset the value to 100
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);

        }
    }

    if (mutableDealerCardSum[0] < 17 && deck.hasCards()) {
        Card newCard = deck.dealCard();
        dealCardToPlayer(nextSlot, newCard, null); // Deal the card to the dealer
        mutableDealerCardSum[0] = calculateHandValue(dealerHand); // Recalculate after adding each card

        // Update the dealer head after dealing a new card
        updateDealerHead();

        // Schedule the next card if needed
        Bukkit.getScheduler().runTaskLater(plugin, () -> dealDealerCardsUntilSeventeen(nextSlot + 1, mutableDealerCardSum[0], delay), delay);
    } else if (mutableDealerCardSum[0] == 17) {
        // Determine whether the dealer stops at 17 based on the percentage chance
        if (Math.random() * 100 < standOn17Chance) {
            // Stop at 17
            Bukkit.getScheduler().runTaskLater(plugin, this::finishGame, delay);
        } else {
            // Continue if the dealer does not stop at 17
            if (deck.hasCards()) {
                Card newCard = deck.dealCard();
                dealCardToPlayer(nextSlot, newCard, null);
                mutableDealerCardSum[0] = calculateHandValue(dealerHand);
                updateDealerHead();
                
                Bukkit.getScheduler().runTaskLater(plugin, () -> 
                    dealDealerCardsUntilSeventeen(nextSlot + 1, mutableDealerCardSum[0], delay), delay);
            }
        }
    } else {
        // Proceed to finish the game after the dealer's turn
        Bukkit.getScheduler().runTaskLater(plugin, this::finishGame, delay);
    }
}


private void scheduleCardDealingWithDelay(int slot, Card card, long delay, UUID playerId) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        dealCardToPlayer(slot, card, playerId);
        if (playerId != null) {
            updatePlayerHead(playerId); // Update player's head lore
        } else {
            updateDealerHead(); // Update dealer's head lore
        }
    }, delay);
}


private void setClickAllowed(UUID playerId, boolean allowed) {
    clickAllowed.put(playerId, allowed);
}


private void refundBet(Player player, Map<Integer, Double> bets) {
    if (bets != null) {
        double totalBet = bets.values().stream().mapToDouble(Double::doubleValue).sum();
        addWagerToInventory(player, totalBet);
    }
}

private void finishGame() {
    int dealerCardSum = calculateHandValue(dealerHand);
    boolean dealerBusted = dealerCardSum > 21;
    for (UUID playerId : playerSeats.keySet()) {
        if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty()) {
            continue; // Skip players without bets
        }

        Player player = Bukkit.getPlayer(playerId);
        int playerCardSum = calculateHandValue(playerHands.get(playerId));

        Map<Integer, Double> bets = playerBets.get(playerId);

        // Check if the player has a blackjack (Ace + 10-value card) and only has 2 cards
        boolean isBlackjack = playerCardSum == 21 && playerHands.get(playerId).size() == 2
                && hasAceAndTenValueCard(playerHands.get(playerId));

        if (isBlackjack) {
            player.sendMessage("§g§lBlackjack!");
            Random random = new Random();
            
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            // We'll pick from a small array of fun pitches
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                player.playSound(player.getLocation(), 
                        Sound.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.MASTER,
                        1.0f, 
                        chosenPitch
                    );
                // Schedule them slightly apart for a "ding-ding-ding" effect
            
            }
            payOut(player, bets, 2.5); // Pay out 2.5x for a blackjack
        } else if (playerCardSum > 21) {
            player.sendMessage("§c§lYou busted and lost your bet.");
            player.playSound(player.getLocation(), 
                        Sound.ENTITY_GENERIC_EXPLODE,
                        SoundCategory.MASTER,
                        1.0f, 
                        1.0f
                    );
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);  
        } else if (dealerBusted || playerCardSum > dealerCardSum) {
            player.sendMessage("§a§lYou won! Collect your winnings.");
            
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            Random random = new Random();
            // We'll pick from a small array of fun pitches
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                player.playSound(player.getLocation(), 
                        Sound.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.MASTER,
                        1.0f, 
                        chosenPitch
                    );
                // Schedule them slightly apart for a "ding-ding-ding" effect
            
            }
            payOut(player, bets, 2.0); // Regular win pays out 2x
        } else if (playerCardSum < dealerCardSum) {
            player.sendMessage("§c§lYou lost this round.");
            player.playSound(player.getLocation(), 
            Sound.ENTITY_GENERIC_EXPLODE,
            SoundCategory.MASTER,
            1.0f, 
            1.0f
        );
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);  
        } else {
            player.sendMessage("§6§lIt's a tie! Your bet is returned.");
            refundBet(player, bets);
        }
    }

    // Reset game for the next round
    resetGame();
}

private void payOut(Player player, Map<Integer, Double> bets, double multiplier) {
    if (bets != null) {
        double totalBet = bets.values().stream().mapToDouble(Double::doubleValue).sum();
        double payout = totalBet * multiplier;
        addWagerToInventory(player, payout);
        player.sendMessage("§a§l" + payout + " " + plugin.getCurrencyName(internalName) + "s");
    }
}

// Utility method to check if the hand has an Ace and a 10-value card
private boolean hasAceAndTenValueCard(List<ItemStack> hand) {
    boolean hasAce = false;
    boolean hasTenValueCard = false;

    for (ItemStack cardItem : hand) {
        int cardValue = getCardValue(cardItem);
        if (cardValue == 1) { // Ace
            hasAce = true;
        } else if (cardValue == 10) { // 10-value card (10, Jack, Queen, King)
            hasTenValueCard = true;
        }
    }

    return hasAce && hasTenValueCard;
}

private void resetGame() {
    gameActive = false;
    
    playerBets.clear();
    lastBetAmounts.clear();
    playerCardCounts.clear(); // Clear the card count map
    playerDone.clear(); // Clear the player status map
    playerHands.clear(); // Clear player hands
    dealerHand.clear(); // Clear dealer's hand
    playerIterator = null;
    currentPlayerId = null;

    // Cancel any ongoing countdown
    if (countdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(countdownTaskId);
        countdownTaskId = -1;
    }

    // Clear the inventory to ensure no leftover items
    inventory.clear();

    // Set up the Game Info lever in the second slot
    addItem(createCustomItem(Material.CLOCK, "Game Info"), 1);

    // Reinitialize the game menu but do not clear player seats
    initializeGameMenu();

    // Re-populate the player heads in the seats
    for (UUID playerId : playerSeats.keySet()) {
        int seatSlot = playerSeats.get(playerId);
        inventory.setItem(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
    }

}

private int calculateHandValue(List<ItemStack> hand) {
    int totalValue = 0;
    int acesCount = 0;

    if (hand == null || hand.isEmpty()) {
        return 0;
    }

    for (ItemStack cardItem : hand) {
        int cardValue = getCardValue(cardItem);
        totalValue += cardValue;
        if (cardValue == 1) { // Ace is worth 1
            acesCount++;
        }
    }

    // Adjust for Aces: Consider them as 11 if it doesn't bust the hand
    while (acesCount > 0 && totalValue + 10 <= 21) {
        totalValue += 10;
        acesCount--;
    }

    return totalValue;
}

private void scheduleCardDealing(int slot, Card card, int delay, UUID playerId) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {

        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
        
        dealCardToPlayer(slot, card, playerId);
        if (playerId != null) {
            updatePlayerHead(playerId); // Update player's head lore
        } else {
            updateDealerHead(); // Update dealer's head lore
        }
    }, delay);
}

private void updatePlayerHead(UUID playerId) {
    if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty()) {
        return; // Skip updating if the player hasn't placed a bet
    }
    
    List<ItemStack> hand = playerHands.get(playerId);
    String handValue = calculateHandValueWithSoftCheck(hand);
    
    int seatSlot = playerSeats.get(playerId);
    updateHeadLore(seatSlot, handValue, Bukkit.getPlayer(playerId).getName());
}

private void updateDealerHead() {
    String handValue = calculateHandValueWithSoftCheck(dealerHand);
    updateHeadLore(0, handValue, "Dealer");
}


private void updateHeadLore(int slot, String cardValue, String name) {
    ItemStack headItem = inventory.getItem(slot);
    if (headItem != null && (headItem.getType() == Material.PLAYER_HEAD || headItem.getType() == Material.CREEPER_HEAD)) {
        ItemMeta meta = headItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("Card Value: " + cardValue);
            meta.setLore(lore);
            headItem.setItemMeta(meta);
            inventory.setItem(slot, headItem);
        }
    }
}
private String calculateHandValueWithSoftCheck(List<ItemStack> hand) {
    int totalValue = 0;
    int acesCount = 0;

    if (hand == null || hand.isEmpty()) {
        return "0";
    }

    // Calculate total value and count aces
    for (ItemStack cardItem : hand) {
        int cardValue = getCardValue(cardItem);
        totalValue += cardValue;
        if (cardValue == 1) { // Ace is worth 1
            acesCount++;
        }
    }

    // Calculate the soft value if there are aces
    int softValue = totalValue;
    if (acesCount > 0) {
        while (acesCount > 0 && softValue + 10 <= 21) {
            softValue += 10;
            acesCount--;
        }
    }

    // Return soft/hard value if an ace is present; otherwise, just return the hard value
    if (softValue != totalValue) {
        return softValue + "/" + totalValue;
    } else {
        return String.valueOf(totalValue);
    }
}

private int getCardValue(ItemStack cardItem) {
    if (cardItem == null || !cardItem.hasItemMeta()) return 0;
    
    ItemMeta meta = cardItem.getItemMeta();
    String displayName = meta.getDisplayName();
    if (displayName == null || displayName.isEmpty()) return 0;

    String[] parts = displayName.split(" ");
    if (parts.length < 2) return 0;

    String rank = parts[0].toUpperCase(); // Get the first part of the display name, which should be the rank
    int value;

    switch (rank) {
        case "ACE":
            value = 1;
            break;
        case "TWO":
            value = 2;
            break;
        case "THREE":
            value = 3;
            break;
        case "FOUR":
            value = 4;
            break;
        case "FIVE":
            value = 5;
            break;
        case "SIX":
            value = 6;
            break;
        case "SEVEN":
            value = 7;
            break;
        case "EIGHT":
            value = 8;
            break;
        case "NINE":
            value = 9;
            break;
        case "TEN":
        case "JACK":
        case "QUEEN":
        case "KING":
            value = 10;
            break;
        default:
            value = 0;
            break;
    }

    return value;
}

private void scheduleHiddenCardDealing(int slot, int delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Material material = Material.WHITE_STAINED_GLASS_PANE; // Hidden card is now white
        ItemStack hiddenCard = new ItemStack(material, 1);
        ItemMeta meta = hiddenCard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Hidden Card");
            hiddenCard.setItemMeta(meta);
        }
        
        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
        inventory.setItem(slot, hiddenCard);
    }, delay);
}

private void dealCardToPlayer(int slot, Card card, UUID playerId) {
    // No changes needed here; the deck will reshuffle automatically if it's empty.
    Material material = (card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
    int stackSize = getCardValueStackSize(card);
    for (UUID uuid : playerSeats.keySet()) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    ItemStack cardItem = new ItemStack(material, stackSize);
    ItemMeta meta = cardItem.getItemMeta();
    if (meta != null) {
        meta.setDisplayName(card.getRank() + " of " + card.getSuit());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); // Hide item attributes
        cardItem.setItemMeta(meta);
    }

    inventory.setItem(slot, cardItem); // Place the card in the inventory slot

    if (playerId != null) { // If this card is dealt to a player
        playerHands.computeIfAbsent(playerId, k -> new ArrayList<>()).add(cardItem);
        updatePlayerHead(playerId);
    } else { // If this card is dealt to the dealer
        dealerHand.add(cardItem);
        updateDealerHead();
    }
}



private int getCardValueStackSize(Card card) {
    switch (card.getRank()) {
        case ACE:
            return 1;
        case JACK:
        return 10;
        case QUEEN:
        return 10;
        case KING:
        return 10;
           
        default:
            return card.getRank().ordinal() + 2; // +2 because ordinal starts from 0, and 2 is the lowest card rank
    }
}


public void delete() {
    // Stop any ongoing game operations
    cancelGame();

    // Close the inventory for all players currently viewing it
    for (HumanEntity entity : new ArrayList<>(inventory.getViewers())) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            player.closeInventory();
        }
    }

    // Clear all player data and bets
    clearPlayerBets(null);
    playerSeats.clear();
    playerHands.clear();
    playerCardCounts.clear();
    playerTurnActive.clear();
    playerDone.clear();
    selectedWagers.clear();
    lastBetAmounts.clear();

    // Remove any scheduled tasks related to this game
    if (countdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(countdownTaskId);
    }

    // Unregister events related to this inventory
    HandlerList.unregisterAll(this);
    // Clear the inventory itself
    inventory.clear();

    DealerInventory.inventories.remove(dealerId);

    // Mark this inventory as deleted
    inventory = null;
}


    // Cancel the game and reset the board with all items and options
    private void cancelGame() {
        gameActive = false;
        clearPlayerBets(null); // Clear all bets
        clearAllLore(); // Clear all lore
        initializeGameMenu(); // Reestablish the board with all items and options

        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId); // Cancel the countdown if it's running
            countdownTaskId = -1;
        }
    }

        @EventHandler
        public void handleInventoryClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() != this) return;
        
            Player player = (Player) event.getPlayer();

        
            if (!gameActive) {
                handleUndoAllBets(player);
                handleLeaveChair(player);
            } else {
                handleLeaveChairDuringGame(player);
            }
        }
        

    
}