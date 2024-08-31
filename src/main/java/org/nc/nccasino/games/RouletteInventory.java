package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class RouletteInventory extends DealerInventory implements Listener {
    private final List<Integer> wheelLayout = Arrays.asList(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );
    private int currentBallPosition = 0;
    private int pageNum;
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;
    private final Map<Player, BettingTable> Tables;
    private Map<Player, Integer> activeAnimations;
    private static final NamespacedKey BETS_KEY = new NamespacedKey(Nccasino.getPlugin(Nccasino.class), "bets");

    private int bettingCountdownTaskId = -1;
    private boolean betsClosed = false;
    private int bettingTimeSeconds = 10;
    private String internalName;
    private Boolean closeFlag = false;
    private Boolean firstopen = true;
    private Boolean firstFin = true;

    // The offset to track wheel rotation
    private int wheelOffset = 0;

    // Mapping of main slots to their extra slots
    private final Map<Integer, int[]> extraSlotsMap = new HashMap<>();

    public RouletteInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 54, "Roulette Wheel");
        this.plugin = plugin;
        this.pageNum = 1;
        this.Bets = new HashMap<>();
        this.Tables = new HashMap<>();
        this.activeAnimations = new HashMap<>();
        this.internalName = internalName;

        // Initialize extra slots for top-right quadrant
        initializeExtraSlotsForTopRight();

        registerListener();
        plugin.addInventory(dealerId, this);
    }

    private void initializeExtraSlotsForTopRight() {
        // Define main slots for the top-right quadrant
        int[] mainSlots = {27, 28, 29, 30, 31, 32, 33, 43, 53};
        // Define corresponding extra slots for each main slot
        // Adjust these based on your inventory layout
        extraSlotsMap.put(27, new int[]{18});
        extraSlotsMap.put(28, new int[]{19});
        extraSlotsMap.put(29, new int[]{20});
        extraSlotsMap.put(30, new int[]{21});
        extraSlotsMap.put(31, new int[]{22});
        extraSlotsMap.put(32, new int[]{23});
        extraSlotsMap.put(33, new int[]{24, 25});
        extraSlotsMap.put(43, new int[]{34, 35});
        extraSlotsMap.put(53, new int[]{44});
        // Add more if necessary
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void delete() {
        super.delete();
        unregisterListener();
    }

    private void startAnimation(Player player) {
        String animationMessage = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeAnimations.put(player, 1);
            AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
            player.openInventory(animationTable.getInventory());

            animationTable.animateMessage(player, () -> afterAnimationComplete(player));
        }, 1L);
    }

    private void afterAnimationComplete(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeFlag = true;
            if (player != null) {
                if (firstFin) {
                    firstFin = false;
                    this.bettingTimeSeconds = plugin.getTimer(internalName);
                    startBettingTimer();
                }

                player.openInventory(this.getInventory());
            }
        }, 1L);
    }

    @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            if (!firstopen) {
                startAnimation(player);
            }
        }
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.getInventory() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == this.getInventory().getHolder()) {
                    if (firstopen) {
                        firstopen = false;
                        startAnimation(player);
                    }
                }
            }, 2L);
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();
        handleGameMenuClick(slot, player);
    }

    private void setupGameMenu() {
        startBettingTimer();
    }

    private void handleGameMenuClick(int slot, Player player) {
        if (slot == 52) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Villager dealer = (Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                        .filter(entity -> entity instanceof Villager)
                        .map(entity -> (Villager) entity)
                        .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
                        .findFirst().orElse(null);
                if (dealer != null) {
                    Stack<Pair<String, Integer>> bets = getPlayerBets(player.getUniqueId());
                    String internalName = DealerVillager.getInternalName(dealer);
                    BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets, internalName, this);
                    Tables.put(player, bettingTable);
                    player.openInventory(bettingTable.getInventory());
                } else {
                    player.sendMessage("Error: Dealer not found. Unable to open betting table.");
                }
            }, 1L);
        } else if (slot == 53) {
            BettingTable bt = Tables.get(player);
            if (bt != null) {
                bt.clearAllBetsAndRefund(player);
            }
            player.closeInventory();
            player.sendMessage("You have left the game.");

            Tables.remove(player);
            removeAllBets(player.getUniqueId());

            if (Tables.isEmpty()) {
                resetToStartState();
            }
        } else if (slot == 50) {
            if (bettingTimeSeconds > 5)
                bettingTimeSeconds--;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            plugin.saveConfig();
            updateTimerItems();
        } else if (slot == 51) {
            if (bettingTimeSeconds < 64) bettingTimeSeconds++;
            plugin.getConfig().set("dealers." + internalName + ".timer", bettingTimeSeconds);
            plugin.saveConfig();
            updateTimerItems();
        }
    }

    private void updateTimerItems() {
        addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
        addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
    }

    public void addBet(UUID playerId, String betType, int wager) {
        Bets.computeIfAbsent(playerId, k -> new Stack<>()).add(new Pair<>(betType, wager));
        updateAllLore(playerId);
    }

    public void removeFromBets(UUID playId) {
        Bets.remove(playId);
    }

    public void removeLastBet(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null && !betStack.isEmpty()) {
            betStack.pop();
            updateAllLore(playerId);
        }
    }

    public void removeAllBets(UUID playerId) {
        Stack<Pair<String, Integer>> betStack = Bets.get(playerId);
        if (betStack != null) {
            betStack.clear();
            updateAllLore(playerId);
        }
    }

    public Stack<Pair<String, Integer>> getPlayerBets(UUID playerId) {
        return Bets.getOrDefault(playerId, new Stack<>());
    }

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

    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void resetToStartState() {
        Tables.clear();
        firstFin = true;
    }

    private void setTimer(int set) {
        bettingTimeSeconds = set;
    }

    private void startBettingTimer() {
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
        }

        inventory.clear();

        // Initialize menu items
        addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
        addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
        addItem(createCustomItem(Material.BOOK, "Open Betting Table", 1), 52);
        addItem(createCustomItem(Material.BARRIER, "EXIT (Refund and Exit)", 1), 53);

        betsClosed = false;
        bettingCountdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int countdown = bettingTimeSeconds;

            @Override
            public void run() {
                if (countdown > 0) {
                    for (BettingTable bettingTable : Tables.values()) {
                        bettingTable.updateCountdown(countdown, betsClosed);
                    }
                    addItem(createCustomItem(Material.CLOCK, "BETS CLOSE IN " + countdown + " SECONDS!", 1), 45);
                    countdown--;
                } else {
                    handleBetClosure();
                    Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                    bettingCountdownTaskId = -1;
                }
            }
        }, 0L, 20L);
    }

    private boolean isActivePlayer(Player player) {
        InventoryView openInventoryView = player.getOpenInventory();
        Inventory topInventory = openInventoryView.getTopInventory();
        return (topInventory.getHolder() == this || topInventory.getHolder() instanceof BettingTable);
    }

    private void handleBetClosure() {
        betsClosed = true;
        List<Player> activePlayers = new ArrayList<>();
        List<Player> playersWithBets = new ArrayList<>();

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory != null && openInventory.getTopInventory().getHolder() == this) {
                activePlayers.add(player);
            }
        }

        for (Player player : Tables.keySet()) {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory != null && (openInventory.getTopInventory().getHolder() == this || openInventory.getTopInventory().getHolder() == Tables.get(player))) {
                activePlayers.add(player);
            }

            if (player != null && player.isOnline()) {
                Stack<Pair<String, Integer>> playerBets = getPlayerBets(player.getUniqueId());
                if (!playerBets.isEmpty()) {
                    activePlayers.add(player);
                    playersWithBets.add(player);
                }
            }
        }

        if (playersWithBets.isEmpty() && activePlayers.isEmpty()) {
            resetToStartState();
        } else {
            for (Player player : playersWithBets) {
                if (player.isOnline()) {
                    player.sendMessage("Bets Locked!");
                }
            }

            // Update to spinning ball and wheel
            startSpinAnimation(activePlayers);
        }
    }

    private void startSpinAnimation(List<Player> activePlayers) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int spinRevolutions = 5 + new Random().nextInt(5) - 2; // Between 3 to 7 revolutions
            int totalPositions = spinRevolutions * wheelLayout.size();
            long delayBetweenFrames = 2L; // Ticks between each frame

            for (int i = 0; i < totalPositions; i++) {
                final int currentOffset = (wheelOffset - i + wheelLayout.size()) % wheelLayout.size();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    updateWheel(currentOffset);
                }, (i * delayBetweenFrames) * 2);
            }
            wheelOffset = (wheelOffset - totalPositions + wheelLayout.size()) % wheelLayout.size();
        }, 20L); // Start 1 second after bets are closed
    }

    /**
     * Updates the wheel display based on the current offset.
     * This method updates both main slots and their corresponding extra slots.
     *
     * @param offset The current offset for the wheel rotation.
     */
    private void updateWheel(int offset) {
        for (Map.Entry<Integer, int[]> entry : extraSlotsMap.entrySet()) {
            int mainSlot = entry.getKey();
            int[] extras = entry.getValue();
    
            // Calculate the wheel position and number for this slot
            int wheelPosition = (offset + getMainSlotIndex(mainSlot) + wheelLayout.size()) % wheelLayout.size();
            int number = wheelLayout.get(wheelPosition);
    
            // Create the ItemStack with stack size based on the number for the main slot
            ItemStack mainItem = createCustomItem(getMaterialForNumber(number), "Number: " + number, (number == 0) ? 1 : number);
    
            // Update the main slot
            inventory.setItem(mainSlot, mainItem);
    
            // Create a separate ItemStack for extra slots with stack size of 1
            ItemStack extraItem = createCustomItem(getMaterialForNumber(number), "Number: " + number, 1);
    
            // Update the extra slots
            for (int extraSlot : extras) {
                inventory.setItem(extraSlot, extraItem);
            }
        }
    }
    

    /**
     * Retrieves the index of the main slot in the wheel layout.
     *
     * @param mainSlot The main slot number.
     * @return The index in the wheel layout list.
     */
    private int getMainSlotIndex(int mainSlot) {
        // Assuming main slots are sequential in the wheel layout.
        // Modify this method if the mapping is different.
        return wheelLayout.indexOf(getNumberForSlot(mainSlot));
    }

    /**
     * Maps a main slot to its corresponding number in the wheel layout.
     *
     * @param mainSlot The main slot number.
     * @return The number at that slot.
     */
    private int getNumberForSlot(int mainSlot) {
        // This mapping depends on how your wheel is represented.
        // Adjust this method to correctly map slot numbers to wheelLayout indices.
        int slotIndex = Arrays.asList(27, 28, 29, 30, 31, 32, 33, 43, 53).indexOf(mainSlot);
        if (slotIndex == -1) return 0; // Default or handle error
        return wheelLayout.get((wheelOffset + slotIndex) % wheelLayout.size());
    }

    /**
     * Determines the material based on the number's color.
     *
     * @param number The roulette number.
     * @return The corresponding Material.
     */
    private Material getMaterialForNumber(int number) {
        if (number == 0) {
            return Material.GREEN_STAINED_GLASS_PANE;
        } else if (isRed(number)) {
            return Material.RED_STAINED_GLASS_PANE;
        } else {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    /**
     * Checks if a number is red based on standard roulette colors.
     *
     * @param number The roulette number.
     * @return True if red, else false.
     */
    private boolean isRed(int number) {
        int[] redNumbers = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int n : redNumbers) {
            if (n == number) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the game state for the next spin.
     */
    private void resetGameForNextSpin() {
        betsClosed = false;
        for (BettingTable bettingTable : Tables.values()) {
            bettingTable.resetBets();
        }
    }

    /**
     * Updates the player's bets in the internal map.
     *
     * @param playerId The player's UUID.
     * @param bets     The stack of bets.
     * @param player   The Player instance.
     */
    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets, Player player) {
        if (bets == null) {
            bets = new Stack<>();
        }
        Bets.put(playerId, bets);
    }
}
