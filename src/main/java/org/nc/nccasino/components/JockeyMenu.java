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

    public JockeyMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 27, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
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
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.SADDLE, 1, "Add Jockey", slotMapping.get(SlotOption.ADD_JOCKEY), "Add a new jockey to the bottom of the stack");
        addItemAndLore(Material.LEAD, 1, "Add Passenger", slotMapping.get(SlotOption.ADD_PASSENGER), "Add a new jockey to the top of the stack");
        addItemAndLore(Material.BARRIER, 1, "Remove Jockey", slotMapping.get(SlotOption.REMOVE_JOCKEY), "Remove the top jockey from the stack");

        // Add dealer representation in the middle
        addItemAndLore(
            Material.SPAWNER,
            1,
            "Dealer",
            4,
            "Type: §a" + dealer.getType().name(),
            "This is the base dealer"
        );

        // Display current jockeys
        int slot = 5; // Start one slot to the right of the dealer spawner
        for (JockeyNode jockey : jockeyManager.getJockeys()) {
            if (jockey.getPosition() == 0) continue; // Skip the dealer
            Material spawnEgg = MobSelectionMenu.getSpawnEggFor(jockey.getMob().getType());
            addItemAndLore(
                spawnEgg, 
                1, 
                "Jockey #" + jockey.getPosition(), 
                slot,

                "Type: §a" + jockey.getMob().getType().name()
            );
            slot++;
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
            default:
                // Check if clicked on a jockey item
                int slot = event.getSlot();
                if (slot >= 5 && slot < 12) { // Slots 5-11 are for jockeys
                    int position = slot - 4; // Convert slot to position (5 -> 1, 6 -> 2, etc.)
                    JockeyNode jockey = jockeyManager.getJockey(position);
                    if (jockey != null) {
                        handleJockeyOptions(player, jockey);
                    }
                }
                break;
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