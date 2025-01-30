package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.SoundHelper;

public class RouletteAdminInventory extends DealerInventory {
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;

    public RouletteAdminInventory(UUID dealerId, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
        super(dealerId, 9, title);
        this.ret = ret;
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        initalizeMenu();
    }

    private void initalizeMenu(){
        
        addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return to "+returnName), 0);
 
    }



    public void executeReturn() {
        ret.accept(dealerId);
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement

        UUID playerId = player.getUniqueId();

        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);


            switch (slot) {
                case 0:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    executeReturn();
                    break;
                default:
                    if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                    player.sendMessage("§cInvalid option selected.");
                    break;
            }
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
    }

}
