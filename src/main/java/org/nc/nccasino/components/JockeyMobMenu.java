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
            // Set different titles
            (jockeyManager.getJockeyCount() == 0 && !asPassenger) ? "Select Vehicle Mob" :
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
                    // Return to mobSettingsMenu
                    if (MobSettingsMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                        player.openInventory(MobSettingsMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                    } else if (returnCallback != null) {
                        returnCallback.accept(player);
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
                    cleanup();
                    jockeyManager.refresh();

                    // Return to mobSettingsMenu after selecting passenger
                    if (MobSettingsMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                        MobSettingsMenu temp=(MobSettingsMenu)MobSettingsMenu.jockeyInventories.get(player.getUniqueId());
                        temp.initializeMenu();
                        player.openInventory(MobSettingsMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                     
                    } else if (returnCallback != null) {
                        returnCallback.accept(player);
                    }
                } else {
                    // Check if this is the first vehicle (no jockeys yet)
                    if (jockeyManager.getJockeyCount() == 0) {
                        // Get dealer location
                        Mob dealer = jockeyManager.getDealer();
                        Location dealerLoc = dealer.getLocation().clone();
                        float yaw = dealerLoc.getYaw();
                        
                        // Spawn vehicle at dealer location
                        Mob vehicle = (Mob) dealer.getWorld().spawnEntity(dealerLoc, selectedType);
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
                        
                        // Since this is the first vehicle and there are no passengers, add an armor stand
                        ArmorStand armorStand = (ArmorStand) dealer.getWorld().spawnEntity(dealerLoc, EntityType.ARMOR_STAND);
                        armorStand.setVisible(false);
                        armorStand.setGravity(false);
                        armorStand.setInvulnerable(true);
                        armorStand.setSmall(true);
                        armorStand.setMarker(true);
                        armorStand.setCustomNameVisible(true);
                        armorStand.setCustomName(dealer.getCustomName());
                        dealer.setCustomNameVisible(false);
                        dealer.addPassenger(armorStand);
                        
                        // Add to jockey manager's list
                        jockeyManager.getJockeys().add(vehicleNode);
                        
                        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                            case STANDARD -> player.sendMessage("§aAdded vehicle.");
                            case VERBOSE -> player.sendMessage("§aAdded vehicle: " + ChatColor.YELLOW + formatEntityName(selectedType.name()) + "§a.");
                            default -> {}
                        }
                        
                        cleanup();
                        jockeyManager.refresh();

                        // Return to mobSettingsMenu
                        if (MobSettingsMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                            MobSettingsMenu temp=(MobSettingsMenu)MobSettingsMenu.jockeyInventories.get(player.getUniqueId());
                            temp.initializeMenu();
                            player.openInventory(MobSettingsMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
                        } else if (returnCallback != null) {
                            returnCallback.accept(player);
                        }
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

                    // Return to mobSettingsMenu
                    if (MobSettingsMenu.jockeyInventories.containsKey(player.getUniqueId())) {
                        MobSettingsMenu temp=(MobSettingsMenu)MobSettingsMenu.jockeyInventories.get(player.getUniqueId());
                        temp.initializeMenu();
                        player.openInventory(MobSettingsMenu.jockeyInventories.get(player.getUniqueId()).getInventory());
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