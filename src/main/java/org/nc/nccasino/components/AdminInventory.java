package org.nc.nccasino.components;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
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
    private static final Map<UUID, Villager> timerEditMode = new HashMap<>();
    private static final Map<UUID, Villager> amsgEditMode = new HashMap<>();
    private static final Map<UUID, Villager> chipEditMode = new HashMap<>();
    private static final Map<UUID, Villager> currencyEditMode = new HashMap<>();
    // All active AdminInventories by player ID
    public static final Map<UUID, AdminInventory> adminInventories = new HashMap<>();

    // Tracks which dealer is being edited by which player
    public final Map<UUID, Villager> localVillager = new HashMap<>();

    // Slot options for the admin menu
    private enum SlotOption {
        EDIT_DISPLAY_NAME,
        EDIT_GAME_TYPE,
        MOVE_DEALER,
        DELETE_DEALER,
        TOGGLE_CURRENCY_MODE,
        EDIT_CURRENCY,
        //USE_VAULT,
        EDIT_TIMER,
        EDIT_ANIMATION_MESSAGE,
        CHIP_SIZE1,
        CHIP_SIZE2,
        CHIP_SIZE3,
        CHIP_SIZE4,
        CHIP_SIZE5,

    }

        private enum CurrencyMode {
            VANILLA,
            CUSTOM,
            VAULT
        }
        private CurrencyMode currencyMode;

    // The slot positions of each option in the inventory
    private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EDIT_DISPLAY_NAME, 2);
        put(SlotOption.EDIT_GAME_TYPE, 0);
        put(SlotOption.EDIT_TIMER, 6);
        put(SlotOption.EDIT_ANIMATION_MESSAGE, 8);

        // put(SlotOption.USE_VAULT, 28);   
        put(SlotOption.EDIT_CURRENCY, 13);
        put(SlotOption.TOGGLE_CURRENCY_MODE, 31);

        put(SlotOption.MOVE_DEALER, 37);
        put(SlotOption.DELETE_DEALER, 43);
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

        registerListener();
        adminInventories.put(this.ownerId, this);
        initializeAdminMenu();
    }

    /**
     * Registers this inventory as an event listener with the plugin.
     */
    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private void initializeAdminMenu() {


        String internalName = DealerVillager.getInternalName(dealer);
    
        // Retrieve dealer config values safely
        FileConfiguration config = plugin.getConfig();
        String currentGame = config.getString("dealers." + internalName + ".game", "Unknown");
        int currentTimer = config.contains("dealers." + internalName + ".timer")
            ? config.getInt("dealers." + internalName + ".timer")
            : 10; // Default to 10 if missing
    
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
        if(currentGame!="Mines"){
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: " + currentTimer);
    }
        else{
        addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Unvailable For Mines");
        }
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, 1, "Edit Animation Message",  slotMapping.get(SlotOption.EDIT_ANIMATION_MESSAGE), "Current: " + currentAnimationMessage);
       /*  addItem(createCustomItem(Material.GOLD_INGOT, "Edit Currency", "Current: " + currencyName + " (" + currencyMaterial + ")"),slotMapping.get(SlotOption.EDIT_CURRENCY));*/
        addItemAndLore(Material.COMPASS, 1, "Move Dealer",  slotMapping.get(SlotOption.MOVE_DEALER));
        addItemAndLore(Material.BARRIER, 1, "Delete Dealer",  slotMapping.get(SlotOption.DELETE_DEALER));
        // Chip Sizes
        for (int i = 1; i <= 5; i++) {
            int chipValue = config.contains("dealers." + internalName + ".chip-sizes.size" + i)
                ? config.getInt("dealers." + internalName + ".chip-sizes.size" + i)
                : 1; // Default to 1 if missing
            addItemAndLore(plugin.getCurrency(internalName), chipValue, "Edit Chip Size #" + i,  slotMapping.get(SlotOption.valueOf("CHIP_SIZE" + i)), "Current: " + chipValue);

    
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
    } 
    }

  
    /**
     * Returns whether the player is currently editing something else (rename, timer, etc.).
     */
    public static boolean isPlayerOccupied(UUID playerId) {
        Villager villager = nameEditMode.get(playerId);
        return (villager != null)
            || (timerEditMode.get(playerId) != null)
            || (amsgEditMode.get(playerId) != null)
            || (moveMode.get(playerId) != null)
            || (chipEditMode.get(playerId) != null)
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
                player.sendMessage("§cShift-click is disabled for the admin inventory.");
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
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_GAME_TYPE:
                        handleSelectGameType(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case MOVE_DEALER:
                        handleMoveDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case DELETE_DEALER:
                        handleDeleteDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_CURRENCY:
                        handleEditCurrency(player,event);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                        /* 
                    case USE_VAULT:
                        handleUseVault(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;*/
                     case TOGGLE_CURRENCY_MODE:
                        handleToggleCurrencyMode(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_TIMER:
                        if(currentGame.equals("Mines")){
                        if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                        }
                        else{
                        handleEditTimer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);}  
                        break;
                    case EDIT_ANIMATION_MESSAGE:
                        handleAnimationMessage(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE1:
                        handleEditChipSize(player,1);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE2:
                        handleEditChipSize(player,2);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE3:
                        handleEditChipSize(player,3);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE4:
                        handleEditChipSize(player,4);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case CHIP_SIZE5:
                        handleEditChipSize(player,5);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
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

    // ----- Option handlers -----
    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        localVillager.put(playerId, dealer);
        timerEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new timer in chat.");
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
        //player.sendMessage("§eSwitched currency mode to: §a" + this.currencyMode.name());
    }


    private void handleSelectCurrency(Player player) {
        
        if (this.currencyMode == CurrencyMode.VAULT) {
            player.sendMessage("§cCannot select currency item in VAULT mode.");
            return;
        }
        // Place the player into "select currency mode"
       //currencyEditMode.put(player.getUniqueId(), this.dealer);
        player.sendMessage("§aPlease click an item in your (bottom) inventory to set as the new currency...");
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
    }

    private void handleEditChipSize(Player player,int chipInd) {
        chipIndex=chipInd;
        UUID playerId = player.getUniqueId();
        localVillager.put(playerId, dealer);
        chipEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(chipInd){
        case 1:{
            player.sendMessage("§aType the new value for the 1st chip size.");
            break;
        }
        case 2:{
            player.sendMessage("§aType the new value for the 2nd chip size.");
            break;
        }
        case 3:{
            player.sendMessage("§aType the new value for the 3rd chip size.");
            break;
        }
        case 4:{
            player.sendMessage("§aType the new value for the 4th chip size.");
            break;
        }
        case 5:{
            player.sendMessage("§aType the new value for the 5th chip size.");
            break;
        }
        default:{
            player.sendMessage("§aType the new value for chip size number "+chipInd+".");
            break;
        }
        
        }
    }

    private void handleAnimationMessage(Player player) {
        UUID playerId = player.getUniqueId();
        localVillager.put(playerId, dealer);
        amsgEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new animation message in chat.");
    }

    private void handleEditDealerName(Player player) {
        UUID playerId = player.getUniqueId();
        nameEditMode.put(playerId, dealer);
        localVillager.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new dealer name in chat.");
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
        player.sendMessage("§aClick a block to move the dealer.");
    }

    private void handleDeleteDealer(Player player) {
        int slot = slotMapping.get(SlotOption.DELETE_DEALER);
        Inventory playerInventory = getInventory();
        ItemStack deleteItem = playerInventory.getItem(slot);

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
                        player.closeInventory();
                        //Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            AdminInventory adminInventory;
                            if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
                                adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                            } else {
                                adminInventory = new AdminInventory(dealerId, player, plugin);
                            }
                            player.openInventory(adminInventory.getInventory());
                    
                            // Force an inventory update to refresh client-side display
                            Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
                        //}, 2L);
                    },
                    plugin
            );

            player.openInventory(confirmInventory.getInventory());
        }
    }

    private void handleEditCurrency(Player player,InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        int slot = event.getSlot();
        AdminInventory adminInv = adminInventories.get(playerId);
        Inventory topInv = adminInv.getInventory();

        // If in Vault mode, just block it
        if (adminInv.currencyMode == CurrencyMode.VAULT) {
            player.sendMessage("§cCurrency slot disabled in VAULT mode.");
            return;
        }

        // CASE 1: Player is putting an item INTO the currency slot
        //   => either with a mouse cursor item or SHIFT-click from bottom
        // event.getCursor() is the item on their mouse pointer
        // event.getCurrentItem() is the item in the slot (if any)
        // SHIFT-click scenario: event.isShiftClick()

        // Check if they are SHIFT-clicking the same top slot (rare) or if there's an item in their cursor
        // Usually for SHIFT from bottom → top, event.getCurrentItem() is the stack from the bottom
        // but the slot is in top. 
        ItemStack cursor = event.getCursor();       // item on mouse
        ItemStack clickedItem = event.getCurrentItem(); // item in the clicked slot

        // If they are picking UP the ghost item from slot 13 to remove it:
        if (clickedItem != null && clickedItem.getType() != Material.AIR && !event.isShiftClick() && cursor == null) {
            // This means they tried to pick up the "ghost" item from slot 13
            //topInv.setItem(slot, null); // remove the ghost
            player.sendMessage("§cDrag an item into this slot to change the currency");
            return;
        }

        // Otherwise, they might be placing an item in:
        // SHIFT-click from bottom inventory → top slot 13
        /* 
        if (event.isShiftClick()) {
            // The stack is the one in the player's bottom slot
            // so we can get it from event.getCurrentItem() 
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                // Make a ghost copy
                ItemStack ghostCopy = clickedItem.clone();
                ghostCopy.setAmount(1); // or entire stack if you want
                topInv.setItem(slot, ghostCopy);

                // TODO: store to config as your "currency"
                // e.g. storeVanillaOrCustomCurrency(ghostCopy);

                player.sendMessage("§aCurrency updated (ghost copy).");
            }
            return;
        }*/

        // Normal mouse drag from bottom → slot 13
        if (cursor != null && cursor.getType() != Material.AIR) {
            // Create a ghost copy of what's on the mouse
            ItemStack ghostCopy = cursor.clone();
            ghostCopy.setAmount(1); // usually 1 is enough for "ghost"
            topInv.setItem(slot, ghostCopy);

            // TODO: store to config as your "currency"
            // e.g. storeVanillaOrCustomCurrency(ghostCopy);

            player.sendMessage("§aCurrency updated (ghost copy).");
        }

        return; // done handling slot 13
        /* 
        if (this.currencyMode == CurrencyMode.VAULT) {
            player.sendMessage("§cCannot select currency item in VAULT mode.");
            return;
        }
        UUID playerId = player.getUniqueId();
        currencyEditMode.put(player.getUniqueId(), this.dealer);
        localVillager.put(playerId, dealer);
        player.sendMessage("§aPlease click an item in your (bottom) inventory to set as the new currency...");*/
        // Implement currency editing logic here
    }

    private void handleUseVault(Player player) {
        player.sendMessage("§aUsing vault...");
        // Implement vault usage logic here
    }

    // ----- Event handlers -----

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (adminInventories.get(playerId) == null){
            cleanup();
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
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer name updated to: '" + ChatColor.YELLOW + newName + "§a'.");
            } else {
                player.sendMessage("§cCould not find dealer.");
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
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer timer updated to: " + ChatColor.YELLOW + newTimer + "§a.");
            } else {
                player.sendMessage("§cCould not find dealer.");
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
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer animation message updated to: '" + ChatColor.YELLOW + newAmsg + "§a'.");
            } else {
                player.sendMessage("§cCould not find dealer.");
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
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aChip size "+chipIndex+" updated to: " + ChatColor.YELLOW + newChipSize + "§a.");
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (adminInventories.get(playerId) == null){
            cleanup();
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
                    if(SoundHelper.getSoundSafely("item.chorus_fruit.teleport")!=null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
                    player.sendMessage("§aDealer moved to new location.");
                } else {
                    player.sendMessage("§cCould not find dealer.");
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
            player.sendMessage("§cYou cannot break blocks while moving the dealer.");
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
        if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        player.sendMessage("§c" + message);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if the player has an active AdminInventory
        if (adminInventories.containsKey(playerId)) {
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

}
