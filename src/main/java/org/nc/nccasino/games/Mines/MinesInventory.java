package org.nc.nccasino.games.Mines;

import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.games.Mines.MinesTable;
import org.nc.nccasino.helpers.DealerInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinesInventory extends DealerInventory implements Listener {
    private final Map<UUID, MinesTable> Tables;
    private final Nccasino plugin;
    private final Map<UUID, Boolean> interactionLocks = new HashMap<>();
    private Boolean firstopen=true;

    public MinesInventory(UUID dealerId, Nccasino plugin) {
        
        super(dealerId, 54, "Mines Start Menu");
   
        this.plugin = plugin;
        this.Tables = new HashMap<>();
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
        Tables.clear();
        interactionLocks.clear();
        unregisterListener();  // Unregister listener when deleting the inventory
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

        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            // Open the MinesTable for the player
            setupGameMenu(player);
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        int slot = event.getRawSlot();
    }                                                                                                                      
  
    // Immediately have player open local mines table
    private void setupGameMenu(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                    .filter(entity -> entity instanceof Villager)
                    .map(entity -> (Villager) entity)
                    .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                    .findFirst().orElse(null);
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);

                MinesTable minesTable = new MinesTable(player, dealer, plugin, internalName, this);
                Tables.put(player.getUniqueId(), minesTable);
                player.openInventory(minesTable.getInventory());
            } else {
                player.sendMessage("§cError: Dealer not found. Unable to open mines table.");
            }
        }, 1L);
    }

  
    public void removeTable(UUID playerId) {
        Tables.remove(playerId);  // Remove by UUID
        interactionLocks.remove(playerId);  // Clear interaction lock on removal
    }

}
