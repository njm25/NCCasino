package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.DealerInventory;
import java.util.UUID;
//import java.util.Vector;

public class DealerInteractListener implements Listener {


    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();

    

        if (clickedEntity instanceof Villager) {
            Villager villager = (Villager) clickedEntity;

            if (DealerVillager.isDealerVillager(villager)) {
                Player player = event.getPlayer();
                UUID dealerId = DealerVillager.getUniqueId(villager);
                DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);


                player.openInventory(dealerInventory.getInventory());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
    
        if (event.getInventory().getHolder() instanceof DealerInventory) {
            event.setCancelled(true); // Prevent item movement
            DealerInventory dealerInventory = (DealerInventory) event.getInventory().getHolder();
    
            // Handle click for all DealerInventory types
            dealerInventory.handleClick(event.getSlot(), player, event);
        }
    }
    
}
