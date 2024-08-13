package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.games.DealerInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.MinesTable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class RailInventory extends DealerInventory implements Listener {
 private final Map<Player,RailTable>Tables;
   private final Nccasino plugin;
   
    public RailInventory(UUID dealerId, Nccasino plugin) {
     
        super(dealerId, 54, "Rail Runner Start Menu");
        this.plugin = plugin;

        this.Tables=new HashMap<>();
        initializeStartMenu();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear();
        addItem(createCustomItem(Material.BROWN_WOOL, "Start Rail Runner", 1), 22);
    }

    
    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
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

    // Set up items for the game menu
    private void setupGameMenu(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                    .filter(entity -> entity instanceof Villager)
                    .map(entity -> (Villager) entity)
                    .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                    .findFirst().orElse(null);
            if (dealer != null) { 
                String internalName = DealerVillager.getInternalName(dealer);
              
                RailTable railTable = new RailTable(player, dealer, plugin, internalName, this);
                Tables.put(player, railTable);
                player.openInventory(railTable.getInventory());
            } else {
                player.sendMessage("Error: Dealer not found. Unable to open mines table.");
            }
        }, 1L);



    }

}
