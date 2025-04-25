package org.nc.nccasino.entities;

import org.bukkit.entity.Mob;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JockeyManager {
    private final Mob dealer;
    private JockeyNode head;
    private final List<JockeyNode> jockeys;

    public JockeyManager(Mob dealer) {
        this.dealer = dealer;
        this.jockeys = new ArrayList<>();
        this.head = new JockeyNode(dealer, 0);
        jockeys.add(head);
    }

    public JockeyNode addJockey(Mob mob, int position) {
        if (position <= 0 || position > jockeys.size() + 1) {
            return null;
        }

        JockeyNode newNode = new JockeyNode(mob, position);
        
        // If adding at an existing position, shift all subsequent jockeys up
        if (position <= jockeys.size()) {
            for (int i = jockeys.size() - 1; i >= position; i--) {
                JockeyNode jockey = jockeys.get(i);
                jockey.setPosition(i + 1);
            }
        }

        // Get the parent node (the one below this new jockey)
        JockeyNode parent = position > 1 ? jockeys.get(position - 1) : head;
        
        // If there's already a jockey at this position or above, unmount it first
        if (position < jockeys.size()) {
            JockeyNode existing = jockeys.get(position);
            existing.unmount();
            // Mount it on the new node
            existing.mountOn(newNode);
        }

        // Mount the new node on its parent
        newNode.mountOn(parent);
        
        // Add the new node to our list at the correct position
        if (position >= jockeys.size()) {
            jockeys.add(newNode);
        } else {
            jockeys.add(position, newNode);
        }

        return newNode;
    }

    public boolean removeJockey(int position) {
        if (position <= 0 || position >= jockeys.size()) {
            return false;
        }

        JockeyNode toRemove = jockeys.get(position);
        JockeyNode parent = toRemove.getParent();
        JockeyNode child = toRemove.getChild();

        toRemove.remove();
        jockeys.remove(position);

        // If there was a child, mount it on the parent
        if (child != null && parent != null) {
            child.mountOn(parent);
        }

        // Update positions of all subsequent jockeys
        for (int i = position; i < jockeys.size(); i++) {
            jockeys.get(i).setPosition(i);
        }

        return true;
    }

    public JockeyNode getJockey(int position) {
        if (position < 0 || position >= jockeys.size()) {
            return null;
        }
        return jockeys.get(position);
    }

    public List<JockeyNode> getJockeys() {
        return new ArrayList<>(jockeys);
    }

    public int getJockeyCount() {
        return jockeys.size() - 1; // Exclude the dealer
    }

    public void clearJockeys() {
        // Start from the top and remove each jockey
        while (jockeys.size() > 1) {
            removeJockey(jockeys.size() - 1);
        }
    }

    public void teleportAll(Location location) {
        dealer.teleport(location);
        // All jockeys will follow since they're mounted
    }

    public boolean changeMobType(int position, Mob newMob) {
        if (position <= 0 || position >= jockeys.size()) {
            return false;
        }

        JockeyNode jockey = jockeys.get(position);
        jockey.setMob(newMob);
        return true;
    }

    public boolean changeJockeyName(int position, String newName) {
        if (position <= 0 || position >= jockeys.size()) {
            return false;
        }

        JockeyNode jockey = jockeys.get(position);
        jockey.setCustomName(newName);
        return true;
    }

    public Mob getDealer() {
        return dealer;
    }
} 