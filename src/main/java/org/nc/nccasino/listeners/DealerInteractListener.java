package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.AnimationTable;
import org.nc.nccasino.components.PlayerMenu;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.Preferences.MessageSetting;
import org.nc.nccasino.helpers.SoundHelper;

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

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();
        if (!(clickedEntity instanceof Mob)) return;
        Player player = event.getPlayer();
        this.dealer = (Mob) clickedEntity;
        String interactionKey = player.getUniqueId() + ":" + clickedEntity.getUniqueId();

        // Prevent duplicate interactions from the same player-entity pair
        if (recentInteractions.contains(interactionKey)) {
            return;
        }
        recentInteractions.add(interactionKey);
        // Schedule removal after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentInteractions.remove(interactionKey), 1L);



        UUID dealerId = Dealer.getUniqueId(dealer);
        String internalName = Dealer.getInternalName(dealer);


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

        List<String> occupations = AdminInventory.playerOccupations(player.getUniqueId());
        List<Mob> mobs = AdminInventory.getOccupiedDealers(player.getUniqueId())
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
        if (PlayerMenu.playerMenus.containsKey(player.getUniqueId())) {
            PlayerMenu playerMenu = PlayerMenu.playerMenus.get(player.getUniqueId());

            if (playerMenu.getDealerId().equals(dealerId)) {
                player.openInventory(playerMenu.getInventory());
            } else {
                Bukkit.getLogger().warning("Error: playerMenu's dealerId does not match the dealerId of entity interacted with");
            }
        } else {
            PlayerMenu playerMenu = new PlayerMenu(player,plugin,dealerId);
            player.openInventory(playerMenu.getInventory());
        }
    }

    private void handleAdminInventory(Player player, UUID dealerId) {
        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());

            if (adminInventory.getDealerId().equals(dealerId)) {
                player.openInventory(adminInventory.getInventory());
            } else {
                Bukkit.getLogger().warning("Error: adminInventory's dealerId does not match the dealerId of entity interacted with");
            }
        } else {
            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
            player.openInventory(adminInventory.getInventory());
        }
    }

    private void handleDealerInventory(Player player, UUID dealerId) {
        DealerInventory dealerInventory = DealerInventory.inventories.get(dealerId);
        if (shouldPlayAnimation(player, dealerId)) {
            startAnimation(player, dealerInventory, dealerId);
        } else {
            player.openInventory(dealerInventory.getInventory());
        }
    }

    private boolean shouldPlayAnimation(Player player, UUID dealerId) {
        return !activeAnimations.contains(player) &&
               plugin.getConfig().contains("dealers." + Dealer.getInternalName(dealer) + ".animation-message");
    }

    private void startAnimation(Player player, DealerInventory dealerInventory, UUID dealerId) {
        String animationMessage = plugin.getConfig().getString("dealers." + Dealer.getInternalName(dealer) + ".animation-message");
        activeAnimations.add(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
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
        event.setCancelled(true);
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            // By default, SHIFT-click will attempt to move items into the top inventory

            MessageSetting settings = plugin.getPreferences(player.getUniqueId()).getMessageSetting();
            
            if(event.isShiftClick() && !(event.getInventory().getHolder() instanceof AdminInventory)){
                    
                switch(settings)
                {
                    case VERBOSE:{
                        player.sendMessage("§cShift-click is disabled.");
                        break;}
                    default:{
                        break;}
                }
            }
        
            return;
        }
        else if (event.getClickedInventory() != null) {
            if(event.getSlot() == -999){
                return;
            }
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
            default: return null;
        }
    }
}
