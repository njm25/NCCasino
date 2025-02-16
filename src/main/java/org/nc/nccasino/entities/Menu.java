package org.nc.nccasino.entities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstract class for all menu components.
 * Handles common menu functionality such as slot mappings, return callbacks, and click throttling.
 */
public abstract class Menu extends DealerInventory {

    protected enum SlotOption {
        EXIT,
        RETURN,
        TEST_OPTION_ONE,
        TEST_OPTION_TWO,
        SOUNDS,
        MESSAGES,
        PREFERENCES,
        STATS,
        BLACKJACK,
        ROULETTE,
        MINES,
        EDIT_MINES,
        EDIT_TIMER,
        STAND_17,
        NUMBER_OF_DECKS,
    }

    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();
    protected final UUID ownerId;
    protected final UUID dealerId;
    protected final Nccasino plugin;
    protected final Consumer<Player> returnCallback;
    protected final String returnMessage;
    protected final Map<UUID, Boolean> clickAllowed = new HashMap<>();

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
        this.returnCallback = returnCallback;

        // Register this menu as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

    }

    /**
     * This method should be overridden by subclasses to define menu contents.
     */
    protected abstract void initializeMenu();

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option == null) return;
        
        switch (option) {
            case EXIT -> handleExit(player);
            case RETURN -> executeReturn(player);
            default -> handleCustomClick(option, player);
        }
    }
    
    /**
     * Handles custom slot actions that subclasses should implement.
     */
    protected abstract void handleCustomClick(SlotOption option, Player player);
    
    
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

}
