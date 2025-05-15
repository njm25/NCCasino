package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
        super(player, plugin, jockeyManager.getDealer().getUniqueId(), "Select Jockey Mob", 54, returnName, returnCallback);
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
                    return;
                case RETURN:
                    returnToJockeyMenu(player);
                    return;
                default:
                  
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
                targetJockey.setMob(newMob);
                // Keep the existing custom name when editing
                if (targetJockey.getCustomName() != null) {
                    targetJockey.setCustomName(targetJockey.getCustomName());
                }
            } else {
                if (asPassenger) {
                    // Add as passenger (on top of stack)
                    Mob dealer = jockeyManager.getDealer();
                    Mob newMob = (Mob) dealer.getWorld().spawnEntity(dealer.getLocation(), selectedType);
                    
                    // Find the topmost jockey to add this as a passenger
                    List<JockeyNode> jockeys = jockeyManager.getJockeys();
                    if (!jockeys.isEmpty()) {
                        // Find the topmost jockey by following the passenger chain
                        JockeyNode topJockey = jockeys.get(0); // Start with dealer
                        while (!topJockey.getMob().getPassengers().isEmpty()) {
                            Mob passenger = (Mob)topJockey.getMob().getPassengers().get(0);
                            // Find the node for this passenger
                            for (JockeyNode node : jockeys) {
                                if (node.getMob().equals(passenger)) {
                                    topJockey = node;
                                    break;
                                }
                            }
                        }
                        
                        // Create new jockey node with position after the topmost jockey
                        JockeyNode newJockey = new JockeyNode(newMob, topJockey.getPosition() + 1, true);
                        newJockey.setCustomName(dealerName);
                        
                        // Mount the new passenger on top
                        newJockey.mountOn(topJockey);
                        
                        // Since this is a passenger on top, make its name permanently visible
                        newMob.setCustomNameVisible(true);
                        
                        // Hide the name of the jockey it's mounted on
                        topJockey.getMob().setCustomNameVisible(false);
                        
                        // Remove any existing silverfish
                        for (JockeyNode node : jockeys) {
                            if (node.getMob().getType() == EntityType.SILVERFISH) {
                                node.getMob().remove();
                                break;
                            }
                        }
                        
                        // Add to jockey manager's list and update positions
                        jockeys.add(newJockey);
                        
                        // Update positions for all jockeys
                        for (JockeyNode jockey : jockeys) {
                            if (jockey.getPosition() > 0) {
                                jockey.setPosition(jockey.getPosition() + 1);
                            }
                        }
                    }
                } else {
                    // Check if this is the first vehicle (no jockeys yet)
                    if (jockeyManager.getJockeyCount() == 0) {
                        // Don't spawn anything yet, just return to menu
                        playDefaultSound(player);
                        returnToJockeyMenu(player);
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
                }
            }
            
            playDefaultSound(player);
            returnToJockeyMenu(player);
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
} 