package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PotionSplashEvent;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.nc.nccasino.entities.Dealer;

public class DealerEventListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            if (Dealer.isDealer(mob)) {
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

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob && Dealer.isDealer((Mob) entity)) {
                event.setCancelled(true);
        }
    }

    //regular splash potion
    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Mob && Dealer.isDealer((Mob) entity)) {
                event.setIntensity(entity, 0); // Nullify the effect
            }
        }
    }

    //lingering potions
    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        List<LivingEntity> affectedEntities = event.getAffectedEntities();
        affectedEntities.removeIf(entity -> entity instanceof Mob && Dealer.isDealer((Mob) entity));
    }

}


