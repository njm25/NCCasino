package org.nc.nccasino.components;

import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.HashSet;

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
        addItemAndLore(Material.SADDLE, 1, "Add Jockey", slotMapping.get(SlotOption.ADD_JOCKEY), "Add a new jockey to the bottom of the stack");
        addItemAndLore(Material.LEAD, 1, "Add Passenger", slotMapping.get(SlotOption.ADD_PASSENGER), "Add a new jockey to the top of the stack");
        addItemAndLore(Material.BARRIER, 1, "Remove Jockey", slotMapping.get(SlotOption.REMOVE_JOCKEY), "Remove the top jockey from the stack");

        // Add dealer representation
        addItemAndLore(
            Material.SPAWNER,
            1,
            "Dealer",
            4,
            "Type: §a" + dealer.getType().name(),
            "This is the base dealer"
        );

        // Display current jockeys with dynamic slot assignment
        int slot = 5; // Starting slot for jockeys
        
        // Get all jockeys excluding the dealer (position 0)
        List<JockeyNode> allJockeys = jockeyManager.getJockeys().stream()
            .filter(j -> j.getPosition() > 0)
            .toList();
            
        // Create a set to track which jockeys we've already displayed
        Set<JockeyNode> displayedJockeys = new HashSet<>();
        
        // Display jockeys in order, skipping those that are passengers of other jockeys
        for (JockeyNode jockey : allJockeys) {
            if (displayedJockeys.contains(jockey)) {
                continue; // Skip if we've already displayed this jockey
            }
            
            Material spawnEgg = MobSelectionMenu.getSpawnEggFor(jockey.getMob().getType());
            String customName = jockey.getCustomName() != null ? jockey.getCustomName() : "Jockey #" + jockey.getPosition();
            String role = jockey.isPassenger() ? "Passenger" : "Jockey";
            
            addItemAndLore(
                spawnEgg,
                1,
                customName,
                slot,
                "Type: §a" + jockey.getMob().getType().name(),
                "Role: §e" + role,
                "Position: " + jockey.getPosition(),
                "Click to customize"
            );

            // Map this slot to the jockey
            slotToJockeyMap.put(slot, jockey);
            displayedJockeys.add(jockey);
            slot++;

            // Display any passengers this jockey has
            List<JockeyNode> passengers = jockey.getPassengers();
            for (JockeyNode passenger : passengers) {
                if (displayedJockeys.contains(passenger)) {
                    continue; // Skip if we've already displayed this passenger
                }
                
                Material passengerEgg = MobSelectionMenu.getSpawnEggFor(passenger.getMob().getType());
                String passengerName = passenger.getCustomName() != null ? passenger.getCustomName() : "Passenger of " + customName;
                
                addItemAndLore(
                    passengerEgg,
                    1,
                    passengerName,
                    slot,
                    "Type: §a" + passenger.getMob().getType().name(),
                    "Role: §ePassenger",
                    "Position: " + passenger.getPosition(),
                    "Click to customize"
                );

                // Map this slot to the passenger
                slotToJockeyMap.put(slot, passenger);
                displayedJockeys.add(passenger);
                slot++;
            }
        }
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
        JockeyMobMenu mobMenu = new JockeyMobMenu(
            player, 
            plugin, 
            this.jockeyManager,
            null, 
            "Jockey Menu",
            (p) -> {
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