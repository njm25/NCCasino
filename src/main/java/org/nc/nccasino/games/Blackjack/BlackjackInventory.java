package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.Collections;
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

    /** See {@link #runHandTransitionReveal}'s own doc: the first slot clear of the collapsed previous hand's own two cards -- where the next hand's reveal first becomes visible, sliding out from under the previous hand rather than simply materializing already off to the side. */
    private static final int HAND_TRANSITION_REVEAL_START_OFFSET = 2;
    /** See {@link #runHandTransitionReveal}'s own doc: how many slots right of its real position the next hand's reveal slides out to before reversing -- two slots past {@link #HAND_TRANSITION_REVEAL_START_OFFSET}, so a visible two-slot background gap opens up against the collapsed previous hand. */
    private static final int HAND_TRANSITION_REVEAL_OUT_OFFSET = 4;

    private final Nccasino plugin; // Reference to the main plugin
    private final Map<Integer, Double> chipValues; // Track chip values by fixed inventory slot
    private final String internalName; // Internal name for config lookup
    private final CurrencyMode currencyMode;
    private final String currencyName;
    /** Seated players whose bet-spot glow is transiently forced off mid hand-to-hand transition (see {@link #runHandTransitionCollapse}/{@link #runHandTransitionReveal}) -- otherwise {@link #buildBetSpotItemForViewer} derives glow purely from "is it this player's turn," which stays true the whole time and would leave the bet spot glowing while the transitioning hand itself briefly isn't. */
    private final Set<UUID> betSpotGlowSuppressed = new HashSet<>();

    private final Map<UUID, Integer> playerSeats; // Track player seats
    private final Map<UUID, Map<Integer, Double>> playerBets; // Track player bets by slot number
    private final Map<UUID, List<Double>> lastBetAmounts; // Track the last bet amounts placed by the player
    private boolean gameActive; // Track whether the game is active
    private int countdownTaskId; // Task ID for the countdown timer
    private UUID currentPlayerId; // Track the current player
    private Iterator<UUID> playerIterator; // Iterator for player turns
    private final Map<UUID, Integer> playerCardCounts = new HashMap<>(); // Track number of cards each player has
    private final Map<UUID, Boolean> playerDone = new HashMap<>(); // Track whether the player is done (stood or busted)
    // Per-player hand queue -- a single BlackjackHand until a split
    // inserts a sibling (see handleSplit/BlackjackSplitQueue), at which
    // point this list grows depth-first. activeHandIndex is a live pointer
    // (looked up, not captured across ticks) -- see ensureActiveHand/activeHand.
    private final Map<UUID, List<BlackjackHand>> playerHands = new HashMap<>();
    private final Map<UUID, Integer> activeHandIndex = new HashMap<>();
    private final List<Card> dealerHand = new ArrayList<>();
    // Wager selection vs. commitment (see the table redesign plan): a
    // chip/all-in click only sets a persistent selection tool here, moving
    // no funds and touching no ledger -- only a bet-spot click actually
    // commits it (see commitWager/commitWagerFundsAlreadyRemoved), pushing
    // onto pregameWagerIncrements below. Deliberately NOT cleared by a
    // normal round reset (see clearConsumedRoundWagerLedger, which only
    // touches pregameWagerIncrements) -- a seated player's selection
    // persists across rounds exactly like their seat does. Only cleared by
    // leaving the chair (clearPlayerBets(playerId), from removePlayerData)
    // or picking a different selection (selectWager overwrites the entry).
    // See BlackjackWagerSelection for the FIXED-vs-ALL_IN typed model.
    private final Map<UUID, BlackjackWagerSelection> selectedWager = new HashMap<>();
    // Round-scoped "has this player already been guided" completion flags
    // (see the table redesign plan's chair/wager guidance-completion
    // requirement) -- separate from selectedWager/playerSeats themselves so
    // a same-round leave+reseat (which clears the selection, per above, and
    // removes the seat) does NOT re-trigger guidance that already did its
    // job earlier in this round. Only cleared by resetGame's own genuine
    // round boundary -- deliberately NOT by cancelGame (also reached from
    // an ordinary single-player pregame leave, which must never un-complete
    // that very player's own guidance) or delete (the controller is being
    // discarded entirely; nothing left to guide).
    private final Set<UUID> chairGuidanceCompleted = new HashSet<>();
    private final Set<UUID> wagerGuidanceCompleted = new HashSet<>();
    // The wager bar's live solid-slide position/target per viewer -- see
    // BlackjackWagerRevealPlan#CLOSED (0, fully unseated) / #OPEN (8, fully
    // seated) and requestWagerBarPosition. wagerBarPosition is the frame
    // actually last rendered (independent of whatever scheduled callback is
    // in flight -- read fresh every frame so a reversal mid-slide continues
    // from exactly where the strip visually is, never jumping to an
    // endpoint first); wagerBarTarget is which endpoint the current/most
    // recent request is heading toward, used to make repeated requests for
    // the same target idempotent. Deliberately NOT touched by
    // cancelPrivateAnimation -- that only cancels/replaces the scheduled
    // callback chain (see its own doc); only a genuine view-close or
    // table-wide teardown (cancelAllAnimations) discards these two maps.
    private final Map<UUID, Integer> wagerBarPosition = new HashMap<>();
    private final Map<UUID, Integer> wagerBarTarget = new HashMap<>();
    // Committed wager-increment ledger, per player -- the actual source of
    // truth for a seated player's pregame wager once they've committed at
    // least one increment (see commitWagerFundsAlreadyRemoved,
    // syncPlayerBetsFromLedger, handleUndoLastBet/handleUndoAllBets). The
    // legacy playerBets/lastBetAmounts maps below are kept in sync from
    // this ledger's own total, not the other way around.
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
    // The deck token's current slot, trailing one row behind the dealer
    // head during the start-transition slide (see applyDealerInspectionStep)
    // -- invisible (-1) until the dealer's first move, resting one slot
    // above the dealer's final in-play position for the rest of the round,
    // where dealt/hit cards visually originate from.
    private int dealerDeckTokenSlot = -1;
    // True from the moment the pregame countdown hits zero until the
    // readiness gate opens and activateGame() actually deals -- see
    // beginStartTransition/isReadyToDeal. Drives BlackjackFrame.Phase.START_TRANSITION.
    private boolean startTransitionActive = false;
    // Seated players whose private door-conceal animation has finished
    // (the final step, door arrived at slot 45) during the current
    // start-transition attempt. Cleared at the start of every
    // beginStartTransition call; read by isReadyToDeal's polling gate.
    private final Set<UUID> startTransitionDoorConcealComplete = new HashSet<>();
    // The exact set of players who were seated at the moment
    // beginStartTransition snapshotted them for door-conceal (see
    // beginStartTransition) -- isReadyToDeal awaits only these players, by
    // id, never the live playerSeats set. This is deliberate, independent
    // defense-in-depth alongside handleChairClick's own startTransitionActive
    // rejection: even if a seat is mutated unexpectedly during the
    // transition (a late join that somehow slips past that guard, a future
    // code path that doesn't know about this phase), a player absent from
    // this snapshot can never be awaited -- and a snapshotted player who
    // later leaves (see removePlayerData) simply stops being awaited too,
    // since isReadyToDeal only requires still-seated snapshot members to
    // have completed conceal. Cleared alongside startTransitionDoorConcealComplete.
    private final Set<UUID> startTransitionSeatedSnapshot = new HashSet<>();

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
    /**
     * Every eligible player's exact offered insurance cost, computed and
     * stored exactly once per player when the offer is created (see
     * {@link #beginInsurancePhase}) -- never recomputed at display,
     * acceptance, debit, payout, timeout, abort, or teardown time. Distinct
     * from {@link #insuranceStakes}: an entry here exists for every eligible
     * player the instant the offer opens, regardless of whether they ever
     * accept it, while a {@code insuranceStakes} entry exists only once
     * money has actually been debited. For Vault, the offer is the exact
     * fractional half of the wager; for physical (whole-unit) currency, it's
     * the whole-unit cost {@link #insuranceRoundingCoinFlip} resolved to,
     * exactly once, when the wager is odd.
     */
    private final Map<UUID, Double> insuranceOfferedCost = new HashMap<>();
    /**
     * The single 50/50 decision seam an odd physical wager's insurance
     * offer resolves through -- {@code true} rounds the half-unit cost up,
     * {@code false} rounds it down. Defaults to a real coin flip; tests
     * substitute a deterministic supplier via
     * {@link #setInsuranceRoundingCoinFlipForTest} to force either
     * direction without any statistical/flaky assertion. Never consulted
     * for Vault (exact fractional cost) or an even physical wager (already
     * a whole unit) -- see {@link #computeAndStoreInsuranceOffer}.
     */
    private java.util.function.BooleanSupplier insuranceRoundingCoinFlip = () -> java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
    private int insuranceTaskId = -1;
    private int insuranceSecondsRemaining;
    /**
     * {@code dealers.<name>.insurance.enabled}, loaded at construction
     * (default true), then live-patchable by {@link #setInsuranceEnabledLive}
     * -- see that method's doc for exactly when a live change takes effect.
     */
    private boolean insuranceEnabled;
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
    // fresh one). Mandatory -- always runs; a table wanting minimal time
    // pressure just configures a long timeout-seconds instead of disabling
    // it, so a round (and a disconnected/closed player's own hand) always
    // has a real, bounded resolution path and can never stall indefinitely.
    /** {@code dealers.<name>.turn-timer.timeout-seconds}, clamped [5,3600] (default 20). */
    private final int turnTimerTimeoutSeconds;
    private int turnTimerTaskId = -1;
    // Canonical turn-timer deadline state, deliberately separate from
    // turnTimerTaskId's rendering/countdown task -- stopTurnTimerTask only
    // ever cancels the task, never these fields, so a paused deadline (an
    // action being validated/processed) can be resumed with its exact
    // remaining time by resumeTurnTimerAfterFailedAction rather than either
    // being lost or replaced with a fresh full window. null/-1 means no
    // deadline is currently canonical (disabled, or no decision started).
    private UUID turnTimerPlayerId;
    private long turnTimerRoundGeneration;
    private int turnTimerHandToken;
    private long turnTimerHandId;
    private int turnTimerExpectedHandGeneration;
    private int turnTimerSecondsRemaining = -1;

    // ---- Real splitting -----------------------------------------------------
    // Config loaded once at construction (see loadBooleanConfig et al.);
    // playerHands/activeHandIndex above already carry the actual per-player
    // hand queue. See BlackjackSplitEligibility/BlackjackSplitMatching/
    // BlackjackMaxHands for the pure eligibility/matching mechanics this
    // config feeds.
    /**
     * {@code dealers.<name>.splitting.enabled}, default true, then
     * live-patchable by {@link #setSplittingEnabledLive} -- see that
     * method's doc for exactly when a live change takes effect.
     */
    private boolean splittingEnabled;
    /**
     * {@code dealers.<name>.splitting.matching}, default SAME_RANK, then
     * live-patchable by {@link #setSplitMatchingLive} -- see that method's
     * doc for exactly when a live change takes effect.
     */
    private BlackjackSplitMatching splitMatching;
    /** {@code dealers.<name>.splitting.max-hands}, default UNBOUNDED. Applies per player, never table-wide. */
    private final BlackjackMaxHands maxHands;
    /**
     * {@code dealers.<name>.splitting.double-after-split}, default true, then
     * live-patchable by {@link #setDoubleAfterSplitLive} -- see that method's
     * doc for exactly when a live change takes effect.
     */
    private boolean doubleAfterSplit;
    /**
     * {@code dealers.<name>.splitting.aces.resplit}, default true, then
     * live-patchable by {@link #setAcesResplitAllowedLive} -- see that
     * method's doc for exactly when a live change takes effect.
     */
    private boolean acesResplitAllowed;
    /**
     * {@code dealers.<name>.splitting.aces.hit}, default false, then
     * live-patchable by {@link #setAcesHitAllowedLive} -- see that method's
     * doc for exactly when a live change takes effect.
     */
    private boolean acesHitAllowed;
    /**
     * {@code dealers.<name>.splitting.aces.double}, default false, then
     * live-patchable by {@link #setAcesDoubleAllowedLive} -- see that
     * method's doc for exactly when a live change takes effect.
     */
    private boolean acesDoubleAllowed;
    /** {@code dealers.<name>.splitting.split-21-is-blackjack}, default false -- see BlackjackRules#classify's 3-arg overload. */
    private final boolean split21IsBlackjack;
    // Shared/table-owned split animation lifecycle (slide-out / deal / deal
    // / park / reactivate) -- lives in sharedAnimationRun above (viewerId
    // null); this flag/queue tracks which hand-activation is waiting on that
    // animation to finish before its actions become live, so one viewer
    // closing their inventory can never cancel it (see cancelSharedAnimation
    // vs cancelPrivateAnimation).
    private boolean splitAnimationInFlight = false;
    // Which player's split operation splitAnimationInFlight refers to --
    // lets removePlayerData recognize when the *acting* player of an
    // in-flight split is the one leaving (as opposed to some unrelated
    // viewer closing their own inventory, which must never touch this) and
    // proactively tear it down instead of leaving a lingering
    // in-flight/cancelled-task-free state around until the animation's own
    // (now-guarded-off) steps eventually no-op.
    private UUID splitAnimationPlayerId;
    // Animation infrastructure -- private (per-viewer) animation runs:
    // chair guide, wager guide, bet-spot blink, door reveal/conceal, action
    // guide. At most one per viewer; cancelled on that viewer's own
    // close/quit/seat-change -- see cancelPrivateAnimation.
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
    // Snapshots the in-flight game-reset white-tile sweep restores from --
    // see startResetSweep/cancelResetSweepIfActive. Non-null only while a
    // reset sweep (sharedAnimationRun tagged LOBBY) is actually running.
    private Map<Integer, ItemStack> resetSweepLegacySnapshot;
    private Map<UUID, Map<Integer, ItemStack>> resetSweepViewSnapshots;
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
    // Dealer-sequence token, bumped every time a dealer sequence begins
    // (startDealerTurn) -- distinguishes two dealer sequences that could in
    // principle be initiated within the very same roundGeneration, and is
    // also explicitly bumped on reset/cancel/delete (on top of
    // roundGeneration's own bump there) for clarity. Every reveal/draw/
    // shoe-abort/finish callback in the dealer sequence validates this
    // alongside roundGeneration -- see isStaleDealerSequenceCallback.
    private int dealerSequenceToken = 0;
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
        this.turnTimerTimeoutSeconds = loadClampedIntConfig("turn-timer.timeout-seconds", 20, 5, 3600);
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

    // ---- Live-patchable settings ------------------------------------------
    // A single-click boolean toggle or matching-rule cycle in BlackjackMenu
    // must never destroy a live table (committed wagers, an in-progress
    // round, or even just seated players) or close the admin's own settings
    // menu as an unwanted side effect of persisting the new value -- both of
    // which used to happen because every settings change, however small,
    // went through plugin.reloadDealer(), which deletes and recreates this
    // entire controller (see BlackjackInventory#delete/cancelGame). These
    // four settings are exactly the ones BlackjackMenu's own boolean/cycle
    // toggles touch (see BlackjackMenu#handleToggleBoolean/
    // handleToggleSplitMatching) -- the menu now calls these directly on the
    // live controller instead of reloading, so config persists to disk and
    // this instance's own field is updated in the same click, with no
    // deletion/recreation at all.
    //
    // Effective timing, precisely: the new value is read live by every
    // future evaluation from this point on (including any decision still in
    // progress this same round -- e.g. disabling splitting immediately
    // removes Split from a not-yet-decided hand's available actions), but
    // never retroactively changes anything already resolved, and never
    // touches an in-flight timer's own already-captured
    // turnTimerTimeoutSeconds/insuranceTimeoutSeconds snapshot (those are
    // captured once when a fresh deadline starts -- see startTurnTimer/
    // beginInsurancePhase -- so a length-only edit, which still goes through
    // the reload path below, correctly applies at the next controller
    // creation, not retroactively to a deadline already ticking).
    //
    // Every *other* settings edit (numeric timeouts/lengths, max-hands,
    // stand-on-17, deck count, the pregame timer) still goes through
    // plugin.reloadDealer() and therefore still deletes and recreates this
    // controller -- but delete() itself is now economically safe (every
    // committed wager/hand/split/double/insurance stake is refunded or
    // durably queued first, see delete()'s own doc), so even that path can
    // no longer destroy money. Consolidating every one of those onto the
    // same live-patch model this method demonstrates is deliberately left
    // for a later round of work, not attempted here.

    /** Live-patches {@code insurance.enabled} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setInsuranceEnabledLive(boolean enabled) {
        this.insuranceEnabled = enabled;
    }

    /** Live-patches {@code splitting.enabled} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setSplittingEnabledLive(boolean enabled) {
        this.splittingEnabled = enabled;
    }


    /** Live-patches {@code splitting.matching} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setSplitMatchingLive(BlackjackSplitMatching matching) {
        if (matching != null) {
            this.splitMatching = matching;
        }
    }

    /** Live-patches {@code splitting.double-after-split} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setDoubleAfterSplitLive(boolean allowed) {
        this.doubleAfterSplit = allowed;
    }

    /** Live-patches {@code splitting.aces.resplit} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setAcesResplitAllowedLive(boolean allowed) {
        this.acesResplitAllowed = allowed;
    }

    /** Live-patches {@code splitting.aces.hit} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setAcesHitAllowedLive(boolean allowed) {
        this.acesHitAllowed = allowed;
    }

    /** Live-patches {@code splitting.aces.double} for this already-running table -- see the class-level "Live-patchable settings" note for exact timing. */
    public void setAcesDoubleAllowedLive(boolean allowed) {
        this.acesDoubleAllowed = allowed;
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

    // ---- Per-player hand-queue helpers ----------------------------------

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

    // ---- Animation-run bookkeeping ---------------------------------------
    // Shared cancellation-scope machinery used by every animation category
    // (chair/wager guide, bet-spot blink, door reveal/conceal, action
    // guide, dealer U-path inspection, split sequence).

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
        splitAnimationPlayerId = null;
    }

    /** Cancels every currently-tracked animation (private and shared) -- for table-wide teardown only (reset/cancel/delete). */
    private void cancelAllAnimations() {
        for (BlackjackAnimationRun run : privateAnimationRuns.values()) {
            run.cancel();
        }
        privateAnimationRuns.clear();
        viewerAnimationGeneration.clear();
        // Table-wide teardown discards the wager bar's own slide position
        // state too -- a genuine reset/cancel/delete, unlike a private
        // cancellation mid-reversal (see the fields' own doc). Every open
        // view gets repainted canonical right after this (initializeGameMenu
        // or bootstrapView), which also resyncs these maps fresh.
        wagerBarPosition.clear();
        wagerBarTarget.clear();
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
     * seated/gameActive checks are the more precise guard; phase-scoped animations (e.g. the start-transition
     * dealer U-path, or the split sequence) lean on BlackjackAnimationRun.isStale's phase comparison instead,
     * since those really are only ever valid for one fixed phase.
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
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            target.setItem(slot, buildUnseatedBottomBarSlotItem(slot, viewer));
        }
    }

    /**
     * Same per-slot interpretation as {@link #paintUnseatedBottomBar}, but
     * written straight into one viewer's own private view rather than a
     * caller-supplied {@link Inventory} -- used to immediately (non-
     * animated) normalize a leaving viewer back to the canonical unseated
     * bar when animating the conceal isn't appropriate (e.g. the GUI is
     * closing right after).
     */
    private void paintUnseatedBottomBarForViewer(UUID playerId) {
        Player viewer = Bukkit.getPlayer(playerId);
        for (int slot = BlackjackSlotLayout.UNDO_ALL_SLOT; slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT; slot++) {
            renderPrivateItem(playerId, slot, buildUnseatedBottomBarSlotItem(slot, viewer));
        }
    }

    /** Single source of truth for what belongs at one bottom-bar slot for an unseated viewer -- door@45 (UNSEATED_EXIT_SLOT), brown edge glass@46 (UNSEATED_EDGE_GLASS_SLOT), background everywhere else. Reused by the full paint, the door-conceal animation step, and the immediate leave repaint. */
    private ItemStack buildUnseatedBottomBarSlotItem(int slot, Player viewer) {
        if (slot == BlackjackSlotLayout.UNSEATED_EXIT_SLOT) {
            return createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
        }
        if (slot == BlackjackSlotLayout.UNSEATED_EDGE_GLASS_SLOT) {
            return buildBrownEdgeGlassItem();
        }
        return buildBackgroundPaneItem();
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
            BlackjackWagerSelection selected = selectedWager.get(playerId);
            String chipName = plugin.getChipDisplayName(currencyMode, currencyName, value);
            return BlackjackWagerSelection.isSelected(selected, value)
                ? applySelectedWagerLore(createEnchantedItem(plugin.getCurrency(internalName), chipName, (int) (double) value), viewer)
                : createCustomItem(plugin.getCurrency(internalName), chipName, (int) (double) value);
        }
        if (slot == BlackjackSlotLayout.ALL_IN_SLOT) {
            boolean allInSelected = BlackjackWagerSelection.isAllInSelected(selectedWager.get(playerId));
            return allInSelected
                ? applySelectedWagerLore(createEnchantedItem(Material.SNIFFER_EGG, localize(viewer, "blackjack.all-in"), 1), viewer)
                : createCustomItem(Material.SNIFFER_EGG, localize(viewer, "blackjack.all-in"), 1);
        }
        if (slot == BlackjackSlotLayout.PREGAME_EXIT_SLOT) {
            return createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
        }
        return buildBackgroundPaneItem();
    }

    /**
     * Appends the localized "Currently Selected" subtitle to {@code item}'s
     * lore, preserving whatever lore it already carries rather than
     * replacing it, and never appending a duplicate if it's already there.
     * Applied only to the one wager control (a fixed chip or All In) that
     * currently carries the canonical selected glint.
     */
    private ItemStack applySelectedWagerLore(ItemStack item, Player viewer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> existing = meta.getLore();
            List<String> lore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
            String selectedLine = localize(viewer, "blackjack.currently-selected");
            if (!lore.contains(selectedLine)) {
                lore.add(selectedLine);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---- Chair guidance (private, per unseated viewer) ------------------

    /** Schedules the first chair-guidance cycle to begin CHAIR_GUIDANCE_START_DELAY_TICKS from now, per the table redesign plan. */
    private void scheduleChairGuidanceStart(UUID playerId) {
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> startChairGuidance(playerId, myGeneration), BlackjackTiming.CHAIR_GUIDANCE_START_DELAY_TICKS);
    }

    private void startChairGuidance(UUID playerId, int myGeneration) {
        if (isStaleViewerAnimation(playerId, myGeneration) || playerSeats.containsKey(playerId) || gameActive
            || !views.containsKey(playerId) || chairGuidanceCompleted.contains(playerId)) {
            return;
        }
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runChairGuidancePhase(playerId, myGeneration, true);
    }

    /**
     * Renders one whole-set glow/plain phase of BlackjackChairGuidancePlan, then reschedules the opposite phase
     * CHAIR_GUIDANCE_STEP_TICKS later -- re-deriving which seats are filled fresh at every phase (never baking a
     * stale seat list into a long-running plan), so a seat filling mid-loop drops out of the very next phase.
     * Every currently-empty seat glows (or goes plain) together in the same tick -- never one seat at a time.
     */
    private void runChairGuidancePhase(UUID playerId, int myGeneration, boolean glowPhase) {
        if (isStaleViewerAnimation(playerId, myGeneration) || playerSeats.containsKey(playerId) || gameActive
            || !views.containsKey(playerId) || chairGuidanceCompleted.contains(playerId)) {
            return;
        }
        Set<Integer> filledSeats = new HashSet<>(playerSeats.values());
        List<Integer> slots = BlackjackChairGuidancePlan.applicableSlots(filledSeats);
        if (slots.isEmpty()) {
            return; // every seat is filled -- nothing left to guide
        }
        for (int slot : slots) {
            applyChairGuidanceStep(playerId, slot, glowPhase);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runChairGuidancePhase(playerId, myGeneration, !glowPhase), BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS);
    }

    private void applyChairGuidanceStep(UUID playerId, int slot, boolean glowing) {
        Player viewer = Bukkit.getPlayer(playerId);
        ItemStack item = glowing
            ? createGlowingCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.chair-guidance-hint"), 1)
            : createCustomItem(Material.OAK_STAIRS, localize(viewer, "blackjack.click-sit"), 1);
        renderPrivateItem(playerId, slot, item);
    }

    /**
     * Repaints every still-empty seat back to its canonical plain state,
     * private to {@code playerId} -- called the moment chair guidance is
     * cancelled by that viewer sitting, so a glow frame caught mid-flash by
     * the sit doesn't freeze there for the rest of the round (nothing else
     * repaints the seat row for an already-seated viewer). {@code playerId}
     * is seated by the time this runs, so {@link #buildEmptySeatChairItem}
     * correctly shows the "leave your own chair" redirect rather than
     * "click to sit" for every other empty seat.
     */
    private void repaintEmptySeatsPlainForViewer(UUID playerId) {
        Player viewer = Bukkit.getPlayer(playerId);
        Set<Integer> filledSeats = new HashSet<>(playerSeats.values());
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            if (!filledSeats.contains(seatSlot)) {
                renderPrivateItem(playerId, seatSlot, buildEmptySeatChairItem(playerId, viewer));
            }
        }
    }

    // ---- Wager bar solid slide (private, per viewer) + wager guidance handoff ----
    // The bottom bar (45-53) is one solid nine-item strip -- see
    // BlackjackWagerRevealPlan -- that slides between CLOSED (0, door+glass)
    // and OPEN (8, the full seated bar) one frame per tick. Every request
    // (sit, unsit, start-transition door-conceal) funnels through
    // requestWagerBarPosition, which always continues from wherever the
    // strip visually is right now (wagerBarPosition), never jumping to an
    // endpoint first -- this is what makes a rapid sit/unsit reversal smooth
    // instead of restarting from scratch.

    /** This viewer's live slide position, defaulting to their canonical resting frame (OPEN if seated, CLOSED otherwise) if nothing has been tracked yet -- e.g. a freshly bootstrapped view. */
    private int currentWagerBarPosition(UUID playerId) {
        return wagerBarPosition.getOrDefault(playerId, playerSeats.containsKey(playerId) ? BlackjackWagerRevealPlan.OPEN : BlackjackWagerRevealPlan.CLOSED);
    }

    /** Sets this viewer's slide position/target to their canonical resting frame -- called on bootstrap/full repaint, never mid-slide. */
    private void syncWagerBarPositionToCanonicalResting(UUID playerId) {
        int resting = playerSeats.containsKey(playerId) ? BlackjackWagerRevealPlan.OPEN : BlackjackWagerRevealPlan.CLOSED;
        wagerBarPosition.put(playerId, resting);
        wagerBarTarget.put(playerId, resting);
    }

    /**
     * Requests the wager bar move toward {@code targetPosition} ({@link BlackjackWagerRevealPlan#CLOSED}
     * or {@link BlackjackWagerRevealPlan#OPEN}) for {@code playerId}, continuing from wherever the strip
     * currently is -- reversing smoothly, never jumping to an endpoint first, and never launching a second
     * competing chain (repeating the same target while already there or already heading there is a no-op).
     * {@code onReachTarget} runs (synchronously, if already there; otherwise once the final frame actually
     * renders) only if the request that scheduled it is still current by then.
     *
     * @param myRoundGeneration the round generation this request belongs to -- every scheduled frame step
     *        also verifies {@code roundGeneration} is still this value, on top of the per-viewer animation
     *        generation, so a start-transition door-conceal can never survive into a later round
     */
    private void requestWagerBarPosition(UUID playerId, int targetPosition, long myRoundGeneration, Runnable onReachTarget) {
        int currentPosition = currentWagerBarPosition(playerId);
        Integer existingTarget = wagerBarTarget.get(playerId);
        if (existingTarget != null && existingTarget == targetPosition
            && (currentPosition == targetPosition || privateAnimationRuns.containsKey(playerId))) {
            return; // idempotent: already there, or already sliding toward the same target
        }
        wagerBarTarget.put(playerId, targetPosition);
        if (currentPosition == targetPosition) {
            wagerBarPosition.put(playerId, targetPosition);
            renderWagerBarFrame(playerId, targetPosition);
            if (onReachTarget != null) {
                onReachTarget.run();
            }
            return;
        }
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, myRoundGeneration, myGeneration, capturePhase()));
        scheduleWagerBarFrameStep(playerId, myGeneration, myRoundGeneration, targetPosition, onReachTarget);
    }

    private void scheduleWagerBarFrameStep(UUID playerId, int myGeneration, long myRoundGeneration, int targetPosition, Runnable onReachTarget) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration || isStaleViewerAnimation(playerId, myGeneration)) {
                return; // a stale callback must never repaint the row or trigger guidance/completion
            }
            Integer liveTarget = wagerBarTarget.get(playerId);
            if (liveTarget == null || liveTarget != targetPosition) {
                return; // superseded by a newer request (defensive -- that request would already have bumped the generation above)
            }
            int current = currentWagerBarPosition(playerId);
            int next = current + Integer.signum(targetPosition - current);
            wagerBarPosition.put(playerId, next);
            renderWagerBarFrame(playerId, next);
            if (next == targetPosition) {
                privateAnimationRuns.remove(playerId);
                if (onReachTarget != null) {
                    onReachTarget.run();
                }
            } else {
                scheduleWagerBarFrameStep(playerId, myGeneration, myRoundGeneration, targetPosition, onReachTarget);
            }
        }, BlackjackTiming.WAGER_REVEAL_STEP_TICKS);
    }

    /** Atomically repaints all nine bottom-bar slots (45-53) from {@link BlackjackWagerRevealPlan#frame}'s snapshot at {@code position} -- one complete frame, never a single-slot mutation. */
    private void renderWagerBarFrame(UUID playerId, int position) {
        Player viewer = Bukkit.getPlayer(playerId);
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(position);
        for (int i = 0; i < frame.length; i++) {
            renderPrivateItem(playerId, BlackjackWagerRevealPlan.slotForFrameIndex(i), buildWagerBarControlItem(frame[i], playerId, viewer));
        }
    }

    /** Resolves one {@link BlackjackWagerRevealPlan.Control} to the real item -- chip/control identities go through {@link #buildSeatedBottomBarSlotItem} keyed by their own canonical slot, so selected-wager glow/lore always attaches to the correct logical chip regardless of which physical slot it's currently sliding through. */
    private ItemStack buildWagerBarControlItem(BlackjackWagerRevealPlan.Control control, UUID playerId, Player viewer) {
        switch (control) {
            case DOOR:
                return createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
            case EDGE_GLASS:
                return buildBrownEdgeGlassItem();
            case UNDO_ALL:
                return buildSeatedBottomBarSlotItem(BlackjackSlotLayout.UNDO_ALL_SLOT, playerId, viewer);
            case UNDO_LAST:
                return buildSeatedBottomBarSlotItem(BlackjackSlotLayout.UNDO_LAST_SLOT, playerId, viewer);
            case CHIP_1:
                return buildSeatedBottomBarSlotItem(ChipSlots.FIRST_SLOT, playerId, viewer);
            case CHIP_2:
                return buildSeatedBottomBarSlotItem(ChipSlots.FIRST_SLOT + 1, playerId, viewer);
            case CHIP_3:
                return buildSeatedBottomBarSlotItem(ChipSlots.FIRST_SLOT + 2, playerId, viewer);
            case CHIP_4:
                return buildSeatedBottomBarSlotItem(ChipSlots.FIRST_SLOT + 3, playerId, viewer);
            case CHIP_5:
                return buildSeatedBottomBarSlotItem(ChipSlots.FIRST_SLOT + 4, playerId, viewer);
            case ALL_IN:
                return buildSeatedBottomBarSlotItem(BlackjackSlotLayout.ALL_IN_SLOT, playerId, viewer);
            case BACKGROUND:
            default:
                return buildBackgroundPaneItem();
        }
    }

    /** Slides the bottom bar from door+glass to the full seated wager bar for the viewer who just sat, then hands off to wager guidance once fully open. */
    private void startWagerBarReveal(UUID playerId) {
        requestWagerBarPosition(playerId, BlackjackWagerRevealPlan.OPEN, roundGeneration, () -> startWagerGuidance(playerId));
    }

    /**
     * Slides the bottom bar from the full seated wager bar back to door+glass
     * for a player who voluntarily left their chair while their GUI stays
     * open -- the exact reverse of {@link #startWagerBarReveal}. Chains into
     * {@link #scheduleChairGuidanceStart} only once fully closed, mirroring
     * how {@code startWagerBarReveal} only hands off to wager guidance once
     * fully open.
     */
    private void startWagerBarConceal(UUID playerId) {
        requestWagerBarPosition(playerId, BlackjackWagerRevealPlan.CLOSED, roundGeneration, () -> scheduleChairGuidanceStart(playerId));
    }

    /**
     * Cycles glow left-to-right over the 5 chip slots until the viewer
     * selects a denomination (or All In) -- a help prompt, not something
     * that restarts after every bet. Gated on wagerGuidanceCompleted, not
     * merely "no selection currently exists": a player who selected, then
     * left and reseated (clearing their selection but not the completion
     * flag -- see removePlayerData/clearPlayerBets), must never see this
     * flash again in the same round.
     */
    private void startWagerGuidance(UUID playerId) {
        if (!playerSeats.containsKey(playerId) || gameActive || wagerGuidanceCompleted.contains(playerId)) {
            return;
        }
        int myGeneration = bumpAndGetViewerAnimationGeneration(playerId);
        privateAnimationRuns.put(playerId, new BlackjackAnimationRun(playerId, roundGeneration, myGeneration, capturePhase()));
        runWagerGuidancePhase(playerId, myGeneration, true);
    }

    /**
     * Renders one whole-set glow/plain phase of BlackjackWagerGuidancePlan, then reschedules the opposite phase
     * WAGER_GUIDANCE_STEP_TICKS later. Every applicable chip slot glows (or goes plain) together in the same tick
     * -- never one chip at a time.
     */
    private void runWagerGuidancePhase(UUID playerId, int myGeneration, boolean glowPhase) {
        if (isStaleViewerAnimation(playerId, myGeneration) || !playerSeats.containsKey(playerId) || gameActive
            || wagerGuidanceCompleted.contains(playerId)) {
            return;
        }
        if (selectedWager.containsKey(playerId)) {
            return; // a selection is pending -- the bet-spot blink owns the UI now, not wager guidance
        }
        List<Integer> slots = BlackjackWagerGuidancePlan.applicableSlots();
        for (int slot : slots) {
            applyWagerGuidanceStep(playerId, slot, glowPhase);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runWagerGuidancePhase(playerId, myGeneration, !glowPhase), BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS);
    }

    private void applyWagerGuidanceStep(UUID playerId, int slot, boolean glowing) {
        Double value = chipValues.get(slot);
        if (value == null) {
            return;
        }
        Player viewer = Bukkit.getPlayer(playerId);
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
        renderPrivateItem(playerId, slot, item);
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
        if (!selectedWager.containsKey(playerId)) {
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
        BlackjackWagerSelection selection = selectedWager.get(playerId);
        // Purely cosmetic display amount -- for All In this re-resolves the
        // live balance every render, exactly like a bet-spot click itself
        // would, so the blink text never shows a stale snapshot.
        double selected = selection == null ? 0.0 : (viewer != null ? resolveSelectionAmount(viewer, selection) : (selection.isFixed() ? selection.getFixedAmount() : 0.0));
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
        dealerSequenceToken++;
        long myRoundGeneration = roundGeneration;
        startTransitionActive = true;
        startTransitionDoorConcealComplete.clear();

        List<UUID> seatedPlayers = new ArrayList<>(playerSeats.keySet());
        startTransitionSeatedSnapshot.clear();
        startTransitionSeatedSnapshot.addAll(seatedPlayers);
        for (UUID playerId : seatedPlayers) {
            // Stops wager guidance / bet-spot blink for anyone still
            // mid-selection -- only players with a committed wager get
            // cards/turns, per the table redesign plan. The selection tool
            // itself (selectedWager) is deliberately left untouched: it's a
            // persistent per-seat selection that must survive into next
            // round's bet-spot clicks, not a one-shot pending amount.
            cancelPrivateAnimation(playerId);
        }

        for (UUID playerId : seatedPlayers) {
            startDoorConceal(playerId, myRoundGeneration);
        }
        startDealerInspection(myRoundGeneration);

        scheduleDealReadinessCheck(myRoundGeneration);
    }

    // ---- Per-viewer door-conceal ----------------------------------------

    /**
     * Slides {@code playerId}'s bar to CLOSED for the start-transition, using the same solid-slide engine as
     * an ordinary unsit -- continuing from wherever their reveal actually left off (never jumping to OPEN
     * first) if the countdown hit zero mid-reveal. Door-conceal completion for the readiness gate
     * ({@link #startTransitionDoorConcealComplete}) is recorded only once this viewer's position genuinely
     * reaches {@link BlackjackWagerRevealPlan#CLOSED}, not merely once requested.
     */
    private void startDoorConceal(UUID playerId, long myRoundGeneration) {
        requestWagerBarPosition(playerId, BlackjackWagerRevealPlan.CLOSED, myRoundGeneration,
            () -> startTransitionDoorConcealComplete.add(playerId));
    }

    // ---- Shared dealer U-path inspection ---------------------------------

    /**
     * Runs the dealer's start-transition slide as a shared/table-owned
     * animation (viewer = null) -- per phase 2's cancellation-scope design,
     * this must survive any single viewer closing their inventory. A smooth,
     * uniform-speed slide straight down the board's right edge with no
     * stops. The final (bottom-row) step is gated behind every seated
     * viewer's door-conceal finishing (a fixed worst-case delay, not an
     * event wait, since both animations want that same slot) -- but only
     * ever by the <em>minimum</em> amount actually needed (see
     * {@link BlackjackDealerInspectionPlan#withBottomRowCoordination}).
     */
    private void startDealerInspection(long myRoundGeneration) {
        List<BlackjackAnimationStep> path = BlackjackDealerInspectionPlan.build(BlackjackTiming.DEALER_INSPECTION_STEP_TICKS);
        long bottomRowGateTicks = BlackjackWagerRevealPlan.concealDurationTicks(BlackjackTiming.WAGER_REVEAL_STEP_TICKS);
        // Applies only the minimum shift actually needed -- if committed-player
        // pauses already push the dealer's natural bottom-row arrival past the
        // conceal's own worst-case completion, no shift is added at all (see
        // withBottomRowCoordination's own doc for why unconditionally adding
        // the full gate here would be an unintended extra delay).
        path = BlackjackDealerInspectionPlan.withBottomRowCoordination(path, bottomRowGateTicks);

        BlackjackAnimationRun run = new BlackjackAnimationRun(null, myRoundGeneration, 0, BlackjackFrame.Phase.START_TRANSITION);
        sharedAnimationRun = run;

        for (BlackjackAnimationStep step : path) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                    return;
                }
                applyDealerInspectionStep(step);
            }, step.getDelayTicks());
        }

        // The real, coordinated completion time -- derived from the actual
        // final scheduled step (including any committed-player pauses and
        // any bottom-row coordination shift), never a separately-added
        // constant that could drift from what was actually scheduled above.
        long lastStepDelay = BlackjackDealerInspectionPlan.totalDurationTicks(path);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                return;
            }
            // Natural completion -- a valid end for a shared run per phase 2's design (not a viewer-close).
            dealerHeadSlot = BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT; // safety net in case the last MOVE step wasn't exactly 53
            if (dealerDeckTokenSlot != BlackjackSlotLayout.DECK_HOME_SLOT) {
                if (dealerDeckTokenSlot != -1) {
                    renderBackgroundToAllViews(dealerDeckTokenSlot);
                }
                dealerDeckTokenSlot = BlackjackSlotLayout.DECK_HOME_SLOT;
                renderHiddenCardToAllViews(dealerDeckTokenSlot);
            }
            cancelSharedAnimation();
        }, lastStepDelay + BlackjackTiming.DEALER_INSPECTION_STEP_TICKS);
    }

    /**
     * Moves the canonical dealerHeadSlot to {@code step}'s slot and
     * re-renders the dealer head there for every view. The deck token
     * trails one row behind, unrendered until the dealer's first genuine
     * move, then following into wherever the dealer just vacated -- so it
     * naturally comes to rest one slot above the dealer's final in-play
     * position once the slide completes.
     */
    private void applyDealerInspectionStep(BlackjackAnimationStep step) {
        int previousSlot = dealerHeadSlot;
        int newSlot = step.getSlot();
        dealerHeadSlot = newSlot;

        if (previousSlot != newSlot) {
            if (dealerDeckTokenSlot != -1) {
                renderBackgroundToAllViews(dealerDeckTokenSlot);
            }
            dealerDeckTokenSlot = previousSlot;
            renderHiddenCardToAllViews(dealerDeckTokenSlot);
        }
        renderLocalizedToAllViews(newSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer");
    }

    // ---- Round-end cards-return-to-deck sweep -----------------------------

    /** Every currently-rendered card slot (bounded to what's actually visible, matching isRenderableCardSlot/the deal itself) -- slot -&gt; whether it's a dealer card. Read before resetGame() clears any hand data. */
    private Map<Integer, Boolean> collectVisibleCardSlots() {
        Map<Integer, Boolean> slots = new HashMap<>();
        for (UUID playerId : playerSeats.keySet()) {
            Integer seatSlot = playerSeats.get(playerId);
            if (seatSlot == null) {
                continue;
            }
            List<Card> cards = activeHandCards(playerId);
            for (int i = 0; i < cards.size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                slots.put(BlackjackSlotLayout.playerCardSlot(seatSlot, i), false);
            }
        }
        for (int i = 0; i < dealerHand.size() && i < BlackjackSlotLayout.DEALER_CARD_CAPACITY; i++) {
            slots.put(BlackjackSlotLayout.dealerCardSlot(i), true);
        }
        return slots;
    }

    /**
     * Plays every visible card's own deal-in flight (see
     * {@link #scheduleCardFlight}) in reverse, all starting at the same
     * instant -- the exact same path each card arrived by, walked backward,
     * which is why this needs no separate geometry: player cards sweep
     * right along their row into the deck's column before dropping down
     * into it, the dealer's own cards rise into the deck's row before
     * sliding right into it. Every card moves at the identical uniform hop
     * rate, so cards sharing a lane (same row, or the shared descent
     * column) never catch up to and collide with one another -- whichever
     * started closer to the deck simply finishes first, freeing the lane
     * before a farther card arrives.
     *
     * <p>{@code onComplete} (in production, the white-tile sweep) fires
     * once the slowest card's own return lands -- immediately, synchronously,
     * if there was nothing to animate (an early abort, or a plain reset with
     * no round ever dealt).
     */
    private void animateCardsReturnToDeck(Map<Integer, Boolean> visibleCardSlots, int deckSlot, long myRoundGeneration, Runnable onComplete) {
        if (visibleCardSlots.isEmpty()) {
            onComplete.run();
            return;
        }

        // The caller (resetGame()/cancelGame()) has already repainted the
        // whole board correctly for the round that's about to begin --
        // including, for the dealer's own returning cards, the bottom bar
        // (45-53) that a still-seated player's row 5 now shows as their
        // pregame wager bar (chips/Undo/All In/door) rather than the
        // dealer's card row it was during active play. Snapshotted here so
        // every hop restores each departed slot to that exact
        // already-correct per-viewer content instead of blindly clearing to
        // plain background, which would otherwise blow the wager bar away
        // wherever a dealer card's return path happens to cross it.
        Map<Integer, ItemStack> legacySnapshot = snapshotSlots(inventory);
        Map<UUID, Map<Integer, ItemStack>> viewSnapshots = new HashMap<>();
        for (BlackjackView view : views.values()) {
            viewSnapshots.put(view.getPlayerId(), snapshotSlots(view.getInventory()));
        }

        long flipDelay = BlackjackTiming.CARD_FLIP_DELAY_TICKS;
        long hopTicks = BlackjackTiming.CARD_FLIGHT_HOP_TICKS;
        long longestReturnTicks = 0L;

        for (Map.Entry<Integer, Boolean> entry : visibleCardSlots.entrySet()) {
            int cardSlot = entry.getKey();
            boolean dealerCard = entry.getValue();

            List<Integer> returnPath = new ArrayList<>(BlackjackCardFlightPlan.path(deckSlot, cardSlot, dealerCard));
            Collections.reverse(returnPath);

            // resetGame()'s own instant repaint (inventory.clear() +
            // initializeGameMenu()) already wiped this slot back to plain
            // felt by the time this method runs -- redraw the card here,
            // immediately/synchronously, or there'd be a visible gap where
            // the hand has already vanished before its own return animation
            // has even begun.
            renderHiddenCardToAllViews(cardSlot); // flip face-down before sweeping away, mirroring the deal-in's own flip

            for (int i = 1; i < returnPath.size(); i++) {
                int previousSlot = returnPath.get(i - 1);
                int hopSlot = returnPath.get(i);
                boolean isLastHop = i == returnPath.size() - 1;
                long hopDelay = flipDelay + i * hopTicks;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (roundGeneration != myRoundGeneration) {
                        return;
                    }
                    restoreSweptSlot(previousSlot, legacySnapshot, viewSnapshots);
                    if (!isLastHop) {
                        renderHiddenCardToAllViews(hopSlot);
                    }
                    // The last hop lands exactly on the deck's own slot --
                    // left as whatever the snapshot already says belongs
                    // there (the deck token itself is long gone by
                    // round-end, and the white sweep right behind this
                    // repaints everything anyway).
                }, hopDelay);
            }

            longestReturnTicks = Math.max(longestReturnTicks, flipDelay + (returnPath.size() - 1) * hopTicks);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration) {
                return;
            }
            onComplete.run();
        }, longestReturnTicks + 1);
    }

    // ---- Game-reset white-tile sweep --------------------------------------

    /** Blank white tile used to cover a slot mid-sweep -- see {@link #startResetSweep}. */
    private ItemStack buildResetSweepWhiteTileItem() {
        ItemStack item = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Plays a diagonal white-tile wipe over the whole board, immediately
     * after the caller (resetGame/cancelGame) has already instantly
     * repainted it to its correct post-reset state -- the sweep never
     * invents content, it only briefly covers and then re-reveals exactly
     * what's already sitting in the legacy inventory and every open view at
     * the moment it starts, snapshotted here per-view so each viewer's own
     * personalized items (their own chair/bet-spot text) come back
     * correctly rather than being collapsed to one shared item. Runs as a
     * shared/table-owned {@link BlackjackAnimationRun} (viewer = null) under
     * the fresh post-reset {@code roundGeneration}, so it's automatically
     * invalidated the instant another reset/round/plugin-shutdown event
     * calls {@link #cancelAllAnimations()} again -- never a bare timer.
     *
     * <p>Swappable: a different reset animation only needs a replacement for
     * {@link BlackjackResetSweepPlan} with the same {@code build(...)} shape
     * and a one-line change to the call below.
     */
    private void startResetSweep(long myRoundGeneration) {
        resetSweepLegacySnapshot = snapshotSlots(inventory);
        resetSweepViewSnapshots = new HashMap<>();
        for (BlackjackView view : views.values()) {
            resetSweepViewSnapshots.put(view.getPlayerId(), snapshotSlots(view.getInventory()));
        }

        List<BlackjackAnimationStep> steps = BlackjackResetSweepPlan.build(
            BlackjackTiming.RESET_SWEEP_STEP_TICKS, BlackjackTiming.RESET_SWEEP_HOLD_DIAGONALS
        );

        BlackjackAnimationRun run = new BlackjackAnimationRun(null, myRoundGeneration, 0, BlackjackFrame.Phase.LOBBY);
        sharedAnimationRun = run;

        for (BlackjackAnimationStep step : steps) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                    return;
                }
                if (step.getKind() == BlackjackAnimationStep.Kind.CONCEAL) {
                    renderToAllViews(step.getSlot(), buildResetSweepWhiteTileItem());
                } else {
                    restoreSweptSlot(step.getSlot(), resetSweepLegacySnapshot, resetSweepViewSnapshots);
                }
            }, step.getDelayTicks());
        }

        long lastStepDelay = BlackjackResetSweepPlan.totalDurationTicks(steps);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration || run.isCancelled()) {
                return;
            }
            cancelSharedAnimation();
            resetSweepLegacySnapshot = null;
            resetSweepViewSnapshots = null;
        }, lastStepDelay + 1);
    }

    /**
     * Cancels an in-flight reset sweep (if one is running) and immediately
     * restores every slot it might have covered back to its snapshotted
     * correct content, synchronously -- unlike a plain {@link
     * #cancelSharedAnimation()}, which only stops <em>future</em> steps and
     * would leave a slot the sweep happened to be covering at this exact
     * moment stuck white (and, worse, unclickable as its real functional
     * item -- e.g. an empty seat's chair) until whatever stale step was
     * ever going to reveal it. Called the instant any real seat mutation
     * (sit or leave) is about to happen, since that's exactly the kind of
     * event a purely cosmetic sweep must always yield to. A no-op when no
     * reset sweep is currently running, or when the current shared
     * animation is something else entirely (dealer inspection, split
     * sequence) -- scoped by the run's own LOBBY phase tag, which only the
     * reset sweep ever uses.
     */
    private void cancelResetSweepIfActive() {
        if (sharedAnimationRun == null || sharedAnimationRun.getExpectedPhase() != BlackjackFrame.Phase.LOBBY) {
            return;
        }
        cancelSharedAnimation();
        if (resetSweepLegacySnapshot != null) {
            for (int slot = 0; slot < BlackjackSlotLayout.TOTAL_SLOTS; slot++) {
                restoreSweptSlot(slot, resetSweepLegacySnapshot, resetSweepViewSnapshots);
            }
            resetSweepLegacySnapshot = null;
            resetSweepViewSnapshots = null;
        }
    }

    /** Captures a defensive-copy snapshot of every slot in {@code target}, for {@link #startResetSweep} to restore later. */
    private Map<Integer, ItemStack> snapshotSlots(Inventory target) {
        Map<Integer, ItemStack> snapshot = new HashMap<>();
        for (int slot = 0; slot < BlackjackSlotLayout.TOTAL_SLOTS; slot++) {
            ItemStack item = target.getItem(slot);
            snapshot.put(slot, item == null ? null : item.clone());
        }
        return snapshot;
    }

    /** Restores {@code slot} in the legacy inventory and every open view back to its own snapshotted (already-correct) item. */
    private void restoreSweptSlot(int slot, Map<Integer, ItemStack> legacySnapshot, Map<UUID, Map<Integer, ItemStack>> viewSnapshots) {
        inventory.setItem(slot, cloneOrNull(legacySnapshot.get(slot)));
        for (BlackjackView view : views.values()) {
            Map<Integer, ItemStack> snapshot = viewSnapshots.get(view.getPlayerId());
            ItemStack restore = snapshot != null ? snapshot.get(slot) : legacySnapshot.get(slot);
            view.getInventory().setItem(slot, cloneOrNull(restore));
        }
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    // ---- Readiness gate before dealing -----------------------------------

    /**
     * Polls (runTaskLater retry loop bounded by roundGeneration, not a
     * busy-loop) until every start-transition condition is satisfied, then
     * flips out of START_TRANSITION and calls the existing, unchanged
     * activateGame() entry point. A stale roundGeneration (table
     * reset/cancelled, or a brand-new round already begun) simply stops
     * the polling loop instead of ever opening the gate.
     *
     * <p>Bounded by {@link BlackjackTiming#START_TRANSITION_READINESS_MAX_POLLS}
     * -- this is deliberate defense-in-depth against ever polling forever,
     * on top of (not instead of) {@link #isReadyToDeal}'s own snapshot-based
     * guard against being permanently stalled by an unexpected seat
     * mutation. Under entirely normal conditions this bound is never even
     * approached (door-conceal/dealer-inspection are fixed, short
     * animations); if it's ever actually reached, that means some
     * start-transition invariant this method doesn't know how to satisfy
     * has been violated, so rather than deal cards against inconsistent
     * player/animation state, the round is safely aborted and every seated
     * player is refunded (see {@link #abortRoundAndRefund}), with a
     * high-severity log entry for admin diagnosis.
     */
    private void scheduleDealReadinessCheck(long myRoundGeneration) {
        scheduleDealReadinessCheck(myRoundGeneration, 0);
    }

    private void scheduleDealReadinessCheck(long myRoundGeneration, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myRoundGeneration) {
                return;
            }
            if (isReadyToDeal()) {
                startTransitionActive = false;
                activateGame();
                return;
            }
            int nextAttempt = attempt + 1;
            if (nextAttempt >= BlackjackTiming.START_TRANSITION_READINESS_MAX_POLLS) {
                plugin.getLogger().severe("[NCCasino] Blackjack start-transition readiness gate for dealer '"
                    + internalName + "' never resolved after " + nextAttempt + " polls (round generation "
                    + myRoundGeneration + ") -- aborting the round and refunding every seated player rather than "
                    + "polling forever or dealing against inconsistent player/animation state. This should never "
                    + "happen under normal operation and likely indicates a stuck animation or an unexpected seat "
                    + "mutation during the start transition.");
                abortRoundAndRefund("blackjack.start-transition-failed-refunded");
                return;
            }
            scheduleDealReadinessCheck(myRoundGeneration, nextAttempt);
        }, BlackjackTiming.START_TRANSITION_READINESS_POLL_TICKS);
    }

    /**
     * True once: the dealer has actually arrived at its in-play head slot,
     * the pregame countdown clock is gone for good, and every player who
     * was actually seated at the moment the start transition began (see
     * {@link #startTransitionSeatedSnapshot}) and is <em>still</em> seated
     * now has finished their door-conceal (which also implies none of them
     * are still in a door-revealed/wager-guide/bet-spot-blink lobby state,
     * since beginStartTransition force-cancelled all of those before
     * conceal ever started).
     *
     * <p>Deliberately awaits {@code startTransitionSeatedSnapshot}, never
     * the live {@code playerSeats}: a player seated after the snapshot was
     * taken was never scheduled a door-conceal sequence at all (see
     * beginStartTransition/startDoorConceal) and must never be able to
     * block this gate -- {@code handleChairClick} already rejects seating
     * during this phase, but this is independent, redundant protection
     * against that exact deadlock even if some other path mutates
     * {@code playerSeats} unexpectedly. A snapshotted player who leaves
     * mid-transition (removePlayerData removes them from playerSeats)
     * correctly stops being awaited, rather than blocking the gate forever.
     */
    private boolean isReadyToDeal() {
        if (dealerHeadSlot != BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT) {
            return false;
        }
        if (countdownTaskId != -1) {
            return false;
        }
        for (UUID playerId : startTransitionSeatedSnapshot) {
            if (playerSeats.containsKey(playerId) && !startTransitionDoorConcealComplete.contains(playerId)) {
                return false;
            }
        }
        return true;
    }

    // ---- Wager selection vs. commitment ---------------------------------

    /**
     * Sets {@code playerId}'s persistent wager-selection tool -- moves no
     * funds, pushes nothing to the ledger. Shared by chip clicks (FIXED)
     * and All In (ALL_IN): the plan treats them identically at selection
     * time (both only select, never commit), differing only in how a later
     * bet-spot click resolves the amount to commit (see
     * {@link #resolveSelectionAmount}). Also marks wager guidance complete
     * for this player for the round -- a help prompt that has done its job
     * the moment a selection exists, never restarted by anything short of
     * the next genuine round boundary (see wagerGuidanceCompleted's own doc).
     *
     * @param displayAmount the amount shown in the (optional) verbose
     *        selection-confirmation message only -- for All In this is the
     *        player's live balance at the moment of selection, purely
     *        cosmetic and never itself stored or reused at commit time.
     */
    private void selectWager(Player player, UUID playerId, BlackjackWagerSelection selection, double displayAmount) {
        if (SoundHelper.getSoundSafely("item.flintandsteel.use", player) != null) {
            player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD: {
                break;
            }
            case VERBOSE: {
                player.sendMessage(text(player, "blackjack.wager-selected", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, displayAmount)));
                break;
            }
            case NONE: {
                break;
            }
        }
        selectedWager.put(playerId, selection);
        wagerGuidanceCompleted.add(playerId);
        cancelPrivateAnimation(playerId); // stop wager guidance -- the blink takes over
        refreshWagerControlsForPlayer(playerId);
        startBetSpotBlink(playerId);
    }

    /**
     * Resolves the live amount {@code selection} would commit right now --
     * a FIXED selection's captured amount is reused unchanged, but an
     * ALL_IN selection is re-derived from {@code player}'s current
     * available balance every single time, never a snapshot taken when All
     * In was originally selected (see BlackjackWagerSelection's own doc).
     */
    private double resolveSelectionAmount(Player player, BlackjackWagerSelection selection) {
        if (selection.isAllIn()) {
            return getPlayerTotalBalance(player);
        }
        return selection.getFixedAmount();
    }

    /**
     * Commits {@code amount} for {@code playerId}: debits their balance, then applies the ledger-side effects (see
     * {@link #commitWagerFundsAlreadyRemoved}). Used by the chip-selection commit path, where nothing has removed
     * any funds yet. NOT used for the cursor-drag-onto-bet-spot path -- there, {@code player.setItemOnCursor(null)}
     * already destroys the dragged physical stack, so calling this too would debit the same amount a second time
     * (previously a real bug: the cursor stack was deleted AND removeWagerFromInventory ran again, over-charging
     * the player). See handleBetClick's cursor-drag branch, which calls commitWagerFundsAlreadyRemoved instead.
     */
    /**
     * Distinguishes why a wager commit didn't go through -- callers need
     * this because the correct recovery differs: a transaction failure
     * means the amount itself was fine and the player may simply retry
     * (their selection is restored), while an insurance-incompatible
     * rejection means that exact amount will never work and retrying it
     * is pointless (the selection is cleared instead).
     */
    private enum WagerCommitResult { COMMITTED, TRANSACTION_FAILED, INSURANCE_INCOMPATIBLE }

    /**
     * Commits {@code amount} for {@code playerId}: debits their balance,
     * then applies the ledger-side effects -- but only if the debit itself
     * actually succeeded. The transaction result from {@link #tryRemoveWager}
     * is authoritative, never inferred from a preceding {@code hasEnoughWager}
     * check (a Vault economy call can still fail after reporting a
     * sufficient balance). Used by the chip-selection commit path, where
     * nothing has removed any funds yet. NOT used for the
     * cursor-drag-onto-bet-spot path -- there, {@code player.setItemOnCursor(null)}
     * already destroys the dragged physical stack (a debit that cannot
     * itself "fail" the way a programmatic withdrawal can), so calling this
     * too would debit the same amount a second time. See handleBetClick's
     * cursor-drag branch, which calls commitWagerFundsAlreadyRemoved directly.
     */
    private WagerCommitResult commitWager(Player player, UUID playerId, int betSpotSlot, double amount) {
        if (!tryRemoveWager(player, amount)) {
            // Nothing was debited -- never touch the ledger, playerBets,
            // lastBetAmounts, or start the countdown as though a wager was
            // committed.
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, "blackjack.wager-transaction-failed"));
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            return WagerCommitResult.TRANSACTION_FAILED;
        }
        boolean committed = commitWagerFundsAlreadyRemoved(player, playerId, betSpotSlot, amount);
        return committed ? WagerCommitResult.COMMITTED : WagerCommitResult.INSURANCE_INCOMPATIBLE;
    }

    /**
     * The funds-movement-free half of {@link #commitWager}: pushes {@code amount} onto the committed-increment
     * ledger and keeps the legacy playerBets/lastBetAmounts maps (still relied on by finishGame/refund/deal-order
     * logic) in sync with the ledger's new total. Callers are responsible for having already removed {@code amount}
     * from the player's balance through whichever mechanism applies (or, for a cursor-drag commit, having already
     * had it removed by the client destroying the dragged stack).
     *
     * <p>Also the single choke point both the selected-wager and
     * cursor-drag commit paths funnel through. Odd whole-item wagers are
     * fully eligible for insurance (see {@link BlackjackInsuranceRules#physicalCost}
     * and {@code BlackjackInventory#computeAndStoreInsuranceOffer}), so
     * nothing here rejects a commit based on parity -- this always
     * succeeds once the funds have genuinely been removed.
     */
    private boolean commitWagerFundsAlreadyRemoved(Player player, UUID playerId, int betSpotSlot, double amount) {
        java.util.Deque<Double> increments = pregameWagerIncrements.computeIfAbsent(playerId, k -> new java.util.ArrayDeque<>());
        BlackjackWagerLedger.commit(increments, amount);
        syncPlayerBetsFromLedger(playerId, betSpotSlot);
        lastBetAmounts.computeIfAbsent(playerId, k -> new ArrayList<>()).add(amount);
        updateItemLore(betSpotSlot, BlackjackWagerLedger.total(increments));
        return true;
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

    /**
     * Resolves the exact {@link BlackjackHand} a scheduled callback was
     * meant for, by its stable {@code handId}, and validates its
     * {@code handGeneration} is still exactly what was captured at
     * schedule time -- the full round-generation + handId + handGeneration
     * + expected-state contract the table redesign plan requires for every
     * delayed hand mutation, used alongside (strengthening, not replacing)
     * {@link #isStaleHandCallback}'s own coarser per-player check. Looks
     * the hand up fresh by id every time (see
     * {@link BlackjackSplitQueue#findById}) rather than trusting any
     * captured list index or object reference, since indexes shift as
     * sibling hands are inserted mid-round.
     *
     * Also enforces the rest of the authoritative expected-state contract:
     * the hand must genuinely be {@code activeHandIndex}'s own hand (never
     * merely present somewhere else in the queue), the table's phase must
     * still be {@link BlackjackFrame.Phase#ACTIVE}, and {@code playerId}
     * must still be the table's own {@code currentPlayerId} -- a hand that
     * matches by id+generation but has been superseded as "the" active
     * hand (or whose owner is no longer the current player, or whom the
     * table has moved into insurance/lobby/etc. around) must still resolve
     * to null.
     *
     * @param expectedState {@link BlackjackHandCallbackGuard.ExpectedHandState#ACTIONABLE}
     *        for the turn timer (which only ever runs while a decision is
     *        genuinely actionable, and must be rejected while processing);
     *        {@link BlackjackHandCallbackGuard.ExpectedHandState#PROCESSING}
     *        for Hit/Double's own render/evaluation callbacks, which
     *        legitimately fire while the action is still "processing"
     *        ({@code playerTurnActive} deliberately false) but must
     *        equally be rejected once the decision has become actionable
     *        again
     * @return the live hand if every check passes, or null if the callback must no-op (round moved on, player left, reseated, no longer current, phase changed, or this exact hand -- by id, generation, and active-hand identity -- is no longer the one it was scheduled for)
     */
    private BlackjackHand resolveExpectedHand(UUID playerId, long expectedRoundGeneration, long expectedHandId, int expectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState expectedState) {
        if (roundGeneration != expectedRoundGeneration || !gameActive) {
            return null;
        }
        List<BlackjackHand> hands = playerHands.get(playerId);
        BlackjackHand candidate = null;
        if (hands != null) {
            int activeIdx = activeHandIndex.getOrDefault(playerId, -1);
            if (activeIdx >= 0 && activeIdx < hands.size()) {
                candidate = hands.get(activeIdx);
            }
        }
        boolean isSeated = playerSeats.containsKey(playerId);
        boolean isCurrentPlayer = playerId.equals(currentPlayerId);
        boolean isActivePhase = capturePhase() == BlackjackFrame.Phase.ACTIVE;
        boolean turnActive = playerTurnActive.getOrDefault(playerId, false);
        boolean expected = BlackjackHandCallbackGuard.isExpectedActiveHand(
            candidate, expectedHandId, expectedHandGeneration, isSeated, isCurrentPlayer, isActivePhase, turnActive, expectedState
        );
        return expected ? candidate : null;
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
            target.setItem(BlackjackSlotLayout.ACTIVE_EXIT_SLOT, buildExitDoorItem(viewer, view.getPlayerId()));
            // Private, like the action row: only the acting player's own
            // freshly-bootstrapped view may show the exact remaining time
            // immediately, not the idle fallback until the next scheduled
            // tick corrects it up to ~1s later. Every other viewer (another
            // seated player, a spectator) must never see another player's
            // countdown, so they get the canonical brown glass instead.
            // Never starts or extends anything -- purely reads existing
            // canonical state (see isTurnTimerCanonicallyActive) the same
            // way the running task's own ticks already validate it.
            if (isTurnTimerCanonicallyActive() && view.getPlayerId().equals(turnTimerPlayerId)) {
                target.setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, buildTurnTimerItem(viewer, turnTimerSecondsRemaining));
            } else {
                target.setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem());
            }
        } else if (playerSeats.containsKey(view.getPlayerId())) {
            paintSeatedBottomBar(target, viewer, view.getPlayerId());
            syncWagerBarPositionToCanonicalResting(view.getPlayerId());
        } else {
            paintUnseatedBottomBar(target, viewer);
            syncWagerBarPositionToCanonicalResting(view.getPlayerId());
        }

        target.setItem(frame.dealerHeadSlot(), createCustomItem(Material.CREEPER_HEAD, localize(viewer, "blackjack.dealer")));
        // While the hole card is hidden, presentation must never calculate
        // or expose a value derived from it -- only the publicly visible
        // portion of the canonical hand (the up-card) may feed the head
        // lore. frame.dealerHand() itself stays the full canonical hand
        // (peek logic elsewhere legitimately needs it); only rendering is
        // restricted. See BlackjackFrame#publiclyVisibleDealerHand.
        List<Card> visibleDealerHand = frame.publiclyVisibleDealerHand();
        String dealerHandText = BlackjackRules.formatHandCardsAndTotal(visibleDealerHand);
        if (dealerHandText != null) {
            applyHeadLore(target, frame.dealerHeadSlot(), List.of(dealerHandText), null, "blackjack.dealer", viewer);
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
            int betSlipSlot = BlackjackSlotLayout.betSlipSlot(seatSlot);

            if (seat == null) {
                target.setItem(seatSlot, buildEmptySeatChairItem(view.getPlayerId(), viewer));
                // Brown is a permanent part of the table's edge -- painted
                // for an empty seat in every phase, never left as (or
                // cleared to) the green background. See buildBetSpotItemForViewer.
                target.setItem(betSlipSlot, buildBrownEdgeGlassItem());
                continue;
            }

            Player seatOwnerPlayer = Bukkit.getPlayer(seat.getPlayerId());
            if (seatOwnerPlayer != null) {
                target.setItem(seatSlot, createPlayerHeadItem(seatOwnerPlayer, 1));
                List<String> seatHandTexts = new ArrayList<>();
                for (BlackjackFrame.HandSnapshot handSnapshot : seat.getHands()) {
                    String text = BlackjackRules.formatHandCardsAndTotal(handSnapshot.getCards());
                    if (text != null) {
                        seatHandTexts.add(text);
                    }
                }
                if (!seatHandTexts.isEmpty()) {
                    applyHeadLore(target, seatSlot, seatHandTexts, seatOwnerPlayer.getName(), null, viewer);
                }
            } else {
                // Seat tracked but the player can't be resolved (e.g. just
                // disconnected) -- fall back to the empty-seat item rather
                // than leave the slot blank.
                target.setItem(seatSlot, buildEmptySeatChairItem(view.getPlayerId(), viewer));
            }

            // Permanent bet spot: the seat owner's own click-to-bet/active
            // item, or "{name}'s betting circle" for every other viewer --
            // see buildBetSpotItemForViewer for the full per-viewer rule.
            target.setItem(betSlipSlot, buildBetSpotItemForViewer(seatSlot, view.getPlayerId(), viewer));

            if (active) {
                for (int i = 0; i < seat.getHand().size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                    target.setItem(BlackjackSlotLayout.playerCardSlot(seatSlot, i), buildCardItem(seat.getHand().get(i), viewer, seat.isCurrentTurn()));
                }
            } else if (frame.phase() == BlackjackFrame.Phase.COUNTDOWN) {
                // Private, like the turn-timer/action row above: only this
                // seat's own owner may see the countdown in their own
                // freshly-bootstrapped view, immediately, not the idle
                // background until the next scheduled tick corrects it up
                // to ~1s later. Every other viewer (another seated player,
                // a spectator) must never see another player's countdown --
                // they already get the canonical green background from
                // paintBackground's own base pass, so this only needs to
                // explicitly overlay the clock for the owner.
                if (seat.getPlayerId().equals(view.getPlayerId())) {
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
     * The empty-seat "click to sit" item as {@code viewerId} should see it --
     * the normal invite when they aren't seated anywhere yet, or a redirect
     * to their own chair once they are (an already-seated viewer can never
     * displace another empty seat by clicking it -- see handleChairClick's
     * own already-seated guard -- so the label makes that explicit instead
     * of just silently refusing the click).
     */
    private ItemStack buildEmptySeatChairItem(UUID viewerId, Player viewer) {
        boolean viewerSeatedElsewhere = viewerId != null && playerSeats.containsKey(viewerId);
        String key = viewerSeatedElsewhere ? "blackjack.seat-taken-elsewhere" : "blackjack.click-sit";
        return createCustomItem(Material.OAK_STAIRS, localize(viewer, key), 1);
    }

    /**
     * The canonical bet-spot item at {@code seatSlot} as {@code viewerId}
     * should see it right now -- the single source of truth every bet-spot
     * repaint (sitting, leaving, committing/undoing/doubling/splitting a
     * wager, a turn change, the active-phase transition, and bootstrap) all
     * funnel through:
     * <ul>
     *   <li>An empty seat is always blank brown edge glass, for every
     *       viewer, in every phase -- a permanent part of the table's edge,
     *       never cleared to the green background.</li>
     *   <li>The seat's own occupant keeps the existing click-to-bet
     *       (pregame) / permanent glowing-on-turn (active) item, carrying
     *       their own wager lore once it's nonzero.</li>
     *   <li>Any other viewer sees "{name}'s betting circle" instead --
     *       carrying the same wager lore once nonzero, and glowing while
     *       it's that seat's turn, so the shared "whose turn is it" cue
     *       survives becoming per-viewer.</li>
     * </ul>
     */
    private ItemStack buildBetSpotItemForViewer(int seatSlot, UUID viewerId, Player viewer) {
        UUID occupant = seatOwnerAt(seatSlot);
        if (occupant == null) {
            return buildBrownEdgeGlassItem();
        }
        BlackjackHand hand = activeHand(occupant);
        double wager = hand != null ? hand.getWager() : totalBet(occupant);
        boolean glowing = gameActive && occupant.equals(currentPlayerId) && !betSpotGlowSuppressed.contains(occupant);
        if (occupant.equals(viewerId)) {
            ItemStack item = gameActive
                ? buildActiveBetSpotItem(glowing)
                : createCustomItem(Material.BROWN_STAINED_GLASS_PANE, localize(viewer, "blackjack.click-bet"), 1);
            return withWagerLore(item, wager, viewer);
        }
        Player owner = Bukkit.getPlayer(occupant);
        String displayName = localize(viewer, "blackjack.other-betting-circle", "name", owner != null ? owner.getName() : "?");
        ItemStack item = glowing
            ? createGlowingCustomItem(Material.BROWN_STAINED_GLASS_PANE, displayName, 1)
            : createCustomItem(Material.BROWN_STAINED_GLASS_PANE, displayName, 1);
        return withWagerLore(item, wager, viewer);
    }

    /** Repaints {@code seatSlot}'s bet spot for the legacy inventory and every open view via {@link #buildBetSpotItemForViewer} -- the single fan-out every bet-spot-affecting event calls. */
    private void renderBetSpotToAllViews(int seatSlot) {
        int betSpotSlot = BlackjackSlotLayout.betSlipSlot(seatSlot);
        inventory.setItem(betSpotSlot, buildBetSpotItemForViewer(seatSlot, null, null));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(betSpotSlot, buildBetSpotItemForViewer(seatSlot, view.getPlayerId(), viewer));
        }
    }

    /** Repaints {@code seatSlot}'s chair icon (now empty) for the legacy inventory and every open view via {@link #buildEmptySeatChairItem}. */
    private void renderEmptySeatChairToAllViews(int seatSlot) {
        inventory.setItem(seatSlot, buildEmptySeatChairItem(null, null));
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            view.getInventory().setItem(seatSlot, buildEmptySeatChairItem(view.getPlayerId(), viewer));
        }
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
        // Closing this viewer also discards their wager bar's own slide
        // position state -- a reopen bootstraps position 8/0 fresh from
        // canonical seated status (see bootstrapView), never resuming a
        // stale mid-slide value from before the close.
        wagerBarPosition.remove(player.getUniqueId());
        wagerBarTarget.remove(player.getUniqueId());
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
        // All In (52) sits outside the chip-slot range but carries its own
        // glint, matching whichever selection (fixed chip or All In) is
        // currently selected -- see buildSeatedBottomBarSlotItem.
        view.getInventory().setItem(BlackjackSlotLayout.ALL_IN_SLOT, buildSeatedBottomBarSlotItem(BlackjackSlotLayout.ALL_IN_SLOT, playerId, viewer));
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
                item = createCustomItem(Material.WEEPING_VINES, localize(viewer, "blackjack.split"));
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
     *
     * <p>One lore line per entry in {@code cardValueTexts} -- so a split
     * player's head carries one line per hand in their queue, in order.
     * {@code cardValueTexts} is empty before any card has actually been
     * dealt (see {@link BlackjackRules#formatHandCardsAndTotal}), which
     * intentionally clears the lore to nothing rather than showing a
     * placeholder total. A single entry renders plain ("K/A:21"); more than
     * one (a split hand) gets a "Hand N-" label per line so each is
     * distinguishable.
     */
    private void applyHeadLore(Inventory target, int slot, List<String> cardValueTexts, String literalName, String nameKey, Player viewer) {
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
        if (cardValueTexts.size() == 1) {
            lore.add(localize(viewer, "blackjack.card-value", "value", cardValueTexts.get(0)));
        } else {
            for (int i = 0; i < cardValueTexts.size(); i++) {
                lore.add(localize(viewer, "blackjack.hand-label", "number", i + 1, "value", cardValueTexts.get(i)));
            }
        }
        meta.setLore(lore);
        headItem.setItemMeta(meta);
        target.setItem(slot, headItem);
    }

    /** Fans a head-lore update (player or dealer) out to the legacy inventory and every open view. */
    private void renderHeadLoreToAllViews(int slot, List<String> cardValueTexts, String literalName, String nameKey) {
        applyHeadLore(inventory, slot, cardValueTexts, literalName, nameKey, null);
        for (BlackjackView view : views.values()) {
            Player viewer = Bukkit.getPlayer(view.getPlayerId());
            applyHeadLore(view.getInventory(), slot, cardValueTexts, literalName, nameKey, viewer);
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

    /**
     * The active-play exit/door item, personalized per viewer: a seated
     * viewer who still has real money riding on this round (see {@link
     * #totalRoundRefundForPlayer}) gets an extra lore line warning that
     * clicking it forfeits that wager -- unseated spectators and a seated
     * player with nothing left at stake (every one of their hands already
     * resolved) get the plain door with no warning. {@code viewerId} may
     * be null (the legacy/no-viewer inventory), which never has anything
     * at stake by construction.
     */
    private ItemStack buildExitDoorItem(Player viewer, UUID viewerId) {
        ItemStack item = createCustomItem(Material.SPRUCE_DOOR, localize(viewer, "blackjack.leave-exit"), 1);
        if (viewerId != null && totalRoundRefundForPlayer(viewerId) > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(localize(viewer, "blackjack.leave-exit-forfeit-warning")));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /** Repaints the active-play exit/door item for the legacy inventory and every open view via {@link #buildExitDoorItem}. */
    private void renderExitDoorToAllViews() {
        inventory.setItem(BlackjackSlotLayout.ACTIVE_EXIT_SLOT, buildExitDoorItem(null, null));
        for (BlackjackView view : views.values()) {
            UUID viewerId = view.getPlayerId();
            Player viewer = Bukkit.getPlayer(viewerId);
            view.getInventory().setItem(BlackjackSlotLayout.ACTIVE_EXIT_SLOT, buildExitDoorItem(viewer, viewerId));
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
     * Renders the single canonical pregame countdown into each seated
     * player's own {@link BlackjackSlotLayout#pregameCountdownSlot} --
     * private per-viewer, exactly like the turn-timer/insurance countdowns
     * (see {@link #renderPrivateItem}): a seated viewer sees the clock only
     * in their own row, never in any other seat's row, and an unseated
     * spectator sees none at all. There is still only one canonical
     * countdown/task/deadline ({@code countdownSecondsRemaining}) -- this
     * only changes who each seat's rendering reaches, never creates a
     * separate per-player deadline. The shared/legacy {@code inventory} is
     * deliberately never written here either, so it can never leak a clock
     * to anything that might still fall back to it. Renamed from
     * {@code renderPregameCountdownToAllViews}, which became misleading
     * once this stopped fanning out to every view.
     */
    private void renderPregameCountdownToOwners(int seconds) {
        leverKey = "blackjack.starts-in";
        leverPlaceholders = new Object[] {"seconds", seconds};
        countdownSecondsRemaining = seconds;
        int amount = Math.max(seconds, 1);
        for (Map.Entry<UUID, Integer> seatEntry : playerSeats.entrySet()) {
            UUID ownerId = seatEntry.getKey();
            int seatSlot = seatEntry.getValue();
            int slot = BlackjackSlotLayout.pregameCountdownSlot(seatSlot);
            Player owner = Bukkit.getPlayer(ownerId);
            renderPrivateItem(ownerId, slot, createCustomItem(Material.CLOCK, localize(owner, "blackjack.starts-in", "seconds", seconds), amount));
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
     * Whether the current player has an actionable, unresolved turn right
     * now -- independent of whether they're online. Used to decide if the
     * turn timer must (re)start, since a genuinely offline RIDE_TO_RESULT
     * player still needs their deadline running so
     * {@link #autoStandOnTurnTimeout} can resolve their hand; {@link
     * #currentPlayerActionLayout()} can't be used for that decision because
     * it deliberately goes empty for an offline player (nothing to render).
     */
    private boolean currentPlayerHasActionableTurn() {
        return currentPlayerId != null
            && !playerDone.getOrDefault(currentPlayerId, false)
            && playerTurnActive.getOrDefault(currentPlayerId, false)
            && activeHand(currentPlayerId) != null;
    }

    /**
     * The live available-action set for one hand, config- and funds-aware --
     * used by currentPlayerActionLayout for rendering/click-validation.
     * Split-ace hands (still on their 2-card first decision) use the ace
     * matrix from {@link BlackjackActionLayout#splitAceActions}; every other
     * hand (including non-ace split hands, and a split-ace hand after it has
     * itself been hit) uses the ordinary {@link BlackjackActionLayout#availableActions}.
     * Deliberately distinct from the split-ace <em>auto-complete</em> check
     * in activateSplitHand/resolveHandAfterSplitAnimation: that decision is
     * purely configuration-driven (never balance-dependent), whereas
     * whether Double is actually visible here does fold in affordability --
     * an unaffordable-but-configured Double must still leave Stand offered
     * rather than collapse the whole decision into auto-completion.
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
            boolean acesDoubleVisible = acesDoubleAllowed && doubleAfterSplit && hasEnoughWager(player, hand.getWager());
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            return BlackjackActionLayout.splitAceActions(acesHitAllowed, acesDoubleVisible, resplitEligible);
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
        if (currentPlayerHasActionableTurn()) {
            startActionGuidance(currentPlayerId);
            startTurnTimer(currentPlayerId);
            notifyTurnStartedIfAway(currentPlayerId);
        }
    }

    /**
     * If {@code playerId}'s own private view isn't currently open (closed
     * the GUI, or genuinely disconnected but still riding out an earlier
     * hand per RIDE_TO_RESULT), they'd otherwise have no way to know it's
     * now their turn -- the live countdown only ever renders into a
     * viewer's own open view (see {@code isTurnTimerCanonicallyActive}'s
     * use in {@code bootstrapView}). A chat message is the only way to
     * reach them; naturally a no-op if they're genuinely offline.
     */
    private void notifyTurnStartedIfAway(UUID playerId) {
        if (views.containsKey(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, "blackjack.turn-started-while-away", "seconds", turnTimerTimeoutSeconds));
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
        runActionGuidancePhase(playerId, myGeneration, true);
    }

    /**
     * Renders one whole-set glow/plain phase of BlackjackActionGuidancePlan, then reschedules the opposite phase
     * ACTION_GUIDANCE_STEP_TICKS later -- re-deriving the current player's available action layout fresh at every
     * phase, so a layout change (e.g. Double Down dropping out after a Hit) is reflected in the very next phase.
     * Every currently-available action glows (or goes plain) together in the same tick -- never one action at a time.
     */
    private void runActionGuidancePhase(UUID playerId, int myGeneration, boolean glowPhase) {
        if (isStaleViewerAnimation(playerId, myGeneration) || !playerId.equals(currentPlayerId) || !playerTurnActive.getOrDefault(playerId, false)) {
            return;
        }
        Map<BlackjackAction, Integer> layout = currentPlayerActionLayout();
        if (layout.isEmpty()) {
            return;
        }
        for (Map.Entry<BlackjackAction, Integer> entry : layout.entrySet()) {
            applyActionGuidanceStep(playerId, entry.getKey(), entry.getValue(), glowPhase);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runActionGuidancePhase(playerId, myGeneration, !glowPhase), BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS);
    }

    private void applyActionGuidanceStep(UUID playerId, BlackjackAction action, int slot, boolean glowing) {
        Player viewer = Bukkit.getPlayer(playerId);
        renderPrivateItem(playerId, slot, buildActionItem(action, viewer, glowing));
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
            reRenderBetSpot(previousPlayerId);
        }
        if (newCurrentPlayerId != null) {
            reRenderHand(newCurrentPlayerId, true);
            reRenderBetSpot(newCurrentPlayerId);
        }
    }

    /** The permanent brown bet-spot item shown throughout active play -- glowing solidly while it's this seat's turn, plain otherwise. Never cleared to background while a player occupies the seat (see transitionBottomBarToActive). */
    private ItemStack buildActiveBetSpotItem(boolean glowing) {
        return glowing ? createGlowingCustomItem(Material.BROWN_STAINED_GLASS_PANE, "§r", 1) : buildBrownEdgeGlassItem();
    }

    /** Re-renders a seated player's permanent bet spot via {@link #renderBetSpotToAllViews} -- the glow state is derived live from {@code currentPlayerId} (already updated by the caller before this runs), so it self-corrects without needing to be passed in. No-op if the player isn't seated. */
    private void reRenderBetSpot(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Integer seatSlot = playerSeats.get(playerId);
        if (seatSlot == null) {
            return;
        }
        renderBetSpotToAllViews(seatSlot);
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
                Player seatOwnerPlayer = Bukkit.getPlayer(playerId);
                ItemStack headItem = seatOwnerPlayer != null
                        ? createPlayerHeadItem(seatOwnerPlayer, 1)
                        : createPlayerHead(playerId, Bukkit.getOfflinePlayer(playerId).getName(), null);
                renderToAllViews(seatSlot, headItem);
            } else {
                // Personalized per viewer: "click to sit" normally, or a
                // redirect to their own chair for a viewer already seated
                // elsewhere -- see buildEmptySeatChairItem.
                renderEmptySeatChairToAllViews(seatSlot);
            }
        }
        // Add the necessary items for the game menu
        renderLocalizedToAllViews(dealerHeadSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer"); // Dealer
        // The status clock is added when the timer starts.

        sittable=true;

        // Add bet spots (permanent brown glass -- the only betting UI
        // element, personalized per viewer -- see buildBetSpotItemForViewer).
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            renderBetSpotToAllViews(seatSlot);
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
            // Canonical repaint -- resync the wager bar's slide position to
            // match (never leaves a stale mid-slide value from before a
            // reset/cancel wiped it, see cancelAllAnimations).
            syncWagerBarPositionToCanonicalResting(view.getPlayerId());
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
        // The wager bar's controls (45-53) visually shift slots while the
        // solid strip is mid-slide -- a click there would otherwise route
        // through whichever *final* control normally owns that slot number,
        // triggering the wrong Undo/chip/All-In/door action. Only gate this
        // narrow range: seat/head clicks fall outside it and must stay live
        // so a rapid sit/unsit can keep reversing the animation.
        if (slot >= BlackjackSlotLayout.UNDO_ALL_SLOT && slot <= BlackjackSlotLayout.PREGAME_EXIT_SLOT) {
            int position = currentWagerBarPosition(playerId);
            if (position != BlackjackWagerRevealPlan.CLOSED && position != BlackjackWagerRevealPlan.OPEN) {
                return;
            }
        }
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
            // The inventory is closing right behind this call -- animating
            // the conceal would just schedule invisible steps, so repaint
            // the canonical unseated state immediately instead (see
            // handleLeaveChair's animateConceal doc).
            handleLeaveChair(player, false);
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

    // All In selects a persistent dynamic mode, never a captured balance --
    // per the table redesign plan, it never debits or commits by itself,
    // exactly like a chip click, and every later bet-spot click re-resolves
    // the player's live balance at that moment (see
    // selectWager/resolveSelectionAmount/commitWager) rather than reusing
    // totalBalance as it stood right now.
     if (SoundHelper.getSoundSafely("entity.lightning_bolt.thunder", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.5f, 0.8f);
    selectWager(player, playerId, BlackjackWagerSelection.allIn(), totalBalance);
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
            // Fully handled here -- an easter-egg click is never a game
            // action, so it must never fall through into turn-active/
            // action-lookup below (which would treat it as an invalid
            // action and produce a spurious denial sound, chat message,
            // and repaint on top of the easter egg's own feedback).
            handleDealerHeadEasterEgg(player);
            return;
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
        // Never call Deck#dealCard while it's empty during an active round --
        // that would silently trigger Deck's own auto-reshuffle, which the
        // table redesign plan explicitly forbids mid-round. Check first and
        // abort the whole round (full refund) instead.
        if (!deck.hasCards()) {
            abortRoundForShoeExhaustion();
            return;
        }

        BlackjackHand hand = activeHand(playerId);
        if (hand == null) {
            return;
        }

        int seatSlot = playerSeats.get(playerId);
        int cardCount = hand.getCards().size(); // Cards already in the active hand -- derived, not a separate lifetime counter, so it's automatically correct for whichever hand (post-split or not) is active.
        int nextCardSlot = seatSlot + 2 + cardCount; // Plain arithmetic (not playerCardSlot) -- cardCount can exceed the visible row; dealCardToPlayer bounds the render, never the canonical hand.

        long myGeneration = roundGeneration;
        int myHandToken = currentHandToken(playerId);
        long handId = hand.getHandId();
        // Captured before the card lands -- the render step below validates
        // against exactly this. addCard() (inside dealCardToPlayer) always
        // bumps handGeneration by exactly 1, and nothing else can touch
        // this hand between now and the eval step firing (playerTurnActive
        // is false the whole time, blocking any further action) -- so
        // generationBeforeCard + 1 is the eval step's own provably-correct
        // expected generation, not a guess. See resolveExpectedHand's doc.
        int generationBeforeCard = hand.getHandGeneration();

        Card newCard = deck.dealCard();
        if (isRenderableCardSlot(playerId, nextCardSlot)) {
            scheduleCardFlightEndingAt(nextCardSlot, false, BlackjackTiming.CARD_DEAL_DELAY_TICKS, myGeneration);
        }
        scheduleCardDealingWithDelay(nextCardSlot, newCard, BlackjackTiming.CARD_DEAL_DELAY_TICKS, playerId, myGeneration, myHandToken, handId, generationBeforeCard); // Deal the card with a delay

        // Delay the hand value calculation to ensure the card is fully added to the player's hand
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                return;
            }
            BlackjackHand liveHand = resolveExpectedHand(playerId, myGeneration, handId, generationBeforeCard + 1, BlackjackHandCallbackGuard.ExpectedHandState.PROCESSING);
            if (liveHand == null) {
                return;
            }
            int handValue = calculateHandValue(liveHand.getCards());
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

                liveHand.setDone(true);
                // Depth-first: if this player has another pending split
                // hand, its turn begins next; only once the whole queue is
                // exhausted does the table move on to the next player.
                advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            } else if (handValue > 21) {

                liveHand.setDone(true);
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
            // resuming the same deadline's exact remaining time rather than
            // granting a fresh one or leaving none at all.
            repaintActionsForCurrentPlayer();
            resumeTurnTimerAfterFailedAction(playerId);
            return;
        }

        // Precheck the shoe before debiting anything -- never call
        // Deck#dealCard while it's empty during an active round (that would
        // silently trigger Deck's own auto-reshuffle, forbidden mid-round),
        // and never leave a temporary debit behind if the round has to
        // abort instead.
        if (!deck.hasCards()) {
            playerTurnActive.put(playerId, true);
            repaintActionsForCurrentPlayer();
            abortRoundForShoeExhaustion();
            return;
        }

        // Remove exactly one additional wager (this hand's own, not the
        // player's whole playerBets ledger -- per-hand doubling must debit
        // exactly one more wager for that specific hand only, independent
        // of any sibling hands). The transaction itself is authoritative --
        // hasEnoughWager above was only a pre-filter; a Vault economy call
        // (or any other provider) can still fail here. Nothing about the
        // hand may change unless this actually succeeds.
        if (!tryRemoveWager(player, currentBet)) {
            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    player.sendMessage(text(player, "blackjack.wager-transaction-failed"));
                    break;}
                case VERBOSE:{
                    player.sendMessage(text(player, "blackjack.wager-transaction-failed"));
                    break;}
                case NONE:{
                    break;
                }
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            playerTurnActive.put(playerId, true); // Restore the same actionable decision -- nothing about the hand changed
            repaintActionsForCurrentPlayer(); // never grant a fresh window for a failed action
            resumeTurnTimerAfterFailedAction(playerId); // ...but never lose the remaining time either
            return;
        }
        hand.setWager(hand.getWager() * 2);
        hand.setDoubled(true);

        // The seat's single bet-spot slot always reflects the active
        // hand's own live wager during active play.
        int seatSlot = playerSeats.get(playerId);
        updateItemLore(BlackjackSlotLayout.betSlipSlot(seatSlot), hand.getWager());

        int cardCount = hand.getCards().size();
        int cardSlot = seatSlot + 2 + cardCount;
        long myGeneration = roundGeneration;
        int myHandToken = currentHandToken(playerId);
        long handId = hand.getHandId();
        // Captured after setWager/setDoubled (both already bumped this
        // hand's generation) but before the card lands -- see handleHit's
        // identical reasoning for why +1 below is provably correct, not a
        // guess: addCard always bumps by exactly 1, and nothing else can
        // touch this hand before the completion callback fires.
        int generationBeforeCard = hand.getHandGeneration();

        // Exactly one more card.
        Card newCard = deck.dealCard();
        if (isRenderableCardSlot(playerId, cardSlot)) {
            scheduleCardFlightEndingAt(cardSlot, false, BlackjackTiming.CARD_DEAL_DELAY_TICKS, myGeneration);
        }
        scheduleCardDealingWithDelay(cardSlot, newCard, BlackjackTiming.CARD_DEAL_DELAY_TICKS, playerId, myGeneration, myHandToken, handId, generationBeforeCard);

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
            BlackjackHand doubledHand = resolveExpectedHand(playerId, myGeneration, handId, generationBeforeCard + 1, BlackjackHandCallbackGuard.ExpectedHandState.PROCESSING);
            if (doubledHand == null) {
                return;
            }
            // Finish the hand regardless of its value -- never re-enable
            // Hit/Stand/Double afterward.
            doubledHand.setDone(true);
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
            BlackjackHand previousHand = hands.get(currentIndex);
            activeHandIndex.put(playerId, nextIndex);
            playerDone.put(playerId, false);
            activateSplitHand(playerId, hands.get(nextIndex), previousHand);
            return;
        }
        playerDone.put(playerId, true);
        playerTurnActive.put(playerId, false);
        repaintActionsForCurrentPlayer();
        startNextPlayerTurnWithDelay(delay);
    }

    /**
     * Activates a pending split hand that has just become the seat's
     * current hand, after a short visible handoff from whichever hand just
     * finished: the finished hand collapses down to just its first and
     * last card (see {@link #runHandTransitionCollapse}), then the newly
     * active hand slides out from under it and back over it (see {@link
     * #runHandTransitionReveal}) -- rather than the row jump-cutting
     * straight from one hand's cards to the other's. Once the reveal
     * lands, {@link #finishActivatingSplitHand} runs the exact same
     * post-render logic this always has: either auto-completing the hand
     * immediately (split-ace hand with no hit/double/resplit permitted) or
     * beginning a fresh actionable decision for it.
     */
    private void activateSplitHand(UUID playerId, BlackjackHand hand, BlackjackHand previousHand) {
        Integer seatSlotBoxed = playerSeats.get(playerId);
        if (seatSlotBoxed == null) {
            return;
        }
        int seatSlot = seatSlotBoxed;
        long myGeneration = roundGeneration;
        // Captured after advanceAfterHandResolved's own bumpHandToken call
        // (which already ran before this method was invoked), so this is
        // the token for hand 2 now being current -- if the acting player
        // leaves (door click) at any point during this whole transition,
        // isStaleHandCallback catches it below, either via the token
        // bumping again (handleLeaveChairDuringGame bumps it whenever it
        // was their turn) or simply via playerSeats no longer containing
        // them (removePlayerData always removes the seat regardless).
        // Without this, only roundGeneration (a table-wide counter that
        // does NOT change on a single player's leave) gated these
        // callbacks, so a forfeit mid-transition would leave every already-
        // scheduled step free to keep firing: rendering ghost cards/wager
        // lore into the now-vacated seat, and finishActivatingSplitHand
        // eventually calling beginActionableDecision() -- which, since
        // currentPlayerId has already moved on to whoever's turn is next,
        // would silently restart *that* unrelated player's own turn timer
        // and action guidance from scratch.
        int myHandToken = currentHandToken(playerId);

        // The finished hand's own glow (the enchant glint marking "this is
        // the active hand") -- and the seat's shared bet-spot glow riding
        // along with it -- drop the instant the hand is actually done,
        // right here, before the pause even starts: that's the visible
        // "hand 1 is done" cue, immediately followed by a beat of quiet
        // before the transition's own motion begins, rather than staying
        // glowy through the whole pause and only dropping once the
        // collapse/slide animation starts moving.
        List<Card> prevCards = previousHand.getCards();
        for (int i = 0; i < prevCards.size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            renderCardToAllViews(BlackjackSlotLayout.playerCardSlot(seatSlot, i), prevCards.get(i), false);
        }
        betSpotGlowSuppressed.add(playerId);
        renderBetSpotToAllViews(seatSlot);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                return;
            }
            runHandTransitionCollapse(playerId, seatSlot, previousHand, hand, myGeneration, myHandToken);
        }, BlackjackTiming.HAND_TRANSITION_PAUSE_TICKS);
    }

    /**
     * The finished hand collapses down to just two visible cards -- its
     * first (untouched, stays exactly where it already is) and its last
     * (ends up directly beside the first) -- before the next hand reveals.
     * Any cards strictly between them vanish one at a time, right to left,
     * then the last card itself slides left through the now-empty cells
     * into the second slot. A no-op straight into {@link
     * #runHandTransitionReveal} if the finished hand never had more than
     * two cards to begin with. (The finished hand's own glow was already
     * dropped back in {@link #activateSplitHand}, before this method's own
     * pause even started.)
     */
    private void runHandTransitionCollapse(UUID playerId, int seatSlot, BlackjackHand previousHand, BlackjackHand nextHand, long myGeneration, int myHandToken) {
        List<Card> prevCards = previousHand.getCards();
        int prevCount = prevCards.size();
        if (prevCount <= 2) {
            runHandTransitionReveal(playerId, seatSlot, nextHand, myGeneration, myHandToken);
            return;
        }

        long step = BlackjackTiming.HAND_TRANSITION_STEP_TICKS;
        int middleCards = prevCount - 2;
        for (int s = 0; s < middleCards; s++) {
            int cardIndexToVanish = prevCount - 2 - s; // rightmost middle card first, working left -- "stacking up"
            int slotToClear = BlackjackSlotLayout.playerCardSlot(seatSlot, cardIndexToVanish);
            long delay = (s + 1) * step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                    return;
                }
                renderBackgroundToAllViews(slotToClear);
            }, delay);
        }

        long afterVanish = (middleCards + 1) * step;
        int lastCardIndex = prevCount - 1;
        Card lastCard = prevCards.get(lastCardIndex);
        List<Integer> path = new ArrayList<>();
        for (int i = lastCardIndex; i >= 1; i--) {
            path.add(BlackjackSlotLayout.playerCardSlot(seatSlot, i));
        }
        for (int i = 1; i < path.size(); i++) {
            int fromSlot = path.get(i - 1);
            int toSlot = path.get(i);
            long hopDelay = afterVanish + i * step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                    return;
                }
                renderBackgroundToAllViews(fromSlot);
                renderCardToAllViews(toSlot, lastCard, false);
            }, hopDelay);
        }

        long collapseDone = afterVanish + (path.size() - 1) * step;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                return;
            }
            runHandTransitionReveal(playerId, seatSlot, nextHand, myGeneration, myHandToken);
        }, collapseDone + step);
    }

    /**
     * The next hand actually emerges from under the (by now collapsed to
     * two cards) previous hand, in two visible legs rather than simply
     * appearing already off to the side, and glowing (the enchant glint
     * marking "this is the active hand") from the very first frame it's
     * visible in, all the way through landing.
     *
     * <p>Leg 1 (slide right) is itself staggered, not a rigid two-card
     * block: the rightmost card becomes visible alone first, at {@link
     * #HAND_TRANSITION_REVEAL_START_OFFSET} -- the first slot clear of the
     * previous hand's own two cards, since a real card can never be
     * visible in the same slot as the previous hand's still-showing card
     * -- then it hops right one slot per step while each card to its left
     * joins in, one step later than the card to its right, so the hand
     * reads as filing out from under the previous hand one card at a time
     * rather than sliding as a solid block. Every card's own leading
     * (rightmost, most-recently-vacated) slot is cleared before any card's
     * new slot is rendered -- not interleaved per card -- since a moving
     * card's destination is exactly the slot the card ahead of it just
     * vacated; clearing and rendering per card in index order would wipe
     * out whichever card had just landed there a moment earlier.
     *
     * <p>All cards finish leg 1 together at {@link
     * #HAND_TRANSITION_REVEAL_OUT_OFFSET}, two slots past {@link
     * #HAND_TRANSITION_REVEAL_START_OFFSET} -- opening a visible two-slot
     * gap of bare background against the collapsed previous hand. Leg 2
     * then reverses, sliding the whole hand back left in lockstep one hop
     * at a time, landing in its real slots -- clearing the previous hand's
     * own cards (and anything beyond this hand's own card count, up to the
     * row's capacity) at the exact instant they land, so there's never a
     * moment with both hands' cards simultaneously occupying the same
     * slots.
     */
    private void runHandTransitionReveal(UUID playerId, int seatSlot, BlackjackHand nextHand, long myGeneration, int myHandToken) {
        List<Card> cards = nextHand.getCards();
        int n = cards.size();
        long step = BlackjackTiming.HAND_TRANSITION_STEP_TICKS;
        int startOffset = HAND_TRANSITION_REVEAL_START_OFFSET;
        int outOffset = HAND_TRANSITION_REVEAL_OUT_OFFSET;
        int outHops = outOffset - startOffset;
        int leg1Hops = outHops + Math.max(0, n - 1);

        // t=0: only the rightmost card (laneOffset 0) is visible yet, alone
        // at startOffset -- it leads the emergence out from under the
        // previous hand.
        for (int i = 0; i < n; i++) {
            int laneOffset = n - 1 - i;
            if (laneOffset <= 0) {
                int slot = BlackjackSlotLayout.playerCardSlot(seatSlot, startOffset - laneOffset);
                renderCardToAllViews(slot, cards.get(i), true);
            }
        }

        // Leg 1: each card i becomes visible at tick t = laneOffset (i's
        // distance behind the leading rightmost card) and from then on
        // moves right one slot per tick, landing every card at i+outOffset
        // by t = leg1Hops.
        for (int t = 1; t <= leg1Hops; t++) {
            long hopDelay = t * step;
            int tFinal = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                    return;
                }
                List<Integer> clears = new ArrayList<>();
                List<int[]> renders = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    int laneOffset = n - 1 - i;
                    if (tFinal - 1 >= laneOffset) {
                        clears.add(BlackjackSlotLayout.playerCardSlot(seatSlot, startOffset + (tFinal - 1) - laneOffset));
                    }
                    if (tFinal >= laneOffset) {
                        renders.add(new int[] {i, startOffset + tFinal - laneOffset});
                    }
                }
                for (int slot : clears) {
                    renderBackgroundToAllViews(slot);
                }
                for (int[] render : renders) {
                    renderCardToAllViews(BlackjackSlotLayout.playerCardSlot(seatSlot, render[1]), cards.get(render[0]), true);
                }
            }, hopDelay);
        }

        // Leg 2: reverse and slide all the way back left into the real slots.
        long leg2Base = leg1Hops * step;
        for (int hop = 1; hop <= outOffset; hop++) {
            long hopDelay = leg2Base + hop * step;
            boolean finalHop = hop == outOffset;
            int hopFinal = hop;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
                    return;
                }
                for (int i = 0; i < n; i++) {
                    int fromSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, i + outOffset - hopFinal + 1);
                    int toSlot = BlackjackSlotLayout.playerCardSlot(seatSlot, i + outOffset - hopFinal);
                    renderBackgroundToAllViews(fromSlot);
                    renderCardToAllViews(toSlot, cards.get(i), true);
                }
                if (finalHop) {
                    for (int i = cards.size(); i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
                        int slotToClear = BlackjackSlotLayout.playerCardSlot(seatSlot, i);
                        // The bottom seat's row shares its very last card
                        // cell with the deck token's own resting slot (see
                        // BlackjackSlotLayout#DECK_HOME_SLOT's doc) -- a
                        // blind background clear here would wipe the deck
                        // icon itself out from under the table. Restore it
                        // instead of clearing to background whenever this
                        // cleanup reaches that slot.
                        if (slotToClear == dealerDeckTokenSlot) {
                            renderHiddenCardToAllViews(slotToClear);
                        } else {
                            renderBackgroundToAllViews(slotToClear);
                        }
                    }
                    // Hand 2 has just slammed back into its real slots,
                    // right beside the bet spot -- the bet spot's own glow
                    // comes back on in this exact same step, not before.
                    betSpotGlowSuppressed.remove(playerId);
                    renderBetSpotToAllViews(seatSlot);
                    finishActivatingSplitHand(playerId, seatSlot, nextHand);
                }
            }, hopDelay);
        }
    }

    /**
     * The post-reveal continuation of {@link #activateSplitHand} -- exactly
     * the logic that method always ran immediately after rendering a newly
     * active split hand's cards, now reached once the hand-to-hand
     * transition animation has actually landed instead.
     */
    private void finishActivatingSplitHand(UUID playerId, int seatSlot, BlackjackHand hand) {
        Player player = Bukkit.getPlayer(playerId);
        List<Card> cards = hand.getCards();
        updatePlayerHead(playerId);
        updateItemLore(BlackjackSlotLayout.betSlipSlot(seatSlot), hand.getWager());

        if (player != null && hand.isSplitFromAce() && cards.size() == 2) {
            List<BlackjackHand> hands = playerHands.getOrDefault(playerId, List.of());
            // Auto-complete is a purely configuration-driven decision --
            // never balance-dependent. A configured Double that's merely
            // unaffordable right now must still leave Stand offered, not
            // silently finish the hand as if Double had never been
            // permitted at all (see BlackjackActionLayoutTest's affordability
            // matrix cases).
            boolean acesDoubleConfigured = acesDoubleAllowed && doubleAfterSplit;
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            if (BlackjackActionLayout.splitAceHandAutoCompletes(acesHitAllowed, acesDoubleConfigured, resplitEligible)) {
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
                resumeTurnTimerAfterFailedAction(playerId);
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
                // A denied split is not a new decision -- repaint only,
                // resuming the same deadline's remaining time.
                repaintActionsForCurrentPlayer();
                resumeTurnTimerAfterFailedAction(playerId);
                return;
            }

            boolean wasResplit = hand.isFromSplit();

            // Exactly one additional matching wager, debited once. The
            // transaction itself is authoritative -- splitEligibleForHand's
            // own affordability check above was only a pre-filter; a Vault
            // economy call can still fail here. Nothing about the hand
            // queue, cards, shoe, or animation may change unless this
            // actually succeeds.
            if (!tryRemoveWager(player, hand.getWager())) {
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, "blackjack.wager-transaction-failed"));
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                }
                playerTurnActive.put(playerId, true); // Restore the same actionable decision -- nothing about the hand changed
                repaintActionsForCurrentPlayer(); // never grant a fresh window for a failed action
                resumeTurnTimerAfterFailedAction(playerId); // ...but never lose the remaining time either
                return;
            }

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
     * The shared/table-owned split animation, staged so a viewer can
     * actually see two hands come into being (see
     * {@link BlackjackSplitAnimationPlan}'s own doc for the full visual
     * story): B slides out to a temporary slot, C deals in beside A, D
     * deals in beside temp-B, the inactive [B][D] pair slides one step
     * left, then both temporary slots clear, leaving only the active [A][C]
     * hand visible. Owned by the table, not the acting viewer -- see
     * {@link #cancelSharedAnimation()}'s doc -- so a random <em>other</em>
     * viewer closing their inventory never interrupts it; only a genuinely
     * table-wide event (round reset/cancel, generation change) or the
     * <em>acting</em> player themselves leaving their seat does -- the
     * latter is exactly what {@link BlackjackSplitOperationGuard} detects
     * (see {@link #isSplitOperationValid}), so every phase here proves it
     * still belongs to this exact split (round + phase + the acting
     * player's own seat + both hands still present by stable handId) before
     * touching anything, never trusting a captured list index or object
     * reference alone. Every phase repaints only cells inside the acting
     * seat's own row (see {@link BlackjackSlotLayout#playerCardSlot}'s own
     * bound), never any other seat's.
     */
    private void runSplitAnimation(UUID playerId, int seatSlot, BlackjackHand originalHand, BlackjackHand siblingHand, Card originalReplacement, Card siblingReplacement) {
        long myGeneration = roundGeneration;
        BlackjackFrame.Phase myPhase = capturePhase();
        cancelSharedAnimation();
        BlackjackAnimationRun run = new BlackjackAnimationRun(null, myGeneration, 0, myPhase);
        sharedAnimationRun = run;
        splitAnimationInFlight = true;
        splitAnimationPlayerId = playerId;
        long stepTicks = BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS;

        // Phase 1/2's guard: both hands still exactly as handleSplit left
        // them -- neither has received its replacement card yet.
        BlackjackSplitOperationGuard guardBeforeAnyDeal = new BlackjackSplitOperationGuard(
            playerId, seatSlot, myGeneration, myPhase, originalHand.getHandId(), siblingHand.getHandId(),
            originalHand.getHandGeneration(), siblingHand.getHandGeneration()
        );

        int slotOrigB = BlackjackSlotLayout.playerCardSlot(seatSlot, 1); // becomes C's slot in phase 2
        int slotGap = BlackjackSlotLayout.playerCardSlot(seatSlot, 2);
        int slotTempB = BlackjackSlotLayout.playerCardSlot(seatSlot, 3);
        int slotTempD = BlackjackSlotLayout.playerCardSlot(seatSlot, 4);

        // D (phase 3) shoots in from the deck, same as any other dealt card
        // -- its target is past B entirely so the plain up-then-left flight
        // never crosses anything. Two further tunings on top of the
        // already-halved idle gap: D's own per-hop movement is faster
        // still (~25%), and the idle gap before it starts moving is
        // smaller still (~20% off the already-halved value) -- both
        // against the SAME fixed landing tick is not achievable (a faster
        // flight is a shorter one, which necessarily starts later against
        // a fixed landing tick, not earlier), so the landing tick itself
        // now moves earlier by whatever both together need -- phase 3's
        // own firing (and phase 4/5, chained after it) shifts earlier by
        // the same amount, never phase 3's own relative spacing from
        // phase 2 onward. How far D actually travels varies by seat, so
        // this is all computed per-call rather than a flat constant --
        // same reasoning as halvedIdleGapHopTicks's own doc.
        long phase3ShiftEarlier = 0L;
        if (isRenderableCardSlot(playerId, slotTempD)) {
            List<Integer> dPath = flightPathFromDeck(slotTempD, false);
            int dHopCount = dPath.size() - 1;
            long dHopTicks = fasterSiblingCardHopTicks(dHopCount, stepTicks, 2 * stepTicks);
            long dLandingTick = fasterSiblingCardLandingTick(dHopCount, stepTicks, 2 * stepTicks);
            phase3ShiftEarlier = Math.max(0L, 2 * stepTicks - dLandingTick);

            scheduleCardFlightAlongPathEndingAt(dPath, dLandingTick, myGeneration, dHopTicks);
        }
        long phase3Delay = Math.max(1L, stepTicks - phase3ShiftEarlier);

        // Deliberately slower than the ordinary dealt-card hop rate -- see
        // BlackjackTiming#SPLIT_SLIDE_HOP_TICKS's own doc for why B's slide
        // needs its own pace, not the fast rate every other flight on the
        // table already uses. The dash (bottom seat only) gets its own,
        // faster rate -- see BOTTOM_SEAT_DASH_HOP_TICKS's own doc.
        long slideHopTicks = BlackjackTiming.SPLIT_SLIDE_HOP_TICKS;
        long dashHopTicks = BlackjackTiming.BOTTOM_SEAT_DASH_HOP_TICKS;
        boolean bottomSeat = seatSlot == BlackjackSlotLayout.SEAT_SLOTS[BlackjackSlotLayout.SEAT_SLOTS.length - 1];

        // C's own flight (phase 2, landing at stepTicks): the general case
        // detours through the row below B's temp slot; the bottom seat
        // instead runs its own two-part dash + final hop below, since it
        // has no row below to detour through in the first place. See
        // bottomSeatSplitDashPath's own doc for the full reasoning.
        long phase1Delay;
        Integer bottomSeatStagingSlot = null;
        if (bottomSeat && isRenderableCardSlot(playerId, slotOrigB)) {
            List<Integer> dashPath = bottomSeatSplitDashPath(slotOrigB);
            scheduleCardFlightHops(dashPath, 0L, myGeneration, dashHopTicks);
            bottomSeatStagingSlot = dashPath.get(dashPath.size() - 1);
            phase1Delay = (dashPath.size() - 1) * dashHopTicks;
        } else {
            phase1Delay = 0L;
            if (isRenderableCardSlot(playerId, slotOrigB)) {
                scheduleCardFlightAlongPathEndingAt(splitOriginalCardFlightPath(slotOrigB), stepTicks, myGeneration);
            }
        }

        // Phase 1: B genuinely slides out of its original slot into its
        // temporary right-hand position -- hopping visibly through the gap
        // cell in between, one hop per slideHopTicks, rather than instantly
        // teleporting -- the moment the split genuinely becomes two hands.
        // For the bottom seat only, this whole phase is delayed (phase1Delay)
        // until C's own dash above has fully cleared the row -- B's slide
        // never has to share the row with C in flight at all. Every step
        // below is scheduled directly from right now, at an absolute delay
        // (phase1Delay + ...), never nested inside another already-delayed
        // callback -- runTaskLater's delay is always relative to whenever
        // it's called, so nesting it would silently double-delay everything
        // inside.
        //
        // The origin's vacate and B's arrival at the gap happen in the very
        // same step, not one then-a-hop-later the other -- B must never be
        // rendered nowhere at all (neither at the origin nor the gap) for
        // even a moment, which is exactly what a separate vacate-then-wait
        // step produced (a real, visible blank flicker once slideHopTicks
        // was slowed down for readability). Only the second half of the
        // slide (gap -> temp slot) is a genuinely separate, delayed hop.
        // Purely presentational: neither hand's cards change yet, so
        // guardBeforeAnyDeal (already valid for this whole pre-deal window)
        // covers every step. (For the bottom seat, C's own final hop -- see
        // hopUpLandingTick below -- is scheduled entirely separately from
        // this phase, on its own deliberate pause.)
        Card slidingSiblingCard = siblingHand.getCards().get(0);
        Runnable vacateOriginAndEnterGap = () -> {
            if (isRenderableCardSlot(playerId, slotOrigB)) {
                renderBackgroundToAllViews(slotOrigB);
            }
            if (isRenderableCardSlot(playerId, slotGap)) {
                renderCardToAllViews(slotGap, slidingSiblingCard, false);
            }
        };
        if (phase1Delay == 0) {
            vacateOriginAndEnterGap.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (run != sharedAnimationRun || !isSplitOperationValid(run, guardBeforeAnyDeal)) {
                    return;
                }
                vacateOriginAndEnterGap.run();
            }, phase1Delay);
        }
        // B's own temp-slot landing -- the second half of its slide,
        // genuinely separate from (and unrelated to) how long C itself
        // waits parked below before hopping up.
        long finalHopDelay = phase1Delay + slideHopTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run != sharedAnimationRun || !isSplitOperationValid(run, guardBeforeAnyDeal)) {
                return;
            }
            if (isRenderableCardSlot(playerId, slotGap)) {
                renderBackgroundToAllViews(slotGap);
            }
            if (isRenderableCardSlot(playerId, slotTempB)) {
                renderCardToAllViews(slotTempB, slidingSiblingCard, false);
            }
        }, finalHopDelay);

        // For the bottom seat, C's own final hop (staging slot -> slotOrigB)
        // waits out its own deliberate pause parked below the target slot
        // -- BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS after the dash finishes --
        // rather than hopping up the instant B clears out of the way. It
        // used to ride along on B's own temp-slot-landing tick, which
        // (before that pause existed) happened to land C's hop on the very
        // same tick as the vacate step above by coincidence of the timing
        // constants, leaving the two same-tick tasks' actual order up to
        // the scheduler; when the vacate step happened to run last, its
        // blind clear of slotOrigB erased the hidden card C's hop had just
        // placed there. Scheduling it on its own delay, well clear of both
        // the vacate step and B's own landing, avoids both that old
        // disappearing-card race and C hopping up looking instantaneous.
        long hopUpLandingTick = phase1Delay + BlackjackTiming.BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS;
        final Integer stagingSlot = bottomSeatStagingSlot;
        if (stagingSlot != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (run != sharedAnimationRun || !isSplitOperationValid(run, guardBeforeAnyDeal)) {
                    return;
                }
                renderBackgroundToAllViews(stagingSlot);
                if (isRenderableCardSlot(playerId, slotOrigB)) {
                    renderHiddenCardToAllViews(slotOrigB);
                }
            }, hopUpLandingTick);
        }

        // Phase 2 (C's real reveal) normally fires at stepTicks flat, but
        // for the bottom seat it must never fire before C's own hop-up
        // above has actually landed and had at least a brief beat to read
        // as parked there face-down -- otherwise the "flip" reads as
        // instantaneous, or phase 2 could even fire while C is still
        // sitting at the staging slot. Widening phase 2's own delay here
        // pushes every phase chained after it (3 through 6, all nested
        // relative-delay callbacks inside phase 2's own closure) later by
        // the same amount automatically; other seats are unaffected since
        // stagingSlot is null for them and phase2Delay stays exactly stepTicks.
        long phase2Delay = stepTicks;
        if (stagingSlot != null) {
            phase2Delay = Math.max(stepTicks, hopUpLandingTick + slideHopTicks);
        }

        // Phase 2: the original hand's replacement card, C, deals in
        // visibly beside A, into the slot B just vacated.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run != sharedAnimationRun || !isSplitOperationValid(run, guardBeforeAnyDeal)) {
                return;
            }
            originalHand.addCard(originalReplacement); // bumps originalHand's own generation by exactly 1
            if (isRenderableCardSlot(playerId, slotOrigB)) {
                renderCardToAllViews(slotOrigB, originalReplacement, playerId.equals(currentPlayerId));
            }
            updatePlayerHead(playerId);

            // Phase 3's own guard: derived from this phase's, with only
            // originalHand's expected generation advanced -- explicit and
            // provably correct (addCard's own contract), never re-derived
            // from a captured value that phase 2 itself would have already
            // invalidated.
            BlackjackSplitOperationGuard guardAfterOriginalDeal = guardBeforeAnyDeal.withExpectedGenerations(
                originalHand.getHandGeneration(), siblingHand.getHandGeneration()
            );

            // Phase 3: the sibling's replacement card, D, deals in visibly
            // beside temp-B.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (run != sharedAnimationRun || !isSplitOperationValid(run, guardAfterOriginalDeal)) {
                    return;
                }
                siblingHand.addCard(siblingReplacement); // bumps siblingHand's own generation by exactly 1
                if (isRenderableCardSlot(playerId, slotTempD)) {
                    renderCardToAllViews(slotTempD, siblingReplacement, false);
                }
                updatePlayerHead(playerId); // the sibling hand's own lore line must reflect D immediately, not stay stale until something unrelated repaints it

                BlackjackSplitOperationGuard guardAfterBothDealt = guardAfterOriginalDeal.withExpectedGenerations(
                    originalHand.getHandGeneration(), siblingHand.getHandGeneration()
                );

                // Phase 4: the inactive [B][D] pair slides one visible step
                // left, into the unused gap cell -- purely presentational
                // (no hand mutation), so phase 5 reuses this exact guard.
                // Phases 4-6 all pace off SPLIT_PARK_STEP_TICKS, not the
                // phase 2/3 stepTicks gap -- a flat delay (not derived from
                // D's own hop count the way its flight pacing is), so it's
                // identical for every seat regardless of row; the whole
                // park-away sequence ("hand 2 slipping under hand 1", see
                // phase 5's own doc) read as too slow at the shared
                // stepTicks pace.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (run != sharedAnimationRun || !isSplitOperationValid(run, guardAfterBothDealt)) {
                        return;
                    }
                    if (isRenderableCardSlot(playerId, slotGap)) {
                        renderCardToAllViews(slotGap, siblingHand.getCards().get(0), false);
                    }
                    if (isRenderableCardSlot(playerId, slotTempB)) {
                        renderCardToAllViews(slotTempB, siblingReplacement, false);
                    }
                    if (isRenderableCardSlot(playerId, slotTempD)) {
                        renderBackgroundToAllViews(slotTempD);
                    }

                    // Phase 5: the sibling hand tucks away behind the active
                    // [A][C] hand instead of both cards just vanishing
                    // together -- B (at the gap, right beside C) disappears
                    // first, D immediately takes its place as the sole
                    // remaining visible card, then after one more beat D
                    // disappears too. Reads as hand 2 slipping under hand 1,
                    // rather than an abrupt simultaneous clear.
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (run != sharedAnimationRun || !isSplitOperationValid(run, guardAfterBothDealt)) {
                            return;
                        }
                        if (isRenderableCardSlot(playerId, slotTempB)) {
                            renderBackgroundToAllViews(slotTempB);
                        }
                        if (isRenderableCardSlot(playerId, slotGap)) {
                            renderCardToAllViews(slotGap, siblingReplacement, false);
                        }

                        // Phase 6: park -- D (now alone at the gap) also
                        // tucks away, leaving only the active [A][C] hand
                        // visible, then return control (phase 7: split-ace
                        // auto-resolution or a fresh actionable decision).
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (run != sharedAnimationRun || !isSplitOperationValid(run, guardAfterBothDealt)) {
                                return;
                            }
                            if (isRenderableCardSlot(playerId, slotGap)) {
                                renderBackgroundToAllViews(slotGap);
                            }
                            sharedAnimationRun = null;
                            splitAnimationInFlight = false;
                            splitAnimationPlayerId = null;
                            resolveHandAfterSplitAnimation(playerId);
                        }, BlackjackTiming.SPLIT_PARK_STEP_TICKS);
                    }, BlackjackTiming.SPLIT_PARK_STEP_TICKS);
                }, BlackjackTiming.SPLIT_PARK_STEP_TICKS / 2 + BlackjackTiming.SPLIT_PARK_PRE_SLIDE_EXTRA_PAUSE_TICKS);
            }, phase3Delay);
        }, phase2Delay);
    }

    /**
     * True only if {@code run} is still the live shared animation run (not
     * superseded/cancelled) AND {@code guard}'s captured split identity
     * still resolves against live state -- the acting player is still
     * seated where expected, and both hands the split produced are still
     * present under their own stable handId. Looks the hands up by id every
     * time rather than trusting any object reference or index captured at
     * schedule time, per the table redesign plan's stale-callback
     * discipline.
     */
    private boolean isSplitOperationValid(BlackjackAnimationRun run, BlackjackSplitOperationGuard guard) {
        if (run.isStale(roundGeneration, 0, capturePhase())) {
            return false;
        }
        UUID playerId = guard.getPlayerId();
        Integer currentSeatSlot = playerSeats.get(playerId);
        List<BlackjackHand> hands = playerHands.get(playerId);
        BlackjackHand currentOriginal = BlackjackSplitQueue.findById(hands, guard.getOriginalHandId());
        // The original hand must genuinely still be *the* active hand
        // (activeHandIndex's own pointer), not merely present somewhere
        // else in the queue -- a resplit or the queue advancing on to a
        // sibling must invalidate a step still expecting the original hand
        // to be current.
        if (currentOriginal != null && hands != null) {
            int activeIdx = activeHandIndex.getOrDefault(playerId, -1);
            boolean isActiveHand = activeIdx >= 0 && activeIdx < hands.size() && hands.get(activeIdx) == currentOriginal;
            if (!isActiveHand) {
                currentOriginal = null;
            }
        }
        BlackjackHand currentSibling = BlackjackSplitQueue.findById(hands, guard.getSiblingHandId());
        return guard.isValid(roundGeneration, capturePhase(), currentSeatSlot, currentOriginal, currentSibling);
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
            // Configuration-driven only -- see activateSplitHand's identical doc.
            boolean acesDoubleConfigured = acesDoubleAllowed && doubleAfterSplit;
            boolean resplitEligible = acesResplitAllowed && splitEligibleForHand(player, hand, hands);
            if (BlackjackActionLayout.splitAceHandAutoCompletes(acesHitAllowed, acesDoubleConfigured, resplitEligible)) {
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
     * The complete refund owed to {@code playerId} for the round currently
     * in progress -- the single authoritative calculation shared by a
     * shoe-exhaustion round abort and a {@code PLUGIN_DISABLE} refund, so
     * neither duplicates or drifts from the other's accounting. Before the
     * first card lands, no {@link BlackjackHand} exists yet and the
     * committed pregame wager still lives only in {@code playerBets}; once
     * hands exist, {@code playerBets} is no longer touched by doubles/splits
     * so the live sum of every hand's own current wager (see
     * {@link BlackjackRoundAbortRefund}) is the only correct source --
     * summing both would double-count the original wager. Any insurance
     * stake still in {@link #insuranceStakes} is, by construction, still
     * undetermined (paid/forfeited stakes are removed from that map the
     * instant the dealer's peek resolves them), so including it here can
     * never double-count an already-settled insurance outcome either.
     */
    private double totalRoundRefundForPlayer(UUID playerId) {
        List<BlackjackHand> hands = playerHands.get(playerId);
        double insuranceStake = insuranceStakes.getOrDefault(playerId, 0.0);
        if (hands != null && !hands.isEmpty()) {
            return BlackjackRoundAbortRefund.totalRefundForPlayer(hands, insuranceStake);
        }
        return totalBet(playerId) + insuranceStake;
    }

    /**
     * Refunds {@code amount} to {@code playerId} directly if they're online
     * AND the live credit actually confirms delivered; otherwise queues it
     * as a {@link PendingPayout} so an offline player's funds -- or a
     * failed live Vault deposit -- are never silently dropped. Never queues
     * after a confirmed successful live delivery (see {@link #refundPendingBets}
     * for the unconditional {@code PLUGIN_DISABLE} case, which never even
     * attempts a live deposit).
     */
    private void refundRoundDebit(UUID playerId, double amount, String messageKey) {
        if (amount <= 0) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && addWagerToInventory(player, amount)) {
            if (messageKey != null) {
                switch (plugin.getPreferences(playerId).getMessageSetting()) {
                    case NONE:
                        break;
                    default:
                        player.sendMessage(text(player, messageKey, "amount", plugin.formatWagerDisplay(currencyMode, currencyName, amount)));
                }
            }
            return;
        }
        queuePendingRefund(playerId, amount);
    }

    /** Queues {@code amount} as a refund {@link PendingPayout}, claimable regardless of the player's online state. */
    private void queuePendingRefund(UUID playerId, double amount) {
        queueBlackjackPendingPayout(playerId, amount, PayoutMessages.serverRestartRefundContext("Blackjack"));
    }

    /**
     * Durably queues {@code amount} owed to {@code playerId} for this dealer
     * -- the single choke point every Blackjack credit path (payout, push,
     * insurance, refund, undo) falls back to whenever a live delivery either
     * can't be attempted (offline recipient) or was attempted and failed
     * (Vault reported a failed deposit). If the pending record itself can't
     * be persisted, logs a high-severity warning identifying the player,
     * this table, the exact amount, and the reason, since at that point the
     * money is genuinely at risk of being lost rather than merely delayed.
     */
    private void queueBlackjackPendingPayout(UUID playerId, double amount, String context) {
        if (amount <= 0) {
            return;
        }
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout payout = PendingPayout.create(
            playerId,
            "Blackjack",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            context
        );
        if (!plugin.getPendingPayoutStore().addPendingPayout(payout)) {
            plugin.getLogger().severe("[NCCasino] FAILED TO PERSIST a Blackjack pending payout -- player="
                + playerId + ", table=" + internalName + ", amount=" + amount + ", reason=" + context
                + ". This amount may be permanently lost and requires manual reconciliation.");
        }
    }

    /**
     * Aborts the entire round because the shoe cannot immediately continue
     * supplying it, refunding every debit of that round via
     * {@link #abortRoundAndRefund}. Never pays out or settles hands -- the
     * round simply never happened.
     */
    private void abortRoundForShoeExhaustion() {
        abortRoundAndRefund("blackjack.shoe-exhausted-refunded");
    }

    /**
     * Aborts the entire round for any reason that means it can never
     * legitimately be dealt/finished (shoe exhaustion, a start-transition
     * readiness gate that never resolved), refunding every debit of that
     * round (original wagers, split wagers, double wagers, insurance
     * stakes -- see {@link #totalRoundRefundForPlayer}) to every seated
     * player -- delivered live when possible, durably queued otherwise, per
     * {@link #refundRoundDebit} -- then resets for the next round. Never
     * pays out or settles hands; the round simply never happened. The
     * single choke point both abort reasons funnel through, so refund
     * accounting can never drift between them.
     */
    private void abortRoundAndRefund(String messageKey) {
        for (UUID playerId : new ArrayList<>(playerSeats.keySet())) {
            refundRoundDebit(playerId, totalRoundRefundForPlayer(playerId), messageKey);
        }
        insuranceStakes.clear();
        insuranceOfferedCost.clear();
        resetGame();
    }

    // Handle chair click
    private void handleChairClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();

        // Must run before the sittable-check below reads this slot's
        // rendered item: a mid-flight game-reset sweep can be covering this
        // very chair with its white tile at the exact instant of this
        // click, which would otherwise make a perfectly legitimate sit
        // silently fail the "ends with _STAIRS" check. See
        // cancelResetSweepIfActive's own doc.
        cancelResetSweepIfActive();

        // Once the countdown hits zero and the start-transition begins, no
        // new seat can be taken -- matches the identical guard already on
        // chip selection, All In, and bet placement (see beginStartTransition).
        // Without this, a player seating mid-transition is never included in
        // startTransitionDoorConcealComplete (only the snapshot captured at
        // beginStartTransition is), which used to stall isReadyToDeal's gate
        // forever; isReadyToDeal is now also defended against this
        // independently (it only ever awaits that same snapshot), but
        // rejecting the seat here is what stops the late player from seeing
        // an apparently-usable wager bar whose clicks are then silently
        // rejected by every one of those sibling guards.
        if (startTransitionActive) {
            return;
        }

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

        // Now that this seat is occupied, every other viewer's bet spot for
        // it must switch from blank brown to "{name}'s betting circle" --
        // see buildBetSpotItemForViewer.
        renderBetSpotToAllViews(slot);

        // Sitting completes chair guidance for this player for the round --
        // never restarted by a later leave/reseat/repaint this same round
        // (see chairGuidanceCompleted's own doc); only the next genuine
        // round boundary clears it.
        chairGuidanceCompleted.add(playerId);

        // Chair guidance no longer applies now that they've sat -- the
        // door-reveal animation (private, this viewer only) takes over,
        // sliding their own bottom bar from door+glass to the full seated
        // wager bar, then handing off to wager guidance once it completes.
        cancelPrivateAnimation(playerId);
        // Sitting mid-glow-frame would otherwise freeze any still-empty
        // seats in this viewer's own view glowing forever (nothing else
        // repaints the seat row for an already-seated viewer) -- put them
        // back in their canonical plain state right now.
        repaintEmptySeatsPlainForViewer(playerId);
        startWagerBarReveal(playerId);
    }


// Handle leave chair during the countdown or active game
private void handleLeaveChair(Player player) {
    handleLeaveChair(player, true);
}

/**
 * @param animateConceal Whether a revealed pregame wager bar should be
 *     concealed via {@link #startWagerBarConceal} (mirroring the reveal
 *     that ran on sit) rather than repainted immediately. False for a
 *     leave that's about to close the viewer's inventory anyway (the
 *     door/exit click) -- see that call site's own comment.
 */
private void handleLeaveChair(Player player, boolean animateConceal) {
    UUID playerId = player.getUniqueId();

    if (!playerSeats.containsKey(playerId)) {
        return;
    }

     if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);

    // A wager bar only ever exists on this viewer's own bottom row while
    // !gameActive (active play shows the exit+timer bar instead, painted
    // independently of seat status -- see bootstrapView) -- captured here,
    // before removePlayerData/gameActive can change under us, so the
    // conceal/repaint decision below reflects what was actually on screen.
    boolean hadWagerBar = !gameActive;

    // Captured before removePlayerData can empty playerSeats and, if this
    // is the table's last occupied seat, trigger cancelGame() -- which does
    // its own synchronous, unconditional initializeGameMenu() repaint of
    // every view (this one included) straight to the canonical closed bar.
    // Without restoring this afterward, a solo player's leave-conceal would
    // find its own starting position already snapped to CLOSED and idempotently
    // no-op instead of animating, i.e. the bar would just vanish instead of
    // sliding shut -- see the restore right before startWagerBarConceal below.
    int positionBeforeLeaving = currentWagerBarPosition(playerId);

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
    // last seat empties. It also cancels this viewer's own private
    // animation (the door-reveal/wager guidance/bet-spot blink that may
    // still be running), so the conceal started below always begins its
    // own fresh generation rather than racing an old one.
    removePlayerData(playerId);

    if (hadWagerBar && animateConceal) {
        // If removePlayerData above just triggered cancelGame() (last seat
        // emptied), its own hard repaint already snapped this viewer's bar
        // to CLOSED this same tick -- restore both the tracked position and
        // the actually-rendered frame to where it genuinely was before that
        // happened, so the conceal requested below has real distance to
        // animate instead of finding itself already "there" and no-oping.
        // A harmless no-op when cancelGame() didn't fire (other seats still
        // occupied): position is simply restored to the value it already had.
        wagerBarPosition.put(playerId, positionBeforeLeaving);
        wagerBarTarget.put(playerId, positionBeforeLeaving);
        renderWagerBarFrame(playerId, positionBeforeLeaving);
        // Mirrors the reveal that ran on sit; chains into
        // scheduleChairGuidanceStart itself once fully concealed (see that
        // method's doc for why chair guidance can't be kicked off here
        // directly without immediately invalidating these very steps).
        startWagerBarConceal(playerId);
        return;
    }

    if (hadWagerBar) {
        // GUI is closing right behind this call (or some other caller
        // opted out of the animation) -- an animated conceal would just
        // schedule invisible steps, so normalize to the canonical unseated
        // bar immediately instead. removePlayerData above already cancelled
        // any prior moving-bar callback chain (cancelPrivateAnimation); this
        // discards its position/target state too rather than leaving a
        // stale mid-slide value behind for whatever repaints this viewer next.
        wagerBarPosition.put(playerId, BlackjackWagerRevealPlan.CLOSED);
        wagerBarTarget.put(playerId, BlackjackWagerRevealPlan.CLOSED);
        paintUnseatedBottomBarForViewer(playerId);
    }

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

    // A random *other* viewer leaving must never touch the shared split
    // animation -- but if the *acting* player of an in-flight split is the
    // one leaving, there's nothing left worth animating for anyone (the
    // whole operation belonged to their now-vacated seat), so proactively
    // tear it down here instead of leaving a stale in-flight flag around
    // until its now-guarded-off steps eventually no-op on their own.
    if (playerId.equals(splitAnimationPlayerId)) {
        cancelSharedAnimation();
    }

    // A leave that doesn't empty the whole table (cancelGame() not
    // triggered below) still changes this seat's real content -- if a
    // game-reset white-tile sweep is still mid-flight from an earlier
    // resetGame()/cancelGame(), its later steps would otherwise restore
    // this now-stale slot back to whatever it snapshotted before this
    // leave happened. Scoped to the sweep specifically (its run is always
    // tagged LOBBY) so this never touches a genuinely unrelated shared
    // animation -- the dealer inspection (START_TRANSITION) or split
    // sequence (ACTIVE) must keep running for remaining players exactly as
    // before when one player leaves mid-round.
    cancelResetSweepIfActive();

    // Retrieve the player's seat slot
    int seatSlot = playerSeats.getOrDefault(playerId, -1);

    // If the player has a valid seat slot
    if (seatSlot != -1) {
        // Clear the player's cards from the table
        List<Card> hand = activeHandCards(playerId);
        for (int i = 0; i < hand.size() && i < BlackjackSlotLayout.SEAT_CARD_CAPACITY; i++) {
            renderBackgroundToAllViews(BlackjackSlotLayout.playerCardSlot(seatSlot, i)); // Clear each card slot in the player's row back to the felt
        }

        // Remove player's data from tracking maps
        playerHands.remove(playerId);
        activeHandIndex.remove(playerId);
        playerCardCounts.remove(playerId);
        playerTurnActive.remove(playerId);
        playerDone.remove(playerId);
        handToken.remove(playerId);
        betSpotGlowSuppressed.remove(playerId);

        // Remove player's bets and related lore
        clearPlayerBetLore(playerId);
        clearPlayerBets(playerId);

        // Remove player from seat map
        playerSeats.remove(playerId);
        SessionRegistry.unregister(playerId, this);

        // Repaint the now-empty seat and its bet spot for every viewer,
        // personalized per viewer -- must run after playerSeats.remove
        // above, so seatOwnerAt/buildEmptySeatChairItem both see the seat
        // as genuinely empty (and, for the other viewers' own seated
        // status, correctly still reflect their own chair, not this one).
        renderEmptySeatChairToAllViews(seatSlot);
        renderBetSpotToAllViews(seatSlot);

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
        insuranceOfferedCost.remove(playerId);
        if (insurancePhaseActive) {
            checkInsuranceAllDecided();
        }
    }

    // Check if all players have left the game
    if (playerSeats.isEmpty()) {
        cancelGame();
    }
}

    // Handle chip selection -- only sets the persistent selected wager
    // tool, moves no funds, pushes nothing to the ledger (see selectWager).
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
        selectWager(player, playerId, BlackjackWagerSelection.fixed(amount), amount);
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
            // must NOT also call removeWagerFromInventory -- see commitWager's doc. May reject and refund (see
            // BlackjackInsuranceWagerPolicy) -- only play the success sound/start the countdown if it actually committed.
            boolean committed = commitWagerFundsAlreadyRemoved(player, playerId, betSpotSlot, amount);

            if (committed) {
                if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);

                if (countdownTaskId == -1) {
                    startCountdownTimer();
                }
            }
            return;
        }

        BlackjackWagerSelection selection = selectedWager.get(playerId);
        // Resolved fresh right now -- for a FIXED selection this is just
        // its captured amount, but for ALL_IN it re-derives the player's
        // live balance at this exact commit attempt, never a balance
        // snapshotted back when All In was originally selected (see
        // resolveSelectionAmount/BlackjackWagerSelection's own doc).
        double resolvedAmount = selection == null ? 0.0 : resolveSelectionAmount(player, selection);
        if (selection != null && resolvedAmount > 0 && hasEnoughWager(player, resolvedAmount)) {
            WagerCommitResult result = commitWager(player, playerId, betSpotSlot, resolvedAmount);
            // The selection itself is a persistent tool, per the table
            // redesign plan: neither a successful commit, a transaction
            // failure, nor an insurance-incompatible rejection ever
            // unselects it here -- only picking a different denomination
            // (selectWager overwrites the entry) or leaving the chair
            // (clearPlayerBets) does.
            cancelPrivateAnimation(playerId); // stop the bet-spot blink -- restarted immediately below
            refreshWagerControlsForPlayer(playerId);

            if (result == WagerCommitResult.COMMITTED) {
                if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);

                if (countdownTaskId == -1) {
                    startCountdownTimer();
                }
            }
            // Selection persists regardless of the result -- resume the
            // blink so a repeated bet-spot click keeps reusing it without
            // forcing the player to re-select.
            startBetSpotBlink(playerId);
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
            if (!addWagerToInventory(player, totalRefund)) {
                queuePendingRefund(playerId, totalRefund);
            }
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

            if (!addWagerToInventory(player, lastBet)) {
                queuePendingRefund(playerId, lastBet);
            }

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

    /**
     * Vault economies support exact fractional balances -- most relevantly,
     * a 12.5 insurance stake off an odd wager. Every other currency mode
     * (plain material items, custom item currency) is whole-unit only, so
     * this only ever applies to Vault; physical-item modes instead prevent
     * an odd wager from ever being committed while insurance is enabled
     * (see commitWagerFundsAlreadyRemoved's validation), so their own
     * insurance cost is always exactly representable as a whole item.
     *
     * <p>This is only ever a pre-filter to avoid attempting a debit that's
     * obviously going to fail -- it must never be treated as proof a
     * subsequent {@link #tryRemoveWager} will succeed. A Vault economy
     * transaction (or any other provider call) can still fail after this
     * reports a sufficient balance; only the transaction itself is
     * authoritative.
     */
    private boolean hasEnoughWager(Player player, double amount) {
        if (amount <= 0.0) {
            return false;
        }
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null && provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
            return vaultProvider.hasAtLeastDecimal(player, internalName, MoneyHelper.clampNonNegative(MoneyHelper.bd(amount)));
        }

        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return false;

        if (provider != null) {
            return provider.has(player, internalName, requiredAmount);
        }

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            return false;
        }
        return player.getInventory().containsAtLeast(new ItemStack(currencyMaterial), requiredAmount);
    }

    /**
     * Attempts to debit exactly {@code amount} from {@code player}'s
     * balance, returning whether the debit actually succeeded -- the
     * transaction itself, never a preceding {@link #hasEnoughWager} check.
     * Every caller must only mutate wager/hand/insurance state after this
     * returns true, and must treat a false return as "nothing was
     * debited" -- if a provider partially withdraws before reporting
     * failure (e.g. {@code StandardItemCurrencyProvider} can pull whatever
     * it finds even when short), whatever it did take is refunded here so
     * a failed debit can never leave the player short.
     */
    private boolean tryRemoveWager(Player player, double amount) {
        if (amount <= 0.0) {
            return false;
        }
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null) {
            return BlackjackWagerTransaction.tryWithdraw(provider, player, internalName, amount);
        }

        int requiredAmount = MoneyHelper.toWagerUnits(amount);
        if (requiredAmount <= 0) return false;

        Material currencyMaterial = plugin.getCurrency(internalName);
        if (currencyMaterial == null) {
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().removeItem(new ItemStack(currencyMaterial, requiredAmount));
        if (leftover.isEmpty()) {
            return true;
        }
        // Same rollback principle for the no-provider raw-material fallback:
        // removeItem() takes whatever it finds and only reports the
        // shortfall, so give back whatever it actually took.
        int shortfall = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        int actuallyRemoved = requiredAmount - shortfall;
        if (actuallyRemoved > 0) {
            player.getInventory().addItem(new ItemStack(currencyMaterial, actuallyRemoved));
        }
        return false;
    }

    /**
     * Delivers {@code amount} to {@code player} right now via whichever
     * {@link CurrencyProvider} this dealer uses. Item currencies always
     * succeed (worst case, overflow drops on the ground below). Vault can
     * genuinely fail (economy hook error, race, provider outage) -- the
     * return value is authoritative and callers that owe this money
     * unconditionally must queue a {@link PendingPayout} for the exact
     * amount when this returns {@code false}, never assume delivery.
     */
    private boolean addWagerToInventory(Player player, double amount) {
        CurrencyProvider provider = getCurrencyProvider();
        if (provider != null && provider.getMode() == CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
            java.math.BigDecimal refund = MoneyHelper.clampNonNegative(MoneyHelper.bd(amount));
            if (refund.compareTo(java.math.BigDecimal.ZERO) > 0) {
                return vaultProvider.deposit(player, internalName, refund);
            }
            return true;
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
        return true;
    }

    private void clearPlayerBets(UUID playerId) {
        if (playerId == null) {
            playerBets.clear();
            lastBetAmounts.clear();
            clearConsumedRoundWagerLedger();
        } else {
            playerBets.remove(playerId);
            lastBetAmounts.remove(playerId);
            pregameWagerIncrements.remove(playerId);
            selectedWager.remove(playerId);
        }
    }

    /**
     * Clears every player's committed pregame wager-increment ledger
     * ({@link #pregameWagerIncrements}) -- state {@link #resetGame}
     * previously left behind, letting a stale committed increment be
     * refunded a second time via Undo All/Undo Last once wagering reopened
     * for the next round. Deliberately leaves {@link #selectedWager} alone:
     * a seated player's persistent wager selection survives a normal round
     * reset (see selectedWager's own doc) -- only leaving the chair or
     * picking a different selection clears it.
     *
     * <p><b>Ordering invariant:</b> every caller must have already fully
     * calculated and delivered (or queued) whatever payout/refund this
     * round's committed wagers are owed <em>before</em> calling this --
     * {@link #finishGame} settles every hand first, and
     * {@link #abortRoundForShoeExhaustion} refunds every seated player
     * first, both immediately before their own call to {@link #resetGame}
     * (the only caller of this method). Calling this any earlier would
     * destroy the very state those calculations read.
     */
    private void clearConsumedRoundWagerLedger() {
        BlackjackRoundWagerLedger.clearConsumed(pregameWagerIncrements);
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
                    renderPregameCountdownToOwners(countdown);
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
            // Brown is a permanent part of the table's edge -- an empty
            // seat's bet spot must never be cleared to the green
            // background, here or anywhere else. See buildBetSpotItemForViewer.
            renderBetSpotToAllViews(seatSlot);
        }
        renderExitDoorToAllViews();
        renderToAllViews(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem()); // idle until the first player's turn actually starts -- see startTurnTimer
        clearPregameCountdownFromAllViews();
        dealerHeadSlot = BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT;
        renderLocalizedToAllViews(dealerHeadSlot, Material.CREEPER_HEAD, 1, "blackjack.dealer");
    }

    // Activate the game and set the dealer's turn
private void activateGame() {
    gameActive = true; // Mark the game as active
    roundGeneration++;
    dealerSequenceToken++;
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

    // Each step's own flight starts as soon as the previous step's card is
    // about to land (minus a small overlap), not at the plan's own
    // precomputed stagger -- so consecutive cards' flights just barely
    // overlap instead of each one fully landing before the next departs.
    // scheduleCardFlight returns the later tick (flight + flip) the real
    // deal (data mutation + true-face render) must actually fire at, so the
    // visual flight's exact landing is what flips face-down to face-up,
    // never a separate/earlier instant reveal.
    long nextStepStartDelay = 0L;
    long lastCardLandingDelay = 0L;
    for (BlackjackDealPlan.Step step : plan.getSteps()) {
        long landingDelay = scheduleCardFlight(step.getSlot(), step.isDealer(), nextStepStartDelay, myGeneration);
        lastCardLandingDelay = Math.max(lastCardLandingDelay, landingDelay);
        nextStepStartDelay = Math.max(nextStepStartDelay + 1, landingDelay - BlackjackTiming.INITIAL_DEAL_OVERLAP_TICKS);
        if (step.isHidden()) {
            // Hidden placeholder: the Card IS drawn from the shoe now (so
            // its rank is known for the dealer peek below), just rendered
            // as a hidden placeholder until revealDealerHoleCardNow/
            // revealDealerCardWithDelay later paints it face-up -- staying
            // face-down at "landing" is exactly the same render either way,
            // so the hole card is the one card that never visibly flips.
            Card holeCard = deck.dealCard();
            scheduleHiddenCardDealing(step.getSlot(), holeCard, landingDelay, myGeneration);
        } else {
            scheduleCardDealing(step.getSlot(), deck.dealCard(), (int) landingDelay, step.getPlayerId(), myGeneration);
        }
    }

    // Check for initial blackjack, then insurance/dealer-peek, right after dealing cards.
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (roundGeneration != myGeneration || !gameActive) {
            return;
        }
        for (UUID playerId : orderedSeatedPlayers()) {
            // Deliberately not gated on the player being online -- a
            // RIDE_TO_RESULT player's natural blackjack must still be
            // resolved here, or they fall through into the ordinary turn
            // loop and stall it once their turn comes up.
            int handValue = calculateHandValue(activeHandCards(playerId));
            if (handValue == 21) {
                playerDone.put(playerId, true); // Mark the player as done
                playerTurnActive.put(playerId, false); // Deactivate the player's turn
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
    }, lastCardLandingDelay + 2L); // A short margin past the last card's own flight+flip landing, not plan.initialBlackjackCheckDelayTicks() -- the flight adds real time on top of the plan's raw per-step stagger.
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
    insuranceOfferedCost.clear();
    insuranceSecondsRemaining = insuranceTimeoutSeconds;

    for (UUID playerId : eligible) {
        // The offer is generated and stored right here, exactly once --
        // every later read (display, acceptance/debit, payout, timeout,
        // abort, teardown) looks this value up rather than ever
        // recomputing it. See computeAndStoreInsuranceOffer's own doc for
        // why that matters: recomputing a physical-currency offer would
        // risk a second, different coin flip.
        BlackjackHand hand = activeHand(playerId);
        computeAndStoreInsuranceOffer(playerId, hand != null ? hand.getOriginalPreSplitWager() : 0.0);
        renderInsurancePromptForPlayer(playerId);
        notifyInsuranceOfferedIfAway(playerId);
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

/**
 * Mirrors {@link #notifyTurnStartedIfAway} for the insurance decision:
 * if {@code playerId}'s own private view isn't currently open when
 * insurance is offered, the private Yes/No/countdown UI just rendered
 * for them is invisible -- a chat message is the only way to reach
 * them. Naturally a no-op if they're genuinely offline.
 */
private void notifyInsuranceOfferedIfAway(UUID playerId) {
    if (views.containsKey(playerId)) {
        return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
        return;
    }
    switch (plugin.getPreferences(playerId).getMessageSetting()) {
        case NONE:
            break;
        default:
            player.sendMessage(text(player, "blackjack.insurance-available-while-away", "seconds", insuranceTimeoutSeconds));
    }
}

/**
 * Computes {@code playerId}'s exact insurance offer for this round and
 * stores it in {@link #insuranceOfferedCost} -- the single point where
 * this cost is ever calculated. Vault preserves the exact fractional
 * half (12.5 off a 25 wager stays 12.5); every other currency mode is
 * whole-unit-only, so an odd wager's exact half lands precisely on a
 * half-unit, and {@link #insuranceRoundingCoinFlip} is consulted exactly
 * once, right here, to pick the whole-unit direction. An even physical
 * wager's half is already a whole unit, so the coin flip is never
 * consulted at all in that case -- there is nothing to decide.
 */
private double computeAndStoreInsuranceOffer(UUID playerId, double originalPreSplitWager) {
    double offer;
    if (currencyMode == CurrencyMode.VAULT) {
        offer = BlackjackInsuranceRules.cost(originalPreSplitWager);
    } else {
        long wholeWager = Math.round(originalPreSplitWager);
        boolean roundUp = wholeWager % 2 != 0 && insuranceRoundingCoinFlip.getAsBoolean();
        offer = BlackjackInsuranceRules.physicalCost(originalPreSplitWager, roundUp);
    }
    insuranceOfferedCost.put(playerId, offer);
    return offer;
}

/** Substitutes a deterministic decider for {@link #insuranceRoundingCoinFlip} so a test can force either physical-rounding direction without any statistical/flaky assertion. */
void setInsuranceRoundingCoinFlipForTest(java.util.function.BooleanSupplier decider) {
    this.insuranceRoundingCoinFlip = decider;
}

/** Records {@code playerId}'s Yes/No insurance decision, or does nothing (stays clickable) if Yes was chosen but they can't afford it. */
private void handleInsuranceDecision(Player player, boolean takeInsurance) {
    UUID playerId = player.getUniqueId();
    if (!insurancePhaseActive || !insuranceEligiblePlayers.contains(playerId) || insuranceDecided.contains(playerId)) {
        return;
    }

    if (takeInsurance) {
        // The exact offer stored when this phase began -- never
        // recomputed here. Recomputing would, for a physical-currency odd
        // wager, risk a second coin flip landing on a different whole
        // unit than what was ever displayed/offered.
        double cost = insuranceOfferedCost.getOrDefault(playerId, 0.0);
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
        // The transaction itself is authoritative -- hasEnoughWager above
        // was only a pre-filter; a Vault economy call can still fail here.
        // Distinguish this from insufficient funds where practical: the
        // decision must stay undecided either way (still clickable, so the
        // player can retry or decline), but the feedback differs.
        if (!tryRemoveWager(player, cost)) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, "blackjack.wager-transaction-failed"));
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            return;
        }
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
        insuranceOfferedCost.clear();
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
        insuranceOfferedCost.clear();
        beginPlayerTurns(myGeneration);
    }
}

/**
 * Pays 2:1 + stake to every player who took insurance -- only ever called
 * once the dealer's peek confirms blackjack. An offline recipient (or a
 * live Vault deposit that fails) must still receive this money, so it's
 * never simply skipped -- it's queued as a durable {@link PendingPayout}
 * instead.
 */
private void payInsuranceWinners() {
    for (Map.Entry<UUID, Double> entry : insuranceStakes.entrySet()) {
        UUID playerId = entry.getKey();
        double stake = entry.getValue();
        double payout = BlackjackInsuranceRules.payoutTotal(stake);
        Player player = Bukkit.getPlayer(playerId);
        boolean online = player != null && player.isOnline();
        boolean delivered = online && addWagerToInventory(player, payout);
        if (!delivered) {
            queueBlackjackPendingPayout(playerId, payout, online
                ? PayoutMessages.committedResultContext("Blackjack")
                : PayoutMessages.disconnectedMidGameContext("Blackjack"));
            continue;
        }
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
    insuranceOfferedCost.clear();
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
 * invalid click, a failed action, reopening a view). Mandatory -- always
 * starts a real deadline; there is no disabled state.
 *
 * <p>The canonical deadline (identity + remaining seconds) lives in
 * {@code turnTimer*} fields, independent of the rendering task itself --
 * {@link #stopTurnTimerTask()} only ever cancels/pauses the *task* that
 * counts it down and re-renders it; it deliberately never clears this
 * canonical state. That's what lets {@link #resumeTurnTimerAfterFailedAction}
 * pick the exact same deadline back up (remaining time, not a fresh
 * window) after a failed Double/Split -- see its own doc.
 */
private void startTurnTimer(UUID playerId) {
    stopTurnTimerTask();
    turnTimerPlayerId = null;
    turnTimerSecondsRemaining = -1;
    BlackjackHand hand = activeHand(playerId);
    if (hand == null) {
        return;
    }
    turnTimerPlayerId = playerId;
    turnTimerRoundGeneration = roundGeneration;
    turnTimerHandToken = currentHandToken(playerId);
    turnTimerHandId = hand.getHandId();
    // The hand's generation at the exact instant this fresh decision began
    // -- nothing may have mutated it since (see beginActionableDecision's
    // own contract), so this is the deadline's whole-lifetime expected
    // generation; any tick, resume, or the timeout itself seeing a
    // different live generation means a superseding action already
    // claimed this hand.
    turnTimerExpectedHandGeneration = hand.getHandGeneration();
    turnTimerSecondsRemaining = turnTimerTimeoutSeconds;

    runTurnTimerTask();
}

/**
 * Starts (or resumes) the repeating render/countdown task for whatever
 * deadline is currently canonical in the {@code turnTimer*} fields --
 * decrementing {@link #turnTimerSecondsRemaining} itself each tick
 * (rather than a value local to the task) so the remaining time survives
 * the task being cancelled and later resumed.
 */
private void runTurnTimerTask() {
    if (turnTimerTaskId != -1) {
        Bukkit.getScheduler().cancelTask(turnTimerTaskId);
        turnTimerTaskId = -1;
    }
    final UUID playerId = turnTimerPlayerId;
    final long myGeneration = turnTimerRoundGeneration;
    final int myHandToken = turnTimerHandToken;
    final long handId = turnTimerHandId;
    final int expectedHandGeneration = turnTimerExpectedHandGeneration;

    turnTimerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        @Override
        public void run() {
            if (isStaleHandCallback(playerId, myGeneration, myHandToken)
                || resolveExpectedHand(playerId, myGeneration, handId, expectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState.ACTIONABLE) == null) {
                // The hand this deadline belonged to has already moved on
                // (Stand/Double/leave/reset/completion) -- stop silently,
                // whatever superseded it owns slot 46 now.
                if (turnTimerTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(turnTimerTaskId);
                    turnTimerTaskId = -1;
                }
                return;
            }
            if (turnTimerSecondsRemaining <= 0) {
                Bukkit.getScheduler().cancelTask(turnTimerTaskId);
                turnTimerTaskId = -1;
                autoStandOnTurnTimeout(playerId, myGeneration, myHandToken, handId, expectedHandGeneration);
                return;
            }
            renderTurnTimerToAllViews(turnTimerSecondsRemaining);
            turnTimerSecondsRemaining--;
        }
    }, 0L, 20L);
}

/**
 * Pauses the turn-timer's rendering/countdown task (e.g. while an action
 * is being validated/processed) and restores slot 46 to its idle
 * brown-glass state, WITHOUT touching the canonical deadline state
 * ({@code turnTimerSecondsRemaining} and identity) -- that's exactly
 * what lets a failed action resume the same deadline afterward instead
 * of either losing it or granting a fresh one. Genuine end-of-decision
 * points (Stand/Double succeeding, leave, reset, round completion) don't
 * need a separate "invalidate": the next {@link #startTurnTimer} simply
 * overwrites the canonical fields, and any stale resume attempt against
 * superseded state fails {@link #resolveExpectedHand}'s own identity
 * check on its own.
 */
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

/**
 * Resumes the canonical turn-timer deadline after a failed Double/Split
 * left the very same decision back in place -- continues counting down
 * from whatever time canonically remained (see {@link #stopTurnTimerTask}'s
 * doc on why that survives the pause) rather than granting a fresh full
 * window or leaving the player with no deadline at all. Validates the
 * paused deadline still genuinely belongs to {@code playerId}'s current
 * actionable hand first (same identity contract as the timer's own
 * ticks), so a deadline superseded while paused is correctly never
 * resumed. If the deadline had already reached zero while the failed
 * transaction was being attempted, resolves it as a timeout (auto-Stand)
 * immediately instead of resuming a countdown from zero -- never grants
 * extra time.
 */
private void resumeTurnTimerAfterFailedAction(UUID playerId) {
    if (turnTimerPlayerId == null || !turnTimerPlayerId.equals(playerId)) {
        return;
    }
    if (isStaleHandCallback(playerId, turnTimerRoundGeneration, turnTimerHandToken)
        || resolveExpectedHand(playerId, turnTimerRoundGeneration, turnTimerHandId, turnTimerExpectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState.ACTIONABLE) == null) {
        // Superseded since the deadline was paused -- nothing to resume;
        // whatever now owns slot 46 (or nothing) is already correct.
        return;
    }
    if (turnTimerSecondsRemaining <= 0) {
        autoStandOnTurnTimeout(playerId, turnTimerRoundGeneration, turnTimerHandToken, turnTimerHandId, turnTimerExpectedHandGeneration);
        return;
    }
    renderTurnTimerToAllViews(turnTimerSecondsRemaining);
    runTurnTimerTask();
}

/**
 * True only if the canonical turn-timer deadline ({@code turnTimer*}
 * fields) genuinely still belongs to a live, actionable hand right now --
 * the exact same identity contract the timer's own ticks and
 * {@link #resumeTurnTimerAfterFailedAction} already validate before
 * touching it, exposed read-only here so {@link #bootstrapView} can
 * render the exact remaining time for a freshly created view instead of
 * always falling back to idle brown glass. Purely a query: never mutates
 * anything and never resolves a timeout itself, even if the canonical
 * seconds have already reached zero -- that stays the running task's job.
 */
private boolean isTurnTimerCanonicallyActive() {
    if (turnTimerPlayerId == null || turnTimerSecondsRemaining <= 0) {
        return false;
    }
    return !isStaleHandCallback(turnTimerPlayerId, turnTimerRoundGeneration, turnTimerHandToken)
        && resolveExpectedHand(turnTimerPlayerId, turnTimerRoundGeneration, turnTimerHandId, turnTimerExpectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState.ACTIONABLE) != null;
}

/** The clock item for slot 46 showing {@code secondsLeft}, localized for {@code viewer} (or the server default if {@code null}, e.g. the shared legacy inventory). */
private ItemStack buildTurnTimerItem(Player viewer, int secondsLeft) {
    int amount = Math.max(secondsLeft, 1);
    return createCustomItem(Material.CLOCK, localize(viewer, "blackjack.turn-timer-lore", "seconds", secondsLeft), amount);
}

private void renderTurnTimerToAllViews(int secondsLeft) {
    renderToAllViews(BlackjackSlotLayout.TURN_TIMER_SLOT, buildBrownEdgeGlassItem());
    BlackjackView actingView = turnTimerPlayerId == null ? null : views.get(turnTimerPlayerId);
    if (actingView != null) {
        Player viewer = Bukkit.getPlayer(turnTimerPlayerId);
        actingView.getInventory().setItem(BlackjackSlotLayout.TURN_TIMER_SLOT, buildTurnTimerItem(viewer, secondsLeft));
    }
}

/**
 * On timeout, auto-Stands exactly the hand whose deadline expired --
 * guarded by both the player-level roundGeneration + handToken pair and
 * the exact hand's own stable handId + captured handGeneration (see
 * resolveExpectedHand), so a timeout can never fire against a hand that's
 * already moved on (superseded by Stand/Hit/Double/Split, or the player
 * leaving/the round resetting). Depth-first, same as an ordinary Stand:
 * only the active hand is finished here -- if the player has another
 * pending split hand, its turn begins next via advanceAfterHandResolved,
 * and the table only advances to the next player once every one of this
 * player's hands is resolved.
 */
private void autoStandOnTurnTimeout(UUID playerId, long myGeneration, int myHandToken, long handId, int expectedHandGeneration) {
    synchronized (turnLock) {
        if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
            return;
        }
        BlackjackHand hand = resolveExpectedHand(playerId, myGeneration, handId, expectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState.ACTIONABLE);
        if (hand == null) {
            return;
        }
        playerTurnActive.put(playerId, false);
        hand.setDone(true);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            switch (plugin.getPreferences(playerId).getMessageSetting()) {
                case NONE:
                    break;
                default:
                    player.sendMessage(text(player, "blackjack.turn-timer-expired"));
            }
        }
        advanceAfterHandResolved(playerId, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
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

            // currentPlayer is null for a RIDE_TO_RESULT player whose turn
            // comes up while they're still offline -- just skip the
            // presence-only feedback (message/sound) and let the turn
            // proceed as normal so the table doesn't stall on them.
            if (currentPlayer != null) {
                switch(plugin.getPreferences(currentPlayerId).getMessageSetting()){
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
            }

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

/**
 * Begins the dealer's own turn -- captures {@code roundGeneration} once
 * here and threads it through every reveal/draw/recursion/finish callback
 * in the whole sequence below, so a stale callback from an old round
 * (reset/cancelled after the last player left, then a new round started
 * and committed a new wager before the old callback fired) can never draw
 * from the new round's shoe, reveal its hole card, settle it, or reset it.
 * Every one of those callbacks must validate this exact generation (plus
 * {@code gameActive}) before touching anything -- see
 * {@link #isStaleDealerSequenceCallback}.
 */
private void startDealerTurn() {
    long myGeneration = roundGeneration;
    dealerSequenceToken++; // a new dealer sequence begins -- invalidates any prior one still pending
    int myDealerSequenceToken = dealerSequenceToken;

    // Skip the dealer's own turn only when every wagered hand at the table
    // (across every player's own hand queue, not just each player's single
    // currently-active hand) is busted -- a player who stood on an earlier
    // split hand and busted on a later one must still have that earlier
    // hand settled against the dealer's play.
    boolean allPlayersBusted = BlackjackTableBustCheck.allHandsBusted(
        playerSeats.keySet().stream().map(playerHands::get).collect(java.util.stream.Collectors.toList())
    );
    if (allPlayersBusted) {

        finishGame(); // Directly finish the game if all players are busted
        return;
    }

    // Reveal the dealer's hidden card with delay
    revealDealerCardWithDelay(myGeneration, myDealerSequenceToken, 20L);

    // Dealer must hit until reaching at least 17. Cards continue leftward
    // from the hole card (51) toward 47 -- descending, not ascending; see
    // the table redesign plan's "Open item to verify" note and
    // BlackjackSlotLayout#dealerCardSlot.
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
            return;
        }
        dealDealerCardsUntilSeventeen(myGeneration, myDealerSequenceToken, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT - 1, calculateHandValue(dealerHand), 20L);
    }, 40L); // Start dealer's turn after revealing with delay
}

/**
 * True if a delayed callback belonging to the dealer sequence captured at
 * {@code capturedGeneration} is no longer valid -- a new round has started
 * (or the round ended) since it was scheduled. Every reveal/draw/recursion/
 * finish callback in {@link #startDealerTurn}'s sequence must check this
 * before mutating or rendering anything.
 */
private boolean isStaleDealerSequenceCallback(long capturedGeneration, int capturedDealerSequenceToken) {
    return BlackjackDealerSequenceGuard.isStale(capturedGeneration, roundGeneration, capturedDealerSequenceToken, dealerSequenceToken, gameActive);
}

private void revealDealerCardWithDelay(long myGeneration, int myDealerSequenceToken, long delay) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
            return;
        }
        revealDealerHoleCardNow();
    }, delay);
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

/**
 * Recursively draws the dealer's own cards up to (and past 17 per the
 * configured stand chance) -- every entry to this method, including each
 * recursive re-schedule, validates {@code myGeneration} first (see
 * {@link #isStaleDealerSequenceCallback}) so a stale recursive draw left
 * over from a reset/cancelled round can never touch the new round's shoe,
 * dealer hand, or settlement.
 */
private void dealDealerCardsUntilSeventeen(long myGeneration, int myDealerSequenceToken, int nextSlot, int dealerCardSum, long delay) {
    if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken) || playerSeats.isEmpty()) {
    // If the round has moved on, or all players have left, stop immediately
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

    if (mutableDealerCardSum[0] < 17) {
        // The dealer's own rule requires another card here -- never settle
        // an incomplete (<17) dealer hand just because the shoe ran out;
        // abort the whole round with a full refund instead (see the table
        // redesign plan's shoe-exhaustion requirement).
        if (!deck.hasCards()) {
            abortRoundForShoeExhaustion();
            return;
        }
        Card newCard = deck.dealCard();
        if (isRenderableCardSlot(null, nextSlot)) {
            scheduleCardFlightEndingAt(nextSlot, true, delay, myGeneration);
        }

        // Deal the card (and schedule the next one) only once its own
        // deck-flight actually lands -- same pattern as a player's hit/
        // double-down card, instead of the old instant-appear behavior.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
                return;
            }
            dealCardToPlayer(nextSlot, newCard, null); // Deal the card to the dealer
            mutableDealerCardSum[0] = calculateHandValue(dealerHand); // Recalculate after adding each card
            updateDealerHead();
            dealDealerCardsUntilSeventeen(myGeneration, myDealerSequenceToken, nextSlot - 1, mutableDealerCardSum[0], delay);
        }, delay);
    } else if (mutableDealerCardSum[0] == 17) {
        // Determine whether the dealer stops at 17 based on the percentage chance
        if (!BlackjackRules.dealerShouldHit(17, standOn17Chance, Math.random() * 100)) {
            // Stop at 17
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
                    return;
                }
                finishGame();
            }, delay);
        } else {
            // Continue if the dealer does not stop at 17 -- same
            // shoe-exhaustion guard as above; previously this branch simply
            // did nothing at all when the shoe was empty, silently hanging
            // the round forever instead of resolving it either way.
            if (!deck.hasCards()) {
                abortRoundForShoeExhaustion();
                return;
            }
            Card newCard = deck.dealCard();
            if (isRenderableCardSlot(null, nextSlot)) {
                scheduleCardFlightEndingAt(nextSlot, true, delay, myGeneration);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
                    return;
                }
                dealCardToPlayer(nextSlot, newCard, null);
                mutableDealerCardSum[0] = calculateHandValue(dealerHand);
                updateDealerHead();
                dealDealerCardsUntilSeventeen(myGeneration, myDealerSequenceToken, nextSlot - 1, mutableDealerCardSum[0], delay);
            }, delay);
        }
    } else {
        // Proceed to finish the game after the dealer's turn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStaleDealerSequenceCallback(myGeneration, myDealerSequenceToken)) {
                return;
            }
            finishGame();
        }, delay);
    }
}


/**
 * Deals a hit/double-down card after a delay, guarded by both the
 * player-level round-generation/hand-token pair (see isStaleHandCallback)
 * and the exact hand's own stable handId + captured handGeneration (see
 * resolveExpectedHand) -- not just the "player still seated" check inside
 * dealCardToPlayer. That check alone can't tell a still-seated player's
 * stale card (left over from a resolved or superseded hand) from a live
 * one; this full identity contract can.
 */
private void scheduleCardDealingWithDelay(int slot, Card card, long delay, UUID playerId, long myGeneration, int myHandToken, long handId, int expectedHandGeneration) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (isStaleHandCallback(playerId, myGeneration, myHandToken)) {
            return;
        }
        if (resolveExpectedHand(playerId, myGeneration, handId, expectedHandGeneration, BlackjackHandCallbackGuard.ExpectedHandState.PROCESSING) == null) {
            return;
        }
        dealCardToPlayer(slot, card, playerId);
    }, delay);
}


/**
 * One hand's own settled outcome and net result (profit for a win/
 * blackjack, {@code -wager} for a loss/bust, {@code 0} for a push) -- used
 * only to build the consolidated round-summary message for a player who
 * settled more than one hand this round (see {@link #sendSplitRoundSummary}).
 */
private record HandOutcomeSummary(int handNumber, BlackjackOutcome outcome, double netAmount) {
}

/**
 * Settles every {@link BlackjackHand} across every seated player
 * independently, using each hand's own {@code handId}-stable wager --
 * required for splitting, where one player can hold several
 * simultaneously-resolved hands with different outcomes (bust one,
 * blackjack-value-win another) from the very same round.
 *
 * <p>A player who settled exactly one wagered hand gets the plain,
 * unchanged per-outcome message straight from {@link #settleHandOutcome}.
 * A player who settled more than one (split into multiple hands) instead
 * gets every individual message suppressed and one consolidated summary
 * sent after the whole loop -- otherwise a lucky player with several split
 * hands would be flooded with a wall of separate "you won!"/"you lost"
 * lines and separate payout confirmations for each one.
 */
private void finishGame() {
    for (UUID playerId : playerSeats.keySet()) {
        List<BlackjackHand> hands = playerHands.get(playerId);
        if (hands == null || hands.isEmpty()) {
            continue; // Skip players who never got dealt in
        }

        List<BlackjackHand> wageredHands = new ArrayList<>();
        for (BlackjackHand hand : hands) {
            if (hand.getWager() > 0) {
                wageredHands.add(hand);
            }
        }
        if (wageredHands.isEmpty()) {
            continue;
        }

        // Deliberately does NOT skip an offline player here -- their hands
        // still owe real money (a win/blackjack/push payout), which must be
        // queued as a PendingPayout, not silently dropped just because
        // Bukkit.getPlayer returns null while they're disconnected. See
        // settleHandOutcome/payOut, which resolve online state per hand and
        // fall back to queuing instead of assuming a live Player exists.
        boolean consolidate = wageredHands.size() > 1;
        List<HandOutcomeSummary> summaries = consolidate ? new ArrayList<>() : null;
        int handNumber = 0;
        for (BlackjackHand hand : wageredHands) {
            handNumber++;
            // A player natural blackjack pays 3:2 unless the dealer also
            // has a natural, in which case the main wager pushes -- see
            // BlackjackRules.classify and BlackjackRulesTest's both-natural
            // case. eligibleForNaturalBlackjack scopes split-21-is-blackjack
            // to exactly a two-card post-split 21 -- see BlackjackHand's doc.
            boolean eligibleForNatural = hand.eligibleForNaturalBlackjack(split21IsBlackjack);
            BlackjackOutcome outcome = BlackjackRules.classify(hand.getCards(), dealerHand, eligibleForNatural);
            double netAmount = settleHandOutcome(playerId, hand, outcome, !consolidate);
            if (consolidate) {
                summaries.add(new HandOutcomeSummary(handNumber, outcome, netAmount));
            }
        }
        if (consolidate) {
            sendSplitRoundSummary(playerId, summaries);
        }
    }

    // Reset game for the next round
    resetGame();
}

/**
 * Sends one consolidated chat message per hand a split player settled
 * this round (its outcome plus that hand's own net profit/loss), each its
 * own line, followed by a final total line -- instead of the wall of
 * separate per-hand messages and separate payout confirmations
 * {@link #settleHandOutcome}/{@link #payOut} would otherwise have sent.
 * A no-op for an offline recipient (nothing to message) or a NONE message
 * preference -- currency itself is already fully settled by the time this
 * runs regardless, this is presentation only.
 */
private void sendSplitRoundSummary(UUID playerId, List<HandOutcomeSummary> summaries) {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
        return;
    }
    if (plugin.getPreferences(playerId).getMessageSetting() == org.nc.nccasino.helpers.Preferences.MessageSetting.NONE) {
        return;
    }

    double total = 0.0;
    for (HandOutcomeSummary summary : summaries) {
        total += summary.netAmount();
        String line;
        switch (summary.outcome()) {
            case BLACKJACK:
                line = text(player, "blackjack.round-summary-hand-blackjack",
                    "number", summary.handNumber(),
                    "amount", plugin.formatWagerDisplay(currencyMode, currencyName, summary.netAmount()));
                break;
            case WIN:
                line = text(player, "blackjack.round-summary-hand-won",
                    "number", summary.handNumber(),
                    "amount", plugin.formatWagerDisplay(currencyMode, currencyName, summary.netAmount()));
                break;
            case LOSS:
                line = text(player, "blackjack.round-summary-hand-lost",
                    "number", summary.handNumber(),
                    "amount", plugin.formatWagerDisplay(currencyMode, currencyName, -summary.netAmount()));
                break;
            case BUST:
                line = text(player, "blackjack.round-summary-hand-busted",
                    "number", summary.handNumber(),
                    "amount", plugin.formatWagerDisplay(currencyMode, currencyName, -summary.netAmount()));
                break;
            case PUSH:
                line = text(player, "blackjack.round-summary-hand-push", "number", summary.handNumber());
                break;
            default:
                continue;
        }
        player.sendMessage(line);
    }
    String sign = total > 0 ? "+" : total < 0 ? "-" : "";
    player.sendMessage(text(player, "blackjack.round-summary-total",
        "amount", sign + plugin.formatWagerDisplay(currencyMode, currencyName, Math.abs(total))));
}

/**
 * Settles one hand's outcome: messaging/sounds/particles exactly as
 * before when the player is online, now driven by that hand's own wager
 * rather than the player's whole bet-slip total. Any currency owed
 * (BLACKJACK/WIN/PUSH) is always delivered live when possible or queued
 * as a durable {@link PendingPayout} otherwise -- an offline recipient
 * (no {@code Player} to message/animate for) still gets paid, just
 * without the online-only presentation.
 *
 * @param sendMessage false for a split player whose hands are being folded
 *        into one consolidated summary by {@link #sendSplitRoundSummary}
 *        instead -- sounds/particles/payout still happen exactly as
 *        normal either way, only the per-hand chat line (and, via {@link
 *        #payOut}, the separate payout confirmation) is suppressed.
 * @return this hand's own net result: profit for BLACKJACK/WIN, {@code
 *         -hand.getWager()} for LOSS/BUST, {@code 0} for PUSH.
 */
private double settleHandOutcome(UUID playerId, BlackjackHand hand, BlackjackOutcome outcome, boolean sendMessage) {
    Player player = Bukkit.getPlayer(playerId);
    boolean online = player != null && player.isOnline();
    switch (outcome) {
        case BLACKJACK: {
            if (online) {
                if (sendMessage) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
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
                }
                if (SoundHelper.getSoundSafely("ui.toast.challenge_complete", player) != null)player.playSound(player.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.MASTER, 1.0f,1.0f);
                player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            }
            double paid = payOut(playerId, hand.getWager(), outcome.getMultiplier(), sendMessage); // Pay out 2.5x for a blackjack
            return paid - hand.getWager();
        }
        case BUST: {
            if (online) {
                if (sendMessage) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
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
                }
                if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
            }
            return -hand.getWager();
        }
        case WIN: {
            if (online) {
                if (sendMessage) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
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
            }
            double paid = payOut(playerId, hand.getWager(), outcome.getMultiplier(), sendMessage); // Regular win pays out 2x
            return paid - hand.getWager();
        }
        case LOSS: {
            if (online) {
                if (sendMessage) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
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
                }
                if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.MASTER,1.0f,1.0f);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
            }
            return -hand.getWager();
        }
        case PUSH: {
            if (online) {
                if (sendMessage) {
                    switch(plugin.getPreferences(playerId).getMessageSetting()){
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
                }
            }
            boolean delivered = online && addWagerToInventory(player, hand.getWager());
            if (!delivered) {
                queueBlackjackPendingPayout(playerId, hand.getWager(), online
                    ? PayoutMessages.committedResultContext("Blackjack")
                    : PayoutMessages.disconnectedMidGameContext("Blackjack"));
            } else {
                if (SoundHelper.getSoundSafely("item.shield.break", player) != null)player.playSound(player.getLocation(),Sound.ITEM_SHIELD_BREAK,SoundCategory.MASTER,1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 20);
            }
            return 0.0;
        }
        default:
            // Never silently fall back to a push/refund for an outcome
            // this switch doesn't know about -- that's exactly the
            // mistake that would hide a bug once insurance/splitting
            // add new outcomes.
            throw new IllegalStateException("Unhandled BlackjackOutcome: " + outcome);
    }
}

/**
 * Pays out {@code multiplier}x a specific hand's own wager -- required by
 * per-hand payout so each of a player's simultaneously-resolved split
 * hands pays independently. Resolves the recipient by id rather than
 * requiring a live {@code Player}: an offline recipient, or a live Vault
 * deposit that Vault itself reports as failed, is durably queued as a
 * {@link PendingPayout} for the exact amount instead of ever being
 * dropped -- see {@link #queueBlackjackPendingPayout}. Vault math stays
 * in exact {@link java.math.BigDecimal} the whole way through, on both
 * the live-delivery and the queued-fallback path, so a queued 12.5/37.5
 * payout is never rounded away.
 *
 * @param sendMessage false for a split player whose "payout.paid"/
 *        "payout.paid-with-profit" confirmation is being folded into
 *        {@link #sendSplitRoundSummary} instead -- delivery/queueing
 *        happens identically either way, only this confirmation line
 *        (never the inventory-full drop warning, which stays important
 *        enough to always show) is suppressed.
 * @return the amount this hand's payout is worth (owed, whether delivered live or queued).
 */
private double payOut(UUID playerId, double totalBet, double multiplier, boolean sendMessage) {
    Player player = Bukkit.getPlayer(playerId);
    boolean online = player != null && player.isOnline();
    CurrencyProvider provider = getCurrencyProvider();

    if (provider != null && provider.getMode() == org.nc.nccasino.currency.CurrencyMode.VAULT && provider instanceof VaultCurrencyProvider vaultProvider) {
        java.math.BigDecimal betAmount = MoneyHelper.clampNonNegative(MoneyHelper.bd(totalBet));
        java.math.BigDecimal payout = betAmount.multiply(MoneyHelper.bd(multiplier));
        java.math.BigDecimal displayPayout = MoneyHelper.roundDisplay(payout);
        java.math.BigDecimal displayProfit = MoneyHelper.roundDisplay(payout.subtract(betAmount));

        boolean owesMoney = payout.compareTo(java.math.BigDecimal.ZERO) > 0;
        boolean delivered = !owesMoney || (online && vaultProvider.deposit(player, internalName, payout));

        if (owesMoney && !delivered) {
            queueBlackjackPendingPayout(playerId, payout.doubleValue(), online
                ? PayoutMessages.committedResultContext("Blackjack")
                : PayoutMessages.disconnectedMidGameContext("Blackjack"));
            return displayPayout.doubleValue();
        }
        if (!online) {
            return displayPayout.doubleValue();
        }

        if (sendMessage) {
            switch(plugin.getPreferences(playerId).getMessageSetting()){
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
        }
        return displayPayout.doubleValue();
    }

    double payout = totalBet * multiplier;
    int totalAmount = applyProbabilisticRounding(payout);

    if (!online) {
        if (totalAmount > 0) {
            queueBlackjackPendingPayout(playerId, totalAmount, PayoutMessages.disconnectedMidGameContext("Blackjack"));
        }
        return totalAmount;
    }

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
    if (sendMessage) {
        switch(plugin.getPreferences(playerId).getMessageSetting()){
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
    }

    // Print total dropped if any items couldn't fit in inventory -- always
    // shown regardless of sendMessage: this is an important warning about
    // where the player's items actually ended up, not routine result
    // spam, so it's never folded into the split-round summary.
    if (totalDropped > 0) {
        switch(plugin.getPreferences(playerId).getMessageSetting()){
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
    return totalAmount;
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
    // Captured before anything below clears it -- which currently-rendered
    // card slots (and which slot the deck token itself was resting on)
    // need to sweep back into the deck before the white-tile reset sweep
    // plays. See the deferred animateCardsReturnToDeck call at the very
    // end of this method.
    Map<Integer, Boolean> returningCardSlots = collectVisibleCardSlots();
    int deckSlotForReturn = dealerDeckTokenSlot != -1 ? dealerDeckTokenSlot : BlackjackSlotLayout.DECK_HOME_SLOT;

    gameActive = false;
    roundGeneration++;
    dealerSequenceToken++;
    handToken.clear();
    betSpotGlowSuppressed.clear(); // never leave a bet spot stuck un-glowed if a reset lands mid hand-to-hand transition
    // Table-wide event: any shared animation (dealer U-path, split
    // sequence) and every private one both end here -- the whole round is
    // over, not just one viewer's inventory closing.
    cancelAllAnimations();

    playerBets.clear();
    lastBetAmounts.clear();
    // Every committed pregame wager increment this round already had its
    // payout/refund calculated and delivered (or queued) by whichever
    // caller reached this point (finishGame's settlement, or
    // abortRoundForShoeExhaustion's refund, both run strictly before this
    // call) -- clearing it now is what stops Undo All/Undo Last from
    // refunding an already-settled round's wager a second time once
    // wagering reopens for the next round. Deliberately leaves selectedWager
    // alone -- a seated player's persistent wager selection (fixed
    // denomination or All In) survives into the next round exactly like
    // their seat does; see clearConsumedRoundWagerLedger's own doc.
    clearConsumedRoundWagerLedger();
    // This IS the "next genuine round boundary" the chair/wager guidance
    // completion flags are scoped to -- a player who stays seated keeps
    // their selection (above) so guidance stays naturally dormant for them
    // anyway, but one who left and reseated with no selection is now
    // eligible for guidance again this new round.
    chairGuidanceCompleted.clear();
    wagerGuidanceCompleted.clear();
    playerCardCounts.clear(); // Clear the card count map
    playerDone.clear(); // Clear the player status map
    playerHands.clear(); // Clear player hands
    activeHandIndex.clear();
    dealerHand.clear(); // Clear dealer's hand
    playerIterator = null;
    currentPlayerId = null;
    hiddenCardPlaceholderVisible = false;
    dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
    dealerDeckTokenSlot = -1;
    startTransitionActive = false;
    startTransitionDoorConcealComplete.clear();
    startTransitionSeatedSnapshot.clear();
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
        Player seatOwnerPlayer = Bukkit.getPlayer(playerId);
        ItemStack headItem = seatOwnerPlayer != null
                ? createPlayerHeadItem(seatOwnerPlayer, 1)
                : createPlayerHead(playerId, Bukkit.getOfflinePlayer(playerId).getName(), null);
        renderToAllViews(seatSlot, headItem);
    }

    // Cards sweep back into the deck first, then the white-tile sweep --
    // a no-op straight into the sweep when nothing was ever dealt this
    // round (an early abort, or a plain lobby reset).
    animateCardsReturnToDeck(returningCardSlots, deckSlotForReturn, roundGeneration, () -> startResetSweep(roundGeneration));
}

private int calculateHandValue(List<Card> hand) {
    return BlackjackRules.handValue(hand);
}

/**
 * The path a card dealt to {@code targetSlot} takes from wherever the deck
 * token currently sits (see {@link BlackjackCardFlightPlan}).
 *
 * <p>A dealer card can never safely take the normal row-first-then-drop
 * path (or any vertical-first detour -- see {@link BlackjackCardFlightPlan}'s
 * class doc for why) once the bottom seat's hand has grown far enough out
 * to either occupy the deck's own resting slot outright, or merely block
 * an intermediate slot along that row-first sweep: both would mean flying
 * through (and repainting over) a real card, or -- for any vertical drop --
 * the dealer's own head slot, which sits directly below the deck's column.
 * {@link BlackjackCardFlightPlan#dealerRowLegClear} detects either case, and
 * {@link BlackjackCardFlightPlan#dealerDoorPath} is the one fallback that's
 * always safe: it originates from the dealer's own row, right of the door,
 * never touching the deck's row or the head slot at all.
 */
private List<Integer> flightPathFromDeck(int targetSlot, boolean dealerCard) {
    int originSlot = dealerDeckTokenSlot != -1 ? dealerDeckTokenSlot : BlackjackSlotLayout.DECK_HOME_SLOT;
    if (dealerCard && !BlackjackCardFlightPlan.dealerRowLegClear(originSlot, targetSlot, this::isCardOccupiedSlot)) {
        return BlackjackCardFlightPlan.dealerDoorPath(targetSlot);
    }
    return BlackjackCardFlightPlan.path(originSlot, targetSlot, dealerCard);
}

/**
 * Renders a face-down card icon hopping along {@code path}, one hop every
 * {@link BlackjackTiming#CARD_FLIGHT_HOP_TICKS}, starting {@code baseDelay}
 * ticks from now -- every hop, including the landing one, renders
 * face-down; the caller is responsible for scheduling the real deal (which
 * paints the true face, i.e. the "flip") at its own separately-computed
 * landing tick, so the swap from this animation's own final frame to the
 * real card is what actually reads as a flip rather than a separate
 * reveal. The deck token itself is restored (not cleared to background)
 * wherever a hop departs from its own resting slot, since the deck is
 * still sitting there for the next card.
 */
private void scheduleCardFlightHops(List<Integer> path, long baseDelay, long myGeneration) {
    scheduleCardFlightHops(path, baseDelay, myGeneration, BlackjackTiming.CARD_FLIGHT_HOP_TICKS);
}

/** Same as {@link #scheduleCardFlightHops(List, long, long)}, but at a caller-chosen hop rate instead of the ordinary dealt-card {@link BlackjackTiming#CARD_FLIGHT_HOP_TICKS} -- see {@link BlackjackTiming#SPLIT_SLIDE_HOP_TICKS}'s own doc for why the split's own slides need their own, slower rate. */
private void scheduleCardFlightHops(List<Integer> path, long baseDelay, long myGeneration, long hopTicks) {
    for (int i = 1; i < path.size(); i++) {
        int previousSlot = path.get(i - 1);
        int hopSlot = path.get(i);
        long hopDelay = baseDelay + i * hopTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (roundGeneration != myGeneration) {
                return;
            }
            if (previousSlot == dealerDeckTokenSlot) {
                renderHiddenCardToAllViews(previousSlot);
            } else {
                renderBackgroundToAllViews(previousSlot);
            }
            renderHiddenCardToAllViews(hopSlot);
        }, hopDelay);
    }
}

/**
 * Initial-deal variant: the flight starts at {@code baseDelay} (the deal
 * plan's own per-card stagger) and the real deal is free to land whenever
 * the flight actually finishes -- see dealInitialCards, which reschedules
 * its own downstream (initial-blackjack-check) timing off this method's
 * return value rather than a fixed constant.
 *
 * @return the tick (relative to now) at which the flight lands and the
 *     card should flip -- {@code baseDelay} plus the path's travel time
 *     plus {@link BlackjackTiming#CARD_FLIP_DELAY_TICKS}
 */
private long scheduleCardFlight(int targetSlot, boolean dealerCard, long baseDelay, long myGeneration) {
    List<Integer> path = flightPathFromDeck(targetSlot, dealerCard);
    scheduleCardFlightHops(path, baseDelay, myGeneration);
    long flightTicks = (path.size() - 1) * BlackjackTiming.CARD_FLIGHT_HOP_TICKS;
    return baseDelay + flightTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
}

/**
 * Mid-game (hit/double-down) variant: unlike the initial deal, a hit's
 * real deal is scheduled on {@link BlackjackTiming#CARD_DEAL_DELAY_TICKS}
 * and its resulting hand value is read on the independently fixed {@link
 * BlackjackTiming#HIT_EVALUATION_DELAY_TICKS} -- both locked down by
 * BlackjackTimingTest and never safe to shift later. So instead the flight
 * is worked backwards from that fixed {@code dealDelayTicks} landing tick,
 * starting as late as it can while still finishing (hops + flip) exactly
 * when the real deal already fires -- purely additive visual polish in
 * front of unchanged data timing, never behind it.
 */
private void scheduleCardFlightEndingAt(int targetSlot, boolean dealerCard, long dealDelayTicks, long myGeneration) {
    scheduleCardFlightAlongPathEndingAt(flightPathFromDeck(targetSlot, dealerCard), dealDelayTicks, myGeneration);
}

/** Shared backward-scheduling math for any pre-built path -- see {@link #scheduleCardFlightEndingAt} for why the landing tick itself is never allowed to move. */
private void scheduleCardFlightAlongPathEndingAt(List<Integer> path, long dealDelayTicks, long myGeneration) {
    scheduleCardFlightAlongPathEndingAt(path, dealDelayTicks, myGeneration, BlackjackTiming.CARD_FLIGHT_HOP_TICKS);
}

/** Same as {@link #scheduleCardFlightAlongPathEndingAt(List, long, long)}, but at a caller-chosen hop rate -- see {@link #halvedIdleGapHopTicks} for the one caller that needs this. */
private void scheduleCardFlightAlongPathEndingAt(List<Integer> path, long dealDelayTicks, long myGeneration, long hopTicks) {
    long flightTicks = (path.size() - 1) * hopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
    long flightStart = Math.max(0L, dealDelayTicks - flightTicks);
    scheduleCardFlightHops(path, flightStart, myGeneration, hopTicks);
}

/**
 * The per-hop rate that makes a {@code hopCount}-hop flight -- landing at
 * a fixed {@code landingTick}, worked backward exactly the way {@link
 * #scheduleCardFlightAlongPathEndingAt} always does -- start visibly
 * moving about halfway between {@code priorPhaseTick} (when the previous
 * phase already landed) and wherever it would have started at the
 * ordinary {@link BlackjackTiming#CARD_FLIGHT_HOP_TICKS} rate, instead of
 * leaving that whole stretch as idle screen time. Never faster than the
 * ordinary rate (the floor is {@code CARD_FLIGHT_HOP_TICKS} itself) --
 * this only ever slows a flight down to fill more of an otherwise-idle
 * gap, never speeds one up past normal.
 *
 * <p>Computed per-call rather than as a flat constant deliberately: how
 * far a flight actually has to travel (and therefore how much slower it
 * needs to go to fill half the gap) varies with the target's distance
 * from the deck -- a flat rate calibrated for a short flight would, for a
 * long one, overshoot and start moving before the prior phase has even
 * finished.
 */
private long halvedIdleGapHopTicks(int hopCount, long priorPhaseTick, long landingTick) {
    if (hopCount <= 0) {
        return BlackjackTiming.CARD_FLIGHT_HOP_TICKS;
    }
    long ordinaryFlightTicks = hopCount * BlackjackTiming.CARD_FLIGHT_HOP_TICKS + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
    long ordinaryStart = Math.max(0L, landingTick - ordinaryFlightTicks);
    long idleGap = Math.max(0L, ordinaryStart - priorPhaseTick);
    long desiredStart = priorPhaseTick + idleGap / 2;
    long desiredFlightTicks = landingTick - desiredStart;
    long hopTicks = (desiredFlightTicks - BlackjackTiming.CARD_FLIP_DELAY_TICKS) / hopCount;
    return Math.max(BlackjackTiming.CARD_FLIGHT_HOP_TICKS, hopTicks);
}

/**
 * D's own per-hop rate, tuned two steps past the ordinary rate: first
 * {@link #halvedIdleGapHopTicks} (already-halved idle gap against a fixed
 * landing tick), then ~25% faster still on top of that. Never below the
 * ordinary {@link BlackjackTiming#CARD_FLIGHT_HOP_TICKS} floor. Must be
 * paired with {@link #fasterSiblingCardLandingTick} -- the landing tick
 * this rate assumes is no longer the fixed {@code ordinaryLandingTick}
 * passed in here, since holding the landing tick fixed while also going
 * faster isn't possible (a faster flight is a shorter one, which starts
 * later against a fixed landing tick, not earlier).
 */
private long fasterSiblingCardHopTicks(int hopCount, long priorPhaseTick, long ordinaryLandingTick) {
    long previousHopTicks = halvedIdleGapHopTicks(hopCount, priorPhaseTick, ordinaryLandingTick);
    return Math.max(BlackjackTiming.CARD_FLIGHT_HOP_TICKS, previousHopTicks * 3 / 4);
}

/**
 * The new (earlier) landing tick D's flight -- and everything chained
 * after it (phase 3's own data/reveal, then phase 4, then phase 5) --
 * must move to, given {@link #fasterSiblingCardHopTicks}'s own faster
 * rate and an idle gap ~20% smaller than {@link #halvedIdleGapHopTicks}'s
 * already-halved one. See {@link #fasterSiblingCardHopTicks}'s own doc
 * for why the landing tick has to move at all.
 */
private long fasterSiblingCardLandingTick(int hopCount, long priorPhaseTick, long ordinaryLandingTick) {
    if (hopCount <= 0) {
        return ordinaryLandingTick;
    }
    long ordinaryFlightTicks = hopCount * BlackjackTiming.CARD_FLIGHT_HOP_TICKS + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
    long ordinaryIdle = Math.max(0L, (ordinaryLandingTick - ordinaryFlightTicks) - priorPhaseTick);
    long previousIdle = ordinaryIdle / 2;
    long newIdle = previousIdle * 4 / 5;
    long hopTicks = fasterSiblingCardHopTicks(hopCount, priorPhaseTick, ordinaryLandingTick);
    return priorPhaseTick + newIdle + hopCount * hopTicks + BlackjackTiming.CARD_FLIP_DELAY_TICKS;
}

/** Whether {@code slot} currently shows a real dealt card (either face color) -- used only to steer the split zigzag path around real cards, never background/decorative items. */
private boolean isCardOccupiedSlot(int slot) {
    ItemStack item = inventory.getItem(slot);
    if (item == null) {
        return false;
    }
    Material type = item.getType();
    return type == Material.RED_STAINED_GLASS_PANE || type == Material.BLACK_STAINED_GLASS_PANE;
}

/**
 * Card C's own flight path (the split's original hand's replacement card)
 * -- unlike every other dealt card, its target (the seat's own card index
 * 1) sits in the very row where B is already resting at its temp slot
 * (index 3) by the time C deals in, so the plain up-then-left flight would
 * cut straight through it. Detours through the row below the target
 * (toward the deck) instead: up into that row, left across it -- as far as
 * it can go before actually running into a real card there -- then up into
 * the target row, finishing with one last short left if it got blocked
 * before reaching the target column outright.
 *
 * <p>The bottom seat never reaches here -- its own row already IS the
 * deck's row, so "the row below" doesn't exist. It's handled entirely
 * separately (see {@link #runSplitAnimation} and {@link
 * #bottomSeatSplitDashPath}) with a different visual strategy altogether,
 * not a variant of this one.
 */
private List<Integer> splitOriginalCardFlightPath(int targetSlot) {
    int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
    int deckSlot = dealerDeckTokenSlot != -1 ? dealerDeckTokenSlot : BlackjackSlotLayout.DECK_HOME_SLOT;
    int targetRow = targetSlot / width;
    int targetCol = targetSlot % width;
    int deckCol = deckSlot % width;
    int detourRow = targetRow + 1;

    List<Integer> path = new ArrayList<>();
    path.add(deckSlot);
    path.add(detourRow * width + deckCol); // up into the detour row

    int col = deckCol;
    while (col > targetCol && !isCardOccupiedSlot(detourRow * width + (col - 1))) {
        col--;
        path.add(detourRow * width + col);
    }

    path.add(targetRow * width + col); // up into the target row, at whichever column the sweep actually reached
    while (col > targetCol) {
        col--;
        path.add(targetRow * width + col); // blocked partway -- finish left within the target row, already past B's temp slot by now
    }

    return path;
}

/**
 * ONLY for the bottom seat's split -- C's own pre-positioning dash, run
 * (see {@link #runSplitAnimation}) before B's slide-out even begins, not a
 * clever route around anything. Left along the bottom seat's own row --
 * still entirely empty out here, since B hasn't moved yet -- down into the
 * dealer's row at the column directly left of the hole card (always one of
 * its own empty not-yet-dealt hit cells; never the up-card, the hole card
 * itself, or the head slot), then left the rest of the way to directly
 * beneath the target slot. Ends parked there; {@link #runSplitAnimation}
 * schedules B's own slide-out only after this dash finishes, then gives C
 * one final hop straight up into the target slot once B has vacated it.
 *
 * @return the path ending at the parked staging slot, directly beneath {@code targetSlot} -- not {@code targetSlot} itself
 */
private List<Integer> bottomSeatSplitDashPath(int targetSlot) {
    int width = BlackjackSlotLayout.SEAT_ROW_WIDTH;
    int deckSlot = dealerDeckTokenSlot != -1 ? dealerDeckTokenSlot : BlackjackSlotLayout.DECK_HOME_SLOT;
    int targetRow = targetSlot / width;
    int targetCol = targetSlot % width;
    int deckCol = deckSlot % width;
    int dealerRow = targetRow + 1;
    int dropCol = BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT % width - 1; // directly left of the hole card

    List<Integer> path = new ArrayList<>();
    path.add(deckSlot);

    int col = deckCol;
    while (col > dropCol) {
        col--;
        path.add(targetRow * width + col); // left, along the bottom seat's own row -- stops well short of B's own slot
    }

    path.add(dealerRow * width + col); // down, into the dealer's row, directly left of the hole card -- always an empty not-yet-dealt hit cell

    while (col > targetCol) {
        col--;
        path.add(dealerRow * width + col); // left, still within the dealer's row, until it's in the correct column
    }

    return path;
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

/**
 * Renders one lore line per hand in {@code playerId}'s own hand queue
 * (every hand, if split -- not only the currently active one), each as
 * "cards-total" via {@link BlackjackRules#formatHandCardsAndTotal}. A hand
 * with no cards yet (including split's not-yet-dealt-into sibling)
 * contributes no line at all -- never a placeholder {@code "0"}.
 */
private void updatePlayerHead(UUID playerId) {
    if (!playerBets.containsKey(playerId) || playerBets.get(playerId).isEmpty() || playerSeats.get(playerId) == null) {
        return; // Skip updating if the player hasn't placed a bet
    }

    List<BlackjackHand> hands = playerHands.getOrDefault(playerId, List.of());
    List<String> handTexts = new ArrayList<>();
    for (BlackjackHand hand : hands) {
        String text = BlackjackRules.formatHandCardsAndTotal(hand.getCards());
        if (text != null) {
            handTexts.add(text);
        }
    }

    int seatSlot = playerSeats.get(playerId);
    renderHeadLoreToAllViews(seatSlot, handTexts, Bukkit.getOfflinePlayer(playerId).getName(), null);
}

private void updateDealerHead() {
    String handText = BlackjackRules.formatHandCardsAndTotal(dealerHand);
    renderHeadLoreToAllViews(dealerHeadSlot, handText != null ? List.of(handText) : List.of(), null, "blackjack.dealer");
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

    // Only ever the bottom seat's own last visible card slot (the one seat
    // row that shares a column with the deck's resting slot) -- a real card
    // has now permanently taken that cell, so the deck token is gone for
    // the rest of the round; any later flight passing through here must
    // clear to plain background like any other transit slot, never
    // resummon the deck icon over this real card.
    if (slot == dealerDeckTokenSlot) {
        dealerDeckTokenSlot = -1;
    }
}


/**
 * Tears this controller down completely -- reached whenever a dealer is
 * administratively reloaded, replaced, or removed (see
 * Nccasino#deleteAssociatedInventories/#reloadDealer), and from
 * DealerInventory#cleanupAll on plugin shutdown. Economically safe: every
 * seated player's full stake for whatever round is in progress (committed
 * pregame wager, every split/double-down debit, any still-undetermined
 * insurance stake) is refunded or durably queued <em>before</em> any of
 * that state is cleared -- see the refund loop below. A selected-but-
 * uncommitted wager is correctly never refunded, since it was never
 * debited in the first place (totalRoundRefundForPlayer never reads
 * selectedWager). Idempotent: a duplicate call (this instance already
 * deleted) is a safe, logged no-op rather than a double refund or an NPE
 * against already-cleared state.
 */
public void delete() {
    if (inventory == null) {
        plugin.getLogger().warning("[NCCasino] Blackjack delete() called again for dealer " + dealerId
            + " after it was already deleted -- ignoring the duplicate teardown.");
        return;
    }

    // Refund every seated player's complete stake for the round in
    // progress -- delivered live when the credit actually confirms,
    // durably queued otherwise (offline recipient, or a failed live Vault
    // deposit), exactly like abortRoundAndRefund/refundPendingBets already
    // do for their own teardown reasons. This must run before cancelGame()
    // below, which is what actually clears playerHands/playerBets/
    // pregameWagerIncrements/insuranceStakes -- refunding after that point
    // would have nothing left to read.
    for (UUID seatedPlayerId : new ArrayList<>(playerSeats.keySet())) {
        refundRoundDebit(seatedPlayerId, totalRoundRefundForPlayer(seatedPlayerId), "blackjack.table-reset-refunded");
    }

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
    dealerDeckTokenSlot = -1;
    startTransitionActive = false;
    startTransitionDoorConcealComplete.clear();
    startTransitionSeatedSnapshot.clear();
    stopInsurancePhaseBookkeeping();
    roundGeneration++;
    dealerSequenceToken++;
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
        // Same reasoning as resetGame()'s identical capture -- a table
        // emptying out mid-round (the last seated player leaves/is kicked)
        // can still have real cards on the board, and they deserve the same
        // sweep-back-into-the-deck treatment as a genuinely finished round.
        Map<Integer, Boolean> returningCardSlots = collectVisibleCardSlots();
        int deckSlotForReturn = dealerDeckTokenSlot != -1 ? dealerDeckTokenSlot : BlackjackSlotLayout.DECK_HOME_SLOT;

        gameActive = false;
        roundGeneration++;
        dealerSequenceToken++;
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
        // Deliberately NOT chairGuidanceCompleted/wagerGuidanceCompleted --
        // cancelGame() also runs from an ordinary single-player pregame
        // leave (removePlayerData, once playerSeats empties), which must
        // never un-complete that very player's own guidance for the round
        // they're still (from their perspective) part of. Only a genuine
        // round reset (resetGame) clears those.
        hiddenCardPlaceholderVisible = false;
        dealerHeadSlot = BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT;
        dealerDeckTokenSlot = -1;
        startTransitionActive = false;
        startTransitionDoorConcealComplete.clear();
        startTransitionSeatedSnapshot.clear();
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

        animateCardsReturnToDeck(returningCardSlots, deckSlotForReturn, roundGeneration, () -> startResetSweep(roundGeneration));
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
     * {@code gameActive} alone would still leave a real gap though: a
     * player who already committed a wager during the countdown or the
     * start-transition window (door-conceal + shared dealer inspection,
     * before {@code activateGame} flips {@code gameActive}) closing right
     * then would still be refunded-and-removed rather than riding into the
     * round they already paid for -- {@code hasCommittedWager} below closes
     * that gap without affecting a merely-seated, never-bet spectator, who
     * must still free their seat immediately on close (see
     * GameTerminationPolicy#blackjack's own doc).
     */
    @Override
    public void onSessionTerminated(UUID playerId, ExitReason reason) {
        if (!playerSeats.containsKey(playerId)) {
            // Already resolved through another path (e.g. a voluntary
            // leave-chair click that ran before this).
            return;
        }

        boolean hasCommittedWager = totalRoundRefundForPlayer(playerId) > 0;
        org.nc.nccasino.session.TerminationAction action =
            GameTerminationPolicy.blackjack(reason, gameActive, hasCommittedWager);
        if (action == org.nc.nccasino.session.TerminationAction.FORFEIT) {
            // Only ever reached for a kick now (see GameTerminationPolicy.blackjack) —
            // a kicked player forfeits unconditionally regardless of phase.
            // Never calculate an automated hand or create a pending payout.
            // Advance the turn safely first if it was theirs.
            if (playerId.equals(currentPlayerId)) {
                playerDone.put(playerId, true);
                playerTurnActive.put(playerId, false);
                bumpHandToken(playerId);
                advanceTurnNow();
            }
            removePlayerData(playerId);
        } else if (action == org.nc.nccasino.session.TerminationAction.REFUND) {
            // Reached only when nothing is actually at stake yet (no
            // committed wager, pre-deal) -- refund is a safe no-op in that
            // case, and the seat is freed immediately rather than held for
            // a spectator who never bet. Also reached unconditionally on
            // PLUGIN_DISABLE, which can never ride through a restart.
            refundPendingBets(playerId, reason);
            removePlayerData(playerId);
        } else if (action == org.nc.nccasino.session.TerminationAction.RIDE_TO_RESULT) {
            // Either the deal has begun, or a wager is already committed
            // for the countdown/start-transition round about to deal --
            // either way real money is on the line, so leave the seat,
            // hand, wager, and turn state completely untouched. The
            // mandatory Action Timer (and, during insurance, the insurance
            // timer) resolves any decision on schedule regardless of
            // presence, exactly like Roulette/Baccarat/CoinFlip/
            // RockPaperScissors already do; a pregame/start-transition
            // wager simply rides into the deal like an online player's
            // would, once cards are dealt. Settlement already queues a
            // PendingPayout if they're still offline when it pays out (see
            // queueBlackjackPendingPayout and its call sites). Re-register
            // with SessionRegistry (mirroring Roulette's RIDE_TO_RESULT
            // handling) so a later PLUGIN_DISABLE while still riding still
            // resolves correctly -- delete() also already iterates
            // playerSeats directly as a second safety net regardless.
            SessionRegistry.register(playerId, this);
            // onViewClosed already unconditionally called
            // cancelPrivateAnimation(playerId) for this view closing (a
            // deliberate, necessary cancellation for chair/wager guidance,
            // bet-spot blink, etc.) -- but that also bumps this viewer's
            // animation generation, which invalidates an in-flight
            // start-transition door-conceal's own scheduled stepping
            // (startDoorConceal/requestWagerBarPosition are guarded by that
            // same per-viewer generation), freezing it mid-slide. Left
            // alone, isReadyToDeal's gate -- which still requires this
            // player's conceal to finish, since they're (correctly) still
            // seated under RIDE_TO_RESULT -- would stall until the
            // bounded-poll fallback aborts the whole round. There is no
            // view to animate the slide for anymore, so there's nothing to
            // gain by resuming it tick-by-tick either -- jump the position
            // straight to CLOSED instead. Harmless: bootstrapView/
            // syncWagerBarPositionToCanonicalResting resets this from live
            // canonical state on any future reopen, and the pregame wager
            // bar isn't shown at all once gameActive anyway.
            if (startTransitionActive && !startTransitionDoorConcealComplete.contains(playerId)) {
                wagerBarPosition.put(playerId, BlackjackWagerRevealPlan.CLOSED);
                wagerBarTarget.put(playerId, BlackjackWagerRevealPlan.CLOSED);
                startTransitionDoorConcealComplete.add(playerId);
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendRideToResultMessage(player, playerId);
            }
        } else {
            removePlayerData(playerId);
        }

    }

    /**
     * Sent to a player spared from forfeiture (RIDE_TO_RESULT) who's still
     * online -- explains that nothing was taken from them and what happens
     * if they don't come back in time. Same STANDARD+VERBOSE-fire,
     * NONE-suppress tier as blackjack.wager-selected/blackjack.insurance-taken,
     * since a running timer against the player is more consequential than a
     * routine confirmation message.
     */
    private void sendRideToResultMessage(Player player, UUID playerId) {
        String key;
        Object[] placeholders;
        if (insurancePhaseActive && insuranceEligiblePlayers.contains(playerId) && !insuranceDecided.contains(playerId)) {
            key = "blackjack.closed-during-insurance";
            placeholders = new Object[]{"seconds", insuranceSecondsRemaining};
        } else if (playerId.equals(currentPlayerId)) {
            key = "blackjack.closed-during-turn";
            placeholders = new Object[]{"seconds", turnTimerSecondsRemaining};
        } else if (playerDone.getOrDefault(playerId, false)) {
            // Their own turn (every hand, if split) already resolved --
            // nothing left to return "before", and no timer is running
            // against them at all. Just riding to the result now.
            return;
        } else {
            key = "blackjack.closed-before-turn";
            placeholders = new Object[0];
        }
        switch (plugin.getPreferences(playerId).getMessageSetting()) {
            case NONE:
                break;
            default:
                player.sendMessage(text(player, key, placeholders));
        }
    }

    /**
     * Refunds everything {@code playerId} currently has at stake this round
     * -- the original wager, plus (once cards are down) every split and
     * double-down debit, plus any still-undetermined insurance stake, via
     * {@link #totalRoundRefundForPlayer}. Called before removePlayerData,
     * which clears the underlying bet/hand records without moving any
     * currency itself. Only reached for {@link org.nc.nccasino.session.TerminationAction#REFUND}
     * (never for a kick or an active-game voluntary leave, both of which
     * forfeit unconditionally and never call this at all -- see
     * onSessionTerminated).
     */
    private void refundPendingBets(UUID playerId, ExitReason reason) {
        double total = totalRoundRefundForPlayer(playerId);
        if (total <= 0) {
            return;
        }

        if (reason == ExitReason.PLUGIN_DISABLE) {
            // The server is shutting down -- never hand items/deposit
            // directly here regardless of online state, always queue.
            queuePendingRefund(playerId, total);
        } else {
            refundRoundDebit(playerId, total, null);
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

    // ---- Test-only accessors (package-private; zero production callers) ---
    // A narrow, explicit seam for BlackjackControllerTestSupport and the
    // controller-level integration tests in this same package -- exposes
    // just enough read access and a few of this class's own already-private
    // lifecycle/trigger methods to drive and observe genuine controller
    // behavior, rather than duplicating this class's logic in a parallel
    // test simulation. Nothing here is reachable from production code
    // outside this package.

    int playerSeatsSizeForTest() {
        return playerSeats.size();
    }

    Set<UUID> seatedPlayerIdsForTest() {
        return new HashSet<>(playerSeats.keySet());
    }

    boolean isSeatedForTest(UUID playerId) {
        return playerSeats.containsKey(playerId);
    }

    boolean isGameActiveForTest() {
        return gameActive;
    }

    boolean isStartTransitionActiveForTest() {
        return startTransitionActive;
    }

    int countdownSecondsRemainingForTest() {
        return countdownSecondsRemaining;
    }

    /** @return this player's live hand queue (canonical, depth-first split order), or an empty list if they have none. */
    List<BlackjackHand> playerHandsForTest(UUID playerId) {
        return playerHands.getOrDefault(playerId, List.of());
    }

    /** @return the dealer's current canonical slot -- {@link BlackjackSlotLayout#DEALER_LOBBY_HEAD_SLOT} until the start-transition inspection delivers it to {@link BlackjackSlotLayout#DEALER_INPLAY_HEAD_SLOT}. */
    int dealerHeadSlotForTest() {
        return dealerHeadSlot;
    }

    /** @return the deck-flight path C (the split's original-hand replacement card) would take to {@code targetSlot} against current live board state -- see {@link #splitOriginalCardFlightPath}. */
    List<Integer> splitOriginalCardFlightPathForTest(int targetSlot) {
        return splitOriginalCardFlightPath(targetSlot);
    }

    List<Integer> bottomSeatSplitDashPathForTest(int targetSlot) {
        return bottomSeatSplitDashPath(targetSlot);
    }

    long halvedIdleGapHopTicksForTest(int hopCount, long priorPhaseTick, long landingTick) {
        return halvedIdleGapHopTicks(hopCount, priorPhaseTick, landingTick);
    }

    long fasterSiblingCardHopTicksForTest(int hopCount, long priorPhaseTick, long ordinaryLandingTick) {
        return fasterSiblingCardHopTicks(hopCount, priorPhaseTick, ordinaryLandingTick);
    }

    long fasterSiblingCardLandingTickForTest(int hopCount, long priorPhaseTick, long ordinaryLandingTick) {
        return fasterSiblingCardLandingTick(hopCount, priorPhaseTick, ordinaryLandingTick);
    }

    /** Directly deals {@code card} to {@code playerId} at {@code slot}, bypassing any flight/scheduling -- test setup only, for putting real cards on the board without playing out a whole round. */
    void dealCardToPlayerForTest(int slot, Card card, UUID playerId) {
        dealCardToPlayer(slot, card, playerId);
    }

    /** @return whether a shared/table-owned animation (the dealer inspection, the split sequence, or the reset sweep) is currently running. */
    boolean hasSharedAnimationForTest() {
        return sharedAnimationRun != null;
    }

    /** @return the currently-running shared animation's own tagged phase (see {@link BlackjackAnimationRun#getExpectedPhase()}), or null if none is running. */
    BlackjackFrame.Phase sharedAnimationPhaseForTest() {
        return sharedAnimationRun == null ? null : sharedAnimationRun.getExpectedPhase();
    }

    /** @return this player's active hand's card count -- 0 if they have no hand at all (e.g. never committed a wager this round). */
    int activeHandCardCountForTest(UUID playerId) {
        return activeHandCards(playerId).size();
    }

    /** @return this player's current active-hand index within their own hand queue (0 if unsplit or not seated). */
    int activeHandIndexForTest(UUID playerId) {
        return activeHandIndex.getOrDefault(playerId, 0);
    }

    int turnTimerSecondsRemainingForTest() {
        return turnTimerSecondsRemaining;
    }

    UUID turnTimerPlayerIdForTest() {
        return turnTimerPlayerId;
    }

    int turnTimerTaskIdForTest() {
        return turnTimerTaskId;
    }

    UUID currentPlayerIdForTest() {
        return currentPlayerId;
    }

    BlackjackFrame.Phase capturePhaseForTest() {
        return capturePhase();
    }

    double totalRoundRefundForPlayerForTest(UUID playerId) {
        return totalRoundRefundForPlayer(playerId);
    }

    void stackDeckForTest(List<Card> cardsInDealOrder) {
        deck.stackForTest(cardsInDealOrder);
    }

    /** Commits {@code amount} as {@code player}'s pregame wager via the real commitWager path -- test setup only; {@code player} must already be seated. */
    WagerCommitResult commitWagerForTest(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        Integer seatSlot = playerSeats.get(playerId);
        if (seatSlot == null) {
            throw new IllegalStateException("commitWagerForTest requires an already-seated player");
        }
        int betSpotSlot = BlackjackSlotLayout.betSlipSlot(seatSlot);
        return commitWager(player, playerId, betSpotSlot, amount);
    }

    void beginStartTransitionForTest() {
        beginStartTransition();
    }

    /**
     * Forces the exact same shoe-exhaustion/readiness-gate abort-and-refund
     * path production reaches from {@code abortRoundForShoeExhaustion}/the
     * start-transition readiness gate, at any point a test needs it --
     * including mid-insurance-decision, which neither of those two real
     * triggers can naturally reach (shoe exhaustion only occurs during
     * turn-based play, well after insurance has already resolved and
     * cleared its own stakes; the readiness gate only fires before any card
     * is even dealt). Test setup only -- see {@code abortRoundAndRefund}'s
     * own doc for what this actually refunds.
     */
    void abortRoundAndRefundForTest(String messageKey) {
        abortRoundAndRefund(messageKey);
    }

    /**
     * Drives the exact genuine-round-boundary reset production code reaches
     * from finishGame/abortRoundForShoeExhaustion, without needing to play
     * an entire round of card-dealing/turns/dealer-play through the
     * scheduler just to exercise round-reset lifecycle behavior (e.g.
     * whether a persistent wager selection or a guidance-completion flag
     * survives it). Test setup only.
     */
    void resetGameForTest() {
        resetGame();
    }

    /**
     * Simulates the exact "unexpected seat mutation" isReadyToDeal's
     * snapshot-based defense is meant to survive: a seat that entered
     * {@code startTransitionSeatedSnapshot}/{@code playerSeats} without
     * ever being scheduled a door-conceal sequence (since that only
     * happens inside beginStartTransition's own loop, already finished by
     * the time this runs) -- so it can never complete and, absent the
     * bounded-poll fallback in scheduleDealReadinessCheck, would stall the
     * readiness gate forever. Only reachable from this package's own
     * integration tests.
     */
    void forceUnsatisfiableReadinessForTest(UUID stuckPlayerId, int seatSlot) {
        playerSeats.put(stuckPlayerId, seatSlot);
        startTransitionSeatedSnapshot.add(stuckPlayerId);
    }

    double insuranceStakeForTest(UUID playerId) {
        return insuranceStakes.getOrDefault(playerId, 0.0);
    }

    /** @return the player's exact stored insurance offer, or null if none is currently open for them. */
    Double insuranceOfferedCostForTest(UUID playerId) {
        return insuranceOfferedCost.get(playerId);
    }

    /** @return this table's configured insurance decision duration in seconds -- {@code insurance.timeout-seconds}, defaulting to {@link BlackjackTiming#INSURANCE_TIMEOUT_DEFAULT_SECONDS}. */
    int insuranceTimeoutSecondsForTest() {
        return insuranceTimeoutSeconds;
    }

    /** @return the player's current wager-selection tool, or null if they haven't selected one. */
    BlackjackWagerSelection selectedWagerForTest(UUID playerId) {
        return selectedWager.get(playerId);
    }

    boolean isChairGuidanceCompletedForTest(UUID playerId) {
        return chairGuidanceCompleted.contains(playerId);
    }

    boolean isWagerGuidanceCompletedForTest(UUID playerId) {
        return wagerGuidanceCompleted.contains(playerId);
    }

    boolean hasPrivateAnimationForTest(UUID playerId) {
        return privateAnimationRuns.containsKey(playerId);
    }

    /** @return this player's live {@link BlackjackView}, so a test can force a genuine close/reopen (see {@link #onViewClosed}/{@link #getOrCreateView}) instead of only exercising the incremental refresh path. */
    BlackjackView viewForTest(UUID playerId) {
        return views.get(playerId);
    }

    /** @return this player's live wager bar slide position (0-8, see {@link BlackjackWagerRevealPlan#CLOSED}/{@link BlackjackWagerRevealPlan#OPEN}), defaulting to their canonical resting frame if nothing is tracked yet. */
    int wagerBarPositionForTest(UUID playerId) {
        return currentWagerBarPosition(playerId);
    }

    /** @return this player's current wager bar slide target, or null if none has ever been requested. */
    Integer wagerBarTargetForTest(UUID playerId) {
        return wagerBarTarget.get(playerId);
    }

}
