package org.nc.nccasino.entities;

import org.bukkit.entity.Mob;
import org.bukkit.entity.EntityType;
import java.util.UUID;

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
} 