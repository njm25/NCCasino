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
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;
import org.nc.nccasino.session.TerminationAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One player's independent Slots table: a personal 54-slot view backed by
 * the explicit {@link SlotsSessionState} lifecycle. A dealer mob may have
 * any number of these open concurrently, one per player, dispatched by
 * {@link SlotsInventory}.
 *
 * <p>Financial contract: a debit ({@link WagerTransaction#tryWithdraw}) is
 * always attempted and confirmed <em>before</em> any outcome is generated.
 * The instant that debit succeeds, {@link SlotsSpinGenerator} produces one
 * immutable {@link SlotsOutcome} and the payout owed is computed and stored
 * -- everything from that point on (the animation, the highlight, the
 * eventual credit) is just carrying out an already-decided result. There is
 * no route back from a committed result to a refundable pregame state (see
 * {@link SlotsStateMachine}).
 */
public class SlotsMachine extends DealerInventory implements TerminableSession {

    private static final int[][] GRID_SLOTS = {
        {12, 13, 14},
        {21, 22, 23},
        {30, 31, 32}
    };
    private static final int EXIT_SLOT = 45;
    private static final int PREV_DENOM_SLOT = 47;
    private static final int SPIN_SLOT = 49;
    private static final int NEXT_DENOM_SLOT = 51;
    private static final int PAYTABLE_SLOT = 53;
    private static final int WAGER_INFO_SLOT = 4;
    private static final int TOTAL_BET_SLOT = 40;
    private static final int BALANCE_SLOT = 46;
    private static final int LAST_WIN_SLOT = 48;

    /** All five lines hitting SEVEN -- the maximum possible multiplier sum, used only to probe for payout overflow before a spin's debit is accepted. */
    private static final SlotsOutcome MAX_PAYOUT_PROBE_OUTCOME = new SlotsOutcome(new SlotsSymbol[][] {
        {SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN},
        {SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN},
        {SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN}
    });

    private final UUID playerId;
    private final Player player;
    private final Nccasino plugin;
    private final String internalName;
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private final SlotsInventory slotsInventory;
    private final double[] chipValues;
    private final List<Integer> scheduledTaskIds = new ArrayList<>();

    private int denominationIndex = 0;
    private SlotsSessionState state = SlotsSessionState.IDLE;
    private long generation = 0;
    private SlotsOutcome currentOutcome;
    private long pendingPayoutAmount = 0;
    private long lastWinAmount = -1;
    private boolean closeFlag = false;

    public SlotsMachine(UUID dealerId, Player player, Nccasino plugin, String internalName, SlotsInventory slotsInventory) {
        super(player.getUniqueId(), 54, plugin.getLocalization().text(player, "slots.title"));
        this.dealerId = dealerId;
        this.playerId = player.getUniqueId();
        this.player = player;
        this.plugin = plugin;
        this.internalName = internalName;
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.slotsInventory = slotsInventory;
        this.chipValues = loadChipValues();

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
        renderFrame();
        renderIdleGrid();
        renderControls();
        renderInfo();
    }

    // ---- rendering -----------------------------------------------------

    private void renderFrame() {
        for (int slot = 0; slot < 54; slot++) {
            if (isGridSlot(slot) || isReservedSlot(slot)) {
                continue;
            }
            addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, " ", slot);
        }
    }

    private boolean isGridSlot(int slot) {
        for (int[] row : GRID_SLOTS) {
            for (int gridSlot : row) {
                if (gridSlot == slot) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReservedSlot(int slot) {
        return slot == EXIT_SLOT || slot == PREV_DENOM_SLOT || slot == SPIN_SLOT || slot == NEXT_DENOM_SLOT
            || slot == PAYTABLE_SLOT || slot == WAGER_INFO_SLOT || slot == TOTAL_BET_SLOT
            || slot == BALANCE_SLOT || slot == LAST_WIN_SLOT;
    }

    private void renderIdleGrid() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addItemAndLore(Material.GRAY_STAINED_GLASS_PANE, 1, text("slots.grid-empty"), GRID_SLOTS[row][col]);
            }
        }
    }

    private void renderControls() {
        boolean locked = !isReadyForSpin();
        addItemAndLore(
            Material.ARROW,
            1,
            text("slots.previous-denomination"),
            PREV_DENOM_SLOT
        );
        addItemAndLore(
            locked ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
            1,
            locked ? text("slots.spin-locked") : text("slots.spin"),
            SPIN_SLOT
        );
        addItemAndLore(
            Material.ARROW,
            1,
            text("slots.next-denomination"),
            NEXT_DENOM_SLOT
        );
        addItemAndLore(
            Material.SPRUCE_DOOR,
            1,
            text("slots.exit"),
            EXIT_SLOT
        );

        List<String> paytableLore = new ArrayList<>();
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            paytableLore.add(text("slots.paytable-line", "symbol", text(symbolKey(symbol)), "multiplier", symbol.multiplier()));
        }
        addItemAndLore(Material.KNOWLEDGE_BOOK, 1, text("slots.paytable"), PAYTABLE_SLOT, paytableLore.toArray(new String[0]));
    }

    private void renderInfo() {
        double denomination = chipValues[denominationIndex];
        long denomUnits = Math.max(0L, Math.round(denomination));
        long totalBet;
        try {
            totalBet = SlotsMath.totalBet(denomUnits);
        } catch (ArithmeticException e) {
            totalBet = Long.MAX_VALUE;
        }

        addItemAndLore(
            Material.SUNFLOWER,
            1,
            text("slots.wager-denomination"),
            WAGER_INFO_SLOT,
            text("slots.wager-denomination-value", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, denomination))
        );
        addItemAndLore(
            Material.GOLD_INGOT,
            1,
            text("slots.total-bet"),
            TOTAL_BET_SLOT,
            text("slots.total-bet-value", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, totalBet))
        );

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            int balance = provider.getBalance(player, internalName);
            addItemAndLore(
                Material.EMERALD,
                1,
                text("slots.balance"),
                BALANCE_SLOT,
                text("slots.balance-value", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, balance))
            );
        } else {
            addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, " ", BALANCE_SLOT);
        }

        if (lastWinAmount < 0) {
            addItemAndLore(Material.PAPER, 1, text("slots.last-win-none"), LAST_WIN_SLOT);
        } else if (lastWinAmount == 0) {
            addItemAndLore(Material.PAPER, 1, text("slots.last-win-loss"), LAST_WIN_SLOT);
        } else {
            addItemAndLore(
                Material.PAPER,
                1,
                text("slots.last-win"),
                LAST_WIN_SLOT,
                text("slots.last-win-value", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, lastWinAmount))
            );
        }
    }

    private void renderReelSpin(int col) {
        for (int row = 0; row < 3; row++) {
            SlotsSymbol cosmetic = SlotsSpinGenerator.sampleSymbol(bound -> ThreadLocalRandom.current().nextInt(bound));
            addItemAndLore(cosmetic.material(), 1, text(symbolKey(cosmetic)), GRID_SLOTS[row][col]);
        }
    }

    private void renderReelFinal(int col) {
        for (int row = 0; row < 3; row++) {
            SlotsSymbol symbol = currentOutcome.symbolAt(row, col);
            addItemAndLore(symbol.material(), 1, text(symbolKey(symbol)), ChatColor.WHITE, GRID_SLOTS[row][col]);
        }
    }

    private void highlightWinningLines() {
        for (SlotsMath.LineResult result : SlotsMath.evaluateAllLines(currentOutcome)) {
            if (!result.winning()) {
                continue;
            }
            for (int[] cell : result.payline().cells()) {
                addItemAndLore(
                    result.symbol().material(),
                    1,
                    text(symbolKey(result.symbol())),
                    ChatColor.GOLD,
                    GRID_SLOTS[cell[0]][cell[1]]
                );
            }
        }
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
        switch (slot) {
            case EXIT_SLOT -> handleExit();
            case SPIN_SLOT -> handleSpin();
            case PREV_DENOM_SLOT -> handleChangeDenomination(-1);
            case NEXT_DENOM_SLOT -> handleChangeDenomination(1);
            case PAYTABLE_SLOT -> handlePaytable();
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
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            player.sendMessage(text("slots.paytable-line", "symbol", text(symbolKey(symbol)), "multiplier", symbol.multiplier()));
        }
    }

    private void handleChangeDenomination(int delta) {
        if (!isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }
        int size = chipValues.length;
        denominationIndex = ((denominationIndex + delta) % size + size) % size;
        playDefaultSound(player);
        renderInfo();
    }

    private boolean isReadyForSpin() {
        return state == SlotsSessionState.IDLE || state == SlotsSessionState.RESOLVED;
    }

    private void handleSpin() {
        if (!isReadyForSpin()) {
            denyAction(player, text("slots.spin-locked"));
            return;
        }

        long denomUnits = Math.max(0L, Math.round(chipValues[denominationIndex]));
        if (denomUnits <= 0) {
            denyAction(player, text("slots.invalid-denomination"));
            return;
        }

        long totalBetUnits;
        try {
            totalBetUnits = SlotsMath.totalBet(denomUnits);
            // The worst-case payout (all five lines hitting the richest
            // symbol) multiplies by far more than totalBet's fixed x5, so a
            // wager that passes totalBet's overflow check can still overflow
            // the payout once an outcome is generated. Reject it up front,
            // before any debit happens, rather than discovering the
            // overflow only after money has already left the player.
            SlotsMath.totalPayout(MAX_PAYOUT_PROBE_OUTCOME, denomUnits);
        } catch (ArithmeticException e) {
            denyAction(player, text("slots.bet-too-large"));
            return;
        }

        boolean withdrawn = attemptDebit(totalBetUnits);
        if (!withdrawn) {
            denyAction(player, text("slots.insufficient-funds"));
            return;
        }

        state = SlotsStateMachine.transition(state, SlotsSessionState.DEBIT_ACCEPTED);
        generation++;
        long myGeneration = generation;

        SlotsOutcome outcome = SlotsSpinGenerator.generate(SlotsRandomSource.production());
        currentOutcome = outcome;
        state = SlotsStateMachine.transition(state, SlotsSessionState.RESULT_COMMITTED);
        pendingPayoutAmount = SlotsMath.totalPayout(outcome, denomUnits);

        state = SlotsStateMachine.transition(state, SlotsSessionState.ANIMATING);
        playDefaultSound(player);
        renderControls();
        renderInfo();
        startAnimation(new SlotsCallbackGuard.SpinToken(playerId, dealerId, myGeneration));
    }

    private boolean attemptDebit(long totalBetUnits) {
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
        final long[] elapsed = {0L};
        final boolean[] reelStopped = {false, false, false};
        final boolean[] highlighted = {false};

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!SlotsCallbackGuard.isValid(token, playerId, dealerId, generation)) {
                    cancel();
                    return;
                }

                elapsed[0] += SlotsSpinPlan.TICKER_INTERVAL;
                for (int reel = 0; reel < 3; reel++) {
                    if (!reelStopped[reel] && SlotsSpinPlan.isReelStopped(reel, elapsed[0])) {
                        reelStopped[reel] = true;
                        renderReelFinal(reel);
                        playReelStopSound();
                    } else if (!reelStopped[reel]) {
                        renderReelSpin(reel);
                    }
                }

                if (!highlighted[0] && SlotsSpinPlan.isHighlightActive(elapsed[0])) {
                    highlighted[0] = true;
                    highlightWinningLines();
                }

                if (elapsed[0] >= SlotsSpinPlan.SETTLE_TICK) {
                    cancel();
                    settle(token);
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, SlotsSpinPlan.TICKER_INTERVAL, SlotsSpinPlan.TICKER_INTERVAL);
        scheduledTaskIds.add(task.getTaskId());
    }

    private void playReelStopSound() {
        if (SoundHelper.getSoundSafely("block.note_block.hat", player) != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    private void settle(SlotsCallbackGuard.SpinToken token) {
        if (!SlotsCallbackGuard.isValid(token, playerId, dealerId, generation)) {
            return;
        }
        if (state != SlotsSessionState.ANIMATING) {
            return;
        }
        state = SlotsStateMachine.transition(state, SlotsSessionState.SETTLING);

        long payout = pendingPayoutAmount;
        boolean delivered = true;
        if (payout > 0) {
            delivered = creditPlayerDirect(payout);
            if (!delivered) {
                delivered = queuePayout(payout, PayoutMessages.committedResultContext("Slots"));
            }
        }

        lastWinAmount = payout;
        state = SlotsStateMachine.transition(state, SlotsSessionState.RESOLVED);
        pendingPayoutAmount = 0;

        renderControls();
        renderInfo();

        if (payout > 0) {
            if (delivered) {
                player.sendMessage(text("slots.win", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, payout)));
            } else {
                player.sendMessage(text("slots.payout-pending"));
            }
            if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        } else {
            player.sendMessage(text("slots.loss"));
        }
    }

    private boolean creditPlayerDirect(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (player == null || !player.isOnline()) {
            return false;
        }
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            if (provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
                return vaultProvider.deposit(player, internalName, MoneyHelper.bd(amount));
            }
            int intAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
            return provider.deposit(player, internalName, intAmount);
        }

        Material mat = plugin.getCurrency(internalName);
        if (mat == null) {
            return false;
        }
        int intAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        depositRawMaterial(mat, intAmount);
        return true;
    }

    private void depositRawMaterial(Material mat, int amount) {
        int fullStacks = amount / 64;
        int remainder = amount % 64;
        for (int i = 0; i < fullStacks; i++) {
            dropLeftover(player.getInventory().addItem(new ItemStack(mat, 64)));
        }
        if (remainder > 0) {
            dropLeftover(player.getInventory().addItem(new ItemStack(mat, remainder)));
        }
    }

    private void dropLeftover(HashMap<Integer, ItemStack> leftover) {
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    /**
     * Attempts a live delivery first (the player is actively at the
     * machine, so this is expected to succeed); on failure durably queues
     * the exact amount, with a last-resort direct-credit retry only if the
     * durable write itself fails -- mirroring the same fallback shape used
     * for Blackjack/Mines payouts.
     */
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
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().severe("[NCCasino] Slots pending payout failed to persist for " + playerId
                + " at dealer " + internalName + " (amount=" + amount + "); attempting direct credit fallback.");
            boolean fallback = creditPlayerDirect(amount);
            if (!fallback) {
                plugin.getLogger().severe("[NCCasino] Slots payout of " + amount + " for " + playerId
                    + " at dealer " + internalName + " could not be delivered or durably queued.");
            }
            return fallback;
        }
        return true;
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

        GameTerminationPolicy.SlotsPhase phase = switch (state) {
            case IDLE, RESOLVED -> GameTerminationPolicy.SlotsPhase.PREGAME;
            case TERMINATED -> GameTerminationPolicy.SlotsPhase.RESOLVED;
            default -> GameTerminationPolicy.SlotsPhase.RESULT_COMMITTED;
        };
        TerminationAction action = GameTerminationPolicy.slots(reason, phase);

        if (action == TerminationAction.QUEUE_KNOWN_PAYOUT) {
            String context = reason == ExitReason.PLUGIN_DISABLE
                ? PayoutMessages.committedResultContext("Slots")
                : PayoutMessages.disconnectedMidGameContext("Slots");
            queuePayoutDurableOnly(pendingPayoutAmount, context);
        }
        // FORFEIT (kicked): the debited stake stays with the house, nothing to give back.
        // NO_ACTION: nothing was owed (pregame) or it was already resolved through normal play.

        cancelScheduledTasks();
        if (state != SlotsSessionState.TERMINATED) {
            state = SlotsStateMachine.transition(state, SlotsSessionState.TERMINATED);
        }
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
                + " at dealer " + internalName + " (amount=" + amount + "); attempting direct credit fallback.");
            if (amount > 0 && player != null && player.isOnline() && !creditPlayerDirect(amount)) {
                plugin.getLogger().severe("[NCCasino] Slots payout of " + amount + " for " + playerId
                    + " at dealer " + internalName + " could not be delivered or durably queued.");
            }
        }
    }

    private void cancelScheduledTasks() {
        for (int id : scheduledTaskIds) {
            Bukkit.getScheduler().cancelTask(id);
        }
        scheduledTaskIds.clear();
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
