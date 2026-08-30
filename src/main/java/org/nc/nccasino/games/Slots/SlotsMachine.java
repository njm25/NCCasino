package org.nc.nccasino.games.Slots;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
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
import java.util.concurrent.ThreadLocalRandom;
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
 * <p>The machine's width ({@link SlotsGeometry}) and active payline count are
 * both player-selectable in session, and the grid physically grows outward
 * from the centre as columns are added. Because the configured house edge is
 * applied by deriving the paytable ({@link SlotsPaytable}) rather than by
 * hardcoding multipliers, neither choice moves the machine's return -- a wider
 * machine is higher variance at identical RTP, and extra lines scale stake and
 * expected return together.
 *
 * <p>Financial contract: a debit ({@link WagerTransaction#tryWithdraw}) is
 * always attempted and confirmed <em>before</em> any outcome is generated.
 * The instant that debit succeeds, {@link SlotsSpinGenerator} produces one
 * immutable {@link SlotsOutcome} and the payout owed is computed and stored --
 * everything from that point on (the reel motion, the win walk-through, the
 * eventual credit) is just carrying out an already-decided result. There is no
 * route back from a committed result to a refundable pregame state (see
 * {@link SlotsStateMachine}).
 */
public class SlotsMachine extends DealerInventory implements TerminableSession {

    // ---- layout ----------------------------------------------------------
    // Row 0: machine controls (width, info, lines).  Rows 1-3: reel grid.
    // Row 4: payline selectors.  Row 5: wager controls and meters.

    private static final int FEWER_COLUMNS_SLOT = 1;
    private static final int COLUMNS_DISPLAY_SLOT = 2;
    private static final int MORE_COLUMNS_SLOT = 3;
    private static final int MACHINE_INFO_SLOT = 4;
    private static final int FEWER_LINES_SLOT = 5;
    private static final int LINES_DISPLAY_SLOT = 6;
    private static final int MORE_LINES_SLOT = 7;

    private static final int LINE_SELECTOR_ROW_START = 36;

    private static final int EXIT_SLOT = 45;
    private static final int BALANCE_SLOT = 46;
    private static final int PREV_DENOM_SLOT = 47;
    private static final int DENOM_DISPLAY_SLOT = 48;
    private static final int SPIN_SLOT = 49;
    private static final int TOTAL_BET_SLOT = 50;
    private static final int NEXT_DENOM_SLOT = 51;
    private static final int WIN_METER_SLOT = 52;
    private static final int PAYTABLE_SLOT = 53;

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
    /** Cosmetic symbols currently shown per reel, top row first. Never authoritative. */
    private SlotsSymbol[][] reelDisplay;
    private long displayedWin = -1L;

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
        this.reelDisplay = new SlotsSymbol[config.columns()][SlotsGeometry.ROWS];

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
        redrawEverything();
    }

    private void redrawEverything() {
        renderFrame();
        renderMachineControls();
        renderLineSelectors();
        renderIdleGrid();
        renderControls();
        renderInfo();
    }

    // ---- rendering -------------------------------------------------------

    private void renderFrame() {
        for (int slot = 0; slot < SlotsGeometry.INVENTORY_SIZE; slot++) {
            if (isInteractiveSlot(slot) || SlotsGeometry.isGridSlot(config.columns(), slot)) {
                continue;
            }
            addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, " ", slot);
        }
    }

    private boolean isInteractiveSlot(int slot) {
        if (slot >= LINE_SELECTOR_ROW_START && slot < LINE_SELECTOR_ROW_START + SlotsPayline.MAX_LINES) {
            return true;
        }
        return switch (slot) {
            case FEWER_COLUMNS_SLOT, COLUMNS_DISPLAY_SLOT, MORE_COLUMNS_SLOT, MACHINE_INFO_SLOT,
                 FEWER_LINES_SLOT, LINES_DISPLAY_SLOT, MORE_LINES_SLOT,
                 EXIT_SLOT, BALANCE_SLOT, PREV_DENOM_SLOT, DENOM_DISPLAY_SLOT, SPIN_SLOT,
                 TOTAL_BET_SLOT, NEXT_DENOM_SLOT, WIN_METER_SLOT, PAYTABLE_SLOT -> true;
            default -> false;
        };
    }

    /** Machine width and payline count controls, plus the live RTP readout. */
    private void renderMachineControls() {
        boolean locked = !controller.isReadyForSpin();
        int columns = config.columns();

        addItemAndLore(
            columns > SlotsGeometry.MIN_COLUMNS ? Material.REDSTONE_TORCH : Material.LEVER,
            1,
            text("slots.fewer-columns"),
            FEWER_COLUMNS_SLOT,
            text("slots.columns-current", "columns", columns));
        addItemAndLore(
            Material.OBSERVER, 1, text("slots.columns"), COLUMNS_DISPLAY_SLOT,
            text("slots.columns-current", "columns", columns),
            text("slots.columns-hint"));
        addItemAndLore(
            columns < SlotsGeometry.MAX_COLUMNS ? Material.REDSTONE_TORCH : Material.LEVER,
            1,
            text("slots.more-columns"),
            MORE_COLUMNS_SLOT,
            text("slots.columns-current", "columns", columns));

        addItemAndLore(
            Material.KNOWLEDGE_BOOK, 1, text("slots.machine-info"), MACHINE_INFO_SLOT,
            text("slots.machine-info-rtp", "rtp", formatPercent(config.paytable().theoreticalRtp())),
            text("slots.machine-info-edge", "edge", formatPercent(config.houseEdge())),
            text("slots.machine-info-width", "columns", columns),
            text("slots.machine-info-lines", "lines", config.activeLines()));

        addItemAndLore(
            config.activeLines() > 1 ? Material.REDSTONE_TORCH : Material.LEVER,
            1, text("slots.fewer-lines"), FEWER_LINES_SLOT,
            text("slots.lines-current", "lines", config.activeLines()));
        addItemAndLore(
            Material.ITEM_FRAME, 1, text("slots.lines"), LINES_DISPLAY_SLOT,
            text("slots.lines-current", "lines", config.activeLines()),
            text("slots.lines-hint"));
        addItemAndLore(
            config.activeLines() < SlotsPayline.MAX_LINES ? Material.REDSTONE_TORCH : Material.LEVER,
            1, text("slots.more-lines"), MORE_LINES_SLOT,
            text("slots.lines-current", "lines", config.activeLines()));

        if (locked) {
            // Width/line changes are stake-affecting, so they are visibly
            // inert mid-spin rather than silently ignored.
            dimSlot(FEWER_COLUMNS_SLOT);
            dimSlot(MORE_COLUMNS_SLOT);
            dimSlot(FEWER_LINES_SLOT);
            dimSlot(MORE_LINES_SLOT);
        }
    }

    /**
     * One selector per possible payline. Active lines glow; clicking a
     * selector sets the line count to that line, which is how a player sees
     * what each line's shape actually is before staking on it.
     */
    private void renderLineSelectors() {
        for (int line = 0; line < SlotsPayline.MAX_LINES; line++) {
            int slot = LINE_SELECTOR_ROW_START + line;
            boolean active = line < config.activeLines();
            String name = text(active ? "slots.line-selector-active" : "slots.line-selector-inactive", "line", line + 1);
            String[] lore = {
                active ? text("slots.line-active") : text("slots.line-inactive"),
                text("slots.line-shape", "shape", text(paylineKey(SlotsPayline.ALL[line]))),
                text("slots.line-selector-hint")
            };
            if (active) {
                setGlowingItem(slot, Material.LIME_STAINED_GLASS_PANE, name, lore);
            } else {
                addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1, name, slot, lore);
            }
        }
    }

    /**
     * The pregame grid. Cells that sit on at least one active payline are
     * lit, so the player can see the shape of what they are betting on before
     * committing -- the off-line cells stay dark.
     */
    private void renderIdleGrid() {
        boolean[][] onActiveLine = activeLineCoverage();
        int columns = config.columns();
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                int slot = SlotsGeometry.gridSlot(columns, row, col);
                if (onActiveLine[row][col]) {
                    setGlowingItem(slot, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        text("slots.cell-on-line"),
                        text("slots.cell-on-line-lore"));
                } else {
                    addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1,
                        text("slots.cell-off-line"), slot);
                }
            }
        }
    }

    private boolean[][] activeLineCoverage() {
        int columns = config.columns();
        boolean[][] covered = new boolean[SlotsGeometry.ROWS][columns];
        for (SlotsPayline payline : SlotsPayline.active(config.activeLines())) {
            for (int col = 0; col < columns; col++) {
                covered[payline.rowAt(col, columns)][col] = true;
            }
        }
        return covered;
    }

    private void renderControls() {
        boolean locked = !controller.isReadyForSpin();
        boolean blocked = controller.state() == SlotsSessionState.SETTLEMENT_FAILED;

        addItemAndLore(Material.SPRUCE_DOOR, 1, text("slots.exit"), EXIT_SLOT,
            text("slots.exit-lore"));

        addItemAndLore(Material.ARROW, 1, text("slots.previous-denomination"), PREV_DENOM_SLOT,
            text("slots.denomination-hint"));
        addItemAndLore(Material.ARROW, 1, text("slots.next-denomination"), NEXT_DENOM_SLOT,
            text("slots.denomination-hint"));

        if (blocked) {
            setGlowingItem(SPIN_SLOT, Material.REDSTONE_BLOCK,
                text("slots.payout-blocked"),
                text("slots.payout-blocked-retry"));
        } else if (locked) {
            addItemAndLore(Material.REDSTONE_BLOCK, 1, text("slots.spin-locked"),
                SPIN_SLOT, text("slots.spin-locked-lore"));
        } else {
            setGlowingItem(SPIN_SLOT, Material.EMERALD_BLOCK,
                text("slots.spin"),
                text("slots.spin-lore", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet())));
        }

        List<String> paytableLore = new ArrayList<>();
        SlotsPaytable paytable = config.paytable();
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            StringBuilder runs = new StringBuilder();
            for (int run = symbol.minimumRun(); run <= config.columns(); run++) {
                if (runs.length() > 0) {
                    runs.append("  ");
                }
                runs.append(run).append("x").append(formatMultiplier(paytable.multiplier(symbol, run)));
            }
            paytableLore.add(text("slots.paytable-line",
                "symbol", text(symbolKey(symbol)), "payouts", runs.toString()));
        }
        paytableLore.add(text("slots.paytable-blank-note"));
        paytableLore.add(text("slots.paytable-rtp", "rtp", formatPercent(paytable.theoreticalRtp())));
        addItemAndLore(Material.ENCHANTED_BOOK, 1, text("slots.paytable"), PAYTABLE_SLOT,
            paytableLore.toArray(new String[0]));
    }

    private void renderInfo() {
        double denomination = chipValues[denominationIndex];

        addItemAndLore(Material.SUNFLOWER, 1, text("slots.wager-denomination"), DENOM_DISPLAY_SLOT,
            text("slots.wager-denomination-value", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, denomination)));

        addItemAndLore(Material.GOLD_INGOT, 1, text("slots.total-bet"), TOTAL_BET_SLOT,
            text("slots.total-bet-value", "amount",
                plugin.formatWagerDisplay(currencyMode, currencyName, currentTotalBet())),
            text("slots.total-bet-breakdown", "lines", config.activeLines()));

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            int balance = provider.getBalance(player, internalName);
            addItemAndLore(Material.EMERALD, 1, text("slots.balance"), BALANCE_SLOT,
                text("slots.balance-value", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, balance)));
        } else {
            addItemAndLore(Material.EMERALD, 1, text("slots.balance"), BALANCE_SLOT,
                text("slots.balance-items"));
        }

        renderWinMeter(displayedWin);
    }

    /**
     * Counts the win meter up to the awarded amount rather than snapping to
     * it. The credited balance is already final before this starts -- this is
     * presentation only, and a termination mid-count loses nothing.
     *
     * <p>The step size is derived from the total so a huge win does not take
     * proportionally longer to count than a small one; the whole run is capped
     * at {@link SlotsTiming#WIN_METER_MAX_TICKS}.
     */
    private void animateWinMeter(long payout) {
        cancelWinMeterTask();
        long steps = Math.max(1L, SlotsTiming.WIN_METER_MAX_TICKS / SlotsTiming.WIN_METER_STEP_TICKS);
        long increment = Math.max(1L, payout / steps);
        final long[] shown = {0L};

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (closeFlag || player == null || !player.isOnline()) {
                    cancel();
                    winMeterTask = null;
                    return;
                }
                shown[0] = Math.min(payout, shown[0] + increment);
                renderWinMeter(shown[0]);
                playMeterTick(shown[0], payout);
                if (shown[0] >= payout) {
                    cancel();
                    winMeterTask = null;
                }
            }
        };
        winMeterTask = runnable.runTaskTimer(plugin, SlotsTiming.WIN_METER_STEP_TICKS, SlotsTiming.WIN_METER_STEP_TICKS);
    }

    /** Rising ticks as the meter climbs, the way a physical machine pays out. */
    private void playMeterTick(long shown, long payout) {
        float progress = payout <= 0 ? 1f : (float) shown / payout;
        play("block.note_block.hat", Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 0.9f + (progress * 0.9f));
    }

    private void renderWinMeter(long amount) {
        if (amount < 0) {
            addItemAndLore(Material.PAPER, 1, text("slots.last-win-none"), WIN_METER_SLOT,
                text("slots.last-win-none-lore"));
        } else if (amount == 0) {
            addItemAndLore(Material.PAPER, 1, text("slots.last-win-loss"), WIN_METER_SLOT,
                text("slots.last-win-loss-lore"));
        } else {
            setGlowingItem(WIN_METER_SLOT, Material.GOLD_NUGGET,
                text("slots.last-win"),
                text("slots.last-win-value", "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, amount)));
        }
    }

    private long currentTotalBet() {
        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        try {
            return SlotsMath.totalBet(denomUnits, config.activeLines());
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
                    loreList.add(ChatColor.GRAY + line);
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

    private String paylineKey(SlotsPayline payline) {
        return "slots.payline-" + payline.name().toLowerCase().replace('_', '-');
    }

    // ---- click handling --------------------------------------------------

    @Override
    public void handleClick(int slot, Player clicker, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotsMachine) || !clicker.getUniqueId().equals(playerId)) {
            return;
        }
        if (controller.state() == SlotsSessionState.SETTLEMENT_FAILED) {
            if (slot == SPIN_SLOT) {
                attemptSettlementRetry();
            } else {
                denyAction(player, text("slots.payout-blocked"));
            }
            return;
        }

        if (slot >= LINE_SELECTOR_ROW_START && slot < LINE_SELECTOR_ROW_START + SlotsPayline.MAX_LINES) {
            handleSelectLineCount(slot - LINE_SELECTOR_ROW_START + 1);
            return;
        }

        switch (slot) {
            case EXIT_SLOT -> handleExit();
            case SPIN_SLOT -> handleSpin();
            case PREV_DENOM_SLOT -> handleChangeDenomination(-1);
            case NEXT_DENOM_SLOT -> handleChangeDenomination(1);
            case PAYTABLE_SLOT, MACHINE_INFO_SLOT -> handlePaytable();
            case FEWER_COLUMNS_SLOT -> handleChangeColumns(-2);
            case MORE_COLUMNS_SLOT -> handleChangeColumns(2);
            case FEWER_LINES_SLOT -> handleSelectLineCount(config.activeLines() - 1);
            case MORE_LINES_SLOT -> handleSelectLineCount(config.activeLines() + 1);
            case COLUMNS_DISPLAY_SLOT, LINES_DISPLAY_SLOT, TOTAL_BET_SLOT, DENOM_DISPLAY_SLOT,
                 BALANCE_SLOT, WIN_METER_SLOT -> playDefaultSound(player);
            default -> {
            }
        }
    }

    private void handleExit() {
        playDefaultSound(player);
        player.closeInventory();
    }

    private void handlePaytable() {
        playDefaultSound(player);
        player.sendMessage(text("slots.paytable-header"));
        SlotsPaytable paytable = config.paytable();
        for (SlotsSymbol symbol : SlotsSymbol.payingSymbols()) {
            StringBuilder runs = new StringBuilder();
            for (int run = symbol.minimumRun(); run <= config.columns(); run++) {
                if (runs.length() > 0) {
                    runs.append("  ");
                }
                runs.append(run).append("x").append(formatMultiplier(paytable.multiplier(symbol, run)));
            }
            player.sendMessage(text("slots.paytable-line",
                "symbol", text(symbolKey(symbol)), "payouts", runs.toString()));
        }
        player.sendMessage(text("slots.paytable-rtp", "rtp", formatPercent(paytable.theoreticalRtp())));
    }

    /** Width steps by two so the machine only ever sits on a legal odd width. */
    private void handleChangeColumns(int delta) {
        if (!controller.isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        int target = config.columns() + delta;
        if (!SlotsGeometry.isSupportedColumnCount(target)) {
            denyAction(player, text(delta > 0 ? "slots.columns-at-max" : "slots.columns-at-min"));
            return;
        }
        config = SlotsConfig.of(target, config.activeLines(), config.houseEdge());
        reelDisplay = new SlotsSymbol[config.columns()][SlotsGeometry.ROWS];
        displayedWin = -1L;
        playClick(1.0f + (delta > 0 ? 0.2f : -0.2f));
        // The grid physically changes size, so the whole view is rebuilt
        // rather than patched -- old grid slots must go back to being frame.
        redrawEverything();
    }

    private void handleSelectLineCount(int lines) {
        if (!controller.isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        int normalized = SlotsPayline.normalizeLineCount(lines);
        if (normalized == config.activeLines()) {
            playDefaultSound(player);
            return;
        }
        boolean increasing = normalized > config.activeLines();
        config = config.withActiveLines(normalized);
        playClick(increasing ? 1.4f : 0.8f);
        renderMachineControls();
        renderLineSelectors();
        renderIdleGrid();
        renderControls();
        renderInfo();
    }

    private void handleChangeDenomination(int delta) {
        if (!controller.isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        int next = SlotsDenominationPolicy.nextAllowedIndex(
            chipValues, denominationIndex, delta, config.activeLines(), isItemMode(), config.paytable());
        if (next == denominationIndex) {
            denyAction(player, text("slots.no-safe-denomination"));
            return;
        }
        denominationIndex = next;
        playClick(delta > 0 ? 1.2f : 0.9f);
        renderControls();
        renderInfo();
    }

    private boolean isItemMode() {
        CurrencyProvider provider = getCurrencyProvider();
        return provider == null || provider.getMode() != CurrencyMode.VAULT;
    }

    // ---- spin ------------------------------------------------------------

    private void handleSpin() {
        if (!passesOverflowBankGate()) {
            return;
        }
        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            denomUnits,
            config.columns(),
            config.activeLines(),
            isItemMode(),
            config.paytable(),
            SlotsRandomSource.production(),
            underwriting,
            this::attemptDebit);

        switch (attempt) {
            case SlotsSpinController.SpinAttempt.Rejected rejected ->
                handleRejectedSpin(rejected.reason(), rejected.dealerDecision());
            case SlotsSpinController.SpinAttempt.Accepted accepted -> {
                controller.beginAnimating();
                displayedWin = -1L;
                playLeverPull();
                renderMachineControls();
                renderControls();
                renderInfo();
                startAnimation(new SlotsCallbackGuard.SpinToken(playerId, dealerId, accepted.generation()));
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

    private void startAnimation(SlotsCallbackGuard.SpinToken token) {
        cancelAnimationTask();
        // A still-counting meter from the previous spin would otherwise keep
        // repainting the slot underneath this spin's result.
        cancelWinMeterTask();
        SlotsOutcome outcome = controller.currentOutcome();
        final SlotsReelPlan plan = SlotsReelPlan.build(outcome, config.activeLines());
        final int columns = outcome.columns();
        final List<SlotsMath.LineResult> winners = winningLines(outcome);
        final long[] elapsed = {0L};
        final boolean[] landed = new boolean[columns];
        final boolean[] anticipationAnnounced = {false};
        final int[] revealIndex = {0};

        seedReelDisplay(columns);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!SlotsCallbackGuard.isValid(token, playerId, dealerId, controller.generation())) {
                    cancel();
                    animationTask = null;
                    return;
                }
                long tick = elapsed[0];

                for (int reel = 0; reel < columns; reel++) {
                    if (landed[reel]) {
                        continue;
                    }
                    if (plan.isStopped(reel, tick)) {
                        landed[reel] = true;
                        lockReel(reel, outcome);
                        playReelStop(reel, columns);
                        continue;
                    }
                    if (plan.advancesAt(reel, tick)) {
                        advanceReel(reel);
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
                        }
                    } else if (revealIndex[0] < winners.size()) {
                        if (sinceReveal >= revealIndex[0] * perLine) {
                            SlotsMath.LineResult win = winners.get(revealIndex[0]);
                            revealIndex[0]++;
                            paintOutcomeGrid(outcome);
                            highlightLine(win, outcome);
                            playLineReveal(revealIndex[0], winners.size());
                        }
                    } else {
                        long finaleStart = winners.size() * perLine;
                        if (sinceReveal == finaleStart) {
                            paintOutcomeGrid(outcome);
                            for (SlotsMath.LineResult win : winners) {
                                highlightLine(win, outcome);
                            }
                            playFinale(controller.pendingPayoutAmount());
                        }
                        if (sinceReveal >= finaleStart + SlotsTiming.ALL_LINES_FINALE_TICKS) {
                            cancel();
                            animationTask = null;
                            settle(token);
                        }
                    }
                }

                elapsed[0] = tick + SlotsTiming.TICK_INTERVAL;
            }
        };
        animationTask = runnable.runTaskTimer(plugin, SlotsTiming.TICK_INTERVAL, SlotsTiming.TICK_INTERVAL);
    }

    private List<SlotsMath.LineResult> winningLines(SlotsOutcome outcome) {
        List<SlotsMath.LineResult> winners = new ArrayList<>();
        for (SlotsMath.LineResult result : SlotsMath.evaluateActiveLines(outcome, config.activeLines(), config.paytable())) {
            if (result.winning()) {
                winners.add(result);
            }
        }
        return winners;
    }

    private void seedReelDisplay(int columns) {
        reelDisplay = new SlotsSymbol[columns][SlotsGeometry.ROWS];
        for (int col = 0; col < columns; col++) {
            for (int row = 0; row < SlotsGeometry.ROWS; row++) {
                reelDisplay[col][row] = randomCosmeticSymbol(col);
            }
            paintReel(col);
        }
    }

    /** Scrolls one reel down by a symbol, feeding a fresh one in at the top. */
    private void advanceReel(int col) {
        for (int row = SlotsGeometry.ROWS - 1; row > 0; row--) {
            reelDisplay[col][row] = reelDisplay[col][row - 1];
        }
        reelDisplay[col][0] = randomCosmeticSymbol(col);
        paintReel(col);
    }

    private void lockReel(int col, SlotsOutcome outcome) {
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            reelDisplay[col][row] = outcome.symbolAt(row, col);
        }
        paintReel(col);
    }

    private SlotsSymbol randomCosmeticSymbol(int col) {
        return SlotsSpinGenerator.sampleSymbol(col, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    private void paintReel(int col) {
        int columns = config.columns();
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            SlotsSymbol symbol = reelDisplay[col][row];
            addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), ChatColor.WHITE,
                SlotsGeometry.gridSlot(columns, row, col));
        }
    }

    private void paintOutcomeGrid(SlotsOutcome outcome) {
        int columns = outcome.columns();
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                SlotsSymbol symbol = outcome.symbolAt(row, col);
                addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), ChatColor.WHITE,
                    SlotsGeometry.gridSlot(columns, row, col));
            }
        }
    }

    /** Lights only the cells that actually matched -- the run, not the whole line. */
    private void highlightLine(SlotsMath.LineResult win, SlotsOutcome outcome) {
        int columns = outcome.columns();
        for (int col = 0; col < win.runLength(); col++) {
            int row = win.payline().rowAt(col, columns);
            setGlowingItem(
                SlotsGeometry.gridSlot(columns, row, col),
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
    }

    /**
     * Retried only when the player clicks the blocked spin control. The
     * retry consumes that click even when it succeeds, so resolving an old
     * obligation can never also debit a new spin as a side effect of the
     * same interaction. The retained amount is re-attempted exactly as-is,
     * never recomputed.
     */
    private void attemptSettlementRetry() {
        long payout = controller.pendingPayoutAmount();
        SlotsSettlementResult result = controller.retrySettlement(
            this::creditPlayerDirect,
            amount -> queuePayout(amount, PayoutMessages.committedResultContext("Slots")));
        reportSettlement(result, payout);
    }

    private void reportSettlement(SlotsSettlementResult result, long payout) {
        displayedWin = payout;
        renderMachineControls();
        renderControls();
        renderInfo();
        if (payout > 0) {
            animateWinMeter(payout);
        }

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
     * through {@link OverflowBankService}, which fills the inventory, applies
     * the player's Bank/Drop preference to the rest, caps physical drops and
     * banks the final remainder -- so an item win larger than the inventory
     * is a completed payout rather than a failed one. Only a failed durable
     * bank write returns {@code false}, which hands the obligation on to the
     * controller's durable-queue step instead of losing it.
     */
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
        if (!(event.getInventory().getHolder() instanceof SlotsMachine) || !event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!SessionRegistry.isRegistered(playerId, this)) {
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
     * {@link #handleSpin()} -- whatever this table's state shows at the
     * moment of termination already reflects the true, finished outcome of
     * the last accepted spin.
     */
    @Override
    public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
        if (closeFlag) {
            return;
        }
        closeFlag = true;

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
            // books close here too. Idempotent: if the round had already
            // settled normally, there is no open commitment left to settle.
            controller.settleBudgetOnTermination(underwriting);
            queuePayoutDurableOnly(controller.pendingPayoutAmount(), context);
        } else {
            // Nothing is owed -- a pregame exit, an already-resolved round, or
            // a forfeit. Release any promise so the funds are not stranded.
            controller.settleBudgetOnTermination(underwriting);
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
    }

    private void cancelAnimationTask() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    private void cancelWinMeterTask() {
        if (winMeterTask != null) {
            winMeterTask.cancel();
            winMeterTask = null;
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
