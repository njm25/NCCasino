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
        super(player, plugin, dealerId, plugin.getLocalization().text(player, "test-menu.title"), 9, returnMessage, returnCallback);
        
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
             text("test-menu.option-one"),
             slotMapping.get(SlotOption.COMPLEX_VAR_1), 
             text("test-menu.option-one-lore")
         );
         addItemAndLore(Material.BOOK, 
             1, 
             text("test-menu.option-two"),
             slotMapping.get(SlotOption.COMPLEX_VAR_2), 
             text("test-menu.option-two-lore")
         );
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case COMPLEX_VAR_1:
                player.sendMessage(text("test-menu.clicked-one"));
                break;
            case COMPLEX_VAR_2:
                player.sendMessage(text("test-menu.clicked-two"));
                break;
            default:
                player.sendMessage(text("test-menu.invalid-option"));
        }
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
