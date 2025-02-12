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

public class BlackjackAdminInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, BlackjackAdminInventory> BAInventories = new HashMap<>();

    private enum SlotOption {
        RETURN,
        EDIT_TIMER,
        STAND_17,
        NUMBER_OF_DECKS,
        EXIT
     }
     private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EXIT, 0);
        put(SlotOption.RETURN, 1);
        put(SlotOption.EDIT_TIMER, 2);
        put(SlotOption.STAND_17, 3);
        put(SlotOption.NUMBER_OF_DECKS, 4);
     }};

    public BlackjackAdminInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
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

        BAInventories.put(this.ownerId, this);
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
        BAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminInventory.timerEditMode.remove(ownerId);
        AdminInventory.standOn17Mode.remove(ownerId);
        AdminInventory.decksEditMode.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    private void initalizeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")? config.getInt("dealers." + internalName + ".timer"): 10; 
        int standOn17Chance = config.getInt("dealers." + internalName + ".stand-on-17", 100);
        int numberOfDecks = config.getInt("dealers." + internalName + ".number-of-decks", 6);
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: §a" + currentTimer);
        addItemAndLore(Material.SHIELD, standOn17Chance, "Edit Stand on 17 Chance", slotMapping.get(SlotOption.STAND_17), "Current: §a" + standOn17Chance + "%");
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, numberOfDecks, "Edit Number of Decks", slotMapping.get(SlotOption.NUMBER_OF_DECKS), "Current: §a" + numberOfDecks);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+returnName,  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));

    }

    public boolean isPlayerOccupied(UUID playerId){
        return 
            !AdminInventory.timerEditMode.containsKey(playerId) &&
            !AdminInventory.standOn17Mode.containsKey(playerId) &&
            !AdminInventory.decksEditMode.containsKey(playerId);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof BlackjackAdminInventory){
        // Check if the player has an active AdminInventory
            if (BAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (isPlayerOccupied(playerId)) {
                    // Remove the AdminInventory and clean up references
                    BlackjackAdminInventory inventory = BAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }
                
                    // Unregister this listener if no more AdminInventories exist
                    if (BAInventories.isEmpty()) {
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
        if (!BAInventories.containsKey(playerId)) return;
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
                case STAND_17:
                    handleEditStand(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;   
                case NUMBER_OF_DECKS:
                    handleEditDecks(player);
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
                            player.sendMessage("§cInvalid blackjack settings option selected.");
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

    private void handleEditStand(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localMob.put(playerId, dealer);
        AdminInventory.standOn17Mode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new stand on 17 percent in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType new stand on 17 percent value between 0 and 100 in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }

    private void handleEditDecks(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localMob.put(playerId, dealer);
        AdminInventory.decksEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new number of decks.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }


    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localMob.put(playerId, dealer);
        AdminInventory.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new timer in chat.");
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

        if (BAInventories.get(playerId) == null){
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
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
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
                AdminInventory.localMob.remove(playerId);
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find blackjack settings dealer.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }

                
            cleanup();
        }
        else if(AdminInventory.standOn17Mode.get(playerId) != null) {
            event.setCancelled(true);
            String newTimer = event.getMessage().trim();

            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) < 0|| Integer.parseInt(newTimer) >100) {
                denyAction(player, "Please enter a number between 0 and 100");
                return;
            }
            if (dealer != null) {
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".stand-on-17", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cDealer stand on 17 chance updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer stand on 17 chance updated to: " + ChatColor.YELLOW + newTimer + "§a.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
                AdminInventory.localMob.remove(playerId);
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find blackjack settings dealer.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }

                
            cleanup();
        }
        else if(AdminInventory.decksEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newDecks = event.getMessage().trim();

            if (newDecks.isEmpty() || !newDecks.matches("\\d+") || Integer.parseInt(newDecks) <= 0) {
                denyAction(player, "Please enter a postive number");
                return;
            }
            if (dealer != null) {
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", Integer.parseInt(newDecks));
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§aDealer number of decks updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer number of decks updated to: " + ChatColor.YELLOW + newDecks + "§a.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
                AdminInventory.localMob.remove(playerId);
            } else {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find blackjack settings dealer.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }

                
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
