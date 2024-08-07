package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RouletteInventory extends DealerInventory implements Listener {
    private int pageNum; // Track the current page number
    private final Nccasino plugin; // Reference to the main plugin
    private final Map<UUID, Map<Integer, Double>> playerBets; // Track all players' bets

    public RouletteInventory(UUID dealerId, Nccasino plugin) {
        super(dealerId, 54, "Roulette Start Menu"); // Initialize with 54 slots
        this.plugin = plugin; // Store the plugin reference
        this.pageNum = 1;
        this.playerBets = new HashMap<>(); // Initialize player bets storage

        initializeStartMenu();

        // Register the event listener for this inventory
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public Map<Integer, Double> getPlayerBets(UUID playerId) {
        return playerBets.getOrDefault(playerId, new HashMap<>()); 
    }

    public void printPlayerBets(UUID playerId) {
        // Retrieve the player's bets
        Map<Integer, Double> bets = getPlayerBets(playerId);

        // Print header message
        System.out.println("Player Bets for player ID: " + playerId);

        // Loop through each bet and print the details to the console
        bets.forEach((number, amount) -> {
            System.out.println("Number: " + number + ", Amount: " + amount);
        });

        // Print a footer message
        System.out.println("End of player bets.\n");
    }

    // Clear bets for a specific player
    public void clearPlayerBets(UUID playerId) {
        playerBets.remove(playerId);
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear(); // Clear the inventory before setting up the page
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette", 1), 22); // Central button
    }

    // Set up items for the game menu
    private void setupGameMenu() {
        inventory.clear(); // Clear the inventory before setting up the page
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 20); // Betting Table button
        addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1), 24); // Leave Game button
    }

    // Create an item stack with a custom display name and stack size
    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    // Refresh the inventory for the player
    @Override
    public void refresh(Player player) {
        // Use a delayed task to avoid immediate recursion
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.closeInventory();
            player.openInventory(this.inventory);
        }, 1L);
    }

    // Handle click events in the roulette inventory
    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return; // Ensure this is the correct inventory

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true); // Cancel the event to prevent item movement

        int slot = event.getRawSlot();

        if (pageNum == 1) { // Start menu logic
            if (slot == 22) { // "Start Roulette" button clicked
                player.sendMessage("Starting Roulette...");
                setupGameMenu(); // Switch to game menu
                pageNum = 2; // Set page number to game menu
                refresh(player); // Refresh the player's inventory
            }
        } else if (pageNum == 2) { // Game menu logic
            handleGameMenuClick(slot, player);
        }
    }

    // Handle game menu interactions
    private void handleGameMenuClick(int slot, Player player) {
        if (slot == 20) { // "Open Betting Table" button clicked
            // Use a delayed task to open the betting table to avoid recursion
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Find the dealer associated with the player
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(DealerVillager::isDealerVillager)
                        .findFirst().orElse(null);

                if (dealer != null) {
                    // Retrieve existing bets for the player or initialize if none
                    player.sendMessage("PlayerUniqueId after pressing open betting table"+player.getUniqueId());
                    Map<Integer, Double> bets = getPlayerBets(player.getUniqueId()); // Use method to retrieve
                    player.sendMessage("Retrieved Bets:");
                    bets.forEach((number, amount) -> {
                        player.sendMessage("Number: " + number + ", Amount: " + amount);
                    });
                    // Create a new betting table with the existing bets
                    BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets);

                    // Open the betting table for the player
                    player.openInventory(bettingTable.getInventory());
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open betting table.");
                }
            }, 1L);
        } else if (slot == 24) { // "Leave Game" button clicked
            player.closeInventory();
            player.sendMessage("You have left the game.");
            // Handle any necessary cleanup
            clearPlayerBets(player.getUniqueId()); // Remove the player's bets
        }
    }

    // Clear bets when player closes the inventory directly
    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        
        if (event.getInventory().getHolder() != this) return; // Ensure this is the correct inventory
        Player player = (Player) event.getPlayer();
        player.sendMessage("This should trigger if player presses esc test");
        //clearPlayerBets(player.getUniqueId());
    }

    public void updatePlayerBets(UUID playerId, Map<Integer, Double> bets) {
        playerBets.put(playerId, bets);
    }



}
