package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import io.papermc.paper.event.entity.EntityMoveEvent;

import org.bukkit.entity.Villager;
import org.bukkit.Location;

public class VillagerPositionLockListener implements Listener {
    
    @EventHandler
    public void onVillagerMove(EntityMoveEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.getCustomName() != null && villager.getCustomName().equals("Dealer Villager")) {
                Location originalLocation = villager.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                // Teleport back to the original position if moved
                villager.teleport(originalLocation);
            }
        }
    }


}
