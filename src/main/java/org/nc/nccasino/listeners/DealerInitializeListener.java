package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.JockeyManager;

public class DealerInitializeListener implements Listener {
    private final Nccasino plugin;

    public DealerInitializeListener(Nccasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Loop through all entities in the chunk
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Mob mob) {
                    if(entity instanceof Villager || entity instanceof PiglinBrute){
                        //plugin.getLogger().info("[villager/piglinBrute] in chunk " + chunk.getX() + "," + chunk.getZ() + ": " + mob.getType()+" isDealer:"+Dealer.isDealer(mob));
                    }
                    if (Dealer.isDealer(mob)) {
                       // plugin.getLogger().info("[Debug] Found dealer in chunk " + chunk.getX() + "," + chunk.getZ() + ": " + mob.getType());
                        plugin.reloadDealer(mob);
                        new JockeyManager(mob);
                    } else if (mob instanceof Villager || mob instanceof PiglinBrute) {
                       // plugin.getLogger().info("[Debug] Found potential dealer mob in chunk " + chunk.getX() + "," + chunk.getZ() + ": " + mob.getType());
                    }
                }
            }
        }, 1L);
    }
}
