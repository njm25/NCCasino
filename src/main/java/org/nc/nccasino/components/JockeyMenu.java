package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


public class JockeyMenu extends Menu {
    @SuppressWarnings("unused")
    private UUID dealerId;
    private final Nccasino plugin;
    private final String returnName;
    private Mob dealer;
    private JockeyManager jockeyManager;
    public static final Map<UUID, JockeyMenu> jockeyInventories = new HashMap<>();
    public static final Map<UUID, Consumer<Player>> returnCallbacks = new HashMap<>();
    private Map<Integer, JockeyNode> slotToJockeyMap;
    private boolean deleteMode = false;


    public JockeyMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 54, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
        this.slotToJockeyMap = new HashMap<>();
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());
        
        // Create a fresh JockeyManager to ensure we have the current state
        this.jockeyManager = new JockeyManager(dealer);
        
        slotMapping.put(SlotOption.EXIT, 53);
        slotMapping.put(SlotOption.RETURN, 45);
        slotMapping.put(SlotOption.ADD_JOCKEY, 48);
        slotMapping.put(SlotOption.ADD_PASSENGER, 49);
        slotMapping.put(SlotOption.REMOVE_JOCKEY, 50);
        jockeyInventories.put(this.ownerId, this);
        returnCallbacks.put(this.ownerId, ret);
        
        // Add a small delay before initializing the menu
        new BukkitRunnable() {
            @Override
            public void run() {
                initializeMenu();
            }
        }.runTaskLater(plugin, 2L);
    }

    @Override
    public void initializeMenu() {
        inventory.clear();
        // Clear the jockey mapping
        slotToJockeyMap.clear();

        // Add standard menu items
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.SADDLE, 1, "Add Vehicle", slotMapping.get(SlotOption.ADD_JOCKEY), "Add a new mob directly below dealer");
        addItemAndLore(Material.LEAD, 1, "Add Jockey", slotMapping.get(SlotOption.ADD_PASSENGER), "Add a new mob to the top of the stack");
        
        // Update the remove jockey button based on delete mode
        if (deleteMode) {
            addItemAndLore(Material.BARRIER, 1, "Delete Mode: ON", slotMapping.get(SlotOption.REMOVE_JOCKEY), 
                "Click to exit delete mode",
                "Click any jockey to delete it");
        } else {
            addItemAndLore(Material.BARRIER, 1, "Delete Mode: OFF", slotMapping.get(SlotOption.REMOVE_JOCKEY), 
                "Click to enter delete mode",
                "When active, click jockeys to delete them");
        }

           // Count vehicles and passengers
           int vehicleCount = 0;
           int passengerCount = 0;
           Mob currentMob = dealer;
           
           // Count vehicles (below dealer)
           while (currentMob.getVehicle() instanceof Mob) {
               vehicleCount++;
               currentMob = (Mob) currentMob.getVehicle();
           }
           
        // Reset currentMob for passenger counting
           currentMob = dealer;
           while (!currentMob.getPassengers().isEmpty() && currentMob.getPassengers().get(0) instanceof Mob) {
               passengerCount++;
               currentMob = (Mob) currentMob.getPassengers().get(0);
           }
   
           // Calculate dealer position (default in third row, center)
           int dealerSlot = 22; // Default center position in third row
           if (vehicleCount >= 23) {
               // Move dealer right based on how many vehicles beyond 23
               int shiftAmount = vehicleCount - 22; // Allow shifting all the way to slot 44
               dealerSlot = 22 + shiftAmount;
           } else if (passengerCount >= 23) {
               // Move dealer left based on how many passengers beyond 23
               int shiftAmount = passengerCount - 22; // Allow shifting all the way to slot 0
               dealerSlot = 22 - shiftAmount;
           }
   
           // Add dealer representation
        addItemAndLore(
            Material.SPAWNER,
            1,
            "Dealer",
            dealerSlot,
            "Type: §a" + dealer.getType().name(),
            "This is the base dealer"
        );

        //System.out.println("\n=== MENU DEBUG ===");
        //System.out.println("Dealer: " + dealer.getType());
        
        //System.out.println("Passenger Count: " + passengerCount);
        //System.out.println("Vehicle Count: " + vehicleCount);
        // Display jockeys (below dealer)
        currentMob = dealer;
        int jockeySlot = dealerSlot - 1; // Start left of dealer
        while (currentMob.getVehicle() instanceof Mob) {
            Mob jockeyMob = (Mob)currentMob.getVehicle();
            //System.out.println("Found jockey: " + jockeyMob.getType());
            
            Material spawnEgg = MobSelectionMenu.getSpawnEggFor(jockeyMob.getType());
            String customName = jockeyMob.getCustomName() != null ? jockeyMob.getCustomName() : "Jockey";
            
            addItemAndLore(
                spawnEgg,
                1,
                customName,
                jockeySlot,
                "Type: §a" + jockeyMob.getType().name(),
                "Role: §eJockey",
                "Click to customize"
            );
            
            // Map this slot to the jockey node
            JockeyNode node = findNodeForMob(jockeyMob);
            if (node != null) {
                slotToJockeyMap.put(jockeySlot, node);
            }
            
            currentMob = jockeyMob;
            jockeySlot--; // Move left
        }
        
        // Display passengers (above dealer)
        currentMob = dealer;
        int passengerSlot = dealerSlot + 1; // Start right of dealer
        while (!currentMob.getPassengers().isEmpty() && currentMob.getPassengers().get(0) instanceof Mob) {
            Mob passengerMob = (Mob)currentMob.getPassengers().get(0);
            //System.out.println("Found passenger: " + passengerMob.getType());
            
            Material spawnEgg = MobSelectionMenu.getSpawnEggFor(passengerMob.getType());
            String customName = passengerMob.getCustomName() != null ? passengerMob.getCustomName() : "Passenger";
            
            addItemAndLore(
                spawnEgg,
                1,
                customName,
                passengerSlot,
                "Type: §a" + passengerMob.getType().name(),
                "Role: §ePassenger",
                "Click to customize"
            );
            
            // Map this slot to the jockey node
            JockeyNode node = findNodeForMob(passengerMob);
            if (node != null) {
                slotToJockeyMap.put(passengerSlot, node);
            }
            
            currentMob = passengerMob;
            passengerSlot++; // Move right
        }
        
        //System.out.println("=== END MENU DEBUG ===\n");
    }

    ////////////doesnt this trigger if theres diff mobs of the same type
    private JockeyNode findNodeForMob(Mob mob) {
        for (JockeyNode node : jockeyManager.getJockeys()) {
            if (node.getMob().equals(mob)) {
                return node;
            }
        }
        return null;
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case ADD_JOCKEY:
                handleAddJockey(player, false);
                break;
            case ADD_PASSENGER:
                handleAddJockey(player, true);
                break;
            case REMOVE_JOCKEY:
                handleRemoveJockeyToggle(player);
                break;
            default:
                break;
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        // First check if it's a mapped slot option
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option != null) {
            super.handleClick(slot, player, event);
            return;
        }

        // If not a mapped slot, check if it's a jockey slot
        JockeyNode clickedJockey = slotToJockeyMap.get(slot);
        if (clickedJockey != null) {
            if (deleteMode) {
                handleJockeyDeletion(player, clickedJockey);
            } else {
            handleJockeyOptions(player, clickedJockey);
            }
        }
    }

    private void handleAddJockey(Player player, boolean asPassenger) {

        int vehicleCount = 0;
        int passengerCount = 0;
        Mob currentMob = dealer;
        
        // Count vehicles (below dealer)
        while (currentMob.getVehicle() instanceof Mob) {
            vehicleCount++;
            currentMob = (Mob) currentMob.getVehicle();
        }
        
        // Count passengers (above dealer)
        currentMob = dealer;
        while (!currentMob.getPassengers().isEmpty() && currentMob.getPassengers().get(0) instanceof Mob) {
            passengerCount++;
            currentMob = (Mob) currentMob.getPassengers().get(0);
        }

        // Check if we've reached the maximum total limit
        int totalCount = vehicleCount + passengerCount;
        if (totalCount >= 44) {
            player.sendMessage("§cMaximum total number of vehicles and passengers (44) reached!");
            return;
        }

        // Check if we've reached the maximum limit for the specific type
        if (asPassenger && passengerCount >= 44) {
            player.sendMessage("§cMaximum number of passengers (44) reached!");
            return;
        } else if (!asPassenger && vehicleCount >= 44) {
            player.sendMessage("§cMaximum number of vehicles (44) reached!");
            return;
        }

        // Show mob selection menu first
        JockeyMobMenu mobMenu = new JockeyMobMenu(
            player, 
            plugin, 
            this.jockeyManager,
            null, 
            "Jockey Menu",
            (p) -> {
                // After vehicle is selected, if this is the first vehicle and not a passenger
                if (jockeyManager.getJockeyCount() == 0 && !asPassenger) {
                    // Get the last selected mob type
                    EntityType selectedType = JockeyMobMenu.getLastSelectedType(player);
                    if (selectedType != null) {
                        // Show first vehicle menu
                        FirstVehicleMenu firstVehicleMenu = new FirstVehicleMenu(
                            player,
                            plugin,
                            jockeyManager,
                            "Jockey Menu",
                            returnCallbacks.get(this.ownerId), // Use the original callback directly
                            selectedType
                        );
                        player.openInventory(firstVehicleMenu.getInventory());
                        return;
                    }
                }
                
                // Refresh the jockey manager to ensure state is in sync
                jockeyManager.refresh();
                
                // Otherwise return to jockey menu
                if (jockeyInventories.containsKey(player.getUniqueId())) {
                    player.openInventory(jockeyInventories.get(player.getUniqueId()).getInventory());
                }
            },
            asPassenger
        );
        player.openInventory(mobMenu.getInventory());
    }

    private void handleRemoveJockeyToggle(Player player) {
        deleteMode = !deleteMode;
        
        // Send feedback to player
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD -> player.sendMessage(deleteMode ? "§aDelete mode enabled." : "§aDelete mode disabled.");
            case VERBOSE -> player.sendMessage(deleteMode ? "§aDelete mode enabled. Click any jockey to delete it." : "§aDelete mode disabled.");
            default -> {}
        }

        // Update the menu to reflect the new state
        initializeMenu();
        playDefaultSound(player);
    }

    @SuppressWarnings("unused")
    private void handleJockeyDeletion(Player player, JockeyNode jockey) {
        // Get the position of this jockey in the stack
        int position = jockey.getPosition();

        // Store the absolute bottom location BEFORE any modifications
        Mob bottomMost = jockeyManager.getDealer();
        while (bottomMost.getVehicle() instanceof Mob) {
            bottomMost = (Mob) bottomMost.getVehicle();
        }
        final Location bottomLocation = bottomMost.getLocation().clone();
        final float bottomYaw = bottomMost.getLocation().getYaw();

        // Remove any existing armor stand
        final ArmorStand armorStand;
        final String armorStandName;
        {
            ArmorStand tempArmorStand = null;
            String tempArmorStandName = null;
            for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                if (passenger instanceof ArmorStand) {
                    tempArmorStand = (ArmorStand) passenger;
                    tempArmorStandName = tempArmorStand.getCustomName();
                    tempArmorStand.remove();
                    break;
                }
            }
            armorStand = tempArmorStand;
            armorStandName = tempArmorStandName;
        }

        // Build the complete stack list in order: bottom vehicles -> dealer -> passengers
        List<Mob> allMobs = new ArrayList<>();
        
        // First add vehicles from bottom up
        Mob current = bottomMost;
        while (current != null) {
            allMobs.add(current);
            if (current.getUniqueId().equals(jockeyManager.getDealer().getUniqueId())) {
                break;
            }
            if (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
                current = (Mob) current.getPassengers().get(0);
            } else {
                break;
            }
        }
        
        // Then add passengers
        current = jockeyManager.getDealer();
        while (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
            current = (Mob) current.getPassengers().get(0);
            allMobs.add(current);
        }

        // Find our target mob's index
        int jockeyIndex = -1;
        for (int i = 0; i < allMobs.size(); i++) {
            if (allMobs.get(i).getUniqueId().equals(jockey.getMob().getUniqueId())) {
                jockeyIndex = i;
                break;
            }
        }

        if (jockeyIndex == -1) {
            player.sendMessage("§cFailed to find jockey in stack");
            return;
        }

        // Check if we're deleting the bottom mob
        boolean isDeletingBottom = jockeyIndex == 0;

        // First remove from JockeyManager to update relationships
        jockeyManager.removeJockey(position);

        // Then unmount everything in the stack
        for (Mob mob : allMobs) {
            if (mob.getVehicle() != null) {
                mob.leaveVehicle();
            }
            for (Entity passenger : new ArrayList<>(mob.getPassengers())) {
                mob.removePassenger(passenger);
            }
        }

        // Remove the jockey from our list and from the world
        allMobs.remove(jockeyIndex);
        jockey.getMob().remove();

        if (!allMobs.isEmpty()) {
            // If we deleted the bottom mob, we need to ensure the new bottom mob gets to the right spot
            if (isDeletingBottom) {
                Mob newBottom = allMobs.get(0);
                
                // Force the new bottom mob to the correct location
                newBottom.teleport(bottomLocation);
                newBottom.setRotation(bottomYaw, 0);
                
                // Wait a tick to ensure teleport is processed
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Double-check position and force if needed
                        if (!newBottom.getLocation().equals(bottomLocation)) {
                            newBottom.teleport(bottomLocation);
                            newBottom.setRotation(bottomYaw, 0);
                        }
                        
                        // Wait another tick before repositioning the rest
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // Reposition all mobs starting from the bottom
                                for (int i = 0; i < allMobs.size(); i++) {
                                    Mob mob = allMobs.get(i);
                                    Location newLoc;
                                    if (i == 0) {
                                        // First mob always goes to original bottom location
                                        newLoc = bottomLocation.clone();
                                    } else {
                                        // Other mobs stack on top of the previous mob
                                        Mob below = allMobs.get(i - 1);
                                        newLoc = below.getLocation().clone();
                                        newLoc.add(0, below.getHeight(), 0);
                                    }
                                    mob.teleport(newLoc);
                                    mob.setRotation(bottomYaw, 0);
                                }

                                // Wait another tick before remounting
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        // Remount everything in order from bottom to top
                                        for (int i = 0; i < allMobs.size() - 1; i++) {
                                            Mob lower = allMobs.get(i);
                                            Mob upper = allMobs.get(i + 1);
                                            lower.addPassenger(upper);
                                            lower.setCustomNameVisible(false);
                                        }

                                        // Check if we need to respawn the armor stand
                                        boolean hasVehicles = jockeyManager.getDealer().getVehicle() != null;
                                        boolean hasPassengers = false;
                                        for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                                            if (passenger instanceof Mob) {
                                                hasPassengers = true;
                                                break;
                                            }
                                        }

                                        if (hasVehicles && !hasPassengers) {
                                            // Spawn new armor stand at dealer location
                                            ArmorStand newArmorStand = (ArmorStand) jockeyManager.getDealer().getWorld().spawnEntity(bottomLocation, EntityType.ARMOR_STAND);
                                            newArmorStand.setVisible(false);
                                            newArmorStand.setGravity(false);
                                            newArmorStand.setSmall(true);
                                            newArmorStand.setMarker(true);
                                            newArmorStand.setCustomName(armorStandName != null ? armorStandName : jockeyManager.getDealer().getCustomName());
                                            newArmorStand.setCustomNameVisible(true);

                                            // Add armor stand as passenger to dealer
                                            jockeyManager.getDealer().addPassenger(newArmorStand);
                                            jockeyManager.getDealer().setCustomNameVisible(false);
                                        }

                                        // Refresh the jockey manager to ensure all relationships are correct
                                        jockeyManager.refresh();
                                        
                                        // Refresh the menu with a delay to ensure all changes are processed
                                        new BukkitRunnable() {
                                            @Override
                                            public void run() {
            initializeMenu();
                                            }
                                        }.runTaskLater(plugin, 1L);
                                    }
                                }.runTaskLater(plugin, 1L);
                            }
                        }.runTaskLater(plugin, 1L);
                    }
                }.runTaskLater(plugin, 1L);
            } else {
                // For non-bottom deletions, just rebuild the stack
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Reposition all mobs starting from the bottom
                        for (int i = 0; i < allMobs.size(); i++) {
                            Mob mob = allMobs.get(i);
                            Location newLoc;
                            if (i == 0) {
                                // First mob always goes to original bottom location
                                newLoc = bottomLocation.clone();
        } else {
                                // Other mobs stack on top of the previous mob
                                Mob below = allMobs.get(i - 1);
                                newLoc = below.getLocation().clone();
                                newLoc.add(0, below.getHeight(), 0);
                            }
                            mob.teleport(newLoc);
                            mob.setRotation(bottomYaw, 0);
                        }

                        // Wait a tick before remounting
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // Remount everything in order from bottom to top
                                for (int i = 0; i < allMobs.size() - 1; i++) {
                                    Mob lower = allMobs.get(i);
                                    Mob upper = allMobs.get(i + 1);
                                    lower.addPassenger(upper);
                                    lower.setCustomNameVisible(false);
                                }

                                // Check if we need to respawn the armor stand
                                boolean hasVehicles = jockeyManager.getDealer().getVehicle() != null;
                                boolean hasPassengers = false;
                                for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                                    if (passenger instanceof Mob) {
                                        hasPassengers = true;
                                        break;
                                    }
                                }

                                if (hasVehicles && !hasPassengers) {
                                    // Spawn new armor stand at dealer location
                                    ArmorStand newArmorStand = (ArmorStand) jockeyManager.getDealer().getWorld().spawnEntity(bottomLocation, EntityType.ARMOR_STAND);
                                    newArmorStand.setVisible(false);
                                    newArmorStand.setGravity(false);
                                    newArmorStand.setSmall(true);
                                    newArmorStand.setMarker(true);
                                    newArmorStand.setCustomName(armorStandName != null ? armorStandName : jockeyManager.getDealer().getCustomName());
                                    newArmorStand.setCustomNameVisible(true);

                                    // Add armor stand as passenger to dealer
                                    jockeyManager.getDealer().addPassenger(newArmorStand);
                                    jockeyManager.getDealer().setCustomNameVisible(false);
                                }

                                // Refresh the jockey manager to ensure all relationships are correct
                                jockeyManager.refresh();
                                
                                // Refresh the menu with a delay to ensure all changes are processed
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        initializeMenu();
                                    }
                                }.runTaskLater(plugin, 1L);
                            }
                        }.runTaskLater(plugin, 1L);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }

        // Send feedback to player
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD -> player.sendMessage("§aJockey deleted.");
            case VERBOSE -> player.sendMessage("§aJockey " + ChatColor.YELLOW + jockey.getMob().getType().name() + "§a deleted.");
            default -> {}
        }
    }

    private void handleJockeyOptions(Player player, JockeyNode jockey) {
        JockeyOptionsMenu optionsMenu = new JockeyOptionsMenu(
            player,
            plugin,
            jockey,
            Dealer.getInternalName(dealer) + "'s Jockey Menu",
            (p) -> {
                if (jockeyInventories.containsKey(player.getUniqueId())) {
                    JockeyMenu temp=(JockeyMenu)JockeyMenu.jockeyInventories.get(player.getUniqueId());
                    temp.initializeMenu();
                    player.openInventory(jockeyInventories.get(player.getUniqueId()).getInventory());
                }
            }
        );
        player.openInventory(optionsMenu.getInventory());
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        jockeyInventories.remove(ownerId);
        returnCallbacks.remove(ownerId);
        this.delete();
    }

    public void refreshJockeyManager() {
        // Create a fresh JockeyManager to ensure we have the current state
        this.jockeyManager = new JockeyManager(dealer);
    }
} 