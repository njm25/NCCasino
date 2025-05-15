package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


public class JockeyMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    private JockeyManager jockeyManager;
    public static final Map<UUID, JockeyMenu> jockeyInventories = new HashMap<>();
    private Map<Integer, JockeyNode> slotToJockeyMap;

    public JockeyMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 27, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
        this.slotToJockeyMap = new HashMap<>();
        this.dealer = Dealer.getMobFromId(dealerId);
        if (this.dealer == null) {
            // Attempt to find a nearby Dealer if not found above
            this.dealer = (Mob) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(v -> Dealer.isDealer(v)
                             && Dealer.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
        }
        
        // Create a fresh JockeyManager to ensure we have the current state
        this.jockeyManager = new JockeyManager(dealer);
        
        slotMapping.put(SlotOption.EXIT, 26);
        slotMapping.put(SlotOption.RETURN, 18);
        slotMapping.put(SlotOption.ADD_JOCKEY, 21);
        slotMapping.put(SlotOption.ADD_PASSENGER, 22);
        slotMapping.put(SlotOption.REMOVE_JOCKEY, 23);
        jockeyInventories.put(this.ownerId, this);
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        // Clear the jockey mapping
        slotToJockeyMap.clear();

        // Add standard menu items
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.SADDLE, 1, "Add Vehicle", slotMapping.get(SlotOption.ADD_JOCKEY), "Add a new mob directly below dealer");
        addItemAndLore(Material.LEAD, 1, "Add Jockey", slotMapping.get(SlotOption.ADD_PASSENGER), "Add a new mob to the top of the stack");
        addItemAndLore(Material.BARRIER, 1, "Remove Jockey(WIP)", slotMapping.get(SlotOption.REMOVE_JOCKEY), "Remove the top jockey from the stack(WIP)");

        // Add dealer representation in the middle
        int dealerSlot = 13; // Center slot
        addItemAndLore(
            Material.SPAWNER,
            1,
            "Dealer",
            dealerSlot,
            "Type: §a" + dealer.getType().name(),
            "This is the base dealer"
        );

        System.out.println("\n=== MENU DEBUG ===");
        System.out.println("Dealer: " + dealer.getType());
        
        // Display jockeys (below dealer)
        Mob currentMob = dealer;
        int jockeySlot = dealerSlot - 1; // Start left of dealer
        while (currentMob.getVehicle() instanceof Mob) {
            Mob jockeyMob = (Mob)currentMob.getVehicle();
            System.out.println("Found jockey: " + jockeyMob.getType());
            
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
            System.out.println("Found passenger: " + passengerMob.getType());
            
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
        
        System.out.println("=== END MENU DEBUG ===\n");
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
                handleRemoveJockey(player);
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
            handleJockeyOptions(player, clickedJockey);
        }
    }

    private void handleAddJockey(Player player, boolean asPassenger) {
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
                            (p2) -> {
                                if (jockeyInventories.containsKey(player.getUniqueId())) {
                                    player.openInventory(jockeyInventories.get(player.getUniqueId()).getInventory());
                                }
                            },
                            selectedType
                        );
                        player.openInventory(firstVehicleMenu.getInventory());
                        return;
                    }
                }
                
                // Otherwise return to jockey menu
                if (jockeyInventories.containsKey(player.getUniqueId())) {
                    player.openInventory(jockeyInventories.get(player.getUniqueId()).getInventory());
                }
            },
            asPassenger
        );
        player.openInventory(mobMenu.getInventory());
    }

    private void handleRemoveJockey(Player player) {
        int count = jockeyManager.getJockeyCount();
        if (count > 0) {
            jockeyManager.removeJockey(count);
            player.sendMessage("§aRemoved the top jockey.");
            initializeMenu();
        } else {
            player.sendMessage("§cNo jockeys to remove.");
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
        this.delete();
    }
} 