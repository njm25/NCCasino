package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.EventHandler;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;

/**
 * Slots' admin settings sub-menu. Unlike every other game there is
 * deliberately nothing here to edit -- the audited paytable (symbol
 * weights and multipliers) is fixed and must never be exposed as a
 * per-dealer control, and Slots reuses the dealer's existing chip-size and
 * currency configuration rather than introducing its own. This menu exists
 * purely to show that fixed paytable and to fit the same
 * {@code AdminMenu.handleGameOptions} wiring every other game uses.
 */
public class SlotsMenu extends Menu {
    private final Nccasino plugin;
    private final String returnName;
    public static final Map<UUID, SlotsMenu> openInventories = new HashMap<>();

    public SlotsMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 9, title, ret);
        this.plugin = plugin;
        this.returnName = returnName;
        openInventories.put(this.ownerId, this);

        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        initializeMenu();
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        openInventories.remove(ownerId);
        this.delete();
    }

    /** Tears down this player's open SlotsMenu, if any. */
    public static void clearPlayerState(UUID playerId) {
        SlotsMenu menu = openInventories.get(playerId);
        if (menu != null) {
            menu.cleanup();
        }
    }

    @Override
    protected void initializeMenu() {
        addItemAndLore(
            Material.KNOWLEDGE_BOOK,
            1,
            text("slots-settings.paytable-fixed"),
            4,
            text("slots-settings.symbol-cherry"),
            text("slots-settings.symbol-lemon"),
            text("slots-settings.symbol-bell"),
            text("slots-settings.symbol-diamond"),
            text("slots-settings.symbol-seven"),
            text("slots-settings.rtp-fixed")
        );
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            default -> {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD -> player.sendMessage(text("slots-settings.invalid-option"));
                    case VERBOSE -> player.sendMessage(text("slots-settings.invalid-settings-option"));
                    case NONE -> {
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (event.getInventory().getHolder() instanceof SlotsMenu && openInventories.containsKey(playerId)) {
            SlotsMenu menu = openInventories.remove(playerId);
            if (menu != null) {
                menu.cleanup();
            }
        }
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
}
