package org.nc.nccasino.games.Blackjack;

/**
 * Named scheduler delays (in ticks) used by BlackjackInventory's turn
 * sequencing. Extracted so the ordering relationship between them -- in
 * particular the double-down race documented on
 * BlackjackInventory#handleDoubleDown and characterized in
 * BlackjackTimingTest -- is backed by the literal constants production
 * actually schedules with, not copies of them.
 *
 * These values are preserved exactly as they were inline; changing them
 * changes real gameplay pacing and is out of scope here.
 */
public final class BlackjackTiming {

    private BlackjackTiming() {
    }

    /** Delay before a hit card visually lands after being scheduled. */
    public static final long CARD_DEAL_DELAY_TICKS = 20L;

    /**
     * Delay after a hit before its resulting hand value is evaluated --
     * long enough to assume the card has landed (twice the deal delay).
     */
    public static final long HIT_EVALUATION_DELAY_TICKS = 40L;

    /** Delay before advancing to the next player's turn after a stand or a double-down. */
    public static final long TURN_ADVANCE_DELAY_TICKS = 20L;

    // ---- Animation infrastructure -----------------------------------------
    // Per-animation step delays for the pure Blackjack*Plan classes. These
    // are new (not preserved-inline) values -- unlike the constants above,
    // there is no legacy inline literal they need to match; free to retune
    // for feel once these animations get real playtesting.

    /** Chair guidance begins exactly 2 seconds after a viewer opens the table, per the table redesign plan. */
    public static final long CHAIR_GUIDANCE_START_DELAY_TICKS = 40L;
    /** How long the whole applicable-chair set stays glowing (or plain) per phase before flipping to the other. */
    public static final long CHAIR_GUIDANCE_STEP_TICKS = 5L;
    /** How long the whole applicable wager-control set stays glowing (or plain) per phase before flipping to the other. */
    public static final long WAGER_GUIDANCE_STEP_TICKS = 5L;
    /** How long a bet spot's "click to add" blink stays on per pulse. */
    public static final long BET_SPOT_BLINK_STEP_TICKS = 20L;
    /**
     * Ticks per frame of the wager bar's solid slide (reveal on sit, conceal
     * on unsit) -- one frame per tick, so the full 8-frame endpoint-to-
     * endpoint slide (see BlackjackWagerRevealPlan#CLOSED/OPEN) takes about
     * 8 ticks. Deliberately fast/solid rather than the old one-slot-per-
     * WAGER_REVEAL_STEP_TICKS reveal this replaced (previously 4 ticks/slot,
     * 36 ticks total).
     */
    public static final long WAGER_REVEAL_STEP_TICKS = 1L;
    /**
     * Ticks per frame of the Blackjack settings menu's collapsible-section
     * slide (see {@code BlackjackMenu#layoutMenuAnimated}) -- a parent
     * toggle's dependent entries appearing/disappearing slide the
     * remaining entries into place one slot per tick, the same
     * one-frame-per-tick feel as {@link #WAGER_REVEAL_STEP_TICKS}, instead
     * of an instant jump-cut relayout.
     */
    public static final long MENU_RELAYOUT_STEP_TICKS = 1L;
    /** Per-slot travel time along the dealer's start-transition slide -- uniform for every leg, no per-seat pauses. */
    public static final long DEALER_INSPECTION_STEP_TICKS = 2L;
    /** Per-slot travel time of a dealt card's flight from the deck token to its slot -- deliberately faster than {@link #DEALER_INSPECTION_STEP_TICKS}. */
    public static final long CARD_FLIGHT_HOP_TICKS = 1L;
    /**
     * During the initial deal, how far ahead of the previous card's own
     * landing tick the next card is allowed to start its flight -- a brief
     * head start so consecutive cards' flights just barely overlap, rather
     * than each one fully landing before the next departs. Small on
     * purpose: this is a slight overlap, not concurrent free-for-all
     * flights (see {@code dealInitialCards}'s per-step scheduling, which
     * derives each step's actual start from the previous step's landing
     * tick minus this).
     */
    public static final long INITIAL_DEAL_OVERLAP_TICKS = 4L;
    /** Delay after a card's flight lands before it flips from face-down to its real face. */
    public static final long CARD_FLIP_DELAY_TICKS = 6L;
    /** Ticks between one diagonal and the next joining the game-reset white-tile sweep's wavefront. */
    public static final long RESET_SWEEP_STEP_TICKS = 1L;
    /** How many diagonals' worth of ticks a reset-sweep tile stays white before revealing the board underneath again. */
    public static final long RESET_SWEEP_HOLD_DIAGONALS = 3L;
    /** How long the whole currently-available action-item set stays glowing (or plain) per phase before flipping to the other. */
    public static final long ACTION_GUIDANCE_STEP_TICKS = 5L;
    /** Delay between successive steps of the split slide-out/park/reactivate sequence -- wide enough for C's/D's own deck-flight to comfortably land in sync (see BlackjackInventory#runSplitAnimation). */
    public static final long SPLIT_ANIMATION_STEP_TICKS = 18L;
    /**
     * Per-hop speed of the split's own slide animations -- B's slide-out
     * and (for the bottom seat) C's own pre-positioning dash -- deliberately
     * slower than the ordinary {@link #CARD_FLIGHT_HOP_TICKS} dealt-card
     * rate. Kept as its own constant, not just reusing {@link
     * #CARD_FLIGHT_HOP_TICKS}, so these two purely-presentational slides can
     * be paced to actually read as sliding motion against {@link
     * #SPLIT_ANIMATION_STEP_TICKS}'s own now-slower phase gaps, without
     * touching the speed of every other card dealt anywhere else in the game.
     */
    public static final long SPLIT_SLIDE_HOP_TICKS = 2L;
    /** Per-hop speed of the bottom seat's own split-C dash (see {@code BlackjackInventory#bottomSeatSplitDashPath}) -- deliberately faster than {@link #SPLIT_SLIDE_HOP_TICKS}, since the dash is a longer multi-hop trip and doesn't need to read as deliberately as B's own (much shorter) slide. */
    public static final long BOTTOM_SEAT_DASH_HOP_TICKS = 2L;
    /**
     * Delay between successive steps of the split's own park sequence --
     * the inactive sibling hand sliding one step left into the gap, then
     * tucking away behind the active hand ("hand 2 slipping under hand 1",
     * see {@code BlackjackInventory#runSplitAnimation}'s phase 4-6 doc).
     * Half of {@link #SPLIT_ANIMATION_STEP_TICKS} -- kept as its own
     * constant rather than reusing that one directly so this specific
     * park-away moment can be retuned (it read as too slow) without
     * touching phase 2/3's own timing, which D's flight-pacing math (see
     * {@code fasterSiblingCardHopTicks}/{@code fasterSiblingCardLandingTick})
     * is built around and isn't safe to shrink independently.
     */
    public static final long SPLIT_PARK_STEP_TICKS = 9L;

    // ---- Hand-to-hand transition (a finished split hand handing control
    // to the next one in the queue) -----------------------------------

    /** Pause after a hand finishes before its own collapse-and-reveal transition begins -- "tiny, about half a second". */
    public static final long HAND_TRANSITION_PAUSE_TICKS = 10L;
    /**
     * Per-step pacing of the finished hand's own collapse down to just its
     * first and last card (middle cards vanishing, then the last card
     * sliding into the second slot) and of the next hand's out-and-back
     * reveal slide -- 25% slower than the original 2-tick pace (2 * 1.25,
     * rounded to the nearest whole tick) since that read as too fast.
     */
    public static final long HAND_TRANSITION_STEP_TICKS = 3L;

    /** Default insurance decision timeout, in seconds, before it auto-resolves to No. */
    public static final int INSURANCE_TIMEOUT_DEFAULT_SECONDS = 25;

    // ---- Start transition (Phase 4) ---------------------------------------

    /**
     * Poll interval for the readiness gate between the start-transition
     * animations finishing and the initial deal actually beginning -- a
     * small runTaskLater retry loop bounded by roundGeneration, not a
     * busy-loop, per the table redesign plan.
     */
    public static final long START_TRANSITION_READINESS_POLL_TICKS = 5L;

    /**
     * Hard cap on the number of readiness-gate polls before giving up and
     * safely aborting the round instead of polling forever -- defense in
     * depth against a stuck animation or an unexpected seat mutation
     * leaving {@code isReadyToDeal} permanently unsatisfiable. At the
     * default {@link #START_TRANSITION_READINESS_POLL_TICKS} interval this
     * is 120 * 5 = 600 ticks (30 seconds), comfortably longer than any
     * legitimate door-conceal/dealer-inspection sequence, which is fixed
     * and short by construction.
     */
    public static final int START_TRANSITION_READINESS_MAX_POLLS = 120;
}
