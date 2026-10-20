package org.nc.nccasino.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tadpole;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.JockeyManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class DealerEventListener implements Listener {
    private static final Set<UUID> adminTriggeredTeleports = new HashSet<>();
    private static final Map<UUID, JockeyManager> jockeyManagerCache = new HashMap<>();
    private static final long CACHE_TIMEOUT = 20L * 60L; // 60 seconds in ticks
    
    public static void allowAdminTeleport(UUID entityId) {
        adminTriggeredTeleports.add(entityId);
    }

    public static void clearJockeyManagerCache(UUID dealerId) {
        jockeyManagerCache.remove(dealerId);
    }

    @SuppressWarnings("unused")
    private JockeyManager getJockeyManager(Mob dealer) {
        UUID dealerId = dealer.getUniqueId();
        JockeyManager manager = jockeyManagerCache.get(dealerId);
        
        if (manager == null) {
            manager = new JockeyManager(dealer);
            jockeyManagerCache.put(dealerId, manager);
            
            // Schedule cache cleanup
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(DealerEventListener.class),
                () -> jockeyManagerCache.remove(dealerId), CACHE_TIMEOUT);
        }
        return manager;
    }

    private boolean isPartOfDealerStack(Mob mob) {
        // First check if this mob is a dealer
        if (isVanillaDealer(mob)) {
            return true;
        }

        // Check if this mob is a passenger of any dealer
        Entity vehicle = mob.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof Mob vehicleMob && isVanillaDealer(vehicleMob)) {
                return true;
            }
            vehicle = vehicle.getVehicle();
        }

        // Check if this mob has a dealer as a passenger
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob passengerMob && isVanillaDealer(passengerMob)) {
                return true;
            }
        }

        // Check if this mob is part of a dealer's stack by following the chain
        Entity current = mob;
        // Check upward chain (passengers)
        while (!current.getPassengers().isEmpty()) {
            current = current.getPassengers().get(0);
            if (current instanceof Mob passengerMob && isVanillaDealer(passengerMob)) {
                return true;
            }
        }
        // Check downward chain (vehicles)
        current = mob;
        while (current.getVehicle() != null) {
            current = current.getVehicle();
            if (current instanceof Mob vehicleMob && isVanillaDealer(vehicleMob)) {
                return true;
            }
        }

        return false;
    }

    private boolean isVanillaDealer(Mob mob) {
        return Dealer.isDealer(mob) && Dealer.getBackend(mob) != Dealer.Backend.CITIZENS;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
            // A grown tadpole retries becoming a frog every tick; restart its clock.
            if (mob instanceof Tadpole tadpole) {
                tadpole.setAge(0);
            }
        }
    }

    // A pig's lightning transformation fires PigZapEvent, which has its own
    // handler list, so onEntityTransform never sees it.
    @EventHandler
    public void onPigZap(PigZapEvent event) {
        if (isPartOfDealerStack(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreeperPower(CreeperPowerEvent event) {
        if (isPartOfDealerStack(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // Lightning recolours a mooshroom without an event to cancel, so its
    // colour is put back for as long as the bolt can still hit it.
    @EventHandler(ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        Location strike = event.getLightning().getLocation();
        Map<MushroomCow, MushroomCow.Variant> colours = new HashMap<>();
        // A bolt hits entities up to 3 blocks around it and 9 above.
        for (Entity nearby : strike.getWorld().getNearbyEntities(strike.clone().add(0, 3, 0), 4, 7, 4)) {
            if (nearby instanceof MushroomCow cow && isPartOfDealerStack(cow)) {
                colours.put(cow, cow.getVariant());
            }
        }
        if (colours.isEmpty()) {
            return;
        }
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                colours.forEach((cow, colour) -> {
                    if (cow.isValid() && cow.getVariant() != colour) {
                        cow.setVariant(colour);
                    }
                });
                if (++ticks >= 40) {
                    cancel();
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(DealerEventListener.class), 1L, 1L);
    }

    // A dispenser with shears shears sheep, snow golems and bogged, and turns
    // a mooshroom into a cow.
    @EventHandler
    public void onBlockShearEntity(BlockShearEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    // Dealers never change the world around them (a snow golem's trail, an
    // enderman's block, a copper golem turning into a statue).
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Mob mob && isPartOfDealerStack(mob)) {
                event.setIntensity(entity, 0);
            }
        }
    }

    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        List<LivingEntity> affectedEntities = event.getAffectedEntities();
        affectedEntities.removeIf(entity -> entity instanceof Mob mob && isPartOfDealerStack(mob));
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            mob.setCanPickupItems(false);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Mob mob && isPartOfDealerStack(mob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShulkerShoot(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Shulker shulker && isPartOfDealerStack(shulker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob && (mob instanceof Enderman || mob instanceof Endermite || mob instanceof Shulker)) {
            if (isPartOfDealerStack(mob)) {
                if (adminTriggeredTeleports.contains(entity.getUniqueId())) {
                    adminTriggeredTeleports.remove(entity.getUniqueId());
                    return;
                }
                event.setCancelled(true);
            }
        }
    }
}


