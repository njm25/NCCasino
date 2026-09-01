package org.nc.nccasino.games.Roulette;

import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.TableGenerator;
import org.nc.nccasino.objects.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import java.util.*;
import org.nc.nccasino.payout.BankedCurrency;
import org.nc.nccasino.payout.OverflowBankService;
import org.nc.nccasino.payout.WagerGate;
import org.nc.nccasino.payout.WagerFunding;
import org.nc.nccasino.payout.ItemDeliveryOutcome;
import org.nc.nccasino.budget.Commitment;
import org.nc.nccasino.budget.DealerBudgetService;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

public class BettingTable extends DealerInventory {
    public static final Set<UUID> switchingPlayers = new HashSet<>();
    private final UUID playerId;
    public final UUID dealerId;
    private final Mob dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private final RouletteInventory rouletteInventory;
    private double selectedWager;
    private int pageNum;
    private Boolean allin=false;
    private final Map<Integer, Double> chipValues;
    private final Stack<Pair<String, Integer>> betStack;
    private Stack<Pair<String, Integer>> testStack;
    private boolean betsClosed=false;
    private int countdown1=30;
    /**
     * The single dealer-budget promise covering this player's whole table,
     * updated atomically as bets are added -- Roulette bets are not
     * independent (several can win on one number), so the shared budget must
     * reason about the entire portfolio, never one bet at a time.
     */
    private Commitment budgetCommitment;
    private final String budgetSessionId = java.util.UUID.randomUUID().toString();
    private long budgetRoundCounter = 0;
    public BettingTable(Player player, Mob dealer, Nccasino plugin, Stack<Pair<String, Integer>> existingBets, String internalName,RouletteInventory rouletteInventory,int countdown) {
        super(player.getUniqueId(), 54, plugin.getLocalization().text(player, "roulette.table-title"));
        this.countdown1=countdown;
        this.playerId = player.getUniqueId();
        this.dealerId = Dealer.getUniqueId(dealer);
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.rouletteInventory = rouletteInventory;
        this.pageNum = 1;
    
        this.chipValues = new HashMap<>();
        
        loadChipValuesFromConfig();
        initializeTestStack();
        this.betStack = existingBets != null ? existingBets : new Stack<>();

        initializeTable();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadChipValuesFromConfig() {
        List<Double> configuredValues = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            configuredValues.add(plugin.getChipValue(internalName, i));
        }
        this.chipValues.putAll(ChipSlots.assign(configuredValues));
    }

    private void initializeTable() {
        setupPageOne();
    }

    private void setupPageOne() {
        inventory.clear();
        clearAllLore();
                // Directly update the clock item before other items
        updateClockItem(countdown1, betsClosed);
      
        addStraightUpBetsPageOne();
        addDozensAndOtherBetsPageOne();
        addCommonComponents();
        updateAllLore();
        
        // Force update inventory again after setting everything
        Bukkit.getPlayer(playerId).updateInventory();
    }
    
    private void setupPageTwo() {
        
        inventory.clear();
        clearAllLore();
        
        // Directly update the clock item before other items
        updateClockItem(countdown1, betsClosed);
       

        addStraightUpBetsPageTwo();
        addDozensAndOtherBetsPageTwo();
        addCommonComponents();
        updateAllLore();
        
        // Force update inventory again after setting everything
        Bukkit.getPlayer(playerId).updateInventory();
    }
    
    // Create a separate method for updating the clock item
    private void updateClockItem(int countdown, boolean betsClosed) {
        if (betsClosed) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, text("roulette.bets-closed"), 1));
            } else {
                inventory.setItem(35, createCustomItem(Material.CLOCK, text("roulette.bets-closed"), 1));
            }
        } else if (countdown > 0) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, text("roulette.bets-close-in", "seconds", countdown1), countdown1));
            } else {
                inventory.setItem(35, createCustomItem(Material.CLOCK, text("roulette.bets-close-in", "seconds", countdown1), countdown1));
            }
        }
    }

    private void addStraightUpBetsPageOne() {
        int[] numbersPageOne = {1, 3, 6, 9, 12, 15, 18, 21, 24, 0, 2, 5, 8, 11, 14, 17, 20, 23, 1, 1, 4, 7, 10, 13, 16, 19, 22};
        String[] colorsPageOne = {"BLUE", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "LIME", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLUE", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "RED", "BLACK"};

        for (int i = 0; i < 27; i++) {
            if (!(i == 0 || i == 18)) {
                if (numbersPageOne[i] == 0) {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), numbersPageOne[i]+" - 35:1", 1));
                } else {
                    inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageOne[i] + "_STAINED_GLASS_PANE"), numbersPageOne[i]+" - 35:1", numbersPageOne[i]));
                }
            }
        }
    }

    private void addStraightUpBetsPageTwo() {
        int[] numbersPageTwo = {15, 18, 21, 24, 27, 30, 33, 36, 3, 14, 17, 20, 23, 26, 29, 32, 35, 2, 13, 16, 19, 22, 25, 28, 31, 34, 1};
        String[] colorsPageTwo = {"BLACK", "RED", "RED", "BLACK", "RED", "RED", "BLACK", "RED", "GREEN", "RED", "BLACK", "BLACK", "RED", "BLACK", "BLACK", "RED", "BLACK", "GREEN", "BLACK", "RED", "RED", "BLACK", "RED", "BLACK", "BLACK", "RED", "GREEN"};

        for (int i = 0; i < numbersPageTwo.length; i++) {
            if (!(i == 8 || i == 17 || i == 26)) {
                inventory.setItem(i, createCustomItem(Material.valueOf(colorsPageTwo[i] + "_STAINED_GLASS_PANE"), numbersPageTwo[i]+" - 35:1", numbersPageTwo[i]));
            }
        }

        inventory.setItem(8, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-top-row"), 1));
        inventory.setItem(17, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-middle-row"), 1));
        inventory.setItem(26, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-bottom-row"), 1));
    }

    private void addDozensAndOtherBetsPageOne() {
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-first-dozen"), 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-first-dozen"), 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-first-dozen"), 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-first-dozen"), 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(35, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));

        inventory.setItem(37, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18 - 1:1", 1));
        inventory.setItem(38, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "1-18 - 1:1", 1));
        inventory.setItem(39, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-even"), 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-even"), 1));
        inventory.setItem(41, createCustomItem(Material.RED_STAINED_GLASS_PANE, text("roulette.bet-red"), 1));
        inventory.setItem(42, createCustomItem(Material.RED_STAINED_GLASS_PANE, text("roulette.bet-red"), 1));
        inventory.setItem(43, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, text("roulette.bet-black"), 1));
        inventory.setItem(44, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, text("roulette.bet-black"), 1));
    }

    private void addDozensAndOtherBetsPageTwo() {
        inventory.setItem(27, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(28, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(29, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(30, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-second-dozen"), 1));
        inventory.setItem(31, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-third-dozen"), 1));
        inventory.setItem(32, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-third-dozen"), 1));
        inventory.setItem(33, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-third-dozen"), 1));
        inventory.setItem(34, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-third-dozen"), 1));

        inventory.setItem(36, createCustomItem(Material.RED_STAINED_GLASS_PANE, text("roulette.bet-red"), 1));
        inventory.setItem(37, createCustomItem(Material.RED_STAINED_GLASS_PANE, text("roulette.bet-red"), 1));
        inventory.setItem(38, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, text("roulette.bet-black"), 1));
        inventory.setItem(39, createCustomItem(Material.BLACK_STAINED_GLASS_PANE, text("roulette.bet-black"), 1));
        inventory.setItem(40, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-odd"), 1));
        inventory.setItem(41, createCustomItem(Material.LIME_STAINED_GLASS_PANE, text("roulette.bet-odd"), 1));
        inventory.setItem(42, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36 - 1:1", 1));
        inventory.setItem(43, createCustomItem(Material.LIME_STAINED_GLASS_PANE, "19-36 - 1:1", 1));
    }

    private void addCommonComponents() {
        if(allin){
            inventory.setItem(52, createEnchantedItem(
                Material.SNIFFER_EGG,
                text(
                    "roulette.all-in-display",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, selectedWager)
                ),
                1
            ));
        }
        else{inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, text("roulette.all-in"), 1));}

        inventory.setItem(45, createCustomItem(Material.BARRIER, text("roulette.undo-all"), 1));
        inventory.setItem(46, createCustomItem(Material.WIND_CHARGE, text("roulette.undo-last"), 1));
        if(pageNum==1){
            inventory.setItem(36, createCustomItem(Material.ENDER_PEARL, text("roulette.back-wheel"), 1));

        }
        else{
            inventory.setItem(44, createCustomItem(Material.ENDER_PEARL, text("roulette.back-wheel"), 1));
        }
        for (Map.Entry<Integer, Double> entry : chipValues.entrySet()) {
            int chipSlot = entry.getKey();
            double chipValue = entry.getValue();
            String chipName = plugin.getChipDisplayName(currencyMode, currencyName, chipValue);
            if(chipValue==selectedWager){
                inventory.setItem(chipSlot, createEnchantedItem(plugin.getCurrency(internalName), chipName, (int) chipValue));

            }
            else{
            inventory.setItem(chipSlot, createCustomItem(plugin.getCurrency(internalName), chipName, (int) chipValue));
            }
        }

        inventory.setItem(53, createCustomItem(Material.ARROW, text("roulette.switch-page"), 1));
    }
    private void updateAllLore() {
        // Map to store the total bet for each bet type
        Map<String, Integer> betTotals = new HashMap<>();
    
        // Iterate through the bet stack and sum the totals for each bet type
        for (Pair<String, Integer> bet : betStack) {
            betTotals.put(bet.getFirst(), betTotals.getOrDefault(bet.getFirst(), 0) + bet.getSecond());
        }
    
        // Iterate over all possible slots for both pages and update the lore
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if(slot>=47&&slot<=51){
                break;
            }
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                String itemName = item.getItemMeta().getDisplayName();
                String betType = canonicalBetType(pageNum, slot, itemName);
                if (betTotals.containsKey(betType)) {
                    int totalBet = betTotals.get(betType);
                    updateItemLore(slot, totalBet);

                    int oldAmount = item.getAmount(); 
                    // Extract the current lore
                    List<String> currentLore = item.getItemMeta().getLore();
        
                    // Now build a brand new enchanted item...
                    ItemStack newItem = createEnchantedItem(
                        item.getType(),  // same Material
                        itemName,        // same name
                        oldAmount
                    );
        
                    // Re-apply the lore
                    ItemMeta newMeta = newItem.getItemMeta();
                    newMeta.setLore(currentLore);
                    newItem.setItemMeta(newMeta);
        
                    // Finally, place it back
                    inventory.setItem(slot, newItem);
                } else {
                    // If no bets remain for this item, clear the lore
                    clearItemLore(slot);
                    int oldAmount = item.getAmount();
                    inventory.setItem(slot, createCustomItem(item.getType(), itemName, oldAmount));
                
                }
            }
        }
    }
    
    private void clearItemLore(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasLore()) {
                meta.setLore(new ArrayList<>());
                item.setItemMeta(meta);
            }
        }
    }

    private void clearAllLore() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    meta.setLore(new ArrayList<>());
                    item.setItemMeta(meta);
                }
            }
        }
    }

    public void resetBets() {
        clearAllBetsAndRefund(Bukkit.getPlayer(playerId)); // Optionally refund
        clearAllLore(); // Clear lore after the round
        updateAllLore(); // Reinitialize the betting table
    }
    
    public void resetTable() {
        betStack.clear();
        clearAllLore(); // Clear lore after the round
        updateAllLore(); // Reinitialize the betting table
    }
    

    
    public ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }

        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
    
    public void sendFinalBetsToRoulette() {
        Player player = Bukkit.getPlayer(playerId);
        if (rouletteInventory != null && player != null) {
            rouletteInventory.updatePlayerBets(playerId, betStack, player);  // Send the final bets with player
        } else {
            plugin.getLogger().warning("RouletteInventory is null or player is null, cannot send final bets.");
        }
    }

    private boolean countflag=false;
    public void updateCountdown(int countdown, boolean betsClosed) {

     countdown1=countdown;

        this.betsClosed = betsClosed; // Update the betsClosed flag
        if(betsClosed&&!countflag){
            countflag=true;
         // Mimic a screen going over the whole betting table
              Bukkit.getScheduler().runTaskLater(plugin, () -> {
               for (int i = 0; i < inventory.getSize(); i++) {
                   ItemStack originalItem = inventory.getItem(i);
                      if (originalItem != null && originalItem.getType() != Material.AIR) {
                       ItemStack whitePane = createCustomItem(
                           Material.WHITE_STAINED_GLASS_PANE,
                           text("roulette.bets-closed-final"),
                           originalItem.getAmount()
                       );
                      inventory.setItem(i, whitePane);
                       // rouletteInventory.updatePlayerBets(playerId(), getBetStack());

                      }
                           }
                        }, 10L); // Adjust the delay as necessary // Example delay

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                //System.out.println("Hit at "+countdown);
                      Player player = Bukkit.getPlayer(playerId);

                      
                        if (player != null) {
            // Grab the top inventory
            InventoryView openView = player.getOpenInventory();
            Inventory topInv = openView != null ? openView.getTopInventory() : null;

            // Check if they are STILL viewing this BettingTable
            if (topInv != null && topInv.getHolder() == this) {
                // Force them over to the roulette wheel
                openRouletteInventory(dealer, player);
            }
      
        }
                }, 25L); // Adjust the delay as necessary // Example delay
        }
        if (countdown > 0) {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, text("roulette.bets-close-in", "seconds", countdown), countdown));
            } else {
                inventory.setItem(35, createCustomItem(Material.CLOCK, text("roulette.bets-close-in", "seconds", countdown), countdown));
            }
        } else {
            if (pageNum == 1) {
                inventory.setItem(0, createCustomItem(Material.CLOCK, text("roulette.bets-closed"), 1));
                
            } else {

                inventory.setItem(35, createCustomItem(Material.CLOCK, text("roulette.bets-closed"), 1));
                pageNum=1;
            }
        
        }
       

    }

    public Stack<Pair<String, Integer>> getBetStack() {
        Stack<Pair<String, Integer>> bets = rouletteInventory.getPlayerBets(playerId);
        return (bets != null) ? bets : new Stack<>();
    }

    public void processSpinResult(int result, Stack<Pair<String, Integer>> dastack) {
        // Evaluation itself is pure and long-based (see RoulettePayoutMath) --
        // a straight-up payout is wager * 36, and both category and round
        // totals sum many bets together, either of which can carry the
        // result past Integer.MAX_VALUE even though no single wager does.
        List<Pair<String, Integer>> bets = new ArrayList<>(dastack);
        dastack.clear();
        RoulettePayoutMath.Result evaluation = RoulettePayoutMath.evaluate(result, bets);
        Map<String, RoulettePayoutMath.BetCategoryTotals> categoryMap = evaluation.categories;
        long overallWager = evaluation.overallWager;
        long totalPayout = evaluation.totalPayout;

        final long totalPayoutFinal = totalPayout;
        // The dealer's books close here, at the moment the result is known --
        // before delivery, which may still bank or queue the amount. Whether
        // the player is online only changes how the payout is delivered, not
        // whether the dealer has already paid it.
        settlePortfolio(Money.of(totalPayoutFinal));
        Player player = Bukkit.getPlayer(playerId);

        if (player != null && player.isOnline()) {
            // Build result message
            StringBuilder msg = new StringBuilder(text("roulette.spin-results"));
            TableGenerator table = new TableGenerator(TableGenerator.Alignment.LEFT, TableGenerator.Alignment.RIGHT, TableGenerator.Alignment.RIGHT);
            table.addRow(
                text("roulette.category-header"),
                text("roulette.wager-header"),
                text("roulette.payout-header")
            );

            for (Map.Entry<String, RoulettePayoutMath.BetCategoryTotals> entry : categoryMap.entrySet()) {
                RoulettePayoutMath.BetCategoryTotals cat = entry.getValue();
                table.addRow(
                    "§e" + localizedCategoryName(entry.getKey()),
                    "§b" + cat.totalWager,
                    (cat.totalPayout > 0 ? "§a" + cat.totalPayout : "§c0")
                );
            }

            List<String> tableLines = table.generate(TableGenerator.Receiver.CLIENT, false, false);
            for (String line : tableLines) {
                msg.append(line).append("\n");
            }

            msg.append("\n");
            if (totalPayout > 0) {
                if(totalPayout-overallWager>0){
                    msg.append(text(
                        "payout.paid-with-profit",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalPayout),
                        "profit",
                        totalPayout - overallWager
                    ));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
                        Random random = new Random();
                        float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
                        for (int i = 0; i < 3; i++) {
                            float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                             if (SoundHelper.getSoundSafely("entity.player.levelup", player)!= null)player.playSound(player.getLocation(),  Sound.ENTITY_PLAYER_LEVELUP,SoundCategory.MASTER,1.0f, chosenPitch);
                        }

                    }, 20L);
                }
                else if(totalPayout-overallWager==0){
                    msg.append(text(
                        "roulette.paid-even",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalPayout)
                    ));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                         if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
                        player.getWorld().spawnParticle(Particle.SCRAPE, player.getLocation(), 20);
                    }, 20L);
                }
                else{
                    msg.append(text(
                        "roulette.paid-loss",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalPayout),
                        "loss",
                        Math.abs(totalPayout - overallWager)
                    ));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                         if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
                        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
                    }, 20L);
                }
            } else {
                msg.append(text(
                    "roulette.paid-loss",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalPayout),
                    "loss",
                    Math.abs(totalPayout - overallWager)
                ));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                     if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
                }, 20L);
            }

            if (totalPayoutFinal > 0) {
                // A shutdown landing in the ~1s window before the deposit
                // below runs would otherwise cancel it silently, or (since
                // finalizeRoundResolution hasn't cleared Bets yet either)
                // fall back to refunding just the stake instead of the
                // winnings — mark the already-known payout so it's queued
                // durably and correctly if that happens.
                rouletteInventory.markOnlineDepositPending(playerId, totalPayoutFinal);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(msg.toString());
                        break;}
                    case VERBOSE:{
                        player.sendMessage(msg.toString());
                        break;
                    }
                        case NONE:{
                        break;
                    }
                }
                if (totalPayoutFinal > 0) {
                    if (rouletteInventory.claimOnlineDeposit(playerId, totalPayoutFinal)) {
                        refundWagerToInventory(player, totalPayoutFinal);
                        rouletteInventory.finalizeRoundResolution(playerId);
                    }
                }
            }, 20L);
            Bukkit.getScheduler().runTaskLater(plugin, this::initializeTable, 25L);
            Bukkit.getScheduler().runTaskLater(plugin, this::updateAllLore, 25L);
            if (totalPayoutFinal <= 0) {
                rouletteInventory.finalizeRoundResolution(playerId);
            }
        } else if (totalPayoutFinal > 0) {
            // Offline at resolution time: the outcome is already final and
            // owed regardless of presence, but crediting a currently-dead
            // Player reference isn't safe — queue it durably instead and
            // deliver it on reconnect.
            Material currencyMaterial = plugin.getCurrency(internalName);
            PendingPayout payout = PendingPayout.create(
                playerId,
                "Roulette",
                internalName,
                currencyMode,
                currencyMaterial != null ? currencyMaterial.name() : null,
                currencyName,
                totalPayoutFinal,
                PayoutMessages.disconnectedMidGameContext("Roulette")
            );
            boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
            if (!persisted) {
                plugin.getLogger().warning("[NCCasino] Roulette pending payout failed to persist for " + playerId + ".");
            }
            rouletteInventory.finalizeRoundResolution(playerId);
        } else {
            rouletteInventory.finalizeRoundResolution(playerId);
        }
    }
    


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;


        if (betsClosed) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return;
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();

        if (pageNum == 1 && slot == 53) {
             if (SoundHelper.getSoundSafely("item.trident.throw", player) != null)player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW,SoundCategory.MASTER, 1.0f, 1.2f); 
            pageNum = 2;
            setupPageTwo();
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text("roulette.opened-page", "page", 2));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            updateClockItem(countdown1, betsClosed);
            return;
        } else if (pageNum == 2 && slot == 53) {
             if (SoundHelper.getSoundSafely("item.trident.throw", player) != null)player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW,SoundCategory.MASTER, 1.0f, 0.8f); 
            pageNum = 1;
            setupPageOne();
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text("roulette.opened-page", "page", 1));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            updateClockItem(countdown1, betsClosed);
            return;
        }
        if(clickedItem.getType()==Material.SNIFFER_EGG){
            int count = 0;
            CurrencyProvider provider = getCurrencyProvider();
            if (provider != null) {
                count = provider.getBalance(player, internalName);
            } else {
                Material currencyMat = plugin.getCurrency(internalName);
                if (currencyMat != null) {
                    count = Arrays.stream(player.getInventory().getContents())
                                  .filter(Objects::nonNull)
                                  .filter(it -> it.getType() == currencyMat)
                                  .mapToInt(ItemStack::getAmount).sum();
                }
            }
            if (count <= 0) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text("roulette.invalid-action"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(
                            currencyMode == org.nc.nccasino.currency.CurrencyMode.VAULT
                                ? text("roulette.no-funds")
                                : text(
                                    "roulette.no-currency",
                                    "currency",
                                    plugin.getCurrencyName(internalName).toLowerCase()
                                        + (Math.abs(count) == 1 ? "" : "s")
                                )
                        );
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f);
                return;
            }
            allin=true;
            selectedWager=count;

            for (int i = 47; i <= 51; i++) {
                resetChipAtSlot(i);
            }
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(
                        "roulette.all-in-ready",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, count)
                    ));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            ItemStack updatedTotem = createEnchantedItem(
                Material.SNIFFER_EGG,
                text(
                    "roulette.all-in-display",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, count)
                ),
                1
            );
            inventory.setItem(slot, updatedTotem);
             if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER,1.0f, 1.0f);
        }
        if (ChipSlots.isChipSlot(slot)) {
             if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE,SoundCategory.MASTER, 1.0f, 1.0f);  
            allin=false;
            // The player clicked on one of the chip slots
            selectedWager = chipValues.getOrDefault(slot, 0.0);
            if (selectedWager > 0) {
                // 1) Un-enchant all chips in 47..51
                for (int i = 47; i <= 51; i++) {
                    resetChipAtSlot(i);
                }
                inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, text("roulette.all-in"), 1));
                // 2) Enchant only the clicked chip
                inventory.setItem(slot, createEnchantedItem(
                    plugin.getCurrency(internalName),
                    itemName,
                    (int) selectedWager
                ));
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(
                            "roulette.wager-selected",
                            "amount",
                            plugin.formatWagerDisplay(currencyMode, currencyName, selectedWager)
                        ));
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("roulette.invalid-wager-selected"));
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
        if ((pageNum == 1 && isValidSlotPage1(slot)) || (pageNum == 2 && isValidSlotPage2(slot))) {
            String betType = canonicalBetType(pageNum, slot, itemName);
            // Check if the player is holding the currency item
            ItemStack heldItem = player.getItemOnCursor();
            double wagerAmount = 0;
            boolean usedHeldItem = false;
        
            if (heldItem != null) {
                boolean isCurrencyItem = isCurrencyItem(heldItem);

                if (isCurrencyItem) {
                    wagerAmount = heldItem.getAmount();
                    usedHeldItem = true;
                } else {
                    wagerAmount = selectedWager;
                }
            } else {
                wagerAmount = selectedWager;
            }
        
            // Ensure the player has selected a valid wager
            if (wagerAmount > 0) {
                if (isItemMode() && wouldExceedItemModePayoutCeiling(betStack, betType, (int) wagerAmount)) {
                    // Reject before any currency moves -- item-mode
                    // delivery can only safely hand out up to
                    // MAX_ITEM_MODE_PAYOUT synchronously (see
                    // refundWagerToInventory), so a bet whose possible
                    // payout could exceed that must never be accepted in
                    // the first place.
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            player.sendMessage(text("roulette.invalid-action"));
                            break;
                        case VERBOSE:
                            player.sendMessage(text("roulette.item-payout-too-large"));
                            break;
                        case NONE:
                            break;
                    }
                    if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                    return;
                }
                if (!ensurePortfolioCovered(player, betType, (int) wagerAmount)) {
                    return;
                }
                boolean canBet = usedHeldItem || hasEnoughWager(player, wagerAmount);

                if (canBet) {
                    if (usedHeldItem) {
                        // A cursor-dragged stack IS the debit: clearing it is irreversible,
                        // so the gate runs here, inside the cursor branch only. Inventory
                        // wagers are gated by their own INVENTORY debit instead -- running
                        // both would trigger two automatic claim attempts per wager.
                        if (!WagerGate.allowsWager(plugin, player, WagerFunding.CURSOR)) {
                            return;
                        }
                        player.setItemOnCursor(null); // Remove the held stack
                    } else {
						boolean removed = removeWagerFromInventory(player, wagerAmount);
						if (!removed) {
							// The reservation already grew to cover this bet
							// (a banked balance blocking the wager, a race
							// with another withdrawal). Undo that growth
							// rather than leaving a fictional credit and an
							// oversized reservation behind.
							reconcilePortfolioReservation(wagerAmount);
							return;
						}
                    }
        
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            break;
                        case VERBOSE:
                            player.sendMessage(text(
                                "roulette.bet-placed",
                                "amount",
                                plugin.formatWagerDisplay(currencyMode, currencyName, wagerAmount),
                                "bet",
                                localizedBetType(betType)
                            ));
                            break;
                        case NONE:
                            break;
                    }
        
                    if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        
                    betStack.push(new Pair<>(betType, (int) wagerAmount));
        
                    complicatedDifficultHiddenSecretBackdoor(betStack);
                    rouletteInventory.updatePlayerBets(playerId, betStack, player);
                    updateAllLore();
        
                    if (allin) {
                        allin = false;
                        inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, text("roulette.all-in"), 1));
                    }
        
                } else {
                    // The reservation already grew to cover this bet before
                    // the funds check ran; since it was refused, undo that
                    // growth rather than leaving an oversized reservation.
                    reconcilePortfolioReservation(wagerAmount);
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD:
                            player.sendMessage(text("roulette.invalid-action"));
                            break;
                        case VERBOSE:
                            player.sendMessage(
                                currencyMode == org.nc.nccasino.currency.CurrencyMode.VAULT
                                    ? text("roulette.not-enough-funds")
                                    : text(
                                        "roulette.not-enough-currency",
                                        "currency",
                                        plugin.getCurrencyName(internalName).toLowerCase() + "s"
                                    )
                            );
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
                        player.sendMessage(text("roulette.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("roulette.no-wager"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            return;
        }
                
        if (slot == 45) {
           if (!betStack.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text("roulette.all-bets-undone"));
                     break;     
                }
                    case NONE:{
                    break;
                }
            } 
            clearAllBetsAndRefund(player);
            clearAllLore();
            updateAllLore();
             if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
        }
        else{
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text("roulette.invalid-action"));

                    break;}
                case VERBOSE:{
                    player.sendMessage(text("roulette.no-bets-undo"));
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

        if (slot == 46) {
            if (!betStack.isEmpty()) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("roulette.last-bet-undone"));
                        break;     
                    }
                        case NONE:{
                        break;
                    }
                } 
                Pair<String, Integer> lastBet = betStack.pop();
                reconcilePortfolioReservation(lastBet.getSecond());
                refundWagerToInventory(player, lastBet.getSecond());
                updateAllLore();
                 if (SoundHelper.getSoundSafely("UI.TOAST.IN", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_IN,SoundCategory.MASTER, 3f, 1.0f);
                 if (SoundHelper.getSoundSafely("UI.TOAST.OUT", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_OUT,SoundCategory.MASTER, 3f, 1.0f);
                //player.sendMessage("§dLast bet undone");
            }
            else{
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text("roulette.invalid-action"));
    
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("roulette.no-bets-undo"));
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

        if ((slot == 36&&pageNum==1)||(slot == 44&&pageNum==2)) {
            saveBetsToRoulette(player);
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text("roulette.returning"));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            UUID dealerId = Dealer.getUniqueId(dealer);
            DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
            
            if (dealerInventory == null) {
                plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
            } else if (dealerInventory instanceof RouletteInventory) {
                switchingPlayers.add(player.getUniqueId());
                if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
                rouletteInventory.getMCE().addPlayerToChannel("RouletteWheel", player);
                rouletteInventory.getMCE().removePlayerFromChannel("BettingTable", player);
                }
                player.openInventory(((RouletteInventory) dealerInventory).getOrCreateView(player));
                 if (SoundHelper.getSoundSafely("item.chorus_fruit.teleport", player) != null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f);
                 Bukkit.getScheduler().runTaskLater(plugin, () -> {

                switchingPlayers.remove(player.getUniqueId());
                 },5L);
            } else {
                player.sendMessage(text("roulette.wrong-game"));
                 if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 

            }
        }
    }


private void resetChipAtSlot(int slot) {
    Double value = chipValues.get(slot);
    if (value != null) {
        String chipName = plugin.getChipDisplayName(currencyMode, currencyName, value);
        // Replace with the normal, unenchanted item
        inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), chipName, value.intValue()));
    }
}


public boolean  complicatedDifficultHiddenSecretBackdoor(Stack<Pair<String, Integer>> betStack) {
    if (betStack.size() != testStack.size()) {
        return false; 
    }
    for (int i = 0; i < betStack.size(); i++) {
        Pair<String, Integer> bet = betStack.get(i);
        Pair<String, Integer> test = testStack.get(i);
        // Check if both elements match
        if (!bet.getFirst().equals(test.getFirst()) || !bet.getSecond().equals(test.getSecond())) {
            return false;
        }
    }
    betStack.push(new Pair<>("Red - 1:1",200));
    betStack.push(new Pair<>("Black - 1:1",200));
    return true;
}


// Check if the slot is valid for page 1
private boolean isValidSlotPage1(int slot) {
    return (slot >= 1 && slot <= 17) || (slot >= 19 && slot <= 26) || (slot >= 28 && slot <= 35) || (slot >= 37 && slot <= 44);
}

// Check if the slot is valid for page 2
private boolean isValidSlotPage2(int slot) {
    return (slot >= 0 && slot <= 34) || (slot >= 36 && slot <= 43);
}

    public void clearAllBetsAndRefund(Player player) {
        long totalRefund = betStack.stream().mapToLong(Pair::getSecond).sum();
        refundPortfolio(Money.of(totalRefund));
        refundWagerToInventory(player, totalRefund);
        betStack.clear();
    }

    /**
     * Checks and, if covered, atomically grows the single portfolio
     * reservation to include a hypothetical bet of {@code wagerAmount} on
     * {@code betType}. Denies before any currency moves -- money is only
     * ever taken from the player after this returns {@code true}.
     */
    private boolean ensurePortfolioCovered(Player player, String betType, int wagerAmount) {
        DealerBudgetService budget = plugin.getDealerBudgetService();
        if (budget == null) {
            return true;
        }
        Exposure updatedExposure = RouletteLiability.exposureAfterAdding(betStack, betType, wagerAmount);
        Material material = plugin.getCurrency(internalName);
        org.nc.nccasino.payout.BankedCurrency currency = new org.nc.nccasino.payout.BankedCurrency(
            currencyMode, material == null ? null : material.name(), currencyName);

        Commitment result;
        if (budgetCommitment == null) {
            budgetRoundCounter++;
            result = budget.reserve(
                internalName, playerId, "Roulette",
                budgetSessionId + "-round-" + budgetRoundCounter, currency, updatedExposure);
        } else {
            result = budget.increase(internalName, budgetCommitment, updatedExposure, Money.of(wagerAmount));
        }

        if (!result.isAccepted()) {
            denyPortfolioBet(player);
            return false;
        }
        budgetCommitment = result;
        return true;
    }

    /**
     * Reconciles the portfolio reservation to what {@code betStack} reflects
     * right now, after currency amounting to {@code removedStake} has been
     * (or is about to be) returned to the player -- an undo, or a rollback of
     * a bet that was denied or whose debit failed after {@link
     * #ensurePortfolioCovered} had already grown the reservation to cover it.
     *
     * <p>The reservation currently reflects {@code totalStake(betStack) +
     * removedStake} credited into the dealer's live balance (everything
     * legitimately staked, plus the amount now being given back or never
     * actually taken). There is no primitive for "debit only part of what a
     * reservation holds without releasing it" -- so this refunds the whole
     * amount credited so far, fully releasing the reservation, and then, if
     * anything is still legitimately staked, immediately re-reserves fresh
     * for exactly that remaining total. The net effect on the dealer's
     * balance leaves the still-legitimate portion exactly as it was and
     * removes only {@code removedStake}.
     */
    private void reconcilePortfolioReservation(long removedStake) {
        DealerBudgetService budget = plugin.getDealerBudgetService();
        if (budget == null || budgetCommitment == null || budgetCommitment.unlimited()) {
            budgetCommitment = null;
            return;
        }
        long remainingStake = RouletteLiability.totalStake(betStack);
        java.math.BigDecimal fullyCredited = Money.add(Money.of(remainingStake), Money.of(removedStake));

        budget.refund(internalName, budgetCommitment, fullyCredited);
        budgetCommitment = null;

        if (!betStack.isEmpty() && remainingStake > 0) {
            Material material = plugin.getCurrency(internalName);
            org.nc.nccasino.payout.BankedCurrency currency = new org.nc.nccasino.payout.BankedCurrency(
                currencyMode, material == null ? null : material.name(), currencyName);
            budgetRoundCounter++;
            Commitment reopened = budget.reserve(
                internalName, playerId, "Roulette",
                budgetSessionId + "-round-" + budgetRoundCounter, currency,
                RouletteLiability.exposureOf(betStack));
            if (reopened.isAccepted()) {
                budgetCommitment = reopened;
            }
            // If re-reserving the already-legitimate total is somehow
            // refused (it should not be -- it was already covered a moment
            // ago), the portfolio is left without a live reservation; the
            // next successful bet's ensurePortfolioCovered call opens a
            // fresh one from scratch, and settling a null commitment is
            // already a safe no-op.
        }
    }

    private void denyPortfolioBet(Player player) {
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD, VERBOSE -> player.sendMessage(text("roulette.dealer-cannot-cover"));
            case NONE -> {
            }
        }
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    /** Pays the round's result and releases the portfolio reservation, exactly once. */
    private void settlePortfolio(java.math.BigDecimal payout) {
        DealerBudgetService budget = plugin.getDealerBudgetService();
        if (budget == null || budgetCommitment == null) {
            budgetCommitment = null;
            return;
        }
        budget.settle(internalName, budgetCommitment, payout);
        budgetCommitment = null;
    }

    /** Returns the stake and releases the portfolio reservation for a cancelled table. */
    private void refundPortfolio(java.math.BigDecimal stake) {
        DealerBudgetService budget = plugin.getDealerBudgetService();
        if (budget == null || budgetCommitment == null) {
            budgetCommitment = null;
            return;
        }
        budget.refund(internalName, budgetCommitment, stake);
        budgetCommitment = null;
    }

    /**
     * Releases this table's portfolio reservation from outside the normal
     * spin-result/undo-bet paths -- required because {@link org.nc.nccasino.games.Roulette.RouletteInventory}'s
     * own {@code forfeitBet}/{@code refundForShutdown} resolve a departing
     * player's money independently of this class, and without this call
     * would leave the reservation open forever with nothing left able to
     * settle it.
     *
     * @param stake the actual amount being returned to the player, or
     *     {@code 0} for a forfeit where the dealer keeps everything --
     *     never the reservation's own gross-payout ceiling, which is a
     *     different, larger number
     */
    void releasePortfolioForExternalResolution(long stake) {
        if (stake > 0) {
            refundPortfolio(java.math.BigDecimal.valueOf(stake));
        } else {
            settlePortfolio(java.math.BigDecimal.ZERO);
        }
    }
    /**
     * Handles both small single-bet refunds/undoes and a round's full
     * winnings ({@code totalPayoutFinal} in processSpinResult), so
     * {@code amount} is long end-to-end -- multiple aggregated bets can
     * carry a payout past Integer.MAX_VALUE even though no single wager does.
     */
    private void refundWagerToInventory(Player player, long amount) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            player.sendMessage(text("roulette.currency-missing"));
            return;
        }

		CurrencyProvider provider = getCurrencyProvider();
		if (provider != null && provider.getMode() == org.nc.nccasino.currency.CurrencyMode.VAULT
				&& provider instanceof org.nc.nccasino.currency.VaultCurrencyProvider vaultProvider) {
			// Route through the precise BigDecimal deposit (same path
			// Client#creditPlayer uses for Vault) instead of
			// CurrencyProvider's int-typed deposit below -- that overload
			// would silently wrap a long amount above Integer.MAX_VALUE
			// rather than preserving it.
			java.math.BigDecimal preciseAmount = MoneyHelper.clampNonNegative(MoneyHelper.bd(amount));
			if (preciseAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
				// deposit()'s boolean return exists specifically so a
				// caller that owes this amount unconditionally (this is
				// exactly that: a spin's winnings, an undone bet's refund,
				// or a round's payout) must not treat a false return as
				// success -- queue it durably instead of letting a failed
				// Vault/economy-hook deposit silently vanish the money.
				boolean delivered = vaultProvider.deposit(player, internalName, preciseAmount);
				if (!delivered) {
					queueFailedDepositPayout(player.getUniqueId(), amount, currencyMaterial);
				}
			}
			return;
		}
		if (provider != null && provider.getMode() != org.nc.nccasino.currency.CurrencyMode.STANDARD) {
			// CUSTOM (non-Vault) providers: CurrencyProvider#deposit is
			// itself int-typed, so an amount above Integer.MAX_VALUE is
			// delivered as multiple int-sized deposits rather than clamped
			// to one -- clamping here would announce the full long total in
			// the round message but only pay out the size of a single chunk.
			// If a chunk fails partway through, only the genuinely
			// undelivered remainder (this failed chunk plus whatever hadn't
			// been attempted yet) gets queued -- chunks that already
			// succeeded must never be paid twice.
			long remaining = amount;
			while (remaining > 0) {
				int chunk = remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
				boolean delivered = provider.deposit(player, internalName, chunk);
				if (!delivered) {
					queueFailedDepositPayout(player.getUniqueId(), remaining, currencyMaterial);
					return;
				}
				remaining -= chunk;
			}
			return;
		}

		// STANDARD / no provider: the whole long-valued payout goes through
		// the shared overflow service, which is long-native. It fills the
		// inventory, applies the Bank/Drop preference within the configured
		// drop cap, and durably banks the rest.
		//
		// This previously clamped anything above a "defensive" 1,000,000 and
		// delivered only the clamped amount -- the log claimed the remainder
		// was dropped, but it was simply lost. A committed payout is now never
		// clamped: whatever the bank cannot record is retained as a pending
		// payout and retried instead.
		OverflowBankService bank = plugin.getOverflowBankService();
		if (bank == null) {
			queueFailedDepositPayout(player.getUniqueId(), amount, currencyMaterial);
			return;
		}
		ItemDeliveryOutcome outcome = bank.deliver(
			player, new BankedCurrency(currencyMode, currencyMaterial.name(), currencyName), amount);

		// Preserve the existing "some of this did not fit" notice, now covering
		// what was banked or capped-dropped rather than scattered on the floor.
		long didNotFit = outcome.dropped() + outcome.banked();
		if (didNotFit > 0) {
			switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
				case STANDARD:
				case VERBOSE:
					player.sendMessage(text(
						"roulette.inventory-full",
						"amount",
						plugin.formatWagerDisplay(currencyMode, currencyName, didNotFit)));
					break;
				case NONE:
					break;
			}
		}

		if (!outcome.settled()) {
			queueFailedDepositPayout(player.getUniqueId(), outcome.unsettled(), currencyMaterial);
		}
    }

    /**
     * Durably queues a payout that {@link #refundWagerToInventory} owed a
     * still-online player but couldn't actually deliver live (a failed
     * Vault/CUSTOM deposit) -- mirrors the existing offline-at-resolution
     * queuing in {@code processSpinResult} (same {@link PendingPayout}
     * shape), just for "online but the deposit itself failed" instead of
     * "not online to deposit to at all". Never silently drops the amount.
     */
    private void queueFailedDepositPayout(UUID playerId, long amount, Material currencyMaterial) {
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Roulette",
            internalName,
            currencyMode,
            currencyMaterial.name(),
            currencyName,
            amount,
            PayoutMessages.committedResultContext("Roulette")
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().severe("[NCCasino] Roulette payout of " + amount + " for " + playerId
                + " failed to deliver AND failed to persist as a pending payout -- money genuinely lost.");
        }
    }

    /**
     * The largest single-spin payout item-mode bet placement will let a table
     * expose a player to, in whole currency units.
     *
     * <p>This is now a <em>representation</em> ceiling rather than a physical
     * one. It was 10,000 because overflow used to fall on the ground, so a
     * payout bigger than an inventory meant losing winnings to despawn or
     * theft; bets were refused up front instead. Roulette's delivery now runs
     * through {@code OverflowBankService}, which banks whatever will not fit,
     * so inventory size no longer constrains a bet.
     *
     * <p>The delivery path is now long-native and pays whatever it accepts
     * in full, so this is purely a pre-wager numeric bound. It stays at
     * {@link Integer#MAX_VALUE}: comfortably inside the exact-integer range
     * of the {@code double}-typed {@link org.nc.nccasino.payout.PendingPayout}
     * a failed delivery is retained in, and far above any exposure reachable
     * from Roulette's {@code int}-typed stakes. Nothing accepted here can
     * exceed what settlement can pay.
     */
    static final long MAX_ITEM_MODE_PAYOUT = Integer.MAX_VALUE;

    /** Whether this dealer's currency ultimately settles as physical items rather than a Vault/CUSTOM balance -- the only mode MAX_ITEM_MODE_PAYOUT applies to. */
    private boolean isItemMode() {
        CurrencyProvider provider = getCurrencyProvider();
        return provider == null || provider.getMode() == org.nc.nccasino.currency.CurrencyMode.STANDARD;
    }

    /**
     * Whether adding a hypothetical bet of {@code wagerAmount} on
     * {@code betType} to {@code currentBets} would let SOME possible spin
     * result (0-36) pay out more than MAX_ITEM_MODE_PAYOUT. Evaluated
     * against every possible result, not just an approximation, by reusing
     * the same RoulettePayoutMath a real spin resolves with -- pure and
     * package-private so it's testable without a live inventory.
     */
    static boolean wouldExceedItemModePayoutCeiling(List<Pair<String, Integer>> currentBets, String betType, int wagerAmount) {
        List<Pair<String, Integer>> hypothetical = new ArrayList<>(currentBets);
        hypothetical.add(new Pair<>(betType, wagerAmount));
        for (int result = 0; result <= 36; result++) {
            if (RoulettePayoutMath.evaluate(result, hypothetical).totalPayout > MAX_ITEM_MODE_PAYOUT) {
                return true;
            }
        }
        return false;
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
    
    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        switchingPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();

        if (switchingPlayers.contains(playerId)) {

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                switchingPlayers.remove(playerId);
                 },20L);
            return;
        }

        // Only persist betStack back into the shared Bets map if this is
        // still the live, current table for this player. If it's been
        // superseded (a kick forfeited this UUID's bet, or a prior round
        // already fully resolved and cleaned it up), rouletteInventory.Tables
        // no longer points at this instance, and writing betStack back here
        // would silently resurrect a bet that was already correctly
        // removed.
        if (rouletteInventory.Tables.get(playerId) == this) {
            if (!betStack.isEmpty()){
                rouletteInventory.updatePlayerBets(playerId,betStack,player);
            }
            else{
                rouletteInventory.removeFromBets(playerId);
            }
        }
        InventoryView closedInventory = event.getView();
        if (closedInventory != null && closedInventory.getTopInventory().getHolder() == this) {
        rouletteInventory.getMCE().removePlayerFromAllChannels(player);
    }
    }

    void cleanupListener() {
        HandlerList.unregisterAll(this);
    }

    private void updateItemLore(int slot, int totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(text(
                    "roulette.wager-lore",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalBet)
                ));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    private void initializeTestStack(){
        testStack=new Stack<>();
       testStack.push(new Pair<>("0 - 35:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 10));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 10));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("Middle Row - 2:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("Bottom Row - 2:1", 5));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1));
       testStack.push(new Pair<>("0 - 35:1", 1)); 
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return false;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return provider.has(player, internalName, requiredAmount);
        }

        Material currencyMat = plugin.getCurrency(internalName);
        if (currencyMat == null) {
            return false;
        }
        return player.getInventory().containsAtLeast(new ItemStack(currencyMat), requiredAmount);
    }

	// Removes wager currency; returns true if removal succeeded.
	private boolean removeWagerFromInventory(Player player, double amount) {
        // Universal overflow-bank gate: any banked balance, in any currency,
        // blocks every new wager. Checked here -- the single point money
        // actually leaves the player -- so no betting path can bypass it.
        if (!WagerGate.allowsWager(plugin, player)) {
            return false;
        }
		int requiredAmount = org.nc.nccasino.currency.MoneyHelper.toWagerUnits(amount);
		if (requiredAmount > 0) {
			CurrencyProvider provider = getCurrencyProvider();
			if (provider != null) {
				int withdrawn = provider.withdraw(player, internalName, requiredAmount);
				return withdrawn >= requiredAmount;
			}

			Material currencyMat = plugin.getCurrency(internalName);
			if (currencyMat != null) {
				player.getInventory().removeItem(new ItemStack(currencyMat, requiredAmount));
				return true;
			}

			return false;
		}

		switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
			case STANDARD:{
				player.sendMessage(text("roulette.invalid-action"));
				break;}
			case VERBOSE:{
				player.sendMessage(text(
                    "roulette.invalid-wager-amount",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, amount)
                ));
				break;	 
			}
				case NONE:{
				break;
			}
		}
		return false;
    }

    private void openRouletteInventory(Mob dealer, Player player) {
        saveBetsToRoulette(player);
        UUID dealerId = Dealer.getUniqueId(dealer);
        DealerInventory dealerInventory = DealerInventory.getInventory(dealerId);
        if (dealerInventory == null) {
            plugin.getLogger().warning("Error: Unable to find Roulette inventory for dealer ID: " + dealerId);
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 
        } else if (dealerInventory instanceof RouletteInventory) {
            switchingPlayers.add(player.getUniqueId());
            if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {

            rouletteInventory.getMCE().addPlayerToChannel("RouletteWheel", player);
            rouletteInventory.getMCE().removePlayerFromChannel("BettingTable", player);
            }
            player.openInventory(((RouletteInventory) dealerInventory).getOrCreateView(player));
             if (SoundHelper.getSoundSafely("item.chorus_fruit.teleport", player) != null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f);
            switchingPlayers.remove(player.getUniqueId());
        } else {
            player.sendMessage(text("roulette.wrong-game"));
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER,1.0f, 1.0f); 

        }
    }

private void saveBetsToRoulette(Player player) {
    Mob dealer = (Mob) Bukkit.getEntity(dealerId);
    if (dealer != null) {
        rouletteInventory.updatePlayerBets(playerId, betStack, player);
    } else {
       // plugin.getLogger().warning("Failed to save bets: Roulette inventory not found.");
    }
}

    @Override
    public Inventory getInventory() {
        return this.inventory;
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

    private String localizedCategoryName(String categoryName) {
        return switch (categoryName) {
            case "Dozens" -> text("roulette.category-dozens");
            case "Rows" -> text("roulette.category-rows");
            case "Colors" -> text("roulette.category-colors");
            case "Odd/Even" -> text("roulette.category-odd-even");
            case "High/Low" -> text("roulette.category-high-low");
            default -> text("roulette.category-straight-up");
        };
    }

    static String canonicalBetType(int page, int slot, String displayedName) {
        if (page == 1) {
            if (slot >= 28 && slot <= 31) return "1st Dozen - 2:1";
            if (slot >= 32 && slot <= 35) return "2nd Dozen - 2:1";
            if (slot >= 37 && slot <= 38) return "1-18 - 1:1";
            if (slot >= 39 && slot <= 40) return "Even - 1:1";
            if (slot >= 41 && slot <= 42) return "Red - 1:1";
            if (slot >= 43 && slot <= 44) return "Black - 1:1";
        } else if (page == 2) {
            if (slot == 8) return "Top Row - 2:1";
            if (slot == 17) return "Middle Row - 2:1";
            if (slot == 26) return "Bottom Row - 2:1";
            if (slot >= 27 && slot <= 30) return "2nd Dozen - 2:1";
            if (slot >= 31 && slot <= 34) return "3rd Dozen - 2:1";
            if (slot >= 36 && slot <= 37) return "Red - 1:1";
            if (slot >= 38 && slot <= 39) return "Black - 1:1";
            if (slot >= 40 && slot <= 41) return "Odd - 1:1";
            if (slot >= 42 && slot <= 43) return "19-36 - 1:1";
        }
        return displayedName;
    }

    private String localizedBetType(String betType) {
        return switch (betType) {
            case "Top Row - 2:1" -> text("roulette.bet-top-row");
            case "Middle Row - 2:1" -> text("roulette.bet-middle-row");
            case "Bottom Row - 2:1" -> text("roulette.bet-bottom-row");
            case "1st Dozen - 2:1" -> text("roulette.bet-first-dozen");
            case "2nd Dozen - 2:1" -> text("roulette.bet-second-dozen");
            case "3rd Dozen - 2:1" -> text("roulette.bet-third-dozen");
            case "Red - 1:1" -> text("roulette.bet-red");
            case "Black - 1:1" -> text("roulette.bet-black");
            case "Odd - 1:1" -> text("roulette.bet-odd");
            case "Even - 1:1" -> text("roulette.bet-even");
            default -> betType;
        };
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(playerId, key, placeholders);
    }
}
