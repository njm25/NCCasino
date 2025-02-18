package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.nc.nccasino.entities.Dealer;

public class DealerEventListener implements Listener {
private static final Set<UUID> adminTriggeredTeleports = new HashSet<>();
    public static void allowAdminTeleport(UUID entityId) {
        adminTriggeredTeleports.add(entityId);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            if (Dealer.isDealer(mob)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob && Dealer.isDealer((Mob) entity)) {
            event.setCancelled(true);
        }
    }
    

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob && Dealer.isDealer((Mob) entity)) {
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Mob mob && Dealer.isDealer(mob)) {
                if (event.getAffectedEntities().contains(entity)) { // Ensure the entity is still in the list
                    event.setIntensity(entity, 0); // Nullify the effect
                }
            }
        }
    }

    //lingering potions
    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        List<LivingEntity> affectedEntities = event.getAffectedEntities();
        affectedEntities.removeIf(entity -> entity instanceof Mob && Dealer.isDealer((Mob) entity));
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Mob && Dealer.isDealer((Mob) event.getEntity())) {
            Mob dealer = (Mob) event.getEntity();
            dealer.setCanPickupItems(false); // Prevent item pickup
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
            if (event.getEntity() instanceof Mob&& Dealer.isDealer((Mob)event.getEntity())) {
                event.setCancelled(true); // Prevent dealer foxes from picking up items
        }
    }

    @EventHandler
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Mob &&Dealer.isDealer((Mob)event.getEntity())) {
            event.setCancelled(true); // Cancels all natural item drops
        }
    }
    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Mob && Dealer.isDealer((Mob) event.getEntity())) {
            event.setCancelled(true);
        }
    }

     @EventHandler
    public void onShulkerShoot(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Shulker shulker) {
            if (Dealer.isDealer(shulker)) {
                event.setCancelled(true); // Stop the projectile from being launched
            }
        }
    }

  @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob && (mob instanceof Enderman || mob instanceof Endermite || mob instanceof Shulker)) {
            if (Dealer.isDealer(mob)) {
                if (adminTriggeredTeleports.contains(entity.getUniqueId())) {
                    adminTriggeredTeleports.remove(entity.getUniqueId());
                    return; // Allow the teleport
                }
                event.setCancelled(true);
            }
        }
    }

}


