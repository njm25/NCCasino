package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlackjackTableEntrancePlanTest {

    private static final long HOP = BlackjackTiming.TABLE_ENTRANCE_HOP_TICKS;
    private static final long STAGGER = BlackjackTiming.TABLE_ENTRANCE_LAUNCH_STAGGER_TICKS;

    private static BlackjackTableEntrancePlan.Piece pieceFor(List<BlackjackTableEntrancePlan.Piece> pieces, int targetSlot) {
        return pieces.stream()
            .filter(p -> p.getTargetSlot() == targetSlot)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no piece targets slot " + targetSlot));
    }

    // ==================================================================
    // Exact paths and final destinations
    // ==================================================================

    @Test
    void chairPathsMatchTheSpecifiedRoutesExactly() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);

        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0), pieceFor(pieces, 0).getPath());
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0, 9), pieceFor(pieces, 9).getPath());
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0, 9, 18), pieceFor(pieces, 18).getPath());
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0, 9, 18, 27), pieceFor(pieces, 27).getPath());
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0, 9, 18, 27, 36), pieceFor(pieces, 36).getPath());
        // The door -- one row deeper than seat 36, reusing the exact same corridor.
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1, 0, 9, 18, 27, 36, 45), pieceFor(pieces, 45).getPath());

        for (int target : new int[]{0, 9, 18, 27, 36}) {
            assertEquals(BlackjackTableEntrancePlan.PieceKind.CHAIR, pieceFor(pieces, target).getKind());
        }
        assertEquals(BlackjackTableEntrancePlan.PieceKind.DOOR, pieceFor(pieces, 45).getKind());
    }

    @Test
    void brownPanePathsMatchTheSpecifiedRoutesExactly() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);

        assertEquals(List.of(17, 16, 15, 14, 13, 12, 11, 10), pieceFor(pieces, 10).getPath());
        assertEquals(List.of(17, 26, 25, 24, 23, 22, 21, 20, 19), pieceFor(pieces, 19).getPath());
        assertEquals(List.of(17, 26, 35, 34, 33, 32, 31, 30, 29, 28), pieceFor(pieces, 28).getPath());
        assertEquals(List.of(17, 26, 35, 44, 43, 42, 41, 40, 39, 38, 37), pieceFor(pieces, 37).getPath());
        // The bottom-bar edge glass -- one row deeper than pane 37, reusing the exact same corridor.
        assertEquals(List.of(17, 26, 35, 44, 43, 42, 41, 40, 39, 38, 37, 46), pieceFor(pieces, 46).getPath());
        // The special top-row pane reuses the chair corridor rather than the pane corridor.
        assertEquals(List.of(7, 6, 5, 4, 3, 2, 1), pieceFor(pieces, 1).getPath());

        for (int target : new int[]{1, 10, 19, 28, 37, 46}) {
            assertEquals(BlackjackTableEntrancePlan.PieceKind.PANE, pieceFor(pieces, target).getKind());
        }
    }

    // ==================================================================
    // Dispatch ordering and launch spacing
    // ==================================================================

    @Test
    void chairsLaunchDeepestSeatFirst() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);

        assertTrue(pieceFor(pieces, 45).getLaunchTick() < pieceFor(pieces, 36).getLaunchTick(), "the door, one row deeper than seat 36, must lead the chair stream");
        assertTrue(pieceFor(pieces, 36).getLaunchTick() < pieceFor(pieces, 27).getLaunchTick());
        assertTrue(pieceFor(pieces, 27).getLaunchTick() < pieceFor(pieces, 18).getLaunchTick());
        assertTrue(pieceFor(pieces, 18).getLaunchTick() < pieceFor(pieces, 9).getLaunchTick());
        assertTrue(pieceFor(pieces, 9).getLaunchTick() < pieceFor(pieces, 0).getLaunchTick());
    }

    @Test
    void bottomCorridorPanesLaunchDeepestRowFirst() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);

        assertTrue(pieceFor(pieces, 46).getLaunchTick() < pieceFor(pieces, 37).getLaunchTick(), "the edge glass, one row deeper than pane 37, must lead the bottom-corridor pane stream");
        assertTrue(pieceFor(pieces, 37).getLaunchTick() < pieceFor(pieces, 28).getLaunchTick());
        assertTrue(pieceFor(pieces, 28).getLaunchTick() < pieceFor(pieces, 19).getLaunchTick());
        assertTrue(pieceFor(pieces, 19).getLaunchTick() < pieceFor(pieces, 10).getLaunchTick());
    }

    @Test
    void successiveSameStreamLaunchesAreExactlyOneStaggerApart() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);

        int[] chairOrder = {45, 36, 27, 18, 9, 0};
        for (int i = 1; i < chairOrder.length; i++) {
            long gap = pieceFor(pieces, chairOrder[i]).getLaunchTick() - pieceFor(pieces, chairOrder[i - 1]).getLaunchTick();
            assertEquals(STAGGER, gap, "chair/door launches must be exactly one stagger apart");
        }

        int[] paneOrder = {46, 37, 28, 19, 10};
        for (int i = 1; i < paneOrder.length; i++) {
            long gap = pieceFor(pieces, paneOrder[i]).getLaunchTick() - pieceFor(pieces, paneOrder[i - 1]).getLaunchTick();
            assertEquals(STAGGER, gap, "pane/edge-glass launches must be exactly one stagger apart");
        }
    }

    @Test
    void specialPaneLaunchesTheMinimumSafeDelayAfterTheLastChair() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        long lastChairLaunch = pieceFor(pieces, 0).getLaunchTick(); // shallowest target, launched last
        assertEquals(lastChairLaunch + HOP, pieceFor(pieces, 1).getLaunchTick(),
            "the special pane must launch exactly one hop after the last chair's own launch -- the minimum delay that keeps slot 7 collision-free");
    }

    // ==================================================================
    // Simultaneous flight / frame snapshots
    // ==================================================================

    @Test
    void multiplePiecesAreInFlightSimultaneously() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        // Mid-animation, several chair-stream pieces (door + chairs, sharing
        // the same corridor) are already launched and none have landed yet.
        long inFlightCount = pieces.stream()
            .filter(p -> p.getKind() == BlackjackTableEntrancePlan.PieceKind.CHAIR || p.getKind() == BlackjackTableEntrancePlan.PieceKind.DOOR)
            .filter(p -> p.slotAt(2 * STAGGER, HOP) != -1)
            .count();
        assertTrue(inFlightCount >= 3, "several chair-stream pieces must be simultaneously visible mid-animation, not a strict one-at-a-time sweep");
    }

    @Test
    void frameAtNeverPlacesTwoDifferentPiecesOnTheSameSlot() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        List<String> collisions = BlackjackTableEntrancePlan.findCollisions(pieces, HOP);
        assertTrue(collisions.isEmpty(), "no same-tick same-slot collisions allowed: " + collisions);
    }

    @Test
    void everyVacatedTransitSlotBecomesEmptyOnTheVeryNextFrame() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        BlackjackTableEntrancePlan.Piece chair36 = pieceFor(pieces, 36);

        // Chair 36 occupies slot 7 (its launch slot) only at its own launch tick;
        // the very next frame it must have moved on, leaving slot 7 unoccupied by it.
        long launch = chair36.getLaunchTick();
        assertEquals(BlackjackTableEntrancePlan.CHAIR_EMERGE_SLOT, chair36.slotAt(launch, HOP));
        assertFalse(chair36.slotAt(launch + HOP, HOP) == BlackjackTableEntrancePlan.CHAIR_EMERGE_SLOT,
            "the very next frame after launch must have vacated the emergence slot");

        Map<Integer, BlackjackTableEntrancePlan.PieceKind> nextFrame = BlackjackTableEntrancePlan.frameAt(pieces, launch + HOP, HOP);
        // At tick launch+HOP, chair 36 has moved off slot 7 and no follower has launched yet
        // (the next chair launches a full stagger, not just one hop, later) -- slot 7 must be
        // genuinely vacated back to background, not still showing (or re-showing) a chair.
        assertFalse(nextFrame.containsKey(BlackjackTableEntrancePlan.CHAIR_EMERGE_SLOT),
            "slot 7 must be vacated to background one frame after chair 36 launched from it");
    }

    // ==================================================================
    // Final resting frame
    // ==================================================================

    @Test
    void everyPieceOccupiesPreciselyItsOwnTargetSlotInTheFinalFrame() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        long total = BlackjackTableEntrancePlan.totalDurationTicks(pieces, HOP);

        Map<Integer, BlackjackTableEntrancePlan.PieceKind> finalFrame = BlackjackTableEntrancePlan.frameAt(pieces, total, HOP);

        Set<Integer> expectedChairSlots = Set.of(0, 9, 18, 27, 36);
        Set<Integer> expectedPaneSlots = Set.of(1, 10, 19, 28, 37, 46); // 46 (edge glass) reuses PieceKind.PANE
        int expectedDoorSlot = 45;

        for (int slot : expectedChairSlots) {
            assertEquals(BlackjackTableEntrancePlan.PieceKind.CHAIR, finalFrame.get(slot), "seat slot " + slot + " must hold a landed chair in the final frame");
        }
        for (int slot : expectedPaneSlots) {
            assertEquals(BlackjackTableEntrancePlan.PieceKind.PANE, finalFrame.get(slot), "bet-spot/edge-glass slot " + slot + " must hold a landed pane in the final frame");
        }
        assertEquals(BlackjackTableEntrancePlan.PieceKind.DOOR, finalFrame.get(expectedDoorSlot), "slot 45 must hold the landed door in the final frame");
        assertEquals(expectedChairSlots.size() + expectedPaneSlots.size() + 1, finalFrame.size(),
            "the final frame must show exactly the twelve landed pieces and nothing else still in flight");
    }

    @Test
    void dealerSlotIsNeverClaimedByAnyMovingPiece() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        for (BlackjackTableEntrancePlan.Piece piece : pieces) {
            assertFalse(piece.getPath().contains(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT),
                piece.getKind() + "->" + piece.getTargetSlot() + " must never route through the dealer's own slot 8");
        }
    }

    @Test
    void totalDurationIsDerivedFromTheLatestPieceLandingNotAHardcodedConstant() {
        List<BlackjackTableEntrancePlan.Piece> pieces = BlackjackTableEntrancePlan.build(HOP, STAGGER);
        long expectedMax = pieces.stream().mapToLong(p -> p.landingTick(HOP)).max().orElseThrow();
        assertEquals(expectedMax, BlackjackTableEntrancePlan.totalDurationTicks(pieces, HOP));

        // A different hop/stagger must actually change the derived duration -- confirms it's not a copied literal.
        List<BlackjackTableEntrancePlan.Piece> slower = BlackjackTableEntrancePlan.build(HOP * 3, STAGGER * 3);
        assertTrue(BlackjackTableEntrancePlan.totalDurationTicks(slower, HOP * 3) > BlackjackTableEntrancePlan.totalDurationTicks(pieces, HOP),
            "a slower hop/stagger must produce a longer total duration");
    }

    @Test
    void buildRejectsNonPositiveTimingParameters() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackTableEntrancePlan.build(0L, STAGGER));
        assertThrows(IllegalArgumentException.class, () -> BlackjackTableEntrancePlan.build(HOP, 0L));
    }
}
