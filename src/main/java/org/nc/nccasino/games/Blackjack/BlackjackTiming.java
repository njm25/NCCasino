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
    public static final long DEALER_INSPECTION_STEP_TICKS = 5L;
    /** Ticks between one diagonal and the next joining the game-reset white-tile sweep's wavefront. */
    public static final long RESET_SWEEP_STEP_TICKS = 2L;
    /** How many diagonals' worth of ticks a reset-sweep tile stays white before revealing the board underneath again. */
    public static final long RESET_SWEEP_HOLD_DIAGONALS = 5L;
    /** How long the whole currently-available action-item set stays glowing (or plain) per phase before flipping to the other. */
    public static final long ACTION_GUIDANCE_STEP_TICKS = 5L;
    /** Delay between successive steps of the split slide-out/park/reactivate sequence. */
    public static final long SPLIT_ANIMATION_STEP_TICKS = 10L;

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
