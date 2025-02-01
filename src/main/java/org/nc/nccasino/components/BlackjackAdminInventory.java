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

public class BlackjackAdminInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Villager dealer;
    public static final Map<UUID, BlackjackAdminInventory> BAInventories = new HashMap<>();


    private enum SlotOption {
        RETURN,
        EDIT_TIMER,
        STAND_17,
        NUMBER_OF_DECKS
     }
     private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
         put(SlotOption.RETURN, 0);
         put(SlotOption.EDIT_TIMER, 1);
         put(SlotOption.STAND_17, 2);
         put(SlotOption.NUMBER_OF_DECKS, 3);
     }};

    public BlackjackAdminInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
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
        String internalName = DealerVillager.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")? config.getInt("dealers." + internalName + ".timer"): 10; 
        int standOn17Chance = config.getInt("dealers." + internalName + ".stand-on-17", 100);
        int numberOfDecks = config.getInt("dealers." + internalName + ".number-of-decks", 6);
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: " + currentTimer);
        addItemAndLore(Material.SHIELD, standOn17Chance, "Change Stand On 17 Chance", slotMapping.get(SlotOption.STAND_17), "Current: " + standOn17Chance + "%");
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, numberOfDecks, "Change Number of Decks", slotMapping.get(SlotOption.NUMBER_OF_DECKS), "Current: " + numberOfDecks);
        addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return to "+returnName), 0);
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
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    executeReturn();
                    break;
                case EDIT_TIMER:
                    handleEditTimer(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                case STAND_17:
                    handleEditStand(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;   
                case NUMBER_OF_DECKS:
                    handleEditDecks(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;    
                default:
                    if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                    player.sendMessage("§cInvalid option selected.");
                    break;
            }}
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
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
        AdminInventory.localVillager.put(playerId, dealer);
        AdminInventory.standOn17Mode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType new stand on 17 percent value between 0 and 100.");
    }

    private void handleEditDecks(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localVillager.put(playerId, dealer);
        AdminInventory.decksEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new number of decks.");
    }


    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminInventory.localVillager.put(playerId, dealer);
        AdminInventory.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new timer in chat.");
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
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer timer updated to: " + ChatColor.YELLOW + newTimer + "§a.");
                AdminInventory.localVillager.remove(playerId);
            } else {
                player.sendMessage("§cCould not find dealer.");
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
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".stand-on-17", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer stand on 17 chance updated to: " + ChatColor.YELLOW + newTimer + "§a.");
                AdminInventory.localVillager.remove(playerId);
            } else {
                player.sendMessage("§cCould not find dealer.");
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
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".number-of-decks", Integer.parseInt(newDecks));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer number of decks chance updated to: " + ChatColor.YELLOW + newDecks + "§a.");
                AdminInventory.localVillager.remove(playerId);
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();
        }

    }
    private void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
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
