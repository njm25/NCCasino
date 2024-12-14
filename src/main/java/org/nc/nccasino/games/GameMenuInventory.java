package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.entities.DealerVillager;

import java.util.UUID;

public class GameMenuInventory extends DealerInventory {

    public GameMenuInventory(UUID dealerId) {
        super(dealerId, 9, "Game Menu");
        initializeMenu();
    }

    // Initialize default game menu with clickable items
    private void initializeMenu() {
        addItem(createCustomItem(Material.DIAMOND_BLOCK, "Blackjack"), 0);
        addItem(createCustomItem(Material.EMERALD_BLOCK, "Roulette"), 1);
        addItem(createCustomItem(Material.TNT, "Mines"), 2);
        //addItem(createCustomItem(Material.DRAGON_HEAD, "Dragon Climb"), 3);
        //addItem(createCustomItem(Material.RAIL, "Rail Runner"), 4);
        //addItem(createCustomItem(Material.RED_MUSHROOM_BLOCK, "Dice"), 5);
        /* 
           addItem(createCustomItem(Material.ELYTRA, "Digital Baccarat"),6);
        addItem(createCustomItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Higher or Lower"), 7);
        addItem(createCustomItem(Material.EMERALD, "Keno"), 8);
        addItem(createCustomItem(Material.NAUTILUS_SHELL, "Bandit Wheel"), 9);*/

    }

    // Create an item stack with a custom display name
    public ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Ensure the event is cancelled to prevent unintended item movement

        Villager villager = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Villager)
                .map(entity -> (Villager) entity)
                .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
    
        if (villager == null) {
            player.sendMessage("No dealer nearby!");
            return;
        }
    
        switch (slot) {
            case 0:
                DealerVillager.switchGame(villager, "Blackjack",player);
                break;
            case 1:
                DealerVillager.switchGame(villager, "Roulette",player);
                break;
            case 2:
                DealerVillager.switchGame(villager, "Mines",player);
                break;
                /*
            case 3:
                DealerVillager.switchGame(villager, "Dragon Climb",player);
                break;
            case 4:
                DealerVillager.switchGame(villager, "Rail Runner",player);
                break;
            case 5:
                DealerVillager.switchGame(villager, "Dice",player);
                break;
            case 6:
                DealerVillager.switchGame(villager, "Digital Bacarrat",player);
                break;
            case 7:
                DealerVillager.switchGame(villager, "Higher or Lower",player);
                break;
         
            case 8:
                DealerVillager.switchGame(villager, "Keno",player);
                break;
            case 9:
                DealerVillager.switchGame(villager, "Bandit Camp Wheel",player);
                break;
 */
            default:
                return; // Exit if no valid slot is clicked
        }
    
        // Refresh the inventory to reflect the changes
        DealerInventory dealerInventory = DealerInventory.getInventory(DealerVillager.getUniqueId(villager));
        if (dealerInventory != null) {
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(DealerInventory.class), () -> {
                player.openInventory(dealerInventory.getInventory());
            }, 1L);
        }
    }
    

}
