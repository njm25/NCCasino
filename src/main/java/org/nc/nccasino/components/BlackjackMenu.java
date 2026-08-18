package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Blackjack.BlackjackMaxHandsInputParser;
import org.nc.nccasino.games.Blackjack.BlackjackMenuChatRouting;
import org.nc.nccasino.games.Blackjack.BlackjackSplitMatching;
import org.nc.nccasino.games.Blackjack.BlackjackTiming;
import org.nc.nccasino.helpers.SoundHelper;

public class BlackjackMenu extends Menu {
    /** 2-row chest menu. Slot assignment is fully dynamic -- see {@link #layoutMenu()}. */
    private static final int MENU_SIZE = 18;

    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    /**
     * True while {@link #layoutMenuAnimated()}'s slide is still running --
     * gates {@link #handleCustomClick} so a click can never land on a
     * control that's mid-slide toward a slot it doesn't actually occupy
     * yet. This menu is one instance per admin viewer (unlike the live
     * table's wager bar), so there's no multi-viewer/reversibility concern
     * to handle -- a click is simply ignored until the short slide finishes.
     */
    private boolean relayoutInProgress = false;
    public static final Map<UUID, BlackjackMenu> BAInventories = new HashMap<>();

    public BlackjackMenu(UUID dealerId,Player player, String title, Consumer<Player> ret, Nccasino plugin,String returnName) {
        super(player, plugin, dealerId, title, MENU_SIZE, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());

        // Slot assignment happens in layoutMenu(), called from
        // initializeMenu() below -- see that method's doc. Settings that
        // only matter while another setting is enabled (e.g. Split Matching
        // while Splitting is off) disappear and the remaining buttons
        // compact toward slot 0 when their parent is disabled, reappearing
        // in place when it's re-enabled. Exit is always pinned at the last
        // slot (MENU_SIZE - 1) regardless of how many settings are visible.

        BAInventories.put(this.ownerId, this);
        initializeMenu();
    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @Override
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        BAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminMenu.timerEditMode.remove(ownerId);
        AdminMenu.standOn17Mode.remove(ownerId);
        AdminMenu.decksEditMode.remove(ownerId);
        AdminMenu.blackjackFieldEditMode.remove(ownerId);
        AdminMenu.blackjackFieldEditTarget.remove(ownerId);
        this.delete();
    }

    /** Tears down this player's open BlackjackMenu, if any. AdminMenu's own edit-mode maps are cleared separately and unconditionally by AdminMenu.clearPlayerEditState, so this only needs to handle the case where a menu instance still exists. */
    public static void clearPlayerState(UUID playerId) {
        BlackjackMenu menu = BAInventories.get(playerId);
        if (menu != null) {
            menu.cleanup();
        }
    }

    @Override
    protected void initializeMenu(){
        layoutMenu();
    }

    private String internalName() {
        return Dealer.getInternalName(dealer);
    }

    private void renderReturn() {
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
    }

    private void renderExit() {
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
    }

    private void renderEditTimer() {
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer") ? config.getInt("dealers." + internalName + ".timer") : 10;
        addItemAndLore(
            Material.CLOCK, currentTimer,
            text("blackjack-settings.edit-timer"), slotMapping.get(SlotOption.EDIT_TIMER),
            text("common.current", "value", currentTimer),
            text("blackjack-settings.edit-timer-desc-1"), text("blackjack-settings.edit-timer-desc-2")
        );
    }

    private void renderStandOn17() {
        int standOn17Chance = configInt("stand-on-17", 100);
        addItemAndLore(Material.SHIELD, standOn17Chance, text("blackjack-settings.edit-stand-17"), slotMapping.get(SlotOption.STAND_17), text("blackjack-settings.current-percent", "value", standOn17Chance));
    }

    private void renderNumberOfDecks() {
        int numberOfDecks = configInt("number-of-decks", 6);
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, numberOfDecks, text("blackjack-settings.edit-decks"), slotMapping.get(SlotOption.NUMBER_OF_DECKS), text("common.current", "value", numberOfDecks));
    }

    /** One dynamically-positioned setting: a slot identity, whether it currently applies, and how to paint it once positioned. */
    private static final class MenuEntry {
        final SlotOption option;
        final java.util.function.BooleanSupplier visible;
        final Runnable render;

        MenuEntry(SlotOption option, java.util.function.BooleanSupplier visible, Runnable render) {
            this.option = option;
            this.visible = visible;
            this.render = render;
        }
    }

    /**
     * The full settings list in display order, each paired with whether it
     * currently applies. A setting that only matters while another setting
     * is enabled (e.g. Split Matching while Splitting is off) is listed
     * immediately after its parent, so disabling the parent closes the gap
     * right where it opens, and re-enabling it reinserts the dependent
     * setting in the same place. Rebuilt fresh on every {@link #layoutMenu()}
     * call (cheap -- a handful of lambdas) so every {@code visible}
     * predicate always reads live config, never a stale snapshot.
     */
    private List<MenuEntry> menuEntries() {
        return List.of(
            new MenuEntry(SlotOption.RETURN, () -> true, this::renderReturn),
            new MenuEntry(SlotOption.EDIT_TIMER, () -> true, this::renderEditTimer),
            new MenuEntry(SlotOption.STAND_17, () -> true, this::renderStandOn17),
            new MenuEntry(SlotOption.NUMBER_OF_DECKS, () -> true, this::renderNumberOfDecks),
            new MenuEntry(SlotOption.TOGGLE_INSURANCE_ENABLED, () -> true, this::renderInsuranceToggle),
            new MenuEntry(SlotOption.EDIT_INSURANCE_TIMEOUT, () -> configBoolean("insurance.enabled", true), this::renderInsuranceTimeout),
            new MenuEntry(SlotOption.TOGGLE_SPLITTING_ENABLED, () -> true, this::renderSplittingToggle),
            new MenuEntry(SlotOption.TOGGLE_SPLIT_MATCHING, () -> configBoolean("splitting.enabled", true), this::renderSplitMatchingToggle),
            new MenuEntry(SlotOption.EDIT_MAX_HANDS, () -> configBoolean("splitting.enabled", true), this::renderMaxHands),
            new MenuEntry(SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, () -> configBoolean("splitting.enabled", true), this::renderDoubleAfterSplitToggle),
            new MenuEntry(SlotOption.TOGGLE_ACES_HIT, () -> configBoolean("splitting.enabled", true), this::renderAcesHitToggle),
            new MenuEntry(SlotOption.TOGGLE_ACES_DOUBLE, () -> configBoolean("splitting.enabled", true), this::renderAcesDoubleToggle),
            new MenuEntry(SlotOption.TOGGLE_ACES_RESPLIT, () -> configBoolean("splitting.enabled", true), this::renderAcesResplitToggle),
            new MenuEntry(SlotOption.EDIT_TURN_TIMER_TIMEOUT, () -> true, this::renderTurnTimerTimeout)
        );
    }

    /**
     * Pure compaction: given content options in declaration order and which
     * are currently visible, returns the resulting slot for each -- packed
     * from 0 in declaration order, skipping anything not visible -- plus
     * {@code EXIT} always pinned at {@code menuSize - 1}. Package-private,
     * static, and takes no Bukkit types at all, so this is exactly the
     * algorithm {@link #layoutMenu()} uses, independently unit-testable
     * without constructing a live menu.
     */
    public static Map<SlotOption, Integer> computeLayout(List<SlotOption> orderedContentOptions, java.util.Set<SlotOption> visibleOptions, int menuSize) {
        Map<SlotOption, Integer> result = new java.util.LinkedHashMap<>();
        int slot = 0;
        for (SlotOption option : orderedContentOptions) {
            if (visibleOptions.contains(option)) {
                result.put(option, slot);
                slot++;
            }
        }
        result.put(SlotOption.EXIT, menuSize - 1);
        return result;
    }

    /**
     * Repaints the entire settings list from scratch using {@link #computeLayout}:
     * every slot no longer used by any entry is blanked back to empty; Exit
     * is always pinned at {@code MENU_SIZE - 1}, last, regardless of how
     * many settings are currently visible. Called from
     * {@link #initializeMenu()} for the first paint (nothing to slide
     * {@code from} yet) and as {@link #layoutMenuAnimated()}'s own instant
     * fallback when a parent toggle change happens to move nothing. Every
     * other parent-toggle-triggered relayout (Insurance/Splitting enabled
     * -- see the parent-vs-leaf split in {@link #handleCustomClick}) goes
     * through {@link #layoutMenuAnimated()} instead, which slides rather
     * than jump-cuts.
     */
    private void layoutMenu() {
        List<MenuEntry> entries = menuEntries();
        List<SlotOption> orderedOptions = new java.util.ArrayList<>();
        java.util.Set<SlotOption> visibleOptions = new java.util.LinkedHashSet<>();
        for (MenuEntry entry : entries) {
            orderedOptions.add(entry.option);
            if (entry.visible.getAsBoolean()) {
                visibleOptions.add(entry.option);
            }
        }
        Map<SlotOption, Integer> computed = computeLayout(orderedOptions, visibleOptions, MENU_SIZE);

        int highestUsedSlot = -1;
        for (MenuEntry entry : entries) {
            Integer slot = computed.get(entry.option);
            if (slot == null) {
                // Not currently applicable -- remove any stale mapping from
                // a previous layout so handleClick's reverse slot lookup
                // can never resolve this option to a slot that's now
                // occupied by something else entirely.
                slotMapping.remove(entry.option);
                continue;
            }
            slotMapping.put(entry.option, slot);
            entry.render.run();
            highestUsedSlot = Math.max(highestUsedSlot, slot);
        }
        for (int emptySlot = highestUsedSlot + 1; emptySlot < MENU_SIZE - 1; emptySlot++) {
            addItem(null, emptySlot);
        }
        slotMapping.put(SlotOption.EXIT, MENU_SIZE - 1);
        renderExit();
    }

    /**
     * Same end result as {@link #layoutMenu()} (settings pack/compact
     * around whichever parent toggle just changed), but slides there
     * instead of jump-cutting: every entry that moves travels one slot per
     * tick toward its new position -- the same one-frame-per-tick, fully
     * atomic-repaint-per-tick feel as the live table's wager-bar
     * open/close slide (see {@link BlackjackTiming#MENU_RELAYOUT_STEP_TICKS}
     * / {@code BlackjackWagerRevealPlan}), just applied to a compacting
     * list instead of a fixed 9-slot strip. A newly-hidden entry
     * disappears immediately (nothing to slide it toward); a newly-shown
     * entry only appears once the slide reaches its final slot -- there's
     * nothing correct to render for it at any of the intermediate slots in
     * between, since those briefly belong to whichever entry is still
     * sliding through them.
     *
     * <p>Called only from a parent toggle's click handler, never from
     * {@link #initializeMenu()}'s first paint (nothing to slide {@code
     * from} yet -- that still calls the instant {@link #layoutMenu()}).
     * Input is gated by {@link #relayoutInProgress} for the slide's short
     * duration (see that field's own doc) rather than trying to support
     * reversing mid-slide -- this is a single-viewer admin menu, not the
     * live table's rapid-interaction wager bar.
     */
    private void layoutMenuAnimated() {
        Map<SlotOption, Integer> oldSlots = new java.util.LinkedHashMap<>(slotMapping);
        List<MenuEntry> entries = menuEntries();
        List<SlotOption> orderedOptions = new java.util.ArrayList<>();
        java.util.Set<SlotOption> visibleOptions = new java.util.LinkedHashSet<>();
        Map<SlotOption, Runnable> renderers = new java.util.LinkedHashMap<>();
        for (MenuEntry entry : entries) {
            orderedOptions.add(entry.option);
            renderers.put(entry.option, entry.render);
            if (entry.visible.getAsBoolean()) {
                visibleOptions.add(entry.option);
            }
        }
        Map<SlotOption, Integer> newSlots = computeLayout(orderedOptions, visibleOptions, MENU_SIZE);
        renderers.put(SlotOption.EXIT, this::renderExit);

        int maxSteps = 0;
        for (Map.Entry<SlotOption, Integer> entry : newSlots.entrySet()) {
            Integer from = oldSlots.get(entry.getKey());
            if (from != null) {
                maxSteps = Math.max(maxSteps, Math.abs(entry.getValue() - from));
            }
        }
        if (maxSteps == 0) {
            // Nothing that's staying visible actually changed slot (e.g. the
            // parent toggle's own icon flipped but nothing else moved) --
            // no slide to animate, just paint the (possibly still-changed
            // visibility set) instantly.
            layoutMenu();
            return;
        }

        relayoutInProgress = true;
        animateRelayoutStep(oldSlots, newSlots, renderers, 1, maxSteps);
    }

    /**
     * Pure frame math for one step of {@link #layoutMenuAnimated()}'s
     * slide: every entry still visible in the final layout ({@code
     * newSlots}) lands at {@code min(step, its own delta)} slots along the
     * way from its old slot ({@code oldSlots}) toward its new one (entries
     * with a smaller delta simply arrive early and hold there for the
     * remaining steps); a newly-appearing entry (no entry in {@code
     * oldSlots}) only renders on the final step ({@code step >= maxSteps})
     * -- there's nothing correct to show it as at any earlier intermediate
     * slot, since that slot briefly belongs to whichever entry is still
     * sliding through it. Package-private, static, no Bukkit types --
     * independently unit-testable without constructing a live menu, same
     * as {@link #computeLayout}.
     */
    static Map<SlotOption, Integer> computeRelayoutFrame(Map<SlotOption, Integer> oldSlots, Map<SlotOption, Integer> newSlots, int step, int maxSteps) {
        Map<SlotOption, Integer> frameSlots = new java.util.LinkedHashMap<>();
        for (Map.Entry<SlotOption, Integer> entry : newSlots.entrySet()) {
            SlotOption option = entry.getKey();
            int to = entry.getValue();
            Integer from = oldSlots.get(option);
            if (from == null) {
                if (step >= maxSteps) {
                    frameSlots.put(option, to);
                }
                continue;
            }
            int delta = to - from;
            int travelled = Integer.signum(delta) * Math.min(Math.abs(delta), step);
            frameSlots.put(option, from + travelled);
        }
        return frameSlots;
    }

    /**
     * Renders one frame of {@link #layoutMenuAnimated()}'s slide (see
     * {@link #computeRelayoutFrame}), blanking and fully repainting every
     * slot from scratch each frame -- atomic, like the wager bar's own
     * per-tick render, so no stale item from a previous frame is ever left
     * behind mid-slide.
     */
    private void animateRelayoutStep(Map<SlotOption, Integer> oldSlots, Map<SlotOption, Integer> newSlots, Map<SlotOption, Runnable> renderers, int step, int maxSteps) {
        Map<SlotOption, Integer> frameSlots = computeRelayoutFrame(oldSlots, newSlots, step, maxSteps);

        for (int slot = 0; slot < MENU_SIZE; slot++) {
            addItem(null, slot);
        }
        slotMapping.clear();
        for (Map.Entry<SlotOption, Integer> entry : frameSlots.entrySet()) {
            slotMapping.put(entry.getKey(), entry.getValue());
            renderers.get(entry.getKey()).run();
        }

        if (step >= maxSteps) {
            relayoutInProgress = false;
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> animateRelayoutStep(oldSlots, newSlots, renderers, step + 1, maxSteps), BlackjackTiming.MENU_RELAYOUT_STEP_TICKS);
    }

    private boolean configBoolean(String key, boolean defaultValue) {
        return plugin.getConfig().getBoolean("dealers." + internalName() + "." + key, defaultValue);
    }

    private int configInt(String key, int defaultValue) {
        return plugin.getConfig().getInt("dealers." + internalName() + "." + key, defaultValue);
    }

    private String stateLabel(boolean enabled) {
        return text(enabled ? "blackjack-settings.enabled" : "blackjack-settings.disabled");
    }

    // ---- Boolean toggles (single-click cycle, repaint in place) ----------

    private void renderInsuranceToggle() {
        boolean enabled = configBoolean("insurance.enabled", true);
        addItemAndLore(
            enabled ? Material.TOTEM_OF_UNDYING : Material.BARRIER, 1,
            text("blackjack-settings.toggle-insurance"), slotMapping.get(SlotOption.TOGGLE_INSURANCE_ENABLED),
            stateLabel(enabled), text("common.click-toggle"),
            text("blackjack-settings.insurance-desc-1"), text("blackjack-settings.insurance-desc-2")
        );
    }

    private void renderSplittingToggle() {
        boolean enabled = configBoolean("splitting.enabled", true);
        addItemAndLore(
            enabled ? Material.SHEARS : Material.BARRIER, 1,
            text("blackjack-settings.toggle-splitting"), slotMapping.get(SlotOption.TOGGLE_SPLITTING_ENABLED),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderDoubleAfterSplitToggle() {
        boolean enabled = configBoolean("splitting.double-after-split", true);
        addItemAndLore(
            enabled ? Material.NETHERITE_SCRAP : Material.BARRIER, 1,
            text("blackjack-settings.toggle-double-after-split"), slotMapping.get(SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderAcesHitToggle() {
        boolean enabled = configBoolean("splitting.aces.hit", false);
        addItemAndLore(
            enabled ? Material.IRON_SWORD : Material.BARRIER, 1,
            text("blackjack-settings.toggle-aces-hit"), slotMapping.get(SlotOption.TOGGLE_ACES_HIT),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderAcesDoubleToggle() {
        boolean enabled = configBoolean("splitting.aces.double", false);
        addItemAndLore(
            enabled ? Material.GOLD_INGOT : Material.BARRIER, 1,
            text("blackjack-settings.toggle-aces-double"), slotMapping.get(SlotOption.TOGGLE_ACES_DOUBLE),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderAcesResplitToggle() {
        boolean enabled = configBoolean("splitting.aces.resplit", true);
        addItemAndLore(
            enabled ? Material.WEEPING_VINES : Material.BARRIER, 1,
            text("blackjack-settings.toggle-aces-resplit"), slotMapping.get(SlotOption.TOGGLE_ACES_RESPLIT),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderSplitMatchingToggle() {
        BlackjackSplitMatching matching = readSplitMatching();
        String label = text(matching == BlackjackSplitMatching.SAME_RANK ? "blackjack-settings.match-same-rank" : "blackjack-settings.match-same-value");
        addItemAndLore(
            Material.PAPER, 1,
            text("blackjack-settings.toggle-split-matching"), slotMapping.get(SlotOption.TOGGLE_SPLIT_MATCHING),
            text("common.current", "value", label), text("common.click-cycle"),
            text("blackjack-settings.split-matching-desc-1"), text("blackjack-settings.split-matching-desc-2")
        );
    }

    private BlackjackSplitMatching readSplitMatching() {
        String raw = plugin.getConfig().getString("dealers." + internalName() + ".splitting.matching", BlackjackSplitMatching.SAME_RANK.name());
        try {
            return BlackjackSplitMatching.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BlackjackSplitMatching.SAME_RANK;
        }
    }

    private void renderMaxHands() {
        String path = "dealers." + internalName() + ".splitting.max-hands";
        Object raw = plugin.getConfig().get(path);
        String display = (raw == null || "UNBOUNDED".equalsIgnoreCase(String.valueOf(raw)))
            ? text("blackjack-settings.max-hands-unbounded")
            : String.valueOf(raw);
        addItemAndLore(
            Material.NETHER_STAR, 1,
            text("blackjack-settings.edit-max-hands"), slotMapping.get(SlotOption.EDIT_MAX_HANDS),
            text("common.current", "value", display),
            text("blackjack-settings.max-hands-desc")
        );
    }

    private void renderInsuranceTimeout() {
        int seconds = configInt("insurance.timeout-seconds", 10);
        addItemAndLore(
            Material.CLOCK, Math.max(1, Math.min(seconds, 64)),
            text("blackjack-settings.edit-insurance-timeout"), slotMapping.get(SlotOption.EDIT_INSURANCE_TIMEOUT),
            text("common.current", "value", seconds),
            text("blackjack-settings.insurance-timeout-desc")
        );
    }

    private void renderTurnTimerTimeout() {
        int seconds = configInt("turn-timer.timeout-seconds", 20);
        addItemAndLore(
            Material.CLOCK, Math.max(1, Math.min(seconds, 64)),
            text("blackjack-settings.edit-turn-timer-timeout"), slotMapping.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT),
            text("common.current", "value", seconds),
            text("blackjack-settings.turn-timer-timeout-desc-1"), text("blackjack-settings.turn-timer-timeout-desc-2")
        );
    }

    public boolean isPlayerOccupied(UUID playerId){
        return
            !AdminMenu.timerEditMode.containsKey(playerId) &&
            !AdminMenu.standOn17Mode.containsKey(playerId) &&
            !AdminMenu.decksEditMode.containsKey(playerId) &&
            !AdminMenu.blackjackFieldEditMode.containsKey(playerId);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof BlackjackMenu){
        // Check if the player has an active AdminInventory
            if (BAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (isPlayerOccupied(playerId)) {
                    // Remove the AdminInventory and clean up references
                    BlackjackMenu inventory = BAInventories.remove(playerId);

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
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu) {
                        return;
                    }
                    AdminMenu temp=AdminMenu.adminInventories.get(player.getUniqueId());
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

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!BAInventories.containsKey(playerId)) return;
        // A slide (layoutMenuAnimated) has controls genuinely mid-transit
        // between slots -- ignore input entirely until it finishes rather
        // than routing a click through whatever briefly occupies that slot.
        if (relayoutInProgress) return;
        switch (option) {
            case EDIT_TIMER:
                handleEditTimer(player);
                playDefaultSound(player);
                break;
            case STAND_17:
                handleEditStand(player);
                playDefaultSound(player);
                break;
            case NUMBER_OF_DECKS:
                handleEditDecks(player);
                playDefaultSound(player);
                break;
            case TOGGLE_INSURANCE_ENABLED:
                // Insurance is a "parent" setting -- Edit Insurance Timeout
                // only ever shows while it's enabled, so toggling it can
                // add/remove/shift that slot and needs a full relayout,
                // animated (slides), not just its own single-slot repaint.
                handleToggleBoolean("insurance.enabled", true, this::layoutMenuAnimated, "blackjack-settings.insurance-updated", BlackjackInventory::setInsuranceEnabledLive);
                playDefaultSound(player);
                break;
            case EDIT_INSURANCE_TIMEOUT:
                handleEditTimeout("insurance.timeout-seconds", AdminMenu.BlackjackEditField.INSURANCE_TIMEOUT, player);
                playDefaultSound(player);
                break;
            case TOGGLE_SPLITTING_ENABLED:
                // Splitting is a "parent" setting -- Split Matching, Max
                // Hands, and the four split-ace toggles below all only show
                // while it's enabled, so this needs an animated relayout too.
                handleToggleBoolean("splitting.enabled", true, this::layoutMenuAnimated, "blackjack-settings.splitting-updated", BlackjackInventory::setSplittingEnabledLive);
                playDefaultSound(player);
                break;
            case TOGGLE_SPLIT_MATCHING:
                handleToggleSplitMatching();
                playDefaultSound(player);
                break;
            case EDIT_MAX_HANDS:
                handleEditMaxHands(player);
                playDefaultSound(player);
                break;
            case TOGGLE_DOUBLE_AFTER_SPLIT:
                handleToggleBoolean("splitting.double-after-split", true, this::renderDoubleAfterSplitToggle, "blackjack-settings.double-after-split-updated", BlackjackInventory::setDoubleAfterSplitLive);
                playDefaultSound(player);
                break;
            case TOGGLE_ACES_HIT:
                handleToggleBoolean("splitting.aces.hit", false, this::renderAcesHitToggle, "blackjack-settings.aces-hit-updated", BlackjackInventory::setAcesHitAllowedLive);
                playDefaultSound(player);
                break;
            case TOGGLE_ACES_DOUBLE:
                handleToggleBoolean("splitting.aces.double", false, this::renderAcesDoubleToggle, "blackjack-settings.aces-double-updated", BlackjackInventory::setAcesDoubleAllowedLive);
                playDefaultSound(player);
                break;
            case TOGGLE_ACES_RESPLIT:
                handleToggleBoolean("splitting.aces.resplit", true, this::renderAcesResplitToggle, "blackjack-settings.aces-resplit-updated", BlackjackInventory::setAcesResplitAllowedLive);
                playDefaultSound(player);
                break;
            case EDIT_TURN_TIMER_TIMEOUT:
                handleEditTimeout("turn-timer.timeout-seconds", AdminMenu.BlackjackEditField.TURN_TIMER_TIMEOUT, player);
                playDefaultSound(player);
                break;
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text("blackjack-settings.invalid-option"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("blackjack-settings.invalid-settings-option"));
                        break;}
                    case NONE:{
                        break;
                    }
                }
                break;
        }
    }

    /**
     * The live, already-running {@link BlackjackInventory} for this dealer,
     * if one exists -- {@code null} if nobody has ever opened the actual
     * game table yet since the last restart/reload (nothing to live-patch
     * yet) or this dealer isn't currently a Blackjack game at all.
     */
    private BlackjackInventory liveBlackjackInventory() {
        DealerInventory inv = DealerInventory.getInventory(dealerId);
        return inv instanceof BlackjackInventory blackjack ? blackjack : null;
    }

    /**
     * {@link #liveBlackjackInventory()}, lazily constructing one first if
     * it's still {@code null} -- exactly mirroring how
     * {@code DealerInteractListener} already lazily creates the live table
     * the first time any player opens it, via the same
     * {@code DealerInventory.updateInventory(dealerId, ...)} registration
     * ({@code inventories.get(dealerId)} comes back {@code null} there too,
     * so its own "close whoever's viewing the old one" branch never runs --
     * see that method's own doc). Never touches config and never force-
     * closes any other inventory, unlike {@code plugin.reloadDealer}'s
     * fallback (which sweeps every open {@code Menu} tied to this dealer,
     * including this very settings menu, via {@code deleteAssociatedInventories}
     * -&gt; {@code Menu.getOpenInventories} -- exactly the bug a fresh boot's
     * "nobody has opened the real table yet" case used to hit, since
     * {@code handleToggleBoolean}/{@code handleToggleSplitMatching} used to
     * treat "no live table" as the risk-free case and reload anyway).
     */
    private BlackjackInventory liveOrLazilyCreatedBlackjackInventory() {
        BlackjackInventory live = liveBlackjackInventory();
        if (live != null) {
            return live;
        }
        if (dealer == null || !dealer.isValid()) {
            return null;
        }
        BlackjackInventory created = new BlackjackInventory(dealerId, plugin, internalName());
        DealerInventory.updateInventory(dealerId, created);
        return created;
    }

    /**
     * Left-click toggles the boolean immediately: flips, persists with a
     * single saveConfig, then applies the new value to the live table in
     * place via {@code liveSetter} -- never deleting/recreating an
     * already-running controller, so a live round, committed wagers, and
     * even just seated players are completely undisturbed, and the admin's
     * own settings menu (a {@code Menu} tied to this same dealer) is never
     * force-closed as a side effect the way a {@code reloadDealer} call
     * used to. Lazily constructs the live table first if none exists yet
     * (see {@link #liveOrLazilyCreatedBlackjackInventory}) rather than
     * falling back to that destructive reload -- there is no longer a
     * "risk-free" case that still needs it.
     */
    private void handleToggleBoolean(String configKey, boolean defaultValue, Runnable render, String updatedMessageKey, java.util.function.BiConsumer<BlackjackInventory, Boolean> liveSetter) {
        if (dealer == null) {
            return;
        }
        String path = "dealers." + internalName() + "." + configKey;
        boolean next = !plugin.getConfig().getBoolean(path, defaultValue);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        BlackjackInventory live = liveOrLazilyCreatedBlackjackInventory();
        if (live != null) {
            liveSetter.accept(live, next);
        } else {
            plugin.reloadDealer(dealer);
        }
        render.run();
        if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(updatedMessageKey, "value", stateLabel(next)));
        }
    }

    /** Same live-patch-first, reload-only-as-fallback approach as {@link #handleToggleBoolean} -- see its doc. */
    private void handleToggleSplitMatching() {
        if (dealer == null) {
            return;
        }
        BlackjackSplitMatching next = readSplitMatching() == BlackjackSplitMatching.SAME_RANK
            ? BlackjackSplitMatching.SAME_VALUE : BlackjackSplitMatching.SAME_RANK;
        plugin.getConfig().set("dealers." + internalName() + ".splitting.matching", next.name());
        plugin.saveConfig();
        BlackjackInventory live = liveOrLazilyCreatedBlackjackInventory();
        if (live != null) {
            live.setSplitMatchingLive(next);
        } else {
            plugin.reloadDealer(dealer);
        }
        renderSplitMatchingToggle();
        if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(
                    "blackjack-settings.split-matching-updated", "value",
                    text(next == BlackjackSplitMatching.SAME_RANK ? "blackjack-settings.match-same-rank" : "blackjack-settings.match-same-value")
                ));
        }
    }

    private void handleEditStand(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.standOn17Mode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text("blackjack-settings.prompt-stand-17"));
                break;}
            case VERBOSE:{
                player.sendMessage(text("blackjack-settings.prompt-stand-17-detailed"));
                break;}
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }

    private void handleEditDecks(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.decksEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text("blackjack-settings.prompt-number"));
                break;}
            case VERBOSE:{
                player.sendMessage(text("blackjack-settings.prompt-decks"));
                break;}
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }


    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text("blackjack-settings.prompt-number"));
                break;}
            case VERBOSE:{
                player.sendMessage(text("blackjack-settings.prompt-timer"));
                break;}
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }

    private void handleEditTimeout(String configKey, AdminMenu.BlackjackEditField field, Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.blackjackFieldEditMode.put(playerId, dealer);
        AdminMenu.blackjackFieldEditTarget.put(playerId, field);
        player.closeInventory();
        String promptKey = field == AdminMenu.BlackjackEditField.INSURANCE_TIMEOUT
            ? "blackjack-settings.prompt-insurance-timeout" : "blackjack-settings.prompt-turn-timer-timeout";
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
            default:{
                player.sendMessage(text(promptKey));
                break;
            }
        }
    }

    private void handleEditMaxHands(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.blackjackFieldEditMode.put(playerId, dealer);
        AdminMenu.blackjackFieldEditTarget.put(playerId, AdminMenu.BlackjackEditField.MAX_HANDS);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
            default:{
                player.sendMessage(text("blackjack-settings.prompt-max-hands"));
                break;
            }
        }
    }

    /**
     * This listener is registered per-instance (see the {@code Menu}
     * constructor), so it receives <em>every</em> player's chat on the
     * server, not just this menu's own owner -- two administrators editing
     * different dealers simultaneously each have their own BlackjackMenu
     * instance, and each instance's own {@code onPlayerChat} fires for the
     * other's chat too. An unrelated player's message (including another
     * administrator's own edit input, from this instance's point of view)
     * must have zero effect here: no cancellation, no cleanup, no read of
     * any static edit-mode state beyond this identity check.
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID chattingPlayerId = event.getPlayer().getUniqueId();
        if (!BlackjackMenuChatRouting.isEligible(chattingPlayerId, ownerId)) {
            return;
        }
        // Also confirm this exact instance is still the live, registered
        // menu for its owner -- a stale instance (already superseded by a
        // menu replacement) must not act even on its own owner's chat.
        if (!isLiveMenuForOwner()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage().trim();

         if (AdminMenu.timerEditMode.get(ownerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "timer", 1, 10000, "blackjack-settings.timer-updated");
        }
        else if (AdminMenu.standOn17Mode.get(ownerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "stand-on-17", 0, 100, "blackjack-settings.stand-17-updated");
        }
        else if (AdminMenu.decksEditMode.get(ownerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "number-of-decks", 1, 10000, "blackjack-settings.decks-updated");
        }
        else if (AdminMenu.blackjackFieldEditMode.get(ownerId) != null) {
            event.setCancelled(true);
            AdminMenu.BlackjackEditField field = AdminMenu.blackjackFieldEditTarget.get(ownerId);
            if (field == AdminMenu.BlackjackEditField.MAX_HANDS) {
                handleMaxHandsInput(player, message);
            } else if (field == AdminMenu.BlackjackEditField.INSURANCE_TIMEOUT) {
                handleNumericInput(player, message, "insurance.timeout-seconds", 1, 60, "blackjack-settings.insurance-timeout-updated");
            } else if (field == AdminMenu.BlackjackEditField.TURN_TIMER_TIMEOUT) {
                handleNumericInput(player, message, "turn-timer.timeout-seconds", 5, 3600, "blackjack-settings.turn-timer-timeout-updated");
            }
        }
    }

    /**
     * True only if this exact instance is still the live, registered menu
     * for its owner. Checked once on the async thread before dispatch in
     * {@link #onPlayerChat}, and must be checked again inside every
     * scheduled main-thread callback (see {@link #handleNumericInput},
     * {@link #handleMaxHandsInput}) -- time passes between scheduling and
     * execution, during which the menu can be replaced, the edit session
     * cancelled, the dealer removed, or the owner can disconnect.
     */
    private boolean isLiveMenuForOwner() {
        return BlackjackMenuChatRouting.isLiveMenu(BAInventories, ownerId, this);
    }

    /** Whether any of the five edit-mode maps still has an active session for this owner -- a scheduled callback whose session was cancelled/completed by something else in the meantime (disconnect, a duplicate submission) must see this as false and no-op. */
    private boolean hasAnyActiveEditSession() {
        return AdminMenu.timerEditMode.containsKey(ownerId)
            || AdminMenu.standOn17Mode.containsKey(ownerId)
            || AdminMenu.decksEditMode.containsKey(ownerId)
            || AdminMenu.blackjackFieldEditMode.containsKey(ownerId);
    }

    /**
     * Accepts case-insensitive "unbounded", or an integer >= 2 -- parsing
     * itself never throws (see {@link BlackjackMaxHandsInputParser}, which
     * safely rejects an overflowing digit string instead of letting
     * {@code Long.parseLong} propagate an uncaught exception out of this
     * async chat handler), and an invalid message never overwrites the
     * existing valid stored value. Every terminal outcome -- success,
     * invalid input, or a missing dealer -- clears both blackjackFieldEditMode
     * edit-mode maps and tears the menu session down the same way, so one
     * bad input can never leave the player stuck occupied or holding a
     * stale listener/menu reference; dispatched onto the main thread since
     * config/sound/messaging and the static edit-mode maps are not safe to
     * touch from AsyncPlayerChatEvent's own thread.
     */
    private void handleMaxHandsInput(Player player, String input) {
        Optional<String> parsed = BlackjackMaxHandsInputParser.parse(input);
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Re-validate on the main thread -- time passed since this was
            // scheduled from the async chat event, during which the menu
            // could have been replaced, the edit session cancelled, or the
            // owner could have disconnected. A stale callback must be a
            // complete no-op: never call endEditSession/cleanup here, since
            // that could tear down a *new* menu instance now registered
            // for this same owner.
            if (!isLiveMenuForOwner() || !AdminMenu.blackjackFieldEditMode.containsKey(ownerId)) {
                return;
            }
            if (parsed.isEmpty()) {
                denyAction(player, text("blackjack-settings.invalid-max-hands"));
                endEditSession(player);
                return;
            }
            String toStore = parsed.get();

            if (dealer != null && dealer.isValid()) {
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".splitting.max-hands", toStore);
                plugin.saveConfig();
                plugin.reloadDealer(dealer);

                if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
                }

                String display = "UNBOUNDED".equals(toStore) ? text("blackjack-settings.max-hands-unbounded") : toStore;
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text("blackjack-settings.max-hands-updated", "value", display));
                }

                AdminMenu.localMob.remove(player.getUniqueId());
            } else {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("admin.dealer-not-found"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("blackjack-settings.dealer-not-found"));
                        break;
                    case NONE:
                        break;
                }
            }

            endEditSession(player);
        });
    }

    /**
     * Tears the menu session down -- {@link #cleanup()} already clears
     * every one of the five edit-mode maps (timer/standOn17/decks/
     * blackjackFieldEditMode/blackjackFieldEditTarget) unconditionally for
     * this owner, so this is the single common terminal step every chat
     * edit flow (timer, stand-on-17, deck count, insurance timeout, turn
     * timer timeout, max hands) must reach exactly once on success,
     * invalid input, <em>and</em> a missing dealer alike -- never leaving
     * the player stuck occupied or holding a stale listener/menu reference
     * just because one input was rejected. Must run on the main thread.
     */
    private void endEditSession(Player player) {
        plugin.deleteAssociatedInventories(dealer);
        cleanup();
    }

    /**
     * Parses once inside a single try/catch (an overflowing digit string
     * must produce localized invalid-input feedback, never an uncaught
     * exception out of this async chat handler) and, on every terminal
     * outcome -- success, invalid input, or a missing dealer -- ends the
     * edit session exactly once (see {@link #endEditSession}). Dispatched
     * onto the main thread since config/sound/messaging and the static
     * edit-mode maps are not safe to touch from AsyncPlayerChatEvent's own
     * thread.
     */
    private void handleNumericInput(Player player, String input, String configPath, long min, long max, String messageKey) {
        if (input.isEmpty() || !input.matches("\\d+")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isLiveMenuForOwner() || !hasAnyActiveEditSession()) {
                    return;
                }
                denyAction(player, text("blackjack-settings.valid-positive-integer"));
                endEditSession(player);
            });
            return;
        }

        Long value;
        try {
            value = Long.parseLong(input);
        } catch (NumberFormatException overflow) {
            value = null;
        }
        Long parsedValue = value;

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Re-validate on the main thread -- see handleMaxHandsInput's
            // identical reasoning. A stale callback must be a complete
            // no-op, never tearing down whatever menu/session is now live.
            if (!isLiveMenuForOwner() || !hasAnyActiveEditSession()) {
                return;
            }
            if (parsedValue == null) {
                denyAction(player, text("blackjack-settings.invalid-number-format"));
                endEditSession(player);
                return;
            }
            long numericValue = parsedValue;

            if (numericValue < min || numericValue > max) {
                denyAction(player, text("blackjack-settings.number-range", "min", min, "max", max));
                endEditSession(player);
                return;
            }

            if (dealer != null && dealer.isValid()) {
                String internalName = Dealer.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + "." + configPath, numericValue);
                plugin.saveConfig();
                plugin.reloadDealer(dealer);

                if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
                }

                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text(messageKey));
                        break;
                    case VERBOSE:
                        player.sendMessage(text(
                            "blackjack-settings.updated-detailed",
                            "setting",
                            text(messageKey),
                            "value",
                            numericValue
                        ));
                        break;
                    case NONE:
                        break;
                }

                AdminMenu.localMob.remove(player.getUniqueId());
            } else {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("admin.dealer-not-found"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("blackjack-settings.dealer-not-found"));
                        break;
                    case NONE:
                        break;
                }
            }

            endEditSession(player);
        });
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
}
