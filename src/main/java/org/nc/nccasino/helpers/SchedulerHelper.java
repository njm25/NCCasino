package org.nc.nccasino.helpers;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Helper for scheduling tasks in a Folia-compatible way
 */
public class SchedulerHelper {
    
    public static void executeEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (entity != null && !entity.isDead()) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        }
    }
    
    public static ScheduledTask executeEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (entity != null && !entity.isDead()) {
            return entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        }
        return null;
    }
    
    public static ScheduledTask executeEntityTaskTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (entity != null && !entity.isDead()) {
            return entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), null, delayTicks, periodTicks);
        }
        return null;
    }
    
    public static void executeRegionTask(Plugin plugin, Location loc, Runnable task) {
        if (loc != null && loc.getWorld() != null) {
            Bukkit.getRegionScheduler().run(plugin, loc, scheduledTask -> task.run());
        }
    }
    
    public static ScheduledTask executeRegionTaskLater(Plugin plugin, Location loc, Runnable task, long delayTicks) {
        if (loc != null && loc.getWorld() != null) {
            return Bukkit.getRegionScheduler().runDelayed(plugin, loc, scheduledTask -> task.run(), delayTicks);
        }
        return null;
    }
    
    public static ScheduledTask executeRegionTaskTimer(Plugin plugin, Location loc, Runnable task, long delayTicks, long periodTicks) {
        if (loc != null && loc.getWorld() != null) {
            return Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, scheduledTask -> task.run(), delayTicks, periodTicks);
        }
        return null;
    }
    
    public static void executeGlobalTask(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }
    
    public static void executeAsyncTask(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }
    
    public static void executeAsyncTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        long delayMillis = delayTicks * 50;
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMillis, TimeUnit.MILLISECONDS);
    }
}
