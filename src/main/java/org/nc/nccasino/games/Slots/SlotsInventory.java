package org.nc.nccasino.games.Slots;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The dealer-facing inventory for a Slots machine. Slots is single-player
 * only -- every player who opens the same dealer mob gets their own
 * independent {@link SlotsMachine} and personal 54-slot view, dispatched by
 * UUID exactly the way {@code MinesInventory} dispatches {@code MinesTable}s.
 */
public class SlotsInventory extends DealerInventory {
    private final Map<UUID, SlotsMachine> machines = new HashMap<>();
    private final Nccasino plugin;
    private final String internalName;

    public SlotsInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 54, "");
        this.internalName = internalName;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.addInventory(dealerId, this);
    }

    @Override
    public void delete() {
        super.delete();
        machines.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory() != this.getInventory()) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != this.getInventory().getHolder()) {
                return;
            }

            UUID playerId = player.getUniqueId();
            if (machines.containsKey(playerId)) {
                // A machine from a previous session is still on record for
                // this UUID -- resolve it through the normal disconnect
                // path first (idempotent no-op if already clean), then
                // guarantee it's gone before handing out a fresh one.
                SessionRegistry.terminateSession(playerId, machines.get(playerId), ExitReason.DISCONNECTED);
                removeTable(playerId);
            }

            SlotsMachine machine = new SlotsMachine(dealerId, player, plugin, internalName, this);
            machines.put(playerId, machine);
            machine.initializeTable();
            player.openInventory(machine.getInventory());
        }, 2L);
    }

    public void removeTable(UUID playerId) {
        machines.remove(playerId);
    }
}
