package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.entity.Villager;

import java.util.UUID;

import org.bukkit.entity.Entity;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.plugin.java.JavaPlugin;

public class DealerInteractListener implements Listener {

    public DealerInteractListener(JavaPlugin plugin) {
        // Initialization if needed
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
                // Perform actions when interacting with the DealerVillager
                event.getPlayer().sendMessage("You interacted with a Dealer Villager!");

                // Example: Get the unique ID and name
                UUID uniqueId = DealerVillager.getUniqueId(villager);
                String dealerName = DealerVillager.getName(villager);

                // Send details to the player
                event.getPlayer().sendMessage("Dealer ID: " + uniqueId);
                event.getPlayer().sendMessage("Dealer Name: " + dealerName);

                // Add your custom actions here, such as opening a GUI or starting a trade
            }
        }
    }
}
