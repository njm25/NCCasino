package org.nc.nccasino.integrations;

import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.CitizensReloadEvent;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
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
     * The authoritative trigger for {@link CitizensDealerSupport#sweepAlreadySpawned()}.
     *
     * <p>Citizens' own wiki documents waiting for this event (or {@code
     * CitizensLoadEvent}) as the correct way to know its API -- the NPC
     * registry included -- is safe to use, specifically because
     * {@code CitizensAPI} methods are not reliable merely because Citizens'
     * {@code onEnable} has returned. This class only starts listening from
     * inside {@link CitizensDealerSupport#register}, which Bukkit calls
     * during NCCasino's own {@code onEnable} -- itself only reached after
     * Citizens' {@code onEnable} has already returned, since Citizens is a
     * {@code softdepend}. Whether Citizens could fire this event synchronously
     * within its own {@code onEnable}, before this listener exists to catch
     * it, was not independently verified against Citizens' source; the
     * unconditional call in {@link CitizensDealerSupport#register} exists
     * precisely as a safety net for that possibility.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCitizensEnable(CitizensEnableEvent event) {
        CitizensDealerSupport.sweepAlreadySpawned();
    }

    /** {@code /citizens reload} rebuilds the NPC registry the same way startup does. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCitizensReload(CitizensReloadEvent event) {
        CitizensDealerSupport.sweepAlreadySpawned();
    }

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
     * A genuine temporary despawn retains the NPC binding but closes its live
     * game -- see {@link CitizensDealerSupport#detachOnDespawn}.
     *
     * <p>Citizens fires this same event for {@link DespawnReason#CHUNK_UNLOAD},
     * {@link DespawnReason#WORLD_UNLOAD}, {@link DespawnReason#PENDING_RESPAWN}
     * and {@link DespawnReason#RELOAD} too (confirmed against Citizens' own
     * {@code CitizensNPC.despawn()}) -- ordinary world-streaming/respawn/reload
     * churn a player simply walking away can trigger, not an admin's deliberate
     * {@code /npc despawn}. Tearing down an active session for one of those
     * would force-close a player's GUI mid-round for no real reason, and for
     * every {@code Server}-based game except Blackjack ({@code Client.cleanup()}
     * only unregisters listeners, with no refund or settlement) it would drop
     * an active wager entirely. Each of those four reasons is already handled
     * correctly elsewhere without any help from this handler: the entity
     * survives {@code CHUNK_UNLOAD}/{@code WORLD_UNLOAD} to be found again on
     * reload, {@code PENDING_RESPAWN} is immediately followed by a fresh
     * {@code NPCSpawnEvent} whose {@code adoptBody} already evicts the old
     * entity's registry entry, and {@code RELOAD} is a despawn/respawn pair
     * Citizens itself performs around {@code CitizensReloadEvent} (also
     * handled above via {@link #onCitizensReload}). This mirrors the identical
     * decision already made for chunk *load* in
     * {@code DealerInitializeListener}, which skips reinitializing a
     * Citizens-backed dealer for the same reason.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcDespawn(NPCDespawnEvent event) {
        switch (event.getReason()) {
            case CHUNK_UNLOAD:
            case WORLD_UNLOAD:
            case PENDING_RESPAWN:
            case RELOAD:
                return;
            default:
                CitizensDealerSupport.detachOnDespawn(event.getNPC());
        }
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
