package org.nc.nccasino.games.Baccarat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Deck;

public class BaccaratServer extends Server {
    private final Map<BaccaratClient.BetOption, Double> totalBets = new HashMap<>();
    private final Map<UUID, Map<BaccaratClient.BetOption, Double>> playerBets = new HashMap<>();
   
    private int countdownTaskId = -1;
    private int timeLeft;
    private Deck deck;
    private List<Card> playerHand = new ArrayList<>();
    private List<Card> bankerHand = new ArrayList<>();
    private String currentWinString=null;
    public BaccaratServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
        this.timeLeft = plugin.getTimer(internalName); // Load timer from config
        int numberOfDecks;

        if (!plugin.getConfig().contains("dealers." + internalName + ".number-of-decks")) {
            numberOfDecks = 8;
            plugin.getConfig().set("dealers." + internalName + ".number-of-decks", numberOfDecks);
        } else {
            // Retrieve the current value
            int currentDecks = plugin.getConfig().getInt("dealers." + internalName + ".number-of-decks");
        
            // Check if the value is less than 1
            if (currentDecks <= 0) {
                numberOfDecks = 8;
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", numberOfDecks);
            }
            else if (currentDecks>10000){
                numberOfDecks = 10000;
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", numberOfDecks);
            }
            else{
                numberOfDecks=currentDecks;
            }
        }
        this.deck = new Deck(numberOfDecks); // Initialize the deck
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        BaccaratClient client = new BaccaratClient(this, player, plugin, internalName);
        client.initializeUI(true, false);
        clients.put(player.getUniqueId(), client);
    
        // Send current hand totals (if hands are empty, this will send {-1, -1})
        int playerTotal = getBaccaratHandValue(playerHand);
        int bankerTotal = getBaccaratHandValue(bankerHand);
        client.onServerUpdate("UPDATE_HAND_TOTALS", new int[]{playerTotal, bankerTotal});
        client.onServerUpdate("UPDATE_TIMER", timeLeft);

        // If game is running, send the correct game state
        if (gameState == GameState.RUNNING) {
            client.onServerUpdate("CATCHUP_START", null);
            
    
            // **Send cards in the correct sequence, one at a time**
            if (playerHand.size() > 0) {
                client.onServerUpdate("DEAL_CARDS", Arrays.asList(playerHand.get(0)));
            }
            if (bankerHand.size() > 0) {
                client.onServerUpdate("DEAL_CARDS", Arrays.asList(bankerHand.get(0)));
            }
            if (playerHand.size() > 1) {
                client.onServerUpdate("DEAL_CARDS", Arrays.asList(playerHand.get(1)));
            }
            if (bankerHand.size() > 1) {
                client.onServerUpdate("DEAL_CARDS", Arrays.asList(bankerHand.get(1)));
            }
    
            // Send third-card draws if applicable
            if (playerHand.size() == 3) {
                client.onServerUpdate("PLAYER_DRAW", playerHand.get(2));
            }
            if (bankerHand.size() == 3) {
                client.onServerUpdate("BANKER_DRAW", bankerHand.get(2));
            }
            if(currentWinString!=null){
               client.onServerUpdate("RESULT", currentWinString);
            }
            client.onServerUpdate("CATCHUP_COMPLETE", null);

        }
    
        // Send current bet displays
        Map<BaccaratClient.BetOption, Double> playerCurrentBets = playerBets.getOrDefault(player.getUniqueId(), new HashMap<>());
        for (BaccaratClient.BetOption betType : totalBets.keySet()) {
            double totalBet = totalBets.get(betType);
            double playerBet = playerCurrentBets.getOrDefault(betType, 0.0);
            client.onServerUpdate("UPDATE_BET_DISPLAY", new BetDisplayData(betType, playerBet, totalBet));
        }
    
    
    
        Bukkit.getLogger().info(" clients.size(): " + clients.size() + " | gameState: " + gameState);
    
        // Start timer if this is the first player joining
        if (clients.size() == 1 && gameState == GameState.WAITING) {
            startTimer();
        }
    
        return client;
    }
    
        
    
    
    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        Player player = client.getPlayer();
    
        switch (eventType) {
            case "PLACE_BET":
            if (gameState == GameState.WAITING) {     
                if (data instanceof BetData) {
                    BetData bet = (BetData) data;
                    processBet(player, bet.betType, bet.amount);
                }
            }
            else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
        
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cBets are closed, cannot place bet.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                return;
            }
                break;
            case "UNDO_BET":
            if (gameState == GameState.WAITING) {     
                if (data instanceof BetData) {
                    BetData bet = (BetData) data;
                    undoLastBet(player, bet.betType, bet.amount);
                }
            }
            else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
        
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cBets are closed, cannot undo bet.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                return;
            }
                break;
            case "UNDO_ALL_BETS":
            if (gameState == GameState.WAITING) {     
                undoAllBets(player);
            }
            else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid action.");
        
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cBets are closed, cannot undo bet.");
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                return;
            }
                break;
            case "INVENTORY_OPEN":
                if (gameState == GameState.PAUSED) {
                    Bukkit.broadcastMessage("Game resumed as a player has rejoined.");
                    startTimer();
                }
                resumeGame();
                break;
            case "INVENTORY_CLOSE":
                if (clients.isEmpty() && totalBets.isEmpty()) {
                    Bukkit.broadcastMessage("No players left and no bets - pausing game.");
                    pauseGame();
                }
                break;
        }
    }
    
    private void processBet(Player player, BaccaratClient.BetOption betType, double amount) {
        UUID playerId = player.getUniqueId();
        playerBets.putIfAbsent(playerId, new HashMap<>());
    
        // Accumulate this player's bet correctly
        playerBets.get(playerId).merge(betType, amount, Double::sum);
    
        // Accumulate total bets correctly
        totalBets.merge(betType, amount, Double::sum);
    
        // Broadcast a single update to ALL players
        for (Client c : clients.values()) {
            if (c instanceof BaccaratClient baccaratClient) {
                baccaratClient.onServerUpdate("UPDATE_BET_DISPLAY", new BetDisplayData(
                    betType, 
                    playerBets.getOrDefault(c.getPlayer().getUniqueId(), new HashMap<>()).getOrDefault(betType, 0.0), // Their personal bet
                    totalBets.get(betType) // Correct total bet
                ));
            }
        }
    }
    
    private void undoLastBet(Player player, BaccaratClient.BetOption betType, double amount) {
        UUID playerId = player.getUniqueId();
        if (!playerBets.containsKey(playerId) || !playerBets.get(playerId).containsKey(betType)) return;
    
        double remaining = playerBets.get(playerId).get(betType) - amount;
        if (remaining <= 0) {
            playerBets.get(playerId).remove(betType);
        } else {
            playerBets.get(playerId).put(betType, remaining);
        }
    
        totalBets.computeIfPresent(betType, (k, v) -> (v - amount) <= 0 ? null : v - amount);

        broadcastUpdate("UPDATE_BET_DISPLAY", new BetDisplayData(
            betType, 
            playerBets.get(playerId).getOrDefault(betType, 0.0), 
            totalBets.getOrDefault(betType, 0.0)
        ));
    }
    
    private void undoAllBets(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playerBets.containsKey(playerId)) return;
    
        for (BaccaratClient.BetOption betType : new ArrayList<>(playerBets.get(playerId).keySet())) {
            double amount = playerBets.get(playerId).get(betType);
            totalBets.computeIfPresent(betType, (k, v) -> (v - amount) <= 0 ? null : v - amount);
        }        
    
        playerBets.remove(playerId);
        broadcastUpdate("RESET_BETS", null);
    
        if (playerBets.isEmpty() && clients.isEmpty()) {
            pauseGame();
        }
    }
    
    public double getTotalBetForType(BaccaratClient.BetOption betType) {
        return totalBets.getOrDefault(betType, 0.0);
    }

    public int getBettorCountForType(BaccaratClient.BetOption betType) {
        int count = 0;
        for (Map<BaccaratClient.BetOption, Double> bets : playerBets.values()) {
            if (bets.containsKey(betType)) {
                count++;
            }
        }
        return Math.max(1, count); // Ensure at least 1 bettor is always displayed
    }

    private void startTimer() {
        if (countdownTaskId != -1) return; // Timer is already running

        gameState = GameState.WAITING;
        timeLeft = plugin.getTimer(internalName);
              // Trigger rebet for all clients if enabled
              for (Client client : clients.values()) {
                if (client instanceof BaccaratClient baccaratClient) {
                    baccaratClient.reapplyPreviousBets();
                }
            }
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (timeLeft <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
                // If no viewers and no bets, PAUSE instead of restarting timer
                if (clients.isEmpty() && totalBets.isEmpty()) {
                    Bukkit.broadcastMessage("No viewers and no bets - pausing game.");
                    pauseGame();
                    return;
                }
                gameState = GameState.RUNNING;

                // Otherwise, start the game
                startGame();
                return;
            }

            updateTimerDisplay(timeLeft);
            timeLeft--;

            if (timeLeft <= 6) {
                playCountdownSound();
            }

        }, 0L, 20L); // Run every second
    }
    
    private void pauseGame() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        gameState = GameState.PAUSED;
        updateTimerDisplay(-1); // Remove timer
    }

    private void resumeGame() {
        if (gameState == GameState.PAUSED) {
            startTimer();
        }
    }

    private void updateTimerDisplay(int seconds) {
        broadcastUpdate("UPDATE_TIMER", seconds);
    }

    private void startGame() {
        if (clients.isEmpty() && totalBets.isEmpty()) {
            Bukkit.broadcastMessage("No players watching and no bets - waiting for player.");
            return; // Do nothing, just wait for players to return
        }
    
        updateTimerDisplay(-1); // Remove timer UI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            dealInitialCards();
        }, 20L);
        //evaluateHands();
    
  
    }
    
    private void dealInitialCards() {
        playerHand.clear();
        bankerHand.clear();
    
        // Correct Baccarat draw order
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerHand.add(deck.dealCard());
            broadcastUpdate("DEAL_CARDS", Arrays.asList(playerHand.get(0)));
            updateHandTotals();
        }, 20L);
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bankerHand.add(deck.dealCard());
            broadcastUpdate("DEAL_CARDS", Arrays.asList(bankerHand.get(0)));
            updateHandTotals();
        }, 40L);
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerHand.add(deck.dealCard());
            broadcastUpdate("DEAL_CARDS", Arrays.asList(playerHand.get(1)));
            updateHandTotals();
        }, 60L);
    
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bankerHand.add(deck.dealCard());
            broadcastUpdate("DEAL_CARDS", Arrays.asList(bankerHand.get(1)));
            updateHandTotals();
        }, 80L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (playerHand.size() >= 2 && bankerHand.size() >= 2) {
                evaluateHands();
            } else {
                Bukkit.getLogger().warning("evaluateHands() called too early, retrying...");
                Bukkit.getScheduler().runTaskLater(plugin, this::evaluateHands, 20L);
            }
        }, 100L);
    
      
    }
    
    
    private void evaluateHands() {
         if (playerHand.size() < 2 || bankerHand.size() < 2) {
            Bukkit.getLogger().warning("evaluateHands called too early! Delaying...");
            Bukkit.getScheduler().runTaskLater(plugin, this::evaluateHands, 20L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int playerTotal = getBaccaratHandValue(playerHand);
            int bankerTotal = getBaccaratHandValue(bankerHand);
    
            if (playerTotal >= 8 || bankerTotal >= 8) {
                determineWinner();
                return;
            }
    
            boolean playerDraws = playerTotal <= 5 && playerHand.size() < 3;
    
            if (playerDraws) {
                // Player draws a third card
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    playerHand.add(deck.dealCard());
                    broadcastUpdate("PLAYER_DRAW", playerHand.get(playerHand.size() - 1));
                    updateHandTotals();
    
                    int newPlayerTotal = getBaccaratHandValue(playerHand);
                    int playerThirdCard = getCardValue(playerHand.get(2));
    
                    // Now decide if banker draws
                    handleBankerDraw(newPlayerTotal, bankerTotal, playerThirdCard);
                }, 20L);
            } else {
                // If the player does not draw, check if banker should draw
                handleBankerDraw(playerTotal, bankerTotal, null);
            }
        }, 20L);
    }
    
    private void handleBankerDraw(int playerTotal, int bankerTotal, Integer playerThirdCard) {
        boolean bankerDraws = shouldBankerDraw(playerTotal, bankerTotal, playerThirdCard);
        
        if (bankerDraws) {
                drawBankerCard();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::determineWinner, 40L);
        }
    }
    
    
    private void drawBankerCard() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bankerHand.add(deck.dealCard());
            broadcastUpdate("BANKER_DRAW", bankerHand.get(bankerHand.size() - 1));
            updateHandTotals();
            Bukkit.getScheduler().runTaskLater(plugin, this::determineWinner, 40L);
        }, 20L);
    }
    
    private void updateHandTotals() {
        broadcastUpdate("UPDATE_HAND_TOTALS", new int[]{
            getBaccaratHandValue(playerHand), 
            getBaccaratHandValue(bankerHand)
        });
    }
    
    private int getBaccaratHandValue(List<Card> hand) {
        if (hand.isEmpty()) return -1; // Prevent IndexOutOfBoundsException
        int total = 0;
        for (Card card : hand) {
            total += getCardValue(card);
        }
        return total % 10; // Baccarat rule: only last digit matters
    }
    
    private boolean shouldBankerDraw(int playerTotal, int bankerTotal, Integer playerThirdCard) {
        if (bankerTotal <= 2) return true; // Banker always draws if total is 0, 1, or 2
        if (bankerTotal >= 7) return false; // Banker stands on 7+
    
        // If the player did NOT draw, banker draws if total is 5 or less
        if (playerThirdCard == null) return bankerTotal <= 5;
    
        // Player did draw, apply third-card rules
        return switch (bankerTotal) {
            case 3 -> playerThirdCard != 8;  // Banker draws unless Player's third card is 8
            case 4 -> playerThirdCard >= 2 && playerThirdCard <= 7;
            case 5 -> playerThirdCard >= 4 && playerThirdCard <= 7;
            case 6 -> playerThirdCard == 6 || playerThirdCard == 7;
            default -> false;
        };
    }

    private void determineWinner() {
        int playerTotal = getBaccaratHandValue(playerHand);
        int bankerTotal = getBaccaratHandValue(bankerHand);
        String result;
        
        if (playerTotal > bankerTotal) {
            result = "PLAYER_WINS";
        } else if (playerTotal < bankerTotal) {
            result = "BANKER_WINS";
        } else {
            result = "TIE";
        }
    
        broadcastUpdate("RESULT", result);
        currentWinString=result;
        processPayouts(result);
    
        // Save the current bets before the game resets
        for (Client client : clients.values()) {
            if (client instanceof BaccaratClient &&client.rebetEnabled) {
                ((BaccaratClient) client).saveCurrentBets();
            }
        }
    
        resetGame();
    }
    
    
    private void processPayouts(String result) {
    for (UUID playerId : playerBets.keySet()) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) continue;

        double payout = 0.0;
        double totalBet = 0.0;
        Map<BaccaratClient.BetOption, Double> bets = playerBets.get(playerId);
        
        for (BaccaratClient.BetOption betType : bets.keySet()) {
            double wager = bets.get(betType);
            totalBet += wager; 
            switch (betType) {
                case PLAYER:
                    if (result.equals("PLAYER_WINS")) payout += wager * 2;
                    break;
                case BANKER:
                    if (result.equals("BANKER_WINS")) payout += wager * 1.95; // 5% commission
                    break;
                case TIE:
                    if (result.equals("TIE")) payout += wager * 9;
                    break;
                case PLAYERPAIR:
                    if (playerHand.get(0).getRank() == playerHand.get(1).getRank()) payout += wager * 12;
                    break;
                case BANKERPAIR:
                    if (bankerHand.get(0).getRank() == bankerHand.get(1).getRank()) payout += wager * 12;
                    break;
            }
        }

        if (payout > 0) {
            creditPlayer(player, payout);
        }
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§a§lPaid "+ (int)payout+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(payout) == 1 ? "" : "s"));
                break;}
            case VERBOSE:{
                player.sendMessage("§a§lPaid "+ (int)payout+" "+ plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(payout) == 1 ? "" : "s")  + "\n §r§a§o(profit of "+(int)Math.abs(payout-totalBet)+")");
                break;     
            }
                case NONE:{
                break;
            }
        } 
        if (payout > totalBet) {
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            Random random = new Random();
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                 if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,1.0f,chosenPitch);
            }
        } else if (payout == totalBet) {
            if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.SCRAPE, player.getLocation(), 20); 
        } else {
        if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);  
        }

    }

    resetGame();
}

private void creditPlayer(Player player, double amount) {
    Material currencyMaterial = plugin.getCurrency(internalName);
    if (currencyMaterial == null) {
        player.sendMessage("Error: Currency material is not set. Unable to credit winnings.");
        return;
    }

    int fullStacks = (int) amount / 64;
    int remainder = (int) amount % 64;
    int totalLeftoverAmount = 0;
    HashMap<Integer, ItemStack> leftover;

    // Try adding full stacks
    for (int i = 0; i < fullStacks; i++) {
        ItemStack stack = new ItemStack(currencyMaterial, 64);
        leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
    }

    // Try adding remainder
    if (remainder > 0) {
        ItemStack remainderStack = new ItemStack(currencyMaterial, remainder);
        leftover = player.getInventory().addItem(remainderStack);
        if (!leftover.isEmpty()) {
            totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
    }

    if (totalLeftoverAmount > 0) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");

                break;}
            case VERBOSE:{
                player.sendMessage("§cNo room for " + totalLeftoverAmount + " " + plugin.getCurrencyName(internalName).toLowerCase()+ (Math.abs(totalLeftoverAmount) == 1 ? "" : "s") + ", dropping...");
                break;     
            }
                case NONE:{
                break;
            }
        } 
        dropExcessItems(player, totalLeftoverAmount, currencyMaterial);
    }
}

private void dropExcessItems(Player player, int amount, Material currencyMaterial) {
    while (amount > 0) {
        int dropAmount = Math.min(amount, 64);
        player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(currencyMaterial, dropAmount));
        amount -= dropAmount;
    }
}

private void resetGame() {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        currentWinString=null;
        playerBets.clear();
        totalBets.clear();
        playerHand.clear();
        bankerHand.clear();
        broadcastUpdate("RESET_BETS", null);
        broadcastUpdate("CLEAR_CARDS", null);
        broadcastUpdate("UPDATE_HAND_TOTALS", new int[]{-1, -1});
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
        startTimer();
        }, 10L);
    }, 70L); // Delay before resetting for UI effects
}


    private int getCardValue(Card card) {
        return switch (card.getRank()) {
            case ACE -> 1;
            case TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE -> card.getRank().ordinal() + 2;
            default -> 0;
        };
    }
}
