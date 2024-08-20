package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.games.DealerInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.games.MinesTable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinesInventory extends DealerInventory implements Listener {
    private final Map<Player, MinesTable> Tables;
    private final Nccasino plugin;



    public MinesInventory(UUID dealerId, Nccasino plugin) {
        super(dealerId, 54, "Mines Start Menu");
        this.plugin = plugin;
        this.Tables = new HashMap<>();
            Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
       // inventory.clear();
       // addItem(createCustomItem(Material.GREEN_WOOL, "Start Mines", 1), 22);
    }

    @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            // Open the MinesTable for the player
            setupGameMenu(player);
        }
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

        // setupGameMenu(player);
        /*  // If the player clicks to skip the animation
        if (animationTasks.containsKey(player)) {
            Bukkit.getScheduler().cancelTask(animationTasks.get(player));
            animationTasks.remove(player);
            animationCompleted.put(player, true);  // Mark animation as completed/skipped
            setupGameMenu(player);
        } else if (slot == 22) {
           
            // Start the animation if the player clicks the Start button
          
            startBlockAnimation(player, () -> {
                if (!animationCompleted.getOrDefault(player, false)) {
                    setupGameMenu(player);
                }
            }); 
        }*/
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

                MinesTable minesTable = new MinesTable(player, dealer, plugin, internalName, this);
                Tables.put(player, minesTable);
                player.openInventory(minesTable.getInventory());
            } else {
                player.sendMessage("Error: Dealer not found. Unable to open mines table.");
            }
        }, 1L);
    }

  
}
