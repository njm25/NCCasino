package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.components.AnimationMessage;
import org.nc.nccasino.components.PlayerMenu;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.payout.OverflowBankService;
import org.nc.nccasino.games.Blackjack.BlackjackInventory;
import org.nc.nccasino.games.Roulette.RouletteInventory;
import org.nc.nccasino.helpers.Preferences.MessageSetting;
import org.nc.nccasino.helpers.SoundHelper;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DealerInteractListener implements Listener {
    private final Nccasino plugin;
    public static Set<Player> activeAnimations = new HashSet<>();
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player

    private final Set<String> recentInteractions = new HashSet<>();
    public DealerInteractListener(Nccasino plugin) {
        this.plugin = plugin;
    }

    /**
     * Removes {@code player} from the active-intro-animation tracker. Used
     * on disconnect: the natural-completion removal path in
     * {@code afterAnimationComplete} only runs if the player is still
     * online, so a disconnect mid-animation could otherwise leave them
     * permanently marked as "already saw the animation," suppressing it
     * forever on future dealer interactions.
     */
    public static void clearActiveAnimation(Player player) {
        activeAnimations.remove(player);
    }

    private Mob findDealerFromJockey(Mob clickedMob) {
        // First check if this mob is a dealer
        if (Dealer.isDealer(clickedMob)) {
            return clickedMob;
        }

        // Check if this mob is a passenger of any dealer
        Entity vehicle = clickedMob.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof Mob vehicleMob && Dealer.isDealer(vehicleMob)) {
                return vehicleMob;
            }
            vehicle = vehicle.getVehicle();
        }

        // Check if this mob has a dealer as a passenger
        for (Entity passenger : clickedMob.getPassengers()) {
            if (passenger instanceof Mob passengerMob && Dealer.isDealer(passengerMob)) {
                return passengerMob;
            }
        }

        // Check if this mob is part of a dealer's stack by following the chain
        Entity current = clickedMob;
        // Check upward chain (passengers)
        while (!current.getPassengers().isEmpty()) {
            current = current.getPassengers().get(0);
            if (current instanceof Mob passengerMob && Dealer.isDealer(passengerMob)) {
                return passengerMob;
            }
        }
        // Check downward chain (vehicles)
        current = clickedMob;
        while (current.getVehicle() != null) {
            current = current.getVehicle();
            if (current instanceof Mob vehicleMob && Dealer.isDealer(vehicleMob)) {
                return vehicleMob;
            }
        }

        return null;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();
        if (!(clickedEntity instanceof LivingEntity clickedLiving)) return;
        Player player = event.getPlayer();

        LivingEntity resolved;
        if (clickedLiving instanceof Mob clickedMob) {
            // Find the dealer associated with this mob (either directly or through jockey stack)
            resolved = findDealerFromJockey(clickedMob);
        } else {
            // Non-mob living entities are never part of a jockey stack, but a
            // Citizens player-type NPC lands here and can still be a dealer.
            resolved = Dealer.isDealer(clickedLiving) ? clickedLiving : null;
        }
        if (resolved == null) return;

        // Citizens-backed dealers are driven by Citizens' own click event
        // instead. Leaving this event untouched -- rather than handling and
        // cancelling it -- is what lets any other trait on the same NPC keep
        // working, and stops the menu from opening twice.
        if (Dealer.getBackend(resolved) == Dealer.Backend.CITIZENS) return;

        if (!handleDealerClick(player, resolved)) return;

        event.setCancelled(true);
    }

    /**
     * Runs the shared "a player clicked a dealer" path: duplicate-click
     * suppression, game and permission checks, the busy-editing guard, and
     * finally opening the right menu.
     *
     * <p>Both entry points funnel through here -- Bukkit's
     * {@link PlayerInteractEntityEvent} for mob dealers, and Citizens'
     * NPCRightClickEvent for NPC dealers -- so the two behave identically.
     *
     * @return true if the click was consumed and should be cancelled
     */
    public boolean handleDealerClick(Player player, LivingEntity dealer) {
        String interactionKey = player.getUniqueId() + ":" + dealer.getUniqueId();

        // Prevent duplicate interactions from the same player-entity pair
        if (recentInteractions.contains(interactionKey)) {
            return false;
        }
        recentInteractions.add(interactionKey);
        // Schedule removal after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentInteractions.remove(interactionKey), 1L);

        UUID dealerId = Dealer.getUniqueId(dealer);
        String internalName = Dealer.getInternalName(dealer);

        // Check if dealer has a game type defined
        if (!plugin.getConfig().contains("dealers." + internalName + ".game")) {
            player.sendMessage(plugin.getLocalization().text(player, "interaction.no-game-assigned"));
            return false;
        }

        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game");
        String permission = getGamePermission(gameType);

        if (permission == null || !player.hasPermission(permission)) {
            player.sendMessage(plugin.getLocalization().text(
                player,
                "interaction.no-game-permission",
                "game",
                localizedGameType(player, gameType)
            ));
            return false;
        }

        List<String> occupations = AdminMenu.playerOccupations(player.getUniqueId());
        List<LivingEntity> mobs = AdminMenu.getOccupiedDealers(player.getUniqueId())
            .stream()
            .filter(v -> v != null && !v.isDead() && v.isValid()) // Ensure valid villagers
            .toList();

        if (!occupations.isEmpty() && !mobs.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                if (i >= mobs.size()) {
                    break; // Prevent index mismatch
                }
                String occupation = plugin.getLocalization().text(player, occupations.get(i));
                LivingEntity mob = mobs.get(i);

                String mobName = (mob != null) ? Dealer.getInternalName(mob) : "unknown dealer";
                player.sendMessage(plugin.getLocalization().text(
                    player,
                    "commands.finish-editing",
                    "occupation",
                    occupation,
                    "name",
                    mobName
                ));
            }
            return false;
        }

        if (player.isSneaking() && player.hasPermission("nccasino.adminmenu")) {
            handleAdminInventory(player, dealerId);
        }
        else if (player.isSneaking() && player.hasPermission("nccasino.playermenu")) {
            handlePlayerMenu(player, dealerId);
        } else {
            handleDealerInventory(player, dealerId, dealer);
        }

        return true;
    }

    private String localizedGameType(Player player, String gameType) {
        return switch (gameType) {
            case "Blackjack" -> plugin.getLocalization().text(player, "game-options.blackjack");
            case "Roulette" -> plugin.getLocalization().text(player, "game-options.roulette");
            case "Mines" -> plugin.getLocalization().text(player, "game-options.mines");
            case "Baccarat" -> plugin.getLocalization().text(player, "game-options.baccarat");
            case "Coin Flip" -> plugin.getLocalization().text(player, "game-options.coin-flip");
            case "Rock Paper Scissors" -> plugin.getLocalization().text(player, "game-options.rock-paper-scissors");
            case "Dragon Descent" -> plugin.getLocalization().text(player, "game-options.dragon-descent");
            case "Slots" -> plugin.getLocalization().text(player, "game-options.slots");
            case "Test Game" -> plugin.getLocalization().text(player, "game-options.test-game");
            default -> gameType;
        };
    }

    private void handlePlayerMenu(Player player, UUID dealerId) {
        PlayerMenu playerMenu = new PlayerMenu(player,plugin,dealerId);
        player.openInventory(playerMenu.getInventory());
    }

    private void handleAdminInventory(Player player, UUID dealerId) {
        if (AdminMenu.adminInventories.containsKey(player.getUniqueId()) && AdminMenu.adminInventories.get(player.getUniqueId()).getDealerId().equals(dealerId)) {
            AdminMenu adminInventory = AdminMenu.adminInventories.get(player.getUniqueId());
            player.openInventory(adminInventory.getInventory());
        } else {
            AdminMenu adminInventory = new AdminMenu(dealerId, player, plugin);
            player.openInventory(adminInventory.getInventory());
        }
    }

    private void handleDealerInventory(Player player, UUID dealerId, LivingEntity dealer) {
        DealerInventory dealerInventory = DealerInventory.inventories.get(dealerId);
        if (dealerInventory == null) {
            // Try to find the dealer by following the passenger/vehicle chain
            LivingEntity foundDealer = null;
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
                // Direct match first, so a Citizens player-type NPC -- which is
                // a LivingEntity but not a Mob -- is not skipped.
                if (entity instanceof LivingEntity living
                    && Dealer.isDealer(living)
                    && dealerId.equals(Dealer.getUniqueId(living))) {
                    foundDealer = living;
                    break;
                }
                if (entity instanceof Mob mob) {
                    // Check passengers
                    for (Entity passenger : mob.getPassengers()) {
                        if (passenger instanceof Mob passengerMob && 
                            Dealer.isDealer(passengerMob) && 
                            Dealer.getUniqueId(passengerMob).equals(dealerId)) {
                            foundDealer = passengerMob;
                            break;
                        }
                    }
                    if (foundDealer != null) break;
                    
                    // Check vehicle
                    Entity vehicle = mob.getVehicle();
                    while (vehicle != null) {
                        if (vehicle instanceof Mob vehicleMob && 
                            Dealer.isDealer(vehicleMob) && 
                            Dealer.getUniqueId(vehicleMob).equals(dealerId)) {
                            foundDealer = vehicleMob;
                            break;
                        }
                        vehicle = vehicle.getVehicle();
                    }
                    if (foundDealer != null) break;
                }
            }
            
            if (foundDealer == null) {
                return;
            }

            Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
            String internalName = Dealer.getInternalName(foundDealer);
            String name = plugin.getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
            String gameType = plugin.getConfig().getString("dealers." + internalName + ".game", "Menu");
            int timer = plugin.getConfig().getInt("dealers." + internalName + ".timer", 30);
            String anmsg = plugin.getConfig().getString("dealers." + internalName + ".animation-message", "NCCasino");
            List<Integer> chipSizes = new ArrayList<>();
            ConfigurationSection chipSizeSection = plugin.getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");

            if (chipSizeSection != null) {
                for (String key : chipSizeSection.getKeys(false)) {
                    chipSizes.add(plugin.getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
                }
            }
            chipSizes.sort(Integer::compareTo);
            String currencyMaterial = plugin.getConfig().getString("dealers." + internalName + ".currency.material", "EMERALD");
            String currencyName = plugin.getConfig().getString("dealers." + internalName + ".currency.name", "Emerald");

            // Restore dealer inventory
            Dealer.updateGameType(foundDealer, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
            // Citizens NPCs get their gaze from Citizens' own LookClose trait,
            // and our task teleports the entity to steer it -- which would fight
            // whatever Citizens is doing with the NPC's position.
            if (foundDealer instanceof Mob foundMob) {
                Dealer.startLookingAtPlayers(foundMob);
            }

            dealerInventory = DealerInventory.getInventory(dealerId);

            if (dealerInventory == null) {
                Bukkit.getLogger().warning("Error: Failed to recreate dealerInventory for dealerId " + dealerId);
                return;
            }
        }
        if (shouldPlayAnimation(player, dealer)) {
            startAnimation(dealer, player, dealerInventory, dealerId);
        } else {
            openDealerInventoryForPlayer(player, dealerInventory);
        }
    }

    /**
     * Opens a dealer's inventory for a player. Roulette and Blackjack each
     * get a per-player localized view instead of the shared inventory
     * directly; every other game type is unaffected.
     */
    private void openDealerInventoryForPlayer(Player player, DealerInventory dealerInventory) {
        // Opening any NCCasino game is one of the four automatic
        // bank-delivery opportunities. It only ever adds to the player's own
        // survival inventory -- the dealer view being opened is untouched.
        tryDeliverBankedWinnings(player);

        if (dealerInventory instanceof RouletteInventory roulette) {
            player.openInventory(roulette.getOrCreateView(player));
        } else if (dealerInventory instanceof BlackjackInventory blackjack) {
            player.openInventory(blackjack.getOrCreateView(player));
        } else {
            player.openInventory(dealerInventory.getInventory());
        }
    }

    /**
     * Best-effort automatic claim of any overflow-banked winnings. Silent on
     * success: a player who had room simply gets their items back with no
     * ceremony. Never throws into the interact/open path -- a delivery
     * problem must not stop a player from opening a dealer.
     */
    private void tryDeliverBankedWinnings(Player player) {
        OverflowBankService bank = plugin.getOverflowBankService();
        if (bank == null || !bank.isBlocked(player.getUniqueId())) {
            return;
        }
        long remaining = bank.claimAll(player);
        if (remaining > 0) {
            player.sendMessage(plugin.getLocalization().text(
                player, "payout.bank-still-blocked", "amount", remaining));
        }
    }

    private boolean shouldPlayAnimation(Player player, LivingEntity dealer) {
        return !activeAnimations.contains(player) &&
               plugin.getConfig().contains("dealers." + Dealer.getInternalName(dealer) + ".animation-message");
    }

    private void startAnimation(LivingEntity dealer, Player player, DealerInventory dealerInventory, UUID dealerId) {
        String animationMessage = plugin.getConfig().getString("dealers." + Dealer.getInternalName(dealer) + ".animation-message");
        activeAnimations.add(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            AnimationMessage animationTable = new AnimationMessage(dealer, player, plugin, animationMessage, 0);
            player.openInventory(animationTable.getInventory());

            animationTable.animateMessage(player, () -> afterAnimationComplete(player, dealerInventory));
        }, 1L);
    }

    private void afterAnimationComplete(Player player, DealerInventory dealerInventory) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) {
                activeAnimations.remove(player);
                if (dealerInventory != null) {
                    openDealerInventoryForPlayer(player, dealerInventory);
                } else {
                    Bukkit.getLogger().warning("Error: tried to open null dealerInventory");
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if(!(event.getInventory().getHolder() instanceof DealerInventory)) return;
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            // By default, SHIFT-click will attempt to move items into the top inventory

            MessageSetting settings = plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            
            if(event.isShiftClick() && !(event.getInventory().getHolder() instanceof AdminMenu)){
                    
                event.setCancelled(true);
                switch(settings)
                {
                    case VERBOSE:{
                player.sendMessage(plugin.getLocalization().text(player, "interaction.shift-click-disabled"));
                        break;}
                    default:{
                        break;}
                }
            }
            else if(event.isShiftClick() && event.getInventory().getHolder() instanceof AdminMenu){
                AdminMenu menu = (AdminMenu) event.getInventory().getHolder();
                ItemStack item = event.getCurrentItem();
                menu.handleDrag(item, player, event); 
                return;
            }
        }
        else if (event.getClickedInventory() != null) {
            if(event.getSlot() == -999){
                return;
            }
            
            event.setCancelled(true);
            if (clickAllowed.getOrDefault(playerId, true)) {
                clickAllowed.put(playerId, false); // Prevent rapid clicking
                Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

                DealerInventory dealerInventory = (DealerInventory) event.getInventory().getHolder();
                dealerInventory.handleClick(event.getSlot(), player, event);
            }
            else{
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
            player.sendMessage(plugin.getLocalization().text(player, "interaction.click-cooldown"));
                        break;}
                    case VERBOSE:{
                player.sendMessage(plugin.getLocalization().text(player, "interaction.click-cooldown"));
                        break;}
                    case NONE:{
                        break;
                    }
                }
            }    
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        Player player = (Player) event.getWhoClicked();
        if (event.getInventory().getHolder() instanceof DealerInventory){
            for (int slot : event.getRawSlots()) {
                if (slot < topInventory.getSize()) {
                    switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting())
                    {
                        case VERBOSE:{
            player.sendMessage(plugin.getLocalization().text(player, "interaction.drag-disabled"));
                            break;}
                        default:{
                            break;}
                    }
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }   

    private String getGamePermission(String gameType) {
        if (gameType == null) return null;
        switch (gameType.toLowerCase()) {
            case "roulette": return "nccasino.games.roulette";
            case "mines": return "nccasino.games.mines";
            case "blackjack": return "nccasino.games.blackjack";
            case "test game": return "nccasino.adminmenu";
            case "baccarat" : return "nccasino.games.baccarat";
            case "coin flip" : return "nccasino.games.coinflip";
            case "rock paper scissors" : return "nccasino.games.rockpaperscissors";
            case "dragon descent" : return "nccasino.games.dragon";
            case "slots" : return "nccasino.games.slots";
            default: return null;
        }
    }
}
