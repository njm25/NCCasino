package org.nc.nccasino.entities;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class DealerVillager {
    private final Villager villager;
    private final UUID uniqueId;

    public DealerVillager(Villager villager) {
        this.villager = villager;
        this.uniqueId = UUID.randomUUID(); // Generate a unique ID for this dealer villager

        villager.setAI(true); // Enable AI for rotation but disable movement
        villager.setInvulnerable(true); // Make the villager invulnerable to all damage
        villager.setCustomName("Dealer Villager"); // Set the custom name
        villager.setCustomNameVisible(true); // Make the custom name visible
        villager.setProfession(Villager.Profession.NONE); // Set profession if needed
        villager.setVillagerLevel(5); // Max level for visual differentiation

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
        // Spawn a new villager entity at the specified location
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        DealerVillager dealer = new DealerVillager(villager);
        dealer.setName(name);
        return dealer;
    }
}
