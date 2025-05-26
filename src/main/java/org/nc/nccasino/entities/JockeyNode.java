package org.nc.nccasino.entities;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.MagmaCube;
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
    private final boolean isNewJockey; // Track whether this is a new jockey

    public JockeyNode(Mob mob, int position) {
        this(mob, position, false); // Default to existing jockey for backward compatibility
    }

    public JockeyNode(Mob mob, int position, boolean isNewJockey) {
        this.id = UUID.randomUUID();
        this.mob = mob;
        this.position = position;
        this.customName = mob.getCustomName();
        this.isNewJockey = isNewJockey;
        if (isNewJockey) {
            initializeJockeyAttributes(mob);
        }
    }

    private void initializeJockeyAttributes(Mob mob) {
        // Basic attributes that should be set for all mobs
        mob.setInvulnerable(true);
        mob.setGravity(false);
        mob.setSilent(true);
        mob.setCollidable(false);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setAI(false); // Always disable AI

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
            // Always set these attributes regardless of whether it's a new jockey
            mob.setInvulnerable(true);
            mob.setAI(false);
            
            if (isNewJockey) { // Only initialize other attributes if this is a new jockey
                initializeJockeyAttributes(mob);
            }
            mob.setCustomName(customName);
            mob.setCustomNameVisible(false);
        }
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
        if (mob != null) {
            mob.setCustomName(name);
            mob.setCustomNameVisible(false);
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
            // First ensure we're unmounted from any current vehicle
            unmount();
            
            // Then do the actual mounting
            vehicle.getMob().addPassenger(this.mob);
            
            // Set up the node relationships
            this.parent = vehicle;
            vehicle.setChild(this);
            
            // Update name visibility
            if (this.position == 1) {
                // Bottom jockey should never show name
                this.mob.setCustomNameVisible(false);
            } else {
                // For other mobs, show name only if they're at the top
                boolean isTop = (parent == null);
                this.mob.setCustomNameVisible(isTop);
            }
            
            // Update vehicle's name visibility
            vehicle.getMob().setCustomNameVisible(false);
        }
    }

    public void mountAsVehicle(JockeyNode passenger) {
        if (passenger != null && passenger.getMob() != null && this.mob != null) {
            // First ensure we're unmounted from any current vehicle
            unmount();
            
            // Then do the actual mounting
            this.mob.addPassenger(passenger.getMob());
            
            // Set up the node relationships
            passenger.setParent(this);
            this.setChild(passenger);
            
            // Update name visibility
            if (this.position == 1) {
                // Bottom jockey should never show name
                this.mob.setCustomNameVisible(false);
            } else {
                // For other mobs, show name only if they're at the top
                boolean isTop = (parent == null);
                this.mob.setCustomNameVisible(isTop);
            }
            
            // Update passenger's name visibility
            passenger.getMob().setCustomNameVisible(false);
        }
    }

    public void unmount() {
        if (this.mob != null) {
            // Remove from current vehicle if any
            if (this.parent != null && this.parent.getMob() != null) {
            this.parent.getMob().removePassenger(this.mob);
            this.parent.setChild(null);
            this.parent = null;
            }
            
            // Remove current passengers if any
            if (this.child != null && this.child.getMob() != null) {
                this.mob.removePassenger(this.child.getMob());
                this.child.setParent(null);
                this.child = null;
            }
            
            // Update name visibility
            if (this.position == 1) {
                // Bottom jockey should never show name
                this.mob.setCustomNameVisible(false);
            } else {
                // For other mobs, show name if they're at the top
                boolean isTop = (this.child == null);
                this.mob.setCustomNameVisible(isTop);
            }
        }
    }

    public void remove() {
        unmount();
        if (this.child != null) {
            this.child.unmount();
        }
        // Actually remove the mob from the world
        if (this.mob != null) {
            this.mob.remove();
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