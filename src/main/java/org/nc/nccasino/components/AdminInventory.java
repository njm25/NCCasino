package org.nc.nccasino.components;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.helpers.SoundHelper;

import net.md_5.bungee.api.ChatColor;

public class AdminInventory extends DealerInventory {

    /**
     * We store a reference to the "owner" Player's UUID so we know
     * which player maps we must remove references from in cleanup().
     */
    private final UUID ownerId;

    private UUID dealerId;
    private Villager dealer;
    private final Nccasino plugin;
    // Track click state per player
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();
    private int chipIndex=1;
    // Static maps referencing AdminInventory or the player's editing states
    private static final Map<UUID, Villager> moveMode = new HashMap<>();
    private static final Map<UUID, Villager> nameEditMode = new HashMap<>();
    public static final Map<UUID, Villager> timerEditMode = new HashMap<>();
    public static final Map<UUID, Villager> standOn17Mode = new HashMap<>();
    public static final Map<UUID, Villager> editMinesMode = new HashMap<>();
    private static final Map<UUID, Villager> amsgEditMode = new HashMap<>();
    private static final Map<UUID, Villager> chipEditMode = new HashMap<>();
    private static final Map<UUID, Villager> currencyEditMode = new HashMap<>();
    public static final Map<UUID, Villager> decksEditMode = new HashMap<>();
    // All active AdminInventories by player ID
    public static final Map<UUID, AdminInventory> adminInventories = new HashMap<>();
    private Preferences.MessageSetting messPref;
    // Tracks which dealer is being edited by which player
    public static final Map<UUID, Villager> localVillager = new HashMap<>();

    // Slot options for the admin menu
    private enum SlotOption {
        EDIT_DISPLAY_NAME,
        EDIT_GAME_TYPE,
        MOVE_DEALER,
        DELETE_DEALER,
        TOGGLE_CURRENCY_MODE,
        EDIT_CURRENCY,
        //USE_VAULT,
        GAME_OPTIONS,
        EDIT_ANIMATION_MESSAGE,
        CHIP_SIZE1,
        CHIP_SIZE2,
        CHIP_SIZE3,
        CHIP_SIZE4,
        CHIP_SIZE5,
        PM,
        EXIT

    }

        private enum CurrencyMode {
            VANILLA,
            CUSTOM,
            VAULT
        }
        private CurrencyMode currencyMode;

    // The slot positions of each option in the inventory
    private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EDIT_DISPLAY_NAME, 6);
        put(SlotOption.EDIT_GAME_TYPE, 0);
        put(SlotOption.GAME_OPTIONS, 2);
        put(SlotOption.EDIT_ANIMATION_MESSAGE, 8);

        // put(SlotOption.USE_VAULT, 28);   
        put(SlotOption.EDIT_CURRENCY, 13);
        put(SlotOption.TOGGLE_CURRENCY_MODE, 31);
        put(SlotOption.EXIT, 36);
        put(SlotOption.PM, 38);

        put(SlotOption.MOVE_DEALER, 42);
        put(SlotOption.DELETE_DEALER, 44);
        put(SlotOption.CHIP_SIZE1, 20);
        put(SlotOption.CHIP_SIZE2, 21);
        put(SlotOption.CHIP_SIZE3, 22);
        put(SlotOption.CHIP_SIZE4, 23);
        put(SlotOption.CHIP_SIZE5, 24);

    }};

    /**
     * Constructor: creates an AdminInventory for a specific dealer, owned by a specific player.
     */
    public AdminInventory(UUID dealerId, Player player, Nccasino plugin) {
        super(player.getUniqueId(), 45, 
        
            DealerVillager.getInternalName((Villager) player.getWorld()
                    .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                    .filter(entity -> entity instanceof Villager)
                    .map(entity -> (Villager) entity)
                    .filter(v -> DealerVillager.isDealerVillager(v)
                                && DealerVillager.getUniqueId(v).equals(dealerId))
                    .findFirst().orElse(null))

                    + "'s Admin Menu"
        ); 
        
        // ^ We are using the player's UUID as "dealerId" in the DealerInventory parent. That's your existing logic.

        this.ownerId = player.getUniqueId();  // Store the player's ID
        this.dealerId = dealerId;
        this.plugin = plugin;

        // Find the actual Villager instance (the "dealer")
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

//////////VVVVVVVVVVVVVexpand to retrieve mode from config once thats set up
        this.currencyMode = CurrencyMode.VANILLA;
        this.messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
        adminInventories.put(this.ownerId, this);
        initializeAdminMenu(player);
        registerListener();
    }
   public UUID getDealerId(){
    return dealerId;
   }
    /**
     * Registers this inventory as an event listener with the plugin.
     */
    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private void initializeAdminMenu(Player player ) {


        String internalName = DealerVillager.getInternalName(dealer);
    
        // Retrieve dealer config values safely
        FileConfiguration config = plugin.getConfig();
        String currentGame = config.getString("dealers." + internalName + ".game", "Unknown");
  
    
        String currentAnimationMessage = config.getString("dealers." + internalName + ".animation-message", "Default Message");
    
        //String currencyMaterial = config.getString("dealers." + internalName + ".currency.material", "UNKNOWN");
       // String currencyName = config.getString("dealers." + internalName + ".currency.name", "Unknown Currency");
        addItemAndLore(Material.NAME_TAG, 1, "Edit Display Name",  slotMapping.get(SlotOption.EDIT_DISPLAY_NAME), "Current: " + DealerVillager.getName(dealer));
        switch(currentGame){
            case"Mines":{
                addItemAndLore(Material.TNT, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: " + currentGame);
                break;
            }
            case"Roulette":{
                addItemAndLore(Material.ENDER_PEARL, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: " + currentGame);
                break;
            }  
            case"Blackjack":{
                addItemAndLore(Material.CREEPER_HEAD, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: " + currentGame);
                break;
            }
            default:
            break;
        }
        
        addItemAndLore(Material.BOOK, 1, "Game-Specific Options",  slotMapping.get(SlotOption.GAME_OPTIONS), "Current: " + currentGame);
        
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, 1, "Edit Animation Message",  slotMapping.get(SlotOption.EDIT_ANIMATION_MESSAGE), "Current: " + currentAnimationMessage);
       /*  addItem(createCustomItem(Material.GOLD_INGOT, "Edit Currency", "Current: " + currencyName + " (" + currencyMaterial + ")"),slotMapping.get(SlotOption.EDIT_CURRENCY));*/
        addItemAndLore(Material.COMPASS, 1, "Move Dealer",  slotMapping.get(SlotOption.MOVE_DEALER));
        addItemAndLore(Material.BARRIER, 1, "Delete Dealer",  slotMapping.get(SlotOption.DELETE_DEALER));
        addItemAndLore(Material.COMPASS, 1, "Move Dealer",  slotMapping.get(SlotOption.MOVE_DEALER));
        addItemAndLore(Material.BARRIER, 1, "Delete Dealer",  slotMapping.get(SlotOption.DELETE_DEALER));
        addItem( createCustomItem(Material.SPRUCE_DOOR, "Exit"),slotMapping.get(SlotOption.EXIT) );
        ItemStack head=createPlayerHeadItem(player, 1);
        setCustomItemMeta(head,"Player Menu");
        addItem(head,slotMapping.get(SlotOption.PM) );
        updateCurrencyButtons();
        
    }

  
    /**
     * Returns whether the player is currently editing something else (rename, timer, etc.).
     */
    public static boolean isPlayerOccupied(UUID playerId) {
        Villager villager = nameEditMode.get(playerId);
        return (villager != null)
            || (standOn17Mode.get(playerId) != null)
            || (editMinesMode.get(playerId) != null)
            || (timerEditMode.get(playerId) != null)
            || (amsgEditMode.get(playerId) != null)
            || (moveMode.get(playerId) != null)
            || (chipEditMode.get(playerId) != null)
            || (localVillager.get(playerId) != null)
            || (currencyEditMode.get(playerId) != null);

    }
    
    public static List<String> playerOccupations(UUID playerId) {
        List<String> occupations = new ArrayList<>();
    
        if (nameEditMode.get(playerId) != null) {
            occupations.add("display name");
        }
        if (timerEditMode.get(playerId) != null) {
            occupations.add("timer");
        }
        if (amsgEditMode.get(playerId) != null) {
            occupations.add("animation message");
        }
        if (moveMode.get(playerId) != null) {
            occupations.add("location");
        }
        if (chipEditMode.get(playerId) != null) {
            occupations.add("chip size");
        }
        if (standOn17Mode.get(playerId) != null) {
            occupations.add("stand on 17 chance");
        }
        if (editMinesMode.get(playerId) != null) {
            occupations.add("default # of mines");
        }
        if (currencyEditMode.get(playerId) != null) {///////////might never get to this
            occupations.add("selecting currency item");
        }
        return occupations;
    }
    public static List<Villager> getOccupiedVillagers(UUID playerId) {
        List<Villager> villagers = new ArrayList<>();
    
        Villager villager;
    
        if ((villager = nameEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = timerEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = standOn17Mode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = editMinesMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = amsgEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = moveMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = chipEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = currencyEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
    
        return villagers;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
    
        // 1) Make sure this event is for an AdminInventory
        if (!adminInventories.containsKey(playerId)) return;
        AdminInventory adminInv = adminInventories.get(playerId);
        if (adminInv == null) return;
    
        Inventory topInv = adminInv.getInventory();
        if (topInv == null) return;
        if(player.getOpenInventory().getTopInventory() != this.getInventory()){return;}
        // 2) Check where they clicked
        if (event.getClickedInventory() == null) return; // clicked outside any inventory
        boolean clickedTop = event.getClickedInventory().equals(topInv);
        int slot = event.getSlot();
    
        // 3) If user clicked inside the TOP (admin) inventory:
        if (clickedTop) {
                handleClick(slot, player,event);
        }
        else {
            // 4) Player clicked in their BOTTOM inventory
            if (event.isShiftClick()) {
                // By default, SHIFT-click will attempt to move items into the top inventory
                event.setCancelled(true);
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§cShift-click disabled.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cShift-click is disabled for the admin inventory.");
                        break;}
                    default:{
                        break;}
                }
            }
            else{

            }
        }
    }
    
    /**
     * Handle inventory clicks for AdminInventory.
     */
    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }

  
        UUID playerId = player.getUniqueId();
    if (clickAllowed.getOrDefault(playerId, true)) {
            // Throttle clicking slightly to prevent spam
        clickAllowed.put(playerId, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);
            String internalName = DealerVillager.getInternalName(dealer);
            String currentGame = plugin.getConfig().getString("dealers." + internalName + ".game", "Unknown");

            event.setCancelled(true);
            SlotOption option = getKeyByValue(slotMapping, slot);
            if (option != null) {
                switch (option) {
                    case EDIT_DISPLAY_NAME:
                        handleEditDealerName(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_GAME_TYPE:
                        handleSelectGameType(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case MOVE_DEALER:
                        handleMoveDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case DELETE_DEALER:
                        handleDeleteDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_CURRENCY:
                        handleEditCurrency(player,event);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                        /* 
                    case USE_VAULT:
                        handleUseVault(player);
                        if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;*/
                     case TOGGLE_CURRENCY_MODE:
                        handleToggleCurrencyMode(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case GAME_OPTIONS:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    handleGameOptions(player,currentGame);
                        break;
                    case EDIT_ANIMATION_MESSAGE:
                        handleAnimationMessage(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE1:
                        handleEditChipSize(player,1);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE2:
                        handleEditChipSize(player,2);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE3:
                        handleEditChipSize(player,3);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE4:
                        handleEditChipSize(player,4);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE5:
                        handleEditChipSize(player,5);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case PM:
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        handlePlayerMenu(player);
                        break;
                    case EXIT:
                        handleExit(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    default:
                    switch(messPref){
                        case STANDARD:{
                            player.sendMessage("§cInvalid option selected.");    
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§cInvalid Admin Menu option selected.");
                            break;}
                        default:{
                            break;}
                    }
                        
                        break;
                }
            }

        
        } else {
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§cPlease wait before clicking again!");    
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cClicking too fast, click not registered in admin menu.");
                    break;}
                default:{
                    break;}
            }
           
        }
    }

    private void handlePlayerMenu(Player player) {
        PlayerMenu pm = new PlayerMenu(player, plugin,dealerId, (p) -> {


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
        player.openInventory(pm.getInventory());
    }

    private void handleExit(Player player) {
        player.closeInventory();
        delete();
    }

    // ----- Option handlers -----
    private void handleGameOptions(Player player, String currentGame) {
        switch(currentGame){
            case "Mines":{
                MinesAdminInventory minesAdminInventory = new MinesAdminInventory(
                    dealerId,
                    player,
                    DealerVillager.getInternalName(dealer)+ "'s Mines Settings",
                    (uuid) -> {

                        // Cancel action: re-open the AdminInventory
                        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,DealerVillager.getInternalName(dealer)+ "'s Admin Menu"
            );
            switch(messPref){
                case VERBOSE:{
                    //player.sendMessage("§aMines Settings Opened.");
                    break;}
                default:{
                    break;}
            }
            player.openInventory(minesAdminInventory.getInventory());
                break;
            }
            case "Roulette":{
                RouletteAdminInventory rouletteAdminInventory = new RouletteAdminInventory(
                    dealerId,
                    player,
                    DealerVillager.getInternalName(dealer)+ "'s Roulette Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,DealerVillager.getInternalName(dealer)+ "'s Admin Menu"
            );
            switch(messPref){
                case VERBOSE:{
                    //player.sendMessage("§aRoulette Settings Opened.");
                    break;}
                default:{
                    break;}
            }
                player.openInventory(rouletteAdminInventory.getInventory());

                break;
            }
            case "Blackjack":{
                BlackjackAdminInventory blackjackAdminInventory = new BlackjackAdminInventory(
                    dealerId,
                    player,
                    DealerVillager.getInternalName(dealer)+ "'s Blackjack Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,DealerVillager.getInternalName(dealer)+ "'s Admin Menu"
            );
            switch(messPref){
                case VERBOSE:{
                    //player.sendMessage("§aBlackjack Settings Opened.");
                    break;}
                default:{
                    break;}
            }
                player.openInventory(blackjackAdminInventory.getInventory());
                break;
            }
            default: {
                break;
            }

        }
    }

    private void handleToggleCurrencyMode(Player player) {
        CurrencyMode next;
        switch (this.currencyMode) {
            case VANILLA: next = CurrencyMode.CUSTOM; break;
            case CUSTOM:  next = CurrencyMode.VAULT;  break;
            default:      next = CurrencyMode.VANILLA; break;
        }
        this.currencyMode = next;

        /* 
        // Update the config
        if (dealer != null) {
            String internalName = DealerVillager.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + ".currency.mode", next.name());
            plugin.saveConfig();
        }*/

        // Update the button labels in the admin inventory
        updateCurrencyButtons();
        switch(messPref){
            case VERBOSE:{
                player.sendMessage("§eSwitched currency mode to: §a" + this.currencyMode.name()+"§e."); 
                break;}
            default:{
                break;}
        }
     
    }

    private void updateCurrencyButtons() {
        String internalName= DealerVillager.getInternalName(dealer);
        Inventory inv = getInventory();
        if (inv == null) return;
        switch(this.currencyMode){
            case CurrencyMode.VAULT:
                addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1, "Select Currency",  slotMapping.get(SlotOption.EDIT_CURRENCY), "[Disabled For Vault Mode]");

                //addItem(createCustomItem(Material.GRAY_STAINED_GLASS_PANE, "Select Currency [Disabled For Vault Mode]"),slotMapping.get(SlotOption.EDIT_CURRENCY));
                //addItem(createCustomItem(Material.CHEST,"Toggle Currency Mode: " + currencyMode.name()),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
            break;
            case CurrencyMode.VANILLA:
                addItemAndLore(plugin.getCurrency(internalName), 1, "Select Currency",  slotMapping.get(SlotOption.EDIT_CURRENCY),"Current: §a"+plugin.getCurrencyName(internalName), "Drag item here to change");
                //addItem(createCustomItem(plugin.getCurrency(internalName), "Select Vanilla Currency"), slotMapping.get(SlotOption.EDIT_CURRENCY));
                //addItem(createCustomItem(Material.GRASS_BLOCK,"Toggle Currency Mode: " + currencyMode.name()),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
            break;
            case CurrencyMode.CUSTOM:
                addItem(createCustomItem(plugin.getCurrency(internalName), "Select Custom Currency",1),slotMapping.get(SlotOption.EDIT_CURRENCY));
                //addItem(createEnchantedItem(Material.GRASS_BLOCK,"Toggle Currency Mode: " + currencyMode.name(),1),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
                //addItem(createCustomItem(Material.ENDER_CHEST,"Toggle Currency Mode: " + currencyMode.name(),1),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
                break;
            default:
            addItem(createCustomItem(plugin.getCurrency(internalName), "Select Vanilla Currency"), slotMapping.get(SlotOption.EDIT_CURRENCY));
            //addItem(createCustomItem(Material.GRASS_BLOCK,"Toggle Currency Mode: " + currencyMode.name()),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
            break;
        }
            // Chip Sizes
            for (int i = 1; i <= 5; i++) {
                int chipValue = plugin.getConfig().contains("dealers." + internalName + ".chip-sizes.size" + i)
                    ? plugin.getConfig().getInt("dealers." + internalName + ".chip-sizes.size" + i)
                    : 1; // Default to 1 if missing
                addItemAndLore(plugin.getCurrency(internalName), chipValue, "Edit Chip Size #" + i,  slotMapping.get(SlotOption.valueOf("CHIP_SIZE" + i)), "Current: " + chipValue);
    } 
    }

    private void handleEditChipSize(Player player,int chipInd) {
        chipIndex=chipInd;
        UUID playerId = player.getUniqueId();
        localVillager.put(playerId, dealer);
        chipEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(chipInd){
        case 1:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size value in chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for the 1st chip size in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }
            break;
        }
        case 2:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size value in chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for the 2nd chip size in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }            
            break;
        }
        case 3:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size value in chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for the 3rd chip size in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }            
            break;
        }
        case 4:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size value in chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for the 4th chip size in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }            
            break;
        }
        case 5:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size value in chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for the 5th chip size in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }            
            break;
        }
        default:{
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aType new chip size valuein chat.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aType new value for chip #"+chipIndex+" in chat.");
                    break;}
                case NONE:{
                    player.sendMessage("§aType new value.");
                    break;
                }
            }   
            break;
        }
        
        }
    }

    private void handleAnimationMessage(Player player) {
        UUID playerId = player.getUniqueId();
        localVillager.put(playerId, dealer);
        amsgEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(messPref){
            case STANDARD:{
                player.sendMessage("§aType new message in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new animation message in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value."); break;
            }
        }   
    }

    private void handleEditDealerName(Player player) {
        UUID playerId = player.getUniqueId();
        nameEditMode.put(playerId, dealer);
        localVillager.put(playerId, dealer);
        player.closeInventory();
        switch(messPref){
            case STANDARD:{
                player.sendMessage("§aType new name in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new dealer name in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");break;
            }
        }   
    }

    private void handleSelectGameType(Player player) {
        // Open the Game Options Inventory
        GameOptionsInventory inventory = new GameOptionsInventory(plugin, dealer);
        player.openInventory(inventory.getInventory());
    }

    private void handleMoveDealer(Player player) {
        UUID playerId = player.getUniqueId();

        moveMode.put(playerId, dealer);
        localVillager.put(playerId, dealer);
        player.closeInventory();
        switch(messPref){
            case STANDARD:{
                player.sendMessage("§aClick a block to move the dealer.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aClick a block to move the dealer to that position.");
                 break;}
            case NONE:{
                player.sendMessage("§aClick destination.");break;
            }
        } 
    }

    private void handleDeleteDealer(Player player) {
        int slot = slotMapping.get(SlotOption.DELETE_DEALER);
        Inventory playerInventory = getInventory();
        ItemStack deleteItem = playerInventory.getItem(slot);
        localVillager.put(player.getUniqueId(), dealer);

        if (deleteItem != null && deleteItem.getType() == Material.BARRIER) {
            // Open confirmation inventory
            ConfirmInventory confirmInventory = new ConfirmInventory(
                    dealerId,
                    "Are you sure?",
                    (uuid) -> {
                        // Confirm action
                        player.closeInventory();
                        UUID pid = player.getUniqueId();

                        // Remove editing references
                        nameEditMode.remove(pid);
                        amsgEditMode.remove(pid);
                        timerEditMode.remove(pid);
                        moveMode.remove(pid);
                        localVillager.remove(pid);
                        adminInventories.remove(pid);

                        // Delete this AdminInventory
                        // Overridden delete() below calls cleanup() 
                        delete();

                        // Execute your "delete" command
                        Bukkit.dispatchCommand(player, "ncc delete " + DealerVillager.getInternalName(dealer));
                    },
                    (uuid) -> {

                        // Cancel action: re-open the AdminInventory
                        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                            localVillager.remove(player.getUniqueId());
                        } else {
                            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                            localVillager.remove(player.getUniqueId());
                        }

                    },
                    plugin
            );

            player.openInventory(confirmInventory.getInventory());
        }
    }

    private void handleEditCurrency(Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        AdminInventory adminInv = adminInventories.get(playerId);
        
        if (adminInv.currencyMode == CurrencyMode.VAULT) {
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§cCurrency selection is disabled in VAULT mode.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cCurrency selection is disabled in VAULT mode. Planned for the future");
                    break;}
                case NONE:{break;
                }
            } 
            return;
        }
    
        ItemStack cursorItem = event.getCursor(); // Item being dragged
    
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            ItemStack selectedItem = cursorItem.clone();
            selectedItem.setAmount(1); // Store a single reference item
    
            // Extract material and custom name
            Material selectedMaterial = selectedItem.getType();
            String rawName = selectedMaterial.name();
                    
            // Convert underscore to spaces and apply Pascal Case
            String displayName = Arrays.stream(rawName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
                    
            if (selectedItem.getItemMeta() != null && selectedItem.getItemMeta().hasDisplayName()) {
                displayName = selectedItem.getItemMeta().getDisplayName();
            }
            
            // Save to config
            String internalName = DealerVillager.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + ".currency.material", selectedMaterial.name());
            plugin.getConfig().set("dealers." + internalName + ".currency.name", displayName);
            plugin.saveConfig();
    
            // Update inventory display
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateCurrencyButtons();
                player.updateInventory(); // Ensure client sees the change immediately
            }, 1L);
    
            plugin.reloadDealerVillager(dealer);
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§cCurrency updated.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§aCurrency updated to: " + ChatColor.YELLOW + displayName + "§a (" + ChatColor.YELLOW + selectedMaterial.name() + "§a).");
                    break;}
                case NONE:{break;
                }
            } 
            event.setCancelled(true);
        }
    }

    // ----- Event handlers -----

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (adminInventories.get(playerId) == null){
            return;
        }

        // Editing dealer name
        if (nameEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newName = event.getMessage().trim();
            if (newName.isEmpty()) {
                denyAction(player, "Invalid name. Try again.");
                return;
            }
            if (dealer != null) {
                DealerVillager.setName(dealer, newName);
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".display-name", newName);
                plugin.saveConfig();
                dealer.setCustomNameVisible(true);
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§aDealer name updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer name updated to: '" + ChatColor.YELLOW + newName + "§a'.");
                        break;}
                    case NONE:{break;
                    }
                } 
            } else {
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find dealer for admin menu chat response.");

                        break;}
                    case NONE:{break;
                    }
                } 
            }

                
            cleanup();

        }
        // Editing dealer timer
        else if (timerEditMode.get(playerId) != null) {
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
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§aDealer timer updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer timer updated to: " + ChatColor.YELLOW + newTimer + "§a.");
                        break;}
                    case NONE:{break;
                    }
                } 
            } else {
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find dealer for admin menu chat response");
                        break;}
                    case NONE:{break;
                    }
                } 
            }

                
            cleanup();
        }
        // Editing dealer animation message
        else if (amsgEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newAmsg = event.getMessage().trim();

            if (newAmsg.isEmpty()) {
                denyAction(player, "Invalid input.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".animation-message", newAmsg);
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§aDealer animation message updated..");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aDealer animation message updated to: '" + ChatColor.YELLOW + newAmsg + "§a'.");
                        break;}
                    case NONE:{break;
                    }
                } 
            } else {
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find dealer for admin menu chat response.");

                        break;}
                    case NONE:{break;
                    }
                } 
            }

                
            cleanup();
        }
        else if (chipEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newChipSize = event.getMessage().trim();

            if (newChipSize.isEmpty() || !newChipSize.matches("\\d+") || Integer.parseInt(newChipSize) <= 0) {
                denyAction(player, "Please enter a positive number.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size" + chipIndex,Integer.parseInt(newChipSize));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§aChip size updated.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§aChip size "+chipIndex+" updated to: " + ChatColor.YELLOW + newChipSize + "§a.");
                        break;}
                    case NONE:{break;
                    }
                } 
            } else {
                switch(messPref){
                    case STANDARD:{
                        player.sendMessage("§cCould not find dealer.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cCould not find dealer for admin menu chat response.");
                        break;}
                    case NONE:{break;
                    }
                } 
            }

                
            cleanup();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (adminInventories.get(playerId) == null){
            return;
        }

        if (moveMode.get(playerId) != null) {

            if (event.getClickedBlock() != null) {
                Location newLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);

                if (dealer != null) {
                    dealer.teleport(newLocation);

                    // Save new location to your dealers.yaml
                    File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
                    if (!dealersFile.getParentFile().exists()) {
                        dealersFile.getParentFile().mkdirs();
                    }
                    FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
                    var chunk = dealer.getLocation().getChunk();

                    String path = "dealers." + DealerVillager.getInternalName(dealer);
                    dealersConfig.set(path + ".chunkX", chunk.getX());
                    dealersConfig.set(path + ".chunkZ", chunk.getZ());
                    // Optionally store world name if needed
                    // dealersConfig.set(path + ".world", dealer.getWorld().getName());

                    try {
                        dealersConfig.save(dealersFile);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
                        e.printStackTrace();
                    }
                    plugin.saveConfig();
                    if(SoundHelper.getSoundSafely("item.chorus_fruit.teleport",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
                    switch(messPref){
                        case STANDARD:{
                            player.sendMessage("§aDealer moved to new location.");
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§aDealer moved to new location x: " +ChatColor.YELLOW+newLocation.getX()+"§a y: "+ChatColor.YELLOW+newLocation.getY()+ "§a z: "+ChatColor.YELLOW+newLocation.getZ()+ "§a.");
                            break;}
                        case NONE:{break;
                        }
                    } 
                } else {
                    switch(messPref){
                        case STANDARD:{
                            player.sendMessage("§cCould not find dealer.");
                            break;}
                        case VERBOSE:{
                            player.sendMessage("§cCould not find dealer for admin menu chat response.");
                            break;}
                        case NONE:{break;
                        }
                    } 
                }

                cleanup();
            }
            event.setCancelled(true);
                
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        if (moveMode.get(pid) != null) {
            event.setCancelled(true);
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§cCan't do that.");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cYou cannot break blocks while moving the dealer.");
                    break;}
                case NONE:{break;
                }
            } 
        }
    }

    /**
     * Utility method to retrieve a SlotOption by the mapped slot number.
     */
    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }

  
    /**
     * Provide user feedback if an action is disallowed.
     */
    private void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no",player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        player.sendMessage("§c" + message);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(player.getOpenInventory().getTopInventory().getHolder() instanceof AdminInventory){
            return;
        }
        if(!(event.getInventory().getHolder() instanceof AdminInventory)){
            return;
        }
        // Check if the player has an active AdminInventory
        if (adminInventories.containsKey(playerId)&&adminInventories.get(playerId).getDealerId()==dealerId) {

            // Check if the player is currently editing something
            if (!isPlayerOccupied(playerId)) {
                // Remove the AdminInventory and clean up references
                AdminInventory inventory = adminInventories.remove(playerId);
                
                if (inventory != null) {
                    inventory.unregisterListener();
                    inventory.delete();
                }

                // Unregister this listener if no more AdminInventories exist
                if (adminInventories.isEmpty()) {
                    unregisterListener();
                }
            }
        }

    }
        , 5L);


    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
    
    // --------------------------------------------------------------------------------
    // GC / Cleanup Logic
    // --------------------------------------------------------------------------------

    /**
     * Override delete() from DealerInventory to ensure we do a complete cleanup
     * of references for this AdminInventory.
     */
    @Override
    public void delete() {
        cleanup();
        super.delete(); 
        // super.delete() removes from DealerInventory.inventories & clears the Inventory
    }

    /**
     * Cleanup references to this AdminInventory and unregister event listeners.
     * Once called, there should be no lingering references preventing GC.
     */
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        adminInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        moveMode.remove(ownerId);
        nameEditMode.remove(ownerId);
        timerEditMode.remove(ownerId);
        amsgEditMode.remove(ownerId);
        chipEditMode.remove(ownerId);
        localVillager.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    public static void deleteAssociatedAdminInventories(Player player) {
        if (adminInventories.get(player.getUniqueId()) != null){
            adminInventories.remove(player.getUniqueId());
        }
    }

    private ItemStack createPlayerHeadItem(Player player, int stackSize) {
    if (stackSize <= 0) {
        throw new IllegalArgumentException("Stack size must be greater than 0 for player head.");
    }

    ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, stackSize);
    SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
    if (skullMeta != null) {
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(player.getName());
        playerHead.setItemMeta(skullMeta);
    }
    return playerHead;
}

}
