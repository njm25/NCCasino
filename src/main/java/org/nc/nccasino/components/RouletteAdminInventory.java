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
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;
import net.md_5.bungee.api.ChatColor;

public class RouletteAdminInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Villager dealer;
    public static final Map<UUID, RouletteAdminInventory> RAInventories = new HashMap<>();


    private enum SlotOption {
        RETURN,
        EDIT_TIMER,
        EXIT
     }
     private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EXIT, 0);
        put(SlotOption.RETURN, 1);
        put(SlotOption.EDIT_TIMER, 2);
     }};

    public RouletteAdminInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
        super(player.getUniqueId(), 9, title);
        this.ret = ret;
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = DealerVillager.getVillagerFromId(dealerId);
        if (this.dealer == null) {
            // Attempt to find a nearby Dealer if not found above
            this.dealer = (Villager) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Villager)
                .map(entity -> (Villager) entity)
                .filter(v -> DealerVillager.isDealerVillager(v)
                             && DealerVillager.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
        }
        registerListener();
        this.ownerId = player.getUniqueId();  // Store the player's ID

        RAInventories.put(this.ownerId, this);
        initalizeMenu();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        RAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminInventory.timerEditMode.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    private void initalizeMenu(){
        String internalName = DealerVillager.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")
        ? config.getInt("dealers." + internalName + ".timer")
        : 10; // Default to 10 if missing
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: §a" + currentTimer);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+returnName,  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof RouletteAdminInventory){
        // Check if the player has an active AdminInventory
            if (RAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (!AdminInventory.timerEditMode.containsKey(playerId)) {
                    // Remove the AdminInventory and clean up references
                    RouletteAdminInventory inventory = RAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }
                
                    // Unregister this listener if no more AdminInventories exist
                    if (RAInventories.isEmpty()) {
                        unregisterListener();
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminInventory) {
                        return;
                    }
                    AdminInventory temp=AdminInventory.adminInventories.get(player.getUniqueId());
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

    public void executeReturn() {
        ret.accept(dealerId);
        delete();
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!RAInventories.containsKey(playerId)) return;
        //event.setCancelled(true); // Default behavior: prevent unintended interactions

        if (event.getClickedInventory() == null) return; 
        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

            SlotOption option = getKeyByValue(slotMapping, slot);
            if(option!=null){
            switch (option) {
                case RETURN:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    executeReturn();
                    break;
                case EDIT_TIMER:
                    handleEditTimer(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                case EXIT:
                    handleExit(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                default:
                    if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                    switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage("§cInvalid option selected.");
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§cInvalid roulette settings option selected.");
                            break;}
                        case NONE:{
                            break;
                        }
                    }                    break;
            }}
        } else {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cPlease wait before clicking again!");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cPlease wait before clicking roulette settings again!");
                    break;}
                case NONE:{
                    break;
                }
            }        }
    }

    private void handleExit(Player player) {
        player.closeInventory();
        delete();
    }

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }

    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localVillager.put(player.getUniqueId(), dealer);
        AdminInventory.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType the new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new timer in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType the value.");
                break;
            }
        }  
    }

      @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (RAInventories.get(playerId) == null){
            cleanup();
            return;
        }
         if (AdminInventory.timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newTimer = event.getMessage().trim();

            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) <= 0) {
                denyAction(player, "Please enter a positive number.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§aDealer timer updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer timer updated to: " + ChatColor.YELLOW + newTimer + "§a.");
                        break;}
                    case NONE:{
                        break;
                    }
                }  
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find dealer for roulette settings.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }

            AdminInventory.localVillager.remove(playerId);

            cleanup();
        }

    }
    private void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no",player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        player.sendMessage("§c" + message);
    }
    
    public void delete() {
        cleanup();
   
        super.delete(); 
        // super.delete() removes from DealerInventory.inventories & clears the Inventory
    }

}
