package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;

import java.util.*;
import java.util.function.Consumer;

public class JockeyMobMenu extends Menu {
    private final Nccasino plugin;
    private final String returnName;
    private final JockeyManager jockeyManager;
    private final JockeyNode targetJockey;
    private final boolean asPassenger;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
    public static final Map<UUID, JockeyMobMenu> jockeyMobInventories = new HashMap<>();
    public static final Map<UUID, EntityType> lastSelectedType = new HashMap<>();
    public static final Map<UUID, EntityType> vehicleTypeMap = new HashMap<>();
    
    private static final Map<Material, EntityType> spawnEggToEntity = new HashMap<>();
    private static final Map<EntityType, Material> entityToSpawnEgg = new HashMap<>();
    private static final List<Material> spawnEggList = new ArrayList<>();

    private final Consumer<Player> returnCallback;
    private int totalPages;
    private Map<Integer, EntityType> slotToEntityType = new HashMap<>();

    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                try {
                    String entityName = material.name().replace("_SPAWN_EGG", "");
                    EntityType entityType = EntityType.valueOf(entityName);
    
                    // Exclude Wither & Ender Dragon
                    if (entityType != EntityType.ENDER_DRAGON && entityType != EntityType.WITHER) {
                        spawnEggToEntity.put(material, entityType);
                        spawnEggList.add(material);
                        entityToSpawnEgg.put(entityType, material);
                    }
                } catch (IllegalArgumentException ignored) {
                    // If material name doesn't match an EntityType, skip
                }
            }
        }

        // Sort alphabetically by entity name
        spawnEggList.sort((m1, m2) -> {
            String name1 = formatEntityName(spawnEggToEntity.get(m1).name());
            String name2 = formatEntityName(spawnEggToEntity.get(m2).name());
            return name1.compareToIgnoreCase(name2);
        });
    }

    private static String formatEntityName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    public JockeyMobMenu(Player player, Nccasino plugin, JockeyManager jockeyManager, JockeyNode targetJockey, String returnName, Consumer<Player> returnCallback, boolean asPassenger) {
        super(player, plugin, jockeyManager.getDealer().getUniqueId(), 
            // Set different titles for first vehicle flow
            (jockeyManager.getJockeyCount() == 0 && !asPassenger) ? "Select Vehicle Mob" :
            (returnName.equals("First Vehicle Menu")) ? "Select Passenger Mob" :
            "Select Jockey Mob", 
            54, returnName, returnCallback);
        this.plugin = plugin;
        this.returnName = returnName;
        this.jockeyManager = jockeyManager;
        this.targetJockey = targetJockey;
        this.asPassenger = asPassenger;
        this.returnCallback = returnCallback;
        
        slotMapping.put(SlotOption.EXIT, 53);
        slotMapping.put(SlotOption.RETURN, 45);
        slotMapping.put(SlotOption.PAGE_TOGGLE, 49);
        jockeyMobInventories.put(this.ownerId, this);
        
        // Calculate total pages
        totalPages = (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE);
        
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        inventory.clear();
        slotToEntityType.clear();
        int startIndex = (currentPage - 1) * PAGE_SIZE;

        // Populate spawn eggs
        for (int i = 0; i < PAGE_SIZE && startIndex + i < spawnEggList.size(); i++) {
            Material material = spawnEggList.get(startIndex + i);
            EntityType entityType = spawnEggToEntity.get(material);
            int slot = i;

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + formatEntityName(entityType.name()));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            slotToEntityType.put(slot, entityType);
        }

        // Add navigation buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Page toggle button
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if ((startIndex + PAGE_SIZE) < spawnEggList.size()) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }
    }

    @SuppressWarnings("unused")
    private void returnToJockeyMenu(Player player) {
        // Schedule the return with a delay to ensure proper updates
        new BukkitRunnable() {
            @Override
            public void run() {
                // First update the JockeyMenu if it exists
                JockeyMenu jockeyMenu = JockeyMenu.jockeyInventories.get(player.getUniqueId());
                if (jockeyMenu != null) {
                    jockeyMenu.initializeMenu();
                }
                
                // Then return to it
                if (returnCallback != null) {
                    returnCallback.accept(player);
                }
            }
        }.runTaskLater(plugin, 2L); // 2 tick delay (0.1 seconds)
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        // First check if it's a mapped slot option
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option != null) {
            switch (option) {
                case PAGE_TOGGLE:
                    if (currentPage > 1) {
                        currentPage--;
                    } else if (currentPage < totalPages) {
                        currentPage++;
                    }
                    initializeMenu();
                    playDefaultSound(player);
                    return;
                case EXIT:
                    player.closeInventory();
                    cleanup();
                    return;
                case RETURN:
                    cleanup();
                    // If this is a passenger selection menu, return to FirstVehicleMenu
                    if (asPassenger && returnName.equals("First Vehicle Menu")) {
                        // Return to FirstVehicleMenu with the stored vehicle type
                        FirstVehicleMenu firstVehicleMenu = new FirstVehicleMenu(
                            player,
                            plugin,
                            jockeyManager,
                            "Jockey Menu",
                            (p) -> {
                                // Return to JockeyMenu
                                if (JockeyMenu.jockeyInventories.containsKey(p.getUniqueId())) {
                                    p.openInventory(JockeyMenu.jockeyInventories.get(p.getUniqueId()).getInventory());
                                } else if (returnCallback != null) {
                                    returnCallback.accept(p);
                                }
                            },
                            vehicleTypeMap.get(player.getUniqueId())
                        );
                        player.openInventory(firstVehicleMenu.getInventory());
                    } else {
                        // If returning to first JockeyMobMenu, clear the stored vehicle type
                        if (!asPassenger && jockeyManager.getJockeyCount() == 0) {
                            vehicleTypeMap.remove(player.getUniqueId());
                        }
                        // Return to JockeyMenu
                        if (JockeyMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                            player.openInventory(JockeyMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                        } else if (returnCallback != null) {
                            returnCallback.accept(player);
                        }
                    }
                    return;
                default:
                    break;
            }
            return;
        }

        // If not a mapped slot, check if it's an entity selection slot
        EntityType selectedType = slotToEntityType.get(slot);
        if (selectedType != null) {
            // Store the selected type
            lastSelectedType.put(player.getUniqueId(), selectedType);
            
            // Get the dealer's name to use as base for custom names
            String dealerName = jockeyManager.getDealer().getCustomName();
            if (dealerName == null || dealerName.isEmpty()) {
                dealerName = "Dealer";
            }
            
            if (targetJockey != null) {
                // Editing existing jockey
                Mob newMob = (Mob) player.getWorld().spawnEntity(player.getLocation(), selectedType);
                newMob.setAI(false);
                newMob.setPersistent(true);
                newMob.setRemoveWhenFarAway(false);
                newMob.setGravity(false);
                newMob.setSilent(true);
                newMob.setCollidable(false);
                newMob.setInvulnerable(true);
                
                targetJockey.setMob(newMob);
                // Keep the existing custom name when editing
                if (targetJockey.getCustomName() != null) {
                    targetJockey.setCustomName(targetJockey.getCustomName());
                }
                cleanup();
                if (returnCallback != null) {
                    returnCallback.accept(player);
                }
            } else {
                if (asPassenger) {
                    // Get dealer location
                    Mob dealer = jockeyManager.getDealer();
                    Location dealerLoc = dealer.getLocation().clone();
                    float yaw = dealerLoc.getYaw();
                    
                    if (returnName.equals("First Vehicle Menu")) {
                        // First vehicle flow - use vehicleTypeMap
                        EntityType vehicleType = vehicleTypeMap.get(player.getUniqueId());
                        if (vehicleType != null) {
                            // Spawn vehicle at dealer location
                            Mob vehicle = (Mob) dealer.getWorld().spawnEntity(dealerLoc, vehicleType);
                            vehicle.setAI(false);
                            vehicle.setPersistent(true);
                            vehicle.setRemoveWhenFarAway(false);
                            vehicle.setGravity(false);
                            vehicle.setSilent(true);
                            vehicle.setCollidable(false);
                            vehicle.setInvulnerable(true);
                            vehicle.teleport(dealerLoc);
                            vehicle.setRotation(yaw, 0);
                            
                            // Create vehicle node
                            JockeyNode vehicleNode = new JockeyNode(vehicle, 1, true);
                            vehicleNode.setCustomName("Vehicle");
                            
                            // Mount dealer on vehicle
                            JockeyNode dealerNode = jockeyManager.getJockey(0);
                            vehicleNode.mountAsVehicle(dealerNode);
                            
                            // Spawn passenger at dealer location
                            Mob newMob = (Mob) dealer.getWorld().spawnEntity(dealerLoc, selectedType);
                            newMob.setAI(false);
                            newMob.setPersistent(true);
                            newMob.setRemoveWhenFarAway(false);
                            newMob.setGravity(false);
                            newMob.setSilent(true);
                            newMob.setCollidable(false);
                            newMob.setInvulnerable(true);
                            newMob.teleport(dealerLoc);
                            newMob.setRotation(yaw, 0);
                            
                            // Add passenger to dealer
                            JockeyNode passengerNode = new JockeyNode(newMob, 2, true);
                            passengerNode.setCustomName(dealerName);
                            passengerNode.mountOn(dealerNode);
                            
                            // Update name visibility
                            dealer.setCustomNameVisible(false);
                            vehicle.setCustomNameVisible(false);
                            newMob.setCustomNameVisible(false);
                            
                            // Add to jockey manager's list
                            List<JockeyNode> jockeys = jockeyManager.getJockeys();
                            jockeys.add(vehicleNode);
                            jockeys.add(passengerNode);
                            
                            player.sendMessage("§aAdded vehicle and passenger");
                        }
                    } else {
                        // Regular passenger addition
                        // Find the topmost jockey
                        Mob topMob = dealer;
                        while (!topMob.getPassengers().isEmpty()) {
                            Entity passenger = topMob.getPassengers().get(0);
                            if (passenger instanceof Mob) {
                                topMob = (Mob) passenger;
                            } else {
                                break; // Stop if we hit a non-Mob entity
                            }
                        }
                        
                        // Check for and remove any armor stands
                        for (Entity passenger : dealer.getPassengers()) {
                            if (passenger instanceof ArmorStand) {
                                passenger.remove();
                                break;
                            }
                        }
                        
                        // Spawn new passenger at dealer location
                        Mob newMob = (Mob) dealer.getWorld().spawnEntity(dealerLoc, selectedType);
                        newMob.teleport(dealerLoc);
                        newMob.setRotation(yaw, 0);
                        newMob.setPersistent(true);
                        newMob.setRemoveWhenFarAway(false);
                        newMob.setGravity(false);
                        newMob.setSilent(true);
                        newMob.setCollidable(false);
                        newMob.setInvulnerable(true);
                        newMob.setAI(false);
                        // Mount the new passenger on top
                        topMob.addPassenger(newMob);
                        newMob.setCustomName(dealerName);
                        newMob.setCustomNameVisible(false);
                        
                        // Hide the name of the jockey it's mounted on
                        topMob.setCustomNameVisible(false);
                        
                        player.sendMessage("§aAdded new passenger: " + formatEntityName(selectedType.name()));
                    }
                    cleanup();
                    jockeyManager.refresh();

                    // Return to JockeyMenu after selecting passenger
                    if (JockeyMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                    //System.out.println("skib1");
                    JockeyMenu temp=(JockeyMenu)JockeyMenu.jockeyInventories.get(player.getUniqueId());
                    temp.initializeMenu();
                        player.openInventory(JockeyMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                     
                    } else if (returnCallback != null) {
                        returnCallback.accept(player);
                    }
                } else {
                    // Check if this is the first vehicle (no jockeys yet)
                    if (jockeyManager.getJockeyCount() == 0) {
                        // Store the vehicle type
                        vehicleTypeMap.put(player.getUniqueId(), selectedType);
                        // Open First Vehicle Menu
                        FirstVehicleMenu firstVehicleMenu = new FirstVehicleMenu(
                            player,
                            plugin,
                            jockeyManager,
                            "Jockey Menu",
                            (p) -> {
                                // Return to JockeyMenu
                                if (JockeyMenu.jockeyInventories.containsKey(p.getUniqueId())) {
                                    p.openInventory(JockeyMenu.jockeyInventories.get(p.getUniqueId()).getInventory());
                                } else if (returnCallback != null) {
                                    returnCallback.accept(p);
                                }
                            },
                            selectedType
                        );
                        cleanup();
                        player.openInventory(firstVehicleMenu.getInventory());
                        return;
                    }
                    
                    // Get the current bottom-most mob in the stack
                    Mob dealer = jockeyManager.getDealer();
                    Mob bott = dealer;
                    while (bott.getVehicle() != null) {
                        bott = (Mob)bott.getVehicle();
                    }
                    
                    // Create new mob at the exact position of current bottom
                    Location bottomLoc = bott.getLocation();
                    Mob newMob = (Mob) dealer.getWorld().spawnEntity(bottomLoc, selectedType);
                    newMob.setAI(false);
                    newMob.setPersistent(true);
                    newMob.setRemoveWhenFarAway(false);
                    newMob.setGravity(false);
                    newMob.setSilent(true);
                    newMob.setCollidable(false);
                    newMob.setInvulnerable(true);
                    newMob.teleport(bottomLoc);
                    newMob.setRotation(bott.getLocation().getYaw(), 0);
                    
                    // Create and set up nodes
                    JockeyNode newJockey = new JockeyNode(newMob, jockeyManager.getJockeyCount() + 1, true);
                    newJockey.setCustomName(dealerName);
                    
                    // Find the bottom node
                    JockeyNode bottomNode = null;
                    for (JockeyNode node : jockeyManager.getJockeys()) {
                        if (node.getMob().equals(bott)) {
                            bottomNode = node;
                            break;
                        }
                    }
                    
                    if (bottomNode != null) {
                        // Use the new mountAsVehicle method
                        newJockey.mountAsVehicle(bottomNode);
                        
                        // Update name visibility
                        newMob.setCustomNameVisible(false);
                        bott.setCustomNameVisible(false);
                    }
                    
                    // Add to jockey manager's list and update positions
                    List<JockeyNode> jockeys = jockeyManager.getJockeys();
                    jockeys.add(newJockey);
                    
                    // Update positions for all jockeys
                    for (JockeyNode jockey : jockeys) {
                        if (jockey.getPosition() > 0) {
                            jockey.setPosition(jockey.getPosition() + 1);
                        }
                    }
                    
                    cleanup();
                    jockeyManager.refresh();

                    // Return to JockeyMenu
                    if (JockeyMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                        JockeyMenu temp=(JockeyMenu)JockeyMenu.jockeyInventories.get(player.getUniqueId());
                        temp.initializeMenu();
                        player.openInventory(JockeyMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                    } else if (returnCallback != null) {
                        returnCallback.accept(player);
                    }
                }
            }
            
            playDefaultSound(player);
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case PAGE_TOGGLE:
                if (currentPage > 1) {
                    currentPage--;
                } else if ((currentPage * PAGE_SIZE) < spawnEggList.size()) {
                    currentPage++;
                }
                initializeMenu();
                break;
            default:
                break;
        }
    }

    public static EntityType getLastSelectedType(Player player) {
        return lastSelectedType.get(player.getUniqueId());
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        jockeyMobInventories.remove(ownerId);
        this.delete();
    }

    @SuppressWarnings("unused")
    private void handleMobSelection(Player player, int slot, InventoryClickEvent event) {
        if (slot >= 0 && slot < 45 && event.getCurrentItem() != null) {
            Material clickedMaterial = event.getCurrentItem().getType();
            EntityType selectedType = spawnEggToEntity.get(clickedMaterial);
            if (selectedType != null) {
                // Calculate the actual index based on the current page
                int actualIndex = (currentPage - 1) * PAGE_SIZE + slot;
                if (actualIndex >= spawnEggList.size()) return;
                
                // Store references before changing anything
                JockeyNode parentNode = targetJockey != null ? targetJockey.getParent() : null;
                JockeyNode childNode = targetJockey != null ? targetJockey.getChild() : null;
                
                // First, detach any child to prevent it from being removed with the old mob
                if (childNode != null) {
                    childNode.unmount();
                }
                
                // Spawn new mob near the parent (temporary location)
                Mob parentMob = parentNode != null ? parentNode.getMob() : jockeyManager.getDealer();
                Mob newMob = (Mob) parentMob.getWorld().spawnEntity(parentMob.getLocation(), selectedType);
                newMob.setAI(false);
                newMob.setPersistent(true);
                newMob.setRemoveWhenFarAway(false);
                newMob.setGravity(false);
                newMob.setSilent(true);
                newMob.setCollidable(false);
                newMob.setInvulnerable(true);
                // Get the dealer's name
                String dealerName = jockeyManager.getDealer().getCustomName();
                if (dealerName == null || dealerName.isEmpty()) {
                    dealerName = "Dealer";
                }
                
                // Update the jockey with the new mob
                if (targetJockey != null) {
                    // Editing existing jockey
                    if (jockeyManager.changeMobType(targetJockey.getPosition(), newMob)) {
                        // Set the dealer's name
                        newMob.setCustomName(dealerName);
                        newMob.setCustomNameVisible(true);
                        
                        // First attach this mob to its parent
                        if (parentNode != null) {
                            parentMob.addPassenger(newMob);
                        }
                        
                        // Then reattach the child if it existed
                        if (childNode != null) {
                            Mob childMob = childNode.getMob();
                            newMob.addPassenger(childMob);
                            childNode.setParent(targetJockey);
                            targetJockey.setChild(childNode);
                        }
                        
                        player.sendMessage("§aChanged jockey to " + formatEntityName(selectedType.name()));
                        playDefaultSound(player);
                        if (returnCallback != null) {
                            returnCallback.accept(player);
                        }
                    } else {
                        player.sendMessage("§cFailed to change jockey");
                        newMob.remove();
                        
                        // If we failed, reattach the child to maintain stack
                        if (childNode != null) {
                            childNode.mountOn(targetJockey);
                        }
                    }
                } else {
                    // Adding new jockey/passenger
                    if (asPassenger) {
                        // Add as passenger (on top of stack)
                        Mob dealer = jockeyManager.getDealer();
                        
                        // Find the topmost jockey
                        Mob topMob = dealer;
                        while (!topMob.getPassengers().isEmpty()) {
                            Entity passenger = topMob.getPassengers().get(0);
                            if (passenger instanceof Mob) {
                                topMob = (Mob) passenger;
                            } else {
                                break; // Stop if we hit a non-Mob entity
                            }
                        }
                        
                        // Check for and remove any armor stands
                        for (Entity passenger : dealer.getPassengers()) {
                            if (passenger instanceof ArmorStand) {
                                passenger.remove();
                                break;
                            }
                        }
                        
                        // Mount the new passenger on top
                        topMob.addPassenger(newMob);
                        newMob.setCustomName(dealerName);
                        newMob.setCustomNameVisible(false);
                        
                        // Hide the name of the jockey it's mounted on
                        topMob.setCustomNameVisible(false);
                        
                        player.sendMessage("§aAdded new passenger: " + formatEntityName(selectedType.name()));
                    } else {
                        // Add as vehicle (below stack)
                        Mob dealer = jockeyManager.getDealer();
                        Mob bottomMob = dealer;
                        while (bottomMob.getVehicle() != null) {
                            bottomMob = (Mob) bottomMob.getVehicle();
                        }
                        
                        // Mount the new vehicle below
                        newMob.addPassenger(bottomMob);
                        newMob.setCustomName(dealerName);
                        newMob.setCustomNameVisible(false);
                        bottomMob.setCustomNameVisible(false);
                    }
                    
                    player.sendMessage("§aAdded new " + (asPassenger ? "passenger" : "vehicle") + ": " + formatEntityName(selectedType.name()));
                    playDefaultSound(player);
                    if (returnCallback != null) {
                        returnCallback.accept(player);
                    }
                }
            }
        }
    }
} 