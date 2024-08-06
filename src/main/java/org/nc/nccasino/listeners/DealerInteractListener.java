package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.games.DealerInventory;
import org.nc.nccasino.Nccasino;

import java.util.UUID;

public class DealerInteractListener implements Listener {

    private final Nccasino plugin;

    public DealerInteractListener(Nccasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Get the entity the player interacted with
        Entity clickedEntity = event.getRightClicked();

        // Check if the entity is a Villager and a DealerVillager
        if (clickedEntity instanceof Villager) {
            Villager villager = (Villager) clickedEntity;

            // Check if this villager is a DealerVillager
            if (DealerVillager.isDealerVillager(villager)) {
                Player player = event.getPlayer();


                // Open the dealer's persistent custom inventory (default to game menu)
                UUID dealerId = DealerVillager.getUniqueId(villager);
                DealerInventory dealerInventory = DealerInventory.getOrCreateInventory(dealerId);
                player.openInventory(dealerInventory.getInventory());

                // Cancel the event to prevent default interactions
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory belongs to a DealerInventory
        if (event.getInventory().getHolder() instanceof DealerInventory) {
            event.setCancelled(true); // Cancel event to prevent taking items

            DealerInventory dealerInventory = (DealerInventory) event.getInventory().getHolder();
            Player player = (Player) event.getWhoClicked();
            dealerInventory.handleClick(event.getSlot(), player);
        }
    }
}
