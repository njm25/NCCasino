package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
    private final JockeyNode targetJockey; // null if creating new jockey
    private final boolean isCreatingNew;
    private final boolean asPassenger;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
    public static final Map<UUID, JockeyMobMenu> jockeyMobInventories = new HashMap<>();
    
    private static final Map<Material, EntityType> spawnEggToEntity = new HashMap<>();
    private static final Map<EntityType, Material> entityToSpawnEgg = new HashMap<>();
    private static final List<Material> spawnEggList = new ArrayList<>();

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
        this.isCreatingNew = targetJockey == null;
        this.asPassenger = asPassenger;
        
        slotMapping.put(SlotOption.EXIT, 53);
        slotMapping.put(SlotOption.RETURN, 45);
        slotMapping.put(SlotOption.PAGE_TOGGLE, 49);
        jockeyMobInventories.put(this.ownerId, this);
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        inventory.clear();
        int startIndex = (currentPage - 1) * PAGE_SIZE;

        // Populate spawn eggs
        for (int i = 0; i < PAGE_SIZE && startIndex + i < spawnEggList.size(); i++) {
            Material material = spawnEggList.get(startIndex + i);
            EntityType entityType = spawnEggToEntity.get(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + formatEntityName(entityType.name()));
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        // Navigation buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Page toggle button - always show it if there are multiple pages
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if ((startIndex + PAGE_SIZE) < spawnEggList.size()) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        // First check if it's a navigation button
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option != null) {
            switch (option) {
                case PAGE_TOGGLE:
                    if (currentPage > 1) {
                        currentPage--;
                    } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
                        currentPage++;
                    }
                    initializeMenu();
                    playDefaultSound(player);
                    return;
                case EXIT:
                    player.closeInventory();
                    return;
                case RETURN:
                    executeReturn(player);
                    return;
            }
            return;
        }

        // If not a navigation button, check if it's a spawn egg slot
        if (slot >= 0 && slot < 45 && event.getCurrentItem() != null) {
            Material clickedMaterial = event.getCurrentItem().getType();
            EntityType selectedType = spawnEggToEntity.get(clickedMaterial);
            if (selectedType != null) {
                // Calculate the actual index based on the current page
                int actualIndex = (currentPage - 1) * PAGE_SIZE + slot;
                if (actualIndex >= spawnEggList.size()) return;
                
                Mob newMob = (Mob) player.getWorld().spawnEntity(player.getLocation(), selectedType);
                
                if (isCreatingNew) {
                    // Create new jockey
                    int position;
                    if (asPassenger) {
                        position = jockeyManager.getJockeyCount() + 1; // Add at the top of the stack
                    } else {
                        position = jockeyManager.getJockeyCount() + 1; // Add at the bottom
                    }
                    
                    JockeyNode newJockey = jockeyManager.addJockey(newMob, position);
                    if (newJockey != null) {
                        String type = asPassenger ? "passenger" : "jockey";
                        player.sendMessage("§aCreated new " + type + " with " + formatEntityName(selectedType.name()));
                        playDefaultSound(player);
                        if (returnCallback != null) {
                            returnCallback.accept(player);
                        }
                    } else {
                        player.sendMessage("§cFailed to create jockey");
                        newMob.remove();
                    }
                } else {
                    // Change existing jockey's mob
                    if (jockeyManager.changeMobType(targetJockey.getPosition(), newMob)) {
                        player.sendMessage("§aChanged jockey to " + formatEntityName(selectedType.name()));
                        playDefaultSound(player);
                        if (returnCallback != null) {
                            returnCallback.accept(player);
                        }
                    } else {
                        player.sendMessage("§cFailed to change jockey");
                        newMob.remove();
                    }
                }
            }
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        // This is now handled in handleClick
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        jockeyMobInventories.remove(ownerId);
        this.delete();
    }
} 