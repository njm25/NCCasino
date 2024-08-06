package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.plugin.java.JavaPlugin;
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

                // Example: Get the unique ID and name
                UUID uniqueId = DealerVillager.getUniqueId(villager);
                String dealerName = DealerVillager.getName(villager);


                // Open the dealer's persistent custom inventory
                DealerVillager.openDealerInventory(villager);

                // Cancel the event to prevent default interactions
                event.setCancelled(true);
            }
        }
    }
}
