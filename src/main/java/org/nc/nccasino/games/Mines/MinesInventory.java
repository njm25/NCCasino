package org.nc.nccasino.games.Mines;

import org.nc.nccasino.entities.DealerInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.nc.nccasino.Nccasino;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinesInventory extends DealerInventory {
    private final Map<UUID, MinesTable> Tables = new HashMap<>();
    private final Nccasino plugin;
    private final Map<UUID, Boolean> interactionLocks = new HashMap<>();
    private String internalName;
    public MinesInventory(UUID dealerId, Nccasino plugin, String internalName) {
        
        super(dealerId, 54, "");
        this.internalName = internalName;
        this.plugin = plugin;
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
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory() == this.getInventory()){

            Player player = (Player) event.getPlayer();

            if (player.getInventory() != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() == this.getInventory().getHolder()) {
                 
                        MinesTable minesTable = new MinesTable(player, plugin, internalName, this);
                        Tables.put(player.getUniqueId(), minesTable);
                        minesTable.initializeTable();
                        player.openInventory(minesTable.getInventory());
                    }
                   
                }, 2L);
            }
        }

    }


    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
    }                                                                                                                      
  
    public void removeTable(UUID playerId) {
        Tables.remove(playerId);  // Remove by UUID
        interactionLocks.remove(playerId);  // Clear interaction lock on removal
    }

 


}
