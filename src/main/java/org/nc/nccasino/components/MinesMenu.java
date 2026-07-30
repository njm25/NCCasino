package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;


public class MinesMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, MinesMenu> MAInventories = new HashMap<>();

    public MinesMenu(UUID dealerId,Player player, String title, Consumer<Player> ret, Nccasino plugin,String returnName) {
        super(player, plugin,dealerId, title, 9, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());
     

        
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EDIT_MINES, 1);
        MAInventories.put(this.ownerId, this);
        initializeMenu();
    }


    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @Override
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        MAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminMenu.editMinesMode.remove(ownerId);
        this.delete();
    }

    /** Tears down this player's open MinesMenu, if any. AdminMenu's own edit-mode maps are cleared separately and unconditionally by AdminMenu.clearPlayerEditState. */
    public static void clearPlayerState(UUID playerId) {
        MinesMenu menu = MAInventories.get(playerId);
        if (menu != null) {
            menu.cleanup();
        }
    }

    @Override
    protected void initializeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int defaultMines = config.getInt("dealers." + internalName + ".default-mines", 3);
        addItemAndLore(Material.TNT, defaultMines, text("mines-settings.edit-default"), slotMapping.get(SlotOption.EDIT_MINES), text("common.current", "value", defaultMines));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof MinesMenu){
        // Check if the player has an active AdminInventory
            if (MAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (!AdminMenu.editMinesMode.containsKey(playerId)) {
                    // Remove the AdminInventory and clean up references
                    MinesMenu inventory = MAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }
                
                    // Unregister this listener if no more AdminInventories exist
                    if (MAInventories.isEmpty()) {
                        unregisterListener();
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu) {
                        return;
                    }
                    AdminMenu temp=AdminMenu.adminInventories.get(player.getUniqueId());
                    if(temp!=null){
                        if(temp.getDealerId()==dealerId){
                            temp.delete();
                        }
                    }
                }
                , 5L);
            }
        }
    }


    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!MAInventories.containsKey(playerId)) return;
        //event.setCancelled(true); // Default behavior: prevent unintended interactions

    
        switch (option) {
            case EDIT_MINES:
            handleEditMines(player);
            playDefaultSound(player);
                break;
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text("mines-settings.invalid-option"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("mines-settings.invalid-settings-option"));
                        break;}
                    case NONE:{
                        break;
                    }
                }
                break;
        }
    }

    private void handleEditMines(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(player.getUniqueId(), dealer);
        AdminMenu.editMinesMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text("mines-settings.prompt-default"));
                break;}
            case VERBOSE:{
                player.sendMessage(text("mines-settings.prompt-default-detailed"));
                break;}
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!MAInventories.containsKey(playerId)) {
            cleanup();
            return;
        }

        if (AdminMenu.editMinesMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, event.getMessage().trim(), "default-mines", 1, 24);
        }
    }

    private void handleNumericInput(Player player, String input, String configPath, long min, long max) {
        if (input.isEmpty() || !input.matches("\\d+")) {
            denyAction(player, text("blackjack-settings.valid-positive-integer"));
            return;
        }

        long value;
        try {
            value = Long.parseLong(input);
        } catch (NumberFormatException e) {
            denyAction(player, text("blackjack-settings.invalid-number-format"));
            return;
        }

        if (value < min || value > max) {
            denyAction(player, text("blackjack-settings.number-range", "min", min, "max", max));
            return;
        }

        if (dealer != null) {
            String internalName = Dealer.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + "." + configPath, value);
            plugin.saveConfig();
            plugin.reloadDealer(dealer);

            if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("mines-settings.default-updated"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(
                        "blackjack-settings.updated-detailed",
                        "setting",
                        text("mines-settings.default-updated"),
                        "value",
                        value
                    ));
                    break;
                case NONE:
                    break;
            }

            AdminMenu.localMob.remove(player.getUniqueId());
        } else {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("admin.dealer-not-found"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("mines-settings.dealer-not-found"));
                    break;
                case NONE:
                    break;
            }
        }

        cleanup();
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
