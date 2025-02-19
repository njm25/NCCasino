package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GameOptionsMenu extends Menu {

    private final Nccasino plugin;
    private final String internalName;
    private final Boolean editing;
    private final Mob dealer;

    public GameOptionsMenu(Player player, Nccasino plugin, String internalName) {
        super(player, plugin, UUID.randomUUID(), "Select Game Type", 9, "", null);
        this.plugin = plugin;
        this.internalName = internalName;
        this.editing = false;
        this.dealer = null;
        slotMapping.put(SlotOption.BLACKJACK, 0);
        slotMapping.put(SlotOption.ROULETTE, 1);
        slotMapping.put(SlotOption.MINES, 2);
        slotMapping.put(SlotOption.TEST_GAME, 3);
        slotMapping.put(SlotOption.EXIT, 8);
        initializeMenu();
    }

    public GameOptionsMenu(Player player, Nccasino plugin, Mob dealer, Consumer<Player> ret) {
        super(
            player, 
            plugin, 
            UUID.randomUUID(), 
            "Edit Game Type", 
            9, 
            "Return to " + Dealer.getInternalName(dealer) + "'s Admin Menu", 
            ret
        );
        this.plugin = plugin;
        this.internalName = Dealer.getInternalName(dealer);
        this.editing = true;
        this.dealer = dealer;

        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.BLACKJACK, 1);
        slotMapping.put(SlotOption.ROULETTE, 2);
        slotMapping.put(SlotOption.MINES, 3);
        slotMapping.put(SlotOption.TEST_GAME, 4);
        
    
       initializeMenu();
    }

    @Override
    protected void initializeMenu() {

        if (editing){
            addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+internalName +"'s Admin Menu",  slotMapping.get(SlotOption.RETURN));
            addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
            addItemAndLore(Material.CREEPER_HEAD, 1, "Blackjack",  slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, "Roulette",  slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.ENDER_PEARL, 1, "Test Game",  slotMapping.get(SlotOption.TEST_GAME));
            addItemAndLore(Material.TNT, 1, "Mines",  slotMapping.get(SlotOption.MINES));
        }
        else{
            addItemAndLore(Material.CREEPER_HEAD, 1, "Blackjack",  slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, "Roulette",  slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.ENDER_PEARL, 1, "Test Game",  slotMapping.get(SlotOption.TEST_GAME));
            addItemAndLore(Material.TNT, 1, "Mines",  slotMapping.get(SlotOption.MINES));
            addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
  
        String gameType;
        switch (option) {
            case BLACKJACK:
                playDefaultSound(player);
                gameType = "Blackjack";
                break;
            case ROULETTE:
                playDefaultSound(player);
                gameType = "Roulette";
                break;
            case MINES:
                playDefaultSound(player);
                gameType = "Mines";
                break;
            case TEST_GAME:
                playDefaultSound(player);
                gameType = "Test Game";
                break;
            default:
                return;
        }
        if (!editing){
            createDealer(player, gameType);
        }
        else{
            editDealer(player, gameType);
        }
    }

    private void createDealer(Player player, String gameType) {
        Location location = player.getLocation();
        plugin.saveDefaultDealerConfig(internalName);
        Dealer.spawnDealer(plugin, location, "Dealer", internalName, gameType, EntityType.VILLAGER);
        Location centeredLocation = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        // Save dealer data
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }

        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

        String path = "dealers." + internalName;
        dealersConfig.set(path + ".world", location.getWorld().getName());
        dealersConfig.set(path + ".X", centeredLocation.getX());
        dealersConfig.set(path + ".Y", centeredLocation.getY());
        dealersConfig.set(path + ".Z", centeredLocation.getZ());

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
        if (!Dealer.isDealer(dealer)) {
            return;
        }
    
        UUID dealerId = Dealer.getUniqueId(dealer);

        String internalName = Dealer.getInternalName(dealer);


        ConfirmMenu confirmInventory = new ConfirmMenu(
            player,
            dealerId,
            "Reset config to default?",
            (uuid) -> {
                // Confirm action

                Dealer.switchGame(dealer, gameType, player, true);
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
                Dealer.switchGame(dealer, gameType, player, false);
                player.closeInventory();
                this.delete();
            },
            plugin
            );
        player.openInventory(confirmInventory.getInventory());
    }    


}
