package org.nc.nccasino.integrations;

import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.listeners.DealerInteractListener;

/**
 * Bridges Citizens' NPC events onto NCCasino's dealer lifecycle.
 *
 * <p>Kept separate from {@link CitizensDealerSupport} because these method
 * signatures name Citizens classes: this type is only loaded and instantiated
 * once Citizens has been confirmed present, so a server without it never has to
 * resolve them.
 *
 * <p>Using Citizens' own click event rather than Bukkit's
 * {@code PlayerInteractEntityEvent} is deliberate. It works uniformly for
 * player-type and mob-type NPCs, and it lets NCCasino cooperate with the rest of
 * Citizens instead of cancelling the interaction out from under it.
 */
public final class CitizensDealerListener implements Listener {

    /**
     * Re-tags an NPC's freshly built entity as a dealer.
     *
     * <p>Deferred by a tick: the spawn event fires while Citizens is still
     * assembling the NPC, and the entity is not reliably readable until that
     * settles.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcSpawn(NPCSpawnEvent event) {
        Nccasino plugin = CitizensDealerSupport.plugin();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getNPC().isSpawned()) {
                CitizensDealerSupport.restoreOnSpawn(event.getNPC());
            }
        });
    }

    /**
     * Cleans up when an admin deletes a bound NPC through Citizens, so the
     * casino does not keep a dealer that no longer has a body.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcRemove(NPCRemoveEvent event) {
        CitizensDealerSupport.forgetRemovedNpc(event.getNPC());
    }

    /**
     * Opens the dealer -- or completes a pending bind -- when an NPC is
     * right-clicked.
     *
     * <p>Runs at {@link EventPriority#HIGH} so that plugins which cancel
     * interactions for their own reasons (region protection, for instance) are
     * respected via {@code ignoreCancelled}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player clicker = event.getClicker();

        // An admin part-way through "bind this dealer to an NPC" claims the
        // click before it can be read as opening a game.
        if (CitizensDealerSupport.consumeBindClick(event.getNPC(), clicker)) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getNPC().getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!Dealer.isDealer(living)) {
            // Not one of ours -- leave the NPC entirely alone.
            return;
        }

        Nccasino plugin = CitizensDealerSupport.plugin();
        if (plugin == null) {
            return;
        }
        DealerInteractListener interactListener = plugin.getDealerInteractListener();
        if (interactListener == null) {
            return;
        }
        if (interactListener.handleDealerClick(clicker, living)) {
            event.setCancelled(true);
        }
    }
}
