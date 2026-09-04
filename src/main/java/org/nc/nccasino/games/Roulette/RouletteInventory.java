package org.nc.nccasino.games.Roulette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.nc.VSE.MultiChannelEngine;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.objects.Pair;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class RouletteInventory extends DealerInventory implements TerminableSession {
    private final MultiChannelEngine mce;
    private final List<Integer> wheelLayout = RouletteWheelLayout.WHEEL_NUMBERS;
    private final Set<UUID> switchingPlayers = new HashSet<>();
    private final Nccasino plugin;
    private final Map<UUID, Stack<Pair<String, Integer>>> Bets;
    public final Map<UUID, BettingTable> Tables;
    // Per-player localized wheel views onto this shared round. Not yet
    // populated by any open/switch call site or rendered into -- wired up
    // in a later stage of the localization refactor. Purely additive and
    // inert until then.
    private final Map<UUID, RouletteWheelView> views = new HashMap<>();
    // Tracks a spin's already-computed winning payout for the ~1s window
    // between it being scheduled and the deposit task actually running.
    // Lets onSessionTerminated tell "a win is in flight, pay this exact
    // amount" apart from "no resolution has happened yet, refund the
    // stake from Bets" on a plugin/server shutdown.
    private final Map<UUID, Double> pendingOnlineDeposits = new HashMap<>();
    private int frameCounter;
    private int bettingCountdownTaskId = -1;
    private boolean betsClosed = false;
    private int bettingTimeSeconds = 25;
    private int globalCountdown=bettingTimeSeconds;
    private String internalName;
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private Boolean firstFin = true;
    private int spinTaskId;
    private int fastSpinTaskId;
    private int bfastSpinTaskId;
    private int regTaskId;
    private int reg2TaskId;
    private int ballTaskId;
    private int wheelOffset = 0;
    private int currentQuadrant = 1; // 1=Top-Right, 2=Top-Left, 3=Bottom-Left, 4=Bottom-Right
    private boolean ballMovementStarted = false;
    private boolean spinAnimationOver = false;
    private int ballSpinDirection;
    private boolean foundfirstquadrant =false;
    private Boolean eightflag=false;
    private Boolean sevflag=false;
    private Boolean nextflag=false;
    // Add these variables at the class level
    private static final long INITIAL_BALL_SPEED = 1L;
    private static final long MIN_BALL_SPEED = 6L;

    // Quadrant-specific slot mappings for main and extra slots
    private final Map<Integer, int[]> extraSlotsMapTopRight = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapTopLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomLeft = new HashMap<>();
    private final Map<Integer, int[]> extraSlotsMapBottomRight = new HashMap<>();

    ///////////vvvvvvBall Movement Variables/////////////////////////////////////////
    private final Map<Integer, List<Integer>> tracksTopRight = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksTopLeft = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksBottomLeft = new HashMap<>();
    private final Map<Integer, List<Integer>> tracksBottomRight = new HashMap<>();
    // Ball movement spin ranges per track (in spins)
    private final double track1MinSpins = 1.5;
    private final double track1MaxSpins = 3;
    private final double track2MinSpins = .5;
    private final double track2MaxSpins = 1;
    private final double track3MinSpins = 1/36.0;
    private final double track3MaxSpins = 1/36.0;
    private final double track4MinSpins = 1/18.0;
    private final double track4MaxSpins =1/18.0;
    private int slotsPerSpinTrack1;
    private int slotsPerSpinTrack2;
    private int slotsPerSpinTrack3;
    private int slotsPerSpinTrack4;
    private int minSlotsTrack1;
    private int maxSlotsTrack1;
    private int minSlotsTrack2;
    private int maxSlotsTrack2;
    private int minSlotsTrack3;
    private int maxSlotsTrack3;
    private int minSlotsTrack4;
    private int maxSlotsTrack4;
    private int[] slotsToMovePerTrack = new int[5]; // 5 tracks in the sequence
    private final int[] trackSequence = {1, 2, 3/*, 4, 3*/};
    private int trackSequenceIndex = 0;
    private int ballCurrentTrack;
    private int ballCurrentIndex;
    private int ballPreviousSlot = -1;
    private boolean isSwitchingQuadrant = false;
    private boolean finalpicked;
    private int winningNumber;
    private boolean flip2=true;
    private boolean flip4=true;
    private int wheelSpinDirection = 1; // 1 for clockwise, -1 for counter-clockwise
    private int lastDisplayedOffset = 0;
    private final Set<Integer> activeTaskIds = new HashSet<>();
    private BukkitTask miscTask;
    private int miscTaskId;
    /////////////////////////////////////////////////////////////////////////////////////////
    private final Map<Integer, ItemStack> originalSlotItems = new HashMap<>();

    public RouletteInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(
            dealerId,
            54,
            plugin.getLocalization().text(plugin.getLocalization().getServerDefault(), "roulette.wheel-title")
        );
        this.plugin = plugin;
        this.Bets = new HashMap<>();
        this.Tables = new HashMap<>();
        this.internalName = internalName;
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.mce = new MultiChannelEngine(plugin);
        initializeTracks();
        initializeExtraSlots();
        registerListener();
        plugin.addInventory(dealerId, this);
        this.dealerId = dealerId;
    }

    public MultiChannelEngine getMCE() {
        return mce;
    }

    private void initializeTracks() {
        // Existing track initialization code...
    
        // Quadrant 1 (Top-Right)
        tracksTopRight.put(1, Arrays.asList(17, 7, 6, 5, 4, 3, 2, 1, 0));
        tracksTopRight.put(2, Arrays.asList(26, 16, 15, 14, 13, 12, 11, 10, 9));
        tracksTopRight.put(3, Arrays.asList(44,34, 24,23, 22, 21, 20, 19, 18));
        tracksTopRight.put(4, Arrays.asList(53,43, 33, 32, 31, 30, 29, 28, 27));
    
        // Quadrant 2 (Top-Left)
        tracksTopLeft.put(1, Arrays.asList(9, 1, 2, 3, 4, 5, 6, 7, 8));
        tracksTopLeft.put(2, Arrays.asList(18, 10, 11, 12, 13, 14, 15, 16, 17));
        tracksTopLeft.put(3, Arrays.asList(36, 28, 20, 21, 22, 23, 24, 25, 26));
        tracksTopLeft.put(4, Arrays.asList(45,37,29, 30, 31, 32, 33, 34, 35));
    
        // Quadrant 3 (Bottom-Left)
        tracksBottomLeft.put(1, Arrays.asList(36, 46, 47, 48, 49, 50, 51, 52, 53));
        tracksBottomLeft.put(2, Arrays.asList(27, 37, 38, 39, 40, 41, 42, 43, 44));
        tracksBottomLeft.put(3, Arrays.asList(9,19, 29, 30, 31, 32, 33, 34, 35));
        tracksBottomLeft.put(4, Arrays.asList(0,10, 20, 21, 22, 23, 24, 25, 26));
    
        // Quadrant 4 (Bottom-Right)
        tracksBottomRight.put(1, Arrays.asList(44, 52, 51, 50, 49, 48, 47, 46, 45));
        tracksBottomRight.put(2, Arrays.asList(35, 43, 42, 41, 40, 39, 38, 37, 36));
        tracksBottomRight.put(3, Arrays.asList(17,25, 33, 32, 31, 30, 29, 28, 27));
        tracksBottomRight.put(4, Arrays.asList(8, 16, 24, 23, 22, 21, 20, 19, 18));
    
        // Calculate total slots per spin per track
        slotsPerSpinTrack1 = tracksTopRight.get(1).size() + tracksTopLeft.get(1).size() + tracksBottomLeft.get(1).size() + tracksBottomRight.get(1).size();
        slotsPerSpinTrack2 = tracksTopRight.get(2).size() + tracksTopLeft.get(2).size() + tracksBottomLeft.get(2).size() + tracksBottomRight.get(2).size();
        slotsPerSpinTrack3 = tracksTopRight.get(3).size() + tracksTopLeft.get(3).size() + tracksBottomLeft.get(3).size() + tracksBottomRight.get(3).size();
        slotsPerSpinTrack4 = tracksTopRight.get(4).size() + tracksTopLeft.get(4).size() + tracksBottomLeft.get(4).size() + tracksBottomRight.get(4).size();
    
        // Calculate min and max slots per track
        minSlotsTrack1 = (int)(track1MinSpins * slotsPerSpinTrack1);
        maxSlotsTrack1 = (int)(track1MaxSpins * slotsPerSpinTrack1);
        minSlotsTrack2 = (int)(track2MinSpins * slotsPerSpinTrack2);
        maxSlotsTrack2 = (int)(track2MaxSpins * slotsPerSpinTrack2);
        minSlotsTrack3 = (int)(track3MinSpins * slotsPerSpinTrack3);
        maxSlotsTrack3 = (int)(track3MaxSpins * slotsPerSpinTrack3);
        minSlotsTrack4 = (int)(track4MinSpins * slotsPerSpinTrack4);
        maxSlotsTrack4 = (int)(track4MaxSpins * slotsPerSpinTrack4);
    }

    private void initializeExtraSlots() {
        // Top-right quadrant main and extra slots
        extraSlotsMapTopRight.put(27, new int[]{18});
        extraSlotsMapTopRight.put(28, new int[]{19});
        extraSlotsMapTopRight.put(29, new int[]{20});
        extraSlotsMapTopRight.put(30, new int[]{21});
        extraSlotsMapTopRight.put(31, new int[]{22});
        extraSlotsMapTopRight.put(32, new int[]{23});
        extraSlotsMapTopRight.put(33, new int[]{24, 25});
        extraSlotsMapTopRight.put(43, new int[]{34, 35});
        extraSlotsMapTopRight.put(53, new int[]{44});

        // Top-left quadrant main and extra slots
        extraSlotsMapTopLeft.put(45, new int[]{36});
        extraSlotsMapTopLeft.put(37, new int[]{28, 27});
        extraSlotsMapTopLeft.put(29, new int[]{20, 19});
        extraSlotsMapTopLeft.put(30, new int[]{21});
        extraSlotsMapTopLeft.put(31, new int[]{22});
        extraSlotsMapTopLeft.put(32, new int[]{23});
        extraSlotsMapTopLeft.put(33, new int[]{24});
        extraSlotsMapTopLeft.put(34, new int[]{25});
        extraSlotsMapTopLeft.put(35, new int[]{26});

        // Bottom-left quadrant main and extra slots
        extraSlotsMapBottomLeft.put(0, new int[]{9});
        extraSlotsMapBottomLeft.put(10, new int[]{19, 18});
        extraSlotsMapBottomLeft.put(20, new int[]{29, 28});
        extraSlotsMapBottomLeft.put(21, new int[]{30});
        extraSlotsMapBottomLeft.put(22, new int[]{31});
        extraSlotsMapBottomLeft.put(23, new int[]{32});
        extraSlotsMapBottomLeft.put(24, new int[]{33});
        extraSlotsMapBottomLeft.put(25, new int[]{34});
        extraSlotsMapBottomLeft.put(26, new int[]{35});

        // Bottom-right quadrant main and extra slots
        extraSlotsMapBottomRight.put(18, new int[]{27});
        extraSlotsMapBottomRight.put(19, new int[]{28});
        extraSlotsMapBottomRight.put(20, new int[]{29});
        extraSlotsMapBottomRight.put(21, new int[]{30});
        extraSlotsMapBottomRight.put(22, new int[]{31});
        extraSlotsMapBottomRight.put(23, new int[]{32});
        extraSlotsMapBottomRight.put(24, new int[]{33,34});
        extraSlotsMapBottomRight.put(16, new int[]{25,26});
        extraSlotsMapBottomRight.put(8, new int[]{17});
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
         for (int taskId : activeTaskIds) {
            if(taskId!=1){
        Bukkit.getScheduler().cancelTask(taskId);}
        }
        if (spinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(spinTaskId);
            spinTaskId = -1;
        }
        if (fastSpinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(fastSpinTaskId);
            fastSpinTaskId = -1;
        }
        if (bfastSpinTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
            bfastSpinTaskId = -1;
        }
        if (ballTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ballTaskId);
            ballTaskId = -1;
        }
        if (bettingCountdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
            bettingCountdownTaskId = -1;
        }
    
        // Close and clean up every currently open per-player view. Views
        // hold their own Bukkit inventory (a different InventoryHolder than
        // this controller), so DealerInventory.updateInventory's generic
        // holder-matching close loop can't find them by comparing against
        // this instance -- do it here instead, using the map we actually
        // control, so a replaced dealer never leaves a stale view open
        // against a deleted controller.
        for (RouletteWheelView view : new ArrayList<>(views.values())) {
            Player player = Bukkit.getPlayer(view.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            } else {
                views.remove(view.getPlayerId());
                view.cleanupListener();
            }
        }
        views.clear();

        // Also clear any data references if you like
        Bets.clear();
        Tables.clear();
        playersWithBets.clear();
        newtry.clear();
        unregisterListener();

    }


    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
             
        if (event.getInventory() == this.getInventory()){
            Player player = (Player) event.getPlayer();
            if (player.getInventory() != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player != null&&player.isOnline()) {
                        if (!BettingTable.switchingPlayers.contains(player.getUniqueId())){
                        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                            case STANDARD:{
                                break;}
                            case VERBOSE:{

                                player.sendMessage(text(player, "roulette.welcome"));
                                break;
                            }
                                case NONE:{
                                break;
                            }
                        } 
                    }
                        if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
                            mce.addPlayerToChannel("Master", player);
                            mce.addPlayerToChannel("RouletteWheel", player);
                        }
        
                        if (firstFin) {
                            this.bettingTimeSeconds = plugin.getTimer(internalName);
                            firstFin = false;
                            startBettingTimer();
                        }
        
                        
                    }
                }, 2L);
            }
        }
        
    }

    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        switchingPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // Check if the player is switching inventories
        if (switchingPlayers.contains(player.getUniqueId())) {
            return; // Ignore this close event if the player is switching
        }
    
        // Check if the inventory being closed matches this specific RouletteInventory
        InventoryView closedInventory = event.getView();
        if (closedInventory != null && closedInventory.getTopInventory().getHolder() == this) {
            // Properly remove the player from all channels
            mce.removePlayerFromAllChannels(player);
        }
    }

    // ---- Per-player RouletteWheelView plumbing -----------------------
    // Not yet reachable from any open/switch call site (later stage of the
    // localization refactor); these exist so RouletteWheelView has a
    // stable seam to call into once it is actually wired up.

    /**
     * Returns this player's localized wheel view, creating and immediately
     * painting it with the current round state if it doesn't exist yet --
     * so a late or returning viewer never sees a blank wheel while
     * waiting for the next scheduled render tick.
     */
    public Inventory getOrCreateView(Player player) {
        UUID id = player.getUniqueId();
        RouletteWheelView existing = views.get(id);
        if (existing != null) {
            return existing.getInventory();
        }
        RouletteWheelView view = new RouletteWheelView(player, this, plugin);
        views.put(id, view);
        bootstrapView(view);
        return view.getInventory();
    }

    /**
     * Paints the current wheel state into exactly one freshly created view,
     * by cloning the legacy inventory's contents slot-for-slot rather than
     * reconstructing the frame from currentQuadrant/lastDisplayedOffset/
     * ballPreviousSlot. Every fan-out renderer (renderToAllInventories,
     * renderBallToAllInventories, ...) writes to the legacy inventory
     * first, so it's the authoritative record of whatever every other open
     * view is currently showing -- including transitional states (an
     * in-flight quadrant switch, a ball mid-landing) that reconstructing
     * from those fields alone could get stale or inconsistent with what's
     * already on screen elsewhere. Deliberately does not call
     * updateQuadrantDisplay itself: once a round has resolved
     * (finalpicked), that method's final-landing-highlight branch schedules
     * new delayed quadrant-switch tasks on every call regardless of
     * whether the wheel already found its landing spot, so calling it
     * again here to backfill one view would duplicate those scheduled
     * tasks. Touches only the new view's own inventory -- no fan-out to
     * any other already-open view.
     */
    private void bootstrapView(RouletteWheelView view) {
        Inventory target = view.getInventory();
        Player viewer = Bukkit.getPlayer(view.getPlayerId());

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack source = inventory.getItem(slot);
            target.setItem(slot, source == null ? null : source.clone());
        }

        // Re-localize the player-visible text on top of the clone,
        // identified by role (material) rather than by slot bookkeeping
        // that could be stale for the same reason the old reconstruction
        // was. The "seconds" placeholder is read back from the cloned
        // clock's own stack amount rather than globalCountdown: the
        // countdown task renders the clock with the pre-decrement value
        // and only stores the decremented value into globalCountdown
        // afterward, so globalCountdown is transiently one second behind
        // whatever's actually on screen.
        for (int slot = 0; slot < target.getSize(); slot++) {
            ItemStack item = target.getItem(slot);
            if (item == null) {
                continue;
            }
            switch (item.getType()) {
                case CLOCK: {
                    String label = betsClosed
                        ? localize(viewer, "roulette.bets-closed")
                        : localize(viewer, "roulette.bets-close-in", "seconds", item.getAmount());
                    target.setItem(slot, createCustomItem(Material.CLOCK, label, item.getAmount()));
                    break;
                }
                case BOOK:
                    target.setItem(slot, createCustomItem(Material.BOOK, localize(viewer, "roulette.open-table"), item.getAmount()));
                    break;
                case SPRUCE_DOOR:
                    target.setItem(slot, createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "roulette.refund-exit"), item.getAmount()));
                    break;
                case ENDER_PEARL:
                    target.setItem(slot, createBallItem(localize(viewer, "roulette.ball")));
                    break;
                default:
                    break;
            }
        }
    }

    private String localize(Player viewer, String key, Object... placeholders) {
        return viewer != null ? text(viewer, key, placeholders) : text(key, placeholders);
    }

    /**
     * Writes a locale-independent item (wheel numbers, decorative panes --
     * nothing with player-visible text) to the legacy shared inventory and
     * to every currently open per-player view. Views map is empty until a
     * later stage flips the open call sites, so this is a no-op today.
     */
    private void renderToAllInventories(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        for (RouletteWheelView view : views.values()) {
            view.getInventory().setItem(slot, item.clone());
        }
    }

    /**
     * Writes an item whose display name is player-visible text: the legacy
     * shared inventory keeps resolving it against the server default
     * (unchanged from before), while every currently open view resolves it
     * against its own player's locale. Views map is empty until a later
     * stage flips the open call sites, so this is behaviorally a no-op
     * today beyond the (unchanged) legacy-inventory write.
     */
    private void renderLocalizedToAllInventories(int slot, Material material, int amount, String key, Object... placeholders) {
        inventory.setItem(slot, createCustomItem(material, text(key, placeholders), amount));
        for (RouletteWheelView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            String label = viewer != null ? text(viewer, key, placeholders) : text(key, placeholders);
            view.getInventory().setItem(slot, createCustomItem(material, label, amount));
        }
    }

    private ItemStack createBallItem(String displayName) {
        ItemStack ballItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = ballItem.getItemMeta();
        meta.setDisplayName(displayName);
        ballItem.setItemMeta(meta);
        return ballItem;
    }

    /**
     * Draws the ball in the legacy inventory and every view, each with its
     * own localized name. Does not snapshot what was under it -- for the
     * continuous ball-movement animation that needs a later restore, use
     * {@link #placeBallInAllInventories}.
     */
    private void renderBallToAllInventories(int slot) {
        inventory.setItem(slot, createBallItem(text("roulette.ball")));
        for (RouletteWheelView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            String label = viewer != null ? text(viewer, "roulette.ball") : text("roulette.ball");
            view.getInventory().setItem(slot, createBallItem(label));
        }
    }

    /**
     * Snapshots whatever's currently under the ball (once, from the legacy
     * inventory -- identical across every view since only non-localized
     * content ever sits under the ball) before drawing it, exactly like
     * the original single-inventory version. The snapshot is what
     * {@link #restoreSlotInAllInventories} later restores.
     */
    private void placeBallInAllInventories(int slot) {
        if (!originalSlotItems.containsKey(slot)) {
            originalSlotItems.put(slot, inventory.getItem(slot));
        }
        renderBallToAllInventories(slot);
    }

    /**
     * Restores whatever was snapshotted under the ball, in the legacy
     * inventory and every view. No-op if this slot was never snapshotted
     * (e.g. the final-landing highlight, which draws the ball without one
     * -- matching the original, which never restores that placement
     * either).
     */
    private void restoreSlotInAllInventories(int slot) {
        if (slot == -1 || !originalSlotItems.containsKey(slot)) {
            return;
        }
        ItemStack original = originalSlotItems.remove(slot);
        inventory.setItem(slot, original);
        for (RouletteWheelView view : views.values()) {
            view.getInventory().setItem(slot, original.clone());
        }
    }

    void handleViewClick(int slot, Player player) {
        handleGameMenuClick(slot, player);
    }

    void onViewOpened(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) {
                if (!BettingTable.switchingPlayers.contains(player.getUniqueId())) {
                    switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                        case STANDARD: {
                            break;
                        }
                        case VERBOSE: {
                            player.sendMessage(text(player, "roulette.welcome"));
                            break;
                        }
                        case NONE: {
                            break;
                        }
                    }
                }
                if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
                    mce.addPlayerToChannel("Master", player);
                    mce.addPlayerToChannel("RouletteWheel", player);
                }
                if (firstFin) {
                    this.bettingTimeSeconds = plugin.getTimer(internalName);
                    firstFin = false;
                    startBettingTimer();
                }
            }
        }, 2L);
    }

    void onViewClosed(Player player, RouletteWheelView view) {
        views.remove(player.getUniqueId(), view);
        view.cleanupListener();
        if (switchingPlayers.contains(player.getUniqueId())) {
            return;
        }
        mce.removePlayerFromAllChannels(player);
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        handleGameMenuClick(slot, player);
    }


private void handleGameMenuClick(int slot, Player player) {
    if(!betsClosed){
    switch (currentQuadrant) {
        case 1: // Top-right quadrant (initial view)
            switch (slot) {
                /* 
                case 46: // -1 Betting Timer
                    adjustBettingTimer(-1,1);
                    break;
                case 47: // +1 Betting Timer
                    adjustBettingTimer(1,1);
                    break;
*/
                case 46: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 47: // View Betting Info
                 if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);
                    exitGame(player);
                    break;
                default:
                    break;
            }
            break;

        case 2: // Top-left quadrant
            switch (slot) {
 /* 
                case 49: // -1 Betting Timer
                    adjustBettingTimer(-1,2);
                    break;
                case 50: // +1 Betting Timer
                    adjustBettingTimer(1,2);
                    break;
 */

                case 52: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 53: // Exit
                if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 3: // Bottom-left quadrant
            switch (slot) {
 /* 
                case 4: // -1 Betting Timer
                    adjustBettingTimer(-1,3);
                    break;
                case 5: // +1 Betting Timer
                    adjustBettingTimer(1,3);
                    break;
*/

                case 7: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 8: // Exit
                if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        case 4: // Bottom-right quadrant
            switch (slot) {
 /* 
                case 0: // -1 Betting Timer
                    adjustBettingTimer(-1,4);
                    break;
                case 1: // +1 Betting Timer
                    adjustBettingTimer(1,4);
                    break;
*/
                case 1: // Open Betting Table
                    openBettingTable(player);
                    break;
                case 2: // Exit
                if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);

                    exitGame(player);
                    break;
                default:
                    // Handle other slots
                    break;
            }
            break;

        default:
            // Handle invalid quadrants if needed
            break;
    }
}
}

private void openBettingTable(Player player) {
    switchingPlayers.add(player.getUniqueId()); // Mark the player as switching inventories

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        LivingEntity dealer = Dealer.findDealer(dealerId, player.getLocation());
        if (dealer != null) {
            Stack<Pair<String, Integer>> bets = getPlayerBets(player.getUniqueId());
            String internalName = Dealer.getInternalName(dealer);
            BettingTable bettingTable = new BettingTable(player, dealer, plugin, bets, internalName, this, globalCountdown);
            Tables.put(player.getUniqueId(), bettingTable);
            player.openInventory(bettingTable.getInventory());
             if (SoundHelper.getSoundSafely("item.book.page_turn", player) != null)player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 5.0f, 1.0f);
             if (plugin.getPreferences(player.getUniqueId()).getSoundSetting() == Preferences.SoundSetting.ON) {
            mce.addPlayerToChannel("BettingTable", player);
            mce.removePlayerFromChannel("RouletteWheel", player);}
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "roulette.opened-table"));            break;
                }
                    case NONE:{
                    break;
                }
            }
        } else {
            player.sendMessage(text(player, "roulette.dealer-not-found"));
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switchingPlayers.remove(player.getUniqueId()); // Remove the flag after the switch
    }, 1L); // Small delay to allow the inventory to switch
}


private void exitGame(Player player) {
    UUID playerId = player.getUniqueId();
    BettingTable bt = Tables.remove(playerId);
    if (bt != null) {
        bt.clearAllBetsAndRefund(player);
        bt.cleanupListener();
    }
    player.closeInventory();
    switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
        case STANDARD:{
            break;}
        case VERBOSE:{
    player.sendMessage(text(player, "roulette.left-game"));
    break;
        }
            case NONE:{
            break;
        }
    }
    removeAllBets(playerId);
    newtry.remove(playerId);
    playersWithBets.remove(playerId);
    SessionRegistry.unregister(playerId, this);

    if (Tables.isEmpty()) {
        resetToStartState();
    }
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
                    lore.add(text(
                        "roulette.wager-lore",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalBet)
                    ));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }


    private void resetToStartState() {
        Tables.clear();
        firstFin = true;
    }

    

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<UUID,Stack<Pair<String, Integer>>> newtry=new HashMap();
    private List<UUID> playersWithBets = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private void handleBetClosure() {
        newtry.clear();
        betsClosed = true;
        List<Player> activePlayers = new ArrayList<>();
        playersWithBets.clear();

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player == null)
                continue;
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory == null) {
                continue;
            }
            Object holder = openInventory.getTopInventory().getHolder();
            if (holder == this || (holder instanceof RouletteWheelView view && views.get(player.getUniqueId()) == view)) {
                activePlayers.add(player);
            }
        }

        // Snapshot every player with a committed bet into this round's
        // resolution list, regardless of whether they're online right now.
        // The spin resolves independently of player presence, so a bet
        // already withdrawn from a player's balance must ride the spin to
        // a real outcome (delivered as a pending payout if they're gone by
        // the time it resolves) rather than being silently excluded and
        // stranded here just because they happened to be offline at this
        // exact tick.
        for (UUID playerId : Tables.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                InventoryView openInventory = player.getOpenInventory();
                if (openInventory != null && (openInventory.getTopInventory().getHolder() == this || openInventory.getTopInventory().getHolder() == Tables.get(playerId))) {
                    if (!activePlayers.contains(player)) {
                        activePlayers.add(player);
                    }
                }
            }

            Stack<Pair<String, Integer>> playerBets = getPlayerBets(playerId);
            if (!playerBets.isEmpty()) {
                newtry.put(playerId, (Stack<Pair<String, Integer>>) playerBets.clone());
                if (player != null && !activePlayers.contains(player)) {
                    activePlayers.add(player);
                }
                playersWithBets.add(playerId);
                Bets.put(playerId, playerBets);
            }
        }

        if (playersWithBets.isEmpty() && activePlayers.isEmpty()) {
            resetToStartState();
        } else {

            for (UUID playerId : playersWithBets) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
                        case STANDARD:{
                            break;}
                        case VERBOSE:{
                            player.sendMessage(text(player, "roulette.bets-locked"));
                            break;
                        }
                            case NONE:{
                            break;
                        }
                    }
                }
            }

            miscTask=Bukkit.getScheduler().runTaskLater(plugin, () ->
            mce.playSong("RouletteWheel", RouletteSongs.getBallLaunch(), false, "Ball Launch")
            , 20L);
            activeTaskIds.add(miscTask.getTaskId()); // Store the task ID

            miscTask=Bukkit.getScheduler().runTaskLater(plugin, () -> startBallMovement(false), 100L);
            activeTaskIds.add(miscTask.getTaskId());
            // Transition wheel to slower spin after bets close
            // Update to spinning ball and wheel
            startSpinAnimation(activePlayers);
        }
    }
    
private void startBettingTimer() {
    if (bettingCountdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
    }

    // Start the slow spin as soon as the betting phase begins
    startSlowSpinAnimation(6L); 

    betsClosed = false;

    // Initialize the menu buttons in their proper quadrant locations
    updateMenuButtonsForQuadrant(currentQuadrant);

    bettingCountdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        int countdown = bettingTimeSeconds;
        
        @Override
        public void run() {
            if (countdown > 0) {
                for (BettingTable bettingTable : Tables.values()) {
                    bettingTable.updateCountdown(countdown, betsClosed);
                }

                if(countdown==5){
                    mce.playSong("Master", RouletteSongs.getDynamicFastTick(), false, "DynamicFastTick");

                }
                if (countdown < bettingTimeSeconds&&countdown>5) { // Avoid double-playing on first tick
                    mce.playSong("Master", RouletteSongs.getTimerTick(), false, "TimerTick");
                }
                // Update the timer item in the appropriate slot based on the current quadrant
                int countdownSlot = getCountdownSlotForQuadrant(currentQuadrant);
                renderLocalizedToAllInventories(countdownSlot, Material.CLOCK, countdown, "roulette.bets-close-in", "seconds", countdown);

                countdown--;
                globalCountdown=countdown;
                   switch (currentQuadrant) {
        case 1: // Top-right quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 46);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 47);
            renderLocalizedToAllInventories(46, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(47, Material.SPRUCE_DOOR, 1, "roulette.refund-exit");
            break;
        case 2: // Top-left quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
            renderLocalizedToAllInventories(52, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(53, Material.SPRUCE_DOOR, 1, "roulette.refund-exit");
            break;
        case 3: // Bottom-left quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 5);
           // addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 6);
            renderLocalizedToAllInventories(7, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(8, Material.SPRUCE_DOOR, 1, "roulette.refund-exit");
            break;
        case 4: // Bottom-right quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 1);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 2);
            renderLocalizedToAllInventories(1, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(2, Material.SPRUCE_DOOR, 1, "roulette.refund-exit");
            break;
    }
            } else if (countdown == 0) {
                globalCountdown=countdown;
                betsClosed = true;

                for (BettingTable bettingTable : Tables.values()) {
                    bettingTable.updateCountdown(countdown, betsClosed);
                }

                // Clock drawn before decoratives repaint over it, not after:
                // the countdown slot for every quadrant falls inside that
                // quadrant's own decorative pane range, so this leaves the
                // legacy inventory's actual final slot content as the
                // decorative pane, clock invisible again -- which matters
                // now that bootstrapView (see below) clones straight from
                // this inventory instead of independently re-synthesizing a
                // clock item of its own. Drawing the clock last would make
                // it the legacy inventory's permanent slot content, visible
                // and stuck for every viewer (not just one returning from
                // the betting table) until an unrelated later quadrant
                // switch happened to paint over the same slot.
                int countdownSlot = getCountdownSlotForQuadrant(currentQuadrant);
                renderLocalizedToAllInventories(countdownSlot, Material.CLOCK, 1, "roulette.bets-closed");

                clearMenuButtonsForQuadrant(currentQuadrant);
                initializeDecorativeSlotsForQuadrant(currentQuadrant);

                handleBetClosure();
                Bukkit.getScheduler().cancelTask(bettingCountdownTaskId);
                bettingCountdownTaskId = -1;
            }
        }
    }, 0L, 20L);
}



private void startSlowSpinAnimation(long initialSpeed) {

//////////////////////////////////////
    frameCounter = 0;
    int spinDirection = wheelSpinDirection; // Set counter-clockwise

    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }
    if (fastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(fastSpinTaskId);
    }
    if (bfastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
    }
    spinTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        @Override
        public void run() {
            if (betsClosed) {
                Bukkit.getScheduler().cancelTask(spinTaskId); // Stop slow spin when bets are closed
            } else {
                mce.playSong("RouletteWheel", RouletteSongs.getSlowSpinTick(), false, "Basd");
                updateWheelView(frameCounter * spinDirection); // Adjust direction here
                frameCounter++; // Increment frameCounter for the next position
            }
        }
    }, 0L, initialSpeed); // Initial slow speed
}

private void startSpinAnimation(List<Player> activePlayers) {

    if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }

    frameCounter = 0;
    boolean reverseDirection = (frameCounter + 1) % 2 == 0;
    int spinDirection = reverseDirection ? -1 : 1;

    final int totalSpinFrames = 600;
    final long initialWheelSpeed = 1L;
    final long minWheelSpeed = 6L; // Minimum delay (6L) once reached
    final int spinAccelerationFrames = 20;
    final int spinDecelerationFrames = 200;
    long[] currentWheelDelay = {initialWheelSpeed};

    Runnable spinTask = new Runnable() {
        @Override
        public void run() {
            if (!spinAnimationOver) {
                mce.playSong("RouletteWheel", RouletteSongs.getSpinTick(), false, "otherig");
                updateWheel(frameCounter * spinDirection);
    
                if (frameCounter < spinAccelerationFrames) {
                    // Acceleration phase
                    double accelerationProgress = (double) frameCounter / spinAccelerationFrames;
                    currentWheelDelay[0] = (long) Math.max(0.75, initialWheelSpeed - (accelerationProgress * 0.5));
                } else if (frameCounter >= totalSpinFrames - spinDecelerationFrames) {
                    // Deceleration phase: limit delay to minimum of 6L once reached
                    int framesSinceDecelerationStart = frameCounter - (totalSpinFrames - spinDecelerationFrames);
                    double decelerationProgress = Math.pow((double) framesSinceDecelerationStart / spinDecelerationFrames, 2);
                    currentWheelDelay[0] = Math.min(6L, 1L + (long) (decelerationProgress * (minWheelSpeed - 1L)));
                }
                bfastSpinTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this, currentWheelDelay[0]);
                frameCounter++;
            }
        }
    };
    
    fastSpinTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, spinTask, 0L);
    
}

private void startBallMovement(boolean reverseDirection) {
    if (ballMovementStarted) return;
    ballMovementStarted = true;

    wheelSpinDirection *= -1; // Reverse the wheel spin direction
    int ballSpinDirection = wheelSpinDirection; // Ball spins opposite to the wheel

    // Initialize ball movement variables
    ballCurrentTrack = trackSequence[0];
    ballCurrentIndex = 0; // Start at the first position in track
    ballPreviousSlot = -1;
    trackSequenceIndex = 0;

    Random random = new Random();

    // Generate random slots to move per track within specified ranges
    slotsToMovePerTrack[0] = minSlotsTrack1 + random.nextInt(maxSlotsTrack1 - minSlotsTrack1 + 1);
    slotsToMovePerTrack[1] = minSlotsTrack2 + random.nextInt(maxSlotsTrack2 - minSlotsTrack2 + 1);
    slotsToMovePerTrack[2] = minSlotsTrack3 + random.nextInt(maxSlotsTrack3 - minSlotsTrack3 + 1);
    slotsToMovePerTrack[3] = minSlotsTrack4 + random.nextInt(maxSlotsTrack4 - minSlotsTrack4 + 1);
    slotsToMovePerTrack[4] = 0; // Final track 3, stays there

    // Total slots to move
    int totalSlotsToMove = slotsToMovePerTrack[0] + slotsToMovePerTrack[1] + slotsToMovePerTrack[2] + slotsToMovePerTrack[3];

    final int ballAccelerationSlots = totalSlotsToMove / 4;
    final int ballDecelerationSlots = totalSlotsToMove / 2;
    long[] currentBallDelay = {INITIAL_BALL_SPEED};
    
    int[] slotsMovedTotal = {0}; // Wrap in array to allow modification

    // Start moving the ball
    moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);

}

private void moveBall(int ballSpinDirection, long[] currentBallDelay, int[] slotsMovedTotal, int totalSlotsToMove, int ballAccelerationSlots, int ballDecelerationSlots) {

    if (!ballMovementStarted) return;

    if (isSwitchingQuadrant) {
        miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
        }, currentBallDelay[0]);
        activeTaskIds.add(miscTaskId); // Store the task ID
        return;
    }

    long[] tempBallDelay = {1L};    
     Map<Integer, List<Integer>> currentTracks = getTracksForCurrentQuadrant();
    List<Integer> currentTrackSlots = currentTracks.get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) return;

    int nextIndex = (ballCurrentIndex + ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int nextSlot = currentTrackSlots.get(nextIndex);
    int tempSTMPTb=slotsToMovePerTrack[trackSequenceIndex]-1;
    if (tempSTMPTb <= 0) {
        int tempTSIb=trackSequenceIndex+1;
        if (tempTSIb < trackSequence.length) {
        // lookaheadby1 is not final number
        }
        else{
            if(eightflag&&nextIndex==8){
                nextflag=true;

            }
            if(nextIndex==7){
                sevflag=true;

            }
        }

    }

    int lookaheadSlots = 2; // Fixed number of slots to look ahead
    int futureIndex = (ballCurrentIndex + lookaheadSlots * ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int futureSlot = currentTrackSlots.get(futureIndex);
    int tempSTMPT=slotsToMovePerTrack[trackSequenceIndex]-lookaheadSlots;
    if (tempSTMPT == -1) {
        int tempTSI=trackSequenceIndex+lookaheadSlots;
        if (tempTSI < trackSequence.length) {
        // lookaheadslots is not final number
        }
        else{
              // lookaheadslots is !! final number  
            if (isQuadrantBoundary(futureSlot)&&futureIndex==0&&!(ballCurrentIndex==2&&ballCurrentTrack==3)){
                eightflag=true;
            }

        }
    }
    int soundLookaheadSlots = Math.max(4, 7 - (slotsMovedTotal[0] / 15)); 
    int soundFutureIndex = (ballCurrentIndex + soundLookaheadSlots * ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
    int soundFutureSlot = currentTrackSlots.get(soundFutureIndex);

   ///change below to improve whoosh sound delays. redo with a different lookaheadSlots
    if (isQuadrantBoundary(soundFutureSlot)) {
        int pitch = Math.max(3, 10 - (slotsMovedTotal[0] / 10)); 
        mce.stopSong("RouletteWheel", "BallScraping");
        mce.playSong("RouletteWheel", RouletteSongs.getBallScraping(pitch), false, "BallScraping");
        mce.stopSong("RouletteWheel", "Skibidi");
       mce.playSong("RouletteWheel", RouletteSongs.getSkibidi(pitch), false, "Skibidi");
        mce.stopSong("RouletteWheel", "scas");
       mce.playSong("RouletteWheel",RouletteSongs.getWhoosh(pitch),false, "scas");
    } 
    if (isQuadrantBoundary(nextSlot)&&!eightflag&&!sevflag&&trackSequenceIndex!=2) { 
        isSwitchingQuadrant = true;

        // Show ball in the final slot of the current track
        updateBallPosition(ballSpinDirection);

        // Schedule delay for ball to disappear, then switch quadrant view
        miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            // Remove ball from current slot
            restoreSlotInAllInventories(ballPreviousSlot);
            int nextquadnow=0;
            if (wheelSpinDirection == -1) { // Clockwise
                nextquadnow = (currentQuadrant % 4) + 1; // Move to next quadrant in order
            } else { // Counterclockwise
                nextquadnow = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
            }
            switch (currentQuadrant) {
                case 1:
                    if(nextquadnow==4){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 2:
                if(nextquadnow==3){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 3:
                if(nextquadnow==2){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                case 4:
                if(nextquadnow==1){
                    tempBallDelay[0]=6L;    
                    }
                    else{
                    tempBallDelay[0]=2L;    
                    }
                    break;
                default:
                    break;
            }
    
            // Delay to simulate ball going off-screen before quadrant switch
             miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                switchQuadrant();
               
                // Delay to simulate ball still off-screen after quadrant switch
                miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    ballCurrentIndex = getStartingIndexForNewQuadrant();
                    updateBallPosition(ballSpinDirection);
                   
                    isSwitchingQuadrant = false;
                    moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
                }, tempBallDelay[0]); // Delay after switching quadrants
                
                activeTaskIds.add(miscTaskId); 
            }, tempBallDelay[0]); // Delay before switching quadrants
           
            activeTaskIds.add(miscTaskId); 
        }, 3L); // Delay before ball disappears
        
        activeTaskIds.add(miscTaskId); 
        return;
    }

    // Normal ball movement if not at boundary
    if(!nextflag&&!sevflag){
        updateBallPosition(ballSpinDirection);
        slotsMovedTotal[0]++;
        adjustBallSpeed(currentBallDelay, slotsMovedTotal[0], ballAccelerationSlots, totalSlotsToMove, ballDecelerationSlots);
        slotsToMovePerTrack[trackSequenceIndex]--;
    }
   else{
    if(sevflag){
        updateBallPosition(ballSpinDirection);
        slotsMovedTotal[0]++;
        slotsToMovePerTrack[trackSequenceIndex]--;
        ballMovementStarted = false;
        handleWinningNumber();
        sevflag=false;
        eightflag=false;
        nextflag=false;
        return;
    }
    updateBallPosition(0);
    slotsMovedTotal[0]++;
    slotsToMovePerTrack[trackSequenceIndex]--;
    ballMovementStarted = false;
    handleWinningNumber();
    sevflag=false;
    eightflag=false;
    nextflag=false;
    return;
   }
    if (slotsToMovePerTrack[trackSequenceIndex] <= 0) {
        trackSequenceIndex++;
        if (trackSequenceIndex < trackSequence.length) {
            ballCurrentTrack = trackSequence[trackSequenceIndex];

            int adjustment=1;
    ballCurrentIndex = (ballCurrentIndex + adjustment * wheelSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();
        } else {
            ballMovementStarted = false;
            handleWinningNumber();
            sevflag=false;
            eightflag=false;
            nextflag=false;
            return;
        }
    }

     miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
        moveBall(ballSpinDirection, currentBallDelay, slotsMovedTotal, totalSlotsToMove, ballAccelerationSlots, ballDecelerationSlots);
    }, currentBallDelay[0]);
    activeTaskIds.add(miscTaskId); 
}

private void adjustBallSpeed(long[] currentBallDelay, int slotsMoved, int ballAccelerationSlots, int totalSlots, int ballDecelerationSlots) {
    if (slotsMoved < ballAccelerationSlots) {
        double accelerationProgress = (double) slotsMoved / ballAccelerationSlots;
        currentBallDelay[0] = (long) Math.max(1L, INITIAL_BALL_SPEED - (accelerationProgress * 0.5));
    } else if (slotsMoved >= totalSlots - ballDecelerationSlots) {
        int slotsSinceDecelerationStart = slotsMoved - (totalSlots - ballDecelerationSlots);
        double decelerationProgress = Math.pow((double) slotsSinceDecelerationStart / ballDecelerationSlots, 2);
        currentBallDelay[0] = Math.min(6L, 1L + (long) (decelerationProgress * (MIN_BALL_SPEED - 1L)));
    }
}



private void updateBallPosition(int ballSpinDirection) {
    Map<Integer, List<Integer>> currentTracks = getTracksForCurrentQuadrant();
    List<Integer> currentTrackSlots = currentTracks.get(ballCurrentTrack);

    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return; // No slots in this track
    }
    
    // Restore the item in the previous slot
    restoreSlotInAllInventories(ballPreviousSlot);

    // Move the ball index according to the spin direction
    ballCurrentIndex = (ballCurrentIndex + ballSpinDirection + currentTrackSlots.size()) % currentTrackSlots.size();

    int nextSlot = currentTrackSlots.get(ballCurrentIndex);
    //System.out.println("New ball position: slot " + nextSlot +"ballprevi:"+ballPreviousSlot+"track: "+trackSequenceIndex);

    // Store the current item in the slot, then set the ball item
    placeBallInAllInventories(nextSlot);

    // Update previous slot
    ballPreviousSlot = nextSlot;
}



private int getStartingIndexForNewQuadrant() {
    List<Integer> currentTrackSlots = getTracksForCurrentQuadrant().get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return 0;
    }

    // Determine the correct starting index based on the ball's spin direction
    if (ballSpinDirection == -1) { // Counterclockwise
        return currentTrackSlots.size() - 1;
    } else { // Clockwise
        return 0;
    }
}
private int getStartingIndexForNewQuaadrant() {
    List<Integer> currentTrackSlots = getTracksForCurrentQuadrant().get(ballCurrentTrack);
    if (currentTrackSlots == null || currentTrackSlots.isEmpty()) {
        return 0;
    }
    // Determine the correct starting index based on the ball's spin direction
    if (wheelSpinDirection == -1&&currentQuadrant==4) { // Counterclockwise
        return 0;
    } else if(wheelSpinDirection == -1) {
        return currentTrackSlots.size() - 1;}
        else{ // Clockwise
        return 0;}
    }

private void switchQuadrant() {
    if (wheelSpinDirection == -1) { // Clockwise
        currentQuadrant = (currentQuadrant % 4) + 1; // Move to next quadrant in order
    } else { // Counterclockwise
        currentQuadrant = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
    }

    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    ballCurrentIndex = getStartingIndexForNewQuadrant();
}

private void switchQauadrant() {
  
    if (wheelSpinDirection == 1) { // Clockwise
        currentQuadrant = (currentQuadrant % 4) + 1; // Move to next quadrant in order
    } else { // Counterclockwise
        currentQuadrant = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
        if(currentQuadrant==2){
        flip4=true;
        if(flip2){
            frameCounter+=7;  
          flip2=false;
          }
          else{
              flip2=true;
          }
        }
        if(currentQuadrant==4){
            flip2=true;
            if(flip4){
          frameCounter-=9;  
        flip4=false;
        }
        else{
            flip4=true;
        }
        }
    }
    initializeDecorativeSlotsForQuadrant(currentQuadrant);
    ballCurrentIndex = getStartingIndexForNewQuaadrant();
}

private boolean isQuadrantBoundary(int slot) {
    // Retrieve current track configuration for the quadrant
    Map<Integer, List<Integer>> trackMap = getTracksForCurrentQuadrant();
    List<Integer> trackSlots = trackMap.get(ballCurrentTrack);

    // Ensure the track slots are defined and the slot exists within the current track
    if (trackSlots == null || !trackSlots.contains(slot)) {
        return false; // Not a boundary if it's not part of the track
    }

    // Define boundaries based on slot positions for each quadrant and direction
    int firstSlot = trackSlots.get(0);
    int lastSlot = trackSlots.get(trackSlots.size() - 1);

    switch (currentQuadrant) {
        case 1: // Top-Right Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) ||  // clockwise to Quadrant 2
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 4
        case 2: // Top-Left Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 3
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 1
        case 3: // Bottom-Left Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 4
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 2
        case 4: // Bottom-Right Quadrant
            return (wheelSpinDirection == 1 && slot == lastSlot) || // clockwise to Quadrant 1
                   (wheelSpinDirection == -1 && slot == firstSlot); // counterclockwise to Quadrant 3
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}

private void handleWinningNumber() {
    mce.stopSong("RouletteWheel", "BallScraping");
    mce.stopSong("RouletteWheel", "Skibidi");
    mce.stopSong("RouletteWheel", "skibidi");
    // Get the number corresponding to the final slot
    winningNumber = getNumberForSlot(ballPreviousSlot, currentQuadrant);
    finalpicked = true;

    mce.playSong("RouletteWheel", RouletteSongs.getFinalSpot(), false, "Final spot");
    // Resolve by UUID regardless of current online status. The bet already
    // rode the spin, so it is owed the real result either way.
    for (UUID playerId : new ArrayList<>(playersWithBets)) {
        BettingTable bettingTable = Tables.get(playerId);
        Stack<Pair<String, Integer>> playerBets = newtry.get(playerId);
        if (bettingTable == null) {
            plugin.getLogger().warning("No betting table found for player: " + playerId);
            continue;
        }
        if (playerBets == null || playerBets.isEmpty()) {
            plugin.getLogger().warning(playerId + " has no bets to process.");
            continue;
        }
        // The final slot is authoritative now. Commit money before the
        // delayed presentation so shutdown cannot refund a resolved spin.
        bettingTable.processSpinResult(winningNumber, playerBets);

        miscTask=Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // Notify the player of the winning number
                if (isRed(winningNumber)) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage(text(player, "roulette.hit-red", "number", winningNumber));
                            break;}
                        case VERBOSE:{
                            player.sendMessage(text(player, "roulette.hit-red", "number", winningNumber));
                            break;
                        }
                            case NONE:{
                            break;
                        }
                    }
                } else if (isBlack(winningNumber)) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage(text(player, "roulette.hit-black", "number", winningNumber));
                            break;}
                        case VERBOSE:{
                            player.sendMessage(text(player, "roulette.hit-black", "number", winningNumber));
                            break;
                        }
                            case NONE:{
                            break;
                        }
                    }
                } else {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
                        case STANDARD:{
                            player.sendMessage(text(player, "roulette.hit-green", "number", winningNumber));
                            break;}
                        case VERBOSE:{
                            player.sendMessage(text(player, "roulette.hit-green", "number", winningNumber));
                            break;
                        }
                            case NONE:{
                            break;
                        }
                    }
                }
            }

        }, 30L);

        activeTaskIds.add(miscTask.getTaskId());
    }

    // Reset for the next round
    miscTask=Bukkit.getScheduler().runTaskLater(plugin, this::prepareNextRound, 75L);
    activeTaskIds.add(miscTask.getTaskId()); 

}

private void prepareNextRound() {

    betsClosed = false;
    for (BettingTable bettingTable : Tables.values()) {
        bettingTable.resetTable();
    }

    // Remove the ball from the slot if present
    restoreSlotInAllInventories(ballPreviousSlot);

    // Reset movement and state variables
    ballMovementStarted = false;
    spinAnimationOver = false;
    finalpicked = false;
    trackSequenceIndex = 0;
    ballCurrentTrack = 0;
    ballCurrentIndex = 0;
    ballPreviousSlot = -1;
    //countdown=bettingTimeSeconds;
    //globalCountdown=countdown

    // Optionally reverse the wheel direction to add variety
    wheelSpinDirection *= -1;
  if (spinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(spinTaskId);
    }
    if (regTaskId != -1) {
        Bukkit.getScheduler().cancelTask(regTaskId);
    }
    if (reg2TaskId != -1) {
        Bukkit.getScheduler().cancelTask(reg2TaskId);
    }
    //wheelOffset = currentOffset;

    if (fastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(fastSpinTaskId);
    }
    if (bfastSpinTaskId != -1) {
        Bukkit.getScheduler().cancelTask(bfastSpinTaskId);
    }
    wheelOffset = lastDisplayedOffset;
    // Start the betting timer again, which also resets bets and shows menu buttons
    startBettingTimer();

}


private boolean isBlack(int result) {
    return RouletteWheelLayout.isBlack(result);
}

private int getNumberForSlot(int mainSlot, int quadrant) {
    Integer number = slotToNumber.get(mainSlot);
    if (number != null) {
        return number;
    } else {
        //plugin.getLogger().warning("Error: No number associated with slot " + mainSlot + " in quadrant " + quadrant);
        return 0;
    }
}  
   

private Map<Integer, List<Integer>> getTracksForCurrentQuadrant() {
    switch (currentQuadrant) {
        case 1: // Top-Right
            return (ballSpinDirection == -1) ? tracksTopRight : reverseTrack(tracksTopRight);
        case 2: // Top-Left
            return (ballSpinDirection == 1) ? reverseTrack(tracksTopLeft) : tracksTopLeft;
        case 3: // Bottom-Left
            return (ballSpinDirection == -1) ? tracksBottomLeft : reverseTrack(tracksBottomLeft);
        case 4: // Bottom-Right
            return (ballSpinDirection == 1) ?  reverseTrack(tracksBottomRight) :tracksBottomRight;
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}


private Map<Integer, List<Integer>> reverseTrack(Map<Integer, List<Integer>> tracks) {
    Map<Integer, List<Integer>> reversedTracks = new HashMap<>();

    for (Map.Entry<Integer, List<Integer>> entry : tracks.entrySet()) {
        List<Integer> reversedList = new ArrayList<>(entry.getValue());
        Collections.reverse(reversedList);
        reversedTracks.put(entry.getKey(), reversedList);
    }

    return reversedTracks;
}


private void updateWheelView(long frameOffset) {
    int currentOffset = Math.floorMod(wheelOffset - (int) frameOffset, wheelLayout.size());
    updateQuadrantDisplay(currentOffset);
    lastDisplayedOffset = currentOffset;
}

    private void updateWheel(int frame) {
        int currentOffset = Math.floorMod(wheelOffset - frame, wheelLayout.size());
        updateQuadrantDisplay(currentOffset);
        lastDisplayedOffset = currentOffset;
    }
    
// Determine the correct slot for the countdown based on the quadrant
private int getCountdownSlotForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            return 45;
        case 2: // Top-left quadrant
            return 51;  // You can change this to match your design
        case 3: // Bottom-left quadrant
            return 6;
        case 4: // Bottom-right quadrant
            return 0;
        default:
            return 45;  // Default to top-right
    }
}

// Update the menu buttons for the given quadrant
private void updateMenuButtonsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 46);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 47);
            renderLocalizedToAllInventories(46, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(47, Material.SPRUCE_DOOR, 1, "roulette.exit-refund");
            break;
        case 2: // Top-left quadrant
            //addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 50);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 51);
            renderLocalizedToAllInventories(52, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(53, Material.SPRUCE_DOOR, 1, "roulette.exit-refund");
            break;
        case 3: // Bottom-left quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 5);
           // addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 6);
            renderLocalizedToAllInventories(7, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(8, Material.SPRUCE_DOOR, 1, "roulette.exit-refund");
            break;
        case 4: // Bottom-right quadrant
           // addItem(createCustomItem(Material.CLOCK, "-1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 1);
            //addItem(createCustomItem(Material.CLOCK, "+1 Betting Timer (Will take effect next round)", bettingTimeSeconds), 2);
            renderLocalizedToAllInventories(1, Material.BOOK, 1, "roulette.open-table");
            renderLocalizedToAllInventories(2, Material.SPRUCE_DOOR, 1, "roulette.exit-refund");
            break;
    }
}

// Remove the menu buttons when bets are closed
private void clearMenuButtonsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1: // Top-right quadrant
            clearSlots(45, 49);
            break;
        case 2: // Top-left quadrant
            clearSlots(48, 52);
            break;
        case 3: // Bottom-left quadrant
            clearSlots(4, 8);
            break;
        case 4: // Bottom-right quadrant
            clearSlots(0, 4);
            break;
    }
}

    // Clear a range of slots
private void clearSlots(int fromSlot, int toSlot) {
    for (int i = fromSlot; i <= toSlot; i++) {
        inventory.setItem(i, null);  // Set the slot to null to clear it
    }
}



private Map<Integer, Integer> slotToNumber = new HashMap<>();


private void switchStayToQuadrant(int quad){
    currentQuadrant=quad;
    //inventory.clear();
    initializeDecorativeSlotsForQuadrant(currentQuadrant);
}

    private void updateQuadrantDisplay(int globalOffset) {
        // Snapshotted once, up front: this is the quadrant whose slot/extra
        // layout the whole call renders into, matching the original inline
        // implementation this was extracted from.
        int slotQuadrant = currentQuadrant;
        int[] quadrantSlots = RouletteWheelLayout.mainSlotsForQuadrant(slotQuadrant);
        Map<Integer, int[]> currentExtraSlotsMap = RouletteWheelLayout.extraSlotsForQuadrant(slotQuadrant);
        Map<Integer, Integer> quadrantNumbers = RouletteWheelLayout.numbersForQuadrant(slotQuadrant, globalOffset);
        slotToNumber.clear();
        boolean newflag=false;


        if(finalpicked&&!foundfirstquadrant){
            for (int i = 0; i < quadrantSlots.length; i++) {
                int number = quadrantNumbers.get(quadrantSlots[i]);
                if(number==winningNumber){newflag=true;}

        }
        if(!newflag){
            int targetquad;
            targetquad=findWinningNumberQuadrant(winningNumber,globalOffset);
            if(currentQuadrant!=targetquad){
        switchStayToQuadrant(targetquad);}
            }
        }

        // Legacy quirk, preserved intentionally: the render loop below still
        // walks the slots/extras captured above for slotQuadrant, but the
        // original code re-read the wheel-walk direction (ascending for
        // quadrants 1/2, descending for 3/4) from currentQuadrant at render
        // time -- which switchStayToQuadrant above may have just changed.
        // Re-deriving quadrantNumbers with slotQuadrant for slots and the
        // (possibly now different) currentQuadrant for direction reproduces
        // that exactly, rather than "fixing" it into fully self-consistent
        // numbers for one quadrant, which would change same-tick
        // final-landing timing on the fragile deceleration animation.
        quadrantNumbers = RouletteWheelLayout.numbersForQuadrant(slotQuadrant, globalOffset, currentQuadrant);

        // Loop through each slot in the quadrant and assign the correct number
        for (int i = 0; i < quadrantSlots.length; i++) {
            int number = quadrantNumbers.get(quadrantSlots[i]);

            // Create the item with the correct number and place it in the quadrant slot
            ItemStack item = createCustomItem(getMaterialForNumber(number),  ""+number, (number == 0) ? 1 : number);
            renderToAllInventories(quadrantSlots[i], item);

            // Handle the extra slots associated with the main number slot
            if (currentExtraSlotsMap.containsKey(quadrantSlots[i])) {
               
                int[] extraSlots = currentExtraSlotsMap.get(quadrantSlots[i]);
                ItemStack extraItem = createCustomItem(getMaterialForNumber(number), ""+number, 1);
                boolean first=true;
                for (int extraSlot : extraSlots) {
                    
                    slotToNumber.put(extraSlot, number);

                    if(finalpicked&& number==winningNumber&&extraSlot==extraSlots[0]&&first){
                        foundfirstquadrant=true;
                        long[] tempBallDelay ={1L};
                        if(isQuadrantBBoundary(extraSlot)){

                        renderBallToAllInventories(extraSlot);
                        regTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                // Remove ball from current slot
                                restoreSlotInAllInventories(ballPreviousSlot);
                                int nextquadnow=0;
                                if (wheelSpinDirection == -1) { // Clockwise
                                    nextquadnow = (currentQuadrant % 4) + 1; // Move to next quadrant in order
                                } else { // Counterclockwise
                                    nextquadnow = (currentQuadrant == 1) ? 4 : currentQuadrant - 1; // Move to previous quadrant
                                }
                                switch (currentQuadrant) {
                                    case 1:
                                        if(nextquadnow==4){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 2:
                                    if(nextquadnow==3){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 3:
                                    if(nextquadnow==2){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    case 4:
                                    if(nextquadnow==1){
                                        tempBallDelay[0]=6L;    
                                        }
                                        else{
                                        tempBallDelay[0]=2L;    
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            
                                // Delay to simulate ball going off-screen before quadrant switch
                                reg2TaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {

                                    switchQauadrant();

                                    // Delay to simulate ball still off-screen after quadrant switch
                                    miscTaskId=Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                       //?something goes here?
                                    }, 2L); // Delay after switching quadrants
                                    activeTaskIds.add(miscTaskId); 

                                }, 2L); // Delay before switching quadrants
                            }, 3L); // Delay before ball disappears 
                            }
                            else{
                            renderBallToAllInventories(extraSlot);
                            }

                   
                    }
                    else{renderToAllInventories(extraSlot, extraItem);}
                }
            }
        }
    }
  
    private int findWinningNumberQuadrant(int winningNumber, int globalOffset) {
        return RouletteWheelLayout.findWinningNumberQuadrant(winningNumber, globalOffset);
    }
    
    
private boolean isQuadrantBBoundary(int slot) {
    switch (currentQuadrant) {
        case 1: // Top-Right Quadrant
            return (wheelSpinDirection == 1 && slot == 18) ||  // clockwise to Quadrant 2
                   (wheelSpinDirection == -1 && slot == 44); // counterclockwise to Quadrant 4
        case 2: // Top-Left Quadrant
            return (wheelSpinDirection == 1 && slot == 36) || // clockwise to Quadrant 3
                   (wheelSpinDirection == -1 && slot == 26); // counterclockwise to Quadrant 1
        case 3: // Bottom-Left Quadrant
            return (wheelSpinDirection == 1 && slot == 35) || // clockwise to Quadrant 4
                   (wheelSpinDirection == -1 && slot == 9); // counterclockwise to Quadrant 2
        case 4: // Bottom-Right Quadrant
            return (wheelSpinDirection == 1 && slot == 17) || // clockwise to Quadrant 1
                   (wheelSpinDirection == -1 && slot == 27); // counterclockwise to Quadrant 3
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + currentQuadrant);
    }
}

    
private void initializeDecorativeSlotsForQuadrant(int quadrant) {
    switch (quadrant) {
        case 1:
            // Quadrant 1: Brown slots (0-17, 26), Green slots (36-42, 45-52)
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 26}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 48, 49, 50, 51, 52}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 2:
            // Quadrant 2: Brown slots (0-18), Green slots (38-44, 46-53)
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50, 51, 52, 53}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 3:
            // Quadrant 3: Brown slots (27, 36-53), Green slots (1-8, 11-17)
            fillDecorativeSlots(new int[]{27, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 14, 15, 16, 17}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        case 4:
            // Quadrant 4: Brown slots (35-53), Green slots (0-7, 9-15)
            fillDecorativeSlots(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}, Material.BROWN_STAINED_GLASS_PANE);
            fillDecorativeSlots(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15}, Material.GREEN_STAINED_GLASS_PANE);
            break;
        default:
            throw new IllegalArgumentException("Invalid quadrant index: " + quadrant);
    }
}


private void fillDecorativeSlots(int[] slots, Material material) {
    for (int slot : slots) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r"); // Resets to vanilla name (no display)
            meta.setLore(null); // Ensure no lore
            item.setItemMeta(meta);
        }
        item.setItemMeta(meta);
        renderToAllInventories(slot, item);
    }
}

    private Material getMaterialForNumber(int number) {
        switch (RouletteWheelLayout.colorOf(number)) {
            case GREEN:
                return Material.LIME_STAINED_GLASS_PANE;
            case RED:
                return Material.RED_STAINED_GLASS_PANE;
            default:
                return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    private boolean isRed(int number) {
        return RouletteWheelLayout.isRed(number);
    }
    public void updatePlayerBets(UUID playerId, Stack<Pair<String, Integer>> bets, Player player) {
        if (bets == null) {
            bets = new Stack<>();
        }
        Bets.put(playerId, bets);
        if (!bets.isEmpty()) {
            // A committed, currency-withdrawn bet exists for this player —
            // make sure a kick can reach us to forfeit it even if they
            // aren't actively viewing any Roulette inventory right now.
            SessionRegistry.register(playerId, this);
        }
    }

    /**
     * Kicked players forfeit unconditionally, regardless of round phase —
     * no refund, no cash-out, no pending payout. Reached via
     * SessionRegistry from the central PlayerQuitEvent/PlayerKickEvent
     * handling; a plain disconnect intentionally does nothing here and
     * falls through to normal resolution (see handleBetClosure/
     * handleWinningNumber), since the spin resolves independently of
     * player presence. A plugin/server shutdown is different again: the
     * scheduled tasks that would normally carry the bet through to that
     * same resolution are about to be cancelled along with everything
     * else, so there's no "riding it out" — refund instead.
     */
    @Override
    public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
        Double pendingDeposit = pendingOnlineDeposits.get(terminatedPlayerId);
        boolean knownPayoutPending = pendingDeposit != null && pendingDeposit > 0;
        switch (GameTerminationPolicy.roulette(reason, knownPayoutPending)) {
        case FORFEIT:
            pendingOnlineDeposits.remove(terminatedPlayerId);
            forfeitBet(terminatedPlayerId);
            break;
        case QUEUE_KNOWN_PAYOUT:
            pendingOnlineDeposits.remove(terminatedPlayerId);
            queueKnownWinPayout(terminatedPlayerId, pendingDeposit, reason);
            finalizeRoundResolution(terminatedPlayerId);
            break;
        case REFUND:
            pendingOnlineDeposits.remove(terminatedPlayerId);
            refundForShutdown(terminatedPlayerId);
            break;
        case RIDE_TO_RESULT:
            // PlayerQuitEvent atomically claimed the old registration, but
            // this wager is intentionally still unresolved. Keep it
            // reachable so a later plugin shutdown can refund it.
            SessionRegistry.register(terminatedPlayerId, this);
            break;
        case NO_ACTION:
            break;
        default:
            throw new IllegalStateException("Unexpected Roulette termination action");
        }
    }

    /**
     * Called by BettingTable right after a spin resolves to a win for an
     * online player, before the deposit that pays it is scheduled. Marks
     * the exact amount owed so a shutdown landing before that deposit runs
     * queues the correct payout instead of losing it (the deposit task
     * itself is about to be cancelled along with everything else) or
     * falling back to refundForShutdown's stake-based amount, which would
     * short a winner down to just their wager back.
     */
    void markOnlineDepositPending(UUID playerId, double amount) {
        pendingOnlineDeposits.put(playerId, amount);
    }

    /**
     * Claims the delayed live deposit. If termination persisted it first,
     * the old scheduled task becomes a no-op rather than paying twice.
     */
    boolean claimOnlineDeposit(UUID playerId, double amount) {
        return pendingOnlineDeposits.remove(playerId, amount);
    }

    /**
     * Durably queues an already-resolved winning payout that a shutdown
     * interrupted before its scheduled deposit could run. Mirrors
     * refundForShutdown's persistence, but for a known payout amount
     * rather than a re-derived stake total.
     */
    private void queueKnownWinPayout(UUID playerId, double amount, ExitReason reason) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Roulette",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            reason == ExitReason.PLUGIN_DISABLE
                ? "The server restarted after your Roulette result was determined. Your payout was saved."
                : PayoutMessages.disconnectedMidGameContext("Roulette")
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Roulette shutdown win payout failed to persist for " + playerId + ".");
        }
    }

    /**
     * The server is shutting down with this player's bet still committed
     * to an in-flight round. Refunds the full wagered amount via the
     * durable pending-payout store (delivered as a normal chat message on
     * next join) rather than a live credit, since this player may not
     * even be online right now.
     */
    private void refundForShutdown(UUID playerId) {
        Stack<Pair<String, Integer>> bets = Bets.get(playerId);
        if (bets == null || bets.isEmpty()) {
            return;
        }

        double total = 0;
        for (Pair<String, Integer> bet : bets) {
            total += bet.getSecond();
        }
        if (total <= 0) {
            return;
        }

        BettingTable bt = Tables.get(playerId);
        if (bt != null) {
            // The stake is being returned, not paid as a win -- release the
            // portfolio reservation for exactly that amount, or it would sit
            // open forever with no round resolution left to close it.
            bt.releasePortfolioForExternalResolution((long) total);
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Roulette",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            total,
            PayoutMessages.serverRestartRefundContext("Roulette")
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Roulette shutdown refund failed to persist for " + playerId + ".");
        }
    }

    private void forfeitBet(UUID playerId) {
        Bets.remove(playerId);
        newtry.remove(playerId);
        playersWithBets.remove(playerId);

        BettingTable bt = Tables.remove(playerId);
        if (bt != null) {
            // A kick keeps the stake with the dealer -- release the
            // portfolio reservation with nothing paid, or it would sit open
            // forever with no round resolution left to close it.
            bt.releasePortfolioForExternalResolution(0);
            bt.cleanupListener();
        }
    }

    /**
     * Final cleanup once a player's bet for this round has been fully
     * resolved (paid directly or queued as a pending payout) — called from
     * BettingTable.processSpinResult. Avoids leaking the BettingTable's
     * registered listener and Tables entry indefinitely, which previously
     * only ever happened via the manual "Exit" button.
     */
    void finalizeRoundResolution(UUID playerId) {
        Bets.remove(playerId);
        newtry.remove(playerId);
        playersWithBets.remove(playerId);

        BettingTable bt = Tables.remove(playerId);
        if (bt != null) {
            bt.cleanupListener();
        }

        SessionRegistry.unregister(playerId, this);
    }

    private String text(String key, Object... placeholders) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < placeholders.length; index += 2) {
            values.put(String.valueOf(placeholders[index]), placeholders[index + 1]);
        }
        return plugin.getLocalization().text(
            plugin.getLocalization().getServerDefault(),
            key,
            values
        );
    }

    private String text(Player player, String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
}

