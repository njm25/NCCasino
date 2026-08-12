package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.currency.VaultCurrencyProvider;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.AttributeHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Deck;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class BlackjackInventory extends DealerInventory implements TerminableSession {

    private final Nccasino plugin; // Reference to the main plugin
    private final Map<Integer, Double> chipValues; // Track chip values by fixed inventory slot
    private final String internalName; // Internal name for config lookup
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private final Map<UUID, Integer> playerSeats; // Track player seats
    private final Map<UUID, Map<Integer, Double>> playerBets; // Track player bets by slot number
    private final Map<UUID, List<Double>> lastBetAmounts; // Track the last bet amounts placed by the player
    private boolean gameActive; // Track whether the game is active
    private int countdownTaskId; // Task ID for the countdown timer
    private UUID currentPlayerId; // Track the current player
    private Iterator<UUID> playerIterator; // Iterator for player turns
    private final Map<UUID, Integer> playerCardCounts = new HashMap<>(); // Track number of cards each player has
    private final Map<UUID, Boolean> playerDone = new HashMap<>(); // Track whether the player is done (stood or busted)
    private final Map<UUID, List<Card>> playerHands = new HashMap<>();
    private final List<Card> dealerHand = new ArrayList<>();
    private final Map<UUID, Double> selectedWagers = new HashMap<>();
    private final Object turnLock = new Object(); // Lock object for turn actionsactions
    private final Map<UUID, Boolean> playerTurnActive = new HashMap<>();
    private Deck deck; // Declare the deck as a class variable
    private Boolean firstFin=true;
    private Boolean sittable=true;
    public UUID dealerId;
    public BlackjackInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(
            dealerId,
            54,
            plugin.getLocalization().text(plugin.getLocalization().getServerDefault(), "blackjack.title")
        ); // Using 54 slots for start menu
        this.plugin = plugin; // Store the plugin reference
        this.chipValues = new HashMap<>(); // Initialize chip values storage
        this.internalName = internalName; // Store the internal name
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.gameActive = false; // Initialize game active flag
        this.playerSeats = new HashMap<>(); // Initialize player seats storage
        this.playerBets = new HashMap<>(); // Initialize player bets storage
        this.lastBetAmounts = new HashMap<>(); // Initialize last bet amounts storage
        this.countdownTaskId = -1; // Initialize countdown task ID
        // Initialize the start menu
        Nccasino nccasino = plugin;
        this.dealerId = dealerId;
        // Check if the configuration key exists
        if (!nccasino.getConfig().contains("dealers." + internalName + ".stand-on-17")) {
            // If the key doesn't exist, set it to 100
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        } else {
            // Retrieve the current value
            String value = plugin.getConfig().getString("dealers." + internalName + ".stand-on-17", "100").trim();
            int standOn17Chance;
            
            try {
                standOn17Chance = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                standOn17Chance = 100; // Default
                plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                plugin.saveConfig();
            }
        
            // Check if the value is greater than 100 or less than 0
            if (standOn17Chance > 100 ) {
                // Reset the value to 100
                nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            }
            else if(standOn17Chance < 0){
                nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 0);

            }
        }

        int numberOfDecks;

        if (!plugin.getConfig().contains("dealers." + internalName + ".number-of-decks")) {
            numberOfDecks = 6;
            plugin.getConfig().set("dealers." + internalName + ".number-of-decks", numberOfDecks);
        } else {
            // Retrieve the current value
            int currentDecks = plugin.getConfig().getInt("dealers." + internalName + ".number-of-decks");
        
            // Check if the value is less than 1
            if (currentDecks <= 0) {
                numberOfDecks = 6;
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", 6);
            }
            else if (currentDecks>10000){
                numberOfDecks = 10000;
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", 10000);
            }
            else{
                numberOfDecks=currentDecks;
            }
        }

        
        this.deck = new Deck(numberOfDecks); // Initialize the deck
        loadChipValuesFromConfig(); // Load chip values from config
        
       registerListener();
       plugin.addInventory(dealerId, this);
    }

private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }


      @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event){
        if (event.getInventory() == this.getInventory()){
            Player player=(Player)event.getPlayer();
            if(player.getInventory() !=null){
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player != null) {
                            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                                case STANDARD:{
                                    break;}
                                case VERBOSE:{
                                    player.sendMessage(text(player, "blackjack.welcome"));
                                    break;     
                                }
                                    case NONE:{
                                    break;
                                }
                            } 
                            if(firstFin){
                        firstFin=false;
                        initializeGameMenu();
            
                            }
            
                            // No need to register the listener here since it's handled in the constructor
                        }
                }, 2L);    
            }
        }   
      
    }
 
    // Load chip values from the plugin config
    private void loadChipValuesFromConfig() {
        List<Double> configuredValues = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            configuredValues.add(plugin.getChipValue(internalName, i));
        }
        this.chipValues.putAll(ChipSlots.assign(configuredValues));
    }

    // CurrencyProvider helper for this dealer/game
    private CurrencyProvider getCurrencyProvider() {
        if (plugin.getCurrencyManager() == null) {
            return null;
        }
        return plugin.getCurrencyManager().getProvider(internalName);
    }

    // Helper to determine if a stack represents this dealer's currency
    private boolean isCurrencyItem(ItemStack stack) {
        if (stack == null) return false;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return provider.isCurrencyItem(stack, internalName);
        }

        Material mat = plugin.getCurrency(internalName);
        return mat != null && stack.getType() == mat;
    }

    // Initialize Blackjack-specific game menu
    @SuppressWarnings("removal")
    private void initializeGameMenu() {

        inventory.clear(); // Clear the inventory before setting up the page

        for (UUID playerId : playerSeats.keySet()) {
            int seatSlot = playerSeats.get(playerId);
            inventory.setItem(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
        }
        // Add the necessary items for the game menu
        addItem(createCustomItem(Material.CREEPER_HEAD, text("blackjack.dealer")), 0); // Dealer
        // The Game Info lever stack will be added when the timer starts.

        // Add chairs
        addItem(createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")), 9); // Chair 1
        addItem(createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")), 18); // Chair 2
        addItem(createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")), 27); // Chair 3
        sittable=true;

        // Add betting papers
        addItem(createCustomItem(Material.PAPER, text("blackjack.click-bet")), 10); // Bet 1
        addItem(createCustomItem(Material.PAPER, text("blackjack.click-bet")), 19); // Bet 2
        addItem(createCustomItem(Material.PAPER, text("blackjack.click-bet")), 28); // Bet 3

        // Add game action buttons
        addItem(createCustomItem(Material.DIAMOND_SWORD, text("blackjack.hit")), 36); // Hit
        ItemStack item = inventory.getItem(36);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.addAttributeModifier(AttributeHelper.getAttributeSafely("ATTACK_DAMAGE"), 
            new AttributeModifier("foo", 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
     // This is necessary as of 1.20.6
        for(ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        addItem(createCustomItem(Material.SHIELD, text("blackjack.stand")), 37); // Stand
        addItem(createCustomItem(Material.PAPER, text("blackjack.double-down")), 38); // Double Down
        //addItem(createCustomItem(Material.SHEARS, "Split"), 39); // Split
        //addItem(createCustomItem(Material.TOTEM_OF_UNDYING, "Insurance"), 40); // Insurance

        // Add undo options and chip denominations
        inventory.setItem(45, createCustomItem(Material.BARRIER, text("blackjack.undo-all"), 1));
        inventory.setItem(46, createCustomItem(Material.WIND_CHARGE, text("blackjack.undo-last"), 1));

        for (Map.Entry<Integer, Double> entry : chipValues.entrySet()) {
            double value = entry.getValue();
            inventory.setItem(
                entry.getKey(),
                createCustomItem(
                    plugin.getCurrency(internalName),
                    plugin.getChipDisplayName(currencyMode, currencyName, value),
                    (int) value
                )
            );
        }
        inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, text("blackjack.all-in"), 1));
        // Add leave chair option with a wooden door
        inventory.setItem(53, createCustomItem(Material.SPRUCE_DOOR, text("blackjack.leave-exit"), 1));
    }

    // Create an item stack with a custom display name
    public ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Create an item stack with a custom display name and amount
    public ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }
        ItemStack itemStack = new ItemStack(material, amount);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Set custom item metadata
    public void setCustomItemMeta(ItemStack itemStack, String name) {
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

@Override
public void handleClick(int slot, Player player, InventoryClickEvent event) {
    if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
        return;
    }
    UUID playerId = player.getUniqueId();

    if (gameActive) { // Game is active, handle player actions
        if (playerId.equals(currentPlayerId)) {
            handlePlayerAction(player, slot);
        } else if (slot == 53) { // Handle leave chair
            handleLeaveChairDuringGame(player);
            player.closeInventory();}
        else if (isPlayerHeadSlot(slot, player)) { // Handle clicking own player head
                handleLeaveChair(player); // Leave chair but stay in inventory
        }
        else if (slot >= 10 && slot <= 28 && slot % 9 == 1) { // Bet slots
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.invalid-action"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.betting-closed"));
                break;}
            case NONE:{
                break;
            }
        }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
        }
        else if (slot == 0){
                            // 1 in 1000 chance
            if (Math.random() < 0.001) { 
                                // Spawn the firework at the player's location
                Firework firework = (Firework) player.getLocation().getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);

                // Customize the firework
                FireworkMeta fireworkMeta = firework.getFireworkMeta();

                FireworkEffect effect = FireworkEffect.builder()
                        .with(FireworkEffect.Type.CREEPER) // Explosion shape: Creeper head
                        .withColor(Color.GREEN)           // Primary color
                        .withFade(Color.BLACK)            // Fade color
                        .flicker(true)                    // Flicker effect
                        .trail(true)                      // Trail effect
                        .build();
            
                fireworkMeta.addEffect(effect);
                fireworkMeta.setPower(2); // Flight duration
                firework.setFireworkMeta(fireworkMeta);
            
                // Do NOT call firework.detonate(), let the firework fly naturally
            }
            
                if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT,SoundCategory.MASTER, 1.0f, 1.0f); 
        }
    }
        else { // Handle clicks in the game menu before the game starts
        if (isPlayerHeadSlot(slot, player)) { // Handle clicking own player head
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.left-chair"));
                    break;     
    
                }
                    case NONE:{
                    break;
                }
            }
            handleLeaveChair(player); // Leave chair but stay in inventory
        }
        else if (slot >= 9 && slot <= 27 && slot % 9 == 0) { // Chair slots (9, 18, 27)
            handleChairClick(slot, player);
        } else if (slot == 53) { // Leave chair
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.left-game"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            }
            handleLeaveChair(player);
            player.closeInventory();
        } else if (slot >= 10 && slot <= 28 && slot % 9 == 1) { // Bet slots (10, 19, 28)
            handleBetClick(slot, player, event);
        } else if (ChipSlots.isChipSlot(slot)) { // Chip selection
            handleChipSelection(player, slot);
        } 
        else if (slot == 0){

            // 1 in 1000 chance
            if (Math.random() < 0.001) { 
                                // Spawn the firework at the player's location
                Firework firework = (Firework) player.getLocation().getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);

                // Customize the firework
                FireworkMeta fireworkMeta = firework.getFireworkMeta();

                FireworkEffect effect = FireworkEffect.builder()
                        .with(FireworkEffect.Type.CREEPER) // Explosion shape: Creeper head
                        .withColor(Color.GREEN)           // Primary color
                        .withFade(Color.BLACK)            // Fade color
                        .flicker(true)                    // Flicker effect
                        .trail(true)                      // Trail effect
                        .build();
            
                fireworkMeta.addEffect(effect);
                fireworkMeta.setPower(2); // Flight duration
                firework.setFireworkMeta(fireworkMeta);
            
                // Do NOT call firework.detonate(), let the firework fly naturally
            }
            
                if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT,SoundCategory.MASTER, 1.0f, 1.0f); 
        } else {
            switch (slot) {
                case 45:


                    handleUndoAllBets(player);
                    break;
                case 46:
                    handleUndoLastBet(player);
                    break;
                case 52:
                    handleAllIn(player);
                default:
                    // Handle other slots if needed
                    break;
            }
        }
    }
}

private boolean isPlayerHeadSlot(int slot, Player player) {
    if (slot < 0 || slot >= inventory.getSize()) { 
        return false; // Prevent invalid slot access
    }

    ItemStack item = inventory.getItem(slot);
    if (item == null || item.getType() != Material.PLAYER_HEAD) return false;

    SkullMeta meta = (SkullMeta) item.getItemMeta();
    return meta != null && meta.hasOwner() && meta.getOwningPlayer() != null &&
           meta.getOwningPlayer().getUniqueId().equals(player.getUniqueId());
}


private void handleAllIn(Player player) {
    UUID playerId = player.getUniqueId();
    
    // Ensure the player is seated before allowing all-in
    if (!playerSeats.containsKey(playerId)) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
        player.sendMessage(text(player, "blackjack.invalid-action"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.must-sit-all-in"));
                break;}
            case NONE:{
                break;
            }
        }
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        return;
    }

    // Calculate total player balance in inventory
    double totalBalance = getPlayerTotalBalance(player);
    
    if (totalBalance <= 0) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.invalid-action"));
                break;}
            case VERBOSE:{
                player.sendMessage(
                    currencyMode == org.nc.nccasino.currency.CurrencyMode.VAULT
                        ? text(player, "blackjack.no-funds")
                        : text(
                            player,
                            "blackjack.no-currency",
                            "currency",
                            plugin.getCurrencyName(internalName).toLowerCase() + "s"
                        )
                );
                break;}
            case NONE:{
                break;
            }
        }
         if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        return;
    }

    int seatSlot = playerSeats.get(playerId);
    int betSlot = seatSlot + 1; // The slot where bets are placed

    // Remove the full balance from the player's inventory
    removeWagerFromInventory(player, totalBalance);

    // Store the bet amount
    Map<Integer, Double> bets = playerBets.computeIfAbsent(playerId, k -> new HashMap<>());
    double newTotal = bets.merge(betSlot, totalBalance, (existing, newValue) -> (existing != null ? existing : 0.0) + (newValue != null ? newValue : 0.0));

    // Update the displayed bet amount with the new total
    updateItemLore(betSlot, newTotal);

    lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(totalBalance); // Store last bet amount

    // Play sound effect to confirm All In
     if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.5f, 0.8f);
     switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
        case STANDARD:{
            break;}
        case VERBOSE:{
            player.sendMessage(text(
                player,
                "blackjack.all-in-with",
                "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, newTotal)
            ));
                        break;}
        case NONE:{
            break;
        }
    }
    //

    // Start countdown if not already running
    if (countdownTaskId == -1) {
        startCountdownTimer();
    }
}

private double getPlayerTotalBalance(Player player) {
    CurrencyProvider provider = getCurrencyProvider();
    if (provider != null) {
        return provider.getBalance(player, internalName);
    }

    int totalAmount = 0;
    Material currencyMaterial = plugin.getCurrency(internalName);
    if (currencyMaterial == null) {
        return 0;
    }

    for (ItemStack item : player.getInventory().getContents()) {
        if (item != null && item.getType() == currencyMaterial) {
            totalAmount += item.getAmount();
        }
    }

    return totalAmount;
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
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.not-your-turn"));
                    break;     
                }
                case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }

        // Disable further actions until the current one is processed
        playerTurnActive.put(playerId, false);

        switch (slot) {
            case 36: // Hit
                handleHit(player);
                 if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
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
            case 0:
                                // 1 in 1000 chance
                if (Math.random() < 0.001) { 
                                    // Spawn the firework at the player's location
                    Firework firework = (Firework) player.getLocation().getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);

                    // Customize the firework
                    FireworkMeta fireworkMeta = firework.getFireworkMeta();

                    FireworkEffect effect = FireworkEffect.builder()
                            .with(FireworkEffect.Type.CREEPER) // Explosion shape: Creeper head
                            .withColor(Color.GREEN)           // Primary color
                            .withFade(Color.BLACK)            // Fade color
                            .flicker(true)                    // Flicker effect
                            .trail(true)                      // Trail effect
                            .build();
                
                    fireworkMeta.addEffect(effect);
                    fireworkMeta.setPower(2); // Flight duration
                    firework.setFireworkMeta(fireworkMeta);
                
                    // Do NOT call firework.detonate(), let the firework fly naturally
                }
                
                 if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT,SoundCategory.MASTER, 1.0f, 1.0f); 
            default:
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action-spaced"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.invalid-action-spaced"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            }
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                playerTurnActive.put(playerId, true); // Re-enable actions if the action was invalid
        }
    }
}

private void handleHit(Player player) {
    synchronized (turnLock) {

        UUID playerId = player.getUniqueId();
        if (playerSeats.get(playerId) == null || !playerSeats.containsKey(playerId)){
            return;
        }
        int seatSlot = playerSeats.get(playerId);
        int cardCount = playerCardCounts.getOrDefault(playerId, 2); // Default to 2 because of the initial 2 cards dealt
        int nextCardSlot = seatSlot + 2 + cardCount; // Calculate the next slot based on the number of cards

        Card newCard = deck.dealCard();
        scheduleCardDealingWithDelay(nextCardSlot, newCard, BlackjackTiming.CARD_DEAL_DELAY_TICKS, playerId); // Deal the card with a delay

        cardCount++; // Increment the card count
        playerCardCounts.put(playerId, cardCount); // Update the card count

        // Delay the hand value calculation to ensure the card is fully added to the player's hand
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!playerSeats.containsKey(playerId)) {
                return;
            }
            int handValue = calculateHandValue(playerHands.get(playerId));
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                 
                    player.sendMessage(text(
                        player,
                        "blackjack.drew-card",
                        "rank",
                        newCard.getRank().toString().toLowerCase()
                    ));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            if (handValue == 21) {

                playerTurnActive.put(playerId, false); // Deactivate the player's turn
                startNextPlayerTurnWithDelay(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS); // Start next player's turn with delay
            } else if (handValue > 21) {


                playerDone.put(playerId, true); // Mark the player as done
                playerTurnActive.put(playerId, false); // Deactivate the player's turn
                startNextPlayerTurnWithDelay(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS); // Start next player's turn with delay

            } else {

                playerTurnActive.put(playerId, true); // Allow more actions since the player hasn't busted

            }
        }, BlackjackTiming.HIT_EVALUATION_DELAY_TICKS); // The delay should be enough to ensure that the card has been added
    }
}

private void handleStand(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        playerDone.put(playerId, true); // Mark the player as done
        playerTurnActive.put(playerId, false); // Deactivate the player's turn
         if (SoundHelper.getSoundSafely("item.shield.block", player) != null)player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER,1.0f, 1.0f);
         switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.stood"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.stood"));
                break;     
            }
                case NONE:{
                break;
            }
        }
        startNextPlayerTurnWithDelay(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS); // Start next player's turn with delay
    }
}

private void startNextPlayerTurnWithDelay(long delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> startNextPlayerTurn(), delay);
}

/**
 * Preserved quirk, not fixed here (see BlackjackTimingTest): the extra card
 * comes from handleHit, whose evaluation callback fires after
 * HIT_EVALUATION_DELAY_TICKS (40t), but this method advances the turn
 * unconditionally after the shorter TURN_ADVANCE_DELAY_TICKS (20t). The
 * stale callback can still mutate playerDone/playerTurnActive and
 * re-advance the turn after it's no longer this player's. Address before
 * or during splitting.
 */
private void handleDoubleDown(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        double currentBet = playerBets.get(playerId).values().stream().mapToDouble(Double::doubleValue).sum();

        if (!hasEnoughWager(player, currentBet)) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.insufficient-funds"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.insufficient-double-down"));}
                    case NONE:{
                    break;
                }
            }
            playerTurnActive.put(playerId, true); // Allow more actions since the double down failed
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
         if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f); 
         switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.doubled-down"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.doubled-down"));
                break;     

            }
                case NONE:{
                break;
            }
        }
        startNextPlayerTurnWithDelay(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS); // Start next player's turn with delay
    }
}

/*
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
 */
    // Handle chair click
    private void handleChairClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack clickedItem = inventory.getItem(slot);

        // Check if the player is already sitting in a chair
        if (playerSeats.containsKey(playerId)||clickedItem == null || !clickedItem.getType().name().endsWith("_STAIRS")||!sittable) {
            if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f); 
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
               case STANDARD:{
                player.sendMessage(text(player, "blackjack.already-seated"));
                break;}
               case VERBOSE:{
                player.sendMessage(text(player, "blackjack.cannot-switch"));
                break;     

            }
                   case NONE:{
                   break;
               }
           }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

         if (SoundHelper.getSoundSafely("block.wood.place", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE,SoundCategory.MASTER, 1.0f, 1.0f); 
        // Set the player's actual head at the chair's position
        inventory.setItem(slot, createPlayerHeadItem(player, 1));
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.sat-down"));
                break;     

            }
                case NONE:{
                break;
            }
        }
        // Track the player's seat
        playerSeats.put(playerId, slot);
        SessionRegistry.register(playerId, this);
    }
    

// Handle leave chair during the countdown or active game
private void handleLeaveChair(Player player) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

    int chairSlot = playerSeats.remove(playerId);

     if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f); 
    // Reset the chair to its original state
    inventory.setItem(chairSlot, createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")));

    // If the timer is still running, undo all bets
    if (countdownTaskId != -1 && !gameActive) {
        handleUndoAllBets(player);
    } else {
        removePlayerData(playerId);
    }

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        sittable=false;
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
        startNextPlayerTurn(); // Start next player's turn with delay
    }

    int chairSlot = playerSeats.remove(playerId);

    // Remove all the player's associated data
    removePlayerData(playerId);

     if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f); 
    // Reset the chair to its original state
    inventory.setItem(chairSlot, createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")));
  
    
    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        sittable=false;
        cancelGame();
    }
}

private void removePlayerData(UUID playerId) {
    // Retrieve the player's seat slot
    int seatSlot = playerSeats.getOrDefault(playerId, -1);

    // If the player has a valid seat slot
    if (seatSlot != -1) {
        // Clear the player's cards from the table
        List<Card> hand = playerHands.get(playerId);
        if (hand != null) {
            for (int i = 0; i < hand.size(); i++) {
                inventory.setItem(seatSlot + 2 + i, new ItemStack(Material.AIR)); // Clear each card slot in the player's row
            }
        }

        // Remove the player's head from the seat
        inventory.setItem(seatSlot, createCustomItem(Material.OAK_STAIRS, text("blackjack.click-sit")));

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
        SessionRegistry.unregister(playerId, this);

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
    private void handleChipSelection(Player player, int slot) {
        double selectedWager = chipValues.getOrDefault(slot, 0.0);
        if (selectedWager <= 0) {
            return;
        }
    
        UUID playerId = player.getUniqueId();
         if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
         switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage(text(
                    player,
                    "blackjack.wager-selected",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, selectedWager)
                ));
                break;     
            }
                case NONE:{
                break;
            }
        }
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
    
    private void handleBetClick(int slot, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        double selectedWager = getSelectedWager(playerId);
        
        // Ensure the player is sitting before placing a bet
        if (!playerSeats.containsKey(playerId)) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.sit-to-bet"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.must-sit-to-bet"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
    
        // Ensure the player can only bet on their own paper
        int chairSlot = playerSeats.get(playerId);
        int betSlot = chairSlot + 1; // Paper is always right next to the chair
        
        if (slot != betSlot) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.not-your-bet"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.cannot-bet-other"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
    
        // Check if the player is holding the currency item
        ItemStack heldItem = event.getCursor();
        boolean isCurrencyItem = isCurrencyItem(heldItem);
        if (isCurrencyItem && heldItem != null) {
            int amount = heldItem.getAmount();
            double totalWager = amount;
    
            if (totalWager > 0) {
                player.setItemOnCursor(null); // Remove the stack from the cursor
                Map<Integer, Double> bets = playerBets.computeIfAbsent(playerId, k -> new HashMap<>());
                double currentBet = bets.getOrDefault(betSlot, 0.0);
                bets.put(betSlot, currentBet + totalWager);
                updateItemLore(betSlot, bets.get(betSlot));
                lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(totalWager);
    
                if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
    
                if (countdownTaskId == -1) {
                    startCountdownTimer();
                }
                return;
            }
        } else if (selectedWager > 0 && hasEnoughWager(player, selectedWager)) {
            removeWagerFromInventory(player, selectedWager);
            Map<Integer, Double> bets = playerBets.computeIfAbsent(playerId, k -> new HashMap<>());
            double currentBet = bets.getOrDefault(betSlot, 0.0);
            bets.put(betSlot, currentBet + selectedWager);
            updateItemLore(betSlot, bets.get(betSlot));
            lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(selectedWager);
    
            if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
    
            if (countdownTaskId == -1) {
                startCountdownTimer();
            }
        } else {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.insufficient-bet"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }
    
    private void handleUndoAllBets(Player player) {
        if (gameActive) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.cannot-undo-all"));
                    break;     

                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        UUID playerId = player.getUniqueId();
        Map<Integer, Double> bets = playerBets.get(playerId);
    
        if (bets != null && !bets.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                 
                    player.sendMessage(text(player, "blackjack.all-bets-undone"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            }
            double totalRefund = bets.values().stream().mapToDouble(Double::doubleValue).sum();
            addWagerToInventory(player, totalRefund);
            clearPlayerBetLore(playerId);  // Clear lore for items related to this player
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);
    
             if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
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
            inventory.setItem(1, createCustomItem(Material.CLOCK, text("blackjack.game-info")));
        }
    }

    // Handle undo last bet
    private void handleUndoLastBet(Player player) {
        if (gameActive) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.cannot-undo"));
                    break;     

                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            return;
        }
    
        UUID playerId = player.getUniqueId();
        Map<Integer, Double> bets = playerBets.get(playerId);
        List<Double> lastBets = lastBetAmounts.get(playerId);
    
        if (bets != null && lastBets != null && !lastBets.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                 
                    player.sendMessage(text(player, "blackjack.last-bet-undone"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            double lastBet = lastBets.remove(lastBets.size() - 1); // Get the last bet amount
    
             if (SoundHelper.getSoundSafely("ui.toast.in", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
             if (SoundHelper.getSoundSafely("ui.toast.out", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);
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
                    lore.add(text("blackjack.wager-lore", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, wager)));
                    meta.setLore(lore);
                } else {
                    meta.setLore(new ArrayList<>()); // Clear lore if no wager
                }
                item.setItemMeta(meta);
            }
        }
    }
    
    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return false;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return provider.has(player, internalName, requiredAmount);
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            return false;
        }
        return player.getInventory().containsAtLeast(new ItemStack(currencyMaterial), requiredAmount);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            provider.withdraw(player, internalName, requiredAmount);
            return;
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial != null && requiredAmount > 0) {
            player.getInventory().removeItem(new ItemStack(currencyMaterial, requiredAmount));
        }
    }

    private void addWagerToInventory(Player player, double amount) {
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null && provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
            java.math.BigDecimal refund = MoneyHelper.clampNonNegative(MoneyHelper.bd(amount));
            if (refund.compareTo(java.math.BigDecimal.ZERO) > 0) {
                vaultProvider.deposit(player, internalName, refund);
            }
            return;
        }
        int totalAmount = (int) Math.floor(amount);
        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, 64);
            } else {
                stack = new ItemStack(currencyMaterial, 64);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
        if (remainder > 0) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, remainder);
            } else {
                stack = new ItemStack(currencyMaterial, remainder);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
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
                    inventory.setItem(1, createCustomItem(
                        Material.CLOCK,
                        text("blackjack.starts-in", "seconds", countdown),
                        countdown
                    ));
                    if (countdown <=3 ){
                        for (UUID uuid : playerSeats.keySet()) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                 if (SoundHelper.getSoundSafely("block.note_block.hat", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1.0f, 1.0f);
                            }
                        }
                    }
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
            leverMeta.setDisplayName(text("blackjack.dealer-turn")); // Set the display name to "Dealer's turn"
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
    List<UUID> bettingPlayerOrder = new ArrayList<>();
    for (UUID playerId : playerSeats.keySet()) {
        if (playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty()) {
            bettingPlayerOrder.add(playerId);
        }
    }

    BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(
        bettingPlayerOrder, playerSeats, 2, 3, (int) BlackjackTiming.CARD_DEAL_DELAY_TICKS
    );

    for (BlackjackDealPlan.Step step : plan.getSteps()) {
        if (step.isHidden()) {
            // Hidden placeholder: no Card is drawn from the shoe here, only
            // revealed later in revealDealerCardWithDelay.
            scheduleHiddenCardDealing(step.getSlot(), step.getDelayTicks());
        } else {
            scheduleCardDealing(step.getSlot(), deck.dealCard(), step.getDelayTicks(), step.getPlayerId());
        }
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
    }, plan.initialBlackjackCheckDelayTicks()); // Delay slightly longer to allow cards to be fully dealt
}

private void startNextPlayerTurn() {
    if (!gameActive || playerSeats.isEmpty()) {
       // cancelGame(); // If game is no longer active or all players have left, stop immediately
        return;
    }
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
        if (previousPlayerId != null) {
            ItemStack prevItem = inventory.getItem(playerSeats.get(previousPlayerId) + 1);
            ItemStack replacementItem = createCustomItem(Material.PAPER, text("blackjack.turn-over"), 1);
            
            // Retrieve and transfer lore properly using ItemMeta
            if (prevItem != null && prevItem.hasItemMeta()) {
                ItemMeta prevMeta = prevItem.getItemMeta();
                if (prevMeta.hasLore()) {
                    ItemMeta replacementMeta = replacementItem.getItemMeta();
                    replacementMeta.setLore(prevMeta.getLore());
                    replacementItem.setItemMeta(replacementMeta);
                }
            }
            
            inventory.setItem(playerSeats.get(previousPlayerId) + 1, replacementItem);
        }
        
        currentPlayerId = playerIterator.next();
        if (!playerDone.getOrDefault(currentPlayerId, false)) { // Skip players who are done
            Player currentPlayer = Bukkit.getPlayer(currentPlayerId);
            ItemStack item = inventory.getItem(playerSeats.get(currentPlayerId) + 1);
            
            ItemStack enchantedItem = createEnchantedItem(Material.BOOK, text("blackjack.your-turn"), 1);
            switch(plugin.getPreferences(currentPlayer.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                 
                    currentPlayer.sendMessage(text(currentPlayer, "blackjack.your-turn-message"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            // Retrieve and transfer lore properly using ItemMeta
            if (item != null && item.hasItemMeta()) {
                ItemMeta itemMeta = item.getItemMeta();
                if (itemMeta.hasLore()) {
                    ItemMeta enchantedMeta = enchantedItem.getItemMeta();
                    enchantedMeta.setLore(itemMeta.getLore());
                    enchantedItem.setItemMeta(enchantedMeta);
                }
            }

            addItem(enchantedItem, playerSeats.get(currentPlayerId) + 1);
             if (SoundHelper.getSoundSafely("block.enchantment_table.use", currentPlayer) != null)currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f); 
            
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

            return;
        }
    }

    if (playerSeats.get(currentPlayerId) != null) {
        ItemStack prevItem = inventory.getItem(playerSeats.get(currentPlayerId) + 1);
        ItemStack replacementItem = createCustomItem(Material.PAPER, text("blackjack.turn-over"), 1);
        
        // Retrieve and transfer lore properly using ItemMeta
        if (prevItem != null && prevItem.hasItemMeta()) {
            ItemMeta prevMeta = prevItem.getItemMeta();
            if (prevMeta.hasLore()) {
                ItemMeta replacementMeta = replacementItem.getItemMeta();
                replacementMeta.setLore(prevMeta.getLore());
                replacementItem.setItemMeta(replacementMeta);
            }
        }

        inventory.setItem(playerSeats.get(currentPlayerId) + 1, replacementItem);
    }

    // No more players left, proceed to the dealer's turn
    startDealerTurn();
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
    updateLeverDisplayName(text("blackjack.dealer-turn-capitalized"));

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
    if (!gameActive || playerSeats.isEmpty()) {
    // If game is no longer active or all players have left, stop immediately
        return;
    }
    final int[] mutableDealerCardSum = {dealerCardSum}; // Wrap the dealerCardSum in an array to make it mutable
    Nccasino nccasino = (Nccasino) plugin;



    int standOn17Chance=100;

    if (!nccasino.getConfig().contains("dealers." + internalName + ".stand-on-17")) {
        // If the key doesn't exist, set it to 100
        nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
    } else {
        // Retrieve the current value
        String value = plugin.getConfig().getString("dealers." + internalName + ".stand-on-17", "100").trim();
        
        try {
            standOn17Chance = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            standOn17Chance = 100; // Default
            plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            plugin.saveConfig();
        }
    
        // Check if the value is greater than 100 or less than 0
        if (standOn17Chance > 100 ) {
            // Reset the value to 100
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        }
        else if(standOn17Chance < 0){
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 0);

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
        if (!BlackjackRules.dealerShouldHit(17, standOn17Chance, Math.random() * 100)) {
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


private void refundBet(Player player, Map<Integer, Double> bets) {
    if (bets != null) {
        double totalBet = bets.values().stream().mapToDouble(Double::doubleValue).sum();
        addWagerToInventory(player, totalBet);
    }
}

private void finishGame() {
    for (UUID playerId : playerSeats.keySet()) {
        if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty()) {
            continue; // Skip players without bets
        }

        Player player = Bukkit.getPlayer(playerId);
        Map<Integer, Double> bets = playerBets.get(playerId);

        // Player-natural-blackjack takes precedence over everything else,
        // including a dealer natural -- BlackjackRules.classify checks it
        // first, same as the branch order this replaced. That precedence
        // is preserved intentionally; see BlackjackRulesTest for the
        // both-natural regression case.
        BlackjackOutcome outcome = BlackjackRules.classify(playerHands.get(playerId), dealerHand);

        switch (outcome) {
            case BLACKJACK: {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text(player, "blackjack.result-blackjack"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.result-blackjack"));
                        break;

                    }
                        case NONE:{
                        break;
                    }
                }
                if (SoundHelper.getSoundSafely("ui.toast.challenge_complete", player) != null)player.playSound(player.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.MASTER, 1.0f,1.0f);
                player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
                payOut(player, bets, outcome.getMultiplier()); // Pay out 2.5x for a blackjack
                break;
            }
            case BUST: {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text(player, "blackjack.result-busted"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.result-busted"));
                        break;
                    }
                        case NONE:{
                        break;
                    }
                }
                 if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
                break;
            }
            case WIN: {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text(player, "blackjack.result-won"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.result-won"));
                        break;

                    }
                        case NONE:{
                        break;
                    }
                }
                player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
                Random random = new Random();
                // We'll pick from a small array of fun pitches
                float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
                for (int i = 0; i < 3; i++) {
                    float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                     if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,SoundCategory.MASTER,1.0f,chosenPitch);
                    // Schedule them slightly apart for a "ding-ding-ding" effect

                }
                payOut(player, bets, outcome.getMultiplier()); // Regular win pays out 2x
                break;
            }
            case LOSS: {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text(player, "blackjack.result-lost"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.result-lost"));
                        break;
                    }
                        case NONE:{
                        break;
                    }
                }
                 if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
                break;
            }
            case PUSH: {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text(player, "blackjack.result-push"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.result-push-returned"));
                        break;

                    }
                        case NONE:{
                        break;
                    }
                }
                refundBet(player, bets);
                 if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(),Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 20);
                break;
            }
            default:
                // Never silently fall back to a push/refund for an outcome
                // this switch doesn't know about -- that's exactly the
                // mistake that would hide a bug once insurance/splitting
                // add new outcomes.
                throw new IllegalStateException("Unhandled BlackjackOutcome: " + outcome);
        }
    }

    // Reset game for the next round
    resetGame();
}

private void payOut(Player player, Map<Integer, Double> bets, double multiplier) {
    if (bets != null) {
        double totalBet = bets.values().stream().mapToDouble(Double::doubleValue).sum();
		CurrencyProvider provider = getCurrencyProvider();

		if (provider != null && provider.getMode() == org.nc.nccasino.currency.CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
			java.math.BigDecimal betAmount = MoneyHelper.clampNonNegative(MoneyHelper.bd(totalBet));
			java.math.BigDecimal payout = betAmount.multiply(MoneyHelper.bd(multiplier));
			java.math.BigDecimal displayPayout = MoneyHelper.roundDisplay(payout);
			java.math.BigDecimal displayProfit = MoneyHelper.roundDisplay(payout.subtract(betAmount));

			if (payout.compareTo(java.math.BigDecimal.ZERO) > 0) {
				vaultProvider.deposit(player, internalName, payout);
			}

			switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
				case STANDARD:{
					player.sendMessage(text(
                        player,
                        "payout.paid",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayPayout.doubleValue())
                    ));
					break;}
				case VERBOSE:{
					player.sendMessage(text(
                        player,
                        "payout.paid-with-profit",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayPayout.doubleValue()),
                        "profit",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayProfit.doubleValue())
                    ));
					break;     
				}
					case NONE:{
					break;
				}
			}
			return;
		}

        double payout = totalBet * multiplier;
        int totalAmount = applyProbabilisticRounding(payout);

        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
        int totalDropped = 0; // Track how many items were dropped

        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, 64);
            } else {
                stack = new ItemStack(currencyMaterial, 64);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }

        if (remainder > 0) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, remainder);
            } else {
                stack = new ItemStack(currencyMaterial, remainder);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(
                    player,
                    "payout.paid",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalAmount)
                ));
                break;}
            case VERBOSE:{
                player.sendMessage(text(
                    player,
                    "payout.paid-with-profit",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalAmount),
                    "profit",
                    (int) Math.abs(totalAmount - totalBet)
                ));
                break;     
            }
                case NONE:{
                break;
            }
        } 

        //player.sendMessage("§a§l" + (int)payout + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(payout) == 1 ? "" : "s") + "\n");
        // Print total dropped if any items couldn't fit in inventory
        if (totalDropped > 0) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(
                        player,
                        "blackjack.inventory-full",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalDropped)
                    ));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(
                        player,
                        "blackjack.inventory-full",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalDropped)
                    ));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
         } else {

        }
    }
}

private int applyProbabilisticRounding(double value) {
    int integerPart = (int) value;
    double fractionalPart = value - integerPart;
    Random random = new Random();
    if (random.nextDouble() <= fractionalPart) {
        return integerPart + 1; // Round up based on probability
    }
    return integerPart; // Otherwise, keep it rounded down
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
    addItem(createCustomItem(Material.CLOCK, text("blackjack.game-info")), 1);

    // Reinitialize the game menu but do not clear player seats
    initializeGameMenu();

    // Re-populate the player heads in the seats
    for (UUID playerId : playerSeats.keySet()) {
        int seatSlot = playerSeats.get(playerId);
        inventory.setItem(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
    }

}

private int calculateHandValue(List<Card> hand) {
    return BlackjackRules.handValue(hand);
}

private void scheduleCardDealing(int slot, Card card, int delay, UUID playerId) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (!gameActive || playerSeats.isEmpty()) {
            return;
        }
        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                 if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
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
    if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty() || playerSeats.get(playerId) == null) {
        return; // Skip updating if the player hasn't placed a bet
    }
    
    List<Card> hand = playerHands.get(playerId);
    String handValue = calculateHandValueWithSoftCheck(hand);
    
    int seatSlot = playerSeats.get(playerId);
    updateHeadLore(seatSlot, handValue, Bukkit.getPlayer(playerId).getName());
}

private void updateDealerHead() {
    String handValue = calculateHandValueWithSoftCheck(dealerHand);
    updateHeadLore(0, handValue, text("blackjack.dealer"));
}


private void updateHeadLore(int slot, String cardValue, String name) {
    ItemStack headItem = inventory.getItem(slot);
    if (headItem != null && (headItem.getType() == Material.PLAYER_HEAD || headItem.getType() == Material.CREEPER_HEAD)) {
        ItemMeta meta = headItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add(text("blackjack.card-value", "value", cardValue));
            meta.setLore(lore);
            headItem.setItemMeta(meta);
            inventory.setItem(slot, headItem);
        }
    }
}
private String calculateHandValueWithSoftCheck(List<Card> hand) {
    if (hand == null || hand.isEmpty()) {
        return "0";
    }

    BlackjackHandValue value = BlackjackRules.evaluate(hand);

    // Return soft/hard value if an ace is present; otherwise, just return the hard value
    if (value.isSoft()) {
        return value.getBestTotal() + "/" + value.getHardTotal();
    } else {
        return String.valueOf(value.getHardTotal());
    }
}

private void scheduleHiddenCardDealing(int slot, int delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (!gameActive || playerSeats.isEmpty()) {
            return;
        }
        Material material = Material.WHITE_STAINED_GLASS_PANE; // Hidden card is now white
        ItemStack hiddenCard = new ItemStack(material, 1);
        ItemMeta meta = hiddenCard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("blackjack.hidden-card"));
            hiddenCard.setItemMeta(meta);
        }
        
        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                 if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
        inventory.setItem(slot, hiddenCard);
    }, delay);
}

private void dealCardToPlayer(int slot, Card card, UUID playerId) {
    // No changes needed here; the deck will reshuffle automatically if it's empty.
    
    if (!playerSeats.containsKey(playerId) && slot > 9) {
        return;
    }
    Material material = (card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
    int stackSize = BlackjackRules.cardStackSize(card);
    for (UUID uuid : playerSeats.keySet()) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
             if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
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
        playerHands.computeIfAbsent(playerId, k -> new ArrayList<>()).add(card);
        updatePlayerHead(playerId);
    } else { // If this card is dealt to the dealer
        dealerHand.add(card);
        updateDealerHead();
    }
}


public void delete() {
    // Stop any ongoing game operations
    cancelGame();

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
    
        // Clear all player and game-related data
        clearPlayerBets(null); // Clear all bets
        clearAllLore(); // Clear all lore
        playerBets.clear();
        lastBetAmounts.clear();
        playerCardCounts.clear();
        playerDone.clear();
        playerHands.clear();
        dealerHand.clear();
        playerIterator = null;
        currentPlayerId = null;
        selectedWagers.clear();
    
        // Stop any ongoing scheduled tasks
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
    
        // Clear and reset the inventory
        inventory.clear();
        initializeGameMenu(); // Reset the game menu
    
        // Reset player seats
        playerSeats.clear();
        
    }
    

        @EventHandler
        public void handleInventoryClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() != this) return;

            Player player = (Player) event.getPlayer();
            UUID playerId = player.getUniqueId();

            if (!playerSeats.containsKey(playerId)) {
                return;
            }

            // Route through the same idempotent path used for quit/kick,
            // rather than resolving the wager directly here. It's unclear
            // whether InventoryCloseEvent reliably fires on a real
            // disconnect, so this must not race ahead of PlayerQuitEvent
            // and get it wrong (e.g. refunding a kicked player) — whichever
            // of this or the quit event fires first "wins" and the other
            // becomes a safe no-op, and consumeQuitReason still correctly
            // reports KICKED here even if this fires first, since the kick
            // is marked as soon as PlayerKickEvent itself fires.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!SessionRegistry.isRegistered(playerId, this)) {
                    return;
                }
                if (!player.isOnline()) {
                    ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
                    SessionRegistry.terminatePlayerSession(playerId, reason);
                    return;
                }
                SessionRegistry.terminateSession(playerId, this, ExitReason.DISCONNECTED);
            });
        }

    /**
     * Authoritative disconnect/kick resolution, reached via SessionRegistry
     * regardless of whether this fires from PlayerQuitEvent or from this
     * table's own InventoryCloseEvent. Transition is based on {@code
     * gameActive} — set the instant dealing is committed server-side, not
     * on any animation — never on what the client had visibly rendered.
     */
    @Override
    public void onSessionTerminated(UUID playerId, ExitReason reason) {
        if (!playerSeats.containsKey(playerId)) {
            // Already resolved through another path (e.g. a voluntary
            // leave-chair click that ran before this).
            return;
        }

        org.nc.nccasino.session.TerminationAction action =
            GameTerminationPolicy.blackjack(reason, gameActive);
        if (action == org.nc.nccasino.session.TerminationAction.FORFEIT) {
            // Deal has begun: forfeit unconditionally. Never calculate an
            // automated hand or create a pending payout. Advance the turn
            // safely first if it was theirs.
            if (playerId.equals(currentPlayerId)) {
                playerDone.put(playerId, true);
                startNextPlayerTurn();
            }
            removePlayerData(playerId);
        } else if (action == org.nc.nccasino.session.TerminationAction.REFUND) {
            // Before the deal: refund the committed wager, unless kicked —
            // a kicked player forfeits regardless of phase.
            refundPendingBets(playerId, reason);
            removePlayerData(playerId);
        } else {
            removePlayerData(playerId);
        }

    }

    /**
     * Refunds whatever {@code playerId} currently has wagered. Called
     * before removePlayerData, which clears the underlying bet records
     * without moving any currency itself.
     */
    private void refundPendingBets(UUID playerId, ExitReason reason) {
        Map<Integer, Double> bets = playerBets.get(playerId);
        if (bets == null || bets.isEmpty()) {
            return;
        }

        double total = bets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return;
        }

        if (reason == ExitReason.PLUGIN_DISABLE) {
            Material currencyMaterial = plugin.getCurrency(internalName);
            PendingPayout payout = PendingPayout.create(
                playerId,
                "Blackjack",
                internalName,
                currencyMode,
                currencyMaterial != null ? currencyMaterial.name() : null,
                currencyName,
                total,
                PayoutMessages.serverRestartRefundContext("Blackjack")
            );
            if (!plugin.getPendingPayoutStore().addPendingPayout(payout)) {
                plugin.getLogger().warning("[NCCasino] Blackjack shutdown refund failed to persist for " + playerId + ".");
            }
        } else {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                addWagerToInventory(player, total);
            }
        }
    }

    private String text(String key, Object... placeholders) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < placeholders.length; index += 2) {
            values.put(String.valueOf(placeholders[index]), placeholders[index + 1]);
        }
        return plugin.getLocalization().text(
            plugin.getLocalization().getServerDefault(),
            key,
            values
        );
    }

    private String text(Player player, String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
