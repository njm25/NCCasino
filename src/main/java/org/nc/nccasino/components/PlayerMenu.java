package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;

import java.util.UUID;
import java.util.function.Consumer;

public class PlayerMenu extends Menu {


    private final boolean fromAdmin;

    public PlayerMenu(Player player, Nccasino plugin,UUID dealerId, Consumer<Player> returnToAdmin, String returnName) {
        super(player, plugin,dealerId, "Player Menu", 9, returnName, returnToAdmin);
        this.fromAdmin = (returnToAdmin != null);

        
        if(fromAdmin){
            slotMapping.put(SlotOption.RETURN, 1);
            slotMapping.put(SlotOption.EXIT, 0);
            slotMapping.put(SlotOption.PREFERENCES, 2);
            slotMapping.put(SlotOption.STATS, 3);
        }
        else{
            slotMapping.put(SlotOption.EXIT, 0);
            slotMapping.put(SlotOption.PREFERENCES, 1);
            slotMapping.put(SlotOption.STATS, 2);
        }
        
        // Build the actual contents
        initializeMenu();

        // Register this menu as a listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public PlayerMenu(Player player, Nccasino plugin,UUID dealerId) {
        this(player, plugin,dealerId, null, null);
    }

    /**
     * Populate our Player Menu items.
     */
    @Override
    protected void initializeMenu() {
        addItemAndLore(Material.BOOK, 1, "Statistics",  slotMapping.get(SlotOption.STATS), "§cComing soon...");
        addItemAndLore(Material.WRITABLE_BOOK, 1, "Preferences",  slotMapping.get(SlotOption.PREFERENCES));

        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
        if (fromAdmin) {       
            addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnMessage,  slotMapping.get(SlotOption.RETURN));
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player) {

        switch (option) {
            case STATS:
                //handleMoveDealer(player);
                playDefaultSound(player);
                break;
            case PREFERENCES:
                handlePreferencesMenu(player);
                playDefaultSound(player);
                break;
            default:
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid option selected.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cInvalid player menu option selected.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
                break;
        }
    }

    private void handlePreferencesMenu(Player player) {
        PreferencesMenu pm = new PreferencesMenu(player, plugin,dealerId, (p) -> {
        if(player.hasPermission("nccasino.playermenu")){
            if (player.hasPermission("nccasino.adminmenu")){
                PlayerMenu pmen = new PlayerMenu(player,plugin,dealerId,(a) -> {
                    if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                        AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                        player.openInventory(adminInventory.getInventory());
                    } else {
                        AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                        player.openInventory(adminInventory.getInventory());
                    }
                },
                    returnMessage
                );
                    player.openInventory(pmen.getInventory());
            }
            else{
                PlayerMenu adminInventory = new PlayerMenu(player,plugin,dealerId);
                player.openInventory(adminInventory.getInventory());
            }
            
        }
        else{
        PlayerMenu adminInventory = new PlayerMenu(player,plugin,dealerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

        player.openInventory(adminInventory.getInventory());
    },2L);
    }
        });
        player.openInventory(pm.getInventory());
    }

}
