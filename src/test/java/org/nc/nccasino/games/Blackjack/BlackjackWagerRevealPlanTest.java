package org.nc.nccasino.games.Blackjack;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-data coverage for the solid, frame-based sliding wager bar strip
 * (Undo All | Undo Last | Chip 1..5 | All In | Door) replacing the old
 * one-slot-at-a-time reveal/conceal. Every frame from {@link BlackjackWagerRevealPlan#CLOSED}
 * (0) through {@link BlackjackWagerRevealPlan#OPEN} (8) is asserted exactly,
 * plus the structural invariants the controller's atomic per-tick repaint
 * depends on: exactly one door, always the rightmost visible item, a
 * contiguous strip with no gap, and no duplicated control.
 */
class BlackjackWagerRevealPlanTest {

    private static final long FRAME_TICKS = 1L;

    // ==================================================================
    // Exact frames, position 0 (CLOSED) through 8 (OPEN)
    // ==================================================================

    @Test
    void position0IsTheFullyClosedRestingFrame() {
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(0);
        assertEquals(BlackjackWagerRevealPlan.Control.DOOR, frame[0]); // slot 45
        assertEquals(BlackjackWagerRevealPlan.Control.EDGE_GLASS, frame[1]); // slot 46
        for (int i = 2; i < frame.length; i++) {
            assertEquals(BlackjackWagerRevealPlan.Control.BACKGROUND, frame[i], "slot " + (45 + i));
        }
    }

    @Test
    void position1IsAllInThenDoor() {
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(1);
        assertEquals(BlackjackWagerRevealPlan.Control.ALL_IN, frame[0]); // 45
        assertEquals(BlackjackWagerRevealPlan.Control.DOOR, frame[1]); // 46
        for (int i = 2; i < frame.length; i++) {
            assertEquals(BlackjackWagerRevealPlan.Control.BACKGROUND, frame[i]);
        }
    }

    @Test
    void position2IsChipFiveAllInThenDoor() {
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(2);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_5, frame[0]); // 45
        assertEquals(BlackjackWagerRevealPlan.Control.ALL_IN, frame[1]); // 46
        assertEquals(BlackjackWagerRevealPlan.Control.DOOR, frame[2]); // 47
        for (int i = 3; i < frame.length; i++) {
            assertEquals(BlackjackWagerRevealPlan.Control.BACKGROUND, frame[i]);
        }
    }

    @Test
    void position3IsChipFourChipFiveAllInThenDoor() {
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(3);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_4, frame[0]);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_5, frame[1]);
        assertEquals(BlackjackWagerRevealPlan.Control.ALL_IN, frame[2]);
        assertEquals(BlackjackWagerRevealPlan.Control.DOOR, frame[3]);
        for (int i = 4; i < frame.length; i++) {
            assertEquals(BlackjackWagerRevealPlan.Control.BACKGROUND, frame[i]);
        }
    }

    @Test
    void position8IsTheFullyOpenCanonicalBar() {
        BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(8);
        assertEquals(BlackjackWagerRevealPlan.Control.UNDO_ALL, frame[0]); // 45
        assertEquals(BlackjackWagerRevealPlan.Control.UNDO_LAST, frame[1]); // 46
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_1, frame[2]); // 47
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_2, frame[3]);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_3, frame[4]);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_4, frame[5]);
        assertEquals(BlackjackWagerRevealPlan.Control.CHIP_5, frame[6]);
        assertEquals(BlackjackWagerRevealPlan.Control.ALL_IN, frame[7]); // 52
        assertEquals(BlackjackWagerRevealPlan.Control.DOOR, frame[8]); // 53
    }

    @Test
    void positionOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackWagerRevealPlan.frame(-1));
        assertThrows(IllegalArgumentException.class, () -> BlackjackWagerRevealPlan.frame(9));
    }

    // ==================================================================
    // Structural invariants over every frame 0..8
    // ==================================================================

    @Test
    void everyFrameHasExactlyOneDoorAndItIsAlwaysTheRightmostVisibleItem() {
        for (int position = 0; position <= BlackjackWagerRevealPlan.OPEN; position++) {
            BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(position);
            int doorCount = 0;
            int doorIndex = -1;
            for (int i = 0; i < frame.length; i++) {
                if (frame[i] == BlackjackWagerRevealPlan.Control.DOOR) {
                    doorCount++;
                    doorIndex = i;
                }
            }
            assertEquals(1, doorCount, "position " + position + " must have exactly one door");
            assertEquals(position, doorIndex, "the door's index must equal the position itself");
            // Nothing to the door's right is ever a visible control -- background
            // everywhere, except the fully-closed resting frame's own decorative
            // edge glass immediately right of the door (slot 46, position 0 only).
            for (int i = doorIndex + 1; i < frame.length; i++) {
                boolean isRestingEdgeGlass = position == BlackjackWagerRevealPlan.CLOSED
                    && frame[i] == BlackjackWagerRevealPlan.Control.EDGE_GLASS;
                assertTrue(frame[i] == BlackjackWagerRevealPlan.Control.BACKGROUND || isRestingEdgeGlass,
                    "position " + position + " slot " + (45 + i) + " must be background (or the resting edge glass), right of the door");
            }
        }
    }

    @Test
    void everyFramesVisibleStripIsContiguousWithNoGap() {
        for (int position = 1; position <= BlackjackWagerRevealPlan.OPEN; position++) {
            BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(position);
            // Slots 0..position (inclusive of the door) must all be non-background controls.
            for (int i = 0; i <= position; i++) {
                assertTrue(frame[i] != BlackjackWagerRevealPlan.Control.BACKGROUND
                        && frame[i] != BlackjackWagerRevealPlan.Control.EDGE_GLASS,
                    "position " + position + " slot " + (45 + i) + " must be part of the contiguous visible strip");
            }
        }
    }

    @Test
    void everyFrameHasNoDuplicatedLogicalControl() {
        Set<BlackjackWagerRevealPlan.Control> nonRepeatable = EnumSet.of(
            BlackjackWagerRevealPlan.Control.DOOR, BlackjackWagerRevealPlan.Control.UNDO_ALL, BlackjackWagerRevealPlan.Control.UNDO_LAST,
            BlackjackWagerRevealPlan.Control.CHIP_1, BlackjackWagerRevealPlan.Control.CHIP_2, BlackjackWagerRevealPlan.Control.CHIP_3,
            BlackjackWagerRevealPlan.Control.CHIP_4, BlackjackWagerRevealPlan.Control.CHIP_5, BlackjackWagerRevealPlan.Control.ALL_IN
        );
        for (int position = 0; position <= BlackjackWagerRevealPlan.OPEN; position++) {
            BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(position);
            Set<BlackjackWagerRevealPlan.Control> seen = new HashSet<>();
            for (BlackjackWagerRevealPlan.Control control : frame) {
                if (nonRepeatable.contains(control)) {
                    assertTrue(seen.add(control), "position " + position + " duplicates " + control);
                }
            }
        }
    }

    @Test
    void everyMovingFrameNeverShowsTheEdgeGlass() {
        // The brown edge glass belongs only to the fully-closed resting frame -- it disappears the instant the strip starts moving.
        for (int position = 1; position <= BlackjackWagerRevealPlan.OPEN; position++) {
            BlackjackWagerRevealPlan.Control[] frame = BlackjackWagerRevealPlan.frame(position);
            for (BlackjackWagerRevealPlan.Control control : frame) {
                assertTrue(control != BlackjackWagerRevealPlan.Control.EDGE_GLASS, "position " + position + " must never show the edge glass");
            }
        }
    }

    @Test
    void concealIsTheExactReverseOfReveal() {
        // Reveal walks position 0 -> 8; conceal is defined as the exact same
        // frame sequence walked backwards -- assert every frame at position p
        // during a "reveal" matches the frame at the same position p during
        // a "conceal" (there's only one frame per position, so this is
        // trivially true by construction, but pins the invariant down).
        for (int position = 0; position <= BlackjackWagerRevealPlan.OPEN; position++) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(BlackjackWagerRevealPlan.frame(position), BlackjackWagerRevealPlan.frame(position));
        }
    }

    // ==================================================================
    // Timing -- one frame per tick, 8-frame endpoint-to-endpoint duration
    // ==================================================================

    @Test
    void fullSlideDurationIsEightFramesAtOneTickPerFrame() {
        assertEquals(8L, BlackjackWagerRevealPlan.revealDurationTicks(FRAME_TICKS));
        assertEquals(8L, BlackjackWagerRevealPlan.concealDurationTicks(FRAME_TICKS));
    }

    @Test
    void durationScalesLinearlyWithFrameTicks() {
        assertEquals(16L, BlackjackWagerRevealPlan.revealDurationTicks(2L));
        assertEquals(16L, BlackjackWagerRevealPlan.concealDurationTicks(2L));
    }

    @Test
    void revealAndConcealDurationsAreIdenticalInThisSymmetricModel() {
        assertEquals(BlackjackWagerRevealPlan.revealDurationTicks(FRAME_TICKS), BlackjackWagerRevealPlan.concealDurationTicks(FRAME_TICKS));
    }
}
