package org.nc.nccasino.entities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Client extends DealerInventory {

    protected final Server server;
    protected final Player player;
    protected final Nccasino plugin;
    protected final String internalName;

    protected final Deque<Double> betStack = new ArrayDeque<>();
    protected boolean rebetEnabled = false;
    protected double selectedWager = 0.0;
    protected final Map<String, Double> chipValues = new LinkedHashMap<>();
    
    public Client(Server server, Player player, int size, String title,
                  Nccasino plugin, String internalName)
    {
        super(player.getUniqueId(), size, title);
        this.server = server;
        this.player = player;
        this.plugin = plugin;
        this.internalName = internalName;

        // Load chip values from config so we can replicate Mines logic
        loadChipValuesFromConfig();

        // Register event listener & set up the 'betting row' 
        registerListener();
        setupBettingRow(false);
    }
    public Player getPlayer(){
        return player;
    }
    public void initializeUI(boolean RebetSwitch) {
            setupBettingRow(RebetSwitch);
    }

    private void loadChipValuesFromConfig() {
        Map<String, Double> temp = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String chipName = plugin.getChipName(internalName, i);
            double chipValue = plugin.getChipValue(internalName, i);
            if (chipValue > 0) {
                temp.put(chipName, chipValue);
            }
        }
        // Sort ascending by value 
        temp.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .forEachOrdered(e -> chipValues.put(e.getKey(), e.getValue()));
    }


    private void setupBettingRow(Boolean switchBoolean) {
        // 1) Build the chips (slots 47..51)
        int chipSlot = 47;
        for (Map.Entry<String, Double> entry : chipValues.entrySet()) {
            String chipName = entry.getKey();
            double chipVal = entry.getValue();
            inventory.setItem(chipSlot, createCustomItem(getCurrencyMaterial(), chipName, (int) chipVal));
            chipSlot++;
        }
        if(!switchBoolean){
        // 2) Paper in slot 53: "Click here to place bet"
        inventory.setItem(53, createCustomItem(Material.PAPER, "Click here to place bet", 1));}
        int slot=switchBoolean?53:43;
        // 3) Rebet: slot 43
        Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String rebetName = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
        inventory.setItem(slot, createCustomItem(rebetMat, rebetName, 1));

        // 4) Undo All: slot 45
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));

        // 5) Undo Last: slot 46
        inventory.setItem(46, createCustomItem(Material.WIND_CHARGE, "Undo Last Bet", 1));

        // 6) All In: slot 52
        inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, "All In", 1));

        // If we had any existing bets, update the lore on slot 53
        double curr = betStack.stream().mapToDouble(Double::doubleValue).sum();
        if (curr > 0) {
            updateBetLore(53, curr);
        }
    }

    public void onServerStateChange(Server.SessionState oldState, Server.SessionState newState) {
        // Do nothing by default
    }


    @Override
    public void handleClick(int slot, Player clicker, InventoryClickEvent event) {
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;

        // If it's one of the bet slots (chips, rebet, etc.), handle it:
        if (isBetSlot(slot)) {
            handleBet(slot, clicker, event);
        } else {
            // Otherwise, let game-specific logic handle it
            handleClientSpecificClick(slot, clicker, event);
        }
    }

    protected boolean isBetSlot(int slot) {
        return slot >= 43 && slot <= 53; // This covers rebet(43), chips(47-51), all in(52), place bet(53), etc.
    }

    protected void handleBet(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        // Rebet
        if (slot == 43) {
            rebetEnabled = !rebetEnabled;
            updateRebetToggle(slot);
            return;
        }

        // Undo all 
        if (slot == 45) {
            undoAllBets();
            updateBetLore(53, 0);
            return;
        }

        // Undo last
        if (slot == 46) {
            undoLastBet();
            double currBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
            updateBetLore(53, currBet);
            return;
        }

        // All In
        if (slot == 52) {
            placeAllInBet();
            double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
            updateBetLore(53, totalBet);
            return;
        }

        // Chips: 47..51
        if (slot >= 47 && slot <= 51) {
            playDefaultSound(player);
            // The user clicked on a chip
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String itemName = clicked.getItemMeta().getDisplayName();
            double val = chipValues.getOrDefault(itemName, 0.0);
            // Mark it as selected
            selectedWager = val;

            // Re-enchant the clicked chip, revert others
            for (int s = 47; s <= 51; s++) {
                ItemStack chip = inventory.getItem(s);
                if (chip != null && chip.hasItemMeta()) {
                    String cName = chip.getItemMeta().getDisplayName();
                    // If it's the clicked chip, enchant it
                    if (cName.equals(itemName)) {
                        inventory.setItem(s,
                            createEnchantedItem(getCurrencyMaterial(), cName, (int) val));
                    } else if (chipValues.containsKey(cName)) {
                        // reset 
                        inventory.clear(s);
                        inventory.setItem(s,
                           createCustomItem(getCurrencyMaterial(), cName, (int)chipValues.get(cName).doubleValue()));
                    }
                }
            }
            return;
        }

        // slot == 53 => "Click here to place bet"
        if (slot == 53) {
            handleBetPlacement();
            double total = betStack.stream().mapToDouble(Double::doubleValue).sum();
            updateBetLore(53, total);
        }
    }

    protected abstract void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event);


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

        if (wagerAmount <= 0) {
            // Possibly send a message to the user "Invalid action" or "Select a wager first."
            return;
        }

        // Check if user has enough currency
        if (!hasEnoughCurrency(player, (int)wagerAmount) && !usedHeldItem) {
            // Possibly message "Not enough currency"
            return;
        }

        // Remove currency from inventory or from cursor
        if (usedHeldItem) {
            player.setItemOnCursor(null);
        } else {
            removeCurrencyFromInventory(player, (int)wagerAmount);
        }

        betStack.push(wagerAmount);
    }

    protected void placeAllInBet() {
        Material cMat = getCurrencyMaterial();
        int count = Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(it -> it.getType() == cMat)
            .mapToInt(ItemStack::getAmount).sum();

        if (count <= 0) {
            // Possibly message "No currency"
            return;
        }

        // Remove them from inventory
        removeCurrencyFromInventory(player, count);

        // push onto bet stack
        betStack.push((double) count);
    }

    protected void undoLastBet() {
        if (!betStack.isEmpty()) {
            double last = betStack.pop();
            refundCurrency(player, (int) last);
        }
    }

    protected void undoAllBets() {
        while (!betStack.isEmpty()) {
            double b = betStack.pop();
            refundCurrency(player, (int) b);
        }
    }

    protected void toggleRebet() {
        rebetEnabled = !rebetEnabled;
    }

    protected void updateRebetToggle(int index) {
        Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String rebetName = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
        inventory.setItem(index, createCustomItem(rebetMat, rebetName, 1));
        player.updateInventory();
    }

    protected static String formatCurrencyName(String entityName) {
        return Arrays.stream(entityName.toLowerCase().replace("_", " ").split(" "))
                     .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                     .collect(Collectors.joining(" "));
    }

    
    protected void setCardItem(int slot, Card card) {
        Material material = (card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS) 
                            ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack cardItem = new ItemStack(material, getCardValueStackSize(card));
        ItemMeta meta = cardItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(card.getRank() + " of " + card.getSuit());
            cardItem.setItemMeta(meta);
        }
        inventory.setItem(slot, cardItem);
    }

    protected void applyStaticEnchantment(int[] slots, Material material, String message) {
        for (int slot : slots) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(message);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
        player.updateInventory();
    }


    protected boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(plugin.getCurrency(internalName)), requiredAmount);
    }

    
    protected void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = (int) Math.ceil(amount);
        player.getInventory().removeItem(new ItemStack(plugin.getCurrency(internalName), requiredAmount));
    }

    
    protected void creditPlayer(Player player, double amount) {
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


    private int getCardValueStackSize(Card card) {
        Rank rank = card.getRank();
        return switch (rank) {
            case ACE -> 1;
            case TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE -> rank.ordinal() + 2;
            default -> 10;
        };
    }
    protected abstract void reapplyPreviousBets();

    protected void updateSelectedWager(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;

        boolean isAllIn = (slot == 52);
        selectedWager = isAllIn ? getTotalCurrency(player) : chipValues.getOrDefault(clicked.getItemMeta().getDisplayName(), 0.0);

        if (isAllIn) {
            if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        } else {
            if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null) {
                player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }

        // Loop through wager slots (chips & All In) to update enchantment
        for (int s = 47; s <= 52; s++) {
            ItemStack chip = inventory.getItem(s);
            if (chip != null && chip.hasItemMeta()) {
                ItemMeta meta = chip.getItemMeta();
                String chipName = meta.getDisplayName();
                double chipValue = chipValues.getOrDefault(chipName, 0.0);

                if (s == slot) {
                    inventory.setItem(s, createEnchantedItem(
                        s == 52 ? Material.SNIFFER_EGG : getCurrencyMaterial(),
                        isAllIn ? "All In (" + (int) selectedWager + ")" : chipName,
                        s == 52 ? 1 : (int) chipValue
                    ));
                } else {
                    inventory.setItem(s, createCustomItem(
                        s == 52 ? Material.SNIFFER_EGG : getCurrencyMaterial(),
                        s == 52 ? "All In" : chipName,
                        s == 52 ? 1 : (int) chipValue
                    ));
                }
            }
        }

        // **Reset Sniffer Egg after "All In" is placed**
        if (isAllIn) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                inventory.setItem(52, createCustomItem(Material.SNIFFER_EGG, "All In", 1));
                player.updateInventory();
            }, 20L); // Delay to ensure reset after UI update
        }
    }



    protected void updateBetLore(int slot, double totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (totalBet > 0) {
                    List<String> lore = new ArrayList<>();
                    lore.add("Wager: " + (int)totalBet + " " + plugin.getCurrencyName(internalName).toLowerCase());
                    meta.setLore(lore);
                } else {
                    meta.setLore(Collections.emptyList());
                }
                item.setItemMeta(meta);
            }
        }
    }

    protected boolean hasEnoughCurrency(Player player, int amount) {
        if (amount <= 0) return true;
        return getTotalCurrency(player) >= amount;
    }

    protected int getTotalCurrency(Player player) {
        return Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(it -> it.getType() == getCurrencyMaterial())
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    protected void removeCurrencyFromInventory(Player player, int amount) {
        if (amount <= 0) return;
        player.getInventory().removeItem(new ItemStack(getCurrencyMaterial(), amount));
    }

    public void refundCurrency(Player player, int amount) {
        if (amount <= 0) return;
        player.getInventory().addItem(new ItemStack(getCurrencyMaterial(), amount));
    }

    protected Material getCurrencyMaterial(){
        return plugin.getCurrency(internalName);
    }

    protected void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    protected void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == this) {
            if (event.getPlayer() instanceof Player) {
                Player p = (Player) event.getPlayer();
                if (p.getUniqueId().equals(player.getUniqueId())) {
                    handleClientInventoryClose();
                }
            }
        }
    }

    protected void handleClientInventoryClose() {
        server.removeClient(player.getUniqueId());
    }

    public void cleanup() {
        unregisterListener();
    }

    public UUID getOwnerId() {
        return player.getUniqueId();
    }

    protected void sendUpdateToServer(String eventType, Object data) {
        if (server != null) {
            Bukkit.getLogger().info("[Client] Sending update to server: " + eventType + " | Data: " + data);
            server.onClientUpdate(this, eventType, data);
        }
    }

    public void onServerUpdate(String eventType, Object data) {
        Bukkit.getLogger().info("[Client] Received server update: " + eventType + " | Data: " + data);
    }
}
