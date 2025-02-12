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
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;
import net.md_5.bungee.api.ChatColor;

public class MinesAdminInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, MinesAdminInventory> MAInventories = new HashMap<>();


    private enum SlotOption {
        RETURN,
        EDIT_MINES,
        EXIT
     }
     private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EXIT, 0);
        put(SlotOption.RETURN, 1);
        put(SlotOption.EDIT_MINES, 2);
     }};

    public MinesAdminInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
        super(player.getUniqueId(), 9, title);
        this.ret = ret;
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = Dealer.getMobFromId(dealerId);
        if (this.dealer == null) {
            // Attempt to find a nearby Dealer if not found above
            this.dealer = (Mob) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(v -> Dealer.isDealer(v)
                             && Dealer.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
        }
        registerListener();
        this.ownerId = player.getUniqueId();  // Store the player's ID

        MAInventories.put(this.ownerId, this);
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
        MAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminInventory.editMinesMode.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    private void initalizeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int defaultMines = config.getInt("dealers." + internalName + ".default-mines", 3);
        addItemAndLore(Material.TNT, defaultMines, "Edit Default # of Mines",  slotMapping.get(SlotOption.EDIT_MINES), "Current: §a" + defaultMines);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+returnName,  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof MinesAdminInventory){
        // Check if the player has an active AdminInventory
            if (MAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (!AdminInventory.editMinesMode.containsKey(playerId)) {
                    // Remove the AdminInventory and clean up references
                    MinesAdminInventory inventory = MAInventories.remove(playerId);

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
        if (!MAInventories.containsKey(playerId)) return;
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
                case EDIT_MINES:
                handleEditMines(player);
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
                            player.sendMessage("§cInvalid mines settings option selected.");
                            break;}
                        case NONE:{
                            break;
                        }
                    }
                    break;
            }}
        } else {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cPlease wait before clicking again!");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cPlease wait before clicking mines settings again!");
                    break;}
                case NONE:{
                    break;
                }
            }
        }
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

    private void handleEditMines(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localMob.put(player.getUniqueId(), dealer);
        AdminInventory.editMinesMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new default # of mines in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType new default # of mines between 1 and 24 in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }

      @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (MAInventories.get(playerId) == null){
            cleanup();
            return;
        }
         if (AdminInventory.editMinesMode.get(playerId) != null) {
            event.setCancelled(true);
            String newTimer = event.getMessage().trim();

            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) <= 0|| Integer.parseInt(newTimer) >24 ) {
                denyAction(player, "Please enter a default # of mines between 1 and 24.");
                return;
            }
            if (dealer != null) {
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".default-mines", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§aDefault # of mines updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDefault # of mines updated to: " + ChatColor.YELLOW + newTimer + "§a.");
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
                        player.sendMessage("§cCould not find dealer for mines settings.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }

            AdminInventory.localMob.remove(playerId);

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
