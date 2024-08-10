package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class RouletteInventory extends DealerInventory implements Listener {

    private int pageNum;
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;

    private static final NamespacedKey BETS_KEY = new NamespacedKey(Nccasino.getPlugin(Nccasino.class), "bets");

    public RouletteInventory(UUID dealerId, Nccasino plugin) {
        super(dealerId, 54, "Roulette Start Menu");
        this.plugin = plugin;
        this.pageNum = 1;
        this.Bets = new HashMap<>();
        initializeStartMenu();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Bet Management Functions
    public void addBet(UUID playerId, String betType, int wager) {
        Bets.computeIfAbsent(playerId, k -> new Stack<>()).add(new Pair<>(betType, wager));
        updateAllLore(playerId); // Update lore after adding a bet
    }

    public void removeLastBet(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null && !betStack.isEmpty()) {
            betStack.pop();
            updateAllLore(playerId); // Update lore after removing a bet
        }
    }

    public void removeAllBets(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null) {
            betStack.clear();
            updateAllLore(playerId); // Update lore after removing all bets
        }
    }

    public Stack<Pair<String, Integer>> getPlayerBets(UUID playerId) {
        return Bets.getOrDefault(playerId, new Stack<>());
    }

    public void savePlayerBets(UUID playerId, Villager dealer) {
        if (dealer == null) {
       //     plugin.getLogger().warning("Attempted to save player bets, but dealer is null.");
            return;
        }

        PersistentDataContainer container = dealer.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, playerId.toString());
        PersistentDataContainer playerContainer = container.getAdapterContext().newPersistentDataContainer();

        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null) {
            for (int i = 0; i < betStack.size(); i++) {
                Pair<String, Integer> bet = betStack.get(i);
                playerContainer.set(new NamespacedKey(plugin, "bet_" + i), PersistentDataType.STRING, bet.getFirst() + ":" + bet.getSecond());
            }
            container.set(key, PersistentDataType.TAG_CONTAINER, playerContainer);
        }
    }

    public void loadPlayerBets(UUID playerId, Villager dealer) {
        if (dealer == null) {
            plugin.getLogger().warning("Attempted to load player bets, but dealer is null.");
            return;
        }

        PersistentDataContainer container = dealer.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, playerId.toString());
        Stack<Pair<String, Integer>> betStack = new Stack<>();

        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            PersistentDataContainer playerContainer = container.get(key, PersistentDataType.TAG_CONTAINER);

            for (NamespacedKey betKey : playerContainer.getKeys()) {
                String betData = playerContainer.get(betKey, PersistentDataType.STRING);
                if (betData != null) {
                    String[] parts = betData.split(":");
                    String betType = parts[0];
                    int wager = Integer.parseInt(parts[1]);
                    betStack.push(new Pair<>(betType, wager));
                }
            }
            Bets.put(playerId, betStack);
            updateAllLore(playerId); // Update lore after loading bets
        }
    }

    // Update the lore for all betting slots based on the player's bets
    private void updateAllLore(UUID playerId) {
        Map<String, Integer> betTotals = new HashMap<>();
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);

        if (betStack != null) {
            for (Pair<String, Integer> bet : betStack) {
                betTotals.put(bet.getFirst(), betTotals.getOrDefault(bet.getFirst(), 0) + bet.getSecond());
            }

            for (Map.Entry<String, Integer> entry : betTotals.entrySet()) {
                updateItemLoreForBet(entry.getKey(), entry.getValue());
            }
        }
    }

    private void updateItemLoreForBet(String betType, int totalBet) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && betType.equals(meta.getDisplayName())) {
                    List<String> lore = new ArrayList<>();
                    lore.add("Current Bet: " + totalBet + " " + plugin.getCurrencyName(dealerId.toString()));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    // Initialize items for the start menu
    private void initializeStartMenu() {
        inventory.clear();
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette", 1), 22);
    }

    // Set up items for the game menu
    private void setupGameMenu() {
        inventory.clear();
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 20);
        addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)", 1), 24);
    }

    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (pageNum == 1) {
            if (slot == 22) {
                player.sendMessage("Starting Roulette...");
                setupGameMenu();
                pageNum = 2;
                player.openInventory(this.getInventory());
            }
        } else if (pageNum == 2) {
            handleGameMenuClick(slot, player);
        }
    }

    private void handleGameMenuClick(int slot, Player player) {
        if (slot == 20) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                        .findFirst().orElse(null);

                if (dealer != null) {
                    loadPlayerBets(player.getUniqueId(), dealer);
                    Stack<Pair<String, Integer>> bets = getPlayerBets(player.getUniqueId());
                    String internalName = DealerVillager.getInternalName(dealer);
                    BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets, internalName);

                    player.openInventory(bettingTable.getInventory());
                    
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open betting table.");
                }
            }, 1L);
        } else if (slot == 24) {
            player.closeInventory();
            player.sendMessage("You have left the game.");
            removeAllBets(player.getUniqueId());
        }
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
        Villager dealer = (Villager) player.getWorld().getEntity(this.dealerId);
        savePlayerBets(player.getUniqueId(), dealer);
    }

    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets) {
        Bets.put(playerId, bets);
        updateAllLore(playerId); // Update lore after updating player bets
    }
}
