package org.nc.nccasino.games;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class GameMenuInventory extends DealerInventory {

    public GameMenuInventory(UUID dealerId) {
        super(dealerId, 9, "Game Menu");
        initializeMenu();
    }

    // Initialize default game menu with clickable items
    private void initializeMenu() {
        addItem(createCustomItem(Material.DIAMOND_BLOCK, "Game 1"), 0);
        addItem(createCustomItem(Material.EMERALD_BLOCK, "Game 2"), 1);
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
    public void handleClick(int slot, Player player) {
        switch (slot) {
            case 0:
                player.sendMessage("Game 1 selected");
                break;
            case 1:
                player.sendMessage("Game 2 selected");
                break;
            default:
                // Handle other slots if needed
                break;
        }
    }
}
