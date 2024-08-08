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
    }

    // Create an item stack with a custom display name
    private ItemStack createCustomItem(Material material, String name) {
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
                player.sendMessage("Blackjack selected");
                DealerVillager.switchGame(villager, "Blackjack");
                break;
            case 1:
                player.sendMessage("Roulette selected");
                DealerVillager.switchGame(villager, "Roulette");
                break;
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
