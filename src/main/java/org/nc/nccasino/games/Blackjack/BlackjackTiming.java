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
    public static final long CHAIR_GUIDANCE_STEP_TICKS = 20L;
    /** How long the whole applicable wager-control set stays glowing (or plain) per phase before flipping to the other. */
    public static final long WAGER_GUIDANCE_STEP_TICKS = 20L;
    /** How long a bet spot's "click to add" blink stays on per pulse. */
    public static final long BET_SPOT_BLINK_STEP_TICKS = 20L;
    /** Ticks between successive toggles of the round-end WIN bet-spot reveal flash -- deliberately fast/flickery, a quick flourish rather than a slow guidance-style blink. Keeps toggling for the whole round-end animation, not a fixed count -- see BlackjackInventory#startRoundResultFlash. */
    public static final long ROUND_RESULT_FLASH_STEP_TICKS = 2L;

    // ---- Table entrance ("dealer builds the table," pregame only) --------

    /** Per-slot travel time for every piece in {@link BlackjackTableEntrancePlan} -- one tick/hop, same fast rate as an ordinary dealt card's own flight ({@link #CARD_FLIGHT_HOP_TICKS}). Left untouched when the whole entrance was slowed ~30% (see {@link #TABLE_ENTRANCE_LAUNCH_STAGGER_TICKS}) -- a single tick is already the finest possible per-hop granularity, so the slowdown is carried entirely by widening the launch gap instead. */
    public static final long TABLE_ENTRANCE_HOP_TICKS = 1L;
    /**
     * How many ticks apart successive same-stream launches are (chair-to-
     * chair, pane-to-pane), deepest target first. A freshly-launched
     * follower starts a full {@link #TABLE_ENTRANCE_HOP_TICKS} behind where
     * its leader already is by then, so the whole thing still reads as
     * "busy, overlapping" without two pieces ever sharing a slot -- just
     * retuned from an original 2x {@link #TABLE_ENTRANCE_HOP_TICKS} (a
     * literal one-empty-slot gap) up to 3x, which -- combined with the door/
     * edge-glass pieces added to each stream's deep end -- reads about 30%
     * slower overall (total duration 17 ticks -> 22 ticks at 1-tick hops).
     */
    public static final long TABLE_ENTRANCE_LAUNCH_STAGGER_TICKS = TABLE_ENTRANCE_HOP_TICKS * 3;
    /** How long the entrance's own "whoosh" (see BlackjackInventory#playTableEntranceWhoosh) is allowed to play before a hard stopSound cuts it off -- trims the sound's own tail/reverb rather than letting it linger past the fast pacing it's layered over. Retune by ear. */
    public static final long TABLE_ENTRANCE_WHOOSH_CUTOFF_TICKS = 4L;
    /** Delay before the entrance's whoosh burst starts, relative to the entrance's own start -- played perfectly synchronously with bootstrapView, the first one audibly overlapped the client's own inventory-open transition sound. Retune by ear. */
    public static final long TABLE_ENTRANCE_WHOOSH_START_DELAY_TICKS = 3L;
    /** Ticks between successive whooshes in the entrance's rapid-fire burst (one per SoundCategory, ten total) -- fast, matching the visual's own busy pacing rather than isolated beats. Retune by ear. */
    public static final long TABLE_ENTRANCE_WHOOSH_RAPID_FIRE_STAGGER_TICKS = 1L;
    /** Volume of each entrance whoosh -- 20% quieter than the original 0.6f. Retune by ear. */
    public static final float TABLE_ENTRANCE_WHOOSH_VOLUME = 0.48f;
    /** Center pitch of the entrance's whoosh burst -- the very first whoosh's own pitch, already confirmed to sound right. Each of the ten burst instances jitters randomly around this rather than using it exactly, see {@link #TABLE_ENTRANCE_WHOOSH_PITCH_JITTER}. */
    public static final float TABLE_ENTRANCE_WHOOSH_BASE_PITCH = 1.0f;
    /** Max random pitch deviation (plus or minus) from {@link #TABLE_ENTRANCE_WHOOSH_BASE_PITCH} for each burst instance -- small on purpose, "very close" per feedback, not spread across a wide range. Retune by ear. */
    public static final float TABLE_ENTRANCE_WHOOSH_PITCH_JITTER = 0.08f;

    // ---- Pregame shuffle flourish (shared, plays once the dealer's own
    // walk-down animation has already landed it in-play) -------------------

    /** Per-slot travel time for the deck token and every card in {@link BlackjackShuffleAnimationPlan} -- the same 1-tick floor the table entrance uses. */
    public static final long SHUFFLE_HOP_TICKS = 1L;
    /** Ticks apart successive cards launch from the deck, regardless of which direction either takes -- twice {@link #SHUFFLE_HOP_TICKS}, the same "genuine one-empty-slot gap" pacing proven out by the table entrance. */
    public static final long SHUFFLE_CARD_LAUNCH_STAGGER_TICKS = SHUFFLE_HOP_TICKS * 2;
    /** How many cards stream out during the shuffle -- picked so the whole round trip (deck out, cards, deck back) lands close to a ~3-second/60-tick "super fast" target with real gaps between cards, not a card count chosen for its own sake. Retune together with the timing constants above if either changes. */
    public static final int SHUFFLE_CARD_COUNT = 16;
    /** The brief pause between the deck arriving at the shuffle's center slot and the first card starting to move -- lets the deck's own arrival actually read before the cards take over. Retune by ear. */
    public static final long SHUFFLE_START_PAUSE_TICKS = 3L;
    /** Hard cutoff for every shuffle bat-whoosh burst, trimming the sample's lingering tail exactly like the proven table-entrance sound. */
    public static final long SHUFFLE_WHOOSH_CUTOFF_TICKS = 4L;
    /** Densest smooth Bukkit cadence: one new clipped bat beat every server tick throughout the complete shuffle. */
    public static final long SHUFFLE_WHOOSH_BEAT_TICKS = 1L;
    /** Volume of each shared shuffle whoosh. */
    public static final float SHUFFLE_WHOOSH_VOLUME = 0.48f;
    /** Center pitch for the shuffle burst. */
    public static final float SHUFFLE_WHOOSH_BASE_PITCH = 1.0f;
    /** Small random pitch variation that keeps the repeated sample from sounding mechanically identical. */
    public static final float SHUFFLE_WHOOSH_PITCH_JITTER = 0.08f;

    /** Same reveal flash for LOSS/PUSH, but at half the WIN rate -- a calmer flicker for the non-win outcomes. */
    public static final long ROUND_RESULT_FLASH_STEP_TICKS_SLOW = ROUND_RESULT_FLASH_STEP_TICKS * 2;
    /**
     * Ticks per frame of the wager bar's solid slide (reveal on sit, conceal
     * on unsit) -- one frame per tick, so the full 8-frame endpoint-to-
     * endpoint slide (see BlackjackWagerRevealPlan#CLOSED/OPEN) takes about
     * 8 ticks. Deliberately fast/solid rather than the old one-slot-per-
     * WAGER_REVEAL_STEP_TICKS reveal this replaced (previously 4 ticks/slot,
     * 36 ticks total).
     */
    public static final long WAGER_REVEAL_STEP_TICKS = 1L;
    /** Quiet single scrape played once when the wager bar genuinely starts moving, matching Roulette's ball-scraping grindstone texture. */
    public static final float WAGER_SLIDE_SOUND_VOLUME = 0.20f;
    /** Higher/lighter grindstone pitch for the wager bar opening toward {@link BlackjackWagerRevealPlan#OPEN}. */
    public static final float WAGER_SLIDE_OPEN_PITCH = 1.30f;
    /** Lower/heavier grindstone pitch for the wager bar closing toward {@link BlackjackWagerRevealPlan#CLOSED}. */
    public static final float WAGER_SLIDE_CLOSE_PITCH = 0.80f;
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
    /**
     * Per-slot travel time of the round-end return-to-deck animation
     * specifically (see {@code BlackjackInventory#animateCardsReturnToDeck}) --
     * kept as its own constant rather than reusing {@link
     * #CARD_FLIGHT_HOP_TICKS} directly, so this specific moment can be
     * retuned without touching every other card flight in the game
     * (initial deal, hits, splits, etc.), which all still use the ordinary
     * rate. Repeatedly retuned by feel: 3x the ordinary rate, then 8x (too
     * fast, then too slow), then 6x (still too slow) -- now a literal ~30%
     * faster than that 6x value (6 ticks/hop) rather than another named
     * multiplier, since the multiplier framing stopped being meaningful
     * once retuning started moving in both directions.
     */
    public static final long RETURN_TO_DECK_HOP_TICKS = 5L;
    /**
     * The round-end animation's own pause between the round actually
     * ending and any card starting to move -- retuned by feel the same way
     * as {@link #RETURN_TO_DECK_HOP_TICKS}: last at 6x {@link
     * #CARD_FLIP_DELAY_TICKS} (36 ticks), which read as too long a gap --
     * shrunk by ~60% from there.
     */
    public static final long RETURN_TO_DECK_START_PAUSE_TICKS = 14L;
    /** How long the whole currently-available action-item set stays glowing (or plain) per phase before flipping to the other. */
    public static final long ACTION_GUIDANCE_STEP_TICKS = 20L;
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
     * How long C sits parked, face-down, two slots below its final
     * destination (directly beneath {@link BlackjackSlotLayout#playerCardSlot}'s
     * target column, in the dealer's row) before hopping up into it -- see
     * {@code BlackjackInventory#runSplitAnimation}'s bottom-seat branch.
     * The pause read as immediate at a shorter gap, so this is deliberately
     * generous; scheduling C's own final hop this far out pushes phase 2
     * (and everything chained after it) later to make room, only for the
     * bottom seat.
     */
    public static final long BOTTOM_SEAT_DASH_PARK_PAUSE_TICKS = 10L;
    /**
     * Delay between successive steps of the split's own park sequence --
     * the inactive sibling hand sliding one step left into the gap, then
     * tucking away behind the active hand ("hand 2 slipping under hand 1",
     * see {@code BlackjackInventory#runSplitAnimation}'s phase 4-6 doc).
     * Kept as its own constant rather than reusing {@link
     * #SPLIT_ANIMATION_STEP_TICKS} directly so this specific park-away
     * moment can be retuned (it read as too slow, then still too slow even
     * after halving) without touching phase 2/3's own timing, which D's
     * flight-pacing math (see {@code fasterSiblingCardHopTicks}/{@code
     * fasterSiblingCardLandingTick}) is built around and isn't safe to
     * shrink independently. First halved from 18 to 9, then cranked
     * roughly another 25% faster to 7, then still read as too slow --
     * cranked again to 4.
     */
    public static final long SPLIT_PARK_STEP_TICKS = 4L;
    /**
     * Extra pause folded into the delay between D pairing up beside temp-B
     * (phase 3 landing) and phase 4 -- the [B][D] pair's own first visible
     * step left, "hand 2 starting to slide under hand 1" -- on top of the
     * ordinary {@link #SPLIT_PARK_STEP_TICKS}/2 gap every other phase
     * transition here uses. That gap alone read as too abrupt right after
     * C/D finish pairing up; half a second (10 ticks) more lets that
     * moment actually read before the park-away begins.
     */
    public static final long SPLIT_PARK_PRE_SLIDE_EXTRA_PAUSE_TICKS = 10L;

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
