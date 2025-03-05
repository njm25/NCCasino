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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.listeners.DealerEventListener;

import net.md_5.bungee.api.ChatColor;

public class AdminMenu extends Menu {

    /**
     * We store a reference to the "owner" Player's UUID so we know
     * which player maps we must remove references from in cleanup().
     */
    private Mob dealer;
    // Track click state per player
    private int chipIndex=1;
    // Static maps referencing AdminInventory or the player's editing states
    private static final Map<UUID, Mob> moveMode = new HashMap<>();
    private static final Map<UUID, Mob> nameEditMode = new HashMap<>();
    public static final Map<UUID, Mob> timerEditMode = new HashMap<>();
    public static final Map<UUID, Mob> standOn17Mode = new HashMap<>();
    public static final Map<UUID, Mob> editMinesMode = new HashMap<>();
    private static final Map<UUID, Mob> amsgEditMode = new HashMap<>();
    private static final Map<UUID, Mob> chipEditMode = new HashMap<>();
    private static final Map<UUID, Mob> currencyEditMode = new HashMap<>();
    public static final Map<UUID, Mob> decksEditMode = new HashMap<>();
    // All active AdminInventories by player ID
    public static final Map<UUID, AdminMenu> adminInventories = new HashMap<>();
    // Tracks which dealer is being edited by which player
    public static final Map<UUID, Mob> localMob = new HashMap<>();
    private final Map<UUID, Boolean> movingDealers = new HashMap<>();


        private enum CurrencyMode {
            VANILLA,
            CUSTOM,
            VAULT
        }
        private CurrencyMode currencyMode;



    /**
     * Constructor: creates an AdminInventory for a specific dealer, owned by a specific player.
     */
    public AdminMenu(UUID dealerId, Player player, Nccasino plugin) {

        super(
            player, 
            plugin, 
            dealerId, 
            Dealer.getInternalName((Mob) player.getWorld()
            .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
            .filter(entity -> entity instanceof Mob)
            .map(entity -> (Mob) entity)
            .filter(v -> Dealer.isDealer(v)
                        && Dealer.getUniqueId(v).equals(dealerId))
            .findFirst().orElse(null))
    
            + "'s Admin Menu",
                    45,
                    null,
                    null
        );
        // Find the actual Villager instance (the "dealer")
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

//////////VVVVVVVVVVVVVexpand to retrieve mode from config once thats set up
        this.currencyMode = CurrencyMode.VANILLA;
        adminInventories.put(this.ownerId, this);
        setupSlotMapping();
        initializeMenu();
        registerListener();
    }
   public UUID getDealerId(){
    return dealerId;
   }

   private final void setupSlotMapping(){
    slotMapping.clear();
    slotMapping.put(SlotOption.EDIT_DISPLAY_NAME, 6);
    slotMapping.put(SlotOption.EDIT_GAME_TYPE, 0);
    slotMapping.put(SlotOption.GAME_OPTIONS, 2);
    slotMapping.put(SlotOption.EDIT_ANIMATION_MESSAGE, 8);
    // put(SlotOption.USE_VAULT, 28);   
    slotMapping.put(SlotOption.EDIT_CURRENCY, 31);
    //put(SlotOption.TOGGLE_CURRENCY_MODE, 31);
    slotMapping.put(SlotOption.EXIT, 36);
    slotMapping.put(SlotOption.PM, 38);

    slotMapping.put(SlotOption.MOVE_DEALER, 42);
    slotMapping.put(SlotOption.DELETE_DEALER, 44);
    slotMapping.put(SlotOption.CHIP_SIZE1, 20);
    slotMapping.put(SlotOption.CHIP_SIZE2, 21);
    slotMapping.put(SlotOption.CHIP_SIZE3, 22);
    slotMapping.put(SlotOption.CHIP_SIZE4, 23);
    slotMapping.put(SlotOption.CHIP_SIZE5, 24);
    slotMapping.put(SlotOption.MOB_SELECTION, 4);

   }


    /**
     * Registers this inventory as an event listener with the plugin.
     */
    private void registerListener() {
        HandlerList.unregisterAll(this); // Ensure no duplicate listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    @Override
    protected void initializeMenu() {


        String internalName = Dealer.getInternalName(dealer);
    
        // Retrieve dealer config values safely
        FileConfiguration config = plugin.getConfig();
        String currentGame = config.getString("dealers." + internalName + ".game", "Unknown");
  
    
        String currentAnimationMessage = config.getString("dealers." + internalName + ".animation-message", "Default Message");
    
        //String currencyMaterial = config.getString("dealers." + internalName + ".currency.material", "UNKNOWN");
       // String currencyName = config.getString("dealers." + internalName + ".currency.name", "Unknown Currency");
        addItemAndLore(Material.NAME_TAG, 1, "Edit Display Name",  slotMapping.get(SlotOption.EDIT_DISPLAY_NAME), "Current: §a" + Dealer.getName(dealer));
        switch(currentGame){
            case"Mines":{
                addItemAndLore(Material.TNT, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }
            case"Roulette":{
                addItemAndLore(Material.ENDER_PEARL, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }  
            case"Blackjack":{
                addItemAndLore(Material.CREEPER_HEAD, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }
            case "Baccarat":{
                addItemAndLore(Material.SKELETON_SKULL, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }
            case "Coin Flip":{
                addItemAndLore(Material.SUNFLOWER, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }
            case "Dragon Descent":{
                addItemAndLore(Material.DRAGON_HEAD, 1, "Edit Game Type",  slotMapping.get(SlotOption.EDIT_GAME_TYPE), "Current: §a" + currentGame);
                break;
            }
            default:
            break;
        }
        
        List<String> gameSettingsLore = getGameSettingsLore(config, internalName, currentGame);
        addItemAndLore(Material.BOOK, 1, currentGame + " Settings", slotMapping.get(SlotOption.GAME_OPTIONS), gameSettingsLore.toArray(new String[0]));
    
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, 1, "Edit Animation Message",  slotMapping.get(SlotOption.EDIT_ANIMATION_MESSAGE), "Current: §a" + currentAnimationMessage);
       /*  addItem(createCustomItem(Material.GOLD_INGOT, "Edit Currency", "Current: " + currencyName + " (" + currencyMaterial + ")"),slotMapping.get(SlotOption.EDIT_CURRENCY));*/
        addItemAndLore(Material.COMPASS, 1, "Move Dealer",  slotMapping.get(SlotOption.MOVE_DEALER));
        addItemAndLore(Material.BARRIER, 1, "Delete Dealer",  slotMapping.get(SlotOption.DELETE_DEALER));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));

        ItemStack head=createPlayerHeadItem(player, 1);
        setCustomItemMeta(head,"Player Menu");
        ItemMeta meta = head.getItemMeta();
    
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW+ "Player Menu");
            head.setItemMeta(meta);
        }

        addItem(head,slotMapping.get(SlotOption.PM) );
        updateCurrencyButtons();
        Material mobEgg = MobSelectionMenu.getSpawnEggFor(dealer.getType());

        // Now display that egg item in the slot
        List<String> lore = getMobSelectionLore(dealer);
        addItemAndLore(mobEgg, 1, "Edit Dealer Mob", slotMapping.get(SlotOption.MOB_SELECTION), lore.toArray(new String[0]));

    }
    
    private List<String> getGameSettingsLore(FileConfiguration config, String internalName, String gameType) {
        List<String> lore = new ArrayList<>();
        
        switch (gameType) {
            case "Blackjack":
                int blackjackTimer = config.getInt("dealers." + internalName + ".timer", 30);
                int standOn17Chance = config.getInt("dealers." + internalName + ".stand-on-17", 100);
                int blackjackDecks = config.getInt("dealers." + internalName + ".number-of-decks", 6);
                lore.add("§7Timer: §a" + blackjackTimer);
                lore.add("§7Stand On 17 Chance: §a" + standOn17Chance + "%");
                lore.add("§7# of Decks: §a" + blackjackDecks);
                break;
    
            case "Roulette":
                int rouletteTimer = config.getInt("dealers." + internalName + ".timer", 30);
                lore.add("§7Timer: §a" + rouletteTimer);
                break;
    
            case "Mines":
                int defaultMines = config.getInt("dealers." + internalName + ".default-mines", 3);
                lore.add("§7Default # of Mines: §a" + defaultMines);
                break;
    
            case "Baccarat":
                int baccaratTimer = config.getInt("dealers." + internalName + ".timer", 30);
                int baccaratDecks = config.getInt("dealers." + internalName + ".number-of-decks", 8);
                lore.add("§7Timer: §a" + baccaratTimer);
                lore.add("§7# of Decks: §a" + baccaratDecks);
                break;
    
            case "Coin Flip":
                int coinFlipTimer = config.getInt("dealers." + internalName + ".timer", 30);
                lore.add("§7Timer: §a" + coinFlipTimer);
                break;
    
            default:
                lore.add("§7No settings available.");
                break;
        }
        
        return lore;
    }
    
    /**
     * Returns whether the player is currently editing something else (rename, timer, etc.).
     */
    public static boolean isPlayerOccupied(UUID playerId) {
        Mob mob = nameEditMode.get(playerId);
        return (mob != null)
            || (standOn17Mode.get(playerId) != null)
            || (editMinesMode.get(playerId) != null)
            || (timerEditMode.get(playerId) != null)
            || (amsgEditMode.get(playerId) != null)
            || (moveMode.get(playerId) != null)
            || (chipEditMode.get(playerId) != null)
            || (localMob.get(playerId) != null)
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
    public static List<Mob> getOccupiedDealers(UUID playerId) {
        List<Mob> mobs = new ArrayList<>();
    
        Mob mob;
    
        if ((mob = nameEditMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = timerEditMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = standOn17Mode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = editMinesMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = amsgEditMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = moveMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = chipEditMode.get(playerId)) != null) {
            mobs.add(mob);
        }
        if ((mob = currencyEditMode.get(playerId)) != null) {
            mobs.add(mob);
        }
    
        return mobs;
    }
    
    /**
     * Handle inventory clicks for AdminInventory.
     */
    @Override
    public void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {

  
        // Throttle clicking slightly to prevent spam
        String internalName = Dealer.getInternalName(dealer);
        String currentGame = plugin.getConfig().getString("dealers." + internalName + ".game", "Unknown");

        switch (option) {
            case EDIT_DISPLAY_NAME:
                handleEditDealerName(player);
                playDefaultSound(player);
                break;
            case EDIT_GAME_TYPE:
                handleSelectGameType(player);
                playDefaultSound(player);
                break;
            case MOVE_DEALER:
                handleMoveDealer(player);
                playDefaultSound(player);
                break;
            case DELETE_DEALER:
                handleDeleteDealer(player);
                playDefaultSound(player);
                break;
            case EDIT_CURRENCY:
                handleEditCurrency(player,event);
                playDefaultSound(player);
                break;
                /* 
            case USE_VAULT:
                handleUseVault(player);
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                break;*/
                case TOGGLE_CURRENCY_MODE:
                /*
                handleToggleCurrencyMode(player);
                playDefaultSound(player);
            */  break;
            case GAME_OPTIONS:
                playDefaultSound(player);
                handleGameOptions(player,currentGame);
                break;
            case EDIT_ANIMATION_MESSAGE:
                handleAnimationMessage(player);
                playDefaultSound(player);
                break;
            case CHIP_SIZE1:
                handleEditChipSize(player,1);
                playDefaultSound(player);
                break;
            case CHIP_SIZE2:
                handleEditChipSize(player,2);
                playDefaultSound(player);
                break;
            case CHIP_SIZE3:
                handleEditChipSize(player,3);
                playDefaultSound(player);
                break;
            case CHIP_SIZE4:
                handleEditChipSize(player,4);
                playDefaultSound(player);
                break;
            case CHIP_SIZE5:
                handleEditChipSize(player,5);
                playDefaultSound(player);
                break;
            case PM:
                playDefaultSound(player);
                handlePlayerMenu(player);
                break;
            case MOB_SELECTION:
                handleMobSelection(player);
                playDefaultSound(player);
                break;
            case TEST_MENU:
                handleTestMenu(player);
                playDefaultSound(player);
                break;
            default:
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

    private void handleMobSelection(Player player) {
        MobSelectionMenu mobSelectionInventory = new MobSelectionMenu(player, plugin, dealerId, (p) -> {
            if (adminInventories.containsKey(player.getUniqueId())) {
                player.openInventory(adminInventories.get(player.getUniqueId()).getInventory());
            } else {
                AdminMenu newAdminInventory = new AdminMenu(dealerId, player, plugin);
                player.openInventory(newAdminInventory.getInventory());
            }
        }, Dealer.getInternalName(dealer) + "'s Admin Menu");

        player.openInventory(mobSelectionInventory.getInventory());
    }

    private void handlePlayerMenu(Player player) {
        if (player.hasPermission("nccasino.playermenu")){
            PlayerMenu pm = new PlayerMenu(player, plugin,dealerId, (p) -> {


                if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                    AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                    player.openInventory(adminInventory.getInventory());
                } else {
                    AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                    player.openInventory(adminInventory.getInventory());
                }
            },
                Dealer.getInternalName(dealer) + "'s Admin Menu"
            );
            player.openInventory(pm.getInventory());
        }
        else {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the player menu.");
        }
    }

    private void handleTestMenu(Player player) {
        String returnMessage = "Return to "+ Dealer.getInternalName(dealer) + "'s Admin Menu";
        TestMenu testMenu = new TestMenu(player, plugin, dealerId, returnMessage,(p) -> {
            if (adminInventories.containsKey(player.getUniqueId())) {
                player.openInventory(adminInventories.get(player.getUniqueId()).getInventory());
            } else {
                AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                player.openInventory(adminInventory.getInventory());
            }
        });
    
        player.openInventory(testMenu.getInventory());
    }
    
    // ----- Option handlers -----
    private void handleGameOptions(Player player, String currentGame) {
        switch(currentGame){
            case "Mines":{
                MinesMenu minesAdminInventory = new MinesMenu(
                    dealerId,
                    player,
                    Dealer.getInternalName(dealer)+ "'s Mines Settings",
                    (uuid) -> {

                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,Dealer.getInternalName(dealer)+ "'s Admin Menu"
            );
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
                RouletteMenu rouletteAdminInventory = new RouletteMenu(
                    dealerId,
                    player,
                    Dealer.getInternalName(dealer)+ "'s Roulette Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,Dealer.getInternalName(dealer)+ "'s Admin Menu"
            );
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
            case "Coin Flip":{
                CoinFlipMenu rouletteAdminInventory = new CoinFlipMenu(
                    dealerId,
                    player,
                    Dealer.getInternalName(dealer)+ "'s Coin Flip Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,Dealer.getInternalName(dealer)+ "'s Admin Menu"
            );
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
            case "Baccarat":{
                BaccaratMenu baccaratMenuAdminInventory = new BaccaratMenu(
                    dealerId,
                    player,
                    Dealer.getInternalName(dealer)+ "'s Baccarat Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,Dealer.getInternalName(dealer)+ "'s Admin Menu"
            );
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            switch(messPref){
                case VERBOSE:{
                    //player.sendMessage("Baccarat Settings Opened.");
                    break;}
                default:{
                    break;}
            }
                player.openInventory(baccaratMenuAdminInventory.getInventory());

                break;
            }
            case "Blackjack":{
                BlackjackMenu blackjackAdminInventory = new BlackjackMenu(
                    dealerId,
                    player,
                    Dealer.getInternalName(dealer)+ "'s Blackjack Settings",
                    (uuid) -> {
        
                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                        }
        
                    },
                    plugin,Dealer.getInternalName(dealer)+ "'s Admin Menu"
            );
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
/* 
    private void handleToggleCurrencyMode(Player player) {

        CurrencyMode next;
        switch (this.currencyMode) {
            case VANILLA: next = CurrencyMode.CUSTOM; break;
            case CUSTOM:  next = CurrencyMode.VAULT;  break;
            default:      next = CurrencyMode.VANILLA; break;
        }
        this.currencyMode = next;

        
        // Update the config
        if (dealer != null) {
            String internalName = Dealer.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + ".currency.mode", next.name());
            plugin.saveConfig();
        }

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
    */
    private void updateCurrencyButtons() {
        String internalName= Dealer.getInternalName(dealer);
        Inventory inv = getInventory();
        if (inv == null) return;
        switch(this.currencyMode){
            case CurrencyMode.VAULT:
                addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1, "Select Currency",  slotMapping.get(SlotOption.EDIT_CURRENCY), "[Disabled For Vault Mode]");

                //addItem(createCustomItem(Material.GRAY_STAINED_GLASS_PANE, "Select Currency [Disabled For Vault Mode]"),slotMapping.get(SlotOption.EDIT_CURRENCY));
                //addItem(createCustomItem(Material.CHEST,"Toggle Currency Mode: " + currencyMode.name()),slotMapping.get(SlotOption.TOGGLE_CURRENCY_MODE));
            break;
            case CurrencyMode.VANILLA:
                addItemAndLore(plugin.getCurrency(internalName), 1, "Select Currency",  slotMapping.get(SlotOption.EDIT_CURRENCY),"Current: §a"+plugin.getCurrencyName(internalName), "Drag or shift-click item here to change");
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
                addItemAndLore(plugin.getCurrency(internalName), chipValue, "Edit Chip Size #" + i,  slotMapping.get(SlotOption.valueOf("CHIP_SIZE" + i)), "Current: §a" + chipValue);
    } 
    }

    private void handleEditChipSize(Player player,int chipInd) {
        chipIndex=chipInd;
        UUID playerId = player.getUniqueId();
        localMob.put(playerId, dealer);
        chipEditMode.put(playerId, dealer);
        player.closeInventory();
        
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
        localMob.put(playerId, dealer);
        amsgEditMode.put(playerId, dealer);
        player.closeInventory();
        
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
        localMob.put(playerId, dealer);
        player.closeInventory();
        
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
        GameOptionsMenu inventory = new GameOptionsMenu(dealerId, player, plugin, dealer,
        (uuid) -> {
        
            // Cancel action: re-open the AdminInventory
            if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                player.openInventory(adminInventory.getInventory());
            } else {
                AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                player.openInventory(adminInventory.getInventory());
            }

        });
        player.openInventory(inventory.getInventory());
    }

    private void handleMoveDealer(Player player) {
        UUID playerId = player.getUniqueId();

        moveMode.put(playerId, dealer);
        localMob.put(playerId, dealer);
        player.closeInventory();
        
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
        localMob.put(player.getUniqueId(), dealer);

        if (deleteItem != null && deleteItem.getType() == Material.BARRIER) {
            // Open confirmation inventory
            ConfirmMenu confirmInventory = new ConfirmMenu(
                    player,
                    dealerId,
                    "Are you sure?",
                    (uuid) -> {
                        // Confirm action
                        player.closeInventory();
                        UUID pid = player.getUniqueId();
                        clearAllEditModes(dealer);
                        // Remove editing references
                        nameEditMode.remove(pid);
                        amsgEditMode.remove(pid);
                        timerEditMode.remove(pid);
                        moveMode.remove(pid);
                        localMob.remove(pid);
                        adminInventories.remove(pid);

                        // Delete this AdminInventory
                        // Overridden delete() below calls cleanup() 
                        delete();

                        // Execute your "delete" command
                        Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
                    },
                    (uuid) -> {

                        // Cancel action: re-open the AdminInventory
                        if (AdminMenu.adminInventories.containsKey(player.getUniqueId())) {
                            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
                            player.openInventory(adminInventory.getInventory());
                            localMob.remove(player.getUniqueId());
                        } else {
                            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
                            player.openInventory(adminInventory.getInventory());
                            localMob.remove(player.getUniqueId());
                        }

                    },
                    plugin
            );

            player.openInventory(confirmInventory.getInventory());
        }
    }

    private void handleEditCurrency(Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        AdminMenu adminInv = adminInventories.get(playerId);
        
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
        handleDrag(cursorItem, player, event);
    }


    public void handleDrag(ItemStack item, Player player, InventoryClickEvent event){

        if (item != null && item.getType() != Material.AIR) {
            ItemStack selectedItem = item.clone();
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
            String internalName = Dealer.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + ".currency.material", selectedMaterial.name());
            plugin.getConfig().set("dealers." + internalName + ".currency.name", displayName);
            plugin.saveConfig();
    
            // Update inventory display
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateCurrencyButtons();
                player.updateInventory(); // Ensure client sees the change immediately
            }, 1L);
    
            Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            plugin.reloadDealer(dealer);
            switch(messPref){
                case STANDARD:{
                    player.sendMessage("§aCurrency updated.");
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

        if (this.player.getUniqueId() != playerId) {
            return;
        }

        if (!adminInventories.containsKey(playerId)) {
            return;
        }
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
                Dealer.setName(dealer, newName);
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".display-name", newName);
                plugin.saveConfig();
                dealer.setCustomNameVisible(true);
                plugin.reloadDealer(dealer);
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

                
            clearAllEditModes(dealer);
            plugin.deleteAssociatedInventories(dealer);
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
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

                
            clearAllEditModes(dealer);
            plugin.deleteAssociatedInventories(dealer);
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
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".animation-message", newAmsg);
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

                
            clearAllEditModes(dealer);
            plugin.deleteAssociatedInventories(dealer);
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
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".chip-sizes.size" + chipIndex,Integer.parseInt(newChipSize));
                plugin.saveConfig();
                plugin.reloadDealer(dealer);
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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
                
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

                
            clearAllEditModes(dealer);
            plugin.deleteAssociatedInventories(dealer);
            cleanup();
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
    
        if (!moveMode.containsKey(playerId)) {
            return;
        }
        if (adminInventories.get(playerId) == null) {
            return;
        }
    
        if (event.getClickedBlock() != null) {
            Location newLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
            UUID dealerId = dealer.getUniqueId();
    
            // Prevent duplicate move executions using atomic check
            if (movingDealers.putIfAbsent(dealerId, true) != null) {
                player.sendMessage("§cDealer is already being moved. Please wait.");
                return;
            }
    
            // Force-load chunk
            newLocation.getChunk().setForceLoaded(true);
    
            // Start teleport attempt sequence
            attemptTeleport(player, newLocation, dealerId, 1);
    
            event.setCancelled(true);
        }
    }
    
    private void attemptTeleport(Player player, Location newLocation, UUID dealerId, int attempt) {
        // Ensure dealer is still in moving state
        if (!movingDealers.containsKey(dealerId)) {
            return; // Already teleported or canceled
        }
    
        Chunk chunk = newLocation.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }
    
        // Successful teleport
        if (chunk.isLoaded() && dealer != null && dealer.getUniqueId().equals(dealerId)) {
             DealerEventListener.allowAdminTeleport(dealer.getUniqueId()); // Allow this teleport
             dealer.setAI(true);
            dealer.teleport(newLocation);
            saveDealerLocation(newLocation);
            dealer.setAI(false);
            if (SoundHelper.getSoundSafely("item.chorus_fruit.teleport", player) != null) {
                player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER, 1.0f, 1.0f);
            }
    
            Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            switch (messPref) {
                case STANDARD:
                    player.sendMessage("§aDealer moved to new location.");
                    break;
                case VERBOSE:
                    player.sendMessage("§aDealer moved to x: " + ChatColor.YELLOW + newLocation.getX() +
                            "§a y: " + ChatColor.YELLOW + newLocation.getY() +
                            "§a z: " + ChatColor.YELLOW + newLocation.getZ() + "§a.");
                    break;
                case NONE:
                    break;
            }
    
            // Mark move as complete
            movingDealers.remove(dealerId);
            moveMode.remove(player.getUniqueId());
            plugin.deleteAssociatedInventories(dealer);
            clearAllEditModes(dealer);
            cleanup();
            return;
        }
    
        // Retry at increasing intervals
        if (attempt == 1) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptTeleport(player, newLocation, dealerId, 10), 10L);
        } else if (attempt == 10) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptTeleport(player, newLocation, dealerId, 30), 30L);
        } else {
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            switch (messPref) {
                case STANDARD:
                    player.sendMessage("§cFailed to move dealer. Chunk did not load.");
                    break;
                case VERBOSE:
                    player.sendMessage("§cFailed to move dealer. Chunk did not load after 30 ticks.");
                    break;
                case NONE:
                    break;
            }
            movingDealers.remove(dealerId); // Ensure cleanup only occurs once
        }
    }
    

    private void saveDealerLocation(Location newLocation) {
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }

        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
        String path = "dealers." + Dealer.getInternalName(dealer);
        dealersConfig.set(path + ".world", dealer.getWorld().getName());
        dealersConfig.set(path + ".X", newLocation.getX());
        dealersConfig.set(path + ".Y", newLocation.getY());
        dealersConfig.set(path + ".Z", newLocation.getZ());

        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
            e.printStackTrace();
        }
        plugin.saveConfig();
    }



    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        if (moveMode.get(pid) != null) {
            event.setCancelled(true);
            
        Preferences.MessageSetting messPref=plugin.getPreferences(player.getUniqueId()).getMessageSetting();
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu){
            return;
        }
        if(!(event.getInventory().getHolder() instanceof AdminMenu)){
            return;
        }
        // Check if the player has an active AdminInventory
        if (adminInventories.containsKey(playerId)&&adminInventories.get(playerId).getDealerId()==dealerId) {

            // Check if the player is currently editing something
            if (!isPlayerOccupied(playerId)) {
                // Remove the AdminInventory and clean up references
                AdminMenu inventory = adminInventories.remove(playerId);
                
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

    @Override
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
        localMob.remove(ownerId);
        currencyEditMode.remove(ownerId);
    }

    public static void clearAllEditModes(Mob mob) {
        nameEditMode.values().removeIf(mob::equals);
        timerEditMode.values().removeIf(mob::equals);
        amsgEditMode.values().removeIf(mob::equals);
        chipEditMode.values().removeIf(mob::equals);
        currencyEditMode.values().removeIf(mob::equals);
    
        // Unregister any active AdminMenu associated with this Mob
        adminInventories.entrySet().removeIf(entry -> {
            AdminMenu menu = entry.getValue();
            if (menu.dealer.equals(mob)) {
                HandlerList.unregisterAll(menu);
                return true;
            }
            return false;
        });
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

    private static String formatEntityName(String entityName) {
        return Arrays.stream(entityName.toLowerCase().replace("_", " ").split(" "))
                     .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                     .collect(Collectors.joining(" "));
    }

    private List<String> getMobSelectionLore(Mob mob) {
        List<String> lore = new ArrayList<>();
        lore.add("Mob: §a" + formatEntityName(mob.getType().toString()));
            String sizeOrAge = getCurrentSizeOrAge(mob);
        if (!sizeOrAge.isEmpty()) {
            lore.add(sizeOrAge);
        }
            if (isComplicatedVariant(mob)) {
            lore.addAll(getComplexVariantDetails(mob));
        } else {
            String variant = getCurrentVariant(mob);
            if (!variant.isEmpty()) {
                lore.add("Variant: §a" + variant);
            }
        }
        return lore;
    }

    private String getCurrentVariant(Mob mob) {
    if (mob instanceof Cat cat) {
        return formatEntityName(cat.getCatType().toString());
    } else if (mob instanceof Fox fox) {
        return formatEntityName(fox.getFoxType().toString());
    } else if (mob instanceof Frog frog) {
        return formatEntityName(frog.getVariant().toString());
    } else if (mob instanceof Parrot parrot) {
        return formatEntityName(parrot.getVariant().toString());
    } else if (mob instanceof Rabbit rabbit) {
        return formatEntityName(rabbit.getRabbitType().toString());
    } else if (mob instanceof Axolotl axolotl) {
        return formatEntityName(axolotl.getVariant().toString());
    } else if (mob instanceof MushroomCow mooshroom) {
        return formatEntityName(mooshroom.getVariant().toString());
    } else if (mob instanceof Panda panda) {
        return formatEntityName(panda.getMainGene().toString());
    } else if (mob instanceof Villager villager) {
        return formatEntityName(villager.getVillagerType().toString());
    } else if (mob instanceof ZombieVillager zombieVillager) {
        return formatEntityName(zombieVillager.getVillagerType().toString());
    } else if (mob instanceof Sheep sheep) {
        return formatEntityName(sheep.getColor().toString());
    }
    return "";
    }

    private List<String> getComplexVariantDetails(Mob mob) {
    List<String> details = new ArrayList<>();
    if (mob instanceof Llama llama) {
        details.add("Current Color: §a" + formatEntityName(llama.getColor().toString()));
        details.add("Current Decor: §a" + getLlamaCarpetName(llama));
    } else if (mob instanceof Horse horse) {
        details.add("Current Color: §a" + formatEntityName(horse.getColor().toString()));
        details.add("Current Style: §a" + formatEntityName(horse.getStyle().toString()));
    } else if (mob instanceof TropicalFish fish) {
        details.add("Current Pattern: §a" + formatEntityName(fish.getPattern().toString()));
        details.add("Current Body Color: §a" + formatEntityName(fish.getBodyColor().toString()));
        details.add("Current Pattern Color: §a" + formatEntityName(fish.getPatternColor().toString()));
    }
    return details;
    }

    private String getLlamaCarpetName(Llama llama) {
        if (llama.getInventory().getDecor() != null) {
            return formatEntityName(llama.getInventory().getDecor().getType().toString().replace("_CARPET", ""));
        }
        return "None";
    }

    private boolean isComplicatedVariant(Mob mob) {
        return (mob instanceof Llama)
            || (mob instanceof Horse)
            || (mob instanceof TropicalFish);
    }

    private String getCurrentSizeOrAge(Mob mob) {
    if (mob instanceof Slime slime && !(mob instanceof MagmaCube)) {
        return "Size: §a" + slime.getSize();
    } else if (mob instanceof MagmaCube magmaCube) {
        return "Size: §a" + magmaCube.getSize();
    } else if (mob instanceof org.bukkit.entity.Ageable ageable&&!(mob instanceof Parrot) &&!(mob instanceof Frog) &&!(mob instanceof PiglinBrute) &&!(mob instanceof WanderingTrader)) {
        return "Age: §a" + (ageable.isAdult() ? "Adult" : "Baby");
    }
    return "";
    }
}
