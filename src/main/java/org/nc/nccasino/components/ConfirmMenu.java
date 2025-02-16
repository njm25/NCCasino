package org.nc.nccasino.components;

import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.SoundHelper;
public class ConfirmMenu extends Menu {
    private final Consumer<UUID> confirm;
    private final Consumer<UUID> cancel;
    private UUID dealerId;
    private Nccasino plugin;

    public ConfirmMenu(
        Player player, 
        UUID dealerId, 
        String title, 
        Consumer<UUID> confirm, 
        Consumer<UUID> cancel, 
        Nccasino plugin
    ) {
        super(player, plugin, dealerId, title, 9, title, null);
        this.confirm = confirm;
        this.cancel = cancel;
        this.dealerId = dealerId;
        this.plugin = plugin;
        slotMapping.put(SlotOption.YES, 0);
        slotMapping.put(SlotOption.NO, 8);

        initializeMenu();
    }

    @Override
    protected void initializeMenu(){
        addItem(createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Yes"), slotMapping.get(SlotOption.YES));
        addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "No"), slotMapping.get(SlotOption.NO));
    }

    public void executeConfirm() {
        confirm.accept(dealerId);
    }

    public void executeCancel() {
        cancel.accept(dealerId);
    }


    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case YES:
                playDefaultSound(player);
                executeConfirm();
                break;
            case NO:
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
