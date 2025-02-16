package org.nc.nccasino.components;

import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.SoundHelper;
public class ConfirmInventory extends DealerInventory {
    private final Consumer<UUID> confirm;
    private final Consumer<UUID> cancel;
    private UUID dealerId;
    private Nccasino plugin;

    public ConfirmInventory(UUID dealerId, String title, Consumer<UUID> confirm, Consumer<UUID> cancel, Nccasino plugin) {
        super(dealerId, 9, title);
        this.confirm = confirm;
        this.cancel = cancel;
        this.dealerId = dealerId;
        this.plugin = plugin;

        initalizeMenu();
    }

    private void initalizeMenu(){
        
        addItem(createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Yes"), 0);
        addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "No"), 8);
    }

    public void executeConfirm() {
        confirm.accept(dealerId);
    }

    public void executeCancel() {
        cancel.accept(dealerId);
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {

        switch (slot) {
            case 0:
                playDefaultSound(player);
                executeConfirm();
                break;
            case 8:
                playDefaultSound(player);
                executeCancel();
                break;
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                player.closeInventory();
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid option selected.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cInvalid confirm menu option selected.");
                        break;}
                    case NONE:{break;
                    }
                }
                break;
        }
    }

}
