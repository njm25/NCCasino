package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.currency.VaultCurrencyProvider;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.AttributeHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Deck;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class BlackjackInventory extends DealerInventory implements TerminableSession {

    private final Nccasino plugin; // Reference to the main plugin
    private final Map<Integer, Double> chipValues; // Track chip values by fixed inventory slot
    private final String internalName; // Internal name for config lookup
    private final CurrencyMode currencyMode;
    private final String currencyName;
    private final Map<UUID, Integer> playerSeats; // Track player seats
    private final Map<UUID, Map<Integer, Double>> playerBets; // Track player bets by slot number
    private final Map<UUID, List<Double>> lastBetAmounts; // Track the last bet amounts placed by the player
    private boolean gameActive; // Track whether the game is active
    private int countdownTaskId; // Task ID for the countdown timer
    private UUID currentPlayerId; // Track the current player
    private Iterator<UUID> playerIterator; // Iterator for player turns
    private final Map<UUID, Integer> playerCardCounts = new HashMap<>(); // Track number of cards each player has
    private final Map<UUID, Boolean> playerDone = new HashMap<>(); // Track whether the player is done (stood or busted)
    // Per-player hand queue -- exactly one BlackjackHand per player in this
    // phase (index 0), but shaped as a list from the start so real
    // splitting (a later phase) doesn't have to retrofit this state shape.
    // activeHandIndex is a live pointer (looked up, not captured across
    // ticks) -- see ensureActiveHand/activeHand.
    private final Map<UUID, List<BlackjackHand>> playerHands = new HashMap<>();
    private final Map<UUID, Integer> activeHandIndex = new HashMap<>();
    private final List<Card> dealerHand = new ArrayList<>();
    // Wager selection vs. commitment (see the table redesign plan): a
    // chip/all-in click only sets a pending selected amount here, moving no
    // funds and touching no ledger -- only a bet-spot click commits. Full
    // commit-side wiring (bet-spot click pushing onto
    // pregameWagerIncrements, Undo Last/Undo All reading from it) is a
    // later phase; today's Undo Last/Undo All still operate on the
    // pre-existing playerBets/lastBetAmounts maps below.
    private final Map<UUID, Double> selectedWager = new HashMap<>();
    // Committed wager-increment ledger, per player -- laid down now so a
    // later phase doesn't have to retrofit this state shape. Unused until
    // bet-spot-click commit wiring lands.
    private final Map<UUID, java.util.Deque<Double>> pregameWagerIncrements = new HashMap<>();
    private final Object turnLock = new Object(); // Lock object for turn actions
    private final Map<UUID, Boolean> playerTurnActive = new HashMap<>();
    private Deck deck; // Declare the deck as a class variable
    private Boolean firstFin=true;
    private Boolean sittable=true;
    public UUID dealerId;
    // Per-player localized views onto this shared table. The legacy
    // `inventory` (from DealerInventory) stays the internal/default render
    // target and every mutation fans out from it to each of these -- see
    // getOrCreateView/BlackjackView. Never a source of canonical state.
    private final Map<UUID, BlackjackView> views = new HashMap<>();
    // Canonical record of what the table's current status is, kept in sync
    // everywhere it's set so a freshly opened view can reproduce it exactly.
    // Null until set for the first time. The 5-seat layout has no single
    // dedicated "status clock" slot in the active phase (see the table
    // redesign plan's slot map) -- the pregame/countdown text still renders,
    // per seated player, at BlackjackSlotLayout.pregameCountdownSlot; the
    // active-phase "whose turn" text is conveyed by card glow (see
    // refreshCardGlow) plus the existing chat messages, not an item anymore.
    private String leverKey;
    private Object[] leverPlaceholders = new Object[0];
    // Last value the countdown clock was rendered with, read by
    // captureFrame for late-view bootstrap. The running task only has this
    // as a Runnable-local variable otherwise.
    private int countdownSecondsRemaining;
    // Canonical dealer-head position -- BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT
    // (8) until the start-transition U-path animation (see
    // startDealerInspection) delivers the dealer to
    // BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT (53), one slot at a time.
    // Tracked as its own field (dealer-position-as-state, per the table
    // redesign plan) rather than derived from phase, and mirrored onto
    // BlackjackFrame so a late viewer bootstraps the dealer where it
    // actually is, mid-animation or not.
    private int dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
    // True from the moment the pregame countdown hits zero until the
    // readiness gate opens and activateGame() actually deals -- see
    // beginStartTransition/isReadyToDeal. Drives BlackjackFrame.Phase.START_TRANSITION.
    private boolean startTransitionActive = false;
    // Seated players whose private door-conceal animation has finished
    // (the final step, door arrived at slot 45) during the current
    // start-transition attempt. Cleared at the start of every
    // beginStartTransition call; read by isReadyToDeal's polling gate.
    private final Set<UUID> startTransitionDoorConcealComplete = new HashSet<>();

    // ---- Insurance (Phase 5) --------------------------------------------
    // Its own explicit phase, never mixed into turn state -- see
    // beginInsurancePhase/performDealerPeekThenProceed. Table-owned
    // canonical deadline (like the plan describes for turn deadlines too):
    // a real generation-guarded scheduled resolution, never the private
    // per-viewer BlackjackAnimationRun category used for cosmetic lobby
    // guidance loops.
    private boolean insurancePhaseActive = false;
    /** Seated players with a committed wager when the insurance offer opened -- every one of them is eligible, including natural-blackjack holders. */
    private final Set<UUID> insuranceEligiblePlayers = new HashSet<>();
    /** Eligible players who have answered (Yes, No, or defaulted via timeout) this round. */
    private final Set<UUID> insuranceDecided = new HashSet<>();
    /** Players who took insurance, and how much they staked (already debited) -- paid out 2:1+stake only if the dealer's peek finds blackjack. */
    private final Map<UUID, Double> insuranceStakes = new HashMap<>();
    private int insuranceTaskId = -1;
    private int insuranceSecondsRemaining;
    /** {@code dealers.<name>.insurance.enabled}, loaded once at construction (default true). */
    private final boolean insuranceEnabled;
    /** {@code dealers.<name>.insurance.timeout-seconds}, clamped [1,60] (default {@link BlackjackTiming#INSURANCE_TIMEOUT_DEFAULT_SECONDS}). */
    private final int insuranceTimeoutSeconds;

    // ---- Player-turn timer (slot 46) -------------------------------------
    // Table-owned canonical deadline, same shape as the insurance countdown
    // above: a real scheduled resolution that keeps running regardless of
    // any single viewer's inventory, guarded by roundGeneration + playerId +
    // handToken so a stale tick can never auto-Stand a hand that has since
    // moved on. Starts only once a decision is actually actionable (never
    // during card-deal/split animations); resets on Hit (a new decision on
    // the same hand bumps handToken, which invalidates the previous timer
    // task on its own next tick, and startNextPlayerTurn/handleHit start a
    // fresh one). When disabled, slot 46 simply never leaves its brown
    // edge-glass idle state.
    /** {@code dealers.<name>.turn-timer.enabled}, loaded once at construction (default true). */
    private final boolean turnTimerEnabled;
    /** {@code dealers.<name>.turn-timer.timeout-seconds}, clamped [1,60] (default 20). */
    private final int turnTimerTimeoutSeconds;
    private int turnTimerTaskId = -1;

    // ---- Real splitting (Phase 6) -----------------------------------------
    // Config loaded once at construction (see loadBooleanConfig et al.);
    // playerHands/activeHandIndex above already carry the actual per-player
    // hand queue. See BlackjackSplitEligibility/BlackjackSplitMatching/
    // BlackjackMaxHands for the pure eligibility/matching mechanics this
    // config feeds.
    /** {@code dealers.<name>.splitting.enabled}, default true. */
    private final boolean splittingEnabled;
    /** {@code dealers.<name>.splitting.matching}, default SAME_RANK. */
    private final BlackjackSplitMatching splitMatching;
    /** {@code dealers.<name>.splitting.max-hands}, default UNBOUNDED. Applies per player, never table-wide. */
    private final BlackjackMaxHands maxHands;
    /** {@code dealers.<name>.splitting.double-after-split}, default true. */
    private final boolean doubleAfterSplit;
    /** {@code dealers.<name>.splitting.aces.resplit}, default true. */
    private final boolean acesResplitAllowed;
    /** {@code dealers.<name>.splitting.aces.hit}, default false. */
    private final boolean acesHitAllowed;
    /** {@code dealers.<name>.splitting.aces.double}, default false. */
    private final boolean acesDoubleAllowed;
    /** {@code dealers.<name>.splitting.split-21-is-blackjack}, default false -- see BlackjackRules#classify's 3-arg overload. */
    private final boolean split21IsBlackjack;
    // Shared/table-owned split animation lifecycle (slide-out / deal / deal
    // / park / reactivate) -- lives in sharedAnimationRun above (viewerId
    // null); this flag/queue tracks which hand-activation is waiting on that
    // animation to finish before its actions become live, so one viewer
    // closing their inventory can never cancel it (see cancelSharedAnimation
    // vs cancelPrivateAnimation).
    private boolean splitAnimationInFlight = false;
    // Animation infrastructure (Phase 2) -- scaffolding only, nothing here
    // is triggered from real gameplay yet (that's a later phase). Private
    // (per-viewer) animation runs: chair guide, wager guide, bet-spot
    // blink, door reveal/conceal, action guide. At most one per viewer;
    // cancelled on that viewer's own close/quit/seat-change -- see
    // cancelPrivateAnimation.
    private final Map<UUID, BlackjackAnimationRun> privateAnimationRuns = new HashMap<>();
    // Per-viewer generation counter, bumped every time that viewer's
    // private animation must be invalidated (close/quit/seat-change, or a
    // fresh guidance cycle superseding an older one). A run captures the
    // value in effect when it was scheduled; a step whose captured value no
    // longer matches the live counter is stale and must no-op.
    private final Map<UUID, Integer> viewerAnimationGeneration = new HashMap<>();
    // The single shared/table-owned animation run (dealer U-path
    // inspection, split slide/park/reactivate), if one is currently active.
    // Never cancelled just because one viewer closes their inventory --
    // only by a genuinely table-wide event (reset/cancel/delete, dealer
    // replacement, plugin shutdown) or its own natural completion. See
    // cancelSharedAnimation.
    private BlackjackAnimationRun sharedAnimationRun;
    // Canonical, locale-neutral record of whether the dealer's hidden-card
    // placeholder has actually been rendered yet -- set only inside the
    // scheduled hidden-card render callback and cleared on reveal and on
    // every reset/cancel/delete path, so a view bootstrapped before that
    // callback has run never shows the placeholder early.
    private boolean hiddenCardPlaceholderVisible;
    // Round-generation counter, bumped every time a brand-new round starts
    // (activateGame) or the table is reset/cancelled/deleted. Captured by
    // every scheduled per-hand callback (hit evaluation, double-down
    // completion) alongside handToken below, so a callback left over from a
    // stale round for a still-seated player can never mutate the new round.
    private long roundGeneration = 0;
    // Per-player token bumped every time that seat's turn is resolved
    // (stand, bust, hit-to-21, double-down completion, leave-during-turn).
    // A scheduled callback captures the token in effect when it was
    // scheduled; if the token has since moved on, the hand it was meant for
    // is already resolved and the callback must no-op rather than mutate
    // playerDone/playerTurnActive or advance the turn again. This is what
    // makes handleDoubleDown atomic (see its doc comment).
    private final Map<UUID, Integer> handToken = new HashMap<>();
    // Bumped every time a turn-advance is initiated -- either the delayed
    // one scheduled by startNextPlayerTurnWithDelay, or an immediate one
    // forced by a player leaving/disconnecting mid-turn (advanceTurnNow).
    // A delayed advance captures the sequence in effect when it was
    // scheduled; if something else has already advanced the turn by the
    // time it fires, its captured value is stale and it must no-op instead
    // of advancing a second time and skipping whoever the immediate
    // advance already moved to.
    private long turnSequence = 0;

    public BlackjackInventory(UUID dealerId, Nccasino plugin, String internalName) {
        super(
            dealerId,
            54,
            plugin.getLocalization().text(plugin.getLocalization().getServerDefault(), "blackjack.title")
        ); // Using 54 slots for start menu
        this.plugin = plugin; // Store the plugin reference
        this.chipValues = new HashMap<>(); // Initialize chip values storage
        this.internalName = internalName; // Store the internal name
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        this.gameActive = false; // Initialize game active flag
        this.playerSeats = new HashMap<>(); // Initialize player seats storage
        this.playerBets = new HashMap<>(); // Initialize player bets storage
        this.lastBetAmounts = new HashMap<>(); // Initialize last bet amounts storage
        this.countdownTaskId = -1; // Initialize countdown task ID
        // Initialize the start menu
        this.dealerId = dealerId;

        int standOn17Chance = loadClampedIntConfig("stand-on-17", 100, 0, 100);
        int numberOfDecks = loadClampedIntConfig("number-of-decks", 6, 1, 10000);

        this.deck = new Deck(numberOfDecks); // Initialize the deck
        loadChipValuesFromConfig(); // Load chip values from config

        this.insuranceEnabled = loadBooleanConfig("insurance.enabled", true);
        this.insuranceTimeoutSeconds = loadClampedIntConfig("insurance.timeout-seconds", BlackjackTiming.INSURANCE_TIMEOUT_DEFAULT_SECONDS, 1, 60);
        this.turnTimerEnabled = loadBooleanConfig("turn-timer.enabled", true);
        this.turnTimerTimeoutSeconds = loadClampedIntConfig("turn-timer.timeout-seconds", 20, 1, 60);
        this.splittingEnabled = loadBooleanConfig("splitting.enabled", true);
        this.splitMatching = loadSplitMatchingConfig("splitting.matching", BlackjackSplitMatching.SAME_RANK);
        this.maxHands = loadMaxHandsConfig("splitting.max-hands", BlackjackMaxHands.unbounded());
        this.doubleAfterSplit = loadBooleanConfig("splitting.double-after-split", true);
        this.acesResplitAllowed = loadBooleanConfig("splitting.aces.resplit", true);
        this.acesHitAllowed = loadBooleanConfig("splitting.aces.hit", false);
        this.acesDoubleAllowed = loadBooleanConfig("splitting.aces.double", false);
        this.split21IsBlackjack = loadBooleanConfig("splitting.split-21-is-blackjack", false);

        // Every load*Config helper above only stages corrections/defaults
        // via config.set -- a single controlled save here (rather than one
        // saveConfig() call per field) is what actually persists them, so
        // an admin who never touched this dealer's settings still gets a
        // config.yml populated with the effective values on first load.
        if (configDirty) {
            plugin.saveConfig();
            configDirty = false;
        }

       registerListener();
       plugin.addInventory(dealerId, this);
    }

    /**
     * Staged by every {@code load*Config} helper below whenever it corrects
     * or defaults a value via {@code config.set} -- the constructor issues
     * exactly one {@code saveConfig()} after every field has been loaded,
     * instead of one per field.
     */
    private boolean configDirty = false;

    /** Reads {@code dealers.<internalName>.<key>} as a boolean, writing (and warning about) a corrected/defaulted value back if the key is unset or not actually a boolean. */
    private boolean loadBooleanConfig(String key, boolean defaultValue) {
        String path = "dealers." + internalName + "." + key;
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultValue);
            configDirty = true;
            return defaultValue;
        }
        Object raw = plugin.getConfig().get(path);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        plugin.getLogger().warning("Invalid boolean for " + path + ": '" + raw + "' -- resetting to default " + defaultValue + ".");
        plugin.getConfig().set(path, defaultValue);
        configDirty = true;
        return defaultValue;
    }

    /** Reads {@code dealers.<internalName>.<key>} as an int clamped to [{@code min}, {@code max}], writing back (and warning about) a corrected value -- default if unset/unparsable, clamped if out of range -- so the persisted config always matches what's in effect. */
    private int loadClampedIntConfig(String key, int defaultValue, int min, int max) {
        String path = "dealers." + internalName + "." + key;
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultValue);
            configDirty = true;
            return defaultValue;
        }
        Integer parsed = parseConfiguredInt(plugin.getConfig().get(path));
        if (parsed == null) {
            plugin.getLogger().warning("Invalid integer for " + path + ": '" + plugin.getConfig().get(path) + "' -- resetting to default " + defaultValue + ".");
            plugin.getConfig().set(path, defaultValue);
            configDirty = true;
            return defaultValue;
        }
        if (parsed < min) {
            plugin.getLogger().warning("Value for " + path + " (" + parsed + ") is below the minimum " + min + " -- clamping.");
            plugin.getConfig().set(path, min);
            configDirty = true;
            return min;
        }
        if (parsed > max) {
            plugin.getLogger().warning("Value for " + path + " (" + parsed + ") is above the maximum " + max + " -- clamping.");
            plugin.getConfig().set(path, max);
            configDirty = true;
            return max;
        }
        return parsed;
    }

    /** Reads {@code dealers.<internalName>.<key>} as a {@link BlackjackSplitMatching}, case-insensitively, warning and defaulting on anything else. */
    private BlackjackSplitMatching loadSplitMatchingConfig(String key, BlackjackSplitMatching defaultValue) {
        String path = "dealers." + internalName + "." + key;
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultValue.name());
            configDirty = true;
            return defaultValue;
        }
        Object raw = plugin.getConfig().get(path);
        if (raw instanceof String) {
            try {
                return BlackjackSplitMatching.valueOf(((String) raw).trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to the warning/reset below
            }
        }
        plugin.getLogger().warning("Invalid splitting.matching for " + path + ": '" + raw + "' -- resetting to default " + defaultValue + ".");
        plugin.getConfig().set(path, defaultValue.name());
        configDirty = true;
        return defaultValue;
    }

    /** Reads {@code dealers.<internalName>.<key>} as a {@link BlackjackMaxHands} -- {@code "UNBOUNDED"} (case-insensitive) or an integer &gt;= 2 -- warning and defaulting on anything else. */
    private BlackjackMaxHands loadMaxHandsConfig(String key, BlackjackMaxHands defaultValue) {
        String path = "dealers." + internalName + "." + key;
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultValue.configValue());
            configDirty = true;
            return defaultValue;
        }
        Object raw = plugin.getConfig().get(path);
        if (raw instanceof String && "UNBOUNDED".equalsIgnoreCase(((String) raw).trim())) {
            return BlackjackMaxHands.unbounded();
        }
        Integer parsed = parseConfiguredInt(raw);
        if (parsed != null && parsed >= 2) {
            return BlackjackMaxHands.limited(parsed);
        }
        plugin.getLogger().warning("Invalid splitting.max-hands for " + path + ": '" + raw + "' -- must be 'UNBOUNDED' or an integer >= 2, resetting to default " + defaultValue.configValue() + ".");
        plugin.getConfig().set(path, defaultValue.configValue());
        configDirty = true;
        return defaultValue;
    }

    /** Best-effort int parse of a raw config value that may already be a {@link Number} or a numeric {@link String}; null if neither. */
    private static Integer parseConfiguredInt(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }


      @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event){
        // Defensive fallback only: with per-player views wired up, nobody
        // has this legacy inventory open directly anymore (see the `views`
        // field), but keep this reachable in case it ever is.
        if (event.getInventory() == this.getInventory()){
            Player player=(Player)event.getPlayer();
            if(player.getInventory() !=null){
                onViewOpened(player);
            }
        }

      }

    // Load chip values from the plugin config
    private void loadChipValuesFromConfig() {
        List<Double> configuredValues = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            configuredValues.add(plugin.getChipValue(internalName, i));
        }
        this.chipValues.putAll(ChipSlots.assign(configuredValues));
    }

    // CurrencyProvider helper for this dealer/game
    private CurrencyProvider getCurrencyProvider() {
        if (plugin.getCurrencyManager() == null) {
            return null;
        }
        return plugin.getCurrencyManager().getProvider(internalName);
    }

    // Helper to determine if a stack represents this dealer's currency
    private boolean isCurrencyItem(ItemStack stack) {
        if (stack == null) return false;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return provider.isCurrencyItem(stack, internalName);
        }

        Material mat = plugin.getCurrency(internalName);
        return mat != null && stack.getType() == mat;
    }

    // ---- Seat-order helpers -------------------------------------------

    /** The player currently occupying {@code seatSlot}, or null if empty. */
    private UUID seatOwnerAt(int seatSlot) {
        for (Map.Entry<UUID, Integer> entry : playerSeats.entrySet()) {
            if (entry.getValue() == seatSlot) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Seated players in table order (9, 18, 27, 36), never HashMap
     * iteration order -- used everywhere dealing/turn sequencing must be
     * deterministic (initial deal, turn order, late-view bootstrap).
     */
    private List<UUID> orderedSeatedPlayers() {
        List<UUID> ordered = new ArrayList<>();
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID owner = seatOwnerAt(seatSlot);
            if (owner != null) {
                ordered.add(owner);
            }
        }
        return ordered;
    }

    private double totalBet(UUID playerId) {
        Map<Integer, Double> bets = playerBets.get(playerId);
        return bets == null ? 0.0 : bets.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    // ---- Per-player hand-queue helpers (Phase 1: exactly one hand, index 0) ----

    /** The active {@link BlackjackHand} for {@code playerId}, or null if they have no hands yet (pregame). */
    private BlackjackHand activeHand(UUID playerId) {
        List<BlackjackHand> hands = playerHands.get(playerId);
        if (hands == null || hands.isEmpty()) {
            return null;
        }
        int idx = activeHandIndex.getOrDefault(playerId, 0);
        if (idx < 0 || idx >= hands.size()) {
            idx = 0;
        }
        return hands.get(idx);
    }

    /** The active hand's cards, or an empty (never null) list before any hand has been created. */
    private List<Card> activeHandCards(UUID playerId) {
        BlackjackHand hand = activeHand(playerId);
        return hand == null ? List.of() : hand.getCards();
    }

    /**
     * The active {@link BlackjackHand} for {@code playerId}, creating one
     * (seeded with their currently committed wager) on first use -- so
     * dealing the very first card lazily establishes the hand rather than
     * requiring every seat to be pre-populated with an empty hand.
     */
    private BlackjackHand ensureActiveHand(UUID playerId) {
        List<BlackjackHand> hands = playerHands.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (hands.isEmpty()) {
            hands.add(new BlackjackHand(totalBet(playerId)));
            activeHandIndex.put(playerId, 0);
        }
        int idx = activeHandIndex.getOrDefault(playerId, 0);
        if (idx < 0 || idx >= hands.size()) {
            idx = 0;
            activeHandIndex.put(playerId, 0);
        }
        return hands.get(idx);
    }

    // ---- Animation-run scaffolding (Phase 2) ---------------------------
    // Nothing below actually schedules a chair-guide/wager-guide/etc.
    // animation yet -- that wiring is a later phase. This is only the
    // bookkeeping + cancellation-scope machinery so that later phase
    // doesn't have to retrofit it.

    private int currentViewerAnimationGeneration(UUID playerId) {
        return viewerAnimationGeneration.getOrDefault(playerId, 0);
    }

    /** Invalidates any in-flight step scheduled under {@code playerId}'s current private animation generation. */
    private void bumpViewerAnimationGeneration(UUID playerId) {
        viewerAnimationGeneration.merge(playerId, 1, Integer::sum);
    }

    /**
     * Cancels {@code playerId}'s currently-running private animation (chair
     * guide, wager guide, bet-spot blink, door reveal/conceal, action
     * guide), if any, and bumps their animation generation so any in-flight
     * step from it no-ops. Never touches the shared/table-owned run --
     * that's the entire point of the private/shared split (see
     * BlackjackAnimationRun's class doc). Called on that viewer's own
     * close/quit/seat-change; safe to call even when no private animation
     * is currently running (e.g. every phase-2 call site today, since
     * nothing schedules one yet).
     */
    private void cancelPrivateAnimation(UUID playerId) {
        BlackjackAnimationRun run = privateAnimationRuns.remove(playerId);
        if (run != null) {
            run.cancel();
        }
        bumpViewerAnimationGeneration(playerId);
    }

    /**
     * Cancels the shared/table-owned animation run (dealer U-path
     * inspection, split slide/park/reactivate), if one is active. Only for
     * genuinely table-wide events (reset/cancel/delete, dealer
     * replacement, plugin shutdown) -- never call this just because one
     * viewer's inventory closed.
     */
    private void cancelSharedAnimation() {
        if (sharedAnimationRun != null) {
            sharedAnimationRun.cancel();
            sharedAnimationRun = null;
        }
        splitAnimationInFlight = false;
    }

    /** Cancels every currently-tracked animation (private and shared) -- for table-wide teardown only (reset/cancel/delete). */
    private void cancelAllAnimations() {
        for (BlackjackAnimationRun run : privateAnimationRuns.values()) {
            run.cancel();
        }
        privateAnimationRuns.clear();
        viewerAnimationGeneration.clear();
        cancelSharedAnimation();
    }

    /**
     * Derives the table-global phase the same way captureFrame does, for BlackjackAnimationRun bookkeeping.
     * INSURANCE must be checked before ACTIVE: gameActive flips true at the very start of activateGame(), well
     * before the insurance decision window opens, so a late viewer bootstrapping (or any animation-scope check)
     * during insurance previously saw ACTIVE and never restored the private insurance Yes/No UI or countdown.
     */
    private BlackjackFrame.Phase capturePhase() {
        if (insurancePhaseActive) {
            return BlackjackFrame.Phase.INSURANCE;
        }
        if (gameActive) {
            return BlackjackFrame.Phase.ACTIVE;
        }
        if (startTransitionActive) {
            return BlackjackFrame.Phase.START_TRANSITION;
        }
        if (countdownTaskId != -1) {
            return BlackjackFrame.Phase.COUNTDOWN;
        }
        return BlackjackFrame.Phase.LOBBY;
    }

    /** Bumps {@code playerId}'s private-animation generation and returns the new value, for a freshly-started run to capture. */
    private int bumpAndGetViewerAnimationGeneration(UUID playerId) {
        bumpViewerAnimationGeneration(playerId);
        return currentViewerAnimationGeneration(playerId);
    }

    /**
     * Whether a step captured under {@code capturedGeneration} for {@code playerId} is stale. Deliberately does not
     * also compare phase (unlike {@link BlackjackAnimationRun#isStale}) -- the lobby/wager guidance animations in
     * this phase must survive a LOBBY-to-COUNTDOWN transition (guidance keeps looping for a still-unseated/still-
     * selecting viewer even after some other player's bet has started the table's countdown), so their own
     * seated/gameActive checks are the more precise guard; a later phase's phase-scoped animations (e.g. the
     * start-transition dealer U-path) can lean on BlackjackAnimationRun.isStale's phase comparison instead, since
     * those really are only ever valid for one fixed phase.
     */
    private boolean isStaleViewerAnimation(UUID playerId, int capturedGeneration) {
        return currentViewerAnimationGeneration(playerId) != capturedGeneration;
    }

    /** Writes {@code item} into only {@code playerId}'s own open view, if they have one -- never the legacy inventory or any other viewer. */
    private void renderPrivateItem(UUID playerId, int slot, ItemStack item) {
        BlackjackView view = views.get(playerId);
        if (view != null) {
            view.getInventory().setItem(slot, item);
        }
    }

    /** Like {@link #createCustomItem(Material, String, int)}, but pre-glowing. */
    private ItemStack createGlowingCustomItem(Material material, String name, int amount) {
        ItemStack item = createCustomItem(material, name, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The decorative brown edge-glass shown at {@link BlackjackSlotLayout#UNSEATED_EDGE_GLASS_SLOT} for an unseated viewer. */
    private ItemStack buildBrownEdgeGlassItem() {
        ItemStack item = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---- Pregame bottom bar: unseated vs. seated, per-viewer -----------
    // The bottom row (45-53) shows a genuinely different layout depending
    // on whether THIS viewer has sat down yet -- not just on the table's
    // global phase -- per the table redesign plan's slot map. An unseated
    // viewer sees door@45 + brown edge glass@46 and nothing else, even if
    // other players are already seated and betting; only once they sit
    // does their own view reveal Undo All/Undo Last/chips/All In/door.

    /** Unseated pregame/countdown bottom bar: door@45, brown edge glass@46, everything else background. */
    private void paintUnseatedBottomBar(Inventory target, Player viewer) {
        target.setItem(BlackjackSlotLayout.UNSEATED_EXIT_SLOT, createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1));
        target.setItem(BlackjackSlotLayout.UNSEATED_EDGE_GLASS_SLOT, buildBrownEdgeGlassItem());
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT + 2; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            target.setItem(slot, buildBackgroundPaneItem());
        }
    }

    /** Seated pregame/countdown bottom bar: Undo All@45, Undo Last@46, 5 chips@47-51, All In@52, door@53. */
    private void paintSeatedBottomBar(Inventory target, Player viewer, UUID playerId) {
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            target.setItem(slot, buildSeatedBottomBarSlotItem(slot, playerId, viewer));
        }
    }

    /** Builds whichever seated-bottom-bar item belongs at {@code slot} -- the single source of truth reused by the full paint, the door-reveal animation, and per-chip refreshes. */
    private ItemStack buildSeatedBottomBarSlotItem(int slot, UUID playerId, Player viewer) {
        if (slot == BlackjackSlotLayout.UNDO_ALL_SLOT) {
            return createCustomItem(Material.BARRIER, localize(viewer, "blackjack.undo-all"), 1);
        }
        if (slot == BlackjackSlotLayout.UNDO_LAST_SLOT) {
            return createCustomItem(Material.WIND_CHARGE, localize(viewer, "blackjack.undo-last"), 1);
        }
        if (ChipSlots.isChipSlot(slot)) {
            Double value = chipValues.get(slot);
            if (value == null) {
                return buildBackgroundPaneItem();
            }
            Double selected = selectedWager.get(playerId);
            String chipName = plugin.getChipDisplayName(currencyMode, currencyName, value);
            return BlackjackWagerSelection.isSelected(selected, value)
                ? createEnchantedItem(plugin.getCurrency(internalName), chipName, (int) (double) value)
                : createCustomItem(plugin.getCurrency(internalName), chipName, (int) (double) value);
        }
        if (slot == BlackjackSlotLayout.ALL_IN_SLOT) {
            return createCustomItem(Material.SNIFFER_EGG, localize(viewer, "blackjack.all-in"), 1);
        }
        if (slot == BlackjackSlotLayout.PREGAME_EXIT_SLOT) {
            return createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
        }
        return buildBackgroundPaneItem();
    }

    // ---- Chair guidance (private, per unseated viewer) ------------------

    /** Schedules the first chair-guidance cycle to begin CHAIR_GUIDANCE_START_DELAY_TICKS from now, per the table redesign plan. */
    private void scheduleChairGuidanceStart(UUID playerId) {
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> startChairGuidance(playerId, myGeneration), BlackjackTiming.CHAIR_GUIDANCE_START_DELAY_TICKS);
    }

    private void startChairGuidance(UUID playerId, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || playerSeats.containsKey(playerId) || gameActive || !views.containsKey(playerId)) {
            return;
        }
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runChairGuidanceCycle(playerId, myGeneration);
    }

    /**
     * Schedules one full pass of BlackjackChairGuidancePlan, then reschedules itself once that pass completes --
     * re-deriving which seats are filled each time (never baking a stale seat list into one long-running plan),
     * so a seat filling mid-loop is reflected in the very next pass.
     */
    private void runChairGuidanceCycle(UUID playerId, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || playerSeats.containsKey(playerId) || gameActive || !views.containsKey(playerId)) {
            return;
        }
        Set<Integer> filledSeats = new HashSet<>(playerSeats.values());
        List<BlackjackAnimationStep> steps = BlackjackChairGuidancePlan.build(filledSeats, BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS);
        if (steps.isEmpty()) {
            return; // every seat is filled -- nothing left to guide
        }
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleViewerAnimation(playerId, myGeneration) || playerSeats.containsKey(playerId) || gameActive) {
                    return;
                }
                applyChairGuidanceStep(playerId, step);
            }, step.getDelayTicks());
        }
        long cycleTicks = BlackjackChairGuidancePlan.cycleDurationTicks(filledSeats, BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runChairGuidanceCycle(playerId, myGeneration), Math.max(cycleTicks, 1L));
    }

    private void applyChairGuidanceStep(UUID playerId, BlackjackAnimationStep step) {
        Player viewer = Bukkit.getPlayer(playerId);
        boolean glowing = step.getKind() == BlackjackAnimationStep.Kind.GLOW_ON;
        ItemStack item = glowing
            ? createGlowingCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.chair-guidance-hint"), 1)
            : createCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.click-sit"), 1);
        renderPrivateItem(playerId, step.getSlot(), item);
    }

    // ---- Door reveal on sit + wager guidance (private, per seated-but-not-yet-selecting viewer) ----

    /** Slides the bottom bar from door+glass to the full seated wager bar for the viewer who just sat, then hands off to wager guidance. */
    private void startWagerBarReveal(UUID playerId) {
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));

        List<BlackjackAnimationStep> steps = BlackjackWagerRevealPlan.reveal(BlackjackTiming.WAGER_REVEAL_STEP_TICKS);
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleViewerAnimation(playerId, myGeneration)) {
                    return;
                }
                applyWagerRevealStep(playerId, step);
            }, step.getDelayTicks());
        }
        long totalTicks = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).getDelayTicks() + BlackjackTiming.WAGER_REVEAL_STEP_TICKS;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleViewerAnimation(playerId, myGeneration)) {
                return;
            }
            startWagerGuidance(playerId);
        }, totalTicks);
    }

    private void applyWagerRevealStep(UUID playerId, BlackjackAnimationStep step) {
        Player viewer = Bukkit.getPlayer(playerId);
        renderPrivateItem(playerId, step.getSlot(), buildSeatedBottomBarSlotItem(step.getSlot(), playerId, viewer));
    }

    /** Cycles glow left-to-right over the 5 chip slots until the viewer selects a denomination (or All In). */
    private void startWagerGuidance(UUID playerId) {
        if (!playerSeats.containsKey(playerId) || gameActive) {
            return;
        }
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runWagerGuidanceCycle(playerId, myGeneration);
    }

    private void runWagerGuidanceCycle(UUID playerId, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || !playerSeats.containsKey(playerId) || gameActive) {
            return;
        }
        Double selected = selectedWager.get(playerId);
        if (selected != null && selected > 0) {
            return; // a selection is pending -- the bet-spot blink owns the UI now, not wager guidance
        }
        List<BlackjackAnimationStep> steps = BlackjackWagerGuidancePlan.build(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS);
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleViewerAnimation(playerId, myGeneration)) {
                    return;
                }
                applyWagerGuidanceStep(playerId, step);
            }, step.getDelayTicks());
        }
        long cycleTicks = BlackjackWagerGuidancePlan.cycleDurationTicks(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runWagerGuidanceCycle(playerId, myGeneration), Math.max(cycleTicks, 1L));
    }

    private void applyWagerGuidanceStep(UUID playerId, BlackjackAnimationStep step) {
        Double value = chipValues.get(step.getSlot());
        if (value == null) {
            return;
        }
        Player viewer = Bukkit.getPlayer(playerId);
        boolean glowing = step.getKind() == BlackjackAnimationStep.Kind.GLOW_ON;
        String chipName = plugin.getChipDisplayName(currencyMode, currencyName, value);
        ItemStack item = createCustomItem(plugin.getCurrency(internalName), chipName, (int) (double) value);
        if (glowing) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(localize(viewer, "blackjack.wager-guidance-hint"));
                meta.setLore(lore);
                applyGlow(meta);
                item.setItemMeta(meta);
            }
        }
        renderPrivateItem(playerId, step.getSlot(), item);
    }

    // ---- Bet-spot blink (private, per viewer with a pending selection) --

    /** Blinks glow on the viewer's own bet spot, showing "Click to add {amount}", until they commit or select something else. */
    private void startBetSpotBlink(UUID playerId) {
        if (!playerSeats.containsKey(playerId)) {
            return;
        }
        int betSpotSlot = BlackjackSlotLayout.betSlipSlot(playerSeats.get(playerId));
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runBetSpotBlinkCycle(playerId, betSpotSlot, myGeneration);
    }

    private void runBetSpotBlinkCycle(UUID playerId, int betSpotSlot, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || !playerSeats.containsKey(playerId) || gameActive) {
            return;
        }
        Double selected = selectedWager.get(playerId);
        if (selected == null || selected <= 0) {
            return; // selection was cleared/consumed elsewhere -- nothing to blink about anymore
        }
        List<BlackjackAnimationStep> steps = BlackjackBetSpotBlinkPlan.build(betSpotSlot, BlackjackTiming.BET_SPOT_BLINK_STEP_TICKS);
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleViewerAnimation(playerId, myGeneration)) {
                    return;
                }
                applyBetSpotBlinkStep(playerId, betSpotSlot, step);
            }, step.getDelayTicks());
        }
        long cycleTicks = BlackjackBetSpotBlinkPlan.cycleDurationTicks(BlackjackTiming.BET_SPOT_BLINK_STEP_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runBetSpotBlinkCycle(playerId, betSpotSlot, myGeneration), Math.max(cycleTicks, 1L));
    }

    private void applyBetSpotBlinkStep(UUID playerId, int betSpotSlot, BlackjackAnimationStep step) {
        Player viewer = Bukkit.getPlayer(playerId);
        boolean glowing = step.getKind() == BlackjackAnimationStep.Kind.GLOW_ON;
        double selected = selectedWager.getOrDefault(playerId, 0.0);
        ItemStack item = createCustomItem(
            Material.BROWN_STAINED_GLASS_PANE,
            localize(viewer, "blackjack.click-to-add-wager", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, selected)),
            1
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            double committed = BlackjackWagerLedger.total(pregameWagerIncrements.computeIfAbsent(playerId, k -> new java.util.ArrayDeque<>()));
            List<String> lore = new ArrayList<>();
            if (committed > 0) {
                lore.add(localize(viewer, "blackjack.hand-wager-lore", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, committed)));
            }
            meta.setLore(lore);
            if (glowing) {
                applyGlow(meta);
            }
            item.setItemMeta(meta);
        }
        renderPrivateItem(playerId, betSpotSlot, item);
    }

    // ---- Start transition (door-conceal + dealer U-path) ----------------
    // Runs once the pregame countdown hits zero, replacing the old
    // immediate countdown-to-activateGame jump. Two animations run
    // concurrently: a private door-conceal per seated viewer, and the
    // shared/table-owned dealer U-path inspection -- see the table
    // redesign plan's "Start-transition sequencing" paragraph for why the
    // dealer's bottom-row leg (47-53) is gated behind every conceal
    // finishing (both animations want that same slot range) while the
    // top/left leg runs freely alongside door-conceal.

    /**
     * Entry point, called instead of activateGame() the moment the
     * countdown reaches zero. Cancels the countdown clock for good, force-
     * cancels any still-in-progress lobby animation for every seated
     * player (a player who hadn't committed a wager yet just doesn't get a
     * hand this round), then kicks off door-conceal + dealer inspection
     * concurrently and starts polling the readiness gate.
     */
    private void beginStartTransition() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        clearPregameCountdownFromAllViews();

        roundGeneration++;
        long myRoundGeneration = roundGeneration;
        startTransitionActive = true;
        startTransitionDoorConcealComplete.clear();

        List<UUID> seatedPlayers = new ArrayList<>(playerSeats.keySet());
        for (UUID playerId : seatedPlayers) {
            // Stops wager guidance / bet-spot blink for anyone still
            // mid-selection -- only players with a committed wager get
            // cards/turns, per the table redesign plan.
            cancelPrivateAnimation(playerId);
        }
        selectedWager.clear();

        for (UUID playerId : seatedPlayers) {
            startDoorConceal(playerId, myRoundGeneration);
        }
        startDealerInspection(myRoundGeneration);

        scheduleDealReadinessCheck(myRoundGeneration);
    }

    // ---- Per-viewer door-conceal ----------------------------------------

    private void startDoorConceal(UUID playerId, long myRoundGeneration) {
        int myAnimGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, myRoundGeneration, myAnimGeneration, BlackjackFrame.Phase.START_TRANSITION));

        List<BlackjackAnimationStep> steps = BlackjackWagerRevealPlan.conceal(BlackjackTiming.WAGER_REVEAL_STEP_TICKS);
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (roundGeneration != myRoundGeneration || isStaleViewerAnimation(playerId, myAnimGeneration)) {
                    return;
                }
                applyDoorConcealStep(playerId, step);
                if (step.getSlot() == BlackjackSlotLayout.UNSEATED_EXIT_SLOT) {
                    // Final step: the door has arrived back at 45.
                    startTransitionDoorConcealComplete.add(playerId);
                }
            }, step.getDelayTicks());
        }
    }

    private void applyDoorConcealStep(UUID playerId, BlackjackAnimationStep step) {
        Player viewer = Bukkit.getPlayer(playerId);
        int slot = step.getSlot();
        ItemStack item;
        if (slot == BlackjackSlotLayout.UNSEATED_EXIT_SLOT) {
            item = createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
        } else if (slot == BlackjackSlotLayout.UNSEATED_EDGE_GLASS_SLOT) {
            item = buildBrownEdgeGlassItem();
        } else {
            item = buildBackgroundPaneItem();
        }
        renderPrivateItem(playerId, slot, item);
    }

    // ---- Shared dealer U-path inspection ---------------------------------

    /**
     * Runs the dealer's U-path as a shared/table-owned animation (viewer =
     * null) -- per phase 2's cancellation-scope design, this must survive
     * any single viewer closing their inventory. The bottom-row leg
     * (47-53) is gated behind every seated viewer's door-conceal finishing
     * (a fixed worst-case delay, not an event wait) since both animations
     * want that same slot range; the top/left leg (8 down to 38) runs
     * concurrently with door-conceal, since it never touches 47-53.
     */
    private void startDealerInspection(long myRoundGeneration) {
        Set<Integer> wageredSeats = new HashSet<>();
        for (UUID playerId : playerSeats.keySet()) {
            double committed = BlackjackWagerLedger.total(pregameWagerIncrements.getOrDefault(playerId, new java.util.ArrayDeque<>()));
            if (committed > 0) {
                wageredSeats.add(playerSeats.get(playerId));
            }
        }

        List<BlackjackAnimationStep> path = BlackjackDealerInspectionPlan.build(
            wageredSeats, BlackjackTiming.DEALER_INSPECTION_STEP_TICKS, BlackjackTiming.DEALER_INSPECTION_SLOWDOWN_EXTRA_TICKS
        );

        BlackjackAnimationRun run = new BlackjackAnimationRun(null, myRoundGeneration, 0, BlackjackFrame.Phase.START_TRANSITION);
        sharedAnimationRun = run;

        long bottomRowGateTicks = BlackjackWagerRevealPlan.concealDurationTicks(BlackjackTiming.WAGER_REVEAL_STEP_TICKS);

        for (BlackjackAnimationStep step : path) {
            boolean isBottomRowLeg = step.getSlot() >= BlackjackSlotLayout.DEALER_CARD_ROW_FIRST_SLOT;
            long gateDelay = isBottomRowLeg ? bottomRowGateTicks : 0L;
            long scheduledDelay = step.getDelayTicks() + gateDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                    return;
                }
                applyDealerInspectionStep(step);
            }, scheduledDelay);
        }

        long lastStepDelay = path.isEmpty() ? 0L : path.get(path.size() - 1).getDelayTicks() + bottomRowGateTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                return;
            }
            // Natural completion -- a valid end for a shared run per phase 2's design (not a viewer-close).
            dealerHeadSlot = BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT; // safety net in case the last MOVE step wasn't exactly 53
            cancelSharedAnimation();
        }, lastStepDelay + BlackjackTiming.DEALER_INSPECTION_STEP_TICKS);
    }

    /** Moves the canonical dealerHeadSlot to {@code step}'s slot and re-renders the dealer head there for every view, clearing the vacated cell back to the felt. */
    private void applyDealerInspectionStep(BlackjackAnimationStep step) {
        int previousSlot = dealerHeadSlot;
        int newSlot = step.getSlot();
        dealerHeadSlot = newSlot;

        if (previousSlot != newSlot) {
            renderBackgroundToAllViews(previousSlot);
        }
        renderLocalizedToAllViews(newSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer");
    }

    // ---- Readiness gate before dealing -----------------------------------

    /**
     * Polls (runTaskLater retry loop bounded by roundGeneration, not a
     * busy-loop) until every start-transition condition is satisfied, then
     * flips out of START_TRANSITION and calls the existing, unchanged
     * activateGame() entry point. A stale roundGeneration (table
     * reset/cancelled, or a brand-new round already begun) simply stops
     * the polling loop instead of ever opening the gate.
     */
    private void scheduleDealReadinessCheck(long myRoundGeneration) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration) {
                return;
            }
            if (isReadyToDeal()) {
                startTransitionActive = false;
                activateGame();
            } else {
                scheduleDealReadinessCheck(myRoundGeneration);
            }
        }, BlackjackTiming.START_TRANSITION_READINESS_POLL_TICKS);
    }

    /**
     * True once: the dealer has actually arrived at its in-play head slot,
     * the pregame countdown clock is gone for good, and every seated
     * viewer's door-conceal has finished (which also implies none of them
     * are still in a door-revealed/wager-guide/bet-spot-blink lobby state,
     * since beginStartTransition force-cancelled all of those before
     * conceal ever started).
     */
    private boolean isReadyToDeal() {
        if (dealerHeadSlot != BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT) {
            return false;
        }
        if (countdownTaskId != -1) {
            return false;
        }
        for (UUID playerId : playerSeats.keySet()) {
            if (!startTransitionDoorConcealComplete.contains(playerId)) {
                return false;
            }
        }
        return true;
    }

    // ---- Wager selection vs. commitment ---------------------------------

    /**
     * Sets {@code playerId}'s pending selected wager amount -- moves no funds, pushes nothing to the ledger. Shared
     * by chip clicks and All In (the plan treats them identically: both only select, never commit).
     */
    private void selectWager(Player player, UUID playerId, double amount) {
        if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null) {
            player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD: {
                break;
            }
            case VERBOSE: {
                player.sendMessage(text(player, "blackjack.wager-selected", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, amount)));
                break;
            }
            case NONE: {
                break;
            }
        }
        selectedWager.put(playerId, amount);
        cancelPrivateAnimation(playerId); // stop wager guidance -- the blink takes over
        refreshWagerControlsForPlayer(playerId);
        startBetSpotBlink(playerId);
    }

    /**
     * Commits {@code amount} for {@code playerId}: debits their balance, then applies the ledger-side effects (see
     * {@link #commitWagerFundsAlreadyRemoved}). Used by the chip-selection commit path, where nothing has removed
     * any funds yet. NOT used for the cursor-drag-onto-bet-spot path -- there, {@code player.setItemOnCursor(null)}
     * already destroys the dragged physical stack, so calling this too would debit the same amount a second time
     * (previously a real bug: the cursor stack was deleted AND removeWagerFromInventory ran again, over-charging
     * the player). See handleBetClick's cursor-drag branch, which calls commitWagerFundsAlreadyRemoved instead.
     */
    private void commitWager(Player player, UUID playerId, int betSpotSlot, double amount) {
        removeWagerFromInventory(player, amount);
        commitWagerFundsAlreadyRemoved(playerId, betSpotSlot, amount);
    }

    /**
     * The funds-movement-free half of {@link #commitWager}: pushes {@code amount} onto the committed-increment
     * ledger and keeps the legacy playerBets/lastBetAmounts maps (still relied on by finishGame/refund/deal-order
     * logic) in sync with the ledger's new total. Callers are responsible for having already removed {@code amount}
     * from the player's balance through whichever mechanism applies (or, for a cursor-drag commit, having already
     * had it removed by the client destroying the dragged stack).
     */
    private void commitWagerFundsAlreadyRemoved(UUID playerId, int betSpotSlot, double amount) {
        java.util.Deque<Double> increments = pregameWagerIncrements.computeIfAbsent(playerId, k -> new java.util.ArrayDeque<>());
        BlackjackWagerLedger.commit(increments, amount);
        syncPlayerBetsFromLedger(playerId, betSpotSlot);
        lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(amount);
        updateItemLore(betSpotSlot, BlackjackWagerLedger.total(increments));
    }

    /** Recomputes playerBets' single entry for {@code playerId} from the ledger's current total. */
    private void syncPlayerBetsFromLedger(UUID playerId, int betSpotSlot) {
        double total = BlackjackWagerLedger.total(pregameWagerIncrements.getOrDefault(playerId, new java.util.ArrayDeque<>()));
        if (total > 0) {
            Map<Integer, Double> bets = playerBets.computeIfAbsent(playerId, k -> new HashMap<>());
            bets.clear();
            bets.put(betSpotSlot, total);
        } else {
            playerBets.remove(playerId);
        }
    }

    // ---- Turn-resolution guard (round generation + per-hand token) ----

    private int currentHandToken(UUID playerId) {
        return handToken.getOrDefault(playerId, 0);
    }

    /** Marks {@code playerId}'s current hand as resolved, invalidating any in-flight callback for it. */
    private void bumpHandToken(UUID playerId) {
        handToken.merge(playerId, 1, Integer::sum);
    }

    /**
     * True if a delayed per-hand callback captured at
     * {@code (capturedGeneration, capturedHandToken)} is no longer valid --
     * a new round has started, the round ended, the player left, or that
     * hand has since been resolved by something else. Every scheduled hit
     * or double-down evaluation must check this before mutating turn state.
     */
    private boolean isStaleHandCallback(UUID playerId, long capturedGeneration, int capturedHandToken) {
        return roundGeneration != capturedGeneration
            || !gameActive
            || !playerSeats.containsKey(playerId)
            || currentHandToken(playerId) != capturedHandToken;
    }

    // ---- Per-player BlackjackView plumbing --------------------------

    /**
     * Returns this player's localized table view, creating and immediately
     * painting it with the current round state if it doesn't exist yet --
     * so a late or returning viewer never sees a blank or stale board while
     * waiting for the next scheduled render.
     */
    public Inventory getOrCreateView(Player player) {
        UUID id = player.getUniqueId();
        BlackjackView existing = views.get(id);
        if (existing != null) {
            return existing.getInventory();
        }
        BlackjackView view = new BlackjackView(player, this, plugin);
        views.put(id, view);
        bootstrapView(view);
        return view.getInventory();
    }

    /**
     * Paints a freshly created view with the exact current table state,
     * captured as a {@link BlackjackFrame} so the same reconstruction logic
     * is unit-testable without Bukkit. Phase-dependent: pregame/countdown
     * paints the wager row and every seat's betting slip; active play
     * paints the exit+status-clock bottom bar, every dealt card (glowing
     * for the current player), and dynamic actions for the viewer only if
     * they are themselves the current, actionable player.
     */
    private void bootstrapView(BlackjackView view) {
        Inventory target = view.getInventory();
        Player viewer = Bukkit.getPlayer(view.getPlayerId());

        BlackjackFrame frame = captureFrame();
        // INSURANCE is an in-play, already-dealt table phase -- cards are
        // down, the active-phase bottom bar/bet spots apply, and the
        // insurance-specific private UI is layered on afterward below. Only
        // ACTIVE and INSURANCE ever reach here with dealt cards; treating
        // INSURANCE as pregame here previously repainted a late viewer with
        // the wager-selection board mid-round and dropped their unanswered
        // insurance Yes/No prompt and countdown entirely.
        boolean active = frame.phase() == BlackjackFrame.Phase.ACTIVE || frame.phase() == BlackjackFrame.Phase.INSURANCE;

        // Felt the whole board green first -- everything painted below
        // overlays it; anything left untouched (unused card-row slots,
        // unused action slots, etc.) stays this background.
        paintBackground(target);

        if (active) {
            target.setItem(BlackjackSlotLayout.ACTIVE_EXIT_SLOT, createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1));
            // Idle brown edge-glass fallback -- a late viewer bootstrapping
            // mid-decision doesn't get the live countdown text (its exact
            // seconds-remaining isn't part of BlackjackFrame), but never
            // sees an empty/background slot 46 either.
            target.setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem());
        } else if (playerSeats.containsKey(view.getPlayerId())) {
            paintSeatedBottomBar(target, viewer, view.getPlayerId());
        } else {
            paintUnseatedBottomBar(target, viewer);
        }

        target.setItem(frame.dealerHeadSlot(), createCustomItem(Material.CREEPER_HEAD, localize(viewer, "blackjack.dealer")));
        // While the hole card is hidden, presentation must never calculate
        // or expose a value derived from it -- only the publicly visible
        // portion of the canonical hand (the up-card) may feed the head
        // lore. frame.dealerHand() itself stays the full canonical hand
        // (peek logic elsewhere legitimately needs it); only rendering is
        // restricted. See BlackjackFrame#publiclyVisibleDealerHand.
        List<Card> visibleDealerHand = frame.publiclyVisibleDealerHand();
        if (!visibleDealerHand.isEmpty()) {
            applyHeadLore(target, frame.dealerHeadSlot(), calculateHandValueWithSoftCheck(visibleDealerHand), null, "blackjack.dealer", viewer);
        }
        if (active) {
            for (int i = 0; i < frame.dealerHand().size() && i < BlackjackSlotLayout.DEALER_CARD_CAPACITY; i++) {
                target.setItem(BlackjackSlotLayout.dealerCardSlot(i), buildCardItem(frame.dealerHand().get(i), viewer, false));
            }
            if (frame.dealerHoleCardHidden()) {
                target.setItem(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT, buildHiddenCardItem(viewer));
            }
        }

        // Every seat is painted unconditionally -- empty seats get the
        // default click-sit/click-bet items, occupied ones are overlaid
        // with the seated player's head and hand. This must hold in every
        // phase (lobby, countdown, active, and again after a reset).
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            BlackjackFrame.Seat seat = frame.seatAt(seatSlot);

            if (seat == null) {
                target.setItem(seatSlot, createCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.click-sit"), 1));
                if (!active) {
                    target.setItem(BlackjackSlotLayout.betSlipSlot(seatSlot), createCustomItem(Material.BROWN_STAINED_GLASS_PANE, localize(viewer, "blackjack.click-bet"), 1));
                }
                continue;
            }

            Player seatOwnerPlayer = Bukkit.getPlayer(seat.getPlayerId());
            if (seatOwnerPlayer != null) {
                target.setItem(seatSlot, createPlayerHeadItem(seatOwnerPlayer, 1));
                if (!seat.getHand().isEmpty()) {
                    applyHeadLore(target, seatSlot, calculateHandValueWithSoftCheck(seat.getHand()), seatOwnerPlayer.getName(), null, viewer);
                }
            } else {
                // Seat tracked but the player can't be resolved (e.g. just
                // disconnected) -- fall back to the empty-seat item rather
                // than leave the slot blank.
                target.setItem(seatSlot, createCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.click-sit"), 1));
            }

            if (active) {
                // Permanent bet spot stays visible throughout active play
                // (never cleared to background -- see
                // transitionBottomBarToActive), glowing solidly while it's
                // this seat's turn, matching refreshCardGlow's live fan-out.
                target.setItem(BlackjackSlotLayout.betSlipSlot(seatSlot), withWagerLore(buildActiveBetSpotItem(seat.isCurrentTurn()), seat.getWager(), viewer));
                for (int i = 0; i < seat.getHand().size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                    target.setItem(BlackjackSlotLayout.playerCardSlot(seatSlot, i), buildCardItem(seat.getHand().get(i), viewer, seat.isCurrentTurn()));
                }
            } else {
                target.setItem(BlackjackSlotLayout.betSlipSlot(seatSlot), withWagerLore(createCustomItem(Material.BROWN_STAINED_GLASS_PANE, localize(viewer, "blackjack.click-bet"), 1), seat.getWager(), viewer));
                if (frame.phase() == BlackjackFrame.Phase.COUNTDOWN) {
                    target.setItem(
                        BlackjackSlotLayout.pregameCountdownSlot(seatSlot),
                        createCustomItem(Material.CLOCK, localize(viewer, "blackjack.starts-in", "seconds", frame.countdownSeconds()), Math.max(frame.countdownSeconds(), 1))
                    );
                }
            }
        }

        if (active && view.getPlayerId().equals(currentPlayerId)) {
            for (Map.Entry<BlackjackAction, Integer> entry : currentPlayerActionLayout().entrySet()) {
                target.setItem(entry.getValue(), buildActionItem(entry.getKey(), viewer));
            }
        }

        if (frame.phase() == BlackjackFrame.Phase.INSURANCE
            && insuranceEligiblePlayers.contains(view.getPlayerId())
            && !insuranceDecided.contains(view.getPlayerId())) {
            // A late viewer (or one reopening) during the insurance window
            // must see their still-unanswered private Yes/No prompt and the
            // live countdown, not a blank/active-phase board -- the
            // deadline itself is table-owned canonical state (see
            // beginInsurancePhase) and keeps running regardless; this only
            // restores this one viewer's own rendering of it.
            renderInsurancePromptForPlayer(view.getPlayerId());
        }
    }

    private ItemStack withWagerLore(ItemStack item, double wager, Player viewer) {
        if (wager <= 0) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(localize(viewer, "blackjack.hand-wager-lore", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, wager)));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Captures the current canonical state as a locale-neutral, immutable
     * snapshot. Pure data derivation only -- no Bukkit types, no player
     * references, no localized strings -- so BlackjackFrameTest can exercise
     * it directly.
     */
    private BlackjackFrame captureFrame() {
        BlackjackFrame.Phase phase = capturePhase();

        List<BlackjackFrame.Seat> seats = new ArrayList<>();
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID playerId = seatOwnerAt(seatSlot);
            if (playerId == null) {
                continue;
            }
            boolean done = playerDone.getOrDefault(playerId, false);
            boolean currentTurn = playerId.equals(currentPlayerId);
            boolean actionable = currentTurn && !done && playerTurnActive.getOrDefault(playerId, false);

            List<BlackjackHand> hands = playerHands.get(playerId);
            List<BlackjackFrame.HandSnapshot> snapshots = new ArrayList<>();
            int activeIdx = 0;
            if (hands != null && !hands.isEmpty()) {
                activeIdx = activeHandIndex.getOrDefault(playerId, 0);
                if (activeIdx < 0 || activeIdx >= hands.size()) {
                    activeIdx = 0;
                }
                for (int i = 0; i < hands.size(); i++) {
                    BlackjackHand hand = hands.get(i);
                    snapshots.add(new BlackjackFrame.HandSnapshot(hand.getCards(), hand.getWager(), hand.isDone(), i == activeIdx));
                }
            } else {
                // Pregame: no BlackjackHand created yet, but Seat requires
                // at least one snapshot -- synthesize one from the
                // committed wager so a late viewer still sees it.
                snapshots.add(new BlackjackFrame.HandSnapshot(List.of(), totalBet(playerId), false, true));
            }

            seats.add(new BlackjackFrame.Seat(playerId, seatSlot, snapshots, activeIdx, currentTurn, actionable));
        }

        return new BlackjackFrame(
            phase,
            countdownSecondsRemaining,
            leverKey != null ? leverKey : "blackjack.game-info",
            List.of(leverPlaceholders),
            dealerHand,
            hiddenCardPlaceholderVisible,
            dealerHeadSlot,
            seats
        );
    }

    private String localize(Player viewer, String key, Object... placeholders) {
        return viewer != null ? plugin.getLocalization().text(viewer, key, placeholders) : text(key, placeholders);
    }

    void handleViewClick(int slot, Player player, InventoryClickEvent event) {
        handleClick(slot, player, event);
    }

    void onViewClosed(Player player, BlackjackView view) {
        views.remove(player.getUniqueId(), view);
        view.cleanupListener();
        // Private animations (chair guide, wager guide, bet-spot blink,
        // door reveal/conceal, action guide) belong to this one viewer --
        // stop unconditionally on their own close, regardless of why they
        // closed. The shared/table-owned run (dealer U-path, split
        // sequence) is deliberately untouched here -- see
        // cancelPrivateAnimation's doc and BlackjackAnimationRun's class doc.
        cancelPrivateAnimation(player.getUniqueId());
        handlePlayerClose(player);
    }

    /**
     * Reproduces the legacy handleInventoryOpen behavior (welcome message,
     * one-time initializeGameMenu on firstFin) from a per-player view's own
     * open event instead of the shared inventory's -- same 2-tick delay,
     * same preference-gated message, same firstFin gate.
     */
    void onViewOpened(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null) {
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        break;}
                    case VERBOSE:{
                        player.sendMessage(text(player, "blackjack.welcome"));
                        break;
                    }
                        case NONE:{
                        break;
                    }
                }
                if(firstFin){
                    firstFin=false;
                    initializeGameMenu();
                }
            }
        }, 2L);

        // Chair guidance begins CHAIR_GUIDANCE_START_DELAY_TICKS after the
        // table opens, per the table redesign plan -- startChairGuidance
        // itself no-ops if the viewer has already sat (or the game is
        // active) by the time it fires.
        scheduleChairGuidanceStart(player.getUniqueId());
    }

    /**
     * Refreshes only {@code playerId}'s own open view's chip slots (enchant
     * glint on whichever one currently matches their selectedWager entry)
     * after that entry changes -- deliberately not a table-wide fan-out
     * (unlike renderToAllViews and friends), since this state is private to
     * the selecting player and must never appear in anyone else's view or
     * the legacy inventory. Uses buildSeatedBottomBarSlotItem, the same
     * single source of truth the full seated-bar paint and the door-reveal
     * animation use.
     */
    private void refreshWagerControlsForPlayer(UUID playerId) {
        BlackjackView view = views.get(playerId);
        if (view == null) {
            return;
        }
        Player viewer = Bukkit.getPlayer(playerId);
        for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
            view.getInventory().setItem(slot, buildSeatedBottomBarSlotItem(slot, playerId, viewer));
        }
    }

    @SuppressWarnings("removal")
    private ItemStack buildHitSwordItem(String label) {
        ItemStack item = createCustomItem(Material.DIAMOND_SWORD, label);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.addAttributeModifier(AttributeHelper.getAttributeSafely("ATTACK_DAMAGE"),
            new AttributeModifier("foo", 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        // This is necessary as of 1.20.6
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /** Builds the dynamic action item for {@code action}, materials per the redesign spec. */
    private ItemStack buildActionItem(BlackjackAction action, Player viewer) {
        return buildActionItem(action, viewer, false);
    }

    /** Like {@link #buildActionItem(BlackjackAction, Player)}, but optionally pre-glowing -- for the action-guidance cycle. */
    private ItemStack buildActionItem(BlackjackAction action, Player viewer, boolean glowing) {
        ItemStack item;
        switch (action) {
            case HIT:
                item = buildHitSwordItem(localize(viewer, "blackjack.hit"));
                break;
            case STAND:
                item = createCustomItem(Material.SHIELD, localize(viewer, "blackjack.stand"));
                break;
            case DOUBLE_DOWN:
                item = createCustomItem(Material.NETHERITE_SCRAP, localize(viewer, "blackjack.double-down"));
                break;
            case SPLIT:
                item = createCustomItem(Material.SHEARS, localize(viewer, "blackjack.split"));
                break;
            default:
                throw new IllegalStateException("Unhandled BlackjackAction: " + action);
        }
        if (glowing) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyGlow(meta);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack buildCardItem(Card card, Player viewer, boolean glowing) {
        Material material = (card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS)
            ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack cardItem = new ItemStack(material, BlackjackRules.cardStackSize(card));
        ItemMeta meta = cardItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(localizedCardName(viewer, card));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (glowing) {
                applyGlow(meta);
            }
            cardItem.setItemMeta(meta);
        }
        return cardItem;
    }

    /**
     * Marks an item's meta as glowing -- prefers Spigot 1.21.11's item-meta
     * glint override, falling back to a harmless hidden enchantment if that
     * API is ever unavailable at runtime.
     */
    private void applyGlow(ItemMeta meta) {
        try {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } catch (Throwable unsupported) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    /** Localizes a card's display name via BlackjackFrame's pure "{rank} of {suit}" resolution. */
    private String localizedCardName(Player viewer, Card card) {
        return BlackjackFrame.localizedCardName(
            card,
            (key, args) -> localize(viewer, key, args),
            key -> localize(viewer, key)
        );
    }

    /**
     * A blank green-felt background pane -- matches RouletteInventory's
     * decorative-slot convention (GREEN_STAINED_GLASS_PANE, blank "§r"
     * name, no lore). Used to fill every slot that has no functional item
     * at the moment, instead of leaving it empty/null.
     */
    private ItemStack buildBackgroundPaneItem() {
        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            meta.setLore(null);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Fills every slot of {@code target} with the background pane; functional items are painted over it afterward. */
    private void paintBackground(Inventory target) {
        for (int slot = 0; slot < target.getSize(); slot++) {
            target.setItem(slot, buildBackgroundPaneItem());
        }
    }

    /** Clears {@code slot} back to the background pane (never to a bare null) in the legacy inventory and every open view. */
    private void renderBackgroundToAllViews(int slot) {
        renderToAllViews(slot, buildBackgroundPaneItem());
    }

    private ItemStack buildHiddenCardItem(Player viewer) {
        ItemStack hiddenCard = new ItemStack(Material.WHITE_STAINED_GLASS_PANE, 1);
        ItemMeta meta = hiddenCard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(localize(viewer, "blackjack.hidden-card"));
            hiddenCard.setItemMeta(meta);
        }
        return hiddenCard;
    }

    /**
     * Overwrites a head item's display name and card-value lore, preserving
     * whatever head item (player or dealer) is already at {@code slot}.
     * Exactly one of {@code literalName}/{@code nameKey} is non-null:
     * player heads keep their real (untranslated) Minecraft name; the
     * dealer head's name is localized per viewer.
     */
    private void applyHeadLore(Inventory target, int slot, String cardValueText, String literalName, String nameKey, Player viewer) {
        ItemStack headItem = target.getItem(slot);
        if (headItem == null || (headItem.getType() != Material.PLAYER_HEAD && headItem.getType() != Material.CREEPER_HEAD)) {
            return;
        }
        ItemMeta meta = headItem.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(literalName != null ? literalName : localize(viewer, nameKey));
        List<String> lore = new ArrayList<>();
        lore.add(localize(viewer, "blackjack.card-value", "value", cardValueText));
        meta.setLore(lore);
        headItem.setItemMeta(meta);
        target.setItem(slot, headItem);
    }

    /** Fans a head-lore update (player or dealer) out to the legacy inventory and every open view. */
    private void renderHeadLoreToAllViews(int slot, String cardValueText, String literalName, String nameKey) {
        applyHeadLore(inventory, slot, cardValueText, literalName, nameKey, null);
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            applyHeadLore(view.getInventory(), slot, cardValueText, literalName, nameKey, viewer);
        }
    }

    /** Writes a localized item to the legacy inventory and every open view, each resolved against its own locale. */
    private void renderLocalizedToAllViews(int slot, Material material, int amount, String key, Object... placeholders) {
        inventory.setItem(slot, createCustomItem(material, text(key, placeholders), amount));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(slot, createCustomItem(material, localize(viewer, key, placeholders), amount));
        }
    }

    /** Writes a locale-independent item (or null to clear) to the legacy inventory and every open view. */
    private void renderToAllViews(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        for (BlackjackView view : views.values()) {
            view.getInventory().setItem(slot, item == null ? null : item.clone());
        }
    }

    /** Deals one Card into the legacy inventory and every open view, each rendered in its own locale. */
    private void renderCardToAllViews(int slot, Card card, boolean glowing) {
        inventory.setItem(slot, buildCardItem(card, null, glowing));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(slot, buildCardItem(card, viewer, glowing));
        }
    }

    /** Draws the dealer's hidden-card placeholder into the legacy inventory and every open view. */
    private void renderHiddenCardToAllViews(int slot) {
        inventory.setItem(slot, buildHiddenCardItem(null));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(slot, buildHiddenCardItem(viewer));
        }
    }

    /**
     * Renders the pregame countdown into every seated player's own
     * {@link BlackjackSlotLayout#pregameCountdownSlot} -- the new 5-seat
     * layout has no single global clock slot (see the table redesign
     * plan's slot map); the countdown overlays each seat's first card cell
     * instead, safe by the same mutual-exclusion pattern used elsewhere
     * (no cards are dealt into that cell until well after the countdown
     * clears).
     */
    private void renderPregameCountdownToAllViews(int seconds) {
        leverKey = "blackjack.starts-in";
        leverPlaceholders = new Object[] {"seconds", seconds};
        countdownSecondsRemaining = seconds;
        int amount = Math.max(seconds, 1);
        for (int seatSlot : playerSeats.values()) {
            int slot = BlackjackSlotLayout.pregameCountdownSlot(seatSlot);
            inventory.setItem(slot, createCustomItem(Material.CLOCK, text("blackjack.starts-in", "seconds", seconds), amount));
            for (BlackjackView view : views.values()) {
                Player viewer = Bukkit.getPlayer(view.getPlayerId());
                view.getInventory().setItem(slot, createCustomItem(Material.CLOCK, localize(viewer, "blackjack.starts-in", "seconds", seconds), amount));
            }
        }
    }

    /** Clears every seated player's pregame countdown slot back to the felt and resets the idle status key. */
    private void clearPregameCountdownFromAllViews() {
        leverKey = null;
        leverPlaceholders = new Object[0];
        for (int seatSlot : playerSeats.values()) {
            renderBackgroundToAllViews(BlackjackSlotLayout.pregameCountdownSlot(seatSlot));
        }
    }

    // ---- Dynamic action-row rendering ----------------------------------

    /**
     * Computes the current player's available actions and their slots from
     * live canonical state (funds included) -- shared by
     * repaintActionsForCurrentPlayer (fan-out) and bootstrapView (late-view
     * catch-up) so both always agree. Empty unless there is a current
     * player whose turn is actionable and unresolved.
     */
    private Map<BlackjackAction, Integer> currentPlayerActionLayout() {
        if (currentPlayerId == null || playerDone.getOrDefault(currentPlayerId, false)
            || !playerTurnActive.getOrDefault(currentPlayerId, false)) {
            return Map.of();
        }
        Player currentPlayer = Bukkit.getPlayer(currentPlayerId);
        if (currentPlayer == null) {
            return Map.of();
        }
        BlackjackHand hand = activeHand(currentPlayerId);
        if (hand == null) {
            return Map.of();
        }
        return BlackjackActionLayout.layout(availableActionsForHand(currentPlayer, hand));
    }

    /**
     * The live available-action set for one hand, config- and funds-aware --
     * shared by currentPlayerActionLayout (rendering/click-validation) and
     * the split-ace auto-complete check (resolveHandAfterSplitAnimation), so
     * both agree on exactly what "actionable" means for a split-ace hand.
     * Split-ace hands (still on their 2-card first decision) use the ace
     * matrix from {@link BlackjackActionLayout#splitAceActions}; every other
     * hand (including non-ace split hands, and a split-ace hand after it has
     * itself been hit) uses the ordinary {@link BlackjackActionLayout#availableActions}.
     */
    private List<BlackjackAction> availableActionsForHand(Player player, BlackjackHand hand) {
        List<Card> cards = hand.getCards();
        int handValue = calculateHandValue(cards);
        if (handValue >= 21) {
            return List.of();
        }
        boolean initialTwoCardDecision = cards.size() == 2;
        List<BlackjackHand> hands = playerHands.getOrDefault(player.getUniqueId(), List.of());
        if (hand.isSplitFromAce() && initialTwoCardDecision) {
            boolean acesDoubleEffective = acesDoubleAllowed && doubleAfterSplit && hasEnoughWager(player, hand.getWager());
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            return BlackjackActionLayout.splitAceActions(acesHitAllowed, acesDoubleEffective, resplitEligible);
        }
        boolean canAffordDoubleDown = initialTwoCardDecision
            && (!hand.isFromSplit() || doubleAfterSplit)
            && hasEnoughWager(player, hand.getWager());
        boolean splitEligible = initialTwoCardDecision && splitEligibleForHand(player, hand, hands);
        return BlackjackActionLayout.availableActions(handValue, initialTwoCardDecision, canAffordDoubleDown, splitEligible);
    }

    /** Full split-eligibility check for {@code hand}, using this dealer's configured splitting rules. */
    private boolean splitEligibleForHand(Player player, BlackjackHand hand, List<BlackjackHand> hands) {
        return BlackjackSplitEligibility.isEligible(
            hand.getCards(), splitMatching, splittingEnabled,
            hasEnoughWager(player, hand.getWager()), hands.size(), maxHands, deck.remainingCards()
        );
    }

    /**
     * Clears the action row for everyone, then repaints it only into the
     * current player's own view if their turn is currently actionable.
     * Other viewers (and the current player themselves while an action is
     * processing) see an empty action row, leaving only the exit door and
     * status clock either side of it.
     *
     * <p>Deliberately never (re)starts action guidance or the turn-timer
     * deadline -- this is a pure repaint, safe to call for any reason
     * (invalid/stale click, a failed action, reopening a view) without
     * granting the current player extra decision time. When the live
     * layout is empty (turn ended/processing), guidance and the timer are
     * still torn down immediately here, since there is no decision left for
     * them to cover. Callers that are actually beginning a fresh actionable
     * decision (initial hand activation, a Hit that leaves the hand
     * actionable, a new split hand becoming active) must call
     * {@link #beginActionableDecision()} instead.
     */
    private void repaintActionsForCurrentPlayer() {
        for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
            renderBackgroundToAllViews(slot);
        }
        if (currentPlayerId == null) {
            return;
        }
        BlackjackView view = views.get(currentPlayerId);
        if (view == null) {
            return;
        }
        Player viewer = Bukkit.getPlayer(currentPlayerId);
        Map<BlackjackAction, Integer> layout = currentPlayerActionLayout();
        for (Map.Entry<BlackjackAction, Integer> entry : layout.entrySet()) {
            view.getInventory().setItem(entry.getValue(), buildActionItem(entry.getKey(), viewer));
        }
        if (layout.isEmpty()) {
            // No actionable decision right now (dealing, an action mid-flight,
            // not this viewer's turn) -- stop any lingering action-guidance
            // cycle and the turn-timer deadline immediately, rather than
            // waiting for either's next scheduled step/tick to notice and
            // self-cancel.
            cancelPrivateAnimation(currentPlayerId);
            stopTurnTimerTask();
        }
    }

    /**
     * Begins a genuinely new actionable decision for the current player:
     * repaints the action row (see {@link #repaintActionsForCurrentPlayer()})
     * and, only when that repaint left a non-empty layout, (re)starts action
     * guidance and the canonical turn-timer deadline. This is the only path
     * that may extend the player's decision deadline -- reserved for
     * initial hand activation, a Hit that leaves the hand actionable, and a
     * newly-activated split hand. Never call this to repaint after an
     * invalid click or a failed action; use
     * {@link #repaintActionsForCurrentPlayer()} for that.
     */
    private void beginActionableDecision() {
        repaintActionsForCurrentPlayer();
        if (!currentPlayerActionLayout().isEmpty()) {
            startActionGuidance(currentPlayerId);
            startTurnTimer(currentPlayerId);
        }
    }

    // ---- Action guidance (private, per current-turn viewer) -------------
    // One-at-a-time glow cycle across the current player's own available
    // action buttons, looping until they act (mirrors wager guidance's
    // pattern). Restarted (via bumpAndGetViewerAnimationGeneration) on
    // every beginActionableDecision call, so the available-action set
    // is always re-derived fresh rather than baked into one long-running
    // plan -- a Hit that removes Double Down from the layout is reflected
    // immediately.

    private void startActionGuidance(UUID playerId) {
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runActionGuidanceCycle(playerId, myGeneration);
    }

    private void runActionGuidanceCycle(UUID playerId, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || !playerId.equals(currentPlayerId) || !playerTurnActive.getOrDefault(playerId, false)) {
            return;
        }
        Map<BlackjackAction, Integer> layout = currentPlayerActionLayout();
        List<Integer> slots = new ArrayList<>(layout.values());
        if (slots.isEmpty()) {
            return;
        }
        List<BlackjackAnimationStep> steps = BlackjackActionGuidancePlan.build(slots, BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS);
        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleViewerAnimation(playerId, myGeneration) || !playerId.equals(currentPlayerId) || !playerTurnActive.getOrDefault(playerId, false)) {
                    return;
                }
                applyActionGuidanceStep(playerId, step);
            }, step.getDelayTicks());
        }
        long cycleTicks = BlackjackActionGuidancePlan.cycleDurationTicks(slots.size(), BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runActionGuidanceCycle(playerId, myGeneration), Math.max(cycleTicks, 1L));
    }

    private void applyActionGuidanceStep(UUID playerId, BlackjackAnimationStep step) {
        BlackjackAction action = null;
        for (Map.Entry<BlackjackAction, Integer> entry : currentPlayerActionLayout().entrySet()) {
            if (entry.getValue() == step.getSlot()) {
                action = entry.getKey();
                break;
            }
        }
        if (action == null) {
            return; // the layout moved on since this step was scheduled -- next full cycle will re-derive it
        }
        Player viewer = Bukkit.getPlayer(playerId);
        boolean glowing = step.getKind() == BlackjackAnimationStep.Kind.GLOW_ON;
        renderPrivateItem(playerId, step.getSlot(), buildActionItem(action, viewer, glowing));
    }

    // ---- Card-glow rendering --------------------------------------------

    /** Re-renders a seated player's already-dealt, visible cards with the given glow state. */
    private void reRenderHand(UUID playerId, boolean glowing) {
        if (playerId == null) {
            return;
        }
        Integer seatSlot = playerSeats.get(playerId);
        if (seatSlot == null) {
            return;
        }
        List<Card> hand = activeHandCards(playerId);
        for (int i = 0; i < hand.size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            renderCardToAllViews(BlackjackSlotLayout.playerCardSlot(seatSlot, i), hand.get(i), glowing);
        }
    }

    /** Turns glow off for the previous current player (if any) and on for the new one (if any) -- both their visible cards and their permanent bet spot. */
    private void refreshCardGlow(UUID previousPlayerId, UUID newCurrentPlayerId) {
        if (previousPlayerId != null && !previousPlayerId.equals(newCurrentPlayerId)) {
            reRenderHand(previousPlayerId, false);
            reRenderBetSpot(previousPlayerId, false);
        }
        if (newCurrentPlayerId != null) {
            reRenderHand(newCurrentPlayerId, true);
            reRenderBetSpot(newCurrentPlayerId, true);
        }
    }

    /** The permanent brown bet-spot item shown throughout active play -- glowing solidly while it's this seat's turn, plain otherwise. Never cleared to background while a player occupies the seat (see transitionBottomBarToActive). */
    private ItemStack buildActiveBetSpotItem(boolean glowing) {
        return glowing ? createGlowingCustomItem(Material.BROWN_STAINED_GLASS_PANE, "§r", 1) : buildBrownEdgeGlassItem();
    }

    /** Re-renders a seated player's permanent bet spot with the given glow state, preserving its wager lore. No-op if the player isn't seated. */
    private void reRenderBetSpot(UUID playerId, boolean glowing) {
        if (playerId == null) {
            return;
        }
        Integer seatSlot = playerSeats.get(playerId);
        if (seatSlot == null) {
            return;
        }
        int betSpotSlot = BlackjackSlotLayout.betSlipSlot(seatSlot);
        double wager = totalBet(playerId);
        inventory.setItem(betSpotSlot, withWagerLore(buildActiveBetSpotItem(glowing), wager, null));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(betSpotSlot, withWagerLore(buildActiveBetSpotItem(glowing), wager, viewer));
        }
    }

    /** Whether {@code slot} is still within the row the card was dealt to -- see BlackjackSlotLayout's bounded row. */
    private boolean isRenderableCardSlot(UUID playerId, int slot) {
        if (playerId == null) {
            return slot >= BlackjackSlotLayout.DEALER_CARD_ROW_FIRST_SLOT && slot <= BlackjackSlotLayout.DEALER_UP_CARD_SLOT;
        }
        Integer seatSlot = playerSeats.get(playerId);
        if (seatSlot == null) {
            return false;
        }
        return slot <= BlackjackSlotLayout.playerCardSlot(seatSlot, BlackjackSlotLayout.SEAT_CARD_CAPACITY - 1);
    }

    // Initialize Blackjack-specific game menu
    private void initializeGameMenu() {

        inventory.clear(); // Clear the inventory before setting up the page
        for (BlackjackView view : views.values()) {
            view.getInventory().clear();
        }

        // Felt the whole table green first -- every slot that isn't
        // overwritten by a functional item below (unused dealer/seat card
        // rows, unused action slots, etc.) stays this background instead of
        // going blank.
        paintBackground(inventory);
        for (BlackjackView view : views.values()) {
            paintBackground(view.getInventory());
        }

        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID playerId = seatOwnerAt(seatSlot);
            if (playerId != null) {
                renderToAllViews(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
            }
        }
        // Add the necessary items for the game menu
        renderLocalizedToAllViews(dealerHeadSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer"); // Dealer
        // The status clock is added when the timer starts.

        // Add empty seats
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            if (seatOwnerAt(seatSlot) == null) {
                renderLocalizedToAllViews(seatSlot, Material.OAK_STAIRS, 1, "blackjack.click-sit");
            }
        }
        sittable=true;

        // Add bet spots (permanent brown glass -- the only betting UI element)
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            renderLocalizedToAllViews(BlackjackSlotLayout.betSlipSlot(seatSlot), Material.BROWN_STAINED_GLASS_PANE, 1, "blackjack.click-bet");
        }

        // Add the pregame bottom bar -- per-viewer: seated viewers get the
        // full Undo/chips/All In/door bar, unseated viewers get door+glass
        // only, regardless of whether other players are already seated and
        // betting (no permanent Hit/Stand/Double-Down anymore either way).
        paintUnseatedBottomBar(inventory, null);
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            if (playerSeats.containsKey(view.getPlayerId())) {
                paintSeatedBottomBar(view.getInventory(), viewer, view.getPlayerId());
            } else {
                paintUnseatedBottomBar(view.getInventory(), viewer);
            }
        }
        //addItem(createCustomItem(Material.SHEARS, "Split"), 39); // Split
        //addItem(createCustomItem(Material.TOTEM_OF_UNDYING, "Insurance"), 40); // Insurance
    }

    // Create an item stack with a custom display name
    public ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Create an item stack with a custom display name and amount
    public ItemStack createCustomItem(Material material, String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 for " + name);
        }
        ItemStack itemStack = new ItemStack(material, amount);
        setCustomItemMeta(itemStack, name);
        return itemStack;
    }

    // Set custom item metadata
    public void setCustomItemMeta(ItemStack itemStack, String name) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>()); // Clear any existing lore
            meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES
            ); // Hide all relevant item flags
            itemStack.setItemMeta(meta); // Set the item meta after making changes
        }
    }

    private void handleDealerHeadEasterEgg(Player player) {
        // 1 in 1000 chance
        if (Math.random() < 0.001) {
            Firework firework = (Firework) player.getLocation().getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
            FireworkMeta fireworkMeta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.CREEPER)
                    .withColor(Color.GREEN)
                    .withFade(Color.BLACK)
                    .flicker(true)
                    .trail(true)
                    .build();
            fireworkMeta.addEffect(effect);
            fireworkMeta.setPower(2);
            firework.setFireworkMeta(fireworkMeta);
        }
        if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null) player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT,SoundCategory.MASTER, 1.0f, 1.0f);
    }

@Override
public void handleClick(int slot, Player player, InventoryClickEvent event) {
    if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
        return;
    }
    UUID playerId = player.getUniqueId();

    if (gameActive) { // Game is active, handle player actions
        if (insurancePhaseActive && insuranceEligiblePlayers.contains(playerId) && !insuranceDecided.contains(playerId)
            && (slot == BlackjackSlotLayout.INSURANCE_YES_SLOT || slot == BlackjackSlotLayout.INSURANCE_NO_SLOT)) {
            // Insurance is its own explicit phase, decided before any
            // player turn begins -- currentPlayerId is still null here, so
            // this must be checked first, not folded into the turn dispatch below.
            handleInsuranceDecision(player, slot == BlackjackSlotLayout.INSURANCE_YES_SLOT);
        } else if (playerId.equals(currentPlayerId)) {
            handlePlayerAction(player, slot);
        } else if (slot == BlackjackSlotLayout.ACTIVE_EXIT_SLOT) { // Handle leave chair
            handleLeaveChairDuringGame(player);
            player.closeInventory();}
        else if (isPlayerHeadSlot(slot, player)) { // Handle clicking own player head
                handleLeaveChair(player); // Leave chair but stay in inventory
        }
        else if (slot == dealerHeadSlot){
            handleDealerHeadEasterEgg(player);
        }
        // Betting slips don't exist during active play -- nothing else to route.
    }
        else { // Handle clicks in the game menu before the game starts
        if (isPlayerHeadSlot(slot, player)) { // Handle clicking own player head
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.left-chair"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
            handleLeaveChair(player); // Leave chair but stay in inventory
        }
        else if (BlackjackSlotLayout.isSeatSlot(slot)) { // Seat slots (9, 18, 27, 36)
            handleChairClick(slot, player);
        } else if (slot == BlackjackSlotLayout.PREGAME_EXIT_SLOT) { // Leave chair
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.left-game"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
            handleLeaveChair(player);
            player.closeInventory();
        } else if (BlackjackSlotLayout.isBetSlipSlot(slot)) { // Betting slips (10, 19, 28, 37)
            handleBetClick(slot, player, event);
        } else if (ChipSlots.isChipSlot(slot)) { // Chip selection
            handleChipSelection(player, slot);
        }
        else if (slot == dealerHeadSlot){
            handleDealerHeadEasterEgg(player);
        } else {
            // Slots 45/46 mean something different depending on whether
            // THIS viewer is seated yet -- seated: Undo All/Undo Last;
            // unseated: door/decorative brown edge glass (see
            // paintUnseatedBottomBar/paintSeatedBottomBar). Every other
            // slot here (All In=52) is only ever rendered/meaningful once
            // seated, so its own handler's existing "must be seated"
            // feedback already covers the unseated case correctly.
            switch (slot) {
                case BlackjackSlotLayout.UNDO_ALL_SLOT: // == UNSEATED_EXIT_SLOT (45)
                    if (playerSeats.containsKey(playerId)) {
                        handleUndoAllBets(player);
                    } else {
                        // Unseated: this is the door -- nothing to leave, just close.
                        player.closeInventory();
                    }
                    break;
                case BlackjackSlotLayout.UNDO_LAST_SLOT: // == UNSEATED_EDGE_GLASS_SLOT (46)
                    if (playerSeats.containsKey(playerId)) {
                        handleUndoLastBet(player);
                    }
                    // Unseated: decorative brown edge glass, no-op.
                    break;
                case BlackjackSlotLayout.ALL_IN_SLOT:
                    handleAllIn(player);
                    break;
                default:
                    // Handle other slots if needed
                    break;
            }
        }
    }
}

private boolean isPlayerHeadSlot(int slot, Player player) {
    if (slot < 0 || slot >= inventory.getSize()) {
        return false; // Prevent invalid slot access
    }

    ItemStack item = inventory.getItem(slot);
    if (item == null || item.getType() != Material.PLAYER_HEAD) return false;

    SkullMeta meta = (SkullMeta) item.getItemMeta();
    return meta != null && meta.hasOwner() && meta.getOwningPlayer() != null &&
           meta.getOwningPlayer().getUniqueId().equals(player.getUniqueId());
}


private void handleAllIn(Player player) {
    UUID playerId = player.getUniqueId();

    // Once the countdown hits zero and the start-transition begins, no new
    // selection/commit is possible -- a player who hadn't committed by
    // then just doesn't get a hand this round (see beginStartTransition).
    if (startTransitionActive) {
        return;
    }

    // Ensure the player is seated before allowing all-in
    if (!playerSeats.containsKey(playerId)) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
        player.sendMessage(text(player, "blackjack.invalid-action"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.must-sit-all-in"));
                break;}
            case NONE:{
                break;
            }
        }
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        return;
    }

    // Calculate total player balance in inventory
    double totalBalance = getPlayerTotalBalance(player);

    if (totalBalance <= 0) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.invalid-action"));
                break;}
            case VERBOSE:{
                player.sendMessage(
                    currencyMode == org.nc.nccasino.currency.CurrencyMode.VAULT
                        ? text(player, "blackjack.no-funds")
                        : text(
                            player,
                            "blackjack.no-currency",
                            "currency",
                            plugin.getCurrencyName(internalName).toLowerCase() + "s"
                        )
                );
                break;}
            case NONE:{
                break;
            }
        }
         if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        return;
    }

    // All In only *selects* the player's full balance as the pending
    // wager amount -- per the table redesign plan, it never debits or
    // commits by itself, exactly like a chip click. Only a subsequent
    // bet-spot click actually commits (see selectWager/commitWager).
     if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.5f, 0.8f);
    selectWager(player, playerId, totalBalance);
}

private double getPlayerTotalBalance(Player player) {
    CurrencyProvider provider = getCurrencyProvider();
    if (provider != null) {
        return provider.getBalance(player, internalName);
    }

    int totalAmount = 0;
    Material currencyMaterial = plugin.getCurrency(internalName);
    if (currencyMaterial == null) {
        return 0;
    }

    for (ItemStack item : player.getInventory().getContents()) {
        if (item != null && item.getType() == currencyMaterial) {
            totalAmount += item.getAmount();
        }
    }

    return totalAmount;
}

/**
 * Resolves a click during the current player's own actionable turn.
 * Never trusts the clicked slot alone: it recomputes the live available
 * actions/layout and only dispatches if the slot still resolves to one of
 * them, so a stale or race-y client render can never trigger an action
 * that's no longer valid.
 */
private void handlePlayerAction(Player player, int slot) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();

        // Allow the player to leave during their turn -- close their
        // inventory too, matching the non-current-player exit path, so
        // "Leave/Exit" behaves the same regardless of whose turn it is.
        if (slot == BlackjackSlotLayout.ACTIVE_EXIT_SLOT) {
            handleLeaveChairDuringGame(player);
            player.closeInventory();
            return;
        }

        if (slot == dealerHeadSlot) {
            handleDealerHeadEasterEgg(player);
        }

        // Check if the player's turn is still active
        if (!playerTurnActive.getOrDefault(playerId, false)) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.not-your-turn"));
                    break;
                }
                case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        BlackjackHand activeHand = activeHand(playerId);
        if (activeHand == null) {
            repaintActionsForCurrentPlayer();
            return;
        }
        List<BlackjackAction> actions = availableActionsForHand(player, activeHand);
        BlackjackAction action = BlackjackActionLayout.actionAt(actions, slot);

        if (action == null) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action-spaced"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.invalid-action-spaced"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
            // The clicked slot no longer matches the live layout (e.g.
            // funds dropped and Double Down disappeared mid-turn) --
            // repaint the correct, current layout rather than leaving a
            // stale one displayed. This must never restart the turn-timer
            // deadline (see repaintActionsForCurrentPlayer's doc) -- an
            // invalid/stale click is not a new decision.
            repaintActionsForCurrentPlayer();
            return;
        }

        // Disable further actions until the current one is processed, and
        // hide the buttons immediately -- a duplicate click must not land.
        playerTurnActive.put(playerId, false);
        repaintActionsForCurrentPlayer();

        switch (action) {
            case HIT:
                handleHit(player);
                 if (SoundHelper.getSoundSafely("entity.creeper.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
                break;
            case STAND:
                handleStand(player);
                break;
            case DOUBLE_DOWN:
                handleDoubleDown(player);
                break;
            case SPLIT:
                handleSplit(player);
                break;
        }
    }
}

private void handleHit(Player player) {
    synchronized (turnLock) {

        UUID playerId = player.getUniqueId();
        if (playerSeats.get(playerId) == null || !playerSeats.containsKey(playerId)){
            return;
        }
        int seatSlot = playerSeats.get(playerId);
        int cardCount = activeHandCards(playerId).size(); // Cards already in the active hand -- derived, not a separate lifetime counter, so it's automatically correct for whichever hand (post-split or not) is active.
        int nextCardSlot = seatSlot + 2 + cardCount; // Plain arithmetic (not playerCardSlot) -- cardCount can exceed the visible row; dealCardToPlayer bounds the render, never the canonical hand.

        long myGeneration = roundGeneration;
        int myHandToken = currentHandToken(playerId);

        Card newCard = deck.dealCard();
        scheduleCardDealingWithDelay(nextCardSlot, newCard, BlackjackTiming.CARD_DEAL_DELAY_TICKS, playerId, myGeneration, myHandToken); // Deal the card with a delay

        // Delay the hand value calculation to ensure the card is fully added to the player's hand
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                return;
            }
            int handValue = calculateHandValue(activeHandCards(playerId));
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{

                    player.sendMessage(text(
                        player,
                        "blackjack.drew-card",
                        "rank",
                        localize(player, "cards.ranks." + newCard.getRank().name().toLowerCase(java.util.Locale.ROOT))
                    ));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
            if (handValue == 21) {

                BlackjackHand hand21 = activeHand(playerId);
                if (hand21 != null) {
                    hand21.setDone(true);
                }
                // Depth-first: if this player has another pending split
                // hand, its turn begins next; only once the whole queue is
                // exhausted does the table move on to the next player.
                advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            } else if (handValue > 21) {

                BlackjackHand bustHand = activeHand(playerId);
                if (bustHand != null) {
                    bustHand.setDone(true);
                }
                advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);

            } else {

                playerTurnActive.put(playerId, true); // Allow more actions since the player hasn't busted
                // A completed Hit that leaves the hand actionable is a
                // genuinely new decision -- restart guidance/the timer.
                beginActionableDecision();

            }
        }, BlackjackTiming.HIT_EVALUATION_DELAY_TICKS); // The delay should be enough to ensure that the card has been added
    }
}

private void handleStand(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        BlackjackHand hand = activeHand(playerId);
        if (hand != null) {
            hand.setDone(true);
        }
         if (SoundHelper.getSoundSafely("item.shield.block", player) != null)player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER,1.0f, 1.0f);
         switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.stood"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.stood"));
                break;
            }
                case NONE:{
                break;
            }
        }
        // Depth-first: a pending split hand (if any) begins next; only once
        // every hand in this player's queue is resolved does the table
        // advance to the next player.
        advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
    }
}

private void startNextPlayerTurnWithDelay(long delay) {
    long mySequence = ++turnSequence;
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (turnSequence != mySequence) {
            // Something else (a forced advance from a mid-turn
            // leave/disconnect, or another scheduled advance) already
            // moved the turn on -- advancing again here would skip
            // whoever it's now the turn of.
            return;
        }
        startNextPlayerTurn();
    }, delay);
}

/** Forces an immediate turn advance (e.g. a mid-turn leave/disconnect), invalidating any pending delayed advance. */
private void advanceTurnNow() {
    turnSequence++;
    startNextPlayerTurn();
}

/**
 * Atomic double down: validates eligibility/funds, removes the matching
 * wager, deals exactly one additional card, evaluates it exactly once,
 * finishes the hand regardless of its value, and advances the turn
 * exactly once. Deliberately does not call handleHit -- that would
 * schedule a second, independently-timed evaluation racing this method's
 * own resolution (the race previously characterized in
 * BlackjackTimingTest, now fixed by construction: there is only ever one
 * scheduled callback for a double down). That callback is additionally
 * guarded by the round-generation/hand-token pair captured up front (see
 * isStaleHandCallback), so a leave-chair or reset mid-flight still can't
 * let it mutate a hand that is no longer current.
 */
private void handleDoubleDown(Player player) {
    synchronized (turnLock) {
        UUID playerId = player.getUniqueId();
        BlackjackHand hand = activeHand(playerId);
        double currentBet = hand == null ? 0.0 : hand.getWager();
        boolean doubleAllowedForThisHand = hand != null && (!hand.isFromSplit() || doubleAfterSplit);

        if (hand == null || !doubleAllowedForThisHand || !hasEnoughWager(player, currentBet)) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.insufficient-funds"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.insufficient-double-down"));}
                    case NONE:{
                    break;
                }
            }
            playerTurnActive.put(playerId, true); // Allow more actions since the double down failed
            // A failed double-down is not a new decision -- repaint only,
            // never extend the deadline for it.
            repaintActionsForCurrentPlayer();
            return;
        }

        // Remove exactly one additional wager (this hand's own, not the
        // player's whole playerBets ledger -- per-hand doubling must debit
        // exactly one more wager for that specific hand only, independent
        // of any sibling hands).
        removeWagerFromInventory(player, currentBet);
        hand.setWager(hand.getWager() * 2);
        hand.setDoubled(true);

        // The seat's single bet-spot slot always reflects the active
        // hand's own live wager during active play.
        int seatSlot = playerSeats.get(playerId);
        updateItemLore(BlackjackSlotLayout.betSlipSlot(seatSlot), hand.getWager());

        int cardCount = activeHandCards(playerId).size();
        int cardSlot = seatSlot + 2 + cardCount;
        long myGeneration = roundGeneration;
        int myHandToken = currentHandToken(playerId);

        // Exactly one more card.
        Card newCard = deck.dealCard();
        scheduleCardDealingWithDelay(cardSlot, newCard, BlackjackTiming.CARD_DEAL_DELAY_TICKS, playerId, myGeneration, myHandToken);

         if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f);
         switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(player, "blackjack.doubled-down"));
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.doubled-down"));
                break;

            }
                case NONE:{
                break;
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                return;
            }
            // Finish the hand regardless of its value -- never re-enable
            // Hit/Stand/Double afterward.
            BlackjackHand doubledHand = activeHand(playerId);
            if (doubledHand != null) {
                doubledHand.setDone(true);
            }
            advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
        }, BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
    }
}

    // ---- Real splitting -------------------------------------------------

    /**
     * Depth-first hand-queue advance: called whenever the current player's
     * active hand has just become fully resolved (bust, reached 21, Stand,
     * Double resolved, or a split-ace auto-complete). If this player has
     * another pending hand in their own queue (see
     * {@link BlackjackSplitQueue#nextActionableIndex}), it activates next;
     * only once every one of their hands is resolved does the player's
     * overall turn actually advance to the next player. Always bumps the
     * hand token first, so any stale callback tied to the just-finished
     * hand's decision can never fire against whatever comes next.
     */
    private void advanceAfterHandResolved(UUID playerId, long delay) {
        bumpHandToken(playerId);
        List<BlackjackHand> hands = playerHands.get(playerId);
        int currentIndex = activeHandIndex.getOrDefault(playerId, 0);
        int nextIndex = hands == null ? -1 : BlackjackSplitQueue.nextActionableIndex(hands, currentIndex);
        if (hands != null && nextIndex >= 0) {
            activeHandIndex.put(playerId, nextIndex);
            playerDone.put(playerId, false);
            activateSplitHand(playerId, hands.get(nextIndex));
            return;
        }
        playerDone.put(playerId, true);
        playerTurnActive.put(playerId, false);
        repaintActionsForCurrentPlayer();
        startNextPlayerTurnWithDelay(delay);
    }

    /**
     * Activates a pending split hand that has just become the seat's
     * current hand: renders its full card set into the seat's row from
     * scratch (it had no visible slot while pending -- see the table
     * redesign plan's "Split rendering" section), then either auto-completes
     * it immediately (split-ace hand with no hit/double/resplit permitted)
     * or begins a fresh actionable decision for it.
     */
    private void activateSplitHand(UUID playerId, BlackjackHand hand) {
        Integer seatSlotBoxed = playerSeats.get(playerId);
        if (seatSlotBoxed == null) {
            return;
        }
        int seatSlot = seatSlotBoxed;
        Player player = Bukkit.getPlayer(playerId);
        List<Card> cards = hand.getCards();
        for (int i = 0; i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            int slot = BlackjackSlotLayout.playerCardSlot(seatSlot, i);
            if (i < cards.size()) {
                renderCardToAllViews(slot, cards.get(i), true);
            } else {
                renderBackgroundToAllViews(slot);
            }
        }
        updatePlayerHead(playerId);
        updateItemLore(BlackjackSlotLayout.betSlipSlot(seatSlot), hand.getWager());

        if (player != null && hand.isSplitFromAce() && cards.size() == 2) {
            List<BlackjackHand> hands = playerHands.getOrDefault(playerId, List.of());
            boolean acesDoubleEffective = acesDoubleAllowed && doubleAfterSplit && hasEnoughWager(player, hand.getWager());
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            if (BlackjackActionLayout.splitAceHandAutoCompletes(acesHitAllowed, acesDoubleEffective, resplitEligible)) {
                hand.setDone(true);
                advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
                return;
            }
        }
        int handValue = calculateHandValue(cards);
        if (handValue == 21) {
            hand.setDone(true);
            advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            return;
        }
        playerTurnActive.put(playerId, true);
        beginActionableDecision();
    }

    /**
     * Splits the current player's active hand: revalidates eligibility live
     * (never trusts the click alone -- same discipline as
     * {@link #handlePlayerAction}), debits exactly one additional matching
     * wager, moves the second card into a new sibling hand inserted
     * immediately after the current one (depth-first -- see
     * {@link BlackjackSplitQueue}), and runs the shared/table-owned split
     * animation before either hand becomes actionable again.
     */
    private void handleSplit(Player player) {
        synchronized (turnLock) {
            UUID playerId = player.getUniqueId();
            Integer seatSlotBoxed = playerSeats.get(playerId);
            List<BlackjackHand> hands = playerHands.get(playerId);
            if (seatSlotBoxed == null || hands == null || hands.isEmpty()) {
                playerTurnActive.put(playerId, true);
                repaintActionsForCurrentPlayer();
                return;
            }
            int seatSlot = seatSlotBoxed;
            int currentIndex = activeHandIndex.getOrDefault(playerId, 0);
            BlackjackHand hand = hands.get(currentIndex);

            boolean eligible = splitEligibleForHand(player, hand, hands);
            if (!eligible) {
                boolean insufficientFunds = !hasEnoughWager(player, hand.getWager());
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, insufficientFunds ? "blackjack.split-insufficient-funds" : "blackjack.split-ineligible"));
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                }
                playerTurnActive.put(playerId, true);
                // A denied split is not a new decision -- repaint only.
                repaintActionsForCurrentPlayer();
                return;
            }

            boolean wasResplit = hand.isFromSplit();

            // Exactly one additional matching wager, debited once.
            removeWagerFromInventory(player, hand.getWager());

            boolean wasAcePair = hand.getCards().get(0).getRank() == Rank.ACE;
            BlackjackHand sibling = new BlackjackHand(hand.getWager());
            sibling.setOriginalPreSplitWager(hand.getOriginalPreSplitWager());
            Card movedCard = hand.getCards().remove(1);
            hand.bumpGeneration();
            sibling.getCards().add(movedCard);
            sibling.bumpGeneration();
            if (wasAcePair) {
                hand.setSplitFromAce(true);
                sibling.setSplitFromAce(true);
            }
            hand.setFromSplit(true);
            sibling.setFromSplit(true);

            BlackjackSplitQueue.insertSiblingAfterCurrent(hands, currentIndex, sibling);

            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, wasResplit ? "blackjack.resplit-offer" : "blackjack.split-success"));
            }
            if (SoundHelper.getSoundSafely("block.anvil.land", player) != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            // Hidden during the shared animation -- neither hand is a
            // legitimate decision again until it completes.
            playerTurnActive.put(playerId, false);
            bumpHandToken(playerId);
            repaintActionsForCurrentPlayer();

            // Both replacement cards are drawn up front (eligibility already
            // confirmed the shoe can immediately supply both -- a split
            // never reshuffles mid-round) so the animation only ever
            // schedules rendering, never a possibly-empty deck draw.
            Card originalReplacement = deck.dealCard();
            Card siblingReplacement = deck.dealCard();

            runSplitAnimation(playerId, seatSlot, hand, sibling, originalReplacement, siblingReplacement);
        }
    }

    /**
     * The shared/table-owned split animation: slides the second card out of
     * view, deals the original (still-active) hand's replacement card
     * visibly, deals the sibling's replacement card canonically only (it
     * has no visible slot while pending), then resolves whatever comes
     * next. Owned by the table, not the acting viewer -- see
     * {@link #cancelSharedAnimation()}'s doc -- so one viewer closing their
     * inventory never interrupts it; only a genuinely table-wide event
     * (round reset/cancel, generation change) does.
     */
    private void runSplitAnimation(UUID playerId, int seatSlot, BlackjackHand originalHand, BlackjackHand siblingHand, Card originalReplacement, Card siblingReplacement) {
        long myGeneration = roundGeneration;
        BlackjackFrame.Phase myPhase = capturePhase();
        cancelSharedAnimation();
        BlackjackAnimationRun run = new BlackjackAnimationRun(null, myGeneration, 0, myPhase);
        sharedAnimationRun = run;
        splitAnimationInFlight = true;
        long stepTicks = BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS;

        int splitCardFromSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, 1);

        // Step 1: the split-off card slides out of view immediately -- it
        // now belongs to the pending sibling hand, which has no visible slot.
        renderBackgroundToAllViews(splitCardFromSlot);

        // Step 2: the original hand's replacement card deals in visibly.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run.isStale(roundGeneration, 0, capturePhase()) || sharedAnimationRun != run) {
                return;
            }
            originalHand.addCard(originalReplacement);
            if (isRenderableCardSlot(playerId, splitCardFromSlot)) {
                renderCardToAllViews(splitCardFromSlot, originalReplacement, playerId.equals(currentPlayerId));
            }
            updatePlayerHead(playerId);
        }, stepTicks);

        // Step 3: the sibling's replacement card deals canonically only.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run.isStale(roundGeneration, 0, capturePhase()) || sharedAnimationRun != run) {
                return;
            }
            siblingHand.addCard(siblingReplacement);
        }, stepTicks * 2);

        // Step 4: animation complete -- the original hand (still active)
        // either auto-completes (split-ace, nothing permitted) or becomes
        // actionable again.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run.isStale(roundGeneration, 0, capturePhase()) || sharedAnimationRun != run) {
                return;
            }
            sharedAnimationRun = null;
            splitAnimationInFlight = false;
            resolveHandAfterSplitAnimation(playerId);
        }, stepTicks * 3);
    }

    /** Resolves whatever the currently-active hand needs once the split animation finishes -- reuses the exact same activation logic a freshly-reached split hand uses. */
    private void resolveHandAfterSplitAnimation(UUID playerId) {
        List<BlackjackHand> hands = playerHands.get(playerId);
        if (hands == null || hands.isEmpty()) {
            return;
        }
        int idx = activeHandIndex.getOrDefault(playerId, 0);
        if (idx < 0 || idx >= hands.size()) {
            idx = 0;
        }
        BlackjackHand hand = hands.get(idx);
        if (hand.isDone()) {
            advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        List<Card> cards = hand.getCards();
        if (player != null && hand.isSplitFromAce() && cards.size() == 2) {
            boolean acesDoubleEffective = acesDoubleAllowed && doubleAfterSplit && hasEnoughWager(player, hand.getWager());
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            if (BlackjackActionLayout.splitAceHandAutoCompletes(acesHitAllowed, acesDoubleEffective, resplitEligible)) {
                hand.setDone(true);
                advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
                return;
            }
        }
        int handValue = calculateHandValue(cards);
        if (handValue == 21) {
            hand.setDone(true);
            advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            return;
        }
        playerTurnActive.put(playerId, true);
        beginActionableDecision();
    }

    /**
     * Aborts the entire round because the shoe cannot immediately continue
     * supplying it, refunding every debit of that round (original wagers,
     * split wagers, double wagers, insurance stakes -- see
     * {@link BlackjackRoundAbortRefund}) to every seated player, then resets
     * for the next round. Never pays out or settles hands -- the round
     * simply never happened.
     */
    private void abortRoundForShoeExhaustion() {
        for (UUID playerId : new ArrayList<>(playerSeats.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<BlackjackHand> hands = playerHands.get(playerId);
            // Before the first card lands, no BlackjackHand exists yet and
            // the committed pregame wager still lives only in playerBets --
            // fall back to that so an abort triggered by the initial deal
            // itself still refunds what was already debited at bet-commit.
            double refund = (hands != null && !hands.isEmpty())
                ? BlackjackRoundAbortRefund.totalRefundForPlayer(hands, insuranceStakes.getOrDefault(playerId, 0.0))
                : totalBet(playerId) + insuranceStakes.getOrDefault(playerId, 0.0);
            if (refund > 0) {
                addWagerToInventory(player, refund);
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, "blackjack.shoe-exhausted-refunded", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, refund)));
                }
            }
        }
        insuranceStakes.clear();
        resetGame();
    }

    // Handle chair click
    private void handleChairClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack clickedItem = inventory.getItem(slot);

        // Check if the player is already sitting in a chair
        if (playerSeats.containsKey(playerId)||clickedItem == null || !clickedItem.getType().name().endsWith("_STAIRS")||!sittable) {
            if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER,1.0f, 1.0f);
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
               case STANDARD:{
                player.sendMessage(text(player, "blackjack.already-seated"));
                break;}
               case VERBOSE:{
                player.sendMessage(text(player, "blackjack.cannot-switch"));
                break;

            }
                   case NONE:{
                   break;
               }
           }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

         if (SoundHelper.getSoundSafely("block.wood.place", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE,SoundCategory.MASTER, 1.0f, 1.0f);
        // Set the player's actual head at the chair's position
        renderToAllViews(slot, createPlayerHeadItem(player, 1));
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                break;}
            case VERBOSE:{
                player.sendMessage(text(player, "blackjack.sat-down"));
                break;

            }
                case NONE:{
                break;
            }
        }
        // Track the player's seat
        playerSeats.put(playerId, slot);
        SessionRegistry.register(playerId, this);

        // Chair guidance no longer applies now that they've sat -- the
        // door-reveal animation (private, this viewer only) takes over,
        // sliding their own bottom bar from door+glass to the full seated
        // wager bar, then handing off to wager guidance once it completes.
        cancelPrivateAnimation(playerId);
        startWagerBarReveal(playerId);
    }


// Handle leave chair during the countdown or active game
private void handleLeaveChair(Player player) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

     if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);

    // Before the deal actually starts (pregame, countdown, or the
    // start-transition window after the countdown clock is already gone
    // but the round hasn't gone active yet), refund the player's committed
    // wager first -- handleUndoAllBets no-ops safely once gameActive
    // (and once there's nothing left to refund), so this is safe to call
    // unconditionally here; running it before removePlayerData's own (now
    // redundant, harmless) bet cleanup doesn't double-refund.
    if (!gameActive) {
        handleUndoAllBets(player);
    }

    // Remove all the player's associated data. Must run while the player
    // is still in playerSeats -- removing the seat first (as this used to)
    // leaves removePlayerData unable to find anything to clean up, so
    // SessionRegistry never unregisters and, worse, an active-game leave
    // (a non-current player clicking their own head) never clears their
    // cards or drops them from the pending turn iterator, leaving a
    // phantom turn for a seat that's already empty. removePlayerData also
    // already re-renders the seat and calls cancelGame() itself once the
    // last seat empties.
    removePlayerData(playerId);

    // This path never closes the viewer's inventory (unlike the door/exit
    // click) -- they're back to being an unseated viewer of the same open
    // table, so chair guidance should resume for them exactly as if they'd
    // just opened it. startChairGuidance's own guards make this a safe
    // no-op if anything's changed by the time it fires (re-seated, game
    // started, closed).
    scheduleChairGuidanceStart(playerId);
}

// Handle leave chair during an active game
private void handleLeaveChairDuringGame(Player player) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

    // If it's the player's turn, end their turn immediately
    if (playerId.equals(currentPlayerId)) {
        playerDone.put(playerId, true); // Mark the player as done
        playerTurnActive.put(playerId, false);
        bumpHandToken(playerId);
        advanceTurnNow(); // Invalidates any turn-advance already scheduled for this hand's resolution
    }

    // Remove all the player's associated data. This must run while the
    // player is still in playerSeats -- removePlayerData looks itself up
    // there to find the seat slot, clear the player's cards, and restore
    // the empty-seat item; removing from playerSeats first (as this used
    // to) leaves removePlayerData unable to find anything to clean up,
    // silently skipping the hand/bet/session cleanup entirely. It also
    // already calls cancelGame() itself once the last seat empties, so
    // there's no need to repeat that check here.
    removePlayerData(playerId);

     if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);
}

private void removePlayerData(UUID playerId) {
    // Leaving a seat invalidates whatever private animation was guiding
    // this player (chair guide while unseated, wager guide/bet-spot blink
    // while seated) -- never the shared/table-owned run.
    cancelPrivateAnimation(playerId);

    // Retrieve the player's seat slot
    int seatSlot = playerSeats.getOrDefault(playerId, -1);

    // If the player has a valid seat slot
    if (seatSlot != -1) {
        // Clear the player's cards from the table
        List<Card> hand = activeHandCards(playerId);
        for (int i = 0; i < hand.size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            renderBackgroundToAllViews(BlackjackSlotLayout.playerCardSlot(seatSlot, i)); // Clear each card slot in the player's row back to the felt
        }

        // Remove the player's head from the seat
        renderLocalizedToAllViews(seatSlot, Material.OAK_STAIRS, 1, "blackjack.click-sit");

        // Remove player's data from tracking maps
        playerHands.remove(playerId);
        activeHandIndex.remove(playerId);
        playerCardCounts.remove(playerId);
        playerTurnActive.remove(playerId);
        playerDone.remove(playerId);
        handToken.remove(playerId);

        // Remove player's bets and related lore
        clearPlayerBetLore(playerId);
        clearPlayerBets(playerId);

        // Remove player from seat map
        playerSeats.remove(playerId);
        SessionRegistry.unregister(playerId, this);

        // Ensure player is removed from active turns
        if (playerIterator != null) {
            List<UUID> remainingPlayers = new ArrayList<>();
            playerIterator.forEachRemaining(remainingPlayers::add);
            remainingPlayers.remove(playerId);
            playerIterator = remainingPlayers.iterator();
        }

        // An active-game leave forfeits everything, including an already-
        // debited insurance stake (consistent with the main wager's own
        // forfeit-on-active-game-leave policy) -- never paid out even if
        // the dealer's peek later finds blackjack. checkInsuranceAllDecided
        // re-reads playerSeats live, so removing them here (already done
        // above) is what lets a pending insurance decision resolve without
        // waiting forever on a player who just left.
        insuranceEligiblePlayers.remove(playerId);
        insuranceDecided.remove(playerId);
        insuranceStakes.remove(playerId);
        if (insurancePhaseActive) {
            checkInsuranceAllDecided();
        }
    }

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        cancelGame();
    }
}

    // Handle chip selection -- only sets the pending selected wager amount,
    // moves no funds, pushes nothing to the ledger (see selectWager).
    private void handleChipSelection(Player player, int slot) {
        if (startTransitionActive) {
            return; // countdown already hit zero -- no new selection is possible this round
        }
        double amount = chipValues.getOrDefault(slot, 0.0);
        if (amount <= 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerSeats.containsKey(playerId)) {
            return; // chip slots aren't even rendered for an unseated viewer -- defensive no-op
        }
        selectWager(player, playerId, amount);
    }

    private void clearPlayerBetLore(UUID playerId) {
        Map<Integer, Double> bets = playerBets.get(playerId);
        if (bets != null) {
            for (int slot : bets.keySet()) {
                updateItemLore(slot, 0); // Clear the lore
            }
        }
    }

    private void handleBetClick(int slot, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();

        // Once the countdown hits zero and the start-transition begins, no
        // new commit is possible -- a player who hadn't committed by then
        // just doesn't get a hand this round (see beginStartTransition).
        if (startTransitionActive) {
            return;
        }

        // Ensure the player is sitting before placing a bet
        if (!playerSeats.containsKey(playerId)) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.sit-to-bet"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.must-sit-to-bet"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        // Ensure the player can only bet on their own spot
        int chairSlot = playerSeats.get(playerId);
        int betSpotSlot = BlackjackSlotLayout.betSlipSlot(chairSlot);

        if (slot != betSpotSlot) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.invalid-bet-spot"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.cannot-bet-other"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        // Only clicking the bet spot itself ever commits (see commitWager)
        // -- either directly dragging real currency onto it, or committing
        // whatever amount was previously selected via a chip/All In click.
        ItemStack heldItem = event.getCursor();
        if (isCurrencyItem(heldItem) && heldItem != null && heldItem.getAmount() > 0) {
            double amount = heldItem.getAmount();
            player.setItemOnCursor(null); // Removes the stack from the cursor -- this IS the debit for a cursor-drag commit
            commitWagerFundsAlreadyRemoved(playerId, betSpotSlot, amount); // must NOT also call removeWagerFromInventory -- see commitWager's doc

            if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);

            if (countdownTaskId == -1) {
                startCountdownTimer();
            }
            return;
        }

        double selected = getSelectedWager(playerId);
        if (selected > 0 && hasEnoughWager(player, selected)) {
            commitWager(player, playerId, betSpotSlot, selected);
            selectedWager.remove(playerId); // the selection is consumed by this commit
            cancelPrivateAnimation(playerId); // stop the bet-spot blink
            refreshWagerControlsForPlayer(playerId); // un-enchant the now-consumed chip
            startWagerGuidance(playerId); // resume guidance so the player can add another increment if they want

            if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);

            if (countdownTaskId == -1) {
                startCountdownTimer();
            }
        } else {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text(player, "blackjack.insufficient-bet"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    private void handleUndoAllBets(Player player) {
        if (gameActive) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.cannot-undo-all"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        UUID playerId = player.getUniqueId();
        java.util.Deque<Double> increments = pregameWagerIncrements.get(playerId);

        if (increments != null && !increments.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{

                    player.sendMessage(text(player, "blackjack.all-bets-undone"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
            double totalRefund = BlackjackWagerLedger.undoAll(increments);
            addWagerToInventory(player, totalRefund);
            clearPlayerBetLore(playerId);  // Clear lore for items related to this player
            pregameWagerIncrements.remove(playerId);
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);

             if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
            // Check if there are no bets left for any player
            if (playerBets.isEmpty()) {
                stopCountdownTimer(); // Stop the timer if no bets are left for any player
            }
        }
    }

    private double getSelectedWager(UUID playerId) {
        return selectedWager.getOrDefault(playerId, 0.0);
    }

    private void stopCountdownTimer() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
            // Clear every seated player's countdown clock back to the felt.
            clearPregameCountdownFromAllViews();
        }
    }

    // Handle undo last bet
    private void handleUndoLastBet(Player player) {
        if (gameActive) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.invalid-action"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.cannot-undo"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        UUID playerId = player.getUniqueId();
        java.util.Deque<Double> increments = pregameWagerIncrements.get(playerId);

        if (increments != null && !increments.isEmpty()) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{

                    player.sendMessage(text(player, "blackjack.last-bet-undone"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
            // Pops and refunds exactly the most recently *committed*
            // increment -- never a pending, uncommitted selection.
            double lastBet = BlackjackWagerLedger.undoLast(increments);

             if (SoundHelper.getSoundSafely("ui.toast.in", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
             if (SoundHelper.getSoundSafely("ui.toast.out", player) != null)player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);

            if (playerSeats.containsKey(playerId)) {
                int betSpotSlot = BlackjackSlotLayout.betSlipSlot(playerSeats.get(playerId));
                syncPlayerBetsFromLedger(playerId, betSpotSlot);
                updateItemLore(betSpotSlot, BlackjackWagerLedger.total(increments));
            }

            addWagerToInventory(player, lastBet);

            // Check if there are no bets left for ANY player at the table
            // (matches handleUndoAllBets' scope -- one player emptying
            // their own ledger must not stop the table's shared countdown
            // while others still have bets in).
            if (playerBets.isEmpty()) {
                stopCountdownTimer();
            }
        }
    }

    private void updateItemLore(int slot, double wager) {
        applyWagerLore(inventory, slot, wager, null);
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            applyWagerLore(view.getInventory(), slot, wager, viewer);
        }
    }

    private void applyWagerLore(Inventory target, int slot, double wager, Player viewer) {
        ItemStack item = target.getItem(slot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (wager > 0) {
                    List<String> lore = new ArrayList<>();
                    lore.add(localize(viewer, "blackjack.hand-wager-lore", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, wager)));
                    meta.setLore(lore);
                } else {
                    meta.setLore(new ArrayList<>()); // Clear lore if no wager
                }
                item.setItemMeta(meta);
                target.setItem(slot, item);
            }
        }
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return false;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return provider.has(player, internalName, requiredAmount);
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            return false;
        }
        return player.getInventory().containsAtLeast(new ItemStack(currencyMaterial), requiredAmount);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return;

        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            provider.withdraw(player, internalName, requiredAmount);
            return;
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial != null && requiredAmount > 0) {
            player.getInventory().removeItem(new ItemStack(currencyMaterial, requiredAmount));
        }
    }

    private void addWagerToInventory(Player player, double amount) {
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null && provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
            java.math.BigDecimal refund = MoneyHelper.clampNonNegative(MoneyHelper.bd(amount));
            if (refund.compareTo(java.math.BigDecimal.ZERO) > 0) {
                vaultProvider.deposit(player, internalName, refund);
            }
            return;
        }
        int totalAmount = (int) Math.floor(amount);
        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, 64);
            } else {
                stack = new ItemStack(currencyMaterial, 64);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
        if (remainder > 0) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, remainder);
            } else {
                stack = new ItemStack(currencyMaterial, remainder);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
    }

    private void clearPlayerBets(UUID playerId) {
        if (playerId == null) {
            playerBets.clear();
            lastBetAmounts.clear();
            pregameWagerIncrements.clear();
            selectedWager.clear();
        } else {
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);
            pregameWagerIncrements.remove(playerId);
            selectedWager.remove(playerId);
        }
    }

    private void clearAllLore() {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    meta.setLore(new ArrayList<>()); // Clear lore
                    item.setItemMeta(meta);
                }
            }
        }
    }

    // Start the countdown timer and display it with a stack of clocks
    private void startCountdownTimer() {
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {

            int countdown =  plugin.getTimer(internalName);
            @Override
            public void run() {
                if (countdown > 0) {
                    countdownSecondsRemaining = countdown;
                    renderPregameCountdownToAllViews(countdown);
                    if (countdown <=3 ){
                        for (UUID uuid : playerSeats.keySet()) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                 if (SoundHelper.getSoundSafely("block.note_block.hat", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1.0f, 1.0f);
                            }
                        }
                    }
                    countdown--;
                } else {
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    beginStartTransition();
                }
            }
        }, 0L, 20L); // Run every second
    }

    /**
     * Commits the round: clears the wager row (stopping short of slot 53,
     * the dealer's in-play head slot -- see below), re-paints every seated
     * player's permanent bet spot (kept visible, not cleared, throughout
     * active play), moves the exit door to slot 45, and clears every seated
     * player's pregame countdown slot. Dealer-position-as-state has already
     * delivered dealerHeadSlot to its in-play value (53) by the time this
     * runs -- see beginStartTransition/startDealerInspection -- but slot 53
     * was still inside the old clear-everything-45-53 loop, wiping the
     * dealer head the inspection animation had just placed there with
     * nothing ever recreating it; the loop now stops at ALL_IN_SLOT (52)
     * and the dealer head is explicitly (re)rendered afterward instead.
     */
    private void transitionBottomBarToActive() {
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT; slot <= BlackjackSlotLayout.ALL_IN_SLOT; slot++) {
            renderBackgroundToAllViews(slot);
        }
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID occupant = seatOwnerAt(seatSlot);
            if (occupant != null) {
                reRenderBetSpot(occupant, false);
            } else {
                renderBackgroundToAllViews(BlackjackSlotLayout.betSlipSlot(seatSlot));
            }
        }
        renderLocalizedToAllViews(BlackjackSlotLayout.ACTIVE_EXIT_SLOT, Material.SPRUCE_DOOR, 1, "blackjack.leave-exit");
        renderToAllViews(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem()); // idle until the first player's turn actually starts -- see startTurnTimer
        clearPregameCountdownFromAllViews();
        dealerHeadSlot = BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT;
        renderLocalizedToAllViews(dealerHeadSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer");
    }

    // Activate the game and set the dealer's turn
private void activateGame() {
    gameActive = true; // Mark the game as active
    roundGeneration++;
    handToken.clear();

    // Initialize hands for players and dealer -- BlackjackHand instances
    // are created lazily (see ensureActiveHand) the moment each player's
    // first card actually lands, seeded with their committed wager then.
    for (UUID playerId : playerSeats.keySet()) {
        playerHands.put(playerId, new ArrayList<>());
        activeHandIndex.put(playerId, 0);
    }
    dealerHand.clear(); // Ensure the dealer's hand is empty

    transitionBottomBarToActive();

    // Ensure player heads are updated correctly
    for (UUID playerId : playerSeats.keySet()) {
        updatePlayerHead(playerId); // Ensure player heads are visible with the correct stack size
    }

    // Deal cards to all players and the dealer
    dealInitialCards();
}


private void dealInitialCards() {
    List<UUID> bettingPlayerOrder = new ArrayList<>();
    for (UUID playerId : orderedSeatedPlayers()) {
        if (playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty()) {
            bettingPlayerOrder.add(playerId);
        }
    }

    // Every seated bettor needs 2 cards, the dealer needs 2 -- if the shoe
    // can't immediately supply the whole initial deal, abort the round and
    // refund every already-committed wager rather than let Deck silently
    // reshuffle mid-round.
    int cardsNeeded = bettingPlayerOrder.size() * 2 + 2;
    if (deck.remainingCards() < cardsNeeded) {
        abortRoundForShoeExhaustion();
        return;
    }

    long myGeneration = roundGeneration;

    BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(
        bettingPlayerOrder, playerSeats,
        BlackjackSlotLayout.DEALER_UP_CARD_SLOT, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT,
        (int) BlackjackTiming.CARD_DEAL_DELAY_TICKS
    );

    for (BlackjackDealPlan.Step step : plan.getSteps()) {
        if (step.isHidden()) {
            // Hidden placeholder: the Card IS drawn from the shoe now (so
            // its rank is known for the dealer peek below), just rendered
            // as a hidden placeholder until revealDealerHoleCardNow/
            // revealDealerCardWithDelay later paints it face-up.
            Card holeCard = deck.dealCard();
            scheduleHiddenCardDealing(step.getSlot(), holeCard, step.getDelayTicks(), myGeneration);
        } else {
            scheduleCardDealing(step.getSlot(), deck.dealCard(), step.getDelayTicks(), step.getPlayerId(), myGeneration);
        }
    }

    // Check for initial blackjack, then insurance/dealer-peek, right after dealing cards.
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (roundGeneration != myGeneration || !gameActive) {
            return;
        }
        for (UUID playerId : orderedSeatedPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                int handValue = calculateHandValue(activeHandCards(playerId));
                if (handValue == 21) {
                    playerDone.put(playerId, true); // Mark the player as done
                    playerTurnActive.put(playerId, false); // Deactivate the player's turn
                }
            }
        }

        // Insurance / dealer peek -- its own explicit phase, decided
        // before any player turn begins, per the table redesign plan's
        // "Insurance -- rules" section. Offered only when the dealer's
        // up-card is an Ace; a ten-value (or any other) up-card just peeks
        // immediately with no insurance UI at all.
        boolean upCardIsAce = !dealerHand.isEmpty() && dealerHand.get(0).getRank() == Rank.ACE;
        if (upCardIsAce && insuranceEnabledForThisTable()) {
            beginInsurancePhase(myGeneration);
        } else {
            performDealerPeekThenProceed(myGeneration);
        }
    }, plan.initialBlackjackCheckDelayTicks()); // Delay slightly longer to allow cards to be fully dealt
}

/** Whether insurance is offered at all this round -- {@code dealers.<name>.insurance.enabled}, loaded once at construction. */
private boolean insuranceEnabledForThisTable() {
    return insuranceEnabled;
}

/**
 * Initializes playerIterator (seated, wagered, not-already-done players in
 * table order) and starts the normal turn-based play. Reached once the
 * dealer's peek confirms no blackjack, whether or not insurance was ever
 * offered this round.
 */
private void beginPlayerTurns(long myGeneration) {
    if (roundGeneration != myGeneration || !gameActive) {
        return;
    }
    playerIterator = orderedSeatedPlayers().stream()
        .filter(playerId -> playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty() && !playerDone.getOrDefault(playerId, false))
        .iterator();
    startNextPlayerTurn();
}

// ---- Insurance phase --------------------------------------------------

/**
 * Offers insurance to every seated player with a committed wager --
 * including natural-blackjack holders (the even-money decision), per the
 * plan's explicit correction. Renders the private Yes/No/countdown UI to
 * each eligible player and starts the table-owned canonical countdown;
 * resolves early (see checkInsuranceAllDecided) once everyone still seated
 * has answered, or at the timeout otherwise.
 */
private void beginInsurancePhase(long myGeneration) {
    List<UUID> eligible = new ArrayList<>();
    for (UUID playerId : orderedSeatedPlayers()) {
        if (BlackjackInsuranceRules.isEligible(totalBet(playerId))) {
            eligible.add(playerId);
        }
    }
    if (eligible.isEmpty()) {
        performDealerPeekThenProceed(myGeneration);
        return;
    }

    insurancePhaseActive = true;
    insuranceEligiblePlayers.clear();
    insuranceEligiblePlayers.addAll(eligible);
    insuranceDecided.clear();
    insuranceStakes.clear();
    insuranceSecondsRemaining = insuranceTimeoutSeconds;

    for (UUID playerId : eligible) {
        renderInsurancePromptForPlayer(playerId);
    }

    insuranceTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        int secondsLeft = insuranceTimeoutSeconds;

        @Override
        public void run() {
            if (roundGeneration != myGeneration || !insurancePhaseActive) {
                if (insuranceTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(insuranceTaskId);
                    insuranceTaskId = -1;
                }
                return;
            }
            if (secondsLeft <= 0) {
                Bukkit.getScheduler().cancelTask(insuranceTaskId);
                insuranceTaskId = -1;
                resolveInsuranceTimeouts(myGeneration);
                return;
            }
            insuranceSecondsRemaining = secondsLeft;
            for (UUID playerId : insuranceEligiblePlayers) {
                if (playerSeats.containsKey(playerId) && !insuranceDecided.contains(playerId)) {
                    renderInsuranceCountdownForPlayer(playerId, secondsLeft);
                }
            }
            secondsLeft--;
        }
    }, 0L, 20L);
}

/** Records {@code playerId}'s Yes/No insurance decision, or does nothing (stays clickable) if Yes was chosen but they can't afford it. */
private void handleInsuranceDecision(Player player, boolean takeInsurance) {
    UUID playerId = player.getUniqueId();
    if (!insurancePhaseActive || !insuranceEligiblePlayers.contains(playerId) || insuranceDecided.contains(playerId)) {
        return;
    }

    if (takeInsurance) {
        BlackjackHand hand = activeHand(playerId);
        double cost = hand != null ? BlackjackInsuranceRules.cost(hand.getOriginalPreSplitWager()) : 0.0;
        if (cost <= 0 || !hasEnoughWager(player, cost)) {
            // Insufficient funds -- still clickable (per the plan), just
            // localized feedback; their decision is NOT consumed, so they
            // can click No (or try Yes again) afterward.
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, "blackjack.insurance-insufficient-funds"));
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            return;
        }
        removeWagerFromInventory(player, cost);
        insuranceStakes.put(playerId, cost);
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, "blackjack.insurance-taken", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, cost)));
        }
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    } else {
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, "blackjack.insurance-declined"));
        }
        if (SoundHelper.getSoundSafely("item.shield.block", player) != null) {
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    insuranceDecided.add(playerId);
    clearInsurancePromptForPlayer(playerId);
    checkInsuranceAllDecided();
}

/** Resolves early once every still-seated eligible player has answered, instead of waiting out the full timeout. */
private void checkInsuranceAllDecided() {
    for (UUID playerId : insuranceEligiblePlayers) {
        if (playerSeats.containsKey(playerId) && !insuranceDecided.contains(playerId)) {
            return; // still waiting on someone
        }
    }
    if (insuranceTaskId != -1) {
        Bukkit.getScheduler().cancelTask(insuranceTaskId);
        insuranceTaskId = -1;
    }
    finishInsurancePhase(roundGeneration);
}

/** Defaults every still-undecided, still-seated eligible player to No, then resolves. */
private void resolveInsuranceTimeouts(long myGeneration) {
    if (roundGeneration != myGeneration || !insurancePhaseActive) {
        return;
    }
    for (UUID playerId : new ArrayList<>(insuranceEligiblePlayers)) {
        if (playerSeats.containsKey(playerId) && !insuranceDecided.contains(playerId)) {
            insuranceDecided.add(playerId);
            clearInsurancePromptForPlayer(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, "blackjack.insurance-declined"));
                }
            }
        }
    }
    finishInsurancePhase(myGeneration);
}

private void finishInsurancePhase(long myGeneration) {
    if (roundGeneration != myGeneration || !insurancePhaseActive) {
        return;
    }
    insurancePhaseActive = false;
    for (UUID playerId : insuranceEligiblePlayers) {
        clearInsurancePromptForPlayer(playerId); // idempotent safety net
    }
    performDealerPeekThenProceed(myGeneration);
}

/**
 * The dealer peeks: with an Ace or ten-value up-card, this determines
 * whether the dealer has a natural blackjack. Blackjack found -> pay
 * insurance winners, reveal the hole card, and settle the round
 * immediately (skipping player turns entirely); no blackjack -> forfeit
 * any insurance stakes (already debited) and proceed into normal
 * turn-based play. In every case, no player turn begins before this
 * resolves -- reached only after the applicable insurance phase (if any).
 */
private void performDealerPeekThenProceed(long myGeneration) {
    if (roundGeneration != myGeneration || !gameActive) {
        return;
    }
    if (BlackjackRules.isNaturalBlackjack(dealerHand)) {
        payInsuranceWinners();
        insuranceStakes.clear();
        revealDealerHoleCardNow();
        for (UUID playerId : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, "blackjack.dealer-peek"));
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myGeneration || !gameActive) {
                return;
            }
            finishGame();
        }, BlackjackTiming.CARD_DEAL_DELAY_TICKS);
    } else {
        forfeitInsuranceStakes();
        insuranceStakes.clear();
        beginPlayerTurns(myGeneration);
    }
}

/** Pays 2:1 + stake to every player who took insurance -- only ever called once the dealer's peek confirms blackjack. */
private void payInsuranceWinners() {
    for (Map.Entry<UUID, Double> entry : insuranceStakes.entrySet()) {
        UUID playerId = entry.getKey();
        double stake = entry.getValue();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            continue;
        }
        double payout = BlackjackInsuranceRules.payoutTotal(stake);
        addWagerToInventory(player, payout);
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, "blackjack.insurance-paid", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, payout)));
        }
        if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.2f);
        }
    }
}

/** Messaging only -- the stake was already debited when insurance was taken, so "forfeiting" it just means not paying it back. */
private void forfeitInsuranceStakes() {
    for (UUID playerId : insuranceStakes.keySet()) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            continue;
        }
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, "blackjack.insurance-lost"));
        }
    }
}

/** Private Yes(Totem)/No(Barrier) + countdown, rendered only into {@code playerId}'s own open view. */
private void renderInsurancePromptForPlayer(UUID playerId) {
    Player viewer = Bukkit.getPlayer(playerId);
    renderPrivateItem(playerId, BlackjackSlotLayout.INSURANCE_YES_SLOT, createCustomItem(Material.TOTEM_OF_UNDYING, localize(viewer, "blackjack.insurance-yes"), 1));
    renderPrivateItem(playerId, BlackjackSlotLayout.INSURANCE_NO_SLOT, createCustomItem(Material.BARRIER, localize(viewer, "blackjack.insurance-no"), 1));
    renderInsuranceCountdownForPlayer(playerId, insuranceSecondsRemaining);
    if (viewer != null) {
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                viewer.sendMessage(text(viewer, "blackjack.insurance-prompt"));
        }
    }
}

private void renderInsuranceCountdownForPlayer(UUID playerId, int secondsLeft) {
    Player viewer = Bukkit.getPlayer(playerId);
    Integer seatSlot = playerSeats.get(playerId);
    if (seatSlot == null) {
        return;
    }
    int slot = BlackjackSlotLayout.insuranceTimerSlot(seatSlot);
    renderPrivateItem(playerId, slot, createCustomItem(Material.CLOCK, localize(viewer, "blackjack.insurance-timer-lore", "seconds", secondsLeft), Math.max(secondsLeft, 1)));
}

private void clearInsurancePromptForPlayer(UUID playerId) {
    renderPrivateItem(playerId, BlackjackSlotLayout.INSURANCE_YES_SLOT, buildBackgroundPaneItem());
    renderPrivateItem(playerId, BlackjackSlotLayout.INSURANCE_NO_SLOT, buildBackgroundPaneItem());
    Integer seatSlot = playerSeats.get(playerId);
    if (seatSlot != null) {
        renderPrivateItem(playerId, BlackjackSlotLayout.insuranceTimerSlot(seatSlot), buildBackgroundPaneItem());
    }
}

/**
 * Table-wide teardown only (reset/cancel/delete): stops the canonical
 * insurance countdown task and the canonical turn-timer task (both
 * table-owned deadlines, never stopped just because one viewer's inventory
 * closes -- see the class docs on each), and clears all insurance
 * bookkeeping.
 */
private void stopInsurancePhaseBookkeeping() {
    if (insuranceTaskId != -1) {
        Bukkit.getScheduler().cancelTask(insuranceTaskId);
        insuranceTaskId = -1;
    }
    insurancePhaseActive = false;
    insuranceEligiblePlayers.clear();
    insuranceDecided.clear();
    insuranceStakes.clear();
    stopTurnTimerTask();
}

// ---- Player-turn timer (slot 46) --------------------------------------

/**
 * (Re)starts the canonical turn-timer deadline for {@code playerId}'s
 * current hand -- called exclusively from beginActionableDecision's
 * non-empty-layout branch, so it fires only when a fresh decision actually
 * becomes available (initial turn start, after a Hit, or a split-hand
 * activation) and is implicitly reset by that same call whenever the
 * previous deadline is superseded. Never invoked for a plain repaint (an
 * invalid click, a failed action, reopening a view). No-op (and immediately
 * restores the idle brown-glass slot) when turn-timer.enabled is false.
 */
private void startTurnTimer(UUID playerId) {
    stopTurnTimerTask();
    if (!turnTimerEnabled) {
        return;
    }
    long myGeneration = roundGeneration;
    int myHandToken = currentHandToken(playerId);

    turnTimerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        int secondsLeft = turnTimerTimeoutSeconds;

        @Override
        public void run() {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                // The hand this deadline belonged to has already moved on
                // (Stand/Double/leave/reset/completion) -- stop silently,
                // whatever superseded it owns slot 46 now.
                if (turnTimerTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(turnTimerTaskId);
                    turnTimerTaskId = -1;
                }
                return;
            }
            if (secondsLeft <= 0) {
                Bukkit.getScheduler().cancelTask(turnTimerTaskId);
                turnTimerTaskId = -1;
                autoStandOnTurnTimeout(playerId, myGeneration, myHandToken);
                return;
            }
            renderTurnTimerToAllViews(secondsLeft);
            secondsLeft--;
        }
    }, 0L, 20L);
}

/** Cancels the turn-timer task, if any, and restores slot 46 to its idle brown-glass state for every view. */
private void stopTurnTimerTask() {
    if (turnTimerTaskId != -1) {
        Bukkit.getScheduler().cancelTask(turnTimerTaskId);
        turnTimerTaskId = -1;
    }
    if (gameActive) {
        // Only during active play does slot 46 belong to the turn timer at
        // all; outside it (transitionBottomBarToActive/resetGame/etc.) some
        // other phase already owns the slot's rendering.
        renderToAllViews(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem());
    }
}

private void renderTurnTimerToAllViews(int secondsLeft) {
    int amount = Math.max(secondsLeft, 1);
    inventory.setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, createCustomItem(Material.CLOCK, text("blackjack.turn-timer-lore", "seconds", secondsLeft), amount));
    for (BlackjackView view : views.values()) {
        Player viewer = Bukkit.getPlayer(view.getPlayerId());
        view.getInventory().setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, createCustomItem(Material.CLOCK, localize(viewer, "blackjack.turn-timer-lore", "seconds", secondsLeft), amount));
    }
}

/**
 * On timeout, auto-Stands exactly the hand whose deadline expired --
 * guarded by roundGeneration + handToken + expected-actionable-state, the
 * same validation model as every other scheduled per-hand callback, so a
 * timeout can never fire against a hand that's already moved on.
 */
private void autoStandOnTurnTimeout(UUID playerId, long myGeneration, int myHandToken) {
    synchronized (turnLock) {
        if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
            return;
        }
        playerDone.put(playerId, true);
        playerTurnActive.put(playerId, false);
        bumpHandToken(playerId);
        repaintActionsForCurrentPlayer();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, "blackjack.turn-timer-expired"));
            }
        }
        startNextPlayerTurnWithDelay(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
    }
}

private void startNextPlayerTurn() {
    if (!gameActive || playerSeats.isEmpty()) {
       // cancelGame(); // If game is no longer active or all players have left, stop immediately
        return;
    }
    // Initialize playerIterator if it's null or the previous iteration has ended
    if (playerIterator == null || !playerIterator.hasNext()) {
        // Create a new iterator, in table order, with players who have active bets and are not done
        playerIterator = orderedSeatedPlayers().stream()
            .filter(playerId -> playerBets.containsKey(playerId) && !playerBets.get(playerId).isEmpty() && !playerDone.getOrDefault(playerId, false))
            .iterator();
    }

    // Now proceed with the turn if there are players left
    while (playerIterator.hasNext()) {

        UUID previousPlayerId = currentPlayerId;
        currentPlayerId = playerIterator.next();

        if (!playerDone.getOrDefault(currentPlayerId, false)) { // Skip players who are done
            Player currentPlayer = Bukkit.getPlayer(currentPlayerId);

            switch(plugin.getPreferences(currentPlayer.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{

                    currentPlayer.sendMessage(text(currentPlayer, "blackjack.your-turn-message"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("block.enchantment_table.use", currentPlayer) != null)currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);

            refreshCardGlow(previousPlayerId, currentPlayerId);

            // Check if the player's hand value is 21
            int handValue = calculateHandValue(activeHandCards(currentPlayerId));
            if (handValue == 21) {
                playerDone.put(currentPlayerId, true); // Mark the player as done
                playerTurnActive.put(currentPlayerId, false); // Deactivate the player's turn
                bumpHandToken(currentPlayerId);
                repaintActionsForCurrentPlayer();
                startNextPlayerTurnWithDelay(20L); // Start the next player's turn with delay
                return; // Skip to the next player
            }

            // Set player's turn as active -- initial activation of this
            // hand's decision is a legitimate fresh deadline.
            playerTurnActive.put(currentPlayerId, true);
            beginActionableDecision();

            return;
        }
    }

    UUID lastPlayerId = currentPlayerId;
    currentPlayerId = null;
    refreshCardGlow(lastPlayerId, null);
    repaintActionsForCurrentPlayer();

    // No more players left, proceed to the dealer's turn
    startDealerTurn();
}

private void startDealerTurn() {

    // Check if all players have busted
    boolean allPlayersBusted = true;
    for (UUID playerId : playerSeats.keySet()) {
        int playerCardSum = calculateHandValue(activeHandCards(playerId));
        if (playerCardSum <= 21) {  // If any player has not busted
            allPlayersBusted = false;
            break;
        }
    }
    if (allPlayersBusted) {

        finishGame(); // Directly finish the game if all players are busted
        return;
    }

    // Reveal the dealer's hidden card with delay
    revealDealerCardWithDelay(20L);

    // Dealer must hit until reaching at least 17. Cards continue leftward
    // from the hole card (51) toward 47 -- descending, not ascending; see
    // the table redesign plan's "Open item to verify" note and
    // BlackjackSlotLayout#dealerCardSlot.
    Bukkit.getScheduler().runTaskLater(plugin, () -> dealDealerCardsUntilSeventeen(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT - 1, calculateHandValue(dealerHand), 20L), 40L); // Start dealer's turn after revealing with delay
}

private void revealDealerCardWithDelay(long delay) {
    Bukkit.getScheduler().runTaskLater(plugin, this::revealDealerHoleCardNow, delay);
}

/**
 * Paints the dealer's hole card face-up. The Card itself was already
 * drawn from the shoe back at deal time (see dealInitialCards --
 * scheduleHiddenCardDealing), specifically so the dealer peek can know
 * its rank before this visual reveal ever happens; this method only
 * renders it, it never adds a new card to dealerHand. Reached either from
 * the normal dealer's-turn reveal (revealDealerCardWithDelay) or
 * immediately from performDealerPeekThenProceed when the peek finds a
 * natural blackjack.
 */
private void revealDealerHoleCardNow() {
    ItemStack hiddenCard = inventory.getItem(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT);
    if (hiddenCard != null && hiddenCard.getType() == Material.WHITE_STAINED_GLASS_PANE && dealerHand.size() > 1) {
        hiddenCardPlaceholderVisible = false;
        Card holeCard = dealerHand.get(1);
        renderCardToAllViews(BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT, holeCard, false);
        updateDealerHead(); // Update dealer's head with new card value
    }
}

private void dealDealerCardsUntilSeventeen(int nextSlot, int dealerCardSum, long delay) {
    if (!gameActive || playerSeats.isEmpty()) {
    // If game is no longer active or all players have left, stop immediately
        return;
    }
    final int[] mutableDealerCardSum = {dealerCardSum}; // Wrap the dealerCardSum in an array to make it mutable
    Nccasino nccasino = (Nccasino) plugin;



    int standOn17Chance=100;

    if (!nccasino.getConfig().contains("dealers." + internalName + ".stand-on-17")) {
        // If the key doesn't exist, set it to 100
        nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
    } else {
        // Retrieve the current value
        String value = plugin.getConfig().getString("dealers." + internalName + ".stand-on-17", "100").trim();

        try {
            standOn17Chance = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            standOn17Chance = 100; // Default
            plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
            plugin.saveConfig();
        }

        // Check if the value is greater than 100 or less than 0
        if (standOn17Chance > 100 ) {
            // Reset the value to 100
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
        }
        else if(standOn17Chance < 0){
            nccasino.getConfig().set("dealers." + internalName + ".stand-on-17", 0);

        }
    }

    if (mutableDealerCardSum[0] < 17 && deck.hasCards()) {
        Card newCard = deck.dealCard();
        dealCardToPlayer(nextSlot, newCard, null); // Deal the card to the dealer
        mutableDealerCardSum[0] = calculateHandValue(dealerHand); // Recalculate after adding each card

        // Update the dealer head after dealing a new card
        updateDealerHead();

        // Schedule the next card if needed
        Bukkit.getScheduler().runTaskLater(plugin, () -> dealDealerCardsUntilSeventeen(nextSlot - 1, mutableDealerCardSum[0], delay), delay);
    } else if (mutableDealerCardSum[0] == 17) {
        // Determine whether the dealer stops at 17 based on the percentage chance
        if (!BlackjackRules.dealerShouldHit(17, standOn17Chance, Math.random() * 100)) {
            // Stop at 17
            Bukkit.getScheduler().runTaskLater(plugin, this::finishGame, delay);
        } else {
            // Continue if the dealer does not stop at 17
            if (deck.hasCards()) {
                Card newCard = deck.dealCard();
                dealCardToPlayer(nextSlot, newCard, null);
                mutableDealerCardSum[0] = calculateHandValue(dealerHand);
                updateDealerHead();

                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    dealDealerCardsUntilSeventeen(nextSlot - 1, mutableDealerCardSum[0], delay), delay);
            }
        }
    } else {
        // Proceed to finish the game after the dealer's turn
        Bukkit.getScheduler().runTaskLater(plugin, this::finishGame, delay);
    }
}


/**
 * Deals a hit/double-down card after a delay, guarded by the same
 * round-generation/hand-token pair as its later evaluation (see
 * isStaleHandCallback) -- not just the "player still seated" check inside
 * dealCardToPlayer. That check alone can't tell a still-seated player's
 * stale card (left over from a resolved or superseded hand) from a live
 * one; the token/generation pair can.
 */
private void scheduleCardDealingWithDelay(int slot, Card card, long delay, UUID playerId, long myGeneration, int myHandToken) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
            return;
        }
        dealCardToPlayer(slot, card, playerId);
    }, delay);
}


/**
 * Settles every {@link BlackjackHand} across every seated player
 * independently, using each hand's own {@code handId}-stable wager --
 * required for splitting, where one player can hold several
 * simultaneously-resolved hands with different outcomes (bust one,
 * blackjack-value-win another) from the very same round.
 */
private void finishGame() {
    for (UUID playerId : playerSeats.keySet()) {
        List<BlackjackHand> hands = playerHands.get(playerId);
        if (hands == null || hands.isEmpty()) {
            continue; // Skip players who never got dealt in
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            continue;
        }

        for (BlackjackHand hand : hands) {
            if (hand.getWager() <= 0) {
                continue;
            }
            // A player natural blackjack pays 3:2 unless the dealer also
            // has a natural, in which case the main wager pushes -- see
            // BlackjackRules.classify and BlackjackRulesTest's both-natural
            // case. eligibleForNaturalBlackjack scopes split-21-is-blackjack
            // to exactly a two-card post-split 21 -- see BlackjackHand's doc.
            boolean eligibleForNatural = hand.eligibleForNaturalBlackjack(split21IsBlackjack);
            BlackjackOutcome outcome = BlackjackRules.classify(hand.getCards(), dealerHand, eligibleForNatural);
            settleHandOutcome(player, hand, outcome);
        }
    }

    // Reset game for the next round
    resetGame();
}

/** Settles one hand's outcome: messaging/sounds/particles exactly as before, now driven by that hand's own wager rather than the player's whole bet-slip total. */
private void settleHandOutcome(Player player, BlackjackHand hand, BlackjackOutcome outcome) {
    switch (outcome) {
        case BLACKJACK: {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.result-blackjack"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.result-blackjack"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
            if (SoundHelper.getSoundSafely("ui.toast.challenge_complete", player) != null)player.playSound(player.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.MASTER, 1.0f,1.0f);
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            payOut(player, hand.getWager(), outcome.getMultiplier()); // Pay out 2.5x for a blackjack
            break;
        }
        case BUST: {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.result-busted"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.result-busted"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
            break;
        }
        case WIN: {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.result-won"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.result-won"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            Random random = new Random();
            // We'll pick from a small array of fun pitches
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f,0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                 if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,SoundCategory.MASTER,1.0f,chosenPitch);
                // Schedule them slightly apart for a "ding-ding-ding" effect

            }
            payOut(player, hand.getWager(), outcome.getMultiplier()); // Regular win pays out 2x
            break;
        }
        case LOSS: {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.result-lost"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.result-lost"));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
             if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
            break;
        }
        case PUSH: {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.result-push"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.result-push-returned"));
                    break;

                }
                    case NONE:{
                    break;
                }
            }
            addWagerToInventory(player, hand.getWager());
             if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(),Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 20);
            break;
        }
        default:
            // Never silently fall back to a push/refund for an outcome
            // this switch doesn't know about -- that's exactly the
            // mistake that would hide a bug once insurance/splitting
            // add new outcomes.
            throw new IllegalStateException("Unhandled BlackjackOutcome: " + outcome);
    }
}

/** Pays out {@code multiplier}x a specific hand's own wager -- required by per-hand payout so each of a player's simultaneously-resolved split hands pays independently. */
private void payOut(Player player, double totalBet, double multiplier) {
    {
		CurrencyProvider provider = getCurrencyProvider();

		if (provider != null && provider.getMode() == org.nc.nccasino.currency.CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
			java.math.BigDecimal betAmount = MoneyHelper.clampNonNegative(MoneyHelper.bd(totalBet));
			java.math.BigDecimal payout = betAmount.multiply(MoneyHelper.bd(multiplier));
			java.math.BigDecimal displayPayout = MoneyHelper.roundDisplay(payout);
			java.math.BigDecimal displayProfit = MoneyHelper.roundDisplay(payout.subtract(betAmount));

			if (payout.compareTo(java.math.BigDecimal.ZERO) > 0) {
				vaultProvider.deposit(player, internalName, payout);
			}

			switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
				case STANDARD:{
					player.sendMessage(text(
                        player,
                        "payout.paid",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayPayout.doubleValue())
                    ));
					break;}
				case VERBOSE:{
					player.sendMessage(text(
                        player,
                        "payout.paid-with-profit",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayPayout.doubleValue()),
                        "profit",
                        plugin.formatWagerDisplay(currencyMode, currencyName, displayProfit.doubleValue())
                    ));
					break;
				}
					case NONE:{
					break;
				}
			}
			return;
		}

        double payout = totalBet * multiplier;
        int totalAmount = applyProbabilisticRounding(payout);

        int fullStacks = totalAmount / 64;
        int remainder = totalAmount % 64;
        Material currencyMaterial = plugin.getCurrency(internalName);
        int totalDropped = 0; // Track how many items were dropped

        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, 64);
            } else {
                stack = new ItemStack(currencyMaterial, 64);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }

        if (remainder > 0) {
            ItemStack stack = null;
            if (provider != null) {
                stack = provider.createCurrencyStack(internalName, remainder);
            } else {
                stack = new ItemStack(currencyMaterial, remainder);
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    totalDropped += item.getAmount();
                }
            }
        }
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(text(
                    player,
                    "payout.paid",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalAmount)
                ));
                break;}
            case VERBOSE:{
                player.sendMessage(text(
                    player,
                    "payout.paid-with-profit",
                    "amount",
                    plugin.formatWagerDisplay(currencyMode, currencyName, totalAmount),
                    "profit",
                    (int) Math.abs(totalAmount - totalBet)
                ));
                break;
            }
                case NONE:{
                break;
            }
        }

        // Print total dropped if any items couldn't fit in inventory
        if (totalDropped > 0) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(
                        player,
                        "blackjack.inventory-full",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalDropped)
                    ));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(
                        player,
                        "blackjack.inventory-full",
                        "amount",
                        plugin.formatWagerDisplay(currencyMode, currencyName, totalDropped)
                    ));
                    break;
                }
                    case NONE:{
                    break;
                }
            }
         }
    }
}

private int applyProbabilisticRounding(double value) {
    int integerPart = (int) value;
    double fractionalPart = value - integerPart;
    Random random = new Random();
    if (random.nextDouble() <= fractionalPart) {
        return integerPart + 1; // Round up based on probability
    }
    return integerPart; // Otherwise, keep it rounded down
}

private void resetGame() {
    gameActive = false;
    roundGeneration++;
    handToken.clear();
    // Table-wide event: any shared animation (dealer U-path, split
    // sequence) and every private one both end here -- the whole round is
    // over, not just one viewer's inventory closing.
    cancelAllAnimations();

    playerBets.clear();
    lastBetAmounts.clear();
    playerCardCounts.clear(); // Clear the card count map
    playerDone.clear(); // Clear the player status map
    playerHands.clear(); // Clear player hands
    activeHandIndex.clear();
    dealerHand.clear(); // Clear dealer's hand
    playerIterator = null;
    currentPlayerId = null;
    hiddenCardPlaceholderVisible = false;
    dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
    startTransitionActive = false;
    startTransitionDoorConcealComplete.clear();
    stopInsurancePhaseBookkeeping();
    playerTurnActive.clear();

    // Cancel any ongoing countdown
    if (countdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(countdownTaskId);
        countdownTaskId = -1;
    }

    // Clear the inventory to ensure no leftover items
    inventory.clear();
    for (BlackjackView view : views.values()) {
        view.getInventory().clear();
    }

    // Reinitialize the game menu but do not clear player seats.
    // initializeGameMenu() clears every inventory itself -- the status
    // clock must be painted after it, not before, or this clock item
    // would immediately be wiped out again for every already-open view.
    initializeGameMenu();

    // Idle status -- no countdown running yet for the next lobby/countdown.
    leverKey = null;
    leverPlaceholders = new Object[0];

    // Re-populate the player heads in the seats
    for (UUID playerId : playerSeats.keySet()) {
        int seatSlot = playerSeats.get(playerId);
        renderToAllViews(seatSlot, createPlayerHeadItem(Bukkit.getPlayer(playerId), 1));
    }

}

private int calculateHandValue(List<Card> hand) {
    return BlackjackRules.handValue(hand);
}

private void scheduleCardDealing(int slot, Card card, int delay, UUID playerId, long myGeneration) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (roundGeneration != myGeneration || !gameActive || playerSeats.isEmpty()) {
            return;
        }
        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                 if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }

        dealCardToPlayer(slot, card, playerId);
    }, delay);
}

private void updatePlayerHead(UUID playerId) {
    if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty() || playerSeats.get(playerId) == null) {
        return; // Skip updating if the player hasn't placed a bet
    }

    List<Card> hand = activeHandCards(playerId);
    String handValue = calculateHandValueWithSoftCheck(hand);

    int seatSlot = playerSeats.get(playerId);
    renderHeadLoreToAllViews(seatSlot, handValue, Bukkit.getPlayer(playerId).getName(), null);
}

private void updateDealerHead() {
    String handValue = calculateHandValueWithSoftCheck(dealerHand);
    renderHeadLoreToAllViews(dealerHeadSlot, handValue, null, "blackjack.dealer");
}
private String calculateHandValueWithSoftCheck(List<Card> hand) {
    if (hand == null || hand.isEmpty()) {
        return "0";
    }

    BlackjackHandValue value = BlackjackRules.evaluate(hand);

    // Return soft/hard value if an ace is present; otherwise, just return the hard value
    if (value.isSoft()) {
        return value.getBestTotal() + "/" + value.getHardTotal();
    } else {
        return String.valueOf(value.getHardTotal());
    }
}

/**
 * Schedules the dealer's hole card to appear as a hidden placeholder --
 * but {@code holeCard} is already drawn from the shoe by the caller
 * (dealInitialCards) at plan-build time, not lazily here, so its rank is
 * known immediately for the dealer peek. Only the visual placeholder is
 * delayed; the canonical dealerHand gets the real card the moment this
 * callback fires.
 */
private void scheduleHiddenCardDealing(int slot, Card holeCard, long delay, long myGeneration) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (roundGeneration != myGeneration || !gameActive || playerSeats.isEmpty()) {
            return;
        }
        for (UUID uuid : playerSeats.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                 if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
        dealerHand.add(holeCard);
        hiddenCardPlaceholderVisible = true;
        renderHiddenCardToAllViews(slot);
    }, delay);
}

/**
 * Deals one Card either to a seated player (playerId non-null) or the
 * dealer (playerId null), always updating canonical hand state; only
 * skips the visual render (via isRenderableCardSlot) if slot has escaped
 * that owner's eight-slot row -- canonical state never drops a card just
 * because it can't currently be shown (see BlackjackSlotLayout's bounded
 * row).
 */
private void dealCardToPlayer(int slot, Card card, UUID playerId) {
    if (playerId != null && !playerSeats.containsKey(playerId)) {
        return;
    }
    for (UUID uuid : playerSeats.keySet()) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
             if (SoundHelper.getSoundSafely("block.soul_soil.step", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    if (playerId != null) { // If this card is dealt to a player
        ensureActiveHand(playerId).addCard(card);
        if (isRenderableCardSlot(playerId, slot)) {
            renderCardToAllViews(slot, card, playerId.equals(currentPlayerId));
        }
        updatePlayerHead(playerId);
    } else { // If this card is dealt to the dealer
        dealerHand.add(card);
        if (isRenderableCardSlot(null, slot)) {
            renderCardToAllViews(slot, card, false);
        }
        updateDealerHead();
    }
}


public void delete() {
    // Stop any ongoing game operations
    cancelGame();

    // Clear all player data and bets
    clearPlayerBets(null);
    playerSeats.clear();
    playerHands.clear();
    activeHandIndex.clear();
    playerCardCounts.clear();
    playerTurnActive.clear();
    playerDone.clear();
    selectedWager.clear();
    pregameWagerIncrements.clear();
    lastBetAmounts.clear();
    hiddenCardPlaceholderVisible = false;
    dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
    startTransitionActive = false;
    startTransitionDoorConcealComplete.clear();
    stopInsurancePhaseBookkeeping();
    roundGeneration++;
    handToken.clear();

    // Remove any scheduled tasks related to this game
    if (countdownTaskId != -1) {
        Bukkit.getScheduler().cancelTask(countdownTaskId);
    }

    // Close and clean up every currently open per-player view. Views hold
    // their own Bukkit inventory (a different InventoryHolder than this
    // controller), so a generic holder-matching close loop elsewhere can't
    // find them by comparing against this instance -- do it here instead,
    // using the map we actually control, so a replaced/removed dealer never
    // leaves a stale view open against a deleted controller.
    for (BlackjackView view : new ArrayList<>(views.values())) {
        Player player = Bukkit.getPlayer(view.getPlayerId());
        if (player != null && player.isOnline()) {
            player.closeInventory();
        } else {
            views.remove(view.getPlayerId());
            view.cleanupListener();
        }
    }
    views.clear();

    // Unregister events related to this inventory
    HandlerList.unregisterAll(this);
    // Clear the inventory itself
    inventory.clear();

    DealerInventory.inventories.remove(dealerId);

    // Mark this inventory as deleted
    inventory = null;
}


    // Cancel the game and reset the board with all items and options
    private void cancelGame() {
        gameActive = false;
        roundGeneration++;
        handToken.clear();
        // Table-wide event -- ends every animation, shared and private alike.
        cancelAllAnimations();

        // Clear all player and game-related data
        clearPlayerBets(null); // Clear all bets
        clearAllLore(); // Clear all lore
        playerBets.clear();
        lastBetAmounts.clear();
        playerCardCounts.clear();
        playerDone.clear();
        playerHands.clear();
        activeHandIndex.clear();
        dealerHand.clear();
        playerIterator = null;
        currentPlayerId = null;
        selectedWager.clear();
        hiddenCardPlaceholderVisible = false;
        dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
        startTransitionActive = false;
        startTransitionDoorConcealComplete.clear();
        stopInsurancePhaseBookkeeping();
        playerTurnActive.clear();

        // Stop any ongoing scheduled tasks
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }

        // Clear and reset the inventory
        inventory.clear();
        initializeGameMenu(); // Reset the game menu

        // Reset player seats
        playerSeats.clear();

    }


        @EventHandler
        public void handleInventoryClose(InventoryCloseEvent event) {
            // Defensive fallback only: with per-player views wired up,
            // nobody has this legacy inventory open directly anymore, but
            // keep this reachable in case it ever is (matches the internal
            // rendering target role documented on the `views` field).
            if (event.getInventory().getHolder() != this) return;
            handlePlayerClose((Player) event.getPlayer());
        }

    /**
     * Authoritative "this player's Blackjack inventory (view or legacy)
     * just closed" path, reached from both the legacy handleInventoryClose
     * above and BlackjackView's own close/quit handlers via onViewClosed.
     * Extracted so a per-player view closing goes through the exact same
     * disconnect/settlement resolution the shared inventory always has.
     */
    private void handlePlayerClose(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerSeats.containsKey(playerId)) {
            return;
        }

        // Route through the same idempotent path used for quit/kick,
        // rather than resolving the wager directly here. It's unclear
        // whether InventoryCloseEvent reliably fires on a real
        // disconnect, so this must not race ahead of PlayerQuitEvent
        // and get it wrong (e.g. refunding a kicked player) — whichever
        // of this or the quit event fires first "wins" and the other
        // becomes a safe no-op, and consumeQuitReason still correctly
        // reports KICKED here even if this fires first, since the kick
        // is marked as soon as PlayerKickEvent itself fires.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!SessionRegistry.isRegistered(playerId, this)) {
                return;
            }
            if (!player.isOnline()) {
                ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
                SessionRegistry.terminatePlayerSession(playerId, reason);
                return;
            }
            SessionRegistry.terminateSession(playerId, this, ExitReason.DISCONNECTED);
        });
    }

    /**
     * Authoritative disconnect/kick resolution, reached via SessionRegistry
     * regardless of whether this fires from PlayerQuitEvent or from this
     * table's own InventoryCloseEvent. Transition is based on {@code
     * gameActive} — set the instant dealing is committed server-side, not
     * on any animation — never on what the client had visibly rendered.
     */
    @Override
    public void onSessionTerminated(UUID playerId, ExitReason reason) {
        if (!playerSeats.containsKey(playerId)) {
            // Already resolved through another path (e.g. a voluntary
            // leave-chair click that ran before this).
            return;
        }

        org.nc.nccasino.session.TerminationAction action =
            GameTerminationPolicy.blackjack(reason, gameActive);
        if (action == org.nc.nccasino.session.TerminationAction.FORFEIT) {
            // Deal has begun: forfeit unconditionally. Never calculate an
            // automated hand or create a pending payout. Advance the turn
            // safely first if it was theirs.
            if (playerId.equals(currentPlayerId)) {
                playerDone.put(playerId, true);
                playerTurnActive.put(playerId, false);
                bumpHandToken(playerId);
                advanceTurnNow();
            }
            removePlayerData(playerId);
        } else if (action == org.nc.nccasino.session.TerminationAction.REFUND) {
            // Before the deal: refund the committed wager, unless kicked —
            // a kicked player forfeits regardless of phase.
            refundPendingBets(playerId, reason);
            removePlayerData(playerId);
        } else {
            removePlayerData(playerId);
        }

    }

    /**
     * Refunds whatever {@code playerId} currently has wagered. Called
     * before removePlayerData, which clears the underlying bet records
     * without moving any currency itself.
     */
    private void refundPendingBets(UUID playerId, ExitReason reason) {
        Map<Integer, Double> bets = playerBets.get(playerId);
        if (bets == null || bets.isEmpty()) {
            return;
        }

        double total = bets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return;
        }

        if (reason == ExitReason.PLUGIN_DISABLE) {
            Material currencyMaterial = plugin.getCurrency(internalName);
            PendingPayout payout = PendingPayout.create(
                playerId,
                "Blackjack",
                internalName,
                currencyMode,
                currencyMaterial != null ? currencyMaterial.name() : null,
                currencyName,
                total,
                PayoutMessages.serverRestartRefundContext("Blackjack")
            );
            if (!plugin.getPendingPayoutStore().addPendingPayout(payout)) {
                plugin.getLogger().warning("[NCCasino] Blackjack shutdown refund failed to persist for " + playerId + ".");
            }
        } else {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                addWagerToInventory(player, total);
            }
        }
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
