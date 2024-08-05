package org.nc.nccasino.entities;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.UUID;

public class DealerVillager {
    private final Villager villager;
    private final UUID uniqueId;
    private final Location fixedLocation;

    public DealerVillager(Villager villager, Location location) {
        this.villager = villager;
        this.uniqueId = UUID.randomUUID(); // Generate a unique ID for this dealer villager
        this.fixedLocation = location.clone(); // Store the fixed location

        initializeVillager();
    }

    private void initializeVillager() {
        // Customize the villager's properties
        villager.setAI(true); // Allow AI for natural rotation
        villager.setInvulnerable(true); // Make the villager invulnerable to all damage
        villager.setCustomName("Dealer Villager"); // Set the custom name
        villager.setCustomNameVisible(true); // Make the custom name visible
        villager.setProfession(Villager.Profession.NONE); // Set profession if needed
        villager.setVillagerLevel(5); // Max level for visual differentiation

        // Disable the villager's default pathfinding to prevent movement
       
    }


    public Villager getVillager() {
        return villager;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void setName(String name) {
        villager.setCustomName(name);
    }

    public static DealerVillager spawn(JavaPlugin plugin, Location location, String name) {
        // Adjust the location to center on the block
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        // Spawn a new villager entity at the centered location
        Villager villager = (Villager) centeredLocation.getWorld().spawnEntity(centeredLocation, EntityType.VILLAGER);
        DealerVillager dealer = new DealerVillager(villager, centeredLocation);
        dealer.setName(name);

        // Start a periodic check to correct large position errors
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villager.isValid()) {
                    this.cancel(); // Stop the task if the villager is no longer valid
                    return;
                }
                Location currentLocation = villager.getLocation();
                if (currentLocation.distanceSquared(dealer.fixedLocation) > 0.25) { // Check for significant deviation
                    villager.teleport(dealer.fixedLocation.clone().setDirection(currentLocation.getDirection()));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second (20 ticks)

        return dealer;
    }
}
