package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonInventory extends DealerInventory implements Listener {
    private final Map<UUID, DragonTable> Tables;  // Use UUID instead of Player
    private final Nccasino plugin;
    private final Map<UUID, Boolean> interactionLocks = new HashMap<>();  // Locking mechanism to prevent double triggers
    private Boolean firstopen=true;
    public DragonInventory(UUID dealerId, Nccasino plugin) {
        super(dealerId, 54, "Dragon Climb");

        this.plugin = plugin;
        this.Tables = new HashMap<>();
        initializeStartMenu();
        registerListener();
        plugin.addInventory(dealerId, this);
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void unregisterListener() {

        HandlerList.unregisterAll(this);
     
    }

    @Override
    public void delete() {
        super.delete();
        unregisterListener();  // Unregister listener when deleting the inventory
    }
    // Initialize items for the start menu using the inherited method
    private void initializeStartMenu() {
        inventory.clear();
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event){
    
        Player player=(Player)event.getPlayer();
        if(player.getInventory() !=null){
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if(player.getOpenInventory().getTopInventory()== this.getInventory()){
                    if(firstopen){
                        firstopen=false;
                        setupGameMenu(player);
                    }
                }
            }, 2L);    
    }
    }

  
    @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prevent double triggering with a temporary lock
        if (interactionLocks.getOrDefault(playerId, false)) {
            return;  // Interaction is locked
        }
        interactionLocks.put(playerId, true);

        // Check if the villager is the dealer and the game is Dragon Climb
        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            openGameInventory(player);
        }

        // Unlock interaction after handling
        Bukkit.getScheduler().runTaskLater(plugin, () -> interactionLocks.put(playerId, false), 1L);
    }

    // Method to handle opening the game inventory and setting up the game if necessary
    private void openGameInventory(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if the player already has a table, if not, set up the game
        if (!Tables.containsKey(playerId)) {
            setupGameMenu(player);
        } else {
            // If the game is already active, just open the existing inventory
            player.openInventory(Tables.get(playerId).getInventory());
        }
    }

    private void setupGameMenu(Player player) {
        UUID playerId = player.getUniqueId();  // Use the player's UUID

        // Avoid opening multiple tables
        if (!Tables.containsKey(playerId)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                        .findFirst().orElse(null);
                if (dealer != null) {
                    DragonTable dragonTable = new DragonTable(player, dealer, plugin, DealerVillager.getInternalName(dealer), this);
                    Tables.put(playerId, dragonTable);  // Use the player's UUID as the key
                    player.openInventory(dragonTable.getInventory());
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open Dragon Climb table.");
                }
            }, 1L);
        } else {
            player.openInventory(Tables.get(playerId).getInventory());
        }
    }

    // Remove the player's table from the Tables map
    public void removeTable(UUID playerId) {
        Tables.remove(playerId);  // Remove by UUID
        interactionLocks.remove(playerId);  // Clear interaction lock on removal
    }
}
