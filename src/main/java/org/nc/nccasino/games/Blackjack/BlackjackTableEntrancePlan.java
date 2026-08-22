package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The "dealer builds the table" pregame entrance: five chairs and five brown
 * bet-spot panes, each sent from a shared emergence point through a fixed
 * corridor to its own final seat/bet-spot slot, one inventory slot per hop,
 * with successive pieces in the same stream launched a fixed stagger apart
 * so several are always in flight together (a busy, overlapping, slightly
 * sporadic look, per the table redesign's entrance-animation brief -- never
 * "finish one row, pause, start the next").
 *
 * <p>Pure data only -- zero Bukkit types -- so every path/timing/collision
 * property is unit-testable without a running server. The controller
 * ({@code BlackjackInventory}) owns turning a tick's {@link #frameAt} result
 * into real {@code ItemStack}s and scheduling the actual Bukkit tasks.
 *
 * <h2>Geometry</h2>
 * <ul>
 *   <li><b>Chairs</b> (plus the door) emerge at {@link #CHAIR_EMERGE_SLOT}
 *       (7, immediately left of the dealer's lobby slot 8), travel left
 *       across the whole top row (7,6,5,4,3,2,1,0), then, unless the target
 *       is slot 0 itself, turn downward through the seat column (0,9,18,27,
 *       36) and, for the door alone, one row further still (45, one row
 *       below seat 36) only as far as needed. Dispatched deepest-target-
 *       first ({@link #DOOR_TARGET}, 36, 27, 18, 9, 0) so an already-parked
 *       piece never blocks a later traveler still passing through its
 *       resting slot.</li>
 *   <li><b>Bet-spot panes</b> (plus the bottom-bar edge glass) emerge at
 *       {@link #PANE_EMERGE_SLOT} (17, directly below the dealer), travel
 *       down the dealer's own column (17,26,35,44) only as far as needed,
 *       then branch left into their own row down to the target column; the
 *       edge glass alone continues one row further still (46, one row below
 *       pane 37). Also dispatched deepest-first ({@link #EDGE_GLASS_TARGET},
 *       37, 28, 19, 10).</li>
 *   <li>The one pane targeting slot 1 -- immediately right of seat 0, in the
 *       top row -- physically reuses the chair stream's own corridor
 *       (7,6,5,4,3,2,1) rather than the pane corridor, so it is scheduled as
 *       a special final shot, launched the minimum delay after the last
 *       (shallowest-target, latest-launched) chair has vacated slot 7: see
 *       {@link #build} for the exact derivation.</li>
 * </ul>
 *
 * <p>These two streams never geometrically overlap (chairs live in column 0
 * and the top row; panes live in column 8 and their own branch rows, columns
 * 1-8) except for that one special pane's reuse of the top row, which is why
 * it alone needs explicit sequencing against the chair stream -- every other
 * ordering property (within a stream, deepest-first with a fixed launch
 * stagger) is what keeps the rest collision-free, verified exhaustively by
 * this class's own test suite rather than asserted by argument alone.
 */
public final class BlackjackTableEntrancePlan {

    private BlackjackTableEntrancePlan() {
    }

    /** Where every chair emerges from -- immediately left of the dealer's lobby head slot (8). */
    public static final int CHAIR_EMERGE_SLOT = 7;
    /** Where every bottom-corridor bet-spot pane emerges from -- directly below the dealer. */
    public static final int PANE_EMERGE_SLOT = 17;

    /** The door, at the bottom of the seat column -- one row deeper than seat 36, so it's the deepest (and first-dispatched) member of the chair stream. */
    public static final int DOOR_TARGET = 45;
    /** The bottom-bar brown edge glass, at the bottom of the bet-spot column -- one row deeper than pane 37, so it's the deepest (and first-dispatched) member of the bottom-corridor pane stream. */
    public static final int EDGE_GLASS_TARGET = 46;

    /** Chair-stream targets, deepest first -- the dispatch order {@link #build} launches them in. The door (45) is one row deeper than seat 36, so it leads. */
    private static final int[] CHAIR_TARGET_ORDER = {DOOR_TARGET, 36, 27, 18, 9, 0};
    /** Bottom-corridor pane-stream targets, deepest first -- the dispatch order {@link #build} launches them in. The edge glass (46) is one row deeper than pane 37, so it leads. */
    private static final int[] PANE_BOTTOM_TARGET_ORDER = {EDGE_GLASS_TARGET, 37, 28, 19, 10};
    /** The one pane that reuses the chair stream's top-row corridor instead of the pane corridor -- its own launch tick is a natural second "whoosh" beat for the controller, see BlackjackInventory#startTableEntrance. */
    public static final int SPECIAL_PANE_TARGET = 1;

    public enum PieceKind { CHAIR, PANE, DOOR }

    /** One moving piece: its kind, final resting slot, full travel path (emergence slot through target slot inclusive), and the tick it launches at. */
    public static final class Piece {
        private final PieceKind kind;
        private final int targetSlot;
        private final List<Integer> path;
        private final long launchTick;

        Piece(PieceKind kind, int targetSlot, List<Integer> path, long launchTick) {
            this.kind = kind;
            this.targetSlot = targetSlot;
            this.path = List.copyOf(path);
            this.launchTick = launchTick;
        }

        public PieceKind getKind() {
            return kind;
        }

        public int getTargetSlot() {
            return targetSlot;
        }

        /** The full sequence of slots this piece visits, in travel order, first entry the emergence slot and last entry {@link #getTargetSlot()}. */
        public List<Integer> getPath() {
            return path;
        }

        public long getLaunchTick() {
            return launchTick;
        }

        /** How many hops (slot-to-slot moves) this piece's path takes -- {@code path.size() - 1}. */
        public int getHopCount() {
            return path.size() - 1;
        }

        /** The tick this piece first occupies {@link #getTargetSlot()} -- it rests there for every tick after, too. */
        public long landingTick(long hopTicks) {
            return launchTick + (long) getHopCount() * hopTicks;
        }

        /**
         * The slot this piece occupies at {@code tick}, or -1 if {@code tick}
         * is strictly before its launch (not yet visible anywhere). Once
         * landed ({@code tick >= landingTick(hopTicks)}), always returns
         * {@link #getTargetSlot()} -- it rests there permanently, exactly
         * like the real chair/pane item the controller paints there from
         * that tick on.
         */
        public int slotAt(long tick, long hopTicks) {
            if (tick < launchTick) {
                return -1;
            }
            long elapsedHops = (tick - launchTick) / hopTicks;
            int index = (int) Math.min(elapsedHops, getHopCount());
            return path.get(index);
        }
    }

    /**
     * Builds the full twelve-piece entrance choreography: five chairs plus
     * the door, five bet-spot panes plus the bottom-bar edge glass (five of
     * those six sharing the bottom/dealer-column corridor, one reusing the
     * chair stream's top-row corridor as a special final shot).
     *
     * @param hopTicks how many ticks each single-slot hop takes, uniform
     *        across every piece
     * @param launchStaggerTicks how many ticks apart successive same-stream
     *        launches are, deepest-target first
     */
    public static List<Piece> build(long hopTicks, long launchStaggerTicks) {
        if (hopTicks <= 0 || launchStaggerTicks <= 0) {
            throw new IllegalArgumentException("hopTicks and launchStaggerTicks must both be positive");
        }
        List<Piece> pieces = new ArrayList<>();

        for (int i = 0; i < CHAIR_TARGET_ORDER.length; i++) {
            int target = CHAIR_TARGET_ORDER[i];
            long launch = i * launchStaggerTicks;
            PieceKind kind = target == DOOR_TARGET ? PieceKind.DOOR : PieceKind.CHAIR;
            pieces.add(new Piece(kind, target, chairPath(target), launch));
        }

        for (int i = 0; i < PANE_BOTTOM_TARGET_ORDER.length; i++) {
            int target = PANE_BOTTOM_TARGET_ORDER[i];
            long launch = i * launchStaggerTicks;
            pieces.add(new Piece(PieceKind.PANE, target, panePath(target), launch));
        }

        // The special pane must never be at slot 7 (or any top-row slot) at
        // the same tick as any chair. The last chair to ever touch the top
        // row is the last-launched one (target 0, launched at
        // (CHAIR_TARGET_ORDER.length-1)*launchStaggerTicks) -- every other
        // chair, launched earlier, has already moved on by the time it gets
        // there (deepest-first ordering guarantees this). That last chair
        // occupies slot 7 only during its own launch tick, vacating it one
        // hop later -- so launching the special pane exactly one hop after
        // that chair's own launch is the minimal delay that keeps slot 7
        // (and, by the same fixed stagger/hop relationship, every slot after
        // it) collision-free the whole way down to slot 1. Verified
        // exhaustively (not just by this argument) in this class's own test
        // suite.
        long lastChairLaunch = (long) (CHAIR_TARGET_ORDER.length - 1) * launchStaggerTicks;
        long specialPaneLaunch = lastChairLaunch + hopTicks;
        pieces.add(new Piece(PieceKind.PANE, SPECIAL_PANE_TARGET, specialPanePath(), specialPaneLaunch));

        return pieces;
    }

    private static List<Integer> chairPath(int targetSeatSlot) {
        List<Integer> path = new ArrayList<>(List.of(
            CHAIR_EMERGE_SLOT, 6, 5, 4, 3, 2, 1, 0
        ));
        for (int slot = BlackjackSlotLayout.SEAT_ROW_WIDTH; slot <= targetSeatSlot; slot += BlackjackSlotLayout.SEAT_ROW_WIDTH) {
            path.add(slot);
        }
        return path;
    }

    private static List<Integer> panePath(int targetPaneSlot) {
        if (targetPaneSlot == EDGE_GLASS_TARGET) {
            // One row directly below pane 37 -- reuses that exact path plus one final vertical hop, rather than a
            // fresh descent down the dealer's own column into the (otherwise unused, in LOBBY, action-row) bottom row.
            List<Integer> path = new ArrayList<>(panePath(37));
            path.add(EDGE_GLASS_TARGET);
            return path;
        }
        int branchRowRightSlot; // the rightmost (dealer-column) slot of the target's own row
        switch (targetPaneSlot) {
            case 10 -> branchRowRightSlot = PANE_EMERGE_SLOT;
            case 19 -> branchRowRightSlot = PANE_EMERGE_SLOT + BlackjackSlotLayout.SEAT_ROW_WIDTH;
            case 28 -> branchRowRightSlot = PANE_EMERGE_SLOT + 2 * BlackjackSlotLayout.SEAT_ROW_WIDTH;
            case 37 -> branchRowRightSlot = PANE_EMERGE_SLOT + 3 * BlackjackSlotLayout.SEAT_ROW_WIDTH;
            default -> throw new IllegalArgumentException("not a bottom-corridor pane target: " + targetPaneSlot);
        }
        List<Integer> path = new ArrayList<>();
        path.add(PANE_EMERGE_SLOT);
        int slot = PANE_EMERGE_SLOT;
        while (slot < branchRowRightSlot) {
            slot += BlackjackSlotLayout.SEAT_ROW_WIDTH;
            path.add(slot);
        }
        for (int s = branchRowRightSlot - 1; s >= targetPaneSlot; s--) {
            path.add(s);
        }
        return path;
    }

    private static List<Integer> specialPanePath() {
        return List.of(CHAIR_EMERGE_SLOT, 6, 5, 4, 3, 2, SPECIAL_PANE_TARGET);
    }

    /** Every slot any piece ever visits, in-flight or resting -- the complete set of slots the controller must repaint on every frame (everything else is untouched background/dealer/bottom-bar state). */
    public static Set<Integer> affectedSlots(List<Piece> pieces) {
        Set<Integer> slots = new HashSet<>();
        for (Piece piece : pieces) {
            slots.addAll(piece.getPath());
        }
        return slots;
    }

    /**
     * Every tick at which some piece's occupied slot could change -- each
     * piece's own launch tick plus every hop tick after, up to (and
     * including) its landing tick, deduplicated and sorted. The controller
     * schedules exactly one frame-paint per entry, never a per-piece
     * per-hop callback (see class doc: every frame is a complete,
     * independent snapshot).
     */
    public static List<Long> distinctTicks(List<Piece> pieces, long hopTicks) {
        TreeSet<Long> ticks = new TreeSet<>();
        for (Piece piece : pieces) {
            long landing = piece.landingTick(hopTicks);
            for (long t = piece.getLaunchTick(); t <= landing; t += hopTicks) {
                ticks.add(t);
            }
        }
        return new ArrayList<>(ticks);
    }

    /**
     * The complete, deterministic occupancy snapshot at {@code tick}: every
     * slot currently occupied by some piece (in flight or already landed and
     * resting), mapped to that piece's kind. A slot in {@link #affectedSlots}
     * but absent from this map is vacated/background at this tick. Never
     * silently drops a same-tick same-slot collision between two different
     * pieces -- callers that need to detect that use {@link #findCollisions}
     * instead, which this method's own last-write-wins map construction does
     * not by itself guard against.
     */
    public static Map<Integer, PieceKind> frameAt(List<Piece> pieces, long tick, long hopTicks) {
        Map<Integer, PieceKind> occupied = new HashMap<>();
        for (Piece piece : pieces) {
            int slot = piece.slotAt(tick, hopTicks);
            if (slot != -1) {
                occupied.put(slot, piece.getKind());
            }
        }
        return occupied;
    }

    /**
     * The same occupancy snapshot as {@link #frameAt}, retaining each
     * moving piece's target identity. Controllers use this when the item
     * flying along a CHAIR or PANE path depends on who occupies that
     * piece's eventual seat (for example, a player head instead of an empty
     * chair). The plan remains collision-free, so every occupied slot maps
     * to exactly one piece.
     */
    public static Map<Integer, Piece> pieceFrameAt(List<Piece> pieces, long tick, long hopTicks) {
        Map<Integer, Piece> occupied = new HashMap<>();
        for (Piece piece : pieces) {
            int slot = piece.slotAt(tick, hopTicks);
            if (slot != -1) {
                occupied.put(slot, piece);
            }
        }
        return occupied;
    }

    /** The real, actual completion tick of the whole entrance -- the latest landing tick across every piece. Callers must derive total duration from this, never a separately-added constant that could drift from the schedule actually built. */
    public static long totalDurationTicks(List<Piece> pieces, long hopTicks) {
        long max = 0L;
        for (Piece piece : pieces) {
            max = Math.max(max, piece.landingTick(hopTicks));
        }
        return max;
    }

    /**
     * Every tick+slot at which two distinct pieces (by target slot) would
     * occupy the same slot at the same time -- empty if the plan is fully
     * collision-free. Brute-force, tick-by-tick and piece-by-piece; used
     * only by tests (production code trusts a plan verified collision-free
     * by this method, rather than calling it at runtime every frame).
     */
    public static List<String> findCollisions(List<Piece> pieces, long hopTicks) {
        List<String> collisions = new ArrayList<>();
        long total = totalDurationTicks(pieces, hopTicks);
        for (long t = 0; t <= total; t++) {
            Map<Integer, Piece> occupantAt = new HashMap<>();
            for (Piece piece : pieces) {
                int slot = piece.slotAt(t, hopTicks);
                if (slot == -1) {
                    continue;
                }
                Piece existing = occupantAt.get(slot);
                if (existing != null && existing.getTargetSlot() != piece.getTargetSlot()) {
                    collisions.add("tick " + t + " slot " + slot + ": " + existing.getKind() + "->" + existing.getTargetSlot()
                        + " vs " + piece.getKind() + "->" + piece.getTargetSlot());
                }
                occupantAt.put(slot, piece);
            }
        }
        return collisions;
    }
}
