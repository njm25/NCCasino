package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.nc.nccasino.entities.Dealer;

public class DealerEventListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mob villager) {
            if (Dealer.isDealer(villager)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVillagerTransform(EntityTransformEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (Dealer.isDealer(villager)) {
                event.setCancelled(true);
            }
        }
    }
}


