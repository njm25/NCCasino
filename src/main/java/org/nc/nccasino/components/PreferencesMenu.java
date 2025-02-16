package org.nc.nccasino.components;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.Preferences;

import java.util.UUID;
import java.util.function.Consumer;

public class PreferencesMenu extends Menu {
    public PreferencesMenu(Player player, Nccasino plugin,UUID dealerId, Consumer<Player> returnToPM) {
        super(player, plugin, dealerId, "Preferences", 9, "Return to Player Menu", returnToPM);
        slotMapping.put(SlotOption.RETURN, 1);
        slotMapping.put(SlotOption.EXIT, 0);
        slotMapping.put(SlotOption.SOUNDS, 2);
        slotMapping.put(SlotOption.MESSAGES, 3);
        // Build the actual contents
        initializeMenu();

    }
    /**
     * Populate our Player Menu items.
     */
    @Override
    protected void initializeMenu() {
        Preferences preferences = plugin.getPreferences(ownerId);
        addItemAndLore(Material.BELL, 1, "Sound", slotMapping.get(SlotOption.SOUNDS), "Current: " + preferences.getSoundDisplay());
        addItemAndLore(Material.BOOKSHELF, 1, "Messages", slotMapping.get(SlotOption.MESSAGES), "Current: " + preferences.getMessageDisplay());
       
        //addItem( createCustomItem(Material.BELL, "Sound"),slotMapping.get(SlotOption.SOUNDS));
        //addItem(createCustomItem(Material.BOOKSHELF, "Messages"),slotMapping.get(SlotOption.MESSAGES));

        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to Player Menu",  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
        
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        Preferences preferences = plugin.getPreferences(playerId);
        switch (option) {
            case SOUNDS:
                preferences.toggleSound();
                updatePreferencesMenu();
                //handleMoveDealer(player);
                playDefaultSound(player);
                break;
            case MESSAGES:
                preferences.cycleMessageSetting();
                updatePreferencesMenu();
                //handleDeleteDealer(player);
                playDefaultSound(player);
                break;
            default:
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cInvalid option selected.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cInvalid preferences menu option selected.");
                    break;}
                case NONE:{
                    break;
                }
            }
                break;
        }
    }

    private void updatePreferencesMenu() {
    Preferences preferences = plugin.getPreferences(ownerId);
    addItemAndLore(Material.BELL, 1, "Sound", slotMapping.get(SlotOption.SOUNDS), "Current: " + preferences.getSoundDisplay());
    addItemAndLore(Material.BOOKSHELF, 1, "Messages", slotMapping.get(SlotOption.MESSAGES), "Current: " + preferences.getMessageDisplay());
}



}
