package org.nc.nccasino.components;

import java.util.HashMap;
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
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.games.Blackjack.BlackjackMaxHandsInputParser;
import org.nc.nccasino.games.Blackjack.BlackjackSplitMatching;
import org.nc.nccasino.helpers.SoundHelper;

public class BlackjackMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, BlackjackMenu> BAInventories = new HashMap<>();

    public BlackjackMenu(UUID dealerId,Player player, String title, Consumer<Player> ret, Nccasino plugin,String returnName) {
        super(player, plugin, dealerId, title, 18, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());

        // Top row: existing chat-prompt settings, then insurance.
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EDIT_TIMER, 1);
        slotMapping.put(SlotOption.STAND_17, 2);
        slotMapping.put(SlotOption.NUMBER_OF_DECKS, 3);
        // Slot 4 intentionally empty.
        slotMapping.put(SlotOption.TOGGLE_INSURANCE_ENABLED, 5);
        slotMapping.put(SlotOption.EDIT_INSURANCE_TIMEOUT, 6);
        // Slots 7-8 intentionally empty.

        // Bottom row: splitting, then the turn timer, then exit at the
        // actual bottom-right slot of an 18-slot (2-row) inventory.
        slotMapping.put(SlotOption.TOGGLE_SPLITTING_ENABLED, 9);
        slotMapping.put(SlotOption.TOGGLE_SPLIT_MATCHING, 10);
        slotMapping.put(SlotOption.EDIT_MAX_HANDS, 11);
        slotMapping.put(SlotOption.TOGGLE_TURN_TIMER_ENABLED, 12);
        slotMapping.put(SlotOption.EDIT_TURN_TIMER_TIMEOUT, 13);
        // Slots 14-16 intentionally empty.
        slotMapping.put(SlotOption.EXIT, 17);

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
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")? config.getInt("dealers." + internalName + ".timer"): 10;
        int standOn17Chance = config.getInt("dealers." + internalName + ".stand-on-17", 100);
        int numberOfDecks = config.getInt("dealers." + internalName + ".number-of-decks", 6);
        addItemAndLore(Material.CLOCK, currentTimer, text("blackjack-settings.edit-timer"), slotMapping.get(SlotOption.EDIT_TIMER), text("common.current", "value", currentTimer));
        addItemAndLore(Material.SHIELD, standOn17Chance, text("blackjack-settings.edit-stand-17"), slotMapping.get(SlotOption.STAND_17), text("blackjack-settings.current-percent", "value", standOn17Chance));
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, numberOfDecks, text("blackjack-settings.edit-decks"), slotMapping.get(SlotOption.NUMBER_OF_DECKS), text("common.current", "value", numberOfDecks));

        renderInsuranceToggle();
        renderInsuranceTimeout();
        renderSplittingToggle();
        renderSplitMatchingToggle();
        renderMaxHands();
        renderTurnTimerToggle();
        renderTurnTimerTimeout();

        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));

    }

    private String internalName() {
        return Dealer.getInternalName(dealer);
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
            stateLabel(enabled), text("common.click-toggle")
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

    private void renderTurnTimerToggle() {
        boolean enabled = configBoolean("turn-timer.enabled", true);
        addItemAndLore(
            enabled ? Material.CLOCK : Material.BARRIER, 1,
            text("blackjack-settings.toggle-turn-timer"), slotMapping.get(SlotOption.TOGGLE_TURN_TIMER_ENABLED),
            stateLabel(enabled), text("common.click-toggle")
        );
    }

    private void renderSplitMatchingToggle() {
        BlackjackSplitMatching matching = readSplitMatching();
        String label = text(matching == BlackjackSplitMatching.SAME_RANK ? "blackjack-settings.match-same-rank" : "blackjack-settings.match-same-value");
        addItemAndLore(
            Material.PAPER, 1,
            text("blackjack-settings.toggle-split-matching"), slotMapping.get(SlotOption.TOGGLE_SPLIT_MATCHING),
            text("common.current", "value", label), text("common.click-cycle")
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
            text("common.current", "value", display)
        );
    }

    private void renderInsuranceTimeout() {
        int seconds = configInt("insurance.timeout-seconds", 10);
        addItemAndLore(
            Material.CLOCK, Math.max(1, Math.min(seconds, 64)),
            text("blackjack-settings.edit-insurance-timeout"), slotMapping.get(SlotOption.EDIT_INSURANCE_TIMEOUT),
            text("common.current", "value", seconds)
        );
    }

    private void renderTurnTimerTimeout() {
        int seconds = configInt("turn-timer.timeout-seconds", 20);
        addItemAndLore(
            Material.CLOCK, Math.max(1, Math.min(seconds, 64)),
            text("blackjack-settings.edit-turn-timer-timeout"), slotMapping.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT),
            text("common.current", "value", seconds)
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
                handleToggleBoolean("insurance.enabled", true, this::renderInsuranceToggle, "blackjack-settings.insurance-updated");
                playDefaultSound(player);
                break;
            case EDIT_INSURANCE_TIMEOUT:
                handleEditTimeout("insurance.timeout-seconds", AdminMenu.BlackjackEditField.INSURANCE_TIMEOUT, player);
                playDefaultSound(player);
                break;
            case TOGGLE_SPLITTING_ENABLED:
                handleToggleBoolean("splitting.enabled", true, this::renderSplittingToggle, "blackjack-settings.splitting-updated");
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
            case TOGGLE_TURN_TIMER_ENABLED:
                handleToggleBoolean("turn-timer.enabled", true, this::renderTurnTimerToggle, "blackjack-settings.turn-timer-updated");
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

    /** Left-click toggles the boolean immediately: flips, persists with a single saveConfig, reloads the dealer, and repaints the item in place -- no chat prompt involved. */
    private void handleToggleBoolean(String configKey, boolean defaultValue, Runnable render, String updatedMessageKey) {
        if (dealer == null) {
            return;
        }
        String path = "dealers." + internalName() + "." + configKey;
        boolean next = !plugin.getConfig().getBoolean(path, defaultValue);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadDealer(dealer);
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

    private void handleToggleSplitMatching() {
        if (dealer == null) {
            return;
        }
        BlackjackSplitMatching next = readSplitMatching() == BlackjackSplitMatching.SAME_RANK
            ? BlackjackSplitMatching.SAME_VALUE : BlackjackSplitMatching.SAME_RANK;
        plugin.getConfig().set("dealers." + internalName() + ".splitting.matching", next.name());
        plugin.saveConfig();
        plugin.reloadDealer(dealer);
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

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().trim();

        if (BAInventories.get(playerId) == null) {
            cleanup();
            return;
        }
         if (AdminMenu.timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "timer", 1, 10000, "blackjack-settings.timer-updated");
        }
        else if (AdminMenu.standOn17Mode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "stand-on-17", 0, 100, "blackjack-settings.stand-17-updated");
        }
        else if (AdminMenu.decksEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "number-of-decks", 1, 10000, "blackjack-settings.decks-updated");
        }
        else if (AdminMenu.blackjackFieldEditMode.get(playerId) != null) {
            event.setCancelled(true);
            AdminMenu.BlackjackEditField field = AdminMenu.blackjackFieldEditTarget.get(playerId);
            if (field == AdminMenu.BlackjackEditField.MAX_HANDS) {
                handleMaxHandsInput(player, message);
            } else if (field == AdminMenu.BlackjackEditField.INSURANCE_TIMEOUT) {
                handleNumericInput(player, message, "insurance.timeout-seconds", 1, 60, "blackjack-settings.insurance-timeout-updated");
            } else if (field == AdminMenu.BlackjackEditField.TURN_TIMER_TIMEOUT) {
                handleNumericInput(player, message, "turn-timer.timeout-seconds", 1, 60, "blackjack-settings.turn-timer-timeout-updated");
            }
        }
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
            if (parsed.isEmpty()) {
                denyAction(player, text("blackjack-settings.invalid-max-hands"));
                endEditSession(player);
                return;
            }
            String toStore = parsed.get();

            if (dealer != null) {
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

            if (dealer != null) {
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
