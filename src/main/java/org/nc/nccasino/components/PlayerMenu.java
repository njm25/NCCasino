package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;

import java.util.UUID;
import java.util.function.Consumer;

public class PlayerMenu extends Menu {


    private final boolean fromAdmin;

    public PlayerMenu(Player player, Nccasino plugin,UUID dealerId, Consumer<Player> returnToAdmin, String returnName) {
        super(
            player,
            plugin,
            dealerId,
            plugin.getLocalization().text(player, "player-menu.title"),
            9,
            returnName == null
                ? null
                : plugin.getLocalization().text(
                    player,
                    "player-menu.return-to",
                    "menu",
                    returnName
                ),
            returnToAdmin
        );
        this.fromAdmin = (returnToAdmin != null);

        
        if(fromAdmin){
            slotMapping.put(SlotOption.RETURN, 0);
            slotMapping.put(SlotOption.EXIT, 8);
            slotMapping.put(SlotOption.PREFERENCES, 1);
            slotMapping.put(SlotOption.STATS, 2);
        }
        else{
            slotMapping.put(SlotOption.EXIT, 8);
            slotMapping.put(SlotOption.PREFERENCES, 0);
            slotMapping.put(SlotOption.STATS, 1);
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
        addItemAndLore(
            Material.BOOK,
            1,
            plugin.getLocalization().text(ownerId, "player-menu.statistics"),
            slotMapping.get(SlotOption.STATS),
            plugin.getLocalization().text(ownerId, "player-menu.coming-soon")
        );
        addItemAndLore(
            Material.WRITABLE_BOOK,
            1,
            plugin.getLocalization().text(ownerId, "player-menu.preferences"),
            slotMapping.get(SlotOption.PREFERENCES)
        );

        addItemAndLore(
            Material.SPRUCE_DOOR,
            1,
            plugin.getLocalization().text(ownerId, "common.exit"),
            slotMapping.get(SlotOption.EXIT)
        );
        if (fromAdmin) {       
            addItemAndLore(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                1,
                returnMessage,
                slotMapping.get(SlotOption.RETURN)
            );
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {

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
                        player.sendMessage(
                            plugin.getLocalization().text(player, "errors.invalid-option")
                        );
                        break;}
                    case VERBOSE:{
                        player.sendMessage(
                            plugin.getLocalization().text(
                                player,
                                "errors.invalid-player-menu-option"
                            )
                        );
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
