package org.nc.nccasino.entities;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import org.bukkit.Bukkit;

public class JockeyManager {
    private final Mob dealer;
    private JockeyNode head;
    private final List<JockeyNode> jockeys;
    private static final Map<UUID, BukkitTask> lookTasks = new HashMap<>();
    private static final Random random = new Random();

    private JockeyNode findTopmostJockey() {
        JockeyNode current = head;
        while (current.getChild() != null) {
            current = current.getChild();
        }
        return current;
    }
    @SuppressWarnings("unused")
    private void initializeFromPassenger(Entity passenger, int position) {
        if (passenger instanceof Mob && position > 0) {  // Ensure we don't try to add the dealer
            Mob mobPassenger = (Mob) passenger;
            
            // Create new node without mounting - this is an existing jockey
            JockeyNode newNode = new JockeyNode(mobPassenger, position, false);
            
            // Find parent node
            JockeyNode parent = jockeys.get(position - 1);
            
            // Set up parent-child relationship
            newNode.setParent(parent);
            parent.setChild(newNode);
            
            // Add to jockeys list
            jockeys.add(newNode);
            
            // Recursively process this passenger's passengers
            if (!mobPassenger.getPassengers().isEmpty()) {
                for (Entity subPassenger : mobPassenger.getPassengers()) {
                    initializeFromPassenger(subPassenger, jockeys.size());
                }
            }
        }
    }
    @SuppressWarnings("unused")
    private void initializeFromVehicle(Entity vehicle, int position) {
        if (vehicle instanceof Mob && position > 0) {  // Ensure we don't try to add the dealer
            Mob mobVehicle = (Mob) vehicle;
            
            // Create new node without mounting - this is an existing jockey
            JockeyNode newNode = new JockeyNode(mobVehicle, position, false);
            
            // Find child node (the one riding this vehicle)
            JockeyNode child = jockeys.get(position - 1);
            
            // Set up parent-child relationship using the new mountAsVehicle method
            newNode.mountAsVehicle(child);
            
            // Add to jockeys list
            jockeys.add(newNode);
            
            // Recursively process this vehicle's vehicle
            if (mobVehicle.getVehicle() instanceof Mob) {
                initializeFromVehicle(mobVehicle.getVehicle(), jockeys.size());
            }
        }
    }

    public JockeyManager(Mob dealer) {
        this.dealer = dealer;
        this.jockeys = new ArrayList<>();
        
        // Add dealer as first node
        JockeyNode dealerNode = new JockeyNode(dealer, 0, false);
        jockeys.add(dealerNode);
        head = dealerNode;
        
        // Build the stack structure without mounting
        int position = 1;
        
        // First handle vehicles (below dealer)
        Mob current = dealer;
        while (current.getVehicle() instanceof Mob) {
            current = (Mob) current.getVehicle();
            JockeyNode node = new JockeyNode(current, position, false);
            jockeys.add(node);
            
            // Set up parent-child relationship without mounting
            JockeyNode parent = jockeys.get(position - 1);
            node.setParent(parent);
            parent.setChild(node);
            position++;
        }
        
        // Then handle passengers (above dealer)
        current = dealer;
        while (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
            current = (Mob) current.getPassengers().get(0);
            JockeyNode node = new JockeyNode(current, position, false);
            jockeys.add(node);
            
            // Set up parent-child relationship without mounting
            JockeyNode parent = jockeys.get(position - 1);
            node.setParent(parent);
            parent.setChild(node);
            position++;
        }
        
        // Start the looking behavior for the stack
        startStackLooking();
    }

    public void refresh() {
        // Clear existing jockeys list
        jockeys.clear();
        
        // Add dealer as first node
        JockeyNode dealerNode = new JockeyNode(dealer, 0, false);
        jockeys.add(dealerNode);
        head = dealerNode;
        
        // Build the stack structure without mounting
        int position = 1;
        
        // First handle vehicles (below dealer)
        Mob current = dealer;
        while (current.getVehicle() instanceof Mob) {
            current = (Mob) current.getVehicle();
            JockeyNode node = new JockeyNode(current, position, false);
            jockeys.add(node);
            
            // Set up parent-child relationship without mounting
            JockeyNode parent = jockeys.get(position - 1);
            node.setParent(parent);
            parent.setChild(node);
            position++;
        }
        
        // Then handle passengers (above dealer)
        current = dealer;
        while (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
            current = (Mob) current.getPassengers().get(0);
            JockeyNode node = new JockeyNode(current, position, false);
            jockeys.add(node);
            
            // Set up parent-child relationship without mounting
            JockeyNode parent = jockeys.get(position - 1);
            node.setParent(parent);
            parent.setChild(node);
            position++;
        }
    }

    private void startStackLooking() {
        // Cancel any existing tasks
        if (lookTasks.containsKey(dealer.getUniqueId())) {
            lookTasks.get(dealer.getUniqueId()).cancel();
            lookTasks.remove(dealer.getUniqueId());
        }

        // Add a shorter delay before starting the task to ensure dealer is fully initialized
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(JockeyManager.class), () -> {
            // Check if dealer is still valid before starting task
            if (!dealer.isValid() || dealer.isDead()) {
                return;
            }

            // Verify the stack is properly mounted
            boolean stackValid = true;
            Mob current = dealer;
            while (current.getVehicle() instanceof Mob) {
                current = (Mob) current.getVehicle();
            }
            while (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
                if (current.getPassengers().isEmpty()) {
                    stackValid = false;
                    break;
                }
                current = (Mob) current.getPassengers().get(0);
            }

            if (!stackValid) {
                // If stack is not valid, try again in a few ticks
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(JockeyManager.class), 
                    () -> startStackLooking(), 5L);
                return;
            }

            BukkitTask task = new BukkitRunnable() {
                private Entity currentTarget = null;
                private float bodyYaw = dealer.getLocation().getYaw();
                private float headYaw = bodyYaw;
                private float headPitch = 0;
                private static final float MIN_TURN_SPEED = 0.5f;
                private static final float MAX_TURN_SPEED = 4.0f;
                private static final float SMOOTH_FACTOR = 0.2f;
                private int targetLockTime = 0;
                private static final int TARGET_LOCK_DURATION = 175;
                private boolean idleMode = false;
                private float idleYaw = bodyYaw;
                private boolean hasLoggedFirstRun = false;

                @Override
                public void run() {
                    if (!hasLoggedFirstRun) {
                        hasLoggedFirstRun = true;
                    }

                    if (dealer.isDead() || !dealer.isValid()) {
                        cancel();
                        lookTasks.remove(dealer.getUniqueId());
                        return;
                    }

                    // Update target or mode
                    if (targetLockTime <= 0) {
                        if (random.nextDouble() < 0.05) {
                            idleMode = true;
                            idleYaw = random.nextFloat() * 360.0f;
                        } else {
                            idleMode = false;
                            currentTarget = (random.nextDouble() < 0.1) ? getNearbyMob() : getNearestPlayer();
                        }
                        targetLockTime = TARGET_LOCK_DURATION;
                    }
                    targetLockTime--;

                    // Calculate target rotation
                    float targetBodyYaw;
                    float targetHeadYaw;
                    float targetPitch = 0;

                    if (idleMode) {
                        targetBodyYaw = idleYaw;
                        targetHeadYaw = idleYaw;
                    } else if (currentTarget != null) {
                        Location dealerLoc = dealer.getLocation();
                        Location targetLoc = currentTarget.getLocation();
                        
                        // Calculate body yaw (horizontal angle)
                        double dx = targetLoc.getX() - dealerLoc.getX();
                        double dz = targetLoc.getZ() - dealerLoc.getZ();
                        targetBodyYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        
                        // Calculate head pitch (vertical angle)
                        double dy = targetLoc.getY() - (dealerLoc.getY() + 1.6); // Add eye height
                        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
                        
                        // Head can look slightly to the sides (within 45 degrees of body)
                        targetHeadYaw = targetBodyYaw;
                    } else {
                        return;
                    }

                    // Smoothly interpolate rotations
                    float bodyDiff = ((targetBodyYaw - bodyYaw + 540) % 360) - 180;
                    float headDiff = ((targetHeadYaw - headYaw + 540) % 360) - 180;
                    //float pitchDiff = targetPitch - headPitch;

                    float turnSpeed = Math.max(MIN_TURN_SPEED, 
                        Math.max(Math.abs(bodyDiff), Math.abs(headDiff)) * SMOOTH_FACTOR);
                    turnSpeed = Math.min(turnSpeed, MAX_TURN_SPEED);

                    bodyYaw = lerpAngle(bodyYaw, targetBodyYaw, turnSpeed);
                    headYaw = lerpAngle(headYaw, targetHeadYaw, turnSpeed);
                    headPitch = lerpAngle(headPitch, targetPitch, turnSpeed);

                    // Apply rotations to the entire stack
                    Location loc = dealer.getLocation();
                    loc.setYaw(bodyYaw);
                    dealer.teleport(loc);
                    
                    // Update head rotations for all entities in the stack
                    for (JockeyNode jockey : jockeys) {
                        Mob mob = jockey.getMob();
                        mob.setRotation(bodyYaw, headPitch);
                    }
                }

                private Entity getNearbyMob() {
                    double maxDistance = 20.0;
                    return dealer.getWorld().getNearbyEntities(dealer.getLocation(), maxDistance, maxDistance, maxDistance)
                        .stream()
                        .filter(e -> e instanceof Mob && !jockeys.stream().anyMatch(j -> j.getMob().equals(e)))
                        .min((e1, e2) -> Double.compare(
                            e1.getLocation().distance(dealer.getLocation()),
                            e2.getLocation().distance(dealer.getLocation())))
                        .orElse(null);
                }

                private Entity getNearestPlayer() {
                    double maxDistance = 20.0;
                    return dealer.getWorld().getNearbyEntities(dealer.getLocation(), maxDistance, maxDistance, maxDistance)
                        .stream()
                        .filter(e -> e instanceof org.bukkit.entity.Player)
                        .min((e1, e2) -> Double.compare(
                            e1.getLocation().distance(dealer.getLocation()),
                            e2.getLocation().distance(dealer.getLocation())))
                        .orElse(null);
                }

                private float lerpAngle(float current, float target, float speed) {
                    float diff = ((target - current + 540) % 360) - 180;
                    return current + Math.min(Math.max(diff, -speed), speed);
                }
            }.runTaskTimer(JavaPlugin.getProvidingPlugin(JockeyManager.class), 0L, 1L);

            lookTasks.put(dealer.getUniqueId(), task);
        }, 5L); // Reduced from 20L to 5L ticks
    }

    public JockeyNode addJockey(Mob mob, int position) {
        if (position <= 0 || position > jockeys.size() + 1) {
            return null;
        }

        JockeyNode newNode = new JockeyNode(mob, position, true); // This is a new jockey
        
        // If adding at an existing position, shift all subsequent jockeys up
        if (position <= jockeys.size()) {
            for (int i = jockeys.size() - 1; i >= position; i--) {
                JockeyNode jockey = jockeys.get(i);
                jockey.setPosition(i + 1);
            }
        }

        // Find the current topmost jockey
        JockeyNode parent = findTopmostJockey();
        
        // Only mount if the parent is not the same entity
        if (parent.getMob() != mob) {
            newNode.mountOn(parent);
        }
        
        // Add the new node to our list at the correct position
        jockeys.add(newNode);

        // Restart looking behavior to include the new jockey
        startStackLooking();

        return newNode;
    }

    public boolean removeJockey(int position) {
        if (position <= 0 || position >= jockeys.size()) {
            return false;
        }

        JockeyNode toRemove = jockeys.get(position);
        JockeyNode parent = toRemove.getParent();
        JockeyNode child = toRemove.getChild();

        // Cancel looking task for the removed jockey
        UUID mobId = toRemove.getMob().getUniqueId();
        if (lookTasks.containsKey(mobId)) {
            lookTasks.get(mobId).cancel();
            lookTasks.remove(mobId);
        }

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

        // Restart looking behavior for the remaining stack
        startStackLooking();

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
        return jockeys.size() - 1; // Exclude dealer
    }

    public int getVehicleCount() {
        int count = 0;
        Mob current = dealer;
        while (current.getVehicle() instanceof Mob) {
            count++;
            current = (Mob) current.getVehicle();
        }
        return count;
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

    public void cleanup() {
        // Cancel all looking tasks for this stack
        for (JockeyNode jockey : jockeys) {
            UUID mobId = jockey.getMob().getUniqueId();
            if (lookTasks.containsKey(mobId)) {
                lookTasks.get(mobId).cancel();
                lookTasks.remove(mobId);
            }
        }
    }

    public Mob getDealer() {
        return dealer;
    }
} 