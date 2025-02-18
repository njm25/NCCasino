package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;

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
                        if (entity instanceof Mob mob && Dealer.isDealer(mob)) {
                            plugin.reloadDealer(mob); // Initialize dealer if found
                            Dealer.startLookingAtPlayers(mob);
                        }
            }
        }, 1L);
    }

}
