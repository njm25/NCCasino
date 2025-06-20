package org.nc.nccasino.entities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.MobSelectionMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstract class for all menu components.
 * Handles common menu functionality such as slot mappings, return callbacks, and click throttling.
 */
public abstract class Menu extends DealerInventory {

    protected enum SlotOption {

        // Default options
        EXIT,
        RETURN,

        // Complex variant menu
        COMPLEX_VAR_1,
        COMPLEX_VAR_2,
        COMPLEX_VAR_3,
        
        // Preferences menu
        SOUNDS,
        MESSAGES,

        // Player menu
        PREFERENCES,
        STATS,

        // Game options menu
        BLACKJACK,
        ROULETTE,
        MINES,
        BACCARAT,
        TEST_GAME,
        COIN_FLIP,
        DRAGON_DESCENT,
        
        // Default game settings
        EDIT_TIMER,
        
        // Mines settings menu
        EDIT_MINES,

        // Blackjack settings menu
        STAND_17,
        NUMBER_OF_DECKS,
        
        // Dragon Descent settings menu
        EDIT_COLUMNS,
        EDIT_VINES, 
        EDIT_FLOORS,

        YES,
        NO,

        // Mob selection menu
        PAGE_TOGGLE,
        VARIANT,
        AGE_TOGGLE,
        DELETE,

        // Admin menu
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
        //CHANGE_BIOME,
        MOB_SELECTION,
        MOB_SETTINGS,
        JOCKEY_MENU,

        // Test buttons
        TEST_MENU,
        ADD_JOCKEY,
        ADD_PASSENGER,
        REMOVE_JOCKEY,

        // Passenger menu
        INVISIBLE_PASSENGER,
        PICK_PASSENGER
    }

    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();
    protected final UUID ownerId;
    public final UUID dealerId;
    protected final Nccasino plugin;
    protected Consumer<Player> returnCallback;
    protected final String returnMessage;
    protected final Player player;
    public Mob dealer;

    /**
     * Constructor for a menu component.
     * @param player The player opening the menu.
     * @param plugin The plugin instance.
     * @param dealerId The UUID of the dealer.
     * @param title The title of the inventory.
     * @param returnCallback The function to execute when returning from this menu.
     */
    public Menu(
        Player player, 
        Nccasino plugin, 
        UUID dealerId, 
        String title, 
        int size,
        String returnMessage,
        Consumer<Player> returnCallback
    ) {
        super(player.getUniqueId(), size, title);
        this.dealerId = dealerId;
        this.ownerId = player.getUniqueId();
        this.plugin = plugin;
        this.returnMessage = returnMessage;
        this.player = player;
        this.returnCallback = returnCallback;
        if (this.dealer == null) {
            // Use the new findDealer method to locate the dealer
            this.dealer = Dealer.findDealer(this.dealerId, player.getLocation());
        }
     
        // Register this menu as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

    }

    /**
     * This method should be overridden by subclasses to define menu contents.
     */
    protected abstract void initializeMenu();

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {     
        if (this instanceof MobSelectionMenu) {
            ((MobSelectionMenu) this).handleEntityClick(player, event);
        }
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option == null) return;
        
        switch (option) {
            case EXIT -> handleExit(player);
            case RETURN -> executeReturn(player);
            default -> handleCustomClick(option, player, event);
        }
    }
    
    /**
     * Handles custom slot actions that subclasses should implement.
     */
    protected abstract void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event);
    
    /**
     * Handles returning to the previous menu if a return callback exists.
     */
    protected void executeReturn(Player player) {
        if (returnCallback != null) {
            playDefaultSound(player);
            returnCallback.accept(player);
        } else {
            player.sendMessage("§cNo return callback was set!");
            player.closeInventory();
        }
    }

    /**
     * Close event cleanup.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().getUniqueId().equals(ownerId)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof Menu) return;

            cleanup();
        }, 5L);
    }

    /**
     * Cleanup and unregister listeners.
     */
    protected void cleanup() {
        HandlerList.unregisterAll(this);
        super.delete();
    }

    
    private void handleExit(Player player) {
        playDefaultSound(player);
        player.closeInventory();
        delete();
    }

    protected void addExitReturn() {
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        addItemAndLore(
            Material.SPRUCE_DOOR, 
            1, 
            "Exit", 
            slotMapping.get(SlotOption.EXIT)
        );
        addItemAndLore(
            Material.MAGENTA_GLAZED_TERRACOTTA, 
            1, 
            returnMessage, 
            slotMapping.get(SlotOption.RETURN)
        );
    }   

    public void setReturnCallback(Consumer<Player> callback) {
        this.returnCallback = callback;
    }

    public static List<Player> getOpenInventories(UUID dealerId) {
        List<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
                continue; // Skip if no inventory is open
            }

            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
                Mob dealer = Dealer.findDealer(menu.dealerId, player.getLocation());
                if (Dealer.getUniqueId(dealer).equals(dealerId) || dealer.getUniqueId().equals(dealerId)) {
                    players.add(player);
                }
            }
        }

        return players;
    }
}
