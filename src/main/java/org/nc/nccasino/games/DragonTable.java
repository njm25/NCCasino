package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.*;

public class DragonTable implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final UUID playerId;
    private final Nccasino plugin;
    private final String internalName;
    private final DragonInventory dragonInventory;

    private final Villager dealer;
    private final Stack<Pair<String, Integer>> betStack;
    private double selectedWager;
    private final Map<String, Double> chipValues;
    private boolean clickAllowed = true;
    private final Map<UUID, Double> currentBets = new HashMap<>();
    private boolean listenerRegistered = false; // Track if the listener has been registered

    public DragonTable(Player player, Villager dealer, Nccasino plugin, String internalName, DragonInventory dragonInventory) {
        this.playerId = player.getUniqueId();
        this.plugin = plugin;
        this.internalName = internalName;
        this.dragonInventory = dragonInventory;
        this.dealer = dealer;
        this.inventory = Bukkit.createInventory(this, 54, "Dragon Climb");
        this.betStack = new Stack<>();
        this.chipValues = new LinkedHashMap<>(); // Ensure insertion order
        loadChipValuesFromConfig();
        initializeTable();

        // Register the event listener only once, and check if it's already registered
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
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

    private void initializeTable() {
        // Add betting buttons
        inventory.setItem(45, createCustomItem(Material.BARRIER, "Undo All Bets", 1));
        inventory.setItem(46, createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Undo Last Bet", 1));

        // Add sorted currency chips (slots 47-51)
        int slot = 47;
        for (Map.Entry<String, Double> entry : chipValues.entrySet()) {
            inventory.setItem(slot, createCustomItem(plugin.getCurrency(internalName), entry.getKey(), entry.getValue().intValue()));
            slot++;
        }

        // Add a single betting option - Paper labeled "Click here to place bet" in slot 52
        inventory.setItem(52, createCustomItem(Material.PAPER, "Click here to place bet", 1));
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DragonTable)) return;
    
        event.setCancelled(true);  // Prevent default click actions, including picking up items
    
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
    
        // Preventing item pickup and drag
        if (event.getClickedInventory() != inventory) return;
    
        // Handle fast click prevention
        if (!clickAllowed) {
            player.sendMessage("Please wait before clicking again!");
            return;
        }
    
        clickAllowed = false;
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed = true, 5L);  // 5 ticks delay for fast click prevention
    
        // Handle bet placement (slot 52 - Paper item)
        if (slot == 52) {
            if (selectedWager > 0) {
                // Check if the player has enough currency to place the bet
                if (hasEnoughCurrency(player, (int) selectedWager)) {
                    double newBetAmount = currentBets.getOrDefault(player.getUniqueId(), 0.0) + selectedWager;
                    currentBets.put(player.getUniqueId(), newBetAmount);
    
                    // Deduct currency from player inventory
                    removeWagerFromInventory(player, (int) selectedWager);
    
                    // Update the lore of the item in slot 52 with the cumulative bet amount
                    updateBetLore(52, newBetAmount);
    
                    player.sendMessage("Placed bet of " + selectedWager + " " + plugin.getCurrencyName(internalName) +
                            ". Total bet is now: " + newBetAmount);

                    // Show "Start Game" lever if bet is greater than 0
                    updateStartGameLever(newBetAmount > 0);
                } else {
                    player.sendMessage("Not enough " + plugin.getCurrencyName(internalName) + "s to place this bet.");
                }
            } else {
                player.sendMessage("Please select a wager amount first.");
            }
            return;
        }
    
        // Handle currency chips (slots 47-51)
        if (slot >= 47 && slot <= 51) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getItemMeta() != null) {
                String itemName = clickedItem.getItemMeta().getDisplayName();
                selectedWager = chipValues.getOrDefault(itemName, 0.0);
                player.sendMessage("Selected wager: " + selectedWager + " " + plugin.getCurrencyName(internalName));
            }
            return;
        }
    
        // Undo all bets
        if (slot == 45) {
            player.sendMessage("Undoing all bets...");
            refundAllBets(player);
            currentBets.clear();
            updateBetLore(52, 0);  // Reset the lore on the bet option after clearing bets
            updateStartGameLever(false); // Hide "Start Game" lever
            player.sendMessage("All bets cleared and refunded.");
            return;
        }
    
        // Undo last bet
        if (slot == 46) {
            if (currentBets.containsKey(player.getUniqueId())) {
                double lastBet = selectedWager;
                double newBetAmount = currentBets.get(player.getUniqueId()) - lastBet;
                currentBets.put(player.getUniqueId(), Math.max(newBetAmount, 0));
    
                refundBet(player, (int) lastBet);
                updateBetLore(52, newBetAmount);
                updateStartGameLever(newBetAmount > 0);  // Update "Start Game" lever based on new bet amount
    
                player.sendMessage("Undoing last bet of " + lastBet + " " + plugin.getCurrencyName(internalName) +
                        ". Total bet is now: " + newBetAmount);
            } else {
                player.sendMessage("No bets to undo.");
            }
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DragonTable && event.getPlayer().getUniqueId().equals(playerId)) {
            endGame();  // Call the end game logic when the inventory is closed
        }
    }

    private void updateBetLore(int slot, double totalBet) {
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (totalBet > 0) {
                    List<String> lore = new ArrayList<>();
                    lore.add("Total Bet: " + totalBet + " " + plugin.getCurrencyName(internalName));
                    meta.setLore(lore);
                } else {
                    meta.setLore(new ArrayList<>());  // Clear lore if no wager
                }
                item.setItemMeta(meta);
            }
        }
    }

    private boolean hasEnoughCurrency(Player player, int amount) {
        ItemStack currencyItem = new ItemStack(plugin.getCurrency(internalName));
        return player.getInventory().containsAtLeast(currencyItem, amount);
    }

    private void removeWagerFromInventory(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(plugin.getCurrency(internalName), amount));
    }

    private void refundAllBets(Player player) {
        double totalRefund = currentBets.getOrDefault(player.getUniqueId(), 0.0);
        refundBet(player, (int) totalRefund);
    }

    private void refundBet(Player player, int amount) {
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);

        for (int i = 0; i < fullStacks; i++) {
            player.getInventory().addItem(new ItemStack(currencyMaterial, 64));
        }

        if (remainder > 0) {
            player.getInventory().addItem(new ItemStack(currencyMaterial, remainder));
        }
    }

    private void endGame() {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("Game over. Thank you for playing!");
            refundAllBets(player);  // Refund any remaining bets
        }

        // Notify DragonInventory to remove the player's table
        dragonInventory.removeTable(playerId);

        cleanup();  // Clean up game state
    }

    // Clean up method to unregister listeners and clear data
    private void cleanup() {
        currentBets.clear();
        betStack.clear();

        // Unregister the event listener when the game ends to prevent memory leaks
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void updateStartGameLever(boolean showLever) {
        if (showLever) {
            inventory.setItem(53, createCustomItem(Material.LEVER, "Start Game", 1));
        } else {
            inventory.setItem(53, null); // Remove the lever if the total bet is 0
        }
    }
}
