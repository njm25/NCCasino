package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The dealer's pregame shuffle flourish: once the dealer's own walk-down
 * animation has already landed it (and the deck token trailing one row
 * above it) at their in-play resting slots, the deck slides from there to
 * a temporary spot near the center of the board, a handful of face-down
 * cards stream out of it and race around the full perimeter of the board's
 * playable field before diving back in, and the deck then slides back to
 * its ordinary resting slot before dealing begins.
 *
 * <p>Pure data only -- zero Bukkit types -- so every path/timing/collision
 * property is unit-testable without a running server, mirroring {@link
 * BlackjackTableEntrancePlan}'s own approach. The controller
 * ({@code BlackjackInventory}) owns turning a tick's {@link #frameAt}
 * result into real {@code ItemStack}s, scheduling the deck's own two travel
 * legs (see {@link #DECK_TO_CENTER_PATH}/{@link #CENTER_TO_DECK_PATH}, plain
 * slot sequences a caller turns into ordinary MOVE steps the same way
 * {@link BlackjackDealerInspectionPlan} already does), and sequencing the
 * whole thing as a single shared (table-wide, not per-viewer) animation.
 *
 * <h2>Geometry</h2>
 * <p>The board's playable field for this animation is rows 0-4, columns
 * 2-8. Column 0 belongs to the five seat/player-head icons and column 1 is
 * their permanent brown bet-spot boundary, so neither is ever part of a
 * shuffle path. {@link #CENTER_SLOT} (23 -- row 2, column 5, 0-indexed) is
 * the deck's temporary shuffle position.
 * Every card leaves the deck heading straight down to the field's bottom
 * edge (row 4), then splits by parity into two streams that trace the
 * playable field's full perimeter in opposite directions -- {@link
 * CardDirection#LEFT} sweeps left along the bottom edge, climbs the left
 * edge, then sweeps right across the top edge; {@link CardDirection#RIGHT}
 * mirrors it the other way -- both converging back at the same slot in the
 * top edge directly above the deck, then filing straight back down into it.
 * With the real left boundary at column 2, the center column is equidistant
 * from both edges and the two streams take matching 14-hop routes. The
 * class's test suite verifies that they still interleave collision-free at
 * the shared merge point, rather than merely assuming symmetry makes that
 * safe.
 */
public final class BlackjackShuffleAnimationPlan {

    private BlackjackShuffleAnimationPlan() {
    }

    /** The deck's temporary shuffle position -- row 2, column 5 (0-indexed) of the board. */
    public static final int CENTER_SLOT = 23;

    /** The deck's own travel from its ordinary resting slot ({@link BlackjackSlotLayout#DECK_HOME_SLOT}) to {@link #CENTER_SLOT}, before any card starts moving. Turn into ordinary MOVE steps the same way {@link BlackjackDealerInspectionPlan#build} does. */
    public static final List<Integer> DECK_TO_CENTER_PATH = List.of(44, 35, 26, 25, 24, 23);
    /** The exact reverse of {@link #DECK_TO_CENTER_PATH} -- the deck's own travel back to {@link BlackjackSlotLayout#DECK_HOME_SLOT}, only after every card has fully landed back at {@link #CENTER_SLOT}. */
    public static final List<Integer> CENTER_TO_DECK_PATH = List.of(23, 24, 25, 26, 35, 44);

    /** The single slot -- directly above the deck's temporary position, in the field's top edge -- both directions converge on before filing back into the deck. */
    private static final int MERGE_SLOT = 5;

    public enum CardDirection { LEFT, RIGHT }

    private static final List<Integer> LEFT_PATH = List.of(
        23, 32, 41, // down to the bottom edge
        40, 39, 38, // left along the bottom edge, stopping inside the brown boundary
        29, 20, 11, 2, // up column 2 -- never overwrite bet spots or player heads
        3, 4, MERGE_SLOT, // right along the top edge to the merge slot
        14, 23 // straight back down into the deck
    );
    private static final List<Integer> RIGHT_PATH = List.of(
        23, 32, 41, // down to the bottom edge (identical prefix to LEFT_PATH)
        42, 43, 44, // right along the bottom edge
        35, 26, 17, 8, // up the right edge
        7, 6, MERGE_SLOT, // left along the top edge to the merge slot
        14, 23 // straight back down into the deck
    );

    /** One card's own journey: which direction it took, its full path (deck, out, around, back to the deck), and the tick it launched at. */
    public static final class CardPiece {
        private final CardDirection direction;
        private final List<Integer> path;
        private final long launchTick;

        CardPiece(CardDirection direction, List<Integer> path, long launchTick) {
            this.direction = direction;
            this.path = path;
            this.launchTick = launchTick;
        }

        public CardDirection getDirection() {
            return direction;
        }

        public List<Integer> getPath() {
            return path;
        }

        public long getLaunchTick() {
            return launchTick;
        }

        public int getHopCount() {
            return path.size() - 1;
        }

        public long landingTick(long hopTicks) {
            return launchTick + (long) getHopCount() * hopTicks;
        }

        /**
         * The slot this card occupies at {@code tick}, or -1 if {@code tick}
         * is before its launch OR at/after it has fully landed back at
         * {@link #CENTER_SLOT} -- unlike {@link
         * BlackjackTableEntrancePlan.Piece#slotAt}, a shuffle card doesn't
         * rest visibly forever once "landed": it's absorbed back into the
         * deck token, which is a single fixed item the controller already
         * renders on its own, not a per-card slot this class needs to keep
         * claiming.
         */
        public int slotAt(long tick, long hopTicks) {
            if (tick < launchTick) {
                return -1;
            }
            long elapsedHops = (tick - launchTick) / hopTicks;
            if (elapsedHops >= getHopCount()) {
                return -1; // absorbed back into the deck -- no longer its own visible piece
            }
            return path.get((int) elapsedHops);
        }
    }

    /**
     * Builds {@code cardCount} cards' worth of the shuffle, alternating
     * {@link CardDirection#LEFT} and {@link CardDirection#RIGHT} starting
     * with LEFT, each launched {@code cardLaunchStaggerTicks} after the
     * previous one -- regardless of which direction either takes, matching
     * {@link BlackjackTableEntrancePlan}'s own proven "hop ticks x2 stagger
     * gives a genuine one-empty-slot gap" pacing.
     *
     * @param cardCount how many cards stream out -- even split rounds down
     *        (an odd count gives LEFT one extra, since it starts first)
     */
    public static List<CardPiece> build(int cardCount, long hopTicks, long cardLaunchStaggerTicks) {
        if (cardCount <= 0) {
            throw new IllegalArgumentException("cardCount must be positive");
        }
        if (hopTicks <= 0 || cardLaunchStaggerTicks <= 0) {
            throw new IllegalArgumentException("hopTicks and cardLaunchStaggerTicks must both be positive");
        }
        List<CardPiece> cards = new ArrayList<>(cardCount);
        for (int i = 0; i < cardCount; i++) {
            CardDirection direction = i % 2 == 0 ? CardDirection.LEFT : CardDirection.RIGHT;
            List<Integer> path = direction == CardDirection.LEFT ? LEFT_PATH : RIGHT_PATH;
            long launch = (long) i * cardLaunchStaggerTicks;
            cards.add(new CardPiece(direction, path, launch));
        }
        return cards;
    }

    /**
     * Every tick at which some card's occupied slot changes -- each card's
     * launch and hop ticks, <em>including</em> its landing/absorption tick.
     * That final empty frame is load-bearing: without it, the last card's
     * previous slot remains visibly face-down until some unrelated later
     * render happens to clear it.
     */
    public static List<Long> distinctTicks(List<CardPiece> cards, long hopTicks) {
        TreeSet<Long> ticks = new TreeSet<>();
        for (CardPiece card : cards) {
            long landing = card.landingTick(hopTicks);
            for (long t = card.getLaunchTick(); t <= landing; t += hopTicks) {
                ticks.add(t);
            }
        }
        return new ArrayList<>(ticks);
    }

    /** The complete, deterministic occupancy snapshot at {@code tick}: every slot currently occupied by some still-in-flight card, mapped to its direction. A card already absorbed back into the deck by this tick never appears here. */
    public static Map<Integer, CardDirection> frameAt(List<CardPiece> cards, long tick, long hopTicks) {
        Map<Integer, CardDirection> occupied = new HashMap<>();
        for (CardPiece card : cards) {
            int slot = card.slotAt(tick, hopTicks);
            if (slot != -1) {
                occupied.put(slot, card.getDirection());
            }
        }
        return occupied;
    }

    /** The tick the very last card is fully absorbed back into the deck -- callers must derive the card phase's own duration from this, never a separately-added constant. */
    public static long totalDurationTicks(List<CardPiece> cards, long hopTicks) {
        long max = 0L;
        for (CardPiece card : cards) {
            max = Math.max(max, card.landingTick(hopTicks));
        }
        return max;
    }

    /**
     * Every tick+slot at which two different cards would occupy the same
     * slot at the same time -- empty if the plan is fully collision-free.
     * Brute-force, tick-by-tick and card-by-card; used only by tests.
     */
    public static List<String> findCollisions(List<CardPiece> cards, long hopTicks) {
        List<String> collisions = new ArrayList<>();
        long total = totalDurationTicks(cards, hopTicks);
        for (long t = 0; t <= total; t++) {
            Map<Integer, CardPiece> occupantAt = new HashMap<>();
            for (CardPiece card : cards) {
                int slot = card.slotAt(t, hopTicks);
                if (slot == -1) {
                    continue;
                }
                CardPiece existing = occupantAt.get(slot);
                if (existing != null && existing != card) {
                    collisions.add("tick " + t + " slot " + slot + ": " + existing.getDirection() + "@" + existing.getLaunchTick()
                        + " vs " + card.getDirection() + "@" + card.getLaunchTick());
                }
                occupantAt.put(slot, card);
            }
        }
        return collisions;
    }
}
