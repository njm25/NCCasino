package org.nc.nccasino.games.Slots;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.currency.VaultCurrencyProvider;
import org.nc.nccasino.currency.WagerTransaction;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.BankedCurrency;
import org.nc.nccasino.payout.ItemDeliveryOutcome;
import org.nc.nccasino.payout.OverflowBankService;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;
import org.nc.nccasino.session.TerminationAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.nc.nccasino.payout.WagerGate;
import org.nc.nccasino.budget.AdmissionDecision;
import org.nc.nccasino.budget.Commitment;
import org.nc.nccasino.budget.DealerBudgetService;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

/**
 * One player's independent Slots machine: a personal 54-slot view backed by
 * the explicit {@link SlotsSessionState} lifecycle.
 *
 * <p>The machine's width and visible height ({@link SlotsGeometry}) and its
 * active payline count are all player-selectable in session; the upper 5
 * inventory rows are a canvas centred around whichever geometry is currently
 * selected, and the bottom row (45-53) holds every control. Because the
 * configured house edge is applied by deriving the paytable
 * ({@link SlotsPaytable}) rather than by hardcoding multipliers, none of
 * width, height, or line count moves the machine's return -- see
 * {@link SlotsPaytable} and {@link SlotsPaylineCatalog} for why every shape
 * and every height shares one run-length distribution per column.
 *
 * <p>Financial contract: a debit ({@link WagerTransaction#tryWithdraw}) is
 * always attempted and confirmed <em>before</em> any outcome is generated.
 * The instant that debit succeeds, {@link SlotsSpinController} draws one
 * immutable {@link SlotsOutcome} (one stop per reel, from that reel's
 * {@link SlotsReelStrip}) and the payout owed is computed and stored --
 * everything from that point on (the reel motion, the win walk-through, the
 * eventual credit) is just carrying out an already-decided result. There is
 * no route back from a committed result to a refundable pregame state (see
 * {@link SlotsStateMachine}).
 *
 * <p>The same inventory has four canvas views ({@link SlotsUiView}):
 * {@code GAME} (the ordinary reel canvas), {@code PAYTABLE} (the condensed
 * symbol-card paytable plus its informational rail), {@code PROFILES} (the
 * player's globally saved configurations) and {@code AUTO_SETTINGS} (the
 * Auto Spin settings menu). Each modal view repaints only the upper 45-slot
 * canvas; the bottom control row stays exactly as it is in Game View except
 * for the single slot that view swaps in place for Back to Game -- 48, 50
 * and 53 respectively, per {@link SlotsUiView#backToGameSlot()}. Demo Spin
 * is not a separate mode: it is an isolated cosmetic animation launched from
 * the same central Spin lever (right-click), rendered directly onto the Game
 * canvas, that can never debit, credit, reserve against the dealer budget,
 * or affect the real spin lifecycle in any way.
 */
public class SlotsMachine extends DealerInventory implements TerminableSession {

    // ---- layout ------------------------------------------------------
    // Inventory rows 0-4 (slots 0-44): the reel/information canvas, centred
    // per SlotsGeometry's exact contract for the current width/height.
    // Inventory row 5 (slots 45-53): every control, in the exact left-to-right
    // order required by the control redesign.

    // Aliases of the single source of truth in SlotsControlLayout, which also
    // owns the click-routing matrix so it can be tested without a live server.

    private static final int EXIT_SLOT = SlotsControlLayout.EXIT_SLOT;
    /** Brown Stained Glass Pane -- the reel (column) count. */
    private static final int REELS_SLOT = SlotsControlLayout.REELS_SLOT;
    /** Pink Stained Glass Pane -- the visible height. */
    private static final int HEIGHT_SLOT = SlotsControlLayout.HEIGHT_SLOT;
    /** Book (open the Paytable) in Game View; Back to Game (Magenta Glazed Terracotta) in Paytable View. */
    private static final int PAYTABLE_SLOT = SlotsControlLayout.PAYTABLE_SLOT;
    /** The true centre of the 9-slot control row (45-53): the always-present central Spin lever. */
    private static final int SPIN_SLOT = SlotsControlLayout.SPIN_SLOT;
    /**
     * Auto Spin (left-click), Spin Speed (right-click) and Auto Spin Settings
     * (shift-right-click) -- all three combined on one Clock control. Becomes
     * Back to Game in the Auto Spin Settings view.
     */
    private static final int CLOCK_SLOT = SlotsControlLayout.CLOCK_SLOT;
    /** Green Stained Glass Pane -- the active payline count. */
    private static final int LINES_SLOT = SlotsControlLayout.LINES_SLOT;
    /** Black Stained Glass Pane -- the per-line wager. */
    private static final int WAGER_SLOT = SlotsControlLayout.WAGER_SLOT;
    /** Ender Chest -- this player's globally saved profiles. Becomes Back to Game in the Profiles view. */
    private static final int PROFILES_SLOT = SlotsControlLayout.PROFILES_SLOT;

    /** How long a demo's hypothetical result stays highlighted before the round finishes. */
    private static final long DEMO_FINALE_HOLD_TICKS = SlotsTiming.ALL_LINES_FINALE_TICKS;

    private final UUID playerId;
    private final Player player;
    private final Nccasino plugin;
    private final String internalName;
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private final SlotsInventory slotsInventory;
    private final double[] chipValues;

    private SlotsConfig config;
    private final SlotsSpinController controller = new SlotsSpinController();
    /**
     * Distinguishes this table's commitments from those of any earlier
     * session at the same dealer.
     *
     * <p>A reservation id must be stable across retries of one spin and
     * distinct across everything else. {@code generation} alone gives the
     * first but not the second: it restarts at zero when the plugin restarts,
     * so a crash that left spin 1 unsettled would let the next session's spin
     * 1 silently adopt that stale reservation instead of making its own. The
     * session nonce closes that, and the orphan is left for
     * {@code staleReservations} to report rather than being quietly reused.
     */
    private final String budgetSessionId = java.util.UUID.randomUUID().toString();
    private final SlotsUnderwriting underwriting = new DealerBudgetUnderwriting();
    private int denominationIndex = 0;
    private boolean closeFlag = false;

    private BukkitTask animationTask;
    private BukkitTask winMeterTask;
    private BukkitTask lineFlashTask;
    /**
     * Pure staleness guard for the Paylines blink flash -- see
     * {@link SlotsLineFlashGuard}. {@link #cancelLineFlashTask()} drives
     * {@link SlotsLineFlashGuard#cancel()} so a stale scheduled blink frame
     * from a superseded flash can never repaint over a newer Paylines input,
     * view change, geometry change, spin start, or a closed/torn-down
     * session -- the same guard pattern {@link #demoGeneration} and
     * {@link #openingGeneration} use.
     */
    private final SlotsLineFlashGuard lineFlashGuard = new SlotsLineFlashGuard();
    private BukkitTask demoTask;
    private BukkitTask openingAnimationTask;
    /** True only while the once-per-session opening animation is running -- every click is ignored. */
    private boolean openingActive = false;
    /**
     * Bumped by {@link #cancelOpeningAnimationTask()} so a stale scheduled
     * frame from a superseded or cancelled opening animation can never
     * repaint a closed or newer inventory session -- the same guard pattern
     * {@link #demoGeneration} uses for Demo Spin.
     */
    private long openingGeneration = 0;
    /** Cosmetic symbols currently shown per reel, top row first. Never authoritative. */
    private SlotsSymbol[][] reelDisplay;
    /** Each reel's current cosmetic position on its strip during a spin -- not the committed stop until landing. */
    private int[] reelScrollPosition;
    /** Whether {@link #reelDisplay}'s current contents came from a Demo Spin -- drives the idle repaint's demo lore. */
    private boolean lastGridIsDemo = false;
    /** The Spin lever's Last Result lore's count-up lifecycle -- see {@link SlotsWinMeterAnimation}. */
    private final SlotsWinMeterAnimation lastWinState = new SlotsWinMeterAnimation();
    /**
     * A demo spin's own independent randomness stream -- see
     * {@link SlotsDemoRandomSource}. Held once and reused for every demo
     * draw; never shared with {@link SlotsRandomSource#production()} and
     * never recreated per spin.
     */
    private final SlotsDemoRandomSource demoRng = new SlotsDemoRandomSource();

    private SlotsUiView uiView = SlotsUiView.GAME;
    /**
     * A demo spin's own generation counter. A running demo task checks this
     * (never the real controller's) so it can never observe or affect the
     * real spin lifecycle, and a stale demo callback can never repaint over
     * a newer demo or a return to Game View.
     */
    private long demoGeneration = 0;
    private boolean demoActive = false;
    /** The demo currently running/just finished -- retained only so a lever fast-forward can paint its own result. */
    private SlotsOutcome demoOutcome;
    private long demoHypotheticalBet;
    private long demoHypotheticalPayout;

    /** Session-local; always resets to {@link SlotsSpinSpeed#NORMAL} on a new session, and only ever loaded from a profile. */
    private SlotsSpinSpeed spinSpeed = SlotsSpinSpeed.NORMAL;
    private boolean autoSpinActive = false;
    private BukkitTask autoSpinTask;

    /**
     * The Auto Spin configuration this session is editing. A brand-new
     * session starts at {@link SlotsAutoSpinSettings#defaults()} -- spin
     * limit 15 with every stop condition off -- and loading a profile
     * replaces it wholesale.
     */
    private SlotsAutoSpinSettings autoSettings = SlotsAutoSpinSettings.defaults();

    /**
     * The current Auto Spin batch's ledger. Reset the instant Auto Spin
     * starts and never carried across batches, so one batch's profit target
     * or loss limit can never be judged against another batch's history.
     */
    private final SlotsAutoSpinBatch autoBatch = new SlotsAutoSpinBatch();

    /**
     * The settings snapshot the running batch was started with, so an edit
     * made after the fact cannot retroactively change a batch that is
     * already running.
     */
    private SlotsAutoSpinSettings activeBatchSettings = SlotsAutoSpinSettings.defaults();

    /** The exact total bet of the spin currently committed -- what the big-win threshold is measured against. */
    private long committedTotalBet = 0L;

    /**
     * Bumped every time a chat prompt is opened or abandoned. A prompt
     * callback that arrives carrying an older value belongs to a superseded
     * prompt and must do nothing -- the same guard pattern
     * {@link #demoGeneration} and {@link #openingGeneration} use for
     * scheduled animation frames.
     */
    private long promptGeneration = 0;

    /**
     * True only while this machine's inventory has been deliberately closed
     * for a chat prompt. It is what stops {@link #onInventoryClose} from
     * terminating a session that is merely suspended, and it is always
     * cleared by whichever path ends the prompt.
     */
    private boolean promptSuspended = false;

    /** The exact configuration a profile save captured when the player started naming it. */
    private SlotsProfile pendingProfileSnapshot;

    public SlotsMachine(UUID dealerId, Player player, Nccasino plugin, String internalName, SlotsInventory slotsInventory) {
        super(player.getUniqueId(), SlotsGeometry.INVENTORY_SIZE,
            plugin.getLocalization().text(player, "slots.title"));
        this.dealerId = dealerId;
        this.playerId = player.getUniqueId();
        this.player = player;
        this.plugin = plugin;
        this.internalName = internalName;
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.slotsInventory = slotsInventory;
        this.chipValues = loadChipValues();
        this.config = SlotsConfig.load(plugin, internalName);
        this.reelDisplay = neutralGrid(config.columns(), config.visibleRows());
        this.reelScrollPosition = new int[config.columns()];

        Bukkit.getPluginManager().registerEvents(this, plugin);
        SessionRegistry.register(playerId, this);
    }

    private double[] loadChipValues() {
        double[] values = new double[5];
        for (int i = 0; i < 5; i++) {
            values[i] = plugin.getChipValue(internalName, i + 1);
        }
        return values;
    }

    public void initializeTable() {
        beginOpeningAnimation();
    }

    private void redrawEverything() {
        repaintCanvas();
        renderControls();
    }

    /**
     * The single authoritative canvas-transition path: every one of the 45
     * canvas slots (0-44) is always repainted -- {@link #renderFrame} first
     * (frame/gutter for the destination geometry, a no-op for the Paytable
     * view, which paints every slot itself), then {@link #renderCanvas} for
     * the destination view. Every view switch (Game/Paytable), every
     * geometry change, and every "return to X" path must go through this
     * method rather than calling {@link #renderCanvas} alone, or a
     * transition from a view that owns cells outside the current grid (the
     * Paytable view owns all 45) can leave its items stranded in the
     * destination view's gutters. The bottom control row (45-53) is never
     * touched here.
     */
    private void repaintCanvas() {
        renderFrame();
        renderCanvas();
    }

    // ---- opening animation -------------------------------------------

    /**
     * Runs exactly once, the first time this machine's inventory is ever
     * shown to the player -- {@link SlotsInventory} always constructs a
     * fresh {@link SlotsMachine} and calls {@link #initializeTable} exactly
     * once, before {@code openInventory}. Every later repaint (geometry
     * change, view switch, spin) goes through {@link #redrawEverything}
     * directly and never touches this method again, which is what stops the
     * animation from ever replaying.
     *
     * <p>The authoritative final frame is rendered for real first, then
     * captured and the inventory cleared back to visually empty -- so the
     * animation always falls the machine's actual real items (rainbow
     * housing, white reel bay, and every control) into place rather than a
     * hand-maintained duplicate of what the final layout should look like.
     * That is also what makes the control row's order safe to change: the
     * animation's targets are captured live from whatever
     * {@link #renderControls()} just painted, so reordering the controls
     * needs no change here at all.
     */
    private void beginOpeningAnimation() {
        redrawEverything();
        int size = SlotsGeometry.INVENTORY_SIZE;
        ItemStack[] finalItems = new ItemStack[size];
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = getInventory().getItem(slot);
            finalItems[slot] = item == null ? null : item.clone();
        }
        for (int slot = 0; slot < size; slot++) {
            getInventory().clear(slot);
        }
        startOpeningAnimation(finalItems);
    }

    /**
     * Drives the nine-column falling-panes intro. Each of the 9 inventory
     * columns is treated as an independent {@link SlotsOpeningColumnMotion#ROWS}-cell
     * reel: the fixed {@link SlotsOpeningFiller#fixedRainbowSequence()}
     * cosmetic sequence, then the column's six real final items fed in
     * bottom-to-top order -- twice, with the same fixed rainbow in between
     * (see {@link SlotsOpeningColumnMotion#buildEntrySequenceWithSettle}), so
     * the correct items visibly pass through once before the second pass
     * actually lands and stays, rather than the column insta-stopping the
     * instant it first reads correctly. The completed column reads
     * top-to-bottom exactly as {@code finalItems} already has it. Columns
     * start on a left-to-right stagger ({@link SlotsTiming#OPENING_COLUMN_STAGGER_TICKS})
     * and overlap in time rather than running one after another.
     *
     * <p>Guarded by {@link #openingGeneration} exactly like {@link #demoGeneration}
     * guards Demo Spin: a stale scheduled frame from a cancelled or
     * superseded animation can never repaint a closed or newer session.
     */
    private void startOpeningAnimation(ItemStack[] finalItems) {
        openingActive = true;
        openingGeneration++;
        final long myGeneration = openingGeneration;

        final int columnCount = SlotsGeometry.INVENTORY_WIDTH;
        final List<List<ItemStack>> entries = new ArrayList<>(columnCount);
        for (int col = 0; col < columnCount; col++) {
            List<ItemStack> firstBurst = fixedRainbowFillerItems();
            List<ItemStack> finalColumn = new ArrayList<>(SlotsOpeningColumnMotion.ROWS);
            for (int row = 0; row < SlotsGeometry.CANVAS_ROWS; row++) {
                finalColumn.add(asSkippableAnimationItem(finalItems[row * columnCount + col]));
            }
            finalColumn.add(asSkippableAnimationItem(finalItems[SlotsGeometry.CANVAS_ROWS * columnCount + col]));
            List<ItemStack> secondBurst = fixedRainbowFillerItems();
            entries.add(SlotsOpeningColumnMotion.buildEntrySequenceWithSettle(firstBurst, secondBurst, finalColumn));
        }
        final int[] entryCounts = new int[columnCount];
        int maxEntryCount = 0;
        for (int col = 0; col < columnCount; col++) {
            entryCounts[col] = entries.get(col).size();
            maxEntryCount = Math.max(maxEntryCount, entryCounts[col]);
        }
        final long finalTick = SlotsOpeningColumnMotion.finalTick(
            columnCount, SlotsTiming.OPENING_COLUMN_STAGGER_TICKS, maxEntryCount);
        final ItemStack[][] columnState = new ItemStack[columnCount][SlotsOpeningColumnMotion.ROWS];
        final long[] elapsed = {0L};

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (closeFlag || openingGeneration != myGeneration) {
                    cancel();
                    openingAnimationTask = null;
                    return;
                }
                long tick = elapsed[0];
                for (int col = 0; col < columnCount; col++) {
                    int localIndex = SlotsOpeningColumnMotion.localEntryIndexAt(
                        col, SlotsTiming.OPENING_COLUMN_STAGGER_TICKS, tick, entryCounts[col]);
                    if (localIndex < 0) {
                        continue;
                    }
                    SlotsOpeningColumnMotion.shiftDownAndInsert(columnState[col], entries.get(col).get(localIndex));
                    paintOpeningColumn(col, columnState[col]);
                }
                if (tick >= finalTick) {
                    cancel();
                    openingAnimationTask = null;
                    finishOpeningAnimation(myGeneration);
                    return;
                }
                elapsed[0] = tick + SlotsTiming.OPENING_STEP_TICKS;
            }
        };
        openingAnimationTask = runnable.runTaskTimer(plugin, SlotsTiming.OPENING_STEP_TICKS, SlotsTiming.OPENING_STEP_TICKS);
    }

    /** Paints one opening-animation column's current 6 cells (5 canvas rows, then the control row) into the real inventory. */
    private void paintOpeningColumn(int col, ItemStack[] state) {
        int columnCount = SlotsGeometry.INVENTORY_WIDTH;
        for (int row = 0; row < SlotsGeometry.CANVAS_ROWS; row++) {
            getInventory().setItem(row * columnCount + col, state[row]);
        }
        getInventory().setItem(SlotsGeometry.CANVAS_ROWS * columnCount + col, state[SlotsGeometry.CANVAS_ROWS]);
    }

    /**
     * On successful completion, unlocks controls and re-renders the
     * authoritative pregame layout from live state -- a cheap, idempotent
     * verification pass, since the last item every column received was
     * already that exact final item.
     */
    private void finishOpeningAnimation(long myGeneration) {
        if (openingGeneration != myGeneration) {
            return;
        }
        openingActive = false;
        redrawEverything();
    }

    /**
     * A cosmetic filler pane: no lore, and named with the shared
     * {@code common.click-skip} text (the same "CLICK TO SKIP" convention
     * {@link org.nc.nccasino.components.AnimationMessage} uses for its own
     * skippable animation) rather than blank, so it can never be mistaken
     * for a control or a rolled symbol while still telling the player a
     * click lands here.
     */
    /** One fixed vertical-rainbow filler burst, as real renderable items. */
    private List<ItemStack> fixedRainbowFillerItems() {
        Material[] colors = SlotsOpeningFiller.fixedRainbowSequence();
        List<ItemStack> items = new ArrayList<>(colors.length);
        for (Material material : colors) {
            items.add(blankFillerItem(material));
        }
        return items;
    }

    private ItemStack blankFillerItem(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("common.click-skip"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * A clone of a real final-layout item, renamed to the same
     * {@code common.click-skip} text as {@link #blankFillerItem} for the
     * duration of the opening animation, so every item the animation shows
     * -- filler and real final items alike -- carries the same "click to
     * skip" hint. {@link #finishOpeningAnimation} always repaints the
     * authoritative frame from scratch afterward, so this renamed clone is
     * never what the player sees once the machine is actually playable.
     */
    private ItemStack asSkippableAnimationItem(ItemStack source) {
        if (source == null) {
            return null;
        }
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("common.click-skip"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * A click anywhere in the inventory while the opening animation is
     * still running skips straight to the finished state: cancels the
     * in-flight task the same way {@link #cancelOpeningAnimationTask()}
     * always does (bumping {@link #openingGeneration} so an already-queued
     * tick can never paint over this), then repaints the authoritative
     * final frame immediately -- the exact same end state the animation
     * would have reached on its own.
     */
    private void skipOpeningAnimation() {
        cancelOpeningAnimationTask();
        redrawEverything();
    }

    /**
     * A genuine interruption of the opening animation -- the inventory
     * closing, session termination, or plugin disable before it finished.
     * Bumps {@link #openingGeneration} (not just cancels the task) so a
     * callback already queued for this tick can still detect it is stale,
     * the same pattern {@link #cancelDemoTask()} uses.
     */
    private void cancelOpeningAnimationTask() {
        openingGeneration++;
        openingActive = false;
        if (openingAnimationTask != null) {
            openingAnimationTask.cancel();
            openingAnimationTask = null;
        }
    }

    // ---- rendering -----------------------------------------------------

    private void renderFrame() {
        if (uiView.isModal()) {
            // Every modal view repurposes the whole 5-row canvas as its own
            // layout rather than the current width/height grid, and paints
            // (or deliberately clears) every one of its own slots itself.
            return;
        }
        int columns = config.columns();
        int rows = config.visibleRows();
        for (int slot = 0; slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
            if (SlotsGeometry.isGridSlot(columns, rows, slot)) {
                continue;
            }
            // The housing is a horizontal rainbow keyed purely off the
            // slot's inventory column (slot % 9, see SlotsRainbowHousing) so
            // it grows and shrinks around every supported geometry without
            // any per-geometry slot table.
            addItemAndLore(SlotsRainbowHousing.materialForSlot(slot), 1, " ", slot);
        }
    }

    /**
     * The pregame/idle canvas: the neutral "ready" state, never the union of
     * every active payline (that reads as meaningless once a horizontal line
     * covers every cell at wide geometries). Once a spin has actually run,
     * {@link #reelDisplay} instead holds that spin's real committed result
     * (or a demo's, per {@link #lastGridIsDemo}) and this simply repaints it
     * unchanged.
     */
    private void renderCanvas() {
        switch (uiView) {
            case PAYTABLE -> renderPaytableCanvas();
            case PROFILES -> renderProfilesCanvas();
            case AUTO_SETTINGS -> renderAutoSettingsCanvas();
            case GAME -> {
                int columns = config.columns();
                for (int col = 0; col < columns; col++) {
                    paintReel(col);
                }
            }
        }
    }

    /** Empties every one of the 45 canvas slots, so no modal view can leak the view it replaced. */
    private void clearCanvas() {
        for (int slot = 0; slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
            getInventory().clear(slot);
        }
    }

    // ---- paytable view -------------------------------------------------

    /**
     * The condensed paytable: exactly one card per real paying symbol, in
     * that symbol's own in-game material, whose lore lists every run length
     * actually achievable at the current reel count.
     *
     * <p>Nothing here duplicates a payout value. The symbols come from
     * {@link SlotsSymbol#payingSymbols()} and every multiplier from the live
     * {@link SlotsPaytable}, so a paytable derived from a different house
     * edge, variance or reel count renders itself correctly with no change
     * here -- and the card slots come from {@link SlotsPaytableLayout}, which
     * packs whatever number of symbols exists rather than assuming five.
     *
     * <p>Canvas row 4 (slots 36-44) is not part of this layout: it belongs to
     * the informational rail that aligns with the bottom control row.
     */
    private void renderPaytableCanvas() {
        clearCanvas();
        SlotsPaytable paytable = config.paytable();
        double denomination = chipValues[denominationIndex];

        for (int slot : SlotsPaytableLayout.paytableCanvasSlots()) {
            addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, " ", slot);
        }

        renderPaytableInfoColumn(paytable);
        renderPaytableLegend();
        renderCurrentMachineCard(paytable, denomination);

        SlotsSymbol[] symbols = SlotsSymbol.payingSymbols();
        int[] cardSlots = SlotsPaytableLayout.symbolCardSlots(symbols.length);
        for (int i = 0; i < symbols.length; i++) {
            renderSymbolCard(symbols[i], cardSlots[i], paytable, denomination);
        }

        renderInformationalRail(denomination);
    }

    /**
     * One symbol's card: its real material, its localized name, and one
     * {@code run - multiplier - return} line per achievable run at the
     * current reel count. Return is the total returned payout for one line
     * (multiplier x the per-line wager), never profit on top of the stake.
     */
    private void renderSymbolCard(SlotsSymbol symbol, int slot, SlotsPaytable paytable, double denomination) {
        int columns = config.columns();
        List<String> lore = new ArrayList<>();
        lore.add(text("slots.paytable-card-header"));
        boolean anyRun = false;
        for (int run = Math.max(1, symbol.minimumRun()); run <= columns; run++) {
            double multiplier = paytable.multiplier(symbol, run);
            if (multiplier <= 0.0) {
                continue;
            }
            anyRun = true;
            lore.add(text("slots.paytable-card-row",
                "run", run,
                "multiplier", formatMultiplier(multiplier),
                "amount", plugin.formatWagerDisplay(currencyMode, currencyName, multiplier * denomination)));
        }
        if (!anyRun) {
            // Only reachable if a symbol's minimum run exceeds the machine's
            // width; stating it is far better than rendering an empty card.
            lore.add(text("slots.paytable-card-no-runs", "columns", columns));
        }
        lore.add(text("slots.paytable-leftmost-rule"));
        addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), slot, lore.toArray(new String[0]));
    }

    /** The narrow left-hand explanatory column: runs, pricing, the Blank symbol, and reels/volatility. */
    private void renderPaytableInfoColumn(SlotsPaytable paytable) {
        int[] slots = SlotsPaytableLayout.infoColumnSlots();

        addItemAndLore(SlotsControlPresentation.Role.GUIDE_BOOK.material(), 1,
            text("slots.guide-runs-title"), slots[0],
            text("slots.guide-runs-leftmost"),
            text("slots.guide-runs-adjacent"),
            text("slots.guide-runs-end"));

        addItemAndLore(SlotsControlPresentation.Role.GUIDE_BOOK.material(), 1,
            text("slots.guide-pricing-title"), slots[1],
            text("slots.guide-pricing-longest"),
            text("slots.guide-pricing-total-return"));

        // BLANK is a real, weighted, non-paying strip symbol -- its
        // payWeight() is 0 and it has no minimum run, so it can never appear
        // on a card above, and it ends any run it lands in.
        addItemAndLore(SlotsSymbol.BLANK.material(), 1, text("slots.guide-blank-title"), slots[2],
            text("slots.guide-blank-never-pays"),
            text("slots.guide-blank-ends-run"));

        addItemAndLore(SlotsControlPresentation.Role.GUIDE_BOOK.material(), 1,
            text("slots.guide-volatility-title"), slots[3],
            text("slots.guide-volatility-tradeoff"),
            text("slots.guide-volatility-normalized", "rtp", formatPercent(paytable.theoreticalRtp())),
            text("slots.guide-volatility-height"));
    }

    /** The single centred Legend that explains the Run / Multiplier / Return card format. */
    private void renderPaytableLegend() {
        // GUIDE_BOOK is BOOK, not KNOWLEDGE_BOOK -- a knowledge book glints
        // inherently in vanilla Minecraft regardless of actual enchantments,
        // which would violate the approved glint list.
        addItemAndLore(SlotsControlPresentation.Role.GUIDE_BOOK.material(), 1,
            text("slots.paytable-legend"), SlotsPaytableLayout.LEGEND_SLOT,
            text("slots.paytable-card-header"),
            text("slots.paytable-legend-run"),
            text("slots.paytable-legend-multiplier"),
            text("slots.paytable-legend-return"));
    }

    /** The live machine summary that balances the Legend on the paytable's top row. */
    private void renderCurrentMachineCard(SlotsPaytable paytable, double denomination) {
        addItemAndLore(SlotsControlPresentation.Role.GUIDE_BOOK.material(), 1,
            text("slots.guide-machine-title"), SlotsPaytableLayout.MACHINE_SLOT,
            text("slots.guide-machine-reels", "columns", config.columns()),
            text("slots.guide-machine-height", "rows", config.visibleRows()),
            text("slots.guide-machine-lines", "lines", config.activeLines()),
            text("slots.guide-machine-wager", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, denomination)),
            text("slots.guide-machine-total-bet", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet())),
            text("slots.guide-machine-rtp", "rtp", formatPercent(paytable.theoreticalRtp())),
            text("slots.guide-machine-edge", "edge", formatPercent(paytable.houseEdge())),
            text("slots.guide-machine-variance", "variance", text(varianceKey(config.variance()))));
    }

    /**
     * The Paytable-only informational rail: canvas slots 36-44, each sitting
     * directly above the bottom-row control it explains (see
     * {@link SlotsInfoRail}). Rendered in one neutral material so it can
     * never be mistaken for a second row of controls, and every click on it
     * is cancelled.
     */
    private void renderInformationalRail(double denomination) {
        Material rail = SlotsControlPresentation.Role.INFO_RAIL.material();
        int profileCount = profileCount();

        addItemAndLore(rail, 1, text("slots.rail-exit"), SlotsInfoRail.railSlotFor(EXIT_SLOT),
            text("slots.rail-exit-what"),
            text("slots.rail-exit-session"));

        addItemAndLore(rail, 1, text("slots.rail-reels"), SlotsInfoRail.railSlotFor(REELS_SLOT),
            text("slots.rail-reels-current", "columns", config.columns()),
            text("slots.rail-reels-tradeoff"),
            text("slots.rail-reels-controls"));

        addItemAndLore(rail, 1, text("slots.rail-height"), SlotsInfoRail.railSlotFor(HEIGHT_SLOT),
            text("slots.rail-height-current", "rows", config.visibleRows()),
            text("slots.rail-height-effect"),
            text("slots.rail-height-controls"));

        addItemAndLore(rail, 1, text("slots.rail-paytable"), SlotsInfoRail.railSlotFor(PAYTABLE_SLOT),
            text("slots.rail-paytable-run"),
            text("slots.rail-paytable-multiplier"),
            text("slots.rail-paytable-return"),
            text("slots.rail-paytable-back"));

        addItemAndLore(rail, 1, text("slots.rail-spin"), SlotsInfoRail.railSlotFor(SPIN_SLOT),
            text("slots.rail-spin-total", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet())),
            balanceRailLine(),
            lastResultLoreLine(),
            text("slots.rail-spin-controls"));

        addItemAndLore(rail, 1, text("slots.rail-clock"), SlotsInfoRail.railSlotFor(CLOCK_SLOT),
            text("slots.rail-clock-speed", "speed", text(spinSpeed.labelKey())),
            autoSpinSummaryLine(),
            text("slots.rail-clock-controls"));

        addItemAndLore(rail, 1, text("slots.rail-paylines"), SlotsInfoRail.railSlotFor(LINES_SLOT),
            text("slots.rail-paylines-current", "lines", config.activeLines()),
            text("slots.rail-paylines-cost"),
            text("slots.rail-paylines-feedback"),
            text("slots.rail-paylines-controls"));

        addItemAndLore(rail, 1, text("slots.rail-wager"), SlotsInfoRail.railSlotFor(WAGER_SLOT),
            text("slots.rail-wager-current", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, denomination)),
            text("slots.rail-wager-total", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet()),
                "lines", config.activeLines()),
            text("slots.rail-wager-controls"));

        addItemAndLore(rail, 1, text("slots.rail-profiles"), SlotsInfoRail.railSlotFor(PROFILES_SLOT),
            text("slots.rail-profiles-count", "count", profileCount,
                "max", SlotsProfileStore.MAX_PROFILES_PER_PLAYER),
            text("slots.rail-profiles-global"),
            text("slots.rail-profiles-stores"),
            profileCount > 0 ? text("slots.rail-profiles-controls") : text("slots.rail-profiles-controls-empty"));
    }

    private String balanceRailLine() {
        CurrencyProvider provider = getCurrencyProvider();
        if (provider == null) {
            return text("slots.spin-lore-balance-items");
        }
        return text("slots.spin-lore-balance", "amount",
            plugin.formatWagerDisplay(currencyMode, currencyName, provider.getBalance(player, internalName)));
    }

    // ---- profiles view -------------------------------------------------

    /**
     * The Profiles view owns the upper 45 slots outright: the rainbow
     * housing, the white reel bay and every other decorative canvas item are
     * cleared away first, so the canvas contains nothing but this player's
     * actual saved profiles -- one inventory slot each, packed row-major from
     * slot 0, with every unused position left genuinely empty.
     */
    private void renderProfilesCanvas() {
        clearCanvas();
        List<SlotsProfile> saved = savedProfiles();
        for (int index = 0; index < saved.size() && index < SlotsProfileStore.MAX_PROFILES_PER_PLAYER; index++) {
            SlotsProfile profile = saved.get(index);
            addItemAndLore(SlotsControlPresentation.Role.PROFILE_ENTRY.material(), 1,
                profile.name(), index, profileEntryLore(profile));
        }
    }

    private String[] profileEntryLore(SlotsProfile profile) {
        List<String> lore = new ArrayList<>();
        lore.add(text("slots.profile-entry-height", "rows", profile.height()));
        lore.add(text("slots.profile-entry-reels", "columns", profile.reels()));
        lore.add(text("slots.profile-entry-paylines", "lines", profile.paylines()));
        lore.add(text("slots.profile-entry-wager", "amount",
            plugin.formatWagerDisplay(currencyMode, currencyName, profile.wagerPerLine())));
        lore.add(text("slots.profile-entry-speed", "speed", text(profile.spinSpeed().labelKey())));
        lore.add(text("slots.profile-entry-auto", "summary", autoSettingsSummary(profile.autoSettings())));
        lore.add("");
        lore.add(text("slots.profile-entry-load"));
        lore.add(text("slots.profile-entry-delete"));
        return lore.toArray(new String[0]);
    }

    // ---- auto spin settings view ---------------------------------------

    /**
     * The Auto Spin Settings menu. Every canvas slot is repainted -- the
     * eight entries on their symmetric cross ({@link SlotsAutoSettingsLayout})
     * and a blank backdrop everywhere else -- so no stale reel symbol can
     * ever show through behind the settings.
     */
    private void renderAutoSettingsCanvas() {
        clearCanvas();
        Material backdrop = SlotsControlPresentation.Role.AUTO_SETTINGS_BACKDROP.material();
        for (int slot = 0; slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
            if (SlotsAutoSettingsLayout.isBackdrop(slot)) {
                addItemAndLore(backdrop, 1, " ", slot);
            }
        }

        addItemAndLore(SlotsControlPresentation.Role.AUTO_SETTINGS_OVERVIEW.material(), 1,
            text("slots.auto-settings-title"), SlotsAutoSettingsLayout.OVERVIEW_SLOT,
            text("slots.auto-settings-intro"),
            text("slots.auto-settings-speed-note", "speed", text(spinSpeed.labelKey())),
            text("slots.auto-settings-summary", "summary", autoSettingsSummary(autoSettings)));

        addItemAndLore(SlotsControlPresentation.Role.AUTO_SETTINGS_SPIN_LIMIT.material(),
            SlotsStackSize.forSpinLimit(autoSettings.spinLimit()),
            text("slots.auto-spin-limit"), SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT,
            autoSettings.hasSpinLimit()
                ? text("slots.auto-spin-limit-current", "spins", autoSettings.spinLimit())
                : text("slots.auto-spin-limit-unlimited"),
            text("slots.auto-spin-limit-hint"));

        addItemAndLore(toggleMaterial(autoSettings.stopOnAnyWin(),
                SlotsControlPresentation.Role.AUTO_SETTINGS_ANY_WIN_ON), 1,
            text("slots.auto-any-win"), SlotsAutoSettingsLayout.STOP_ON_ANY_WIN_SLOT,
            autoSettings.stopOnAnyWin() ? text("slots.auto-state-on") : text("slots.auto-state-off"),
            text("slots.auto-any-win-description"),
            text("slots.auto-any-win-hint"));

        addItemAndLore(toggleMaterial(autoSettings.hasBigWinMultiplier(),
                SlotsControlPresentation.Role.AUTO_SETTINGS_BIG_WIN_ON), 1,
            text("slots.auto-big-win"), SlotsAutoSettingsLayout.BIG_WIN_SLOT,
            autoSettings.hasBigWinMultiplier()
                ? text("slots.auto-big-win-current", "multiplier",
                    formatMultiplier(autoSettings.bigWinMultiplier()))
                : text("slots.auto-state-off"),
            text("slots.auto-big-win-description"),
            text("slots.auto-big-win-hint"));

        addItemAndLore(SlotsControlPresentation.Role.AUTO_SETTINGS_START.material(), 1,
            text("slots.auto-settings-start"), SlotsAutoSettingsLayout.START_SLOT,
            text("slots.auto-settings-start-hint"));

        addItemAndLore(toggleMaterial(autoSettings.hasProfitTarget(),
                SlotsControlPresentation.Role.AUTO_SETTINGS_PROFIT_ON), 1,
            text("slots.auto-profit-target"), SlotsAutoSettingsLayout.PROFIT_TARGET_SLOT,
            autoSettings.hasProfitTarget()
                ? text("slots.auto-profit-target-current", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, autoSettings.profitTarget()))
                : text("slots.auto-state-off"),
            text("slots.auto-profit-target-description"),
            text("slots.auto-profit-target-hint"));

        addItemAndLore(toggleMaterial(autoSettings.hasLossLimit(),
                SlotsControlPresentation.Role.AUTO_SETTINGS_LOSS_ON), 1,
            text("slots.auto-loss-limit"), SlotsAutoSettingsLayout.LOSS_LIMIT_SLOT,
            autoSettings.hasLossLimit()
                ? text("slots.auto-loss-limit-current", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, autoSettings.lossLimit()))
                : text("slots.auto-state-off"),
            text("slots.auto-loss-limit-description"),
            text("slots.auto-loss-limit-hint"));

        addItemAndLore(SlotsControlPresentation.Role.AUTO_SETTINGS_RESET.material(), 1,
            text("slots.auto-settings-reset"), SlotsAutoSettingsLayout.RESET_SLOT,
            text("slots.auto-settings-reset-hint"),
            text("slots.auto-settings-reset-keeps-speed"));
    }

    private static Material toggleMaterial(boolean on, SlotsControlPresentation.Role onRole) {
        return on ? onRole.material() : SlotsControlPresentation.Role.AUTO_SETTINGS_OFF.material();
    }

    /** A one-line human summary of an Auto Spin configuration, used in lore everywhere it is shown. */
    private String autoSettingsSummary(SlotsAutoSpinSettings settings) {
        String limit = settings.hasSpinLimit()
            ? String.valueOf(settings.spinLimit())
            : text("slots.auto-summary-unlimited");
        String stops = settings.stopOnAnyWin() || settings.hasBigWinMultiplier()
            || settings.hasProfitTarget() || settings.hasLossLimit()
            ? text("slots.auto-summary-stops-on")
            : text("slots.auto-summary-stops-none");
        return text("slots.auto-summary", "spins", limit, "stops", stops);
    }

    private String autoSpinSummaryLine() {
        return text(autoSpinActive ? "slots.rail-clock-auto-active" : "slots.rail-clock-auto-stopped",
            "summary", autoSettingsSummary(autoSettings));
    }

    private String varianceKey(SlotsVariance variance) {
        return "slots.variance-" + variance.name().toLowerCase();
    }

    private String shapeKey(SlotsPaylineCatalog.Line line) {
        return "slots.payline-shape-" + line.shapeKey();
    }

    /**
     * The pregame/reset "not yet spun" state, left as {@code null} cells
     * rather than {@link SlotsSymbol#BLANK} -- a rolled BLANK is a real
     * strip stop and must never look identical to "nothing has been rolled
     * here yet". {@link #paintReel} renders a {@code null} cell as a
     * distinct neutral placeholder, never as an evaluated outcome.
     */
    private static SlotsSymbol[][] neutralGrid(int columns, int rows) {
        return new SlotsSymbol[columns][rows];
    }

    /**
     * The bottom control row, always in this exact left-to-right order:
     * Exit (45), Reels (46), Height (47), Paytable (48), the central Spin
     * lever (49), the Clock (50), Paylines (51), Wager Per Line (52) and
     * Saved Profiles (53).
     *
     * <p>The four configuration controls thus read Reels, Height, Paylines,
     * Wager Per Line across the row, each keeping its own colour: brown, pink,
     * green, black respectively.
     *
     * <p>Whichever modal view is open replaces exactly one of those slots
     * with Back to Game -- 48 in Paytable, 50 in Auto Spin Settings, 53 in
     * Profiles -- and nothing else about the row changes, so the player's
     * muscle memory for every other control survives every view.
     */
    private void renderControls() {
        boolean locked = !controller.isReadyForSpin() || demoActive;
        boolean blocked = controller.state() == SlotsSessionState.SETTLEMENT_FAILED;
        boolean heightOne = config.visibleRows() == 1;
        // Profiles and Auto Spin Settings are modal editors: their bottom-row
        // controls stay visible for orientation but must never change the
        // machine out from under the menu.
        boolean inert = !uiView.allowsConfigurationChanges();
        double denomination = chipValues[denominationIndex];

        addItemAndLore(SlotsControlPresentation.Role.EXIT_CONTROL.material(), 1,
            text("slots.exit"), EXIT_SLOT, text("slots.exit-lore"));

        if (!renderBackToGameIfOwned(REELS_SLOT)) {
            addItemAndLore(SlotsControlPresentation.Role.REELS_CONTROL.material(), SlotsStackSize.forReels(config.columns()),
                text("slots.reels"), REELS_SLOT,
                text("slots.reels-current", "columns", config.columns()),
                text("slots.reels-description"),
                text("slots.reels-hint"));
            if (locked || inert) {
                dimSlot(REELS_SLOT);
            }
        }

        if (!renderBackToGameIfOwned(HEIGHT_SLOT)) {
            addItemAndLore(SlotsControlPresentation.Role.HEIGHT_CONTROL.material(), SlotsStackSize.forHeight(config.visibleRows()),
                text("slots.height"), HEIGHT_SLOT,
                text("slots.height-current", "rows", config.visibleRows()),
                text("slots.height-description"),
                text("slots.height-hint"));
            if (locked || inert) {
                dimSlot(HEIGHT_SLOT);
            }
        }

        renderPaytableSlot();
        renderSpinControl(blocked, presentationRunning(), denomination);
        renderAutoSpinControl();

        if (!renderBackToGameIfOwned(LINES_SLOT)) {
            addItemAndLore(SlotsControlPresentation.Role.PAYLINES_CONTROL.material(), SlotsStackSize.forPaylines(config.activeLines()),
                text("slots.paylines"), LINES_SLOT,
                text("slots.paylines-current", "lines", config.activeLines()),
                text("slots.paylines-description"),
                heightOne ? text("slots.paylines-inert") : text("slots.paylines-hint"));
            if (locked || heightOne || inert) {
                dimSlot(LINES_SLOT);
            }
        }

        if (!renderBackToGameIfOwned(WAGER_SLOT)) {
            addItemAndLore(SlotsControlPresentation.Role.WAGER_CONTROL.material(), SlotsStackSize.forWager(denomination),
                text("slots.wager-control"), WAGER_SLOT,
                text("slots.wager-control-current", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, denomination)),
                text("slots.wager-control-description"),
                text("slots.wager-control-hint"));
            if (locked || inert) {
                dimSlot(WAGER_SLOT);
            }
        }

        renderProfilesSlot();
    }

    /**
     * Paints Back to Game over {@code slot} if the open view owns that slot.
     *
     * @return whether Back to Game was painted, in which case the caller must
     *     not paint that slot's ordinary Game View control
     */
    private boolean renderBackToGameIfOwned(int slot) {
        if (uiView.backToGameSlot() != slot) {
            return false;
        }
        addItemAndLore(SlotsControlPresentation.Role.BACK_TO_GAME.material(), 1,
            text("slots.back-to-game"), slot, text("slots.back-to-game-hint"));
        return true;
    }

    /** Whether a paid or Demo Spin presentation is currently running -- the lever's fast-forward eligibility. */
    private boolean presentationRunning() {
        return animationTask != null || demoTask != null;
    }

    private void renderAutoSpinControl() {
        if (renderBackToGameIfOwned(CLOCK_SLOT)) {
            return;
        }
        String titleKey = autoSpinActive ? "slots.auto-spin-title-active" : "slots.auto-spin-title-stopped";
        String statusKey = autoSpinActive ? "slots.auto-spin-status-active" : "slots.auto-spin-status-stopped";
        String startStopKey = autoSpinActive ? "slots.auto-spin-left-click-stop" : "slots.auto-spin-left-click-start";
        addItemAndLore(SlotsControlPresentation.Role.AUTO_SPIN_CONTROL.material(), 1, text(titleKey), CLOCK_SLOT,
            text(statusKey),
            text("slots.auto-spin-speed", "speed", text(spinSpeed.labelKey())),
            text("slots.auto-spin-settings-summary", "summary", autoSettingsSummary(autoSettings)),
            text(startStopKey),
            text("slots.auto-spin-right-click-speed"),
            text("slots.auto-spin-shift-right-settings"));
        if (!uiView.allowsConfigurationChanges()) {
            dimSlot(CLOCK_SLOT);
        }
    }

    private void renderPaytableSlot() {
        if (renderBackToGameIfOwned(PAYTABLE_SLOT)) {
            return;
        }
        addItemAndLore(SlotsControlPresentation.Role.PAYTABLE_OPEN.material(), 1, text("slots.paytable"), PAYTABLE_SLOT,
            text("slots.paytable-open-hint"));
        if (!uiView.allowsConfigurationChanges()) {
            dimSlot(PAYTABLE_SLOT);
        }
    }

    /**
     * The Ender Chest. When the player has no profiles yet its lore says only
     * that a left-click saves one -- there is deliberately no "right-click to
     * open" instruction for a list that does not exist. Once at least one
     * profile is saved, the right-click instruction and the live count appear.
     */
    private void renderProfilesSlot() {
        if (renderBackToGameIfOwned(PROFILES_SLOT)) {
            return;
        }
        int count = profileCount();
        List<String> lore = new ArrayList<>();
        lore.add(text("slots.profiles-global"));
        if (count > 0) {
            lore.add(text("slots.profiles-count", "count", count,
                "max", SlotsProfileStore.MAX_PROFILES_PER_PLAYER));
        }
        lore.add(text("slots.profiles-left-click-save"));
        if (count > 0) {
            lore.add(text("slots.profiles-right-click-open"));
        }
        addItemAndLore(SlotsControlPresentation.Role.PROFILES_CONTROL.material(),
            SlotsStackSize.forProfiles(count), text("slots.profiles"), PROFILES_SLOT,
            lore.toArray(new String[0]));
        if (!uiView.allowsConfigurationChanges()) {
            dimSlot(PROFILES_SLOT);
        }
    }

    /**
     * The Spin lever's own name/lore is the machine's primary financial
     * summary -- the exact conceptual order and blank-line grouping required
     * by the redesign: a left-click instruction, the total bet and per-line
     * breakdown, a right-click Demo instruction, the balance, and finally
     * Last Result.
     */
    private void renderSpinControl(boolean blocked, boolean active, double denomination) {
        if (blocked) {
            // Not glowing -- only a READY real Spin glints; a blocked/retry-pending control is deliberately inert-looking.
            addItemAndLore(SlotsControlPresentation.Role.PAYOUT_BLOCKED.material(), 1, text("slots.payout-blocked"),
                SPIN_SLOT, text("slots.payout-blocked-retry"));
            return;
        }
        if (active) {
            addItemAndLore(SlotsControlPresentation.Role.SPIN_ACTIVE.material(), 1, text("slots.spin-active"),
                SPIN_SLOT, text("slots.spin-active-lore"));
            return;
        }
        if (!uiView.allowsConfigurationChanges()) {
            // A modal editor is open: the lever is inert until the player
            // goes back, so it must read as inert rather than as a ready,
            // glinting Spin the click routing will simply refuse.
            addItemAndLore(SlotsControlPresentation.Role.SPIN_LOCKED.material(), 1, text("slots.spin"),
                SPIN_SLOT, text("slots.modal-view-locked"));
            dimSlot(SPIN_SLOT);
            return;
        }

        List<String> lore = new ArrayList<>();
        lore.add(text("slots.spin-lore-left-click"));
        lore.add("");
        lore.add(text("slots.spin-lore-total", "amount",
            plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet())));
        lore.add(text("slots.spin-lore-breakdown",
            "wager", plugin.formatWagerDisplay(currencyMode, currencyName, denomination),
            "lines", config.activeLines()));
        lore.add(text("slots.spin-lore-right-click-demo"));
        lore.add("");

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            int balance = provider.getBalance(player, internalName);
            lore.add(text("slots.spin-lore-balance", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, balance)));
        } else {
            lore.add(text("slots.spin-lore-balance-items"));
        }
        lore.add("");
        lore.add(lastResultLoreLine());

        setGlowingItem(SPIN_SLOT, SlotsControlPresentation.Role.SPIN_READY.material(), text("slots.spin"), lore.toArray(new String[0]));
    }

    private String lastResultLoreLine() {
        long amount = lastWinState.displayedWin();
        if (amount < 0) {
            return text("slots.last-result-not-yet-spun");
        }
        if (amount == 0) {
            return text("slots.last-result-no-win");
        }
        return text("slots.last-result-won", "amount",
            plugin.formatWagerDisplay(currencyMode, currencyName, amount));
    }

    /**
     * Re-renders only the Spin lever -- used by the win-meter count-up ticker
     * so a still-animating Last Result never has to repaint the other eight
     * controls every {@link SlotsTiming#WIN_METER_STEP_TICKS} ticks.
     */
    private void refreshSpinControl() {
        boolean blocked = controller.state() == SlotsSessionState.SETTLEMENT_FAILED;
        renderSpinControl(blocked, presentationRunning(), chipValues[denominationIndex]);
    }

    /**
     * Counts the win meter up to the awarded amount rather than snapping to
     * it. The credited balance is already final before this starts -- this is
     * presentation only, and a termination mid-count loses nothing.
     *
     * <p>The caller must have already stopped any previous scheduler (via
     * {@link #stopWinMeterScheduler()}) and called {@link SlotsWinMeterAnimation#settle}
     * to obtain {@code generation} <em>before</em> invoking this method --
     * this method itself must never cancel the very animation it is about to
     * schedule ticks for. {@link SlotsWinMeterMath#increment} guarantees
     * completion in at most {@link SlotsTiming#WIN_METER_MAX_TICKS} regardless
     * of how {@code payout} divides, and every tick is guarded by
     * {@code generation} so a stale callback from a superseded animation can
     * never repaint over a newer result.
     */
    private void animateWinMeter(long payout, long generation) {
        long steps = SlotsWinMeterMath.steps(SlotsTiming.WIN_METER_MAX_TICKS, SlotsTiming.WIN_METER_STEP_TICKS);
        long increment = SlotsWinMeterMath.increment(payout, steps);
        long stepTicks = spinSpeed.scaled(SlotsTiming.WIN_METER_STEP_TICKS);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (closeFlag || player == null || !player.isOnline()) {
                    cancel();
                    winMeterTask = null;
                    return;
                }
                long shown = lastWinState.tick(generation, increment);
                if (shown < 0) {
                    // Stale (superseded) or nothing animating -- never repaint
                    // or play a tick sound off a sentinel value.
                    cancel();
                    winMeterTask = null;
                    return;
                }
                refreshSpinControl();
                playMeterTick(shown, payout);
                if (!lastWinState.isCurrent(generation)) {
                    cancel();
                    winMeterTask = null;
                }
            }
        };
        winMeterTask = runnable.runTaskTimer(plugin, stepTicks, stepTicks);
    }

    /** Rising ticks as the meter climbs, the way a physical machine pays out. */
    private void playMeterTick(long shown, long payout) {
        float progress = payout <= 0 ? 1f : (float) shown / payout;
        play("block.note_block.hat", Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 0.9f + (progress * 0.9f));
    }

    private long currentTotalBet() {
        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        try {
            return SlotsMath.totalBetForGeometry(denomUnits, config.visibleRows(), config.activeLines());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    // ---- small render helpers -------------------------------------------

    /** Item with the repo's standard "glow" treatment (harmless enchant, hidden from lore). */
    private void setGlowingItem(int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(line.isEmpty() ? "" : ChatColor.GRAY + line);
                }
                meta.setLore(loreList);
            }
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        getInventory().setItem(slot, item);
    }

    private void dimSlot(int slot) {
        ItemStack existing = getInventory().getItem(slot);
        if (existing == null) {
            return;
        }
        ItemMeta meta = existing.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + ChatColor.stripColor(
                meta.hasDisplayName() ? meta.getDisplayName() : ""));
            existing.setItemMeta(meta);
        }
    }

    private String formatPercent(double fraction) {
        return String.format("%.2f%%", fraction * 100.0);
    }

    private String formatMultiplier(double multiplier) {
        if (multiplier >= 100.0) {
            return String.valueOf(Math.round(multiplier));
        }
        return String.format("%.1f", multiplier);
    }

    private String symbolKey(SlotsSymbol symbol) {
        return "slots.symbol-" + symbol.name().toLowerCase();
    }

    // ---- click handling --------------------------------------------------

    @Override
    public void handleClick(int slot, Player clicker, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotsMachine) || !clicker.getUniqueId().equals(playerId)) {
            return;
        }
        if (openingActive) {
            // The once-per-session opening animation is still falling into
            // place: any click ("CLICK TO SKIP" -- see #asSkippableAnimationItem
            // and #blankFillerItem -- is shown on every animation item)
            // immediately skips to the finished state rather than being a
            // no-op. The listener that dispatched this click has already
            // cancelled the underlying InventoryClickEvent (see
            // DealerInteractListener), so this click can never also fall
            // through to a real control action.
            skipOpeningAnimation();
            return;
        }
        ClickType clickType = event.getClick();
        if (controller.state() == SlotsSessionState.SETTLEMENT_FAILED) {
            if (slot == SPIN_SLOT && SlotsClickClassifier.isOrdinaryClick(clickType)) {
                attemptSettlementRetry();
            } else if (slot == SPIN_SLOT) {
                // An unsupported click type on the retry control is a safe
                // no-op, not a denial message -- it never reached an
                // ordinary-click gate in the first place.
                return;
            } else {
                denyAction(player, text("slots.payout-blocked"));
            }
            return;
        }

        // Every accepted click, in every view, is classified in exactly one
        // place -- including the Clock's shift-right-click, which the
        // ordinary-click gate would otherwise swallow, and the Back to Game
        // substitution each modal view owns. The shared listener has already
        // cancelled the underlying InventoryClickEvent, so nothing routed
        // from here can move an item; this method only ever repaints.
        SlotsControlLayout.Route route = SlotsControlLayout.route(uiView, slot, clickType);
        int direction = route.direction();
        switch (route.target()) {
            case NONE -> {
            }
            case CANVAS -> handleCanvasClick(slot, clickType);
            case BACK_TO_GAME -> handleBackToGame();
            case AUTO_SETTINGS -> handleOpenAutoSettings();
            // Profiles and Auto Spin Settings are modal: their other bottom
            // controls stay visible for orientation but must never change the
            // game out from under the open menu.
            case MODAL_LOCKED -> denyAction(player, text("slots.modal-view-locked"));
            case EXIT -> handleExit();
            case WAGER -> handleChangeDenomination(direction);
            case REELS -> handleChangeColumns(direction);
            case PAYTABLE -> handleOpenPaytable();
            case SPIN -> handleSpinLeverClick(clickType);
            case CLOCK -> handleClockClick(clickType);
            case PAYLINES -> handleChangeLines(direction);
            case HEIGHT -> handleChangeHeight(direction);
            case PROFILES -> handleProfilesControlClick(clickType);
        }
    }

    /**
     * A click inside the upper 45-slot canvas. Only the two menu views act on
     * one: the reel canvas, the Paytable's symbol cards, and its
     * informational rail are all deliberately inert.
     */
    private void handleCanvasClick(int slot, ClickType clickType) {
        switch (uiView) {
            case AUTO_SETTINGS -> handleAutoSettingsClick(slot);
            case PROFILES -> handleProfilesEntryClick(slot, clickType);
            // GAME's reel cells and PAYTABLE's cards/rail are informational
            // only; a click on either is a safe no-op.
            case GAME, PAYTABLE -> {
            }
        }
    }

    private void handleExit() {
        playDefaultSound(player);
        player.closeInventory();
    }

    // ---- view transitions --------------------------------------------

    /**
     * The single authoritative view transition. Every route into a different
     * view goes through here, so the same cleanup always happens: Auto Spin
     * is stopped, any payline flash is cancelled and invalidated (which also
     * invalidates its scheduled frames), the Game canvas is restored from the
     * last committed result when returning, and the whole inventory is
     * repainted authoritatively for the destination.
     *
     * <p>The opening animation is never replayed here -- it belongs to
     * {@link #initializeTable()} alone, which runs exactly once per genuine
     * Slots open.
     *
     * @param destination the view to switch to
     * @param pitch the click pitch to confirm the transition with, or 0 for silence
     */
    private void switchView(SlotsUiView destination, float pitch) {
        stopAutoSpin();
        cancelLineFlashTask();
        if (destination == SlotsUiView.GAME && uiView != SlotsUiView.GAME) {
            restorePlayCanvas();
        }
        uiView = destination;
        if (pitch > 0f) {
            playClick(pitch);
        }
        redrawEverything();
    }

    /** Allowed only while a real spin is not in flight and no demo is currently animating. */
    private void handleOpenPaytable() {
        if (!canOpenModalView()) {
            return;
        }
        switchView(SlotsUiView.PAYTABLE, 1.0f);
    }

    private void handleBackToGame() {
        switchView(SlotsUiView.GAME, 0.9f);
    }

    /**
     * The shared precondition for opening any modal view: no committed spin
     * may be mid-presentation, and no Demo Spin may be animating.
     */
    private boolean canOpenModalView() {
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return false;
        }
        return true;
    }

    /**
     * Silently returns to Game View from any modal view -- used before a
     * spin/demo/auto-spin action begins.
     *
     * <p>The whole inventory is repainted, not just the canvas: the view
     * being left owns one bottom-row slot as Back to Game, and leaving that
     * item in place would advertise "Back to Game" on a slot that has
     * already gone back to its ordinary Game View control. That matters even
     * on the paths that repaint again a moment later, because a rejected
     * spin (insufficient funds, an unsafe wager, a dealer that cannot cover
     * it) never reaches those repaints at all.
     */
    private void returnToGameViewForAction() {
        if (uiView != SlotsUiView.GAME) {
            uiView = SlotsUiView.GAME;
            restorePlayCanvas();
            redrawEverything();
        }
    }

    /**
     * Rebuilds {@link #reelDisplay} from the real controller's last committed
     * outcome (which {@link SlotsSpinController} keeps until the next spin
     * overwrites it, settlement or no), so returning to Game View restores
     * the last compatible paid result -- never a stale Paytable item and
     * never a leftover Demo Spin grid.
     */
    private void restorePlayCanvas() {
        SlotsOutcome last = controller.currentOutcome();
        lastGridIsDemo = false;
        if (last != null && last.columns() == config.columns() && last.rows() == config.visibleRows()) {
            SlotsSymbol[][] grid = new SlotsSymbol[last.columns()][last.rows()];
            for (int col = 0; col < last.columns(); col++) {
                for (int row = 0; row < last.rows(); row++) {
                    grid[col][row] = last.symbolAt(row, col);
                }
            }
            reelDisplay = grid;
        } else {
            reelDisplay = neutralGrid(config.columns(), config.visibleRows());
        }
    }

    // ---- settings controls ---------------------------------------------

    /**
     * Every configuration control checks its precondition <em>before</em>
     * stopping Auto Spin, never after. A rejected click is not a
     * configuration change, so it must not silently end the player's batch --
     * and stopping first would additionally leave the Clock advertising a
     * batch that is no longer running until the next repaint happened to
     * come along.
     */
    private void handleChangeHeight(int direction) {
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        stopAutoSpin();
        int[] supported = SlotsGeometry.supportedRowCounts();
        int next = supported[Math.floorMod(indexOf(supported, config.visibleRows()) + direction, supported.length)];
        config = config.withVisibleRows(next);
        onGeometryChanged();
        playClick(direction > 0 ? 1.2f : 0.9f);
        redrawEverything();
    }

    /** Cycles among the supported widths in {@code direction}'s order, wrapping both ways. */
    private void handleChangeColumns(int direction) {
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        stopAutoSpin();
        int[] supported = SlotsGeometry.supportedColumnCounts();
        int next = supported[Math.floorMod(indexOf(supported, config.columns()) + direction, supported.length)];
        config = config.withColumns(next);
        onGeometryChanged();
        playClick(direction > 0 ? 1.2f : 0.9f);
        redrawEverything();
    }

    private void handleChangeLines(int direction) {
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        if (config.visibleRows() == 1) {
            denyAction(player, text("slots.paylines-inert"));
            return;
        }
        stopAutoSpin();
        // Every new Paylines input synchronously supersedes any active
        // blink -- cancelled, invalidated, and the clean canvas repainted --
        // before this input's own feedback renders, so two different lines'
        // colored paths can never blend together.
        supersedeLineFlash();
        int max = SlotsPaylineCatalog.lineCount(config.visibleRows());
        int oldLines = config.activeLines();
        int next = Math.floorMod((oldLines - 1) + direction, max) + 1;
        // The 1<->max wrap changes every line's active status at once, not
        // just one -- it must never be presented as a single-line
        // added/removed flash.
        boolean wrapped = (oldLines == 1 && next == max) || (oldLines == max && next == 1);
        config = config.withActiveLines(next);
        revalidateDenomination();
        playClick(direction > 0 ? 1.4f : 0.8f);
        renderControls();
        SlotsLineChangeRepaint.Action action = SlotsLineChangeRepaint.decide(toLineChangeMode(uiView), wrapped);
        switch (action) {
            case FLASH_SINGLE_LINE -> flashLineChange(oldLines, next);
            case ANNOUNCE_WRAP_AND_REPAINT_CANVAS -> {
                announceLineWrap(next);
                repaintCanvas();
            }
            case REPAINT_CANVAS -> repaintCanvas();
        }
    }

    /**
     * Only Game View shows the single-line blink; every non-Game view simply
     * repaints its own canvas for the new line count. The modal Profiles and
     * Auto Spin Settings views can never actually reach a Paylines change
     * (the routing makes their bottom controls inert), but mapping them here
     * rather than throwing keeps a future route from producing a flash over
     * a menu.
     */
    private static SlotsLineChangeRepaint.Mode toLineChangeMode(SlotsUiView view) {
        return view == SlotsUiView.GAME
            ? SlotsLineChangeRepaint.Mode.GAME
            : SlotsLineChangeRepaint.Mode.PAYTABLE;
    }

    /**
     * Distinct feedback for the 1<->max payline wrap: a localized
     * count-based notice rather than a flash claiming only one line
     * changed, and never the union of every newly (in)active path.
     */
    private void announceLineWrap(int newLines) {
        play("entity.experience_orb.pickup", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD, VERBOSE -> player.sendMessage(text("slots.paylines-wrap-notice", "lines", newLines));
            case NONE -> {
            }
        }
    }

    /**
     * Blinks only the exact path of the line whose active/inactive status
     * just flipped, alternating between the colored path and the ordinary
     * clean canvas per {@link SlotsLineFlashPlan} -- never a single
     * continuous hold, and never the union of every active line, which reads
     * as meaningless once lines overlap. Always finishes on the clean
     * canvas. The caller ({@link #handleChangeLines}, via
     * {@link #supersedeLineFlash()}) has already cancelled and invalidated
     * any prior flash and repainted clean before this is called. Never
     * called in Paytable View -- see {@link SlotsLineChangeRepaint}.
     */
    private void flashLineChange(int oldLines, int newLines) {
        boolean added = newLines > oldLines;
        int changedLineNumber = added ? newLines : oldLines;
        List<SlotsPaylineCatalog.Line> all = SlotsPaylineCatalog.forGeometry(config.columns(), config.visibleRows());
        if (changedLineNumber < 1 || changedLineNumber > all.size()) {
            return;
        }
        SlotsPaylineCatalog.Line line = all.get(changedLineNumber - 1);
        int columns = config.columns();
        int rows = config.visibleRows();
        // Plain colored tiles, never glinting -- glint is reserved for a
        // ready real Spin and actual matched winning symbols.
        Material material = SlotsLineFlashPlan.materialForChange(added);
        String key = added ? "slots.line-flash-added" : "slots.line-flash-removed";
        int[] path = line.rows();

        final SlotsConfig configAtFlash = config;
        final SlotsUiView viewAtFlash = uiView;
        final long myGeneration = lineFlashGuard.currentGeneration();
        List<SlotsLineFlashPlan.Frame> frames = SlotsLineFlashPlan.frames();
        // Frame 0 (always COLORED, per SlotsLineFlashPlan) paints
        // synchronously, in this same call, immediately after the caller's
        // clean repaint -- no scheduler latency, so the newest change's
        // feedback is genuinely the next thing rendered.
        paintLineFlashFrame(frames.get(0), material, key, changedLineNumber, line, path, columns, rows);
        if (frames.size() > 1) {
            scheduleLineFlashFrame(myGeneration, frames, 1, configAtFlash, viewAtFlash,
                material, key, changedLineNumber, line, path, columns, rows);
        }
    }

    private void paintLineFlashFrame(
        SlotsLineFlashPlan.Frame frame, Material material, String key, int changedLineNumber,
        SlotsPaylineCatalog.Line line, int[] path, int columns, int rows) {

        if (frame == SlotsLineFlashPlan.Frame.COLORED) {
            for (int col = 0; col < columns; col++) {
                addItemAndLore(material, 1, text(key), SlotsGeometry.gridSlot(columns, rows, path[col], col),
                    text("slots.line-flash-number", "line", changedLineNumber),
                    text("slots.line-preview-shape", "shape", text(shapeKey(line))));
            }
        } else {
            repaintCanvas();
        }
    }

    /**
     * Plays {@code frames.get(frameIndex)} one {@link SlotsLineFlashPlan#STEP_TICKS}
     * step after the previous frame, then schedules the next -- each
     * scheduled callback re-checks {@link #lineFlashGuard} via {@link SlotsLineFlashGuard#isStale},
     * {@code closeFlag}, the config, and the {@link SlotsUiView} before
     * painting anything, so a stale frame from a superseded flash can never
     * repaint a closed session, a different geometry, or a different view.
     */
    private void scheduleLineFlashFrame(
        long myGeneration, List<SlotsLineFlashPlan.Frame> frames, int frameIndex,
        SlotsConfig configAtFlash, SlotsUiView viewAtFlash,
        Material material, String key, int changedLineNumber, SlotsPaylineCatalog.Line line,
        int[] path, int columns, int rows) {

        lineFlashTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            lineFlashTask = null;
            if (lineFlashGuard.isStale(myGeneration, closeFlag, configAtFlash, config, viewAtFlash, uiView)) {
                return;
            }
            paintLineFlashFrame(frames.get(frameIndex), material, key, changedLineNumber, line, path, columns, rows);
            int nextIndex = frameIndex + 1;
            if (nextIndex < frames.size()) {
                scheduleLineFlashFrame(myGeneration, frames, nextIndex, configAtFlash, viewAtFlash,
                    material, key, changedLineNumber, line, path, columns, rows);
            }
        }, SlotsLineFlashPlan.STEP_TICKS);
    }

    private void handleChangeDenomination(int direction) {
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        int next = SlotsDenominationPolicy.nextAllowedIndex(
            chipValues, denominationIndex, direction, config.visibleRows(), config.activeLines(), isItemMode(), config.paytable());
        if (next == denominationIndex) {
            denyAction(player, text("slots.no-safe-denomination"));
            return;
        }
        stopAutoSpin();
        denominationIndex = next;
        playClick(direction > 0 ? 1.2f : 0.9f);
        renderControls();
        if (uiView == SlotsUiView.PAYTABLE) {
            // The paytable view shows a hypothetical payout at the current
            // wager -- a wager change must repaint it immediately.
            repaintCanvas();
        }
    }

    /**
     * Every geometry change resets the physical grid to a neutral state and
     * revalidates the current wager -- but never touches {@link #lastWinState},
     * the explicit rule that a harmless setting change must not erase the
     * player's last real result.
     */
    private void onGeometryChanged() {
        cancelLineFlashTask();
        reelDisplay = neutralGrid(config.columns(), config.visibleRows());
        reelScrollPosition = new int[config.columns()];
        lastGridIsDemo = false;
        revalidateDenomination();
    }

    /** If the current denomination is no longer safe under the new geometry, steps to the nearest safe one. */
    private void revalidateDenomination() {
        if (!SlotsDenominationPolicy.isAllowed(
            chipValues[denominationIndex], config.visibleRows(), config.activeLines(), isItemMode(), config.paytable())) {
            denominationIndex = SlotsDenominationPolicy.nextAllowedIndex(
                chipValues, denominationIndex, 1, config.visibleRows(), config.activeLines(), isItemMode(), config.paytable());
        }
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private boolean isItemMode() {
        CurrencyProvider provider = getCurrencyProvider();
        return provider == null || provider.getMode() != CurrencyMode.VAULT;
    }

    // ---- spin lever: paid spin, demo spin, fast-forward -------------------

    private void handleSpinLeverClick(ClickType clickType) {
        if (presentationRunning()) {
            if (clickType == ClickType.LEFT) {
                fastForwardCurrentPresentation();
            }
            // An ordinary right-click while a presentation is running is a
            // safe no-op -- it must never start a second Demo Spin.
            return;
        }
        if (clickType == ClickType.LEFT) {
            if (autoSpinActive) {
                stopAutoSpin();
            }
            returnToGameViewForAction();
            handleSpin(false);
        } else {
            returnToGameViewForAction();
            handleDemoSpin();
        }
    }

    /**
     * @param autoTriggered whether this attempt was started by the Auto Spin
     *     loop rather than a direct player click -- a rejection while
     *     auto-triggered stops the loop (Section 8) rather than just denying
     *     this one spin
     */
    private void handleSpin(boolean autoTriggered) {
        if (!passesOverflowBankGate()) {
            if (autoTriggered) {
                stopAutoSpin();
                renderControls();
            }
            return;
        }
        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            denomUnits,
            config.columns(),
            config.visibleRows(),
            config.activeLines(),
            isItemMode(),
            config.paytable(),
            SlotsRandomSource.production(),
            underwriting,
            this::attemptDebit);

        switch (attempt) {
            case SlotsSpinController.SpinAttempt.Rejected rejected -> {
                handleRejectedSpin(rejected.reason(), rejected.dealerDecision());
                if (autoTriggered && SlotsAutoSpinLifecycle.stopsOn(rejected.reason())) {
                    stopAutoSpin(SlotsAutoSpinRules.StopReason.SPIN_REJECTED);
                    renderControls();
                }
            }
            case SlotsSpinController.SpinAttempt.Accepted accepted -> {
                controller.beginAnimating();
                // The batch ledger only ever moves on real economic events:
                // this wager was actually debited, so it counts, and only
                // now. A rejected attempt never reaches here.
                committedTotalBet = accepted.totalBetUnits();
                if (autoSpinActive) {
                    autoBatch.recordCommittedWager(accepted.totalBetUnits());
                }
                // Last Result names the last COMPLETED real spin, not the
                // in-flight one -- it must keep showing the preceding
                // result throughout this spin's animation (the active-state
                // lever is what tells the player a new spin is running) and
                // only change once this spin actually settles.
                //
                // A still-counting meter from the previous spin is snapped to
                // its exact authoritative payout here, BEFORE controls are
                // painted -- not inside startAnimation(), which runs after
                // renderControls() below. Snapping first and rendering after
                // is what stops the inventory from painting this spin's
                // now-interrupted partial value and leaving it on screen
                // throughout the new spin's reel animation, even though
                // internal state is already correct. A rejected spin (see
                // handleRejectedSpin) never reaches this branch, so it never
                // interrupts a meter.
                cancelWinMeterTask();
                cancelLineFlashTask();
                playLeverPull();
                renderControls();
                startAnimation(
                    new SlotsCallbackGuard.SpinToken(playerId, dealerId, accepted.generation()),
                    accepted.stops());
            }
        }
    }

    private void handleRejectedSpin(
        SlotsSpinController.RejectReason reason, AdmissionDecision dealerDecision) {

        String key = switch (reason) {
            case NOT_READY -> "slots.spin-locked";
            case INVALID_DENOMINATION -> "slots.invalid-denomination";
            case WAGER_OVERFLOW, BET_TOO_LARGE_FOR_MODE -> "slots.bet-too-large";
            case INSUFFICIENT_FUNDS -> "slots.insufficient-funds";
            // The machine is short, not the player. Saying "you cannot afford
            // this" would send them to top up a balance that is already fine.
            // A wager over the machine's fixed tier will never be accepted, so
            // it must not be reported as something to retry later.
            case DEALER_CANNOT_COVER -> dealerDecision == AdmissionDecision.EXCEEDS_RISK_TIER
                ? "slots.dealer-wager-too-large"
                : "slots.dealer-cannot-cover";
        };
        denyAction(player, text(key));
    }

    // ---- demo spin (financially isolated) --------------------

    /**
     * A free practice spin. Uses the real strip/paytable/rounding machinery
     * so the odds shown match reality, but through a source of randomness
     * and a generation counter entirely separate from {@link #controller} --
     * it never calls {@link WagerGate}, never debits or credits currency,
     * never touches the dealer budget, never creates a {@link PendingPayout},
     * and never advances the real spin lifecycle in any way. Rendered
     * directly onto the Game canvas rather than a separate mode.
     */
    private void handleDemoSpin() {
        if (demoActive || presentationRunning()) {
            playDefaultSound(player);
            return;
        }
        if (!controller.isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }

        stopAutoSpin();
        cancelLineFlashTask();
        demoActive = true;
        demoGeneration++;
        long myGeneration = demoGeneration;

        int columns = config.columns();
        int rows = config.visibleRows();
        SlotsSpinGenerator.StripResult result =
            SlotsSpinGenerator.generateFromStrips(columns, rows, demoRng, config.variance());
        SlotsOutcome outcome = result.outcome();

        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        long hypotheticalBet = SlotsMath.totalBetForGeometry(denomUnits, rows, config.activeLines());
        long hypotheticalPayout =
            SlotsMath.totalPayoutForGeometry(outcome, config.activeLines(), denomUnits, config.paytable(), demoRng);

        this.demoOutcome = outcome;
        this.demoHypotheticalBet = hypotheticalBet;
        this.demoHypotheticalPayout = hypotheticalPayout;

        playLeverPull();
        // A real view transition -- e.g. from the Paytable view, which owns
        // every canvas slot -- so the frame/gutters must be repainted before
        // seedReelDisplay paints the grid cells themselves, or stale
        // Paytable items would remain in the gutters. returnToGameViewForAction()
        // has already run this if needed; this call is a harmless idempotent
        // repaint of the current (already-Game) frame.
        renderFrame();
        renderControls();
        startDemoAnimation(myGeneration, outcome, result.stops(), hypotheticalBet, hypotheticalPayout);
    }

    private void startDemoAnimation(
        long myGeneration, SlotsOutcome outcome, int[] stops, long hypotheticalBet, long hypotheticalPayout) {

        cancelDemoAnimationTaskOnly();
        SlotsReelPlan plan = SlotsReelPlan.build(outcome, config.activeLines());
        int columns = outcome.columns();
        int rows = outcome.rows();
        SlotsVariance variance = config.variance();
        SlotsReelStrip[] strips = new SlotsReelStrip[columns];
        for (int col = 0; col < columns; col++) {
            strips[col] = SlotsReelStrip.forReel(variance, col);
        }
        List<SlotsMath.CatalogLineResult> winners = winningLines(outcome);
        boolean[] landed = new boolean[columns];
        // The same rational simulated-tick cadence the paid presentation
        // uses, so a Demo Spin is visibly the same speed as the real thing.
        SlotsReelCadence cadence = SlotsReelCadence.forSpeed(spinSpeed);

        seedReelDisplay(columns, rows, true, strips, plan, stops);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (closeFlag || demoGeneration != myGeneration) {
                    cancel();
                    demoTask = null;
                    return;
                }
                long from = cadence.simulatedTicksElapsed();
                long to = from + cadence.advanceOneRealTick();
                for (long tick = from; tick < to; tick++) {
                    for (int reel = 0; reel < columns; reel++) {
                        if (landed[reel]) {
                            continue;
                        }
                        if (plan.isStopped(reel, tick)) {
                            landed[reel] = true;
                            lockReelToStop(columns, rows, true, reel, strips[reel], stops[reel]);
                            playReelStop(reel, columns);
                            continue;
                        }
                        if (plan.advancesAt(reel, tick)) {
                            advanceReelAlongStrip(columns, rows, true, reel, strips[reel]);
                        }
                    }

                    if (tick >= plan.revealStartTick()) {
                        long sinceReveal = tick - plan.revealStartTick();
                        if (sinceReveal == 0) {
                            paintOutcomeGrid(outcome, true);
                            for (SlotsMath.CatalogLineResult win : winners) {
                                highlightLine(win, outcome, true);
                            }
                            if (!winners.isEmpty()) {
                                playFinale(hypotheticalPayout);
                            } else {
                                playLoss();
                            }
                        }
                        if (sinceReveal >= DEMO_FINALE_HOLD_TICKS) {
                            cancel();
                            demoTask = null;
                            finishDemoSpin(myGeneration, hypotheticalBet, hypotheticalPayout);
                            return;
                        }
                    }
                }
            }
        };
        demoTask = runnable.runTaskTimer(plugin, SlotsTiming.TICK_INTERVAL, SlotsTiming.TICK_INTERVAL);
    }

    private void finishDemoSpin(long myGeneration, long hypotheticalBet, long hypotheticalPayout) {
        if (demoGeneration != myGeneration) {
            return;
        }
        demoActive = false;
        String key = hypotheticalPayout > 0 ? "slots.demo-result-win" : "slots.demo-result-loss";
        player.sendMessage(text(key,
            "bet", plugin.formatWagerDisplay(currencyMode, currencyName, hypotheticalBet),
            "amount", plugin.formatWagerDisplay(currencyMode, currencyName, hypotheticalPayout)));
        renderControls();
    }

    /** Cancels only the running demo animation task, without bumping the generation -- see {@link #cancelDemoTask}. */
    private void cancelDemoAnimationTaskOnly() {
        if (demoTask != null) {
            demoTask.cancel();
            demoTask = null;
        }
    }

    // ---- fast-forward (Section 9) -----------------------------------

    /**
     * Fast-forwards whichever presentation is currently running (paid or
     * Demo Spin) straight to its already-determined result: cancels the
     * remaining reel/reveal/count-up callbacks, paints the exact committed
     * result, and performs normal settlement exactly once for a paid spin
     * (or the equivalent finish for a demo). Never redraws, regenerates,
     * rerolls, or replaces the result, and never duplicates settlement.
     */
    private void fastForwardCurrentPresentation() {
        if (animationTask != null) {
            fastForwardPaidSpin();
        } else if (demoTask != null) {
            fastForwardDemoSpin();
        }
    }

    private void fastForwardPaidSpin() {
        cancelAnimationTask();
        SlotsOutcome outcome = controller.currentOutcome();
        if (outcome == null) {
            return;
        }
        List<SlotsMath.CatalogLineResult> winners = winningLines(outcome);
        paintOutcomeGrid(outcome, false);
        for (SlotsMath.CatalogLineResult win : winners) {
            highlightLine(win, outcome, false);
        }
        if (!winners.isEmpty()) {
            playFinale(controller.pendingPayoutAmount());
        } else {
            playLoss();
        }
        // Same generation as the in-flight spin: nothing new was committed,
        // so a token built from the controller's current generation is valid
        // for exactly the spin being fast-forwarded.
        SlotsCallbackGuard.SpinToken token =
            new SlotsCallbackGuard.SpinToken(playerId, dealerId, controller.generation());
        settle(token);
    }

    private void fastForwardDemoSpin() {
        long myGeneration = demoGeneration;
        cancelDemoAnimationTaskOnly();
        if (demoOutcome == null) {
            demoActive = false;
            renderControls();
            return;
        }
        List<SlotsMath.CatalogLineResult> winners = winningLines(demoOutcome);
        paintOutcomeGrid(demoOutcome, true);
        for (SlotsMath.CatalogLineResult win : winners) {
            highlightLine(win, demoOutcome, true);
        }
        if (!winners.isEmpty()) {
            playFinale(demoHypotheticalPayout);
        } else {
            playLoss();
        }
        finishDemoSpin(myGeneration, demoHypotheticalBet, demoHypotheticalPayout);
    }

    // ---- auto spin ----------------------------------------------------

    /**
     * The Clock's three actions. Shift-right-click is routed ahead of this in
     * {@link #handleClick}, because the ordinary-click gate would otherwise
     * swallow it.
     */
    private void handleClockClick(ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            handleAutoSpinToggle();
        } else if (clickType == ClickType.RIGHT) {
            handleSpeedCycle();
        }
    }

    private void handleAutoSpinToggle() {
        if (autoSpinActive) {
            // Must remain clickable even while other controls are locked --
            // stopping never touches the currently running/committed spin.
            stopAutoSpin();
            playClick(0.8f);
            renderControls();
            return;
        }
        if (!controller.isReadyForSpin() || demoActive) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        returnToGameViewForAction();
        startAutoSpinBatch();
    }

    /**
     * Starts one Auto Spin batch: a fresh ledger, a frozen snapshot of the
     * settings it will run under, and the first spin down the ordinary paid
     * path. There is deliberately no second economic spin implementation --
     * Auto Spin only ever decides <em>whether</em> to call
     * {@link #handleSpin(boolean)}, never how a spin works.
     */
    private void startAutoSpinBatch() {
        autoBatch.reset();
        activeBatchSettings = autoSettings;
        autoSpinActive = true;
        playClick(1.2f);
        renderControls();

        SlotsAutoSpinRules.StopReason blocked =
            SlotsAutoSpinRules.beforeNextSpin(activeBatchSettings, autoBatch, currentTotalBet());
        if (blocked != null) {
            stopAutoSpin(blocked);
            renderControls();
            return;
        }
        handleSpin(true);
    }

    /** SLOW -&gt; NORMAL -&gt; FAST -&gt; SLOW. Never an Auto Spin setting: speed is owned by this control alone. */
    private void handleSpeedCycle() {
        spinSpeed = spinSpeed.next();
        playClick(switch (spinSpeed) {
            case SLOW -> 0.7f;
            case NORMAL -> 1.0f;
            case FAST -> 1.3f;
        });
        renderControls();
    }

    /**
     * Stops the automatic loop from starting its next wager. Never cancels
     * or refunds a spin whose outcome is already committed -- that spin (if
     * any) simply finishes and settles on its own schedule, exactly as it
     * would have without Auto Spin. Idempotent and safe to call whether or
     * not Auto Spin is currently active.
     */
    private void stopAutoSpin() {
        stopAutoSpin(null);
    }

    /**
     * @param reason the stop rule that fired, so the player is told
     *     specifically why the batch ended; {@code null} for an ordinary
     *     manual stop or a view change, which needs no message
     */
    private void stopAutoSpin(SlotsAutoSpinRules.StopReason reason) {
        boolean wasActive = autoSpinActive;
        autoSpinActive = false;
        if (autoSpinTask != null) {
            autoSpinTask.cancel();
            autoSpinTask = null;
        }
        if (wasActive && reason != null && !closeFlag) {
            player.sendMessage(text(reason.messageKey()));
        }
        if (!autoSpinActive) {
            // The batch is over: its ledger must never be judged against a
            // later batch's limits.
            autoBatch.reset();
        }
    }

    /** Called once a spin's settlement has just finished -- schedules the next automatic wager, or stops the loop. */
    private void continueAutoSpinIfNeeded(SlotsSettlementResult result, long payout) {
        if (!autoSpinActive) {
            return;
        }
        // Awarded returns are what the ledger tracks: a delivered or durably
        // queued payout is awarded under this game's settlement semantics; a
        // FAILED settlement awards nothing and stops the batch below.
        if (result != SlotsSettlementResult.FAILED) {
            autoBatch.recordAward(payout);
        }
        SlotsAutoSpinRules.StopReason stop = SlotsAutoSpinRules.afterSettlement(
            activeBatchSettings, autoBatch, committedTotalBet, payout, result);
        if (stop != null) {
            stopAutoSpin(stop);
            renderControls();
            return;
        }
        scheduleNextAutoSpin();
    }

    private void scheduleNextAutoSpin() {
        long delay = spinSpeed.scaled(SlotsTiming.AUTO_SPIN_GAP_TICKS);
        autoSpinTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            autoSpinTask = null;
            if (closeFlag || !autoSpinActive) {
                return;
            }
            // Re-checked immediately before committing, not only after the
            // previous settlement: the loss limit in particular must stop the
            // loop BEFORE it knowingly overshoots by one more wager.
            SlotsAutoSpinRules.StopReason blocked =
                SlotsAutoSpinRules.beforeNextSpin(activeBatchSettings, autoBatch, currentTotalBet());
            if (blocked != null) {
                stopAutoSpin(blocked);
                renderControls();
                return;
            }
            handleSpin(true);
        }, delay);
    }

    // ---- auto spin settings menu ---------------------------------------

    /**
     * Shift-right-clicking the Clock. Any running Auto Spin is stopped --
     * which only prevents the <em>next</em> wager; a spin already committed
     * finishes and settles exactly as it would have.
     */
    private void handleOpenAutoSettings() {
        // The one control that deliberately stops the batch before checking
        // whether the menu can open: a running batch schedules its next
        // wager as soon as the previous spin settles, so the only reliable
        // moment the player can reach this click is while a committed spin
        // is still playing out -- and that spin must (and does) finish and
        // settle untouched.
        boolean wasRunning = autoSpinActive;
        stopAutoSpin();
        if (!canOpenModalView()) {
            if (wasRunning) {
                // The Clock must never keep advertising a batch that this
                // very click has already ended.
                renderControls();
            }
            return;
        }
        switchView(SlotsUiView.AUTO_SETTINGS, 1.1f);
    }

    private void handleAutoSettingsClick(int slot) {
        SlotsAutoSettingsLayout.Entry entry = SlotsAutoSettingsLayout.entryAt(slot);
        if (entry == null) {
            return;
        }
        switch (entry) {
            // The overview card is a label, not a control.
            case OVERVIEW -> {
            }
            case SPIN_LIMIT -> beginAutoSettingPrompt(SlotsChatPrompt.Type.SPIN_LIMIT);
            case STOP_ON_ANY_WIN -> {
                autoSettings = autoSettings.toggleStopOnAnyWin();
                playClick(autoSettings.stopOnAnyWin() ? 1.3f : 0.8f);
                redrawEverything();
            }
            case BIG_WIN_MULTIPLIER -> beginAutoSettingPrompt(SlotsChatPrompt.Type.BIG_WIN_MULTIPLIER);
            case START -> handleStartAutoSpinFromSettings();
            case PROFIT_TARGET -> beginAutoSettingPrompt(SlotsChatPrompt.Type.PROFIT_TARGET);
            case LOSS_LIMIT -> beginAutoSettingPrompt(SlotsChatPrompt.Type.LOSS_LIMIT);
            case RESET -> {
                // Restores exactly the Auto Spin defaults. The gameplay spin
                // speed is not an Auto Spin setting and is deliberately
                // untouched here.
                autoSettings = SlotsAutoSpinSettings.defaults();
                playClick(0.9f);
                player.sendMessage(text("slots.auto-settings-reset-done"));
                redrawEverything();
            }
        }
    }

    /** Start Auto Spin returns to Game View first, so the first spin is never played behind the menu. */
    private void handleStartAutoSpinFromSettings() {
        if (!canOpenModalView()) {
            return;
        }
        switchView(SlotsUiView.GAME, 1.2f);
        startAutoSpinBatch();
    }

    // ---- saved profiles ------------------------------------------------

    private SlotsProfileStore profileStore() {
        return plugin.getSlotsProfileStore();
    }

    private List<SlotsProfile> savedProfiles() {
        SlotsProfileStore store = profileStore();
        return store == null ? List.of() : store.profilesFor(playerId);
    }

    private int profileCount() {
        SlotsProfileStore store = profileStore();
        return store == null ? 0 : store.countFor(playerId);
    }

    /** Exactly what this machine currently has selected, ready to be stored under a name. */
    private SlotsProfile snapshotProfile(String name) {
        return new SlotsProfile(
            name,
            config.visibleRows(),
            config.columns(),
            config.activeLines(),
            chipValues[denominationIndex],
            spinSpeed,
            autoSettings);
    }

    private void handleProfilesControlClick(ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            beginProfileSave();
            return;
        }
        // Right-click opens the list -- but only when there is a list. With
        // no profiles saved, the control advertises only the left-click save.
        if (profileCount() <= 0) {
            playDefaultSound(player);
            return;
        }
        handleOpenProfiles();
    }

    private void handleOpenProfiles() {
        if (!canOpenModalView()) {
            return;
        }
        switchView(SlotsUiView.PROFILES, 1.1f);
    }

    private void handleProfilesEntryClick(int slot, ClickType clickType) {
        SlotsProfileStore store = profileStore();
        if (store == null) {
            return;
        }
        SlotsProfile profile = store.profileAt(playerId, slot);
        if (profile == null) {
            // An empty position in the list: genuinely nothing here.
            return;
        }
        if (clickType == ClickType.LEFT) {
            loadProfile(profile);
        } else {
            deleteProfile(store, slot, profile);
        }
    }

    /** Immediate deletion, no confirmation dialog; the list compacts and repaints in place. */
    private void deleteProfile(SlotsProfileStore store, int slot, SlotsProfile profile) {
        if (!store.deleteAt(playerId, slot)) {
            denyAction(player, text("slots.profile-delete-failed", "name", profile.name()));
            return;
        }
        playClick(0.7f);
        player.sendMessage(text("slots.profile-deleted", "name", profile.name()));
        redrawEverything();
    }

    /**
     * Loads a profile onto <em>this</em> machine, normalizing every saved
     * value first: a profile carries no dealer identity, so its geometry may
     * not be supported here and its per-line wager may not exist (or may not
     * be safe) on this dealer's chip ladder. The wager fallback is always
     * downward -- see {@link SlotsProfileNormalizer} -- so loading can never
     * silently increase the player's exposure, and the player is told
     * whenever anything had to be adjusted.
     */
    private void loadProfile(SlotsProfile profile) {
        if (!canOpenModalView()) {
            return;
        }
        int targetColumns = SlotsGeometry.normalizeColumnCount(profile.reels());
        SlotsPaytable targetPaytable =
            SlotsPaytable.forConfig(targetColumns, config.houseEdge(), config.variance());
        SlotsProfileNormalizer.Fitted fitted = SlotsProfileNormalizer.fit(
            profile, chipValues, denominationIndex, isItemMode(), targetPaytable);

        config = config.withColumns(fitted.reels())
            .withVisibleRows(fitted.height())
            .withActiveLines(fitted.paylines());
        denominationIndex = fitted.denominationIndex();
        spinSpeed = profile.spinSpeed();
        autoSettings = profile.autoSettings();
        onGeometryChanged();

        player.sendMessage(text("slots.profile-loaded", "name", profile.name()));
        if (fitted.adjusted()) {
            player.sendMessage(text("slots.profile-adjusted",
                "rows", config.visibleRows(),
                "columns", config.columns(),
                "lines", config.activeLines(),
                "amount", plugin.formatWagerDisplay(
                    currencyMode, currencyName, chipValues[denominationIndex])));
        }
        // Straight back to the reels, repainted immediately -- never the
        // opening animation, which belongs to a genuine Slots open alone.
        switchView(SlotsUiView.GAME, 1.2f);
    }

    // ---- chat prompts --------------------------------------------------

    /** One prompt type's parse-and-apply step; see {@link SlotsChatPrompt.Handler#submit}. */
    private interface PromptParser {
        SlotsChatPrompt.Outcome parse(SlotsChatPrompt prompt, String input);
    }

    private SlotsChatPromptService promptService() {
        return plugin.getSlotsChatPromptService();
    }

    /** Whether {@code generation} still identifies this session's live prompt. */
    private boolean isCurrentPrompt(long generation) {
        return !closeFlag && promptGeneration == generation;
    }

    /**
     * Opens one chat prompt: suspends this Slots session (the inventory is
     * closed, but the session is deliberately <em>not</em> terminated),
     * privately explains the rules, and hands ownership of the player's chat
     * to the shared {@link SlotsChatPromptService}.
     */
    private void beginPrompt(SlotsChatPrompt.Type type, SlotsUiView returnView, PromptParser parser) {
        SlotsChatPromptService service = promptService();
        if (service == null) {
            denyAction(player, text("slots.prompt-unavailable"));
            return;
        }
        promptGeneration++;
        final long myGeneration = promptGeneration;
        // The handler is constructed with the prompt, so it reaches its own
        // prompt (for the duplicate-name overwrite sub-state) through this
        // holder rather than by looking it up again.
        final SlotsChatPrompt[] self = new SlotsChatPrompt[1];
        SlotsChatPrompt prompt = new SlotsChatPrompt(
            playerId, type, SlotsChatPromptService.deadlineFromNow(), returnView, this, myGeneration,
            new SlotsChatPrompt.Handler() {
                @Override
                public boolean isSessionValid() {
                    return isCurrentPrompt(myGeneration)
                        && SessionRegistry.isRegistered(playerId, SlotsMachine.this)
                        && player != null
                        && player.isOnline();
                }

                @Override
                public SlotsChatPrompt.Outcome submit(String input) {
                    if (!isCurrentPrompt(myGeneration)) {
                        return SlotsChatPrompt.Outcome.CANCELLED;
                    }
                    if (SlotsPromptValues.isCancel(input)) {
                        return SlotsChatPrompt.Outcome.CANCELLED;
                    }
                    return parser.parse(self[0], input);
                }

                @Override
                public void accepted() {
                    if (!isCurrentPrompt(myGeneration)) {
                        return;
                    }
                    resumeFromPrompt(returnView);
                }

                @Override
                public void cancelled() {
                    if (!isCurrentPrompt(myGeneration)) {
                        return;
                    }
                    player.sendMessage(text("slots.prompt-cancelled"));
                    resumeFromPrompt(returnView);
                }

                @Override
                public void timedOut() {
                    if (!isCurrentPrompt(myGeneration)) {
                        return;
                    }
                    promptTimedOut();
                }

                @Override
                public void ended(SlotsChatPrompt.EndReason reason) {
                    if (!isCurrentPrompt(myGeneration)) {
                        // A newer prompt, or a torn-down session, already owns
                        // this state -- a stale callback must change nothing.
                        return;
                    }
                    promptEnded(reason);
                }
            });
        self[0] = prompt;

        // Set before the inventory closes, so the close is already recognized
        // as a suspension rather than an exit.
        promptSuspended = true;
        stopAutoSpin();
        cancelLineFlashTask();
        service.begin(prompt);
        sendPromptInstructions(prompt);
        player.closeInventory();
    }

    private void sendPromptInstructions(SlotsChatPrompt prompt) {
        player.sendMessage(text(prompt.instructionKey()));
        player.sendMessage(text("slots.prompt-deadline", "seconds", SlotsChatPromptService.TIMEOUT_SECONDS));
        player.sendMessage(text("slots.prompt-cancel-hint", "cancel", SlotsPromptValues.CANCEL));
        player.sendMessage(text("slots.prompt-another-game-warning"));
    }

    /** Reopens the machine directly into {@code view}, with no opening animation. */
    private void resumeFromPrompt(SlotsUiView view) {
        promptGeneration++;
        promptSuspended = false;
        pendingProfileSnapshot = null;
        if (closeFlag || player == null || !player.isOnline()) {
            return;
        }
        uiView = view;
        if (view == SlotsUiView.GAME) {
            restorePlayCanvas();
        }
        redrawEverything();
        player.openInventory(getInventory());
    }

    /** The deadline expired: the suspended session ends cleanly and is never reopened. */
    private void promptTimedOut() {
        promptGeneration++;
        promptSuspended = false;
        pendingProfileSnapshot = null;
        if (player != null && player.isOnline()) {
            player.sendMessage(text("slots.prompt-timed-out"));
        }
        terminateSuspendedSession(ExitReason.VOLUNTARY_INVENTORY_CLOSE);
    }

    private void promptEnded(SlotsChatPrompt.EndReason reason) {
        promptGeneration++;
        promptSuspended = false;
        pendingProfileSnapshot = null;
        switch (reason) {
            case ANOTHER_GAME_OPENED -> {
                if (player != null && player.isOnline()) {
                    player.sendMessage(text("slots.prompt-another-game-cancelled"));
                }
                terminateSuspendedSession(ExitReason.VOLUNTARY_INVENTORY_CLOSE);
            }
            case DISCONNECTED -> terminateSuspendedSession(ExitReason.DISCONNECTED);
            case SESSION_ENDED -> terminateSuspendedSession(ExitReason.VOLUNTARY_INVENTORY_CLOSE);
            case TIMED_OUT -> promptTimedOut();
            // A newer prompt for this same session took over; it now owns the
            // suspension and will reopen the inventory itself.
            case SUPERSEDED -> {
            }
        }
    }

    private void terminateSuspendedSession(ExitReason reason) {
        if (closeFlag) {
            return;
        }
        SessionRegistry.terminateSession(playerId, this, reason);
    }

    // ---- profile naming prompt -----------------------------------------

    private void beginProfileSave() {
        SlotsProfileStore store = profileStore();
        if (store == null) {
            denyAction(player, text("slots.prompt-unavailable"));
            return;
        }
        if (store.isFullFor(playerId)) {
            denyAction(player, text("slots.profiles-full",
                "max", SlotsProfileStore.MAX_PROFILES_PER_PLAYER));
            return;
        }
        if (!canOpenModalView()) {
            return;
        }
        // Captured now, before the inventory closes, so the profile stores
        // exactly what the player was looking at when they pressed save.
        pendingProfileSnapshot = snapshotProfile("pending");
        beginPrompt(SlotsChatPrompt.Type.PROFILE_NAME, SlotsUiView.GAME, this::submitProfileName);
    }

    private SlotsChatPrompt.Outcome submitProfileName(SlotsChatPrompt prompt, String input) {
        SlotsProfileStore store = profileStore();
        if (store == null || pendingProfileSnapshot == null || prompt == null) {
            return SlotsChatPrompt.Outcome.CANCELLED;
        }

        String awaiting = prompt.pendingOverwriteName();
        if (awaiting != null) {
            if (SlotsPromptValues.isOverwrite(input)) {
                return finishProfileSave(prompt, store, awaiting, true);
            }
            // Anything else re-asks, without restarting the original deadline.
            player.sendMessage(text("slots.profile-overwrite-retry",
                "name", awaiting, "overwrite", SlotsPromptValues.OVERWRITE, "cancel", SlotsPromptValues.CANCEL));
            return SlotsChatPrompt.Outcome.RETRY;
        }

        SlotsProfileName.Rejection rejection = SlotsProfileName.validate(input);
        if (rejection != null) {
            player.sendMessage(text(rejection.messageKey(),
                "min", SlotsProfileName.MIN_LENGTH, "max", SlotsProfileName.MAX_LENGTH));
            sendRetryHint(prompt);
            return SlotsChatPrompt.Outcome.RETRY;
        }

        String name = SlotsProfileName.normalize(input);
        if (store.hasProfileNamed(playerId, name)) {
            prompt.awaitOverwriteConfirmation(name);
            player.sendMessage(text("slots.profile-duplicate",
                "name", name, "overwrite", SlotsPromptValues.OVERWRITE, "cancel", SlotsPromptValues.CANCEL));
            return SlotsChatPrompt.Outcome.RETRY;
        }
        return finishProfileSave(prompt, store, name, false);
    }

    private SlotsChatPrompt.Outcome finishProfileSave(
        SlotsChatPrompt prompt, SlotsProfileStore store, String name, boolean overwrite) {

        SlotsProfileStore.SaveResult result =
            store.save(playerId, pendingProfileSnapshot.renamed(name), overwrite);
        switch (result) {
            case SAVED -> {
                player.sendMessage(text("slots.profile-saved", "name", name));
                return SlotsChatPrompt.Outcome.ACCEPTED;
            }
            case OVERWROTE -> {
                player.sendMessage(text("slots.profile-overwritten", "name", name));
                return SlotsChatPrompt.Outcome.ACCEPTED;
            }
            case DUPLICATE -> {
                prompt.awaitOverwriteConfirmation(name);
                player.sendMessage(text("slots.profile-duplicate",
                    "name", name, "overwrite", SlotsPromptValues.OVERWRITE, "cancel", SlotsPromptValues.CANCEL));
                return SlotsChatPrompt.Outcome.RETRY;
            }
            case FULL -> {
                player.sendMessage(text("slots.profiles-full",
                    "max", SlotsProfileStore.MAX_PROFILES_PER_PLAYER));
                return SlotsChatPrompt.Outcome.CANCELLED;
            }
            default -> {
                player.sendMessage(text("slots.profile-save-failed"));
                return SlotsChatPrompt.Outcome.CANCELLED;
            }
        }
    }

    // ---- auto setting prompts ------------------------------------------

    private void beginAutoSettingPrompt(SlotsChatPrompt.Type type) {
        beginPrompt(type, SlotsUiView.AUTO_SETTINGS, this::submitAutoSetting);
    }

    private SlotsChatPrompt.Outcome submitAutoSetting(SlotsChatPrompt prompt, String input) {
        if (prompt == null) {
            return SlotsChatPrompt.Outcome.CANCELLED;
        }
        if (prompt.type() == SlotsChatPrompt.Type.SPIN_LIMIT) {
            return submitSpinLimit(prompt, input);
        }
        SlotsPromptValues.Amount parsed = SlotsPromptValues.parsePositiveAmount(input);
        if (parsed.kind() == SlotsPromptValues.Kind.CANCEL) {
            return SlotsChatPrompt.Outcome.CANCELLED;
        }
        if (parsed.kind() == SlotsPromptValues.Kind.INVALID) {
            player.sendMessage(text(prompt.type() == SlotsChatPrompt.Type.BIG_WIN_MULTIPLIER
                ? "slots.prompt-invalid-multiplier"
                : "slots.prompt-invalid-amount"));
            sendRetryHint(prompt);
            return SlotsChatPrompt.Outcome.RETRY;
        }
        boolean off = parsed.kind() == SlotsPromptValues.Kind.OFF;
        double value = off ? 0.0 : parsed.value();
        switch (prompt.type()) {
            case BIG_WIN_MULTIPLIER -> {
                autoSettings = autoSettings.withBigWinMultiplier(value);
                player.sendMessage(off
                    ? text("slots.auto-big-win-off-set")
                    : text("slots.auto-big-win-set", "multiplier", formatMultiplier(value)));
            }
            case PROFIT_TARGET -> {
                autoSettings = autoSettings.withProfitTarget(value);
                player.sendMessage(off
                    ? text("slots.auto-profit-target-off-set")
                    : text("slots.auto-profit-target-set", "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, value)));
            }
            case LOSS_LIMIT -> {
                autoSettings = autoSettings.withLossLimit(value);
                player.sendMessage(off
                    ? text("slots.auto-loss-limit-off-set")
                    : text("slots.auto-loss-limit-set", "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, value)));
            }
            default -> {
                return SlotsChatPrompt.Outcome.CANCELLED;
            }
        }
        return SlotsChatPrompt.Outcome.ACCEPTED;
    }

    private SlotsChatPrompt.Outcome submitSpinLimit(SlotsChatPrompt prompt, String input) {
        SlotsPromptValues.SpinLimit parsed = SlotsPromptValues.parseSpinLimit(input);
        switch (parsed.kind()) {
            case CANCEL -> {
                return SlotsChatPrompt.Outcome.CANCELLED;
            }
            case UNLIMITED -> {
                autoSettings = autoSettings.withSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS);
                player.sendMessage(text("slots.auto-spin-limit-unlimited-set"));
                return SlotsChatPrompt.Outcome.ACCEPTED;
            }
            case VALUE -> {
                autoSettings = autoSettings.withSpinLimit(parsed.value());
                player.sendMessage(text("slots.auto-spin-limit-set", "spins", parsed.value()));
                return SlotsChatPrompt.Outcome.ACCEPTED;
            }
            default -> {
                player.sendMessage(text("slots.prompt-invalid-spin-limit",
                    "unlimited", SlotsPromptValues.UNLIMITED));
                sendRetryHint(prompt);
                return SlotsChatPrompt.Outcome.RETRY;
            }
        }
    }

    /** Tells the player they may try again, and exactly how much of the original deadline is left. */
    private void sendRetryHint(SlotsChatPrompt prompt) {
        player.sendMessage(text("slots.prompt-retry",
            "seconds", prompt.remainingSeconds(System.currentTimeMillis())));
    }

    // ---- shared overflow-bank / debit gate --------------------------------

    /**
     * The universal pre-wager overflow-bank gate.
     *
     * <p>Any nonzero banked balance blocks every NCCasino wager, whatever
     * currency it is in -- banked emeralds block a diamond spin just as much
     * as a diamond balance would. An automatic delivery attempt runs first,
     * so a player who has since made room simply plays on without noticing
     * the gate at all; only a balance that still cannot fit rejects the
     * spin, before any wager is withdrawn.
     */
    private boolean passesOverflowBankGate() {
        // Checked here, before trySpin, so a blocked player never reaches the
        // debit OR the random outcome generation. attemptDebit re-checks as
        // defence in depth; this call is what keeps the rejection clean.
        return WagerGate.allowsWager(plugin, player);
    }

    private boolean attemptDebit(long totalBetUnits) {
        // Universal overflow-bank gate: any banked balance, in any currency,
        // blocks every new wager. Checked here -- the single point money
        // actually leaves the player -- so no betting path can bypass it.
        if (!WagerGate.allowsWager(plugin, player)) {
            return false;
        }
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return WagerTransaction.tryWithdraw(provider, player, internalName, (double) totalBetUnits);
        }
        return tryWithdrawRawMaterial(totalBetUnits);
    }

    private boolean tryWithdrawRawMaterial(long amountUnits) {
        Material mat = plugin.getCurrency(internalName);
        if (mat == null || amountUnits <= 0 || amountUnits > Integer.MAX_VALUE) {
            return false;
        }
        int amount = (int) amountUnits;
        ItemStack required = new ItemStack(mat, amount);
        if (!player.getInventory().containsAtLeast(required, amount)) {
            return false;
        }
        player.getInventory().removeItem(required);
        return true;
    }

    // ---- animation -------------------------------------------------------

    /**
     * Traverses each reel's actual {@link SlotsReelStrip} sequence, landing
     * exactly on the stop {@code committedStops} already fixed at debit
     * time -- see {@link #seedReelDisplay} for how every visible transition,
     * including the last one, is a real +1 step rather than a snap.
     *
     * <p>There is no cosmetic fallback: a paid spin's committed stops are
     * fixed synchronously at debit time by {@link SlotsSpinController}, so
     * missing or malformed stops here means the caller is broken, not that
     * this spin should silently pretend to be a physical reel using
     * unrelated cells. Fail fast instead.
     *
     * <p>Spin Speed never alters the reel-motion schedule itself
     * ({@link SlotsReelPlan}), so every ordering, landing, and reveal
     * guarantee stays exactly as designed. It changes only how many of those
     * already-decided simulated ticks each real server tick plays out, via
     * the exact rational cadence in {@link SlotsReelCadence}: 2 per real tick
     * at FAST, 1 at NORMAL, and 2 every 3 real ticks at SLOW. A real tick
     * that plays out zero simulated ticks is what makes SLOW expressible at
     * all -- the earlier integer step could only ever be 1 or more, so it
     * could only speed the presentation up.
     */
    private void startAnimation(SlotsCallbackGuard.SpinToken token, int[] committedStops) {
        cancelAnimationTask();
        SlotsOutcome outcome = controller.currentOutcome();
        if (committedStops == null || committedStops.length != outcome.columns()) {
            throw new IllegalStateException(
                "Slots animation requires one committed stop per reel; got "
                    + (committedStops == null ? "null" : committedStops.length)
                    + " for " + outcome.columns() + " columns");
        }
        final SlotsReelPlan plan = SlotsReelPlan.build(outcome, config.activeLines());
        final int columns = outcome.columns();
        final int rows = outcome.rows();
        final SlotsVariance variance = config.variance();
        final SlotsReelStrip[] strips = new SlotsReelStrip[columns];
        for (int col = 0; col < columns; col++) {
            strips[col] = SlotsReelStrip.forReel(variance, col);
        }
        final List<SlotsMath.CatalogLineResult> winners = winningLines(outcome);
        final boolean[] landed = new boolean[columns];
        final boolean[] anticipationAnnounced = {false};
        final int[] revealIndex = {0};
        // Captured once, at animation start: a speed change mid-presentation
        // must never retime a spin that is already playing out.
        final SlotsReelCadence cadence = SlotsReelCadence.forSpeed(spinSpeed);

        seedReelDisplay(columns, rows, false, strips, plan, committedStops);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!SlotsCallbackGuard.isValid(token, playerId, dealerId, controller.generation())) {
                    cancel();
                    animationTask = null;
                    return;
                }
                long from = cadence.simulatedTicksElapsed();
                long to = from + cadence.advanceOneRealTick();
                for (long tick = from; tick < to; tick++) {
                    for (int reel = 0; reel < columns; reel++) {
                        if (landed[reel]) {
                            continue;
                        }
                        if (plan.isStopped(reel, tick)) {
                            landed[reel] = true;
                            lockReelToStop(columns, rows, false, reel, strips[reel], committedStops[reel]);
                            playReelStop(reel, columns);
                            continue;
                        }
                        if (plan.advancesAt(reel, tick)) {
                            advanceReelAlongStrip(columns, rows, false, reel, strips[reel]);
                        }
                    }

                    if (!anticipationAnnounced[0]
                        && plan.isAnticipated()
                        && columns >= 2
                        && landed[columns - 2]
                        && !landed[columns - 1]) {
                        anticipationAnnounced[0] = true;
                        playAnticipation();
                    }

                    if (tick >= plan.revealStartTick()) {
                        long sinceReveal = tick - plan.revealStartTick();
                        long perLine = SlotsTiming.LINE_REVEAL_HOLD_TICKS + SlotsTiming.LINE_REVEAL_GAP_TICKS;

                        if (winners.isEmpty()) {
                            if (sinceReveal >= SlotsTiming.LOSS_SETTLE_TICKS) {
                                cancel();
                                animationTask = null;
                                settle(token);
                                return;
                            }
                        } else if (revealIndex[0] < winners.size()) {
                            if (sinceReveal >= revealIndex[0] * perLine) {
                                SlotsMath.CatalogLineResult win = winners.get(revealIndex[0]);
                                revealIndex[0]++;
                                paintOutcomeGrid(outcome, false);
                                highlightLine(win, outcome, false);
                                playLineReveal(revealIndex[0], winners.size());
                            }
                        } else {
                            long finaleStart = winners.size() * perLine;
                            if (sinceReveal == finaleStart) {
                                paintOutcomeGrid(outcome, false);
                                for (SlotsMath.CatalogLineResult win : winners) {
                                    highlightLine(win, outcome, false);
                                }
                                playFinale(controller.pendingPayoutAmount());
                            }
                            if (sinceReveal >= finaleStart + SlotsTiming.ALL_LINES_FINALE_TICKS) {
                                cancel();
                                animationTask = null;
                                settle(token);
                                return;
                            }
                        }
                    }
                }
            }
        };
        animationTask = runnable.runTaskTimer(plugin, SlotsTiming.TICK_INTERVAL, SlotsTiming.TICK_INTERVAL);
    }

    private List<SlotsMath.CatalogLineResult> winningLines(SlotsOutcome outcome) {
        List<SlotsMath.CatalogLineResult> winners = new ArrayList<>();
        for (SlotsMath.CatalogLineResult result : SlotsMath.evaluateActiveCatalogLines(outcome, config.activeLines(), config.paytable())) {
            if (result.winning()) {
                winners.add(result);
            }
        }
        return winners;
    }

    /**
     * Seeds every reel's cosmetic starting position so its scheduled
     * advances land it <em>naturally</em> on the committed stop -- never a
     * snap. For a reel with {@code plan.advanceCount(col)} scheduled
     * advances and committed stop {@code committedStops[col]}, starting at
     * {@code floorMod(committedStop + advanceCount, SIZE)} means the last of
     * those advances lands exactly on the committed stop, because every
     * intervening advance is a real -1 step along that reel's own circular
     * strip (see {@link SlotsReelPlan#advanceCount}). The traversal runs
     * backward (seed ahead of the stop, then decrement) rather than forward
     * so the visible motion is a real symbol entering the top and every
     * existing symbol shifting one row down -- see {@link #advanceReelAlongStrip}.
     *
     * @param demo whether this animation is a Demo Spin -- captured once at
     *     animation start and threaded through explicitly, rather than
     *     re-read from a live view flag on every repaint, so a mid-animation
     *     repaint can never disagree with which animation is actually
     *     running. Also updates {@link #lastGridIsDemo} so the idle repaint
     *     after this animation ends knows whether to keep showing the demo
     *     disclaimer.
     */
    private void seedReelDisplay(
        int columns, int rows, boolean demo, SlotsReelStrip[] strips, SlotsReelPlan plan, int[] committedStops) {

        lastGridIsDemo = demo;
        reelDisplay = new SlotsSymbol[columns][rows];
        reelScrollPosition = new int[columns];
        for (int col = 0; col < columns; col++) {
            int seedPosition = Math.floorMod(committedStops[col] + plan.advanceCount(col), SlotsReelStrip.SIZE);
            reelScrollPosition[col] = seedPosition;
            paintReelWindow(columns, rows, demo, col, strips[col].window(seedPosition, rows));
        }
    }

    /**
     * Scrolls one reel backward by exactly one stop along its own real
     * strip -- {@link SlotsReelStrip#window} indexes top-to-bottom from
     * {@code selectedStop}, so decrementing the selected stop is what makes
     * a brand-new symbol enter at the top of the window and every
     * previously-visible symbol shift one row down toward the bottom, the
     * reel's required downward motion.
     */
    private void advanceReelAlongStrip(int columns, int rows, boolean demo, int col, SlotsReelStrip strip) {
        reelScrollPosition[col] = Math.floorMod(reelScrollPosition[col] - 1, SlotsReelStrip.SIZE);
        paintReelWindow(columns, rows, demo, col, strip.window(reelScrollPosition[col], rows));
    }

    /**
     * Marks the reel landed. By construction (see {@link #seedReelDisplay})
     * the reel's natural position already equals {@code committedStop} after
     * its last scheduled advance -- this repaints the same window rather
     * than performing any unrelated jump, and exists as an explicit,
     * assertable landing event rather than trusting the tick count alone.
     */
    private void lockReelToStop(int columns, int rows, boolean demo, int col, SlotsReelStrip strip, int committedStop) {
        reelScrollPosition[col] = committedStop;
        paintReelWindow(columns, rows, demo, col, strip.window(committedStop, rows));
    }

    /** Paints one reel's window using explicitly captured geometry -- see {@link #seedReelDisplay}. */
    private void paintReelWindow(int columns, int rows, boolean demo, int col, SlotsSymbol[] window) {
        reelDisplay[col] = window;
        for (int row = 0; row < rows; row++) {
            paintCell(columns, rows, demo, col, row, window[row]);
        }
    }

    /**
     * Paints one reel's visible cells from the machine's current live
     * config -- used only outside animation (e.g. {@link #renderCanvas}'s
     * idle repaint), where "the current geometry" and "what is on screen"
     * are the same thing by definition. {@link #lastGridIsDemo} says whether
     * the currently-displayed grid came from a Demo Spin.
     */
    private void paintReel(int col) {
        int columns = config.columns();
        int rows = config.visibleRows();
        for (int row = 0; row < rows; row++) {
            paintCell(columns, rows, lastGridIsDemo, col, row, reelDisplay[col][row]);
        }
    }

    /**
     * Paints one cell. When {@code demo} is true every cell carries an
     * explicit "this is a demo" lore line -- required to be unmistakable
     * everywhere, not only in the end-of-spin chat message.
     */
    private void paintCell(int columns, int rows, boolean demo, int col, int row, SlotsSymbol symbol) {
        int slot = SlotsGeometry.gridSlot(columns, rows, row, col);
        switch (SlotsCellPresentation.of(symbol, demo)) {
            // A clean white reel bay/shutter, not a repeated textual
            // placeholder -- blank name, no lore (the localization keys stay
            // registered but unused here; see slots.neutral-cell(-lore)).
            case NEUTRAL -> addItemAndLore(SlotsControlPresentation.Role.NEUTRAL_CELL.material(), 1, " ", slot);
            case DEMO -> addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)),
                ChatColor.WHITE, ChatColor.YELLOW, slot, text("slots.demo-cell-note"));
            case PAID -> addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), ChatColor.WHITE, slot);
        }
    }

    private void paintOutcomeGrid(SlotsOutcome outcome, boolean demo) {
        int columns = outcome.columns();
        int rows = outcome.rows();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                SlotsSymbol symbol = outcome.symbolAt(row, col);
                int slot = SlotsGeometry.gridSlot(columns, rows, row, col);
                if (demo) {
                    addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)),
                        ChatColor.WHITE, ChatColor.YELLOW, slot, text("slots.demo-cell-note"));
                } else {
                    addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), ChatColor.WHITE, slot);
                }
            }
        }
    }

    /** Lights only the cells that actually matched -- the run, not the whole line. */
    private void highlightLine(SlotsMath.CatalogLineResult win, SlotsOutcome outcome, boolean demo) {
        int columns = outcome.columns();
        int rows = outcome.rows();
        int[] path = win.line().rows();
        boolean demoLabelled = SlotsCellPresentation.of(win.symbol(), demo).isDemoLabelled();
        for (int col = 0; col < win.runLength(); col++) {
            if (demoLabelled) {
                setGlowingItem(
                    SlotsGeometry.gridSlot(columns, rows, path[col], col),
                    win.symbol().material(),
                    ChatColor.GOLD + text(symbolKey(win.symbol())),
                    text("slots.win-line-lore",
                        "run", win.runLength(),
                        "multiplier", formatMultiplier(win.multiplier())),
                    text("slots.demo-cell-note"));
                continue;
            }
            setGlowingItem(
                SlotsGeometry.gridSlot(columns, rows, path[col], col),
                win.symbol().material(),
                ChatColor.GOLD + text(symbolKey(win.symbol())),
                text("slots.win-line-lore",
                    "run", win.runLength(),
                    "multiplier", formatMultiplier(win.multiplier())));
        }
    }

    // ---- audio -----------------------------------------------------------

    private void play(String key, Sound sound, float volume, float pitch) {
        if (SoundHelper.getSoundSafely(key, player) != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    private void playClick(float pitch) {
        play("ui.button.click", Sound.UI_BUTTON_CLICK, 0.6f, pitch);
    }

    private void playLeverPull() {
        play("block.lever.click", Sound.BLOCK_LEVER_CLICK, 1.0f, 0.7f);
        play("block.piston.extend", Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.6f);
    }

    /**
     * The pitch ladder: each reel stops a step higher than the one before it.
     * This is the cheapest and most effective tension device a slot machine
     * has -- the ear tracks the rising sequence and anticipates the last one.
     */
    private void playReelStop(int reel, int columns) {
        float progress = columns <= 1 ? 0f : (float) reel / (columns - 1);
        float pitch = 0.8f + (progress * 0.9f);
        play("block.note_block.bass", Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, pitch);
        play("block.wooden_button.click_on", Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, pitch);
    }

    private void playAnticipation() {
        play("block.note_block.pling", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.9f);
        play("entity.experience_orb.pickup", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.6f);
    }

    private void playLineReveal(int index, int total) {
        float pitch = 1.0f + (0.12f * Math.min(index, 8));
        play("block.note_block.bell", Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, pitch);
    }

    /** Win audio scales with the size of the win relative to the stake. */
    private void playFinale(long payout) {
        long bet = Math.max(1L, currentTotalBet());
        double ratio = (double) payout / bet;
        if (ratio >= 20.0) {
            play("ui.toast.challenge_complete", Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            play("entity.player.levelup", Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        } else if (ratio >= 5.0) {
            play("entity.player.levelup", Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else {
            play("block.note_block.chime", Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.5f);
        }
    }

    private void playLoss() {
        play("block.note_block.bass", Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
    }

    // ---- settlement ------------------------------------------------------

    private void settle(SlotsCallbackGuard.SpinToken token) {
        if (!SlotsCallbackGuard.isValid(token, playerId, dealerId, controller.generation())) {
            return;
        }
        if (controller.state() != SlotsSessionState.ANIMATING && controller.state() != SlotsSessionState.RESULT_COMMITTED) {
            return;
        }
        long payout = controller.pendingPayoutAmount();
        SlotsSettlementResult result = controller.settle(
            this::creditPlayerDirect,
            amount -> queuePayout(amount, PayoutMessages.committedResultContext("Slots")),
            underwriting);
        reportSettlement(result, payout);
        continueAutoSpinIfNeeded(result, payout);
    }

    /**
     * Retried only when the player clicks the blocked spin control. The
     * retry consumes that click even when it succeeds, so resolving an old
     * obligation can never also debit a new spin as a side effect of the
     * same interaction. The retained amount is re-attempted exactly as-is,
     * never recomputed.
     */
    private void attemptSettlementRetry() {
        // The retry attempt itself must use exactly the outstanding
        // remainder -- controller.retrySettlement() reads
        // pendingPayoutAmount() internally for that, and this local capture
        // is only for the announcement below (what THIS retry actually
        // delivers/queues), never for what Last Result displays.
        long remainder = controller.pendingPayoutAmount();
        SlotsSettlementResult result = controller.retrySettlement(
            this::creditPlayerDirect,
            amount -> queuePayout(amount, PayoutMessages.committedResultContext("Slots")));
        // Last Result must show the FULL awarded payout, not the remainder
        // still outstanding after an earlier partial delivery --
        // pendingPayoutAmount() was reduced to the remainder by that partial
        // delivery, but controller.lastWinAmount() retains what the spin
        // actually won. A retry is otherwise a delivery-only event: the
        // spin's result already fixed this payout, and Last Result already
        // shows it (or was snapped to it) from when the spin first settled.
        // Re-running the count-up here would visibly drop the display back
        // to zero and count back up to the same number -- exactly the
        // backward-jump defect this class exists to prevent -- so a retry
        // only ever snaps, never animates.
        stopWinMeterScheduler();
        lastWinState.retrySettled(controller.lastWinAmount());
        renderControls();
        announceSettlementResult(result, remainder);
    }

    private void reportSettlement(SlotsSettlementResult result, long payout) {
        // Stop any previous scheduler first (task only -- must not touch the
        // presentation state), so the settlement below starts the new
        // animation from a clean slate rather than being immediately
        // cancelled by its own setup.
        stopWinMeterScheduler();
        long generation = lastWinState.settle(payout);
        // Other controls (Spin unlocking, Height/Reels/etc.) must update
        // immediately regardless of the win amount; the meter animation
        // below only concerns the Spin lever's own repaint from here on.
        renderControls();
        if (payout > 0) {
            animateWinMeter(payout, generation);
        }
        announceSettlementResult(result, payout);
    }

    private void announceSettlementResult(SlotsSettlementResult result, long payout) {
        switch (result) {
            case DELIVERED -> {
                if (payout > 0) {
                    long bet = Math.max(1L, currentTotalBet());
                    // Reported against the stake so a payout smaller than the
                    // total bet reads honestly as a partial return, not a win.
                    String key = payout >= bet ? "slots.win" : "slots.partial-return";
                    player.sendMessage(text(key,
                        "amount", plugin.formatWagerDisplay(currencyMode, currencyName, payout),
                        "bet", plugin.formatWagerDisplay(currencyMode, currencyName, bet)));
                } else {
                    player.sendMessage(text("slots.loss"));
                    playLoss();
                }
            }
            case QUEUED -> player.sendMessage(text("slots.payout-pending"));
            case FAILED -> {
                plugin.getLogger().severe("[NCCasino] Slots payout could not be delivered or durably queued -- player="
                    + playerId + ", dealer=" + internalName + ", game=Slots, amount=" + payout
                    + ", currencyMode=" + currencyMode + ". Retained as SETTLEMENT_FAILED for retry; requires manual reconciliation if this persists.");
                player.sendMessage(text("slots.payout-blocked"));
            }
        }
    }

    /**
     * Live delivery of a committed payout.
     *
     * <p>Vault balances are numeric and deposit exactly. Item currencies go
     * through {@link OverflowBankService}, which reserves any overflow in the
     * bank before moving a single item, then fills the inventory, applies the
     * player's Bank/Drop preference and caps physical drops -- so an item win
     * larger than the inventory is a completed payout rather than a failed
     * one.
     *
     * @return how much of {@code amount} is STILL owed. A partial result
     *     matters: the controller retains exactly this remainder, so the
     *     portion already delivered is never paid a second time.
     */
    private long creditPlayerDirect(long amount) {
        if (amount <= 0) {
            return 0L;
        }
        if (player == null || !player.isOnline()) {
            return amount;
        }
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null
            && provider.getMode() == CurrencyMode.VAULT
            && provider instanceof VaultCurrencyProvider vaultProvider) {
            return vaultProvider.deposit(player, internalName, MoneyHelper.bd(amount)) ? 0L : amount;
        }

        OverflowBankService bank = plugin.getOverflowBankService();
        Material mat = plugin.getCurrency(internalName);
        if (bank == null || mat == null) {
            return amount;
        }

        ItemDeliveryOutcome outcome = bank.deliver(
            player, new BankedCurrency(currencyMode, mat.name(), currencyName), amount);
        if (outcome.hasBanked()) {
            player.sendMessage(text("slots.payout-banked", "amount", outcome.banked()));
        }
        return outcome.unsettled();
    }

    /** Attempts only durable persistence; live delivery is owned exclusively by the controller. */
    private boolean queuePayout(long amount, String context) {
        Material mat = plugin.getCurrency(internalName);
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Slots",
            internalName,
            currencyMode,
            mat != null ? mat.name() : null,
            currencyName,
            amount,
            context
        );
        return plugin.getPendingPayoutStore().addPendingPayout(payout);
    }

    // ---- lifecycle ---------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this || !event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }
        if (promptSuspended) {
            // This machine closed its own inventory to collect chat input.
            // The session is suspended, not exited: it is reopened on a
            // successful answer or a cancel, and only ever terminated by the
            // prompt's own timeout/another-game/disconnect paths.
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (promptSuspended || !SessionRegistry.isRegistered(playerId, this)) {
                return;
            }
            if (!player.isOnline()) {
                ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
                SessionRegistry.terminatePlayerSession(playerId, reason);
                return;
            }
            SessionRegistry.terminateSession(playerId, this, ExitReason.VOLUNTARY_INVENTORY_CLOSE);
        });
    }

    /**
     * Authoritative disconnect/kick/shutdown resolution. Debit, outcome
     * generation, and payout computation are all synchronous within
     * {@link #handleSpin(boolean)} -- whatever this table's state shows at the
     * moment of termination already reflects the true, finished outcome of
     * the last accepted spin.
     */
    @Override
    public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
        if (closeFlag) {
            return;
        }
        closeFlag = true;
        promptSuspended = false;
        pendingProfileSnapshot = null;
        // Release any chat prompt this machine still owns -- a disconnect,
        // a dealer removal, or a plugin shutdown must never leave the shared
        // service holding a prompt for a session that no longer exists. Scoped
        // to this machine, so it can never cancel a prompt a newer session for
        // the same player has already taken over.
        SlotsChatPromptService prompts = promptService();
        if (prompts != null) {
            prompts.endForSession(playerId, this, SlotsChatPrompt.EndReason.SESSION_ENDED);
        }

        GameTerminationPolicy.SlotsPhase phase = switch (controller.state()) {
            case IDLE, RESOLVED -> GameTerminationPolicy.SlotsPhase.PREGAME;
            case TERMINATED -> GameTerminationPolicy.SlotsPhase.RESOLVED;
            // Covers DEBIT_ACCEPTED, RESULT_COMMITTED, ANIMATING, SETTLING,
            // and SETTLEMENT_FAILED alike -- in every one of those a
            // positive committed payout may still be owed, so termination
            // always (re-)queues it durably, including a fresh attempt for
            // an amount already stuck in SETTLEMENT_FAILED.
            default -> GameTerminationPolicy.SlotsPhase.RESULT_COMMITTED;
        };
        TerminationAction action = GameTerminationPolicy.slots(reason, phase);

        if (action == TerminationAction.QUEUE_KNOWN_PAYOUT) {
            String context = reason == ExitReason.PLUGIN_DISABLE
                ? PayoutMessages.committedResultContext("Slots")
                : PayoutMessages.disconnectedMidGameContext("Slots");
            // The result stands and the payout is preserved, so the dealer's
            // books close here too, for the real amount. Idempotent: if the
            // round had already settled normally, there is no open
            // commitment left to settle.
            controller.settleBudgetOnTermination(underwriting, true);
            queuePayoutDurableOnly(controller.pendingPayoutAmount(), context);
        } else {
            // Nothing is owed to the player -- a pregame exit, an
            // already-resolved round, or a kick that forfeits even a
            // pending win outright (see FORFEIT below). Release any open
            // promise with nothing paid, so the dealer is never debited for
            // a win that will never actually be delivered.
            controller.settleBudgetOnTermination(underwriting, false);
        }
        // FORFEIT (kicked): the debited stake stays with the house, nothing to give back.
        // NO_ACTION: nothing was owed (pregame) or it was already resolved through normal play.

        cancelScheduledTasks();
        controller.terminate();
        slotsInventory.removeTable(terminatedPlayerId);
        HandlerList.unregisterAll(this);
    }

    /**
     * Never attempts a live credit here -- unlike {@link #settle}, it is
     * not reliably knowable whether the {@link Player} object is still
     * safely usable at the exact moment a disconnect/shutdown termination
     * runs, so this always durably queues first and only falls back to a
     * best-effort direct credit if the durable write itself fails.
     */
    private void queuePayoutDurableOnly(long amount, String context) {
        Material mat = plugin.getCurrency(internalName);
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Slots",
            internalName,
            currencyMode,
            mat != null ? mat.name() : null,
            currencyName,
            amount,
            context
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().severe("[NCCasino] Slots pending payout failed to persist for " + playerId
                + " at dealer " + internalName + " (amount=" + amount
                + "); attempting a live fallback only if the player is still safely online.");
            boolean fallbackDelivered = amount <= 0
                || (player != null && player.isOnline() && creditPlayerDirect(amount) <= 0);
            if (!fallbackDelivered) {
                plugin.getLogger().severe("[NCCasino] Slots termination payout requires manual reconciliation -- player="
                    + playerId + ", dealer=" + internalName + ", game=Slots, amount=" + amount
                    + ", currencyMode=" + currencyMode + ", context=" + context
                    + ". It could not be delivered live or durably queued and the client is terminating.");
            }
        }
    }

    /**
     * Binds the pure spin controller to this dealer's shared budget.
     *
     * <p>Every method short-circuits for an UNLIMITED dealer, which is every
     * dealer until an administrator opts one in -- so an ordinary server runs
     * exactly the code it ran before Phase 2.
     */
    private final class DealerBudgetUnderwriting implements SlotsUnderwriting {

        @Override
        public Commitment underwrite(long totalBetUnits, long maxPossiblePayout) {
            DealerBudgetService budget = plugin.getDealerBudgetService();
            if (budget == null) {
                return Commitment.forUnlimitedDealer();
            }
            Material material = plugin.getCurrency(internalName);
            return budget.reserve(
                internalName,
                playerId,
                "Slots",
                budgetSessionId + "-spin-" + (controller.generation() + 1),
                new BankedCurrency(currencyMode, material == null ? null : material.name(), currencyName),
                Exposure.of(totalBetUnits, maxPossiblePayout));
        }

        @Override
        public void cancel(Commitment commitment, long totalBetUnits) {
            DealerBudgetService budget = plugin.getDealerBudgetService();
            if (budget == null || commitment == null) {
                return;
            }
            // Paying the stake back out of the dealer exactly reverses the
            // credit taken when the commitment was accepted.
            budget.refund(internalName, commitment, Money.of(totalBetUnits));
        }

        @Override
        public void settle(Commitment commitment, long payout) {
            DealerBudgetService budget = plugin.getDealerBudgetService();
            if (budget == null || commitment == null) {
                return;
            }
            budget.settle(internalName, commitment, Money.of(payout));
        }
    }

    private void cancelScheduledTasks() {
        cancelAnimationTask();
        cancelWinMeterTask();
        cancelLineFlashTask();
        cancelDemoTask();
        cancelOpeningAnimationTask();
        stopAutoSpin();
    }

    private void cancelAnimationTask() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    /**
     * Stops any currently scheduled meter-tick task without touching the
     * presentation state -- used only when a settlement is about to
     * immediately replace it with its own freshly-started animation
     * ({@link #reportSettlement}, {@link #attemptSettlementRetry}). Must
     * never be confused with {@link #cancelWinMeterTask()}: calling that
     * (state-snapping) version here was the exact bug this fixes -- it
     * cancelled the very animation it was about to schedule ticks for,
     * before a single tick had run.
     */
    private void stopWinMeterScheduler() {
        if (winMeterTask != null) {
            winMeterTask.cancel();
            winMeterTask = null;
        }
    }

    /**
     * A genuine interruption -- a new spin starting, the inventory closing,
     * session teardown. Cancelling the task alone used to be the whole bug:
     * the meter's internal counter simply stopped wherever it was, and that
     * partial value stayed on screen as "the" last result until the next
     * settlement. {@link SlotsWinMeterAnimation#interrupt} snaps the
     * presentation back to the authoritative completed payout in the same
     * call, so a cancel from anywhere here can never leave a partial amount
     * displayed.
     */
    private void cancelWinMeterTask() {
        stopWinMeterScheduler();
        lastWinState.interrupt();
    }

    /**
     * Bumping {@link #lineFlashGuard}'s generation (not just cancelling the
     * task) is what lets every scheduled blink frame check, on its own next
     * tick, whether it has been superseded -- safe to call from teardown
     * paths (session termination, inventory close) since it never repaints.
     * See {@link #supersedeLineFlash()} for the interactive path that also
     * repaints immediately.
     */
    private void cancelLineFlashTask() {
        lineFlashGuard.cancel();
        if (lineFlashTask != null) {
            lineFlashTask.cancel();
            lineFlashTask = null;
        }
    }

    /**
     * Synchronous supersession: cancels and invalidates any active blink,
     * then -- only if a flash was actually running -- immediately repaints
     * the ordinary clean canvas, so a new Paylines input, view change,
     * geometry change, or spin start can never leave a stale colored path
     * (or two different lines' colored paths) visible at once. Never called
     * from a teardown path (use {@link #cancelLineFlashTask()} there
     * instead), since {@code closeFlag} guards against repainting a closed
     * inventory.
     */
    private void supersedeLineFlash() {
        boolean hadActiveFlash = lineFlashTask != null;
        cancelLineFlashTask();
        if (hadActiveFlash && !closeFlag) {
            repaintCanvas();
        }
    }

    /**
     * Bumping {@link #demoGeneration} (rather than only cancelling the task)
     * is what makes this safe to call even from inside a running demo tick --
     * the running callback's own captured generation will no longer match on
     * its next check.
     */
    private void cancelDemoTask() {
        demoGeneration++;
        demoActive = false;
        if (demoTask != null) {
            demoTask.cancel();
            demoTask = null;
        }
    }

    @Override
    public void delete() {
        cancelScheduledTasks();
        HandlerList.unregisterAll(this);
        super.delete();
    }

    private CurrencyProvider getCurrencyProvider() {
        if (plugin.getCurrencyManager() == null) {
            return null;
        }
        return plugin.getCurrencyManager().getProvider(internalName);
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
}
