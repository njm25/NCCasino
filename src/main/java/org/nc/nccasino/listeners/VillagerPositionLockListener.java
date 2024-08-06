package org.nc.nccasino.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.Villager;
import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino; // Ensure this is your main class

public class VillagerPositionLockListener implements Listener {

    private static final NamespacedKey DEALER_KEY = new NamespacedKey(Nccasino.getPlugin(Nccasino.class), "dealer_villager");

    @EventHandler
    public void onVillagerMove(EntityMoveEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
            if (dataContainer.has(DEALER_KEY, PersistentDataType.BYTE)) {
                Location originalLocation = villager.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                // Teleport back to the original position if moved
                villager.teleport(originalLocation);
            }
        }
    }
}
