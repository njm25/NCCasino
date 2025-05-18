package org.nc.nccasino.components;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class FirstVehicleMenu extends Menu {
    private final Nccasino plugin;
    private final String returnName;
    private final JockeyManager jockeyManager;
    private final Consumer<Player> returnCallback;
    private final EntityType vehicleType;
    public static final Map<UUID, FirstVehicleMenu> firstVehicleInventories = new HashMap<>();

    public EntityType getVehicleType() {
        return vehicleType;
    }

    public FirstVehicleMenu(Player player, Nccasino plugin, JockeyManager jockeyManager, String returnName, Consumer<Player> returnCallback, EntityType vehicleType) {
        super(player, plugin, jockeyManager.getDealer().getUniqueId(), "Select First Vehicle Passenger", 9, "Jockey Mob Menu", (p) -> {
            // Return to JockeyMobMenu
            JockeyMobMenu newMobMenu = new JockeyMobMenu(
                p,
                plugin,
                jockeyManager,
                null,
                "Jockey Menu",
                (p2) -> {
                    // Return to JockeyMenu
                    if (JockeyMenu.jockeyInventories.containsKey(p2.getUniqueId())) {
                        p2.openInventory(JockeyMenu.jockeyInventories.get(p2.getUniqueId()).getInventory());
                    } else if (returnCallback != null) {
                        returnCallback.accept(p2);
                    }
                },
                false
            );
            p.openInventory(newMobMenu.getInventory());
        });
        this.plugin = plugin;
        this.returnName = returnName;
        this.jockeyManager = jockeyManager;
        this.returnCallback = returnCallback;
        this.vehicleType = vehicleType;
        
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.INVISIBLE_PASSENGER, 3);
        slotMapping.put(SlotOption.PICK_PASSENGER, 5);
        firstVehicleInventories.put(this.ownerId, this);
        
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        // Add standard menu items
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        
        // Add passenger options
        addItemAndLore(Material.SILVERFISH_SPAWN_EGG, 1, "Invisible Passenger", 
            slotMapping.get(SlotOption.INVISIBLE_PASSENGER),
            "§7Adds an invisible silverfish passenger",
            "§7No visible name will be shown");
            
        addItemAndLore(Material.VILLAGER_SPAWN_EGG, 1, "Pick Passenger", 
            slotMapping.get(SlotOption.PICK_PASSENGER),
            "§7Choose a specific mob as passenger",
            "§7The mob's name will be visible");
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case INVISIBLE_PASSENGER:
                // Get dealer location
                Mob dealer = jockeyManager.getDealer();
                Location dealerLoc = dealer.getLocation().clone();
                float yaw = dealerLoc.getYaw();
                
                // Spawn vehicle at dealer location
                Mob vehicle = (Mob) dealer.getWorld().spawnEntity(dealerLoc, vehicleType);
                vehicle.teleport(dealerLoc);
                vehicle.setRotation(yaw, 0);
                
                // Create vehicle node
                JockeyNode vehicleNode = new JockeyNode(vehicle, 1, true);
                vehicleNode.setCustomName("Vehicle");
                
                // Mount dealer on vehicle
                JockeyNode dealerNode = jockeyManager.getJockey(0);
                vehicleNode.mountAsVehicle(dealerNode);
                
                // Spawn invisible armor stand at dealer location
                ArmorStand armorStand = (ArmorStand) dealer.getWorld().spawnEntity(dealerLoc, EntityType.ARMOR_STAND);
                armorStand.setVisible(false);
                armorStand.setGravity(false);
                armorStand.setSmall(true);
                armorStand.setMarker(true);
                armorStand.setCustomName(dealer.getCustomName());
                armorStand.setCustomNameVisible(true);
                
                // Add armor stand as passenger to dealer directly
                dealer.addPassenger(armorStand);
                
                // Update name visibility
                dealer.setCustomNameVisible(false);
                vehicle.setCustomNameVisible(false);
                
                player.sendMessage("§aAdded vehicle and invisible passenger");
                cleanup();
                
                // Add delay before returning to menu
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Refresh the jockey manager to ensure state is in sync
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
                }.runTaskLater(plugin, 2L);
                break;
                
            case PICK_PASSENGER:
                // Create a new JockeyMobMenu for passenger selection
                JockeyMobMenu passengerMenu = new JockeyMobMenu(
                    player,
                    plugin,
                    jockeyManager,
                    null,
                    "First Vehicle Menu",
                    (p) -> {
                        // Return to FirstVehicleMenu
                        if (firstVehicleInventories.containsKey(p.getUniqueId())) {
                            p.openInventory(firstVehicleInventories.get(p.getUniqueId()).getInventory());
                        } else if (returnCallback != null) {
                            returnCallback.accept(p);
                        }
                    },
                    true
                );
                // Store the vehicle type in the passenger menu
                JockeyMobMenu.vehicleTypeMap.put(player.getUniqueId(), vehicleType);
                cleanup();
                player.openInventory(passengerMenu.getInventory());
                break;
                
            case EXIT:
                player.closeInventory();
                cleanup();
                break;
                
            case RETURN:
                cleanup();
                // Return to JockeyMobMenu
                JockeyMobMenu newMobMenu = new JockeyMobMenu(
                    player,
                    plugin,
                    jockeyManager,
                    null,
                    "Jockey Menu",
                    (p) -> {
                        // Return to JockeyMenu
                        if (JockeyMenu.jockeyInventories.containsKey(p.getUniqueId())) {
                            p.openInventory(JockeyMenu.jockeyInventories.get(p.getUniqueId()).getInventory());
                        } else if (returnCallback != null) {
                            returnCallback.accept(p);
                        }
                    },
                    false
                );
                player.openInventory(newMobMenu.getInventory());
                break;
                
            default:
                break;
        }
        
        playDefaultSound(player);
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        firstVehicleInventories.remove(ownerId);
        this.delete();
    }
} 