package org.nc.nccasino.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Villager;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.NamespacedKey;

public class DealerDeathHandler implements Listener {

    private final Nccasino plugin;

    public DealerDeathHandler(Nccasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDealerVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
            NamespacedKey dealerKey = new NamespacedKey(plugin, "dealer_villager");

            // Check if the villager is a dealer
            if (dataContainer.has(dealerKey, PersistentDataType.BYTE)) {
                // Get the internal name and unique ID of the dealer
                String internalName = dataContainer.get(new NamespacedKey(plugin, "internal_name"), PersistentDataType.STRING);
                String uniqueId = dataContainer.get(new NamespacedKey(plugin, "dealer_unique_id"), PersistentDataType.STRING);

                // Remove the DealerVillager object from any tracking collections
                if (uniqueId != null) {
                    DealerVillager.removeDealer(villager); // Custom method to remove the DealerVillager instance
                }

                // Remove dealer's entry from the configuration
                if (internalName != null) {
                    plugin.getConfig().set("dealers." + internalName, null);
                    plugin.saveConfig();
                }

                
                // Get the player who killed the villager, if applicable
                if (event.getEntity().getKiller() instanceof Player killer) {
                    // Send the removal message to the killer
                    killer.sendMessage(Component.text("Dealer '")
                            .color(NamedTextColor.RED)
                            .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                            .append(Component.text("' has died and all associated data has been removed.").color(NamedTextColor.RED)));
                }

                // Perform any additional cleanup if needed
            }
        }
    }
}
