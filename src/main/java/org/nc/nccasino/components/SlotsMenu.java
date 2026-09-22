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
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.games.Slots.SlotsAdminSettingsTransitions;
import org.nc.nccasino.games.Slots.SlotsChatPrompt;
import org.nc.nccasino.games.Slots.SlotsChatPromptService;
import org.nc.nccasino.games.Slots.SlotsClickClassifier;
import org.nc.nccasino.games.Slots.SlotsConfig;
import org.nc.nccasino.games.Slots.SlotsGeometry;
import org.nc.nccasino.games.Slots.SlotsHouseEdgeInput;
import org.nc.nccasino.games.Slots.SlotsPayline;
import org.nc.nccasino.games.Slots.SlotsPaytable;
import org.nc.nccasino.games.Slots.SlotsPromptValues;
import org.nc.nccasino.games.Slots.SlotsVariance;
import org.nc.nccasino.games.Slots.SlotsVarianceStats;

/**
 * Slots' admin settings sub-menu.
 *
 * <p>Reels, height and paylines are small enumerated sets and cycle on click.
 * House edge instead opens a chat prompt so the administrator can enter any
 * exact percentage inside the supported range.
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

    // Repacked without gaps (rather than the old every-other-slot spacing) so
    // the new Height control fits alongside House Edge, Reels, Lines, Return
    // and Exit in this menu's single 9-slot row -- see the redesign's admin
    // settings section for why a new menu was avoided here.
    private static final int HOUSE_EDGE_SLOT = 1;
    private static final int COLUMNS_SLOT = 2;
    private static final int ROWS_SLOT = 3;
    private static final int LINES_SLOT = 4;
    private static final int VARIANCE_SLOT = 5;

    /** True while this menu instance owns its player's next chat message. */
    private boolean editingHouseEdge;
    private long promptGeneration;

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
        slotMapping.put(SlotOption.SLOTS_DEFAULT_ROWS, ROWS_SLOT);
        slotMapping.put(SlotOption.SLOTS_DEFAULT_LINES, LINES_SLOT);
        slotMapping.put(SlotOption.SLOTS_VARIANCE, VARIANCE_SLOT);

        SlotsConfig.ensureDefaults(plugin, internalName);
        initializeMenu();
    }

    @Override
    public void cleanup() {
        editingHouseEdge = false;
        promptGeneration++;
        SlotsChatPromptService prompts = plugin.getSlotsChatPromptService();
        if (prompts != null) {
            prompts.endForSession(ownerId, this, SlotsChatPrompt.EndReason.SESSION_ENDED);
        }
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
        SlotsVarianceStats varianceStats = SlotsVarianceStats.forConfig(
            config.columns(), config.visibleRows(), config.houseEdge(), config.variance(),
            1L, config.activeLines());

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
            Material.LADDER,
            1,
            text("slots-settings.default-rows"),
            ROWS_SLOT,
            text("slots-settings.default-rows-current", "rows", config.visibleRows()),
            text("slots-settings.default-rows-hint"));

        boolean linesInert = config.visibleRows() == 1;
        addItemAndLore(
            linesInert ? Material.GRAY_DYE : Material.ITEM_FRAME,
            1,
            linesInert ? text("slots-settings.default-lines-inert") : text("slots-settings.default-lines"),
            LINES_SLOT,
            text("slots-settings.default-lines-current", "lines", config.activeLines()),
            linesInert
                ? text("slots-settings.default-lines-inert-hint")
                : text("slots-settings.default-lines-hint", "max", SlotsPayline.MAX_LINES));

        addItemAndLore(
            Material.COMPARATOR,
            1,
            text("slots-settings.variance"),
            VARIANCE_SLOT,
            text("slots-settings.variance-current", "variance", text(varianceKey(config.variance()))),
            text("slots-settings.variance-hit-rate",
                "chance", formatPercent(varianceStats.lineHitProbability())),
            text("slots-settings.variance-top-line",
                "multiplier", formatMultiplier(varianceStats.maxLineMultiplier())),
            text("slots-settings.variance-max-exposure",
                "amount", varianceStats.maxPossiblePayoutAtDenomination(),
                "lines", varianceStats.activeLines()),
            text("slots-settings.variance-tradeoff"),
            text("slots-settings.variance-same-rtp", "rtp", formatPercent(config.paytable().theoreticalRtp())),
            text("slots-settings.variance-hint"));

        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        // Unsupported click types (shift, double, middle, number-key, drop,
        // border) are safe no-ops here too -- never cycle a setting on an
        // inferred "not right, so must be left" guess (Section 6 of the
        // redesign audit applies to this admin menu as well).
        if (!SlotsClickClassifier.isOrdinaryClick(event.getClick())) {
            return;
        }
        int direction = SlotsClickClassifier.cycleDirection(event.getClick());
        switch (option) {
            case SLOTS_HOUSE_EDGE -> beginHouseEdgePrompt(player);
            case SLOTS_DEFAULT_COLUMNS -> cycleColumns(player, direction);
            case SLOTS_DEFAULT_ROWS -> cycleRows(player, direction);
            case SLOTS_DEFAULT_LINES -> cycleLines(player, direction);
            case SLOTS_VARIANCE -> cycleVariance(player, direction);
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

    private void beginHouseEdgePrompt(Player player) {
        SlotsChatPromptService prompts = plugin.getSlotsChatPromptService();
        if (prompts == null) {
            player.sendMessage(text("slots.prompt-unavailable"));
            return;
        }
        promptGeneration++;
        long generation = promptGeneration;
        editingHouseEdge = true;
        SlotsChatPrompt prompt = new SlotsChatPrompt(
            ownerId,
            SlotsChatPrompt.Type.HOUSE_EDGE,
            SlotsChatPromptService.deadlineFromNow(),
            null,
            this,
            generation,
            new SlotsChatPrompt.Handler() {
                @Override
                public boolean isSessionValid() {
                    return isCurrentHouseEdgePrompt(generation) && player.isOnline();
                }

                @Override
                public SlotsChatPrompt.Outcome submit(String input) {
                    if (SlotsPromptValues.isCancel(input)) {
                        return SlotsChatPrompt.Outcome.CANCELLED;
                    }
                    return submitHouseEdge(player, input);
                }

                @Override
                public void accepted() {
                    resumeHouseEdgeMenu(player, generation, true);
                }

                @Override
                public void cancelled() {
                    if (isCurrentHouseEdgePrompt(generation)) {
                        player.sendMessage(text("slots.prompt-cancelled"));
                    }
                    resumeHouseEdgeMenu(player, generation, false);
                }

                @Override
                public void timedOut() {
                    if (isCurrentHouseEdgePrompt(generation) && player.isOnline()) {
                        player.sendMessage(text("slots-settings.house-edge-timed-out"));
                    }
                    abandonHouseEdgePrompt(generation);
                }

                @Override
                public void ended(SlotsChatPrompt.EndReason reason) {
                    abandonHouseEdgePrompt(generation);
                }
            });
        prompts.begin(prompt);
        player.sendMessage(text("slots-settings.house-edge-prompt",
            "min", formatPercent(SlotsPaytable.MIN_HOUSE_EDGE),
            "max", formatPercent(SlotsPaytable.MAX_HOUSE_EDGE)));
        player.sendMessage(text("slots.prompt-deadline", "seconds", SlotsChatPromptService.TIMEOUT_SECONDS));
        player.sendMessage(text("slots.prompt-cancel-hint", "cancel", SlotsPromptValues.CANCEL));
        player.sendMessage(text("slots.prompt-another-game-warning"));
        player.closeInventory();
    }

    private SlotsChatPrompt.Outcome submitHouseEdge(Player player, String input) {
        var parsed = SlotsHouseEdgeInput.parse(input);
        if (parsed.isEmpty()) {
            player.sendMessage(text("slots-settings.house-edge-invalid",
                "min", formatPercent(SlotsPaytable.MIN_HOUSE_EDGE),
                "max", formatPercent(SlotsPaytable.MAX_HOUSE_EDGE)));
            return SlotsChatPrompt.Outcome.RETRY;
        }

        SlotsConfig.setHouseEdge(plugin, internalName, parsed.getAsDouble());
        plugin.saveConfig();
        SlotsConfig updated = SlotsConfig.load(plugin, internalName);
        announce(player, text("slots-settings.house-edge-updated",
            "edge", formatPercent(updated.houseEdge()),
            "rtp", formatPercent(updated.paytable().theoreticalRtp())));
        return SlotsChatPrompt.Outcome.ACCEPTED;
    }

    private boolean isCurrentHouseEdgePrompt(long generation) {
        return editingHouseEdge
            && promptGeneration == generation
            && openInventories.get(ownerId) == this;
    }

    private void resumeHouseEdgeMenu(Player player, long generation, boolean accepted) {
        if (!isCurrentHouseEdgePrompt(generation)) {
            return;
        }
        editingHouseEdge = false;
        promptGeneration++;
        if (!player.isOnline()) {
            cleanup();
            return;
        }
        if (accepted) {
            playDefaultSound(player);
        }
        initializeMenu();
        player.openInventory(getInventory());
    }

    private void abandonHouseEdgePrompt(long generation) {
        if (!isCurrentHouseEdgePrompt(generation)) {
            return;
        }
        editingHouseEdge = false;
        promptGeneration++;
        HandlerList.unregisterAll(this);
        openInventories.remove(ownerId, this);
        // ANOTHER_GAME_OPENED fires after the replacement inventory has been
        // installed. DealerInventory.delete() removes by dealer id rather
        // than by instance, so calling it from this stale menu would remove
        // the newly opened inventory from the shared registry.
        if (DealerInventory.getInventory(dealerId) == this) {
            this.delete();
        }
    }

    private void cycleColumns(Player player, int direction) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        int[] supported = SlotsGeometry.supportedColumnCounts();
        int index = indexOf(supported, config.columns());
        int next = supported[Math.floorMod(index + direction, supported.length)];
        SlotsConfig.setColumns(plugin, internalName, next);
        plugin.saveConfig();

        announce(player, text("slots-settings.default-columns-updated", "columns", next));
        refresh(player);
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private void cycleRows(Player player, int direction) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        // One atomic transition covering both ends -- leaving height 1 forces
        // the persisted line count back to 1 just as surely as entering it
        // does, so a stale raw value (hand-edited config, or left over from
        // before this rule existed) can never resurface. Always persisted,
        // even when unchanged, so slots-rows and slots-lines never drift
        // apart from what this method just decided.
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(config.visibleRows(), config.activeLines(), direction);
        SlotsConfig.setRows(plugin, internalName, transition.nextRows());
        SlotsConfig.setLines(plugin, internalName, transition.nextPersistedLines());
        plugin.saveConfig();

        announce(player, text("slots-settings.default-rows-updated", "rows", transition.nextRows()));
        refresh(player);
    }

    private void cycleLines(Player player, int direction) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        int next = SlotsAdminSettingsTransitions.nextLinesOrInert(config.visibleRows(), config.activeLines(), direction);
        if (next == SlotsAdminSettingsTransitions.INERT) {
            // Height 1 supports exactly one default payline -- never store
            // or announce a value the very next refresh would clamp away.
            announce(player, text("slots-settings.default-lines-inert-hint"));
            playDefaultSound(player);
            return;
        }
        SlotsConfig.setLines(plugin, internalName, next);
        plugin.saveConfig();

        announce(player, text("slots-settings.default-lines-updated", "lines", next));
        refresh(player);
    }

    private void cycleVariance(Player player, int direction) {
        SlotsConfig config = SlotsConfig.load(plugin, internalName);
        var next = SlotsAdminSettingsTransitions.nextVariance(config.variance(), direction);
        SlotsConfig.setVariance(plugin, internalName, next);
        plugin.saveConfig();

        announce(player, text("slots-settings.variance-updated",
            "variance", text(varianceKey(next))));
        refresh(player);
    }

    private static String varianceKey(SlotsVariance variance) {
        return "slots.variance-" + variance.name().toLowerCase();
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

    private static String formatMultiplier(double multiplier) {
        return multiplier >= 100.0
            ? Long.toString(Math.round(multiplier))
            : String.format("%.2f", multiplier);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (event.getInventory().getHolder() instanceof SlotsMenu && openInventories.containsKey(playerId)) {
            if (editingHouseEdge) {
                return;
            }
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
