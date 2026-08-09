package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
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
import org.nc.nccasino.games.RockPaperScissors.RpsMode;
import org.nc.nccasino.helpers.SoundHelper;

public class RockPaperScissorsMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, RockPaperScissorsMenu> RAInventories = new HashMap<>();

    public RockPaperScissorsMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 9, title, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());
        RAInventories.put(this.ownerId, this);

        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.TOGGLE_RPS_MODE, 1);
        slotMapping.put(SlotOption.EDIT_TIMER, 2);
        slotMapping.put(SlotOption.EDIT_RPS_MAX_CHAIN, 3);
        initializeMenu();
    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        RAInventories.remove(ownerId);
        AdminMenu.timerEditMode.remove(ownerId);
        AdminMenu.editRpsChainMode.remove(ownerId);
        this.delete();
    }

    /** Tears down this player's open RockPaperScissorsMenu, if any. AdminMenu's own edit-mode maps are cleared separately and unconditionally by AdminMenu.clearPlayerEditState. */
    public static void clearPlayerState(UUID playerId) {
        RockPaperScissorsMenu menu = RAInventories.get(playerId);
        if (menu != null) {
            menu.cleanup();
        }
    }

    @Override
    protected void initializeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")
        ? config.getInt("dealers." + internalName + ".timer")
        : 10;
        addItemAndLore(Material.CLOCK, currentTimer, text("rock-paper-scissors-settings.edit-timer"), slotMapping.get(SlotOption.EDIT_TIMER), text("common.current", "value", currentTimer));
        addModeItem(internalName);
        addMaxChainItem(internalName);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
    }

    private void addMaxChainItem(String internalName) {
        int maxChain = plugin.getRpsMaxChainRounds(internalName);
        String subtitle = maxChain <= 0
            ? text("rock-paper-scissors-settings.max-chain-unbounded")
            : text(
                "rock-paper-scissors-settings.max-chain-current",
                "rounds", maxChain,
                "multiplier", String.format("%.2f", Math.pow(1.98, maxChain))
            );
        addItemAndLore(Material.IRON_INGOT, 1, text("rock-paper-scissors-settings.edit-max-chain"), slotMapping.get(SlotOption.EDIT_RPS_MAX_CHAIN), subtitle);
    }

    private void addModeItem(String internalName) {
        RpsMode mode = plugin.getRockPaperScissorsMode(internalName);
        Material icon = mode == RpsMode.PLAYER_VS_DEALER ? Material.ZOMBIE_HEAD : Material.PLAYER_HEAD;
        String modeLabel = text(mode == RpsMode.PLAYER_VS_DEALER
            ? "rock-paper-scissors-settings.mode-pvd"
            : "rock-paper-scissors-settings.mode-pvp");
        addItemAndLore(icon, 1, text("rock-paper-scissors-settings.toggle-mode"), slotMapping.get(SlotOption.TOGGLE_RPS_MODE), text("common.current", "value", modeLabel));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof RockPaperScissorsMenu){
            if (RAInventories.containsKey(playerId)) {
                if (!AdminMenu.timerEditMode.containsKey(playerId) && !AdminMenu.editRpsChainMode.containsKey(playerId)) {
                    RockPaperScissorsMenu inventory = RAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }

                    if (RAInventories.isEmpty()) {
                        unregisterListener();
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu) {
                        return;
                    }
                    AdminMenu temp = AdminMenu.adminInventories.get(player.getUniqueId());
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
    public void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!RAInventories.containsKey(playerId)) return;

        switch (option) {
            case EDIT_TIMER:
                handleEditTimer(player);
                playDefaultSound(player);
                break;
            case TOGGLE_RPS_MODE:
                handleToggleMode(player);
                break;
            case EDIT_RPS_MAX_CHAIN:
                handleEditMaxChain(player);
                playDefaultSound(player);
                break;
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage(text("rock-paper-scissors-settings.invalid-option"));
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text("rock-paper-scissors-settings.invalid-settings-option"));
                        break;}
                    case NONE:{
                        break;
                    }
                }
                break;
        }
    }

    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(player.getUniqueId(), dealer);
        AdminMenu.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text("rock-paper-scissors-settings.prompt-number"));
                break;}
            case VERBOSE:{
                player.sendMessage(text("rock-paper-scissors-settings.prompt-timer"));
                break;}
            case NONE:{
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }

    private void handleEditMaxChain(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.editRpsChainMode.put(playerId, dealer);
        player.closeInventory();
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD: {
                player.sendMessage(text("rock-paper-scissors-settings.prompt-number"));
                break;
            }
            case VERBOSE: {
                player.sendMessage(text("rock-paper-scissors-settings.prompt-max-chain"));
                break;
            }
            case NONE: {
                player.sendMessage(text("admin.prompt-new-value"));
                break;
            }
        }
    }

    private void handleToggleMode(Player player) {
        if (dealer == null) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("admin.dealer-not-found"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("rock-paper-scissors-settings.dealer-not-found"));
                    break;
                case NONE:
                    break;
            }
            return;
        }

        String internalName = Dealer.getInternalName(dealer);
        RpsMode current = plugin.getRockPaperScissorsMode(internalName);
        RpsMode next = current == RpsMode.PLAYER_VS_PLAYER ? RpsMode.PLAYER_VS_DEALER : RpsMode.PLAYER_VS_PLAYER;
        plugin.getConfig().set("dealers." + internalName + ".rps-mode", next.name());
        plugin.saveConfig();

        // Swap in a fresh RockPaperScissorsServer so the running game
        // re-reads the new mode. Any *other* player currently looking at
        // the actual game inventory (or another admin's menu for this same
        // dealer) is now pointed at a dead Server instance and needs to be
        // kicked out -- but not through the blanket
        // reloadDealer()/deleteAssociatedInventories() sweep, which would
        // also close this settings menu out from under the admin who just
        // clicked the toggle.
        UUID dealerUuid = Dealer.getUniqueId(dealer);
        if (dealerUuid != null) {
            org.nc.nccasino.entities.DealerInventory.updateInventory(
                dealerUuid,
                new org.nc.nccasino.games.RockPaperScissors.RockPaperScissorsServer(dealerUuid, plugin, internalName)
            );
            closeStaleViewsExceptSelf(player, internalName, dealerUuid);
        }

        if (SoundHelper.getSoundSafely("ui.button.click", player) != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
        }

        addModeItem(internalName);

        String modeLabel = text(next == RpsMode.PLAYER_VS_DEALER
            ? "rock-paper-scissors-settings.mode-pvd"
            : "rock-paper-scissors-settings.mode-pvp");

        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
                player.sendMessage(text("rock-paper-scissors-settings.mode-updated"));
                break;
            case VERBOSE:
                player.sendMessage(text(
                    "blackjack-settings.updated-detailed",
                    "setting",
                    text("rock-paper-scissors-settings.mode-updated"),
                    "value",
                    modeLabel
                ));
                break;
            case NONE:
                break;
        }
    }

    /**
     * Closes every view left pointing at the now-replaced Server instance:
     * anyone actually playing the game itself (always stale after the
     * swap), and any other admin's menu for this same dealer (their
     * displayed mode/settings are now stale too). Deliberately spares the
     * given player's own currently-open inventory -- that's this settings
     * menu, mid-click, and it already refreshes itself in place.
     */
    private void closeStaleViewsExceptSelf(Player self, String internalName, UUID dealerUuid) {
        for (Player viewer : org.nc.nccasino.entities.Client.getOpenInventories(internalName)) {
            viewer.closeInventory();
        }
        for (Player viewer : Menu.getOpenInventories(dealerUuid)) {
            if (!viewer.getUniqueId().equals(self.getUniqueId())) {
                viewer.closeInventory();
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!RAInventories.containsKey(playerId)) {
            cleanup();
            return;
        }

        if (AdminMenu.timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, event.getMessage().trim(), "timer", 1, 10000);
        } else if (AdminMenu.editRpsChainMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, event.getMessage().trim(), "rps-max-chain-rounds", -1, 9999);
        }
    }

    private void handleNumericInput(Player player, String input, String configPath, long min, long max) {
        // -1 is only a valid input for settings whose min allows it (the
        // "unbounded" sentinel for max-chain-rounds) -- the timer's own
        // min=1 call rejects it via the normal range check below.
        boolean isUnboundedSentinel = min < 0 && input.equals("-1");
        if (!isUnboundedSentinel && (input.isEmpty() || !input.matches("\\d+"))) {
            denyAction(player, text("blackjack-settings.valid-positive-integer"));
            return;
        }

        long value;
        try {
            value = isUnboundedSentinel ? -1L : Long.parseLong(input);
        } catch (NumberFormatException e) {
            denyAction(player, text("blackjack-settings.invalid-number-format"));
            return;
        }

        if (value < min || value > max) {
            denyAction(player, text("blackjack-settings.number-range", "min", min, "max", max));
            return;
        }

        if (dealer != null) {
            String internalName = Dealer.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + "." + configPath, value);
            plugin.saveConfig();
            plugin.reloadDealer(dealer);

            if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            String updatedKey = configPath.equals("rps-max-chain-rounds")
                ? "rock-paper-scissors-settings.max-chain-updated"
                : "rock-paper-scissors-settings.timer-updated";

            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(updatedKey));
                    break;
                case VERBOSE:
                    player.sendMessage(text(
                        "blackjack-settings.updated-detailed",
                        "setting",
                        text(updatedKey),
                        "value",
                        value
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
                    player.sendMessage(text("rock-paper-scissors-settings.dealer-not-found"));
                    break;
                case NONE:
                    break;
            }
        }

        plugin.deleteAssociatedInventories(dealer);
        cleanup();
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
