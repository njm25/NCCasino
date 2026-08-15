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

    // ---- Animation infrastructure (Phase 2) ------------------------------
    // Per-animation step delays for the pure Blackjack*Plan classes. These
    // are new (not preserved-inline) values -- unlike the constants above,
    // there is no legacy inline literal they need to match; picked as
    // reasonable defaults for a first pass and free to retune once the
    // animations are actually wired into gameplay (a later phase).

    /** Chair guidance begins exactly 2 seconds after a viewer opens the table, per the table redesign plan. */
    public static final long CHAIR_GUIDANCE_START_DELAY_TICKS = 40L;
    /** How long each empty seat's chair-guidance glow stays on before moving to the next. */
    public static final long CHAIR_GUIDANCE_STEP_TICKS = 20L;
    /** How long each chip slot's wager-guidance glow stays on before moving to the next. */
    public static final long WAGER_GUIDANCE_STEP_TICKS = 20L;
    /** How long a bet spot's "click to add" blink stays on per pulse. */
    public static final long BET_SPOT_BLINK_STEP_TICKS = 20L;
    /** Delay between successive slots as the wager bar reveals/conceals. */
    public static final long WAGER_REVEAL_STEP_TICKS = 4L;
    /** Normal per-slot travel time along the dealer's start-transition U-path. */
    public static final long DEALER_INSPECTION_STEP_TICKS = 5L;
    /** Extra time added on top of the base step when the dealer inspects a seat with a committed wager. */
    public static final long DEALER_INSPECTION_SLOWDOWN_EXTRA_TICKS = 15L;
    /** How long each available action's guidance glow stays on before moving to the next. */
    public static final long ACTION_GUIDANCE_STEP_TICKS = 20L;
    /** Delay between successive steps of the split slide-out/park/reactivate sequence. */
    public static final long SPLIT_ANIMATION_STEP_TICKS = 10L;

    /** Default insurance decision timeout, in seconds, before it auto-resolves to No. */
    public static final int INSURANCE_TIMEOUT_DEFAULT_SECONDS = 10;

    // ---- Start transition (Phase 4) ---------------------------------------

    /**
     * Poll interval for the readiness gate between the start-transition
     * animations finishing and the initial deal actually beginning -- a
     * small runTaskLater retry loop bounded by roundGeneration, not a
     * busy-loop, per the table redesign plan.
     */
    public static final long START_TRANSITION_READINESS_POLL_TICKS = 5L;
}
