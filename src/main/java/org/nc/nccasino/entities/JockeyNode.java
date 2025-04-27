package org.nc.nccasino.entities;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Shulker;
import org.bukkit.attribute.AttributeInstance;
import org.nc.nccasino.helpers.AttributeHelper;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class JockeyNode {
    private final UUID id;
    private Mob mob;
    private String customName;
    private JockeyNode parent;
    private JockeyNode child;
    private int position;

    public JockeyNode(Mob mob, int position) {
        this.id = UUID.randomUUID();
        this.mob = mob;
        this.position = position;
        this.customName = mob.getCustomName();
        initializeJockeyAttributes(mob);
    }

    private void initializeJockeyAttributes(Mob mob) {
        // Basic attributes that should be set for all mobs
        mob.setInvisible(false);
        mob.setInvulnerable(true);
        mob.setCustomNameVisible(true);
        mob.setGravity(false);
        mob.setSilent(true);
        mob.setCollidable(false);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);

        // Always disable AI for all mobs - we'll control movement manually
        mob.setAI(false);

        // Prevent any movement
        AttributeInstance movementSpeedAttribute = mob.getAttribute(AttributeHelper.getAttributeSafely("MOVEMENT_SPEED"));
        if (movementSpeedAttribute != null) {
            movementSpeedAttribute.setBaseValue(0.0);
        }

        // Clear equipment to prevent any interference
        if (mob instanceof org.bukkit.entity.LivingEntity livingEntity) {
            var equipment = livingEntity.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(null);
                equipment.setItemInOffHand(null);
                equipment.setHelmet(null);
                equipment.setChestplate(null);
                equipment.setLeggings(null);
                equipment.setBoots(null);
            }
        }

        // Special handling for specific mob types
        if (mob instanceof Slime) {
            ((Slime)mob).setSize(3);
        }
        if (mob instanceof MagmaCube) {
            ((MagmaCube)mob).setSize(3);
        }
        if (mob instanceof Panda) {
            ((Panda)mob).setMainGene(Panda.Gene.NORMAL);
            ((Panda)mob).setHiddenGene(Panda.Gene.NORMAL);
        }
        if (mob instanceof Shulker) {
            // Even for Shulkers, we want to control their behavior
            ((Shulker)mob).setAI(false);
            ((Shulker)mob).setPeek(0); // Prevent peeking behavior
        }

        // Lock the head rotation to match body rotation
        mob.setRotation(mob.getLocation().getYaw(), 0);
    }

    public UUID getId() {
        return id;
    }

    public Mob getMob() {
        return mob;
    }

    public void setMob(Mob mob) {
        if (this.mob != null) {
            // Remove the old mob from the world
            this.mob.remove();
        }
        this.mob = mob;
        if (mob != null) {
            initializeJockeyAttributes(mob);
            mob.setCustomName(customName);
            mob.setCustomNameVisible(true);
        }
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
        if (mob != null) {
            mob.setCustomName(name);
            mob.setCustomNameVisible(true);
        }
    }

    public JockeyNode getParent() {
        return parent;
    }

    public void setParent(JockeyNode parent) {
        this.parent = parent;
    }

    public JockeyNode getChild() {
        return child;
    }

    public void setChild(JockeyNode child) {
        this.child = child;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void mountOn(JockeyNode vehicle) {
        if (vehicle != null && vehicle.getMob() != null && this.mob != null) {
            vehicle.getMob().addPassenger(this.mob);
            this.parent = vehicle;
            vehicle.setChild(this);
        }
    }

    public void unmount() {
        if (this.mob != null && this.parent != null && this.parent.getMob() != null) {
            this.parent.getMob().removePassenger(this.mob);
            this.parent.setChild(null);
            this.parent = null;
        }
    }

    public void remove() {
        unmount();
        if (this.child != null) {
            this.child.unmount();
        }
    }

    public boolean isPassenger() {
        return parent != null;
    }

    public List<JockeyNode> getPassengers() {
        List<JockeyNode> passengers = new ArrayList<>();
        if (child != null) {
            passengers.add(child);
            passengers.addAll(child.getPassengers());
        }
        return passengers;
    }
} 