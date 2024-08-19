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

public class MinesTable implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final UUID playerId;
    private final UUID dealerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final MinesInventory minesInventory;
    private final Map<Player, Integer> animationTasks;
    private final Map<String, Double> chipValues;
    private final Map<Player, Boolean> animationCompleted;
    private Boolean finished=false;
    private Boolean clickAllowed=true;
    private double selectedWager;
    private final Map<UUID, Double> currentBets = new HashMap<>();

    public MinesTable(Player player, Villager dealer, Nccasino plugin, String internalName, MinesInventory minesInventory) {
        this.playerId = player.getUniqueId();
        this.dealerId = dealer.getUniqueId();
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.minesInventory = minesInventory;
        this.inventory = Bukkit.createInventory(this, 54, "Mines");
        this.chipValues = new LinkedHashMap<>();
        this.animationTasks = new HashMap<>();
        this.animationCompleted = new HashMap<>();
        loadChipValuesFromConfig();

        // Start the animation first, then return to this table once animation completes
        startAnimation(player);
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
            animationTable.animateMessage(player, this::afterAnimationComplete);
        }, 1L); // Delay by 1 tick to ensure smooth opening of inventory
    }

    private void afterAnimationComplete() {
        // Add a slight delay to ensure smooth transition from the animation to the table
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            initializeTable();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.openInventory(inventory);
                // No need to register the listener here since it's handled in the constructor
            }
        }, 1L); // Delay by 1 tick to ensure clean transition between inventories
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
        inventory.clear();
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
        if (!(event.getInventory().getHolder() instanceof MinesTable)) return;
        event.setCancelled(true);  // Prevent default click actions, including picking up items
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (animationTasks.containsKey(player) && event.getCurrentItem() != null) {
            finished=true;
            Bukkit.getScheduler().cancelTask(animationTasks.get(player));
            animationTasks.remove(player);
            animationCompleted.put(player, true);  // Mark animation as completed/skipped
            initializeTable();
            return;
        }
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
    
                player.sendMessage("Undoing last bet of " + lastBet + " " + plugin.getCurrencyName(internalName) +
                        ". Total bet is now: " + newBetAmount);
            } else {
                player.sendMessage("No bets to undo.");
            }
            return;
        }
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

    public Inventory getInventory() {
        return inventory;
    }

    // Handle inventory close event
    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
if(player.getOpenInventory()!=null){
    if (animationTasks.containsKey(player)) {
       // System.out.println("CloseInv?");
        Bukkit.getScheduler().cancelTask(animationTasks.get(player));
        animationTasks.remove(player);
        animationCompleted.remove(player);
    }


}

        //save any games that have been start?
        // Cancel any ongoing animation if the player closes the inventory
       
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
        //minesInventory.removeTable(playerId);

        //cleanup();  // Clean up game state
    }
/* 
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

*/
private void startBlockAnimation(Player player, Runnable onAnimationComplete) {
    animationCompleted.put(player, false);  // Reset the animation completed flag
   
    // Ensure that no duplicate animations are started
    if (animationTasks.containsKey(player)) {
        return;
    }
    
    int[][] full = new int[][]{
        {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,},
        {0,0,0,0,0,0,0,0,0,     1,1,0,1,1,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0, 0,0,0,0,0,0,  1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,1,0,},
        {0,0,0,0,0,0,0,0,0,     1,0,1,0,1,0,0,0,1,0,0,0,1,0,1,0,1,0,1,1,1,1,0,0,1,1,1,0, 0,1,1,1,1,0,  1,0,1,0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,0,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,},
        {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,0,0,0,0,0,1, 0,1,1,1,1,0,  1,0,0,1,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,0,},
        {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,1, 0,0,0,0,0,0,  1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,},
        {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,1,1,1,1,0, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,}
    };

    final int[] taskId = new int[1];
    final int initialRowShift =0;  // Adjust this to change the starting shift, should ensure "CASI" is visible

    taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
        private int rowShift = -initialRowShift;  // Start with a negative shift to position the first visible letter correctly
        
        @Override
        public void run() {
            if (finished) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                animationTasks.remove(player);
                //System.out.println("Finished apparatnelty");
                return;
            }

            // Clear the inventory before drawing the next frame
            inventory.clear();

            // Draw the current frame
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    int arrayIndex = col + rowShift;
                    if (arrayIndex >= 0 && arrayIndex < full[row].length) { // Prevent out of bounds and ensure we're within array limits
                        int blockType = full[row][arrayIndex];
                        Material material = (blockType == 1) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;

                        int slot = row * 9 + col; // calculate slot in inventory grid
                        inventory.setItem(slot, createCustomItem(material, "CLICK TO SKIP", 1));
                    }
                }
            }

            // Shift the display for the next frame
            rowShift++;

            // Adjust this condition to allow for the entire "CASINO MINES" text to be shown
            if (rowShift >= full[0].length) { // Stop when the entire string has moved through
                Bukkit.getScheduler().cancelTask(taskId[0]);
                animationTasks.remove(player);
                onAnimationComplete.run();  // Execute the next action after the animation completes
            }
        }
    }, 0L, 1L).getTaskId();  // Adjust tick speed if needed

    // Store the task ID so it can be canceled later
    int temp=taskId[0];
    //System.out.println("put");
    animationTasks.put(player, 1);
}








}

         
