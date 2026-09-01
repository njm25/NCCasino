package org.nc.nccasino.components;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
    private final LivingEntity dealer;

    public GameOptionsMenu(Player player, Nccasino plugin, String internalName) {
        super(
            player,
            plugin,
            UUID.randomUUID(),
            plugin.getLocalization().text(player, "game-options.select-title"),
            9,
            "",
            null
        );
        this.plugin = plugin;
        this.internalName = internalName;
        this.editing = false;
        this.dealer = null;
        slotMapping.put(SlotOption.BLACKJACK, 0);
        slotMapping.put(SlotOption.ROULETTE, 1);
        slotMapping.put(SlotOption.MINES, 2);
        slotMapping.put(SlotOption.BACCARAT, 3);
        slotMapping.put(SlotOption.COIN_FLIP, 4);
        slotMapping.put(SlotOption.ROCK_PAPER_SCISSORS, 5);
        slotMapping.put(SlotOption.DRAGON_DESCENT, 6);
        slotMapping.put(SlotOption.EXIT, 8);
        initializeMenu();
    }

    public GameOptionsMenu(UUID dealerId, Player player, Nccasino plugin, LivingEntity dealer, Consumer<Player> ret) {
        super(
            player, 
            plugin, 
            dealerId, 
            plugin.getLocalization().text(player, "game-options.edit-title"),
            9, 
            plugin.getLocalization().text(
                player,
                "game-options.return-admin",
                "dealer",
                Dealer.getInternalName(dealer)
            ),
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
        slotMapping.put(SlotOption.BACCARAT, 4);
        slotMapping.put(SlotOption.COIN_FLIP, 5);
        slotMapping.put(SlotOption.ROCK_PAPER_SCISSORS, 6);
        slotMapping.put(SlotOption.DRAGON_DESCENT, 7);



       initializeMenu();
    }

    @Override
    protected void initializeMenu() {

        if (editing){
            addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("game-options.return-admin", "dealer", internalName), slotMapping.get(SlotOption.RETURN));
            addItemAndLore(Material.SPRUCE_DOOR, 1, text("game-options.exit"), slotMapping.get(SlotOption.EXIT));
            addItemAndLore(Material.CREEPER_HEAD, 1, text("game-options.blackjack"), slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, text("game-options.roulette"), slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.SKELETON_SKULL, 1, text("game-options.baccarat"), slotMapping.get(SlotOption.BACCARAT));
            addItemAndLore(Material.SUNFLOWER, 1, text("game-options.coin-flip"), slotMapping.get(SlotOption.COIN_FLIP));
            addItemAndLore(Material.PAPER, 1, text("game-options.rock-paper-scissors"), slotMapping.get(SlotOption.ROCK_PAPER_SCISSORS));
            addItemAndLore(Material.TNT, 1, text("game-options.mines"), slotMapping.get(SlotOption.MINES));
            addItemAndLore(Material.DRAGON_HEAD, 1, text("game-options.dragon-descent"), slotMapping.get(SlotOption.DRAGON_DESCENT));

        }
        else{
            addItemAndLore(Material.CREEPER_HEAD, 1, text("game-options.blackjack"), slotMapping.get(SlotOption.BLACKJACK));
            addItemAndLore(Material.ENDER_PEARL, 1, text("game-options.roulette"), slotMapping.get(SlotOption.ROULETTE));
            addItemAndLore(Material.SUNFLOWER, 1, text("game-options.coin-flip"), slotMapping.get(SlotOption.COIN_FLIP));
            addItemAndLore(Material.PAPER, 1, text("game-options.rock-paper-scissors"), slotMapping.get(SlotOption.ROCK_PAPER_SCISSORS));
            addItemAndLore(Material.SKELETON_SKULL, 1, text("game-options.baccarat"), slotMapping.get(SlotOption.BACCARAT));
            addItemAndLore(Material.TNT, 1, text("game-options.mines"), slotMapping.get(SlotOption.MINES));
            addItemAndLore(Material.DRAGON_HEAD, 1, text("game-options.dragon-descent"), slotMapping.get(SlotOption.DRAGON_DESCENT));
            addItemAndLore(Material.SPRUCE_DOOR, 1, text("game-options.exit"), slotMapping.get(SlotOption.EXIT));
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
            case BACCARAT:
                playDefaultSound(player);
                gameType = "Baccarat";
                break;
            case TEST_GAME:
                playDefaultSound(player);
                gameType = "Test Game";
                break;
            case COIN_FLIP:
                playDefaultSound(player);
                gameType = "Coin Flip";
                break;
            case ROCK_PAPER_SCISSORS:
                playDefaultSound(player);
                gameType = "Rock Paper Scissors";
                break;
            case DRAGON_DESCENT:
                playDefaultSound(player);
                gameType = "Dragon Descent";
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
                player.sendMessage(text("game-options.created", "game", localizedGameType(gameType)));
                break;}
            case VERBOSE:{
                player.sendMessage(text(
                    "game-options.created-at",
                    "game",
                    localizedGameType(gameType),
                    "x",
                    centeredLocation.getX(),
                    "y",
                    centeredLocation.getY(),
                    "z",
                    centeredLocation.getZ()
                ));
                break;}
            case NONE:{
                player.sendMessage(text("game-options.created-minimal"));
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
            text("game-options.reset-confirmation"),
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
                if (!plugin.getConfig().contains("dealers." + internalName + ".default-mines") && gameType.equals("Mines")) {
                    plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
                } else {
                    int deafultMines = plugin.getConfig().getInt("dealers." + internalName + ".default-mines");
                    if (deafultMines > 24 || deafultMines < 1) {
                        plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
                    }
                }

                // Set default values for Dragon Descent
                if (gameType.equals("Dragon Descent")) {
                    if (!plugin.getConfig().contains("dealers." + internalName + ".default-columns")) {
                        plugin.getConfig().set("dealers." + internalName + ".default-columns", 7);
                    }
                    if (!plugin.getConfig().contains("dealers." + internalName + ".default-vines")) {
                        plugin.getConfig().set("dealers." + internalName + ".default-vines", 5);
                    }
                    if (!plugin.getConfig().contains("dealers." + internalName + ".default-floors")) {
                        plugin.getConfig().set("dealers." + internalName + ".default-floors", 4);
                    }
                    
                    // Validate values
                    int columns = plugin.getConfig().getInt("dealers." + internalName + ".default-columns");
                    int vines = plugin.getConfig().getInt("dealers." + internalName + ".default-vines");
                    int floors = plugin.getConfig().getInt("dealers." + internalName + ".default-floors");
                    
                    if (columns < 2 || columns > 9) {
                        plugin.getConfig().set("dealers." + internalName + ".default-columns", 7);
                    }
                    
                    if (vines < 1 || vines >= columns) {
                        plugin.getConfig().set("dealers." + internalName + ".default-vines", Math.min(5, columns - 1));
                    }
                    
                    if (floors < 1 || floors > 100000) {
                        plugin.getConfig().set("dealers." + internalName + ".default-floors", 4);
                    }
                }

                // Set default values for Rock Paper Scissors
                if (gameType.equals("Rock Paper Scissors")) {
                    if (!plugin.getConfig().contains("dealers." + internalName + ".rps-mode")) {
                        plugin.getConfig().set("dealers." + internalName + ".rps-mode", "PLAYER_VS_PLAYER");
                    } else {
                        String rpsModeValue = plugin.getConfig().getString("dealers." + internalName + ".rps-mode", "PLAYER_VS_PLAYER");
                        try {
                            org.nc.nccasino.games.RockPaperScissors.RpsMode.valueOf(rpsModeValue);
                        } catch (IllegalArgumentException e) {
                            plugin.getConfig().set("dealers." + internalName + ".rps-mode", "PLAYER_VS_PLAYER");
                        }
                    }

                    if (!plugin.getConfig().contains("dealers." + internalName + ".rps-max-chain-rounds")) {
                        plugin.getConfig().set("dealers." + internalName + ".rps-max-chain-rounds", -1);
                    } else {
                        int rpsMaxChain = plugin.getConfig().getInt("dealers." + internalName + ".rps-max-chain-rounds");
                        if (rpsMaxChain < -1 || rpsMaxChain > 9999) {
                            plugin.getConfig().set("dealers." + internalName + ".rps-max-chain-rounds", -1);
                        }
                    }

                    if (!plugin.getConfig().contains("dealers." + internalName + ".rps-mode-switching-enabled")) {
                        plugin.getConfig().set("dealers." + internalName + ".rps-mode-switching-enabled", true);
                    }
                }

                // Set default values for Coin Flip
                if (gameType.equals("Coin Flip")) {
                    if (!plugin.getConfig().contains("dealers." + internalName + ".coin-flip-mode")) {
                        plugin.getConfig().set("dealers." + internalName + ".coin-flip-mode", "PLAYER_VS_PLAYER");
                    } else {
                        String coinFlipModeValue = plugin.getConfig().getString("dealers." + internalName + ".coin-flip-mode", "PLAYER_VS_PLAYER");
                        try {
                            org.nc.nccasino.games.CoinFlip.CoinFlipMode.valueOf(coinFlipModeValue);
                        } catch (IllegalArgumentException e) {
                            plugin.getConfig().set("dealers." + internalName + ".coin-flip-mode", "PLAYER_VS_PLAYER");
                        }
                    }

                    if (!plugin.getConfig().contains("dealers." + internalName + ".coin-flip-max-chain-rounds")) {
                        plugin.getConfig().set("dealers." + internalName + ".coin-flip-max-chain-rounds", -1);
                    } else {
                        int coinFlipMaxChain = plugin.getConfig().getInt("dealers." + internalName + ".coin-flip-max-chain-rounds");
                        if (coinFlipMaxChain < -1 || coinFlipMaxChain > 9999) {
                            plugin.getConfig().set("dealers." + internalName + ".coin-flip-max-chain-rounds", -1);
                        }
                    }

                    if (!plugin.getConfig().contains("dealers." + internalName + ".coin-flip-mode-switching-enabled")) {
                        plugin.getConfig().set("dealers." + internalName + ".coin-flip-mode-switching-enabled", true);
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

    private String localizedGameType(String gameType) {
        return switch (gameType) {
            case "Blackjack" -> text("game-options.blackjack");
            case "Roulette" -> text("game-options.roulette");
            case "Mines" -> text("game-options.mines");
            case "Baccarat" -> text("game-options.baccarat");
            case "Coin Flip" -> text("game-options.coin-flip");
            case "Rock Paper Scissors" -> text("game-options.rock-paper-scissors");
            case "Dragon Descent" -> text("game-options.dragon-descent");
            case "Test Game" -> text("game-options.test-game");
            default -> gameType;
        };
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
