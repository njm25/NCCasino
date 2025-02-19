package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;

import java.util.UUID;
import java.util.function.Consumer;

public class TestMenu extends Menu {

    public TestMenu(
        Player player, 
        Nccasino plugin, 
        UUID dealerId, 
        String returnMessage,
        Consumer<Player> returnCallback
    ) {
        super(player, plugin, dealerId, "Test Menu", 9, returnMessage, returnCallback);
        
        // Initialize slot mappings
        slotMapping.put(SlotOption.COMPLEX_VAR_1, 2);
        slotMapping.put(SlotOption.COMPLEX_VAR_2, 3);

        // Build the actual menu items
        addExitReturn();
        initializeMenu();
    }

    protected void initializeMenu() {
 
         addItemAndLore(
             Material.PAPER, 
             1, 
             "Option One", 
             slotMapping.get(SlotOption.COMPLEX_VAR_1), 
             "Click to print a message."
         );
         addItemAndLore(Material.BOOK, 
             1, 
             "Option Two", 
             slotMapping.get(SlotOption.COMPLEX_VAR_2), 
             "Click to print another message."
         );
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case COMPLEX_VAR_1:
                player.sendMessage("You clicked Option One!");
                break;
            case COMPLEX_VAR_2:
                player.sendMessage("You clicked Option Two!");
                break;
            default:
                player.sendMessage("§cInvalid option selected.");
        }
    }

}
