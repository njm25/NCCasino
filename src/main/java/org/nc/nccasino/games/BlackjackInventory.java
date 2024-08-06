package org.nc.nccasino.games;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class BlackjackInventory extends DealerInventory {

    public BlackjackInventory(UUID dealerId) {
        super(dealerId, 27, "Blackjack Table"); // Using 27 slots as an example
        initializeBlackjack();
    }

    // Initialize Blackjack-specific inventory items
    private void initializeBlackjack() {
        addItem(createCustomItem(Material.BLACK_WOOL, "Start Blackjack"), 0);
        // Add other Blackjack-related items here
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

// BlackjackInventory.java

@Override
public void handleClick(int slot, Player player, InventoryClickEvent event) {
    event.setCancelled(true); // Ensure the event is cancelled to prevent unintended item movement

    switch (slot) {
        case 0:
            player.sendMessage("Starting Blackjack...");
            // Implement logic to start the Blackjack game
            break;
        default:
            // Handle other slots if needed
            break;
    }
}

}
