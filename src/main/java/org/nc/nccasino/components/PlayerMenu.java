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
import org.nc.nccasino.helpers.SoundHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerMenu extends DealerInventory {
    private enum SlotOption {
        EXIT,
        RETURN,
        PREFERENCES,
        STATS
    }

    private Map<SlotOption, Integer> slotMapping;
    // Keep track of currently open PlayerMenus per-player
    public static final Map<UUID, PlayerMenu> playerMenus = new HashMap<>();
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();
    private final UUID ownerId;
    private final UUID dealerId;
    private final Nccasino plugin;
    private final boolean fromAdmin;
    private final Consumer<Player> returnToAdmin;


    public PlayerMenu(Player player, Nccasino plugin,UUID dealerId, Consumer<Player> returnToAdmin) {
        super(player.getUniqueId(), 9, "Player Menu");
        this.dealerId=dealerId;
        this.ownerId = player.getUniqueId();
        this.plugin = plugin;
        this.fromAdmin = (returnToAdmin != null);
        this.returnToAdmin = returnToAdmin;

        // Keep track of this menu in the static map
        playerMenus.put(ownerId, this);
        slotMapping=  new HashMap<>(){
            {
                if(fromAdmin){
                    put(SlotOption.RETURN, 1);
                    put(SlotOption.EXIT, 0);
                    put(SlotOption.PREFERENCES, 2);
                    put(SlotOption.STATS, 3);
                }
                else{
                    put(SlotOption.EXIT, 0);
                    put(SlotOption.PREFERENCES, 1);
                    put(SlotOption.STATS, 2);
                }
        
            }
                };
        // Build the actual contents
        initializeMenu();

        // Register this menu as a listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public PlayerMenu(Player player, Nccasino plugin,UUID dealerId) {
        this(player, plugin,dealerId, null);
    }

    public UUID getDealerId(){
        return dealerId;
    }

    /**
     * Populate our Player Menu items.
     */
    private void initializeMenu() {
        addItem( createCustomItem(Material.WRITABLE_BOOK, "Player Preferences"),slotMapping.get(SlotOption.PREFERENCES));
        addItem(createCustomItem(Material.BOOK, "Statistics"),slotMapping.get(SlotOption.STATS));
        addItem( createCustomItem(Material.SPRUCE_DOOR, "Exit"),slotMapping.get(SlotOption.EXIT) );
        if (fromAdmin) {
            addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return to Admin Menu"), slotMapping.get(SlotOption.RETURN)  );
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        UUID playerId = player.getUniqueId();

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
                    if (returnToAdmin != null) {
                        returnToAdmin.accept(player);
                    }
                    else {
                        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                            case STANDARD:{
                                player.sendMessage("§cNo return callback was set!");
                                break;}
                            case VERBOSE:{
                                player.sendMessage("§cNo return callback was set for player menu!");
                                break;}
                            case NONE:{
                                break;
                            }
                        }
                        player.closeInventory();
                    }
                    break;
                case STATS:
                    //handleMoveDealer(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                case PREFERENCES:
                    handlePreferencesMenu(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
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

    
    } else {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§cPlease wait before clicking again!");
                break;}
            case VERBOSE:{
                player.sendMessage("§cPlease wait before clicking player menu again!");
                break;}
            case NONE:{
                break;
            }
        }
    }
       
    }

    private void handlePreferencesMenu(Player player) {
        PreferencesInventory pm = new PreferencesInventory(player, plugin,dealerId, (p) -> {


            if (PlayerMenu.playerMenus.containsKey(player.getUniqueId())) {
                PlayerMenu adminInventory = PlayerMenu.playerMenus.get(player.getUniqueId());
                player.openInventory(adminInventory.getInventory());
            } else {
                if(player.hasPermission("nccasino.adminmenu")){
                    PlayerMenu pmen = new PlayerMenu(player,plugin,dealerId,(a) -> {
                        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                            //localVillager.remove(player.getUniqueId());
                        } else {
                            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                            //localVillager.remove(player.getUniqueId());
                        }
                    });
                        player.openInventory(pmen.getInventory());
                }
                else{
                PlayerMenu adminInventory = new PlayerMenu(player,plugin,dealerId);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {

                player.openInventory(adminInventory.getInventory());
            },2L);
            }
            }
        });
        player.openInventory(pm.getInventory());
    }

    /**
     * When the menu is closed, clean up if needed.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

        if (event.getInventory().getHolder() instanceof PlayerMenu) {
            if (event.getPlayer().getUniqueId().equals(ownerId)) {
                if(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof PlayerMenu){
                    return;
                }
                // Defer removing from static map so we don't get concurrency issues
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (playerMenus.containsKey(ownerId)) {
                        PlayerMenu menu = playerMenus.get(ownerId);
                        if (menu != null && menu.equals(this)) {
                            // Remove from map
                            playerMenus.remove(ownerId);
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
