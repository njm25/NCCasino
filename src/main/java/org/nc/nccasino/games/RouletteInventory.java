package org.nc.nccasino.games;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class RouletteInventory extends DealerInventory {

    public RouletteInventory(UUID dealerId) {
        super(dealerId, 27, "Roulette Table"); // Using 27 slots as an example
        initializeRoulette();
    }

    // Initialize Roulette-specific inventory items
    private void initializeRoulette() {
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette"), 0);
        // Add other Roulette-related items here
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
                player.sendMessage("Starting Roulette...");
                // Add logic for starting the Roulette game
                break;
            default:
                // Handle other slots if needed
                break;
        }
    }
}
