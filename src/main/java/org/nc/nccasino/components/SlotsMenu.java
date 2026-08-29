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
import org.nc.nccasino.games.Slots.SlotsConfig;
import org.nc.nccasino.games.Slots.SlotsGeometry;
import org.nc.nccasino.games.Slots.SlotsPayline;
import org.nc.nccasino.games.Slots.SlotsPaytable;

/**
 * Slots' admin settings sub-menu.
 *
 * <p>Three per-dealer controls, each cycled by clicking rather than typed in
 * chat -- every value here is a small enumerated set, so a click-cycle cannot
 * produce an invalid entry the way free text can.
 *
 * <p>The house edge is the substantive one. Unlike every other game in the
 * plugin, whose edge is a hardcoded constant, Slots derives its whole paytable
 * from this number ({@link SlotsPaytable}), so changing it here genuinely
 * retunes the machine's return rather than adjusting a display value. The
 * supported band runs from the plugin's own 1% convention (matching Mines,
 * Dragon Descent, Coin Flip, and RPS) up to 6%, roughly a real online or
 * high-limit slot.
 */
public class SlotsMenu extends Menu {
    private final Nccasino plugin;
    private final String internalName;
    private final String returnName;
    public static final Map<UUID, SlotsMenu> openInventories = new HashMap<>();

    /** Selectable house edges, as whole percentage points across the supported band. */
    private static final double[] EDGE_STEPS = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06};

    private static final int HOUSE_EDGE_SLOT = 2;
    private static final int COLUMNS_SLOT = 4;
    private static final int LINES_SLOT = 6;

    public SlotsMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName, String internalName) {
        super(player, plugin, dealerId, title, 9, title, ret);
        this.plugin = plugin;
        this.internalName = internalName;
        this.returnName = returnName;
        openInventories.put(this.ownerId, this);

        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.SLOTS_HOUSE_EDGE, HOUSE_EDGE_SLOT);
        slotMapping.put(SlotOption.SLOTS_DEFAULT_COLUMNS, COLUMNS_SLOT);
        slotMapping.put(SlotOption.SLOTS_DEFAULT_LINES, LINES_SLOT);

        SlotsConfig.ensureDefaults(plugin, internalName);
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
        SlotsConfig config = SlotsConfig.load(plugin, internalName);

        addItemAndLore(
            Material.GOLD_INGOT,
            1,
            text("slots-settings.house-edge"),
            HOUSE_EDGE_SLOT,
            text("slots-settings.house-edge-current",
                "edge", formatPercent(config.houseEdge()),
                "rtp", formatPercent(config.paytable().theoreticalRtp())),
            text("slots-settings.house-edge-hint",
                "min", formatPercent(SlotsPaytable.MIN_HOUSE_EDGE),
                "max", formatPercent(SlotsPaytable.MAX_HOUSE_EDGE)));

        addItemAndLore(
            Material.OBSERVER,
            1,
            text("slots-settings.default-columns"),
            COLUMNS_SLOT,
            text("slots-settings.default-columns-current", "columns", config.columns()),
            text("slots-settings.default-columns-hint"));

        addItemAndLore(
            Material.ITEM_FRAME,
            1,
            text("slots-settings.default-lines"),
            LINES_SLOT,
            text("slots-settings.default-lines-current", "lines", config.activeLines()),
            text("slots-settings.default-lines-hint", "max", SlotsPayline.MAX_LINES));

        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case SLOTS_HOUSE_EDGE -> cycleHouseEdge(player);
            case SLOTS_DEFAULT_COLUMNS -> cycleColumns(player);
            case SLOTS_DEFAULT_LINES -> cycleLines(player);
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

    private void cycleHouseEdge(Player player) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        double next = EDGE_STEPS[(nearestEdgeIndex(config.houseEdge()) + 1) % EDGE_STEPS.length];
        SlotsConfig.setHouseEdge(plugin, internalName, next);
        plugin.saveConfig();

        SlotsConfig updated = SlotsConfig.load(plugin, internalName);
        announce(player, text("slots-settings.house-edge-updated",
            "edge", formatPercent(updated.houseEdge()),
            "rtp", formatPercent(updated.paytable().theoreticalRtp())));
        refresh(player);
    }

    /** Snaps an arbitrary stored value onto the nearest selectable step before advancing. */
    private static int nearestEdgeIndex(double edge) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < EDGE_STEPS.length; i++) {
            double distance = Math.abs(EDGE_STEPS[i] - edge);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private void cycleColumns(Player player) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        int[] supported = SlotsGeometry.supportedColumnCounts();
        int index = 0;
        for (int i = 0; i < supported.length; i++) {
            if (supported[i] == config.columns()) {
                index = i;
                break;
            }
        }
        int next = supported[(index + 1) % supported.length];
        SlotsConfig.setColumns(plugin, internalName, next);
        plugin.saveConfig();

        announce(player, text("slots-settings.default-columns-updated", "columns", next));
        refresh(player);
    }

    private void cycleLines(Player player) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        int next = config.activeLines() >= SlotsPayline.MAX_LINES ? 1 : config.activeLines() + 1;
        SlotsConfig.setLines(plugin, internalName, next);
        plugin.saveConfig();

        announce(player, text("slots-settings.default-lines-updated", "lines", next));
        refresh(player);
    }

    private void announce(Player player, String message) {
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD, VERBOSE -> player.sendMessage(message);
            case NONE -> {
            }
        }
    }

    /** Repaints the menu so the new value is visible without reopening it. */
    private void refresh(Player player) {
        initializeMenu();
        player.updateInventory();
    }

    private static String formatPercent(double fraction) {
        return String.format("%.2f%%", fraction * 100.0);
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
