package org.nc.nccasino.games.Baccarat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.TerminationAction;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class BaccaratClient extends Client implements TerminableSession {
    private final int[] playerCardSlots = {10,11,12};  // Left to right
    private final int[] bankerCardSlots = {16,15,14};  // Right to left
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> bankerHand = new ArrayList<>();
    private int taskId=-1;
    protected final List<BetData> previousBets = new ArrayList<>();
    protected final Map<BetOption, Deque<Double>> betStacks = new HashMap<>();
    private boolean catchingUp=false;
    protected final List<BetData> betHistory = new ArrayList<>();
     private final Map<Integer, UUID> seatMap = new HashMap<>();
    private final int[] seatSlots = {37, 38, 39, 40, 41, 42, 43};
    private boolean sessionResolved = false;
    
    protected enum SlotOption {
        EXIT,
        ALLIN,
        PLAYERPAIR,
        PLAYER,
        TIE,
        BANKER,
        BANKERPAIR,
        UNDOLASTBET,
        UNDOALLBETS,
        WAGER1,
        WAGER2,
        WAGER3,
        WAGER4,
        WAGER5
    }

    public enum BetOption {
        PLAYERPAIR,
        PLAYER,
        TIE,
        BANKER,
        BANKERPAIR,
    }
    protected final Map<Integer,SlotOption> slotMapping = new HashMap<>();
    protected final Map<Integer,BetOption> betMapping = new HashMap<>();

        public BaccaratClient(BaccaratServer server, Player player, Nccasino plugin, String internalName) {
            super(server, player, plugin.getLocalization().text(player, "baccarat.title"), plugin, internalName);
            SessionRegistry.register(player.getUniqueId(), this);
            slotMapping.put(53,SlotOption.EXIT );
            slotMapping.put(52,SlotOption.ALLIN);
            slotMapping.put(51,SlotOption.WAGER1);
            slotMapping.put(50,SlotOption.WAGER2);
            slotMapping.put(49,SlotOption.WAGER3);
            slotMapping.put(48,SlotOption.WAGER4);
            slotMapping.put(47,SlotOption.WAGER5);
            slotMapping.put(46,SlotOption.UNDOLASTBET);
            slotMapping.put(45,SlotOption.UNDOALLBETS);
            slotMapping.put(34,SlotOption.BANKERPAIR);
            slotMapping.put(25,SlotOption.BANKERPAIR);
            slotMapping.put(33,SlotOption.BANKER);
            slotMapping.put(24,SlotOption.BANKER);
            slotMapping.put(32,SlotOption.BANKER);
            slotMapping.put(23,SlotOption.BANKER);
            slotMapping.put(31,SlotOption.TIE);
            slotMapping.put(22,SlotOption.TIE);
            slotMapping.put(30,SlotOption.PLAYER);
            slotMapping.put(21,SlotOption.PLAYER);
            slotMapping.put(29,SlotOption.PLAYER);
            slotMapping.put(20,SlotOption.PLAYER);
            slotMapping.put(28,SlotOption.PLAYERPAIR);
            slotMapping.put(19,SlotOption.PLAYERPAIR);
            betMapping.put(34,BetOption.BANKERPAIR);
            betMapping.put(25,BetOption.BANKERPAIR);
            betMapping.put(33,BetOption.BANKER);
            betMapping.put(24,BetOption.BANKER);
            betMapping.put(32,BetOption.BANKER);
            betMapping.put(23,BetOption.BANKER);
            betMapping.put(31,BetOption.TIE);
            betMapping.put(22,BetOption.TIE);
            betMapping.put(30,BetOption.PLAYER);
            betMapping.put(21,BetOption.PLAYER);
            betMapping.put(29,BetOption.PLAYER);
            betMapping.put(20,BetOption.PLAYER);
            betMapping.put(28,BetOption.PLAYERPAIR);
            betMapping.put(19,BetOption.PLAYERPAIR);
          
    }

    @Override
    public void initializeUI(boolean switchRebet, boolean betSlip,boolean deafultRebet) {
        super.initializeUI(switchRebet, betSlip,deafultRebet);
        
        Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String rebetName = text(rebetEnabled ? "baccarat.rebet-on" : "baccarat.rebet-off");
        inventory.setItem(53, createCustomItem(rebetMat, rebetName, 1));
        // Table layout
        int[] tableSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9,10,11,12,13,14,15,16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,28,29,30,31,32,33,34, 35, 36,37,38,39,40,41,42,43, 44};
        for (int slot : tableSlots) {
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                meta.setLore(null); // Ensure no lore
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
        
        // Dealer slot
        inventory.setItem(4, createDealerSkull(text("baccarat.dealer")));
        
        // Player bet slots
        int[] playerSlots = {1,2,3};
        for (int slot : playerSlots) {
            inventory.setItem(slot, createCustomItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, text("baccarat.player"), 1));
        }
        
        // Banker bet slots
        int[] bankerSlots = {5,6,7};
        for (int slot : bankerSlots) {
            inventory.setItem(slot, createCustomItem(Material.PINK_STAINED_GLASS_PANE, text("baccarat.banker"), 1));
        }
        
        // Player pair bet slots
        int[] playerPairSlots = {19,28};
        for (int slot : playerPairSlots) {
            inventory.setItem(slot, createCustomItem(Material.CYAN_STAINED_GLASS_PANE, text("baccarat.player-pair-odds"), 1));
        }
        
        // Player bet additional slots
        int[] playerAdditionalSlots = {20,29, 21,30};
        for (int slot : playerAdditionalSlots) {
            inventory.setItem(slot, createCustomItem(Material.BLUE_STAINED_GLASS_PANE, text("baccarat.player-win-odds"), 1));
        }
        
        // Tie bet slots
        int[] tieSlots = {22,31};
        for (int slot : tieSlots) {
            inventory.setItem(slot, createCustomItem(Material.BROWN_STAINED_GLASS_PANE, text("baccarat.tie-odds"), 1));
        }
        
        // Banker bet additional slots
        int[] bankerAdditionalSlots = {23,32, 24,33};
        for (int slot : bankerAdditionalSlots) {
            inventory.setItem(slot, createCustomItem(Material.PURPLE_STAINED_GLASS_PANE, text("baccarat.banker-win-odds"), 1));
        }
        
        // Banker pair bet slots
        int[] bankerPairSlots = {25,34};
        for (int slot : bankerPairSlots) {
            inventory.setItem(slot, createCustomItem(Material.MAGENTA_STAINED_GLASS_PANE, text("baccarat.banker-pair-odds"), 1));
        }
        setupSeats();

        player.updateInventory();
        sendUpdateToServer("INVENTORY_OPEN", null);
    }

    private void setupSeats() {
        for (int slot : seatSlots) {
            inventory.setItem(slot, createSeatItem(slot,player.getUniqueId()));
        }
    }
    
    private ItemStack createSeatItem(int slot,UUID viewerId) {
        if (seatMap.containsKey(slot)) {
            UUID playerId = seatMap.get(slot);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                if (playerId.equals(viewerId)) {
                    return createPlayerHead(player.getUniqueId(), player.getName(), text("baccarat.click-leave-chair"));
                } else {
                    return createPlayerHead(player.getUniqueId(), player.getName());
                }
            }
        }
        boolean viewerIsSeated = seatMap.containsValue(viewerId);
        String displayName = text(viewerIsSeated ? "baccarat.open-seat" : "baccarat.click-sit");
        return createCustomItem(Material.OAK_STAIRS, displayName, 1);
    }

    private ItemStack createDealerSkull(String name) {
        ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = skull.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onServerUpdate(String eventType, Object data) {
        switch (eventType) {
            case "UPDATE_BET_DISPLAY":
            if (data instanceof BetDisplayData betData) {
                betStacks.putIfAbsent(betData.betType, new ArrayDeque<>());
        
                // Update player's own bet
                betStacks.get(betData.betType).clear();
                betStacks.get(betData.betType).push(betData.playerTotal);
        
                // Update total bets correctly
                updateBetDisplay(betData.betType, betData.totalBets);
            }
            break;
            case "RESTORE_BETS":
            if (data instanceof List) {
                restoreBetHistory((List<BetData>) data);
            }
            break;

            case "RESET_BETS":
                betStacks.clear();
                refreshAllBetDisplays();
                resetBetSlots(); // Cal
                break;
            case "UPDATE_TIMER":
                if (data instanceof Integer) {
                    updateTimerUI((Integer)data);
                }
                break;
            case "DEAL_CARDS":
            if (data instanceof List && ((List<?>) data).size() == 1) {
                Card card = ((List<Card>) data).get(0);
                // Alternate assignment based on current hand sizes
                if (playerHand.size() == bankerHand.size()) {
                    playerHand.add(card);
                } else {
                    bankerHand.add(card);
                }
        
                displayCards();
                if (!catchingUp) {
                    if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
                }
            }
            break;
            case "PLAYER_DRAW":
                if (data instanceof Card) {
                    playerHand.add((Card) data);
                    displayCards();
                    if (!catchingUp) {
                        if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
                    }
                }
                break;
            case "BANKER_DRAW":
                if (data instanceof Card) {
                    bankerHand.add((Card) data);
                    displayCards();
                    if (!catchingUp) {
                        if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
                    }
                }
                break;
            case "UPDATE_HAND_TOTALS":
                if (data instanceof int[]) {
                    int[] totals = (int[]) data;
                    updateHandTotalDisplay(totals[0], totals[1]);
                }
                break;
            case "RESULT":
                if (data instanceof String) {
                    showGameResult((String) data);
                }
                break; 
            case "CLEAR_CARDS":
                playerHand.clear();
                bankerHand.clear();
                displayCards(); // Ensure UI updates properly
                updateHandTotalDisplay(-1, -1); // Reset hand total UI
                if(taskId!=-1)Bukkit.getScheduler().cancelTask(taskId);
                break;
            case "CATCHUP_START":
                catchingUp = true;
                break;
            case "CATCHUP_COMPLETE":
                catchingUp = false;
                break;
            case "UPDATE_SEATS":
                if (data instanceof Map){
                    seatMap.clear();
                    seatMap.putAll((Map<Integer, UUID>) data);
                    setupSeats();
                    player.updateInventory(); 
                }
                break;
        }
    }

    private void resetBetSlots() {
        for (int slot : betMapping.keySet()) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setLore(null); // Remove bet amount display
                    item.setItemMeta(meta);
                }
            }
        }
        player.updateInventory(); // Ensure changes are reflected in UI
    }

    
    private void updateHandTotalDisplay(int playerTotal, int bankerTotal) {
        for (int i=1;i<=3;i++) {
            if(playerTotal==-1){
            inventory.setItem(i, createCustomItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, text("baccarat.player"), 1));
            }
            else{
            updateSlotWithTotal(i, text("baccarat.player"), playerTotal, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

            }
        }
        for (int i=5;i<=7;i++) {
            if(bankerTotal==-1){
            inventory.setItem(i, createCustomItem(Material.PINK_STAINED_GLASS_PANE, text("baccarat.banker"), 1));
        }
        else{
            updateSlotWithTotal(i, text("baccarat.banker"), bankerTotal, Material.PINK_STAINED_GLASS_PANE);}
        }
        player.updateInventory();
    }

    private void updateSlotWithTotal(int slot, String name, int total, Material color) {
        ItemStack totalItem = new ItemStack(color);
        ItemMeta meta = totalItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(text("baccarat.current-total", "total", total)));
            totalItem.setItemMeta(meta);
        }
        inventory.setItem(slot, totalItem);
    }

    private void displayCards() {
        if(playerHand.isEmpty()){
            int[] tableSlots = {10,11,12};
            for (int slot : tableSlots) {
                ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                    meta.setLore(null); // Ensure no lore
                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
            
        }
        if(bankerHand.isEmpty()){
            int[] tableSlots = {14,15,16};
            for (int slot : tableSlots) {
                ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                    meta.setLore(null); // Ensure no lore
                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
            
        }
        for (int i = 0; i < Math.min(playerHand.size(), playerCardSlots.length); i++) {
            setCardItem(playerCardSlots[i], playerHand.get(i));
        }
        for (int i = 0; i < Math.min(bankerHand.size(), bankerCardSlots.length); i++) {
            setCardItem(bankerCardSlots[i], bankerHand.get(i));
        }
    
        player.updateInventory();
    }

    private void showGameResult(String result) {
        if (result.equals("PLAYER_WINS")) {
            animateWinningHand(new int[]{1,2,3}, Material.LIGHT_BLUE_STAINED_GLASS_PANE, text("baccarat.player-wins"));
        } else if (result.equals("BANKER_WINS")) {
            animateWinningHand(new int[]{7,6,5}, Material.PINK_STAINED_GLASS_PANE, text("baccarat.banker-wins"));
        } else {
            applyStaticEnchantment(new int[]{1,2,3,5,6,7}, Material.YELLOW_STAINED_GLASS_PANE, text("baccarat.tie-result"));
        }
    
        player.updateInventory();
    }

    private void animateWinningHand(int[] slots, Material material, String message) {
        int[] index = {0}; // Track which slot to enchant
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            // Reset all slots to normal first
            for (int slot : slots) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(message);
                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
    
            // Apply enchantment to the current slot
            int currentSlot = slots[index[0]];
            ItemStack enchantedItem = new ItemStack(material);
            ItemMeta enchantedMeta = enchantedItem.getItemMeta();
            if (enchantedMeta != null) {
                enchantedMeta.setDisplayName(message);
                enchantedMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                enchantedMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                enchantedItem.setItemMeta(enchantedMeta);
            }
            inventory.setItem(currentSlot, enchantedItem);
    
            player.updateInventory();
            index[0] = (index[0] + 1) % slots.length; // Move to the next slot
    
        }, 0L, 3L); // Runs every 10 ticks (0.5 seconds)
    
        // Stop animation after 5 seconds
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if(taskId!=-1)Bukkit.getScheduler().cancelTask(taskId);
           //applyStaticEnchantment(slots, material, message);
        }, 100L);
    }
    
    private void updateTimerUI(int seconds) {
        if (seconds <= 0) {
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§r"); // Resets to vanilla name (no display)
                meta.setLore(null); // Ensure no lore
                item.setItemMeta(meta);
            }
            inventory.setItem(13, item);
            return;
        }
    
        ItemStack timerItem = new ItemStack(Material.CLOCK, Math.min(seconds, 64));
        ItemMeta meta = timerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("baccarat.time-left", "seconds", seconds));
            timerItem.setItemMeta(meta);
        }
    
        inventory.setItem(13, timerItem);
        player.updateInventory();
    }


        @Override
    protected boolean isBetSlot(int slot) {
        if (slot >= 45 && slot <= 53) return true;  // old logic
        if (slot>=19 && slot <= 25 ) return true;
        if (slot>=28 && slot <= 34 ) return true;
        return false;
    }

        @Override
    protected void handleBet(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        // Handle Wager & All In Selection
        if (slot >= 47 && slot <= 52) {
            updateSelectedWager(slot);
            return;
        }
        if (((BaccaratServer) server).getGameState() != Server.GameState.WAITING) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("baccarat.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("baccarat.bets-closed"));
                    break;
                case NONE:
                    break;
            }
            return;
        }

        if (slot == 53) {
            rebetEnabled = !rebetEnabled;

            // Update rebet toggle UI
            Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
            String rebetName = text(rebetEnabled ? "baccarat.rebet-on" : "baccarat.rebet-off");
            inventory.setItem(53, createCustomItem(rebetMat, rebetName, 1));
        
            // Play rebet toggle sound
            if (SoundHelper.getSoundSafely("ui.button.click", player) != null)
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        if (!seatMap.containsValue(player.getUniqueId())) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("baccarat.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("baccarat.must-be-seated"));
                    break;
                case NONE:
                    break;
            }
            return;
        }
        
        // Undo All Bets
        if (slot == 45) {
            if (betStacks.isEmpty()) {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("baccarat.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("baccarat.no-bets-to-undo"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                return;
            }
            double totalRefund = betStacks.values().stream().flatMap(Collection::stream).mapToDouble(Double::doubleValue).sum();
            betHistory.clear();
            betStacks.clear();
            creditPlayer(player, totalRefund);
            sendUpdateToServer("UNDO_ALL_BETS", null);
            refreshAllBetDisplays();
            if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE:
                    player.sendMessage(text("baccarat.all-bets-undone"));
                    break;
                case NONE:
                case STANDARD:
                    break;
            }
        }

       // Undo Last Bet
        if (slot == 46) {
            if (betHistory.isEmpty()) {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("baccarat.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("baccarat.no-bets-to-undo"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                return;
            }

            BetData lastBet = betHistory.remove(betHistory.size() - 1);
            BetOption lastBetType = lastBet.betType;
            double lastBetAmount = lastBet.amount;

            if (!betStacks.containsKey(lastBetType) || betStacks.get(lastBetType).isEmpty()) {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("baccarat.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("baccarat.no-bets-to-undo"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                return;
            }
           
            Deque<Double> stack = betStacks.get(lastBetType);
            if (!stack.isEmpty()) {
                double updatedAmount = stack.peek() - lastBetAmount;
                if (updatedAmount > 0) {
                    stack.pop();  // Remove old value
                    stack.push(updatedAmount);  // Push back updated value
                } else {
                    stack.pop();  // If it reaches 0, just remove
                }
            }
        
            // If stack is empty, remove category
            if (stack.isEmpty()) {
                betStacks.remove(lastBetType);
            }

            creditPlayer(player, lastBetAmount);
            sendUpdateToServer("UNDO_BET", new BetData(lastBetType, lastBetAmount));
            updateBetDisplay(lastBetType,((BaccaratServer) server).getTotalBetForType(lastBetType));
            if (SoundHelper.getSoundSafely("ui.toast.in", player) != null)
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
            if (SoundHelper.getSoundSafely("ui.toast.out", player) != null)
                player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);
        
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE:
                    player.sendMessage(text("baccarat.last-bet-undone"));
                    break;
                case NONE:
                case STANDARD:
                    break;
            }
        }


     

        if (!betMapping.containsKey(slot)) return; // Not a bet slot
        ItemStack cursorItem = event.getCursor();
        boolean isDraggingCurrency = isCurrencyItem(cursorItem);
    
        // Get bet amount
        double betAmount;
        if (isDraggingCurrency && cursorItem !=null) {
            betAmount = cursorItem.getAmount(); // Use entire stack amount
        } else {
            betAmount = selectedWager;
        }



        if (betAmount <= 0) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("baccarat.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("betting.select-wager"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        if (!isDraggingCurrency) {
            if (!hasEnoughWager(player, betAmount)) {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("baccarat.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("baccarat.insufficient-currency"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                return;
            }
        }
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        BetOption betType = betMapping.get(slot);


        betHistory.add(new BetData(betType, betAmount)); // Maintain exact bet order
        betStacks.putIfAbsent(betType, new ArrayDeque<>());
        betStacks.get(betType).push(betAmount);



        if (isDraggingCurrency) {
        player.setItemOnCursor(null); // Remove held stack
        } else {
        removeWagerFromInventory(player, betAmount);
         }

        // Send bet to server
        sendUpdateToServer("PLACE_BET", new BetData(betType, betAmount));
        
        // Update all slots of the same bet type
        updateBetDisplay(betType,((BaccaratServer) server).getTotalBetForType(betType));
    }
    
    private void refreshAllBetDisplays() {
        for (BetOption betType : betStacks.keySet()) {
            updateBetDisplay(betType,((BaccaratServer) server).getTotalBetForType(betType));
        }
    }

    private void updateBetDisplay(BetOption betType, double totalBet) {
        double playerTotal = betStacks.getOrDefault(betType, new ArrayDeque<>())
                                      .stream().mapToDouble(Double::doubleValue).sum();
        int numBettors = ((BaccaratServer) server).getBettorCountForType(betType);
    
        String playerBetText = playerTotal > 0
            ? text("baccarat.your-bet", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, playerTotal))
            : null;
        String totalBetText = totalBet > 0 ? (numBettors > 1 ? "👥 " : "👤 ") + numBettors + " - " + plugin.formatWagerDisplay(currencyMode, currencyName, totalBet) : null;
    
        for (int slot : betMapping.keySet()) {
            if (betMapping.get(slot) == betType) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> loreLines = new ArrayList<>();
                        if (playerBetText != null) {
                            loreLines.add(playerBetText);
                        }
                        if (totalBetText != null) {
                            loreLines.add(totalBetText);
                        }
                        meta.setLore(loreLines.isEmpty() ? null : loreLines);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
        player.updateInventory();
    }
    

    public void reapplyPreviousBets() {
        if (!rebetEnabled) return;
        
        double totalRequired = previousBets.stream()
            .mapToDouble(bet -> bet.amount)
            .sum();
    
        if (!hasEnoughWager(player, totalRequired)) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("baccarat.rebet-disabled"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("baccarat.rebet-insufficient"));
                    break;
                case NONE:
                    break;
            }
            rebetEnabled = false;
            previousBets.clear();
            return;
        }
    
        betHistory.clear(); // Clear before reapplying

        for (BetData bet : previousBets) {
            betHistory.add(bet); // Maintain order
            betStacks.putIfAbsent(bet.betType, new ArrayDeque<>());
            betStacks.get(bet.betType).addLast(bet.amount); // Add LAST to preserve order

            removeWagerFromInventory(player, bet.amount);
            sendUpdateToServer("PLACE_BET", bet);
        }
        }

        @Override
        protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
            if (inventory.getItem(slot) != null && inventory.getItem(slot).getType() == Material.SKELETON_SKULL) {
                if (SoundHelper.getSoundSafely("entity.skeleton.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_HURT, SoundCategory.MASTER, 1.0f, 1.0f); 
            }
            if (Arrays.stream(seatSlots).anyMatch(s -> s == slot)) {
                sendUpdateToServer("SEAT_CLICK", slot);
            }
        }

    @Override
    public void handleClientInventoryClose() {
        // Route through the same idempotent path used for quit/kick rather
        // than resolving directly here — whichever of this or the quit
        // event fires first "wins" and the other becomes a safe no-op, and
        // consumeQuitReason still correctly reports KICKED here even if
        // this fires first, since the kick is marked as soon as
        // PlayerKickEvent itself fires.
        UUID playerId = player.getUniqueId();
        ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
        SessionRegistry.terminatePlayerSession(playerId, reason);
    }

    /**
     * Authoritative disconnect/kick resolution, reached via SessionRegistry
     * regardless of whether this fires from PlayerQuitEvent or from this
     * client's own InventoryCloseEvent.
     */
    @Override
    public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
        if (sessionResolved) {
            return; // already resolved through another path
        }
        sessionResolved = true;

        BaccaratServer baccaratServer = (BaccaratServer) server;
        sendUpdateToServer("INVENTORY_CLOSE", null);

        boolean waitingForRound = baccaratServer.getGameState() == Server.GameState.WAITING;
        TerminationAction action = GameTerminationPolicy.baccarat(reason, waitingForRound);
        if (action == TerminationAction.FORFEIT) {
            // Forfeit unconditionally regardless of phase — no refund, no
            // pending payout, seat and bet both stripped outright.
            baccaratServer.forfeitPlayer(terminatedPlayerId);
        } else if (action == TerminationAction.REFUND && waitingForRound) {
            // Pregame: nothing has been risked into an active hand yet,
            // so this is a plain refund.
            sendUpdateToServer("PLAYER_LEFT_BEFORE_START", null);
        } else if (action == TerminationAction.REFUND) {
            // Mid-hand, but the scheduled deal/draw/evaluate chain that
            // would normally carry this bet to a real outcome is about to
            // be cancelled along with everything else — refund instead of
            // trying to let it ride through a hand that will never finish.
            baccaratServer.refundForShutdown(terminatedPlayerId);
            baccaratServer.releaseSeatForDisconnect(terminatedPlayerId);
        } else {
            // Mid-hand: the bet already rode into the hand and resolves
            // normally by UUID at payout time (delivered as a pending
            // payout if still offline then) — just free the seat.
            baccaratServer.releaseSeatForDisconnect(terminatedPlayerId);
        }

        server.removeClient(terminatedPlayerId);
    }

    public void saveCurrentBets() {
        previousBets.clear();
        previousBets.addAll(betHistory); // Copy exact order
    }
    
    public void restoreBetHistory(List<BetData> storedHistory) {
        betStacks.clear();
        betHistory.clear();
    
        for (BetData bet : storedHistory) {
            BetOption betType = bet.betType;
            double amount = bet.amount;
    
            betStacks.putIfAbsent(betType, new ArrayDeque<>());
            betStacks.get(betType).push(amount);
            betHistory.add(bet); // Maintain exact order
        }
    
        // Update UI with correct total bets
        for (BetOption betType : betStacks.keySet()) {
            updateBetDisplay(betType, ((BaccaratServer) server).getTotalBetForType(betType));
        }
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
    
    
    
    
    
}
