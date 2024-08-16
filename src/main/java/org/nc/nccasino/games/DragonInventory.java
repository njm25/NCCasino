package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonInventory extends DealerInventory implements Listener {
    private final Map<Player, DragonTable> Tables;
    private final Nccasino plugin;

    public DragonInventory(UUID dealerId, Nccasino plugin) {
        super(dealerId, 54, "Mines Start Menu");
        this.plugin = plugin;
        this.Tables = new HashMap<>();
        initializeStartMenu();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Initialize items for the start menu using the inherited method
    private void initializeStartMenu() {
        inventory.clear();
        addItem(createCustomItem(Material.BLACK_WOOL, "Start Dragon Climb"), 22);
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == 22) {
            setupGameMenu(player);
        }
    }

    // Set up items for the game menu using the inherited method
    private void setupGameMenu(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                    .filter(entity -> entity instanceof Villager)
                    .map(entity -> (Villager) entity)
                    .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                    .findFirst().orElse(null);
            if (dealer != null) {
                DragonTable dragonTable = new DragonTable(player, dealer, plugin, DealerVillager.getInternalName(dealer), this);
                Tables.put(player, dragonTable);
                player.openInventory(dragonTable.getInventory());
            } else {
                player.sendMessage("Error: Dealer not found. Unable to open mines table.");
            }
        }, 1L);
    }
}
