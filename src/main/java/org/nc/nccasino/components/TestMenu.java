package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.entity.Player;
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
        slotMapping.put(SlotOption.TEST_OPTION_ONE, 2);
        slotMapping.put(SlotOption.TEST_OPTION_TWO, 3);

        // Build the actual menu items
        addExitReturn();
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {

        addItemAndLore(
            Material.PAPER, 
            1, 
            "Option One", 
            slotMapping.get(SlotOption.TEST_OPTION_ONE), 
            "Click to print a message."
        );
        addItemAndLore(Material.BOOK, 
            1, 
            "Option Two", 
            slotMapping.get(SlotOption.TEST_OPTION_TWO), 
            "Click to print another message."
        );
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player) {
        switch (option) {
            case TEST_OPTION_ONE:
                player.sendMessage("You clicked Option One!");
                break;
            case TEST_OPTION_TWO:
                player.sendMessage("You clicked Option Two!");
                break;
            default:
                player.sendMessage("§cInvalid option selected.");
        }
    }

}
