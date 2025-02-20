package org.nc.nccasino.entities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.*;

/**
 * Base Client class for ANY game. 
 * Now contains full Mines-style betting logic:
 *  - Chips in slots 47..51
 *  - Paper "Click here to place bet" in slot 53
 *  - Rebet toggle in slot 43
 *  - Undo All (45), Undo Last (46), All In (52)
 *  - Lore updates ("Wager: ...")
 *  - rebetEnabled logic
 *  - No code from onServerUpdate or sendUpdateToServer is removed.
 */
public abstract class Client extends DealerInventory {

    protected final Server server;
    protected final Player player;
    protected final Nccasino plugin;
    protected final String internalName;

    /**
     * The betting stack. Each placed bet pushes onto the stack; undo operations pop.
     */
    protected final Deque<Double> betStack = new ArrayDeque<>();

    /**
     * If the user re-bets, we replicate their previous wager after the game ends.
     */
    protected boolean rebetEnabled = false;

    /**
     * Tracks the currently selected wager (chips).
     */
    protected double selectedWager = 0.0;

    /**
     * The collection of chip values loaded from config (like Mines).
     */
    protected final Map<String, Double> chipValues = new LinkedHashMap<>();

    /**
     * Constructor for base client. 
     */
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
        setupBettingRow();
    }

    /**
     * Called once when creating the client, 
     * to set up any additional UI or placeholders. 
     * Already calls setupBettingRow() in constructor.
     */
    public void initializeUI() {
        setupBettingRow(); // Build the chips, rebet toggle, etc.
    }

    /**
     * --- FROM MINES TABLE ---
     * Load the "chips" from config, as Mines does, storing into chipValues.
     */
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

    /**
     * Creates the 'betting row' at the bottom of the inventory
     * with:
     *  - Chips in slots 47..51
     *  - Paper in 53 ("Click here to place bet")
     *  - Rebet: slot 43
     *  - Undo All: slot 45
     *  - Undo Last: slot 46
     *  - All In: slot 52
     */
    private void setupBettingRow() {
        // 1) Build the chips (slots 47..51)
        int chipSlot = 47;
        for (Map.Entry<String, Double> entry : chipValues.entrySet()) {
            String chipName = entry.getKey();
            double chipVal = entry.getValue();
            inventory.setItem(chipSlot, createCustomItem(getCurrencyMaterial(), chipName, (int) chipVal));
            chipSlot++;
        }

        // 2) Paper in slot 53: "Click here to place bet"
        inventory.setItem(53, createCustomItem(Material.PAPER, "Click here to place bet", 1));

        // 3) Rebet: slot 43
        Material rebetMat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String rebetName = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
        inventory.setItem(43, createCustomItem(rebetMat, rebetName, 1));

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

    /**
     * If your server changes states, the client can respond (like Mines).
     */
    public void onServerStateChange(Server.SessionState oldState, Server.SessionState newState) {
        // Do nothing by default
    }

    /**
     * Called by the plugin. If the user clicks inside this inventory:
     */
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

    /**
     * By default, the 'betting row' is in these slots:
     *  - 43 for rebet
     *  - 45 for undo all
     *  - 46 for undo last
     *  - 47..51 for chips
     *  - 52 for all in
     *  - 53 for 'click here to place bet'
     */
    protected boolean isBetSlot(int slot) {
        return slot >= 43 && slot <= 53; // This covers rebet(43), chips(47-51), all in(52), place bet(53), etc.
    }

    /**
     * Replicates Mines logic for bet slots:
     *  - slot 43: toggle rebet
     *  - slot 45: undo all
     *  - slot 46: undo last
     *  - slot 52: all in
     *  - slot 47..51: user selected a chip
     *  - slot 53: 'Click here to place bet'
     */
    protected void handleBet(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        // Rebet
        if (slot == 43) {
            rebetEnabled = !rebetEnabled;
            updateRebetToggle();
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

    /**
     * Called for any clicks that are not recognized as bet slots.
     */
    protected abstract void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event);

    /**
     * The 'Click here to place bet' logic from Mines. 
     * Checks if the user is holding the currency or using selectedWager.
     */
    private void handleBetPlacement() {
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

    /**
     * We replicate the 'All In' logic from Mines:
     * Sums how many currency items the user has, removes them, pushes onto bet stack.
     */
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

    /**
     * Undo last bet. 
     * EXACT logic from Mines: pop from betStack, refund to user.
     */
    protected void undoLastBet() {
        if (!betStack.isEmpty()) {
            double last = betStack.pop();
            refundCurrency(player, (int) last);
        }
    }

    /**
     * Undo all bets: pop everything from betStack, refund each.
     */
    protected void undoAllBets() {
        while (!betStack.isEmpty()) {
            double b = betStack.pop();
            refundCurrency(player, (int) b);
        }
    }

    /**
     * Called when toggling rebet
     */
    protected void toggleRebet() {
        rebetEnabled = !rebetEnabled;
    }

    /**
     * Updates the item in slot 43 to reflect rebet status.
     */
    protected void updateRebetToggle() {
        Material mat = rebetEnabled ? Material.GREEN_WOOL : Material.RED_WOOL;
        String name = rebetEnabled ? "Rebet: ON" : "Rebet: OFF";
        inventory.setItem(43, createCustomItem(mat, name, 1));
    }

    /**
     * Utility from Mines: updates the lore on 'Click here to place bet' (slot 53)
     * with the current total bet amount.
     */
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

    // ================== MINES UTILITY METHODS PULLED IN ===================

    /**
     * Returns true if player has at least 'amount' currency items.
     * from Mines: hasEnoughCurrency
     */
    protected boolean hasEnoughCurrency(Player player, int amount) {
        if (amount <= 0) return true;
        return getTotalCurrency(player) >= amount;
    }

    /**
     * Sums how many currency items the user has. from Mines
     */
    protected int getTotalCurrency(Player player) {
        return Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(it -> it.getType() == getCurrencyMaterial())
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    /**
     * Removes 'amount' items of getCurrencyMaterial from the player's inventory.
     * from Mines
     */
    protected void removeCurrencyFromInventory(Player player, int amount) {
        if (amount <= 0) return;
        player.getInventory().removeItem(new ItemStack(getCurrencyMaterial(), amount));
    }

    /**
     * Refunds the user 'amount' currency items. from Mines
     */
    protected void refundCurrency(Player player, int amount) {
        if (amount <= 0) return;
        player.getInventory().addItem(new ItemStack(getCurrencyMaterial(), amount));
    }

    // ===================== ALREADY EXISTING CODE (Unmodified) =====================

    /**
     * Must be overridden to specify your currency (like plugin.getCurrency(internalName))
     */
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

    /**
     * For sending events (like 'SLOT_CLICKED') to the server.
     */
    protected void sendUpdateToServer(String eventType, Object data) {
        if (server != null) {
            Bukkit.getLogger().info("[Client] Sending update to server: " + eventType + " | Data: " + data);
            server.onClientUpdate(this, eventType, data);
        }
    }

    /**
     * For receiving updates from the server (like 'UPDATE_UI'), used in Mines or TestGame.
     */
    public void onServerUpdate(String eventType, Object data) {
        Bukkit.getLogger().info("[Client] Received server update: " + eventType + " | Data: " + data);
    }


}
