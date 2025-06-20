package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
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
    private Mob dealer;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player

    private final Set<String> recentInteractions = new HashSet<>();
    public DealerInteractListener(Nccasino plugin) {
        this.plugin = plugin;
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
        if (!(clickedEntity instanceof Mob)) return;
        Player player = event.getPlayer();
        Mob clickedMob = (Mob) clickedEntity;
        
        // Find the dealer associated with this mob (either directly or through jockey stack)
        this.dealer = findDealerFromJockey(clickedMob);
        if (this.dealer == null) return;

        String interactionKey = player.getUniqueId() + ":" + this.dealer.getUniqueId();

        // Prevent duplicate interactions from the same player-entity pair
        if (recentInteractions.contains(interactionKey)) {
            return;
        }
        recentInteractions.add(interactionKey);
        // Schedule removal after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentInteractions.remove(interactionKey), 1L);

        UUID dealerId = Dealer.getUniqueId(this.dealer);
        String internalName = Dealer.getInternalName(this.dealer);

        // Check if dealer has a game type defined
        if (!plugin.getConfig().contains("dealers." + internalName + ".game")) {
            player.sendMessage(ChatColor.RED + "This dealer has no game type assigned.");
            return;
        }

        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game");
        String permission = getGamePermission(gameType);

        if (permission == null || !player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to play " + gameType + ".");
            return;
        }

        List<String> occupations = AdminMenu.playerOccupations(player.getUniqueId());
        List<Mob> mobs = AdminMenu.getOccupiedDealers(player.getUniqueId())
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
                String occupation = occupations.get(i);
                Mob mob = mobs.get(i);
                
                String mobName = (mob != null) ? Dealer.getInternalName(mob) : "unknown dealer";
                Nccasino.sendErrorMessage(player, "Please finish editing " + occupation + " for '" +
                    ChatColor.YELLOW + mobName + ChatColor.RED + "'.");
            }
            return;
        }
        
        if (player.isSneaking() && player.hasPermission("nccasino.adminmenu")) {
            handleAdminInventory(player, dealerId);
        }
        else if (player.isSneaking() && player.hasPermission("nccasino.playermenu")) {
            handlePlayerMenu(player, dealerId);
        } else {
            handleDealerInventory(player, dealerId);
        }
        
        event.setCancelled(true);
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

    private void handleDealerInventory(Player player, UUID dealerId) {
        DealerInventory dealerInventory = DealerInventory.inventories.get(dealerId);
        if (dealerInventory == null) {
            // Try to find the dealer by following the passenger/vehicle chain
            Mob foundDealer = null;
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
                if (entity instanceof Mob mob) {
                    // Check if this mob is a dealer
                    if (Dealer.isDealer(mob) && Dealer.getUniqueId(mob).equals(dealerId)) {
                        foundDealer = mob;
                        break;
                    }
                    
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
            Dealer.startLookingAtPlayers(foundDealer);

            dealerInventory = DealerInventory.getInventory(dealerId);

            if (dealerInventory == null) {
                Bukkit.getLogger().warning("Error: Failed to recreate dealerInventory for dealerId " + dealerId);
                return;
            }
        }
        if (shouldPlayAnimation(player, dealerId)) {
            startAnimation(dealer, player, dealerInventory, dealerId);
        } else {
            player.openInventory(dealerInventory.getInventory());
        }
    }

    private boolean shouldPlayAnimation(Player player, UUID dealerId) {
        return !activeAnimations.contains(player) &&
               plugin.getConfig().contains("dealers." + Dealer.getInternalName(dealer) + ".animation-message");
    }

    private void startAnimation(Mob dealer, Player player, DealerInventory dealerInventory, UUID dealerId) {
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
                    player.openInventory(dealerInventory.getInventory());
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
                        player.sendMessage("§cShift-click is disabled.");
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
                        player.sendMessage("§cPlease wait before clicking again!");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cPlease wait before clicking again!");
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
                            player.sendMessage("§cCannot drag in here.");
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
            case "dragon descent" : return "nccasino.games.dragon";
            default: return null;
        }
    }
}
