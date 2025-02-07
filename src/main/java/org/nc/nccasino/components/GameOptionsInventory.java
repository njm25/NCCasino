package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GameOptionsInventory extends DealerInventory {

    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();
    private final Nccasino plugin;
    private final String internalName;
    private final Boolean editing;
    private final Villager dealer;
    private final Consumer<UUID> ret;

    private enum SlotOption {
        EXIT,
        RETURN,
        BLACKJACK,
        ROULETTE,
        MINES
    }
  private final Map<SlotOption, Integer> slotMapping = new HashMap<>();
    public GameOptionsInventory(Nccasino plugin, String internalName) {
        super(UUID.randomUUID(), 9, "Select Game Type");
        this.plugin = plugin;
        this.internalName = internalName;
        this.editing = false;
        this.dealer = null;
        this.ret = null;
        slotMapping.put(SlotOption.BLACKJACK, 0);
        slotMapping.put(SlotOption.ROULETTE, 1);
        slotMapping.put(SlotOption.MINES, 2);
        initializeMenu();
    }

    public GameOptionsInventory(Nccasino plugin, Villager dealer, Consumer<UUID> ret) {
        super(UUID.randomUUID(), 9, "Edit Game Type");
        this.plugin = plugin;
        this.internalName = DealerVillager.getInternalName(dealer);
        this.editing = true;
        this.ret = ret;
        this.dealer = dealer;

        slotMapping.put(SlotOption.EXIT, 0);
        slotMapping.put(SlotOption.RETURN, 1);
        slotMapping.put(SlotOption.BLACKJACK, 2);
        slotMapping.put(SlotOption.ROULETTE, 3);
        slotMapping.put(SlotOption.MINES, 4);
    
       initializeMenu();
    }

    private void initializeMenu() {

        if (editing){
            addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+internalName +"'s Admin Menu",  slotMapping.get(SlotOption.RETURN));
            addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
            addItemAndLore(Material.CREEPER_HEAD, 1, "Blackjack",  slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, "Roulette",  slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.TNT, 1, "Mines",  slotMapping.get(SlotOption.MINES));
        }
        else{
            addItemAndLore(Material.CREEPER_HEAD, 1, "Blackjack",  slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, "Roulette",  slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.TNT, 1, "Mines",  slotMapping.get(SlotOption.MINES));
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        UUID playerId = player.getUniqueId();

        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

            SlotOption option = getKeyByValue(slotMapping, slot);
            String gameType;
            switch (option) {
                case BLACKJACK:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    gameType = "Blackjack";
                    break;
                case ROULETTE:
                 if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    gameType = "Roulette";
                    break;
                case MINES:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    gameType = "Mines";
                    break;
                case EXIT:
                    handleExit(player);
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    return; 
                case RETURN:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use",player)!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    executeReturn();
                    return;
                default:
                    return;
            }
            if (!editing){
                createDealer(player, gameType);
            }
            else{
                editDealer(player, gameType);
            }
        } else {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage("§cPlease wait before clicking again!");
                    break;}
                case VERBOSE:{
                    player.sendMessage("§cPlease wait before clicking game options menu again!");
                    break;}
                case NONE:{
                    break;
                }
            }
        }
    }

    private void executeReturn(){
        ret.accept(dealerId);
        delete();
    }

    private void handleExit(Player player){
        player.closeInventory();
        delete();
    }

    private void createDealer(Player player, String gameType) {
        Location location = player.getLocation();
        plugin.saveDefaultDealerConfig(internalName);
        DealerVillager.spawnDealer(plugin, location, "Dealer Villager", internalName, gameType);

        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        // Save dealer data
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }

        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

        var chunk = location.getChunk();
        String path = "dealers." + internalName;
        dealersConfig.set(path + ".world", location.getWorld().getName());
        dealersConfig.set(path + ".chunkX", chunk.getX());
        dealersConfig.set(path + ".chunkZ", chunk.getZ());
        dealersConfig.set(path + ".gameType", gameType);

        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
            e.printStackTrace();
        }
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aDealer with game type " + ChatColor.YELLOW + gameType + ChatColor.GREEN + " created successfully!");
                break;}
            case VERBOSE:{
                player.sendMessage("§aDealer with game type " + ChatColor.YELLOW + gameType + ChatColor.GREEN + " created successfully at x: " +ChatColor.YELLOW+centeredLocation.getX()+"§a y: "+ChatColor.YELLOW+centeredLocation.getY()+ "§a z: "+ChatColor.YELLOW+centeredLocation.getZ()+ "§a.");
                break;}
            case NONE:{
                player.sendMessage("§aDealer created successfully!");
                break;
            }
        }

        player.closeInventory();
        this.delete();
    }

    private void editDealer(Player player, String gameType){
        if (!DealerVillager.isDealerVillager(dealer)) {
            return;
        }
    
        UUID dealerId = DealerVillager.getUniqueId(dealer);

        String internalName = DealerVillager.getInternalName(dealer);


        ConfirmInventory confirmInventory = new ConfirmInventory(
            dealerId,
            "Reset config to default?",
            (uuid) -> {
                // Confirm action

                DealerVillager.switchGame(dealer, gameType, player, true);
                player.closeInventory();

                if (!plugin.getConfig().contains("dealers." + internalName + ".stand-on-17") && gameType.equals("Blackjack")) {
                    plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                } else {
                    int hasStand17Config = plugin.getConfig().getInt("dealers." + internalName + ".stand-on-17");
                    if (hasStand17Config > 100 || hasStand17Config < 0) {
                        plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                    }
                }

        
                this.delete();
            },
            (uuid) -> {
                // Dent action
                DealerVillager.switchGame(dealer, gameType, player, false);
                player.closeInventory();
                this.delete();
            },
            plugin
            );
        player.openInventory(confirmInventory.getInventory());
    }    
    
    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }

}
