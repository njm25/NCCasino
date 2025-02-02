package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.helpers.SoundHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PreferencesInventory extends DealerInventory {
    private enum SlotOption {
        EXIT,
        RETURN,
        SOUNDS,
        MESSAGES
    }



    private Map<SlotOption, Integer> slotMapping=  new HashMap<>(){
        {
                put(SlotOption.RETURN, 1);
                put(SlotOption.EXIT, 0);
                put(SlotOption.SOUNDS, 2);
                put(SlotOption.MESSAGES, 3);
        }
    };
    // Keep track of currently open PlayerMenus per-player
    public static final Map<UUID, PreferencesInventory> preferencesInventories = new HashMap<>();
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();
    private final UUID ownerId;
    private final UUID dealerId;
    private final Nccasino plugin;
    private final Consumer<Player> returnToPM;


    public PreferencesInventory(Player player, Nccasino plugin,UUID dealerId, Consumer<Player> returnToPM) {
        super(player.getUniqueId(), 9, "Preferences Menu");
        this.dealerId=dealerId;
        this.ownerId = player.getUniqueId();
        this.plugin = plugin;
        this.returnToPM = returnToPM;

        // Keep track of this menu in the static map
        preferencesInventories.put(ownerId, this);
     
        // Build the actual contents
        initializeMenu();

        // Register this menu as a listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

  

    public UUID getDealerId(){
        return dealerId;
    }

    /**
     * Populate our Player Menu items.
     */
    private void initializeMenu() {
        Preferences preferences = plugin.getPreferences(ownerId);
        addItemAndLore(Material.BELL, 1, "Sound", slotMapping.get(SlotOption.SOUNDS), "Current: " + preferences.getSoundDisplay());
        addItemAndLore(Material.BOOKSHELF, 1, "Messages", slotMapping.get(SlotOption.MESSAGES), "Current: " + preferences.getMessageDisplay());
       
        //addItem( createCustomItem(Material.BELL, "Sound"),slotMapping.get(SlotOption.SOUNDS));
        //addItem(createCustomItem(Material.BOOKSHELF, "Messages"),slotMapping.get(SlotOption.MESSAGES));
        addItem( createCustomItem(Material.SPRUCE_DOOR, "Exit"),slotMapping.get(SlotOption.EXIT) );
        addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return to Player Menu"), slotMapping.get(SlotOption.RETURN)  );
        
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Preferences preferences = plugin.getPreferences(playerId);
        if (clickAllowed.getOrDefault(playerId, true)) {
            // Throttle clicking slightly to prevent spam
        clickAllowed.put(playerId, false);
        SlotOption option = getKeyByValue(slotMapping, slot);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);
        if (option != null) {
            switch (option) {
                case EXIT:
                    player.closeInventory();
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);
                    if(SoundHelper.getSoundSafely("block.wooden_door.close",player)!=null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.MASTER,1.0f, 1.0f);  

                    break;
                case RETURN:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    if (returnToPM != null) {
                        returnToPM.accept(player);
                    }
                    else {
                        player.sendMessage("No return callback was set!");
                        player.closeInventory();
                    }
                    break;
                case SOUNDS:
                    preferences.toggleSound();
                    updatePreferencesMenu();
                    //handleMoveDealer(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                case MESSAGES:
                    preferences.cycleMessageSetting();
                    updatePreferencesMenu();
                    //handleDeleteDealer(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                default:
                    player.sendMessage("§cInvalid option selected.");
                    break;
            }
        }

    
    } else {
        player.sendMessage("§cPlease wait before clicking again!");
    }
       
    }

    private void updatePreferencesMenu() {
    Preferences preferences = plugin.getPreferences(ownerId);
    addItemAndLore(Material.BELL, 1, "Sound", slotMapping.get(SlotOption.SOUNDS), "Current: " + preferences.getSoundDisplay());
    addItemAndLore(Material.BOOKSHELF, 1, "Messages", slotMapping.get(SlotOption.MESSAGES), "Current: " + preferences.getMessageDisplay());
}
    /**
     * When the menu is closed, clean up if needed.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

        if (event.getInventory().getHolder() instanceof PlayerMenu) {
            if (event.getPlayer().getUniqueId().equals(ownerId)) {
                // Defer removing from static map so we don't get concurrency issues
                if(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof PreferencesInventory){
                    return;
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (preferencesInventories.containsKey(ownerId)) {
                        PreferencesInventory menu = preferencesInventories.get(ownerId);
                        if (menu != null && menu.equals(this)) {
                            // Remove from map
                            preferencesInventories.remove(ownerId);
                            // Unregister
                            cleanup();
                        }
                    }
                }, 1L);

            }
        }
    }, 4L);

    }

    /**
     * Cleanup references and unregister. 
     * Then call super.delete() to remove from DealerInventory.inventories.
     */
    private void cleanup() {
        HandlerList.unregisterAll(this);
        super.delete();
    }

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }

}
