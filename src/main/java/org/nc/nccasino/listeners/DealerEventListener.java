package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.nc.nccasino.entities.DealerVillager;

public class DealerEventListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (DealerVillager.isDealerVillager(villager)) {
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID||event.getCause() == EntityDamageEvent.DamageCause.KILL) {
                    // Let it die from /kill
                    return;
                }
                event.setCancelled(true);
                
            }
        }
    }
}
