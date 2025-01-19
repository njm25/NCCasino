package org.nc.nccasino.listeners;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.nc.nccasino.entities.DealerVillager;

public class DealerEventListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (DealerVillager.isDealerVillager(villager)) {
            
                event.setCancelled(true);
                
            }
        }
    }
}
