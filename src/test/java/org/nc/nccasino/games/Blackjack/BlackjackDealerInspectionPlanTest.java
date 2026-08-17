package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlackjackDealerInspectionPlanTest {

    private static final long BASE_TICKS = 5L;
    private static final long SLOWDOWN_TICKS = 15L;

    @Test
    void fullPathMatchesBlackjackSlotLayoutsUPathInOrder() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        List<Integer> expectedPath = BlackjackSlotLayout.dealerUPath();

        assertEquals(expectedPath.size(), steps.size());
        for (int i = 0; i < expectedPath.size(); i++) {
            assertEquals(expectedPath.get(i), steps.get(i).getSlot());
            assertEquals(BlackjackAnimationStep.Kind.MOVE, steps.get(i).getKind());
        }
    }

    @Test
    void noWageredSeatsMeansEveryStepUsesTheBaseDuration() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        for (int i = 1; i < steps.size(); i++) {
            long gap = steps.get(i).getDelayTicks() - steps.get(i - 1).getDelayTicks();
            assertEquals(BASE_TICKS, gap, "with no committed wagers, every leg of the path should take the base step duration");
        }
    }

    @Test
    void theFiveCheckpointSlotsAreEachSeatsFirstCardCell() {
        List<Integer> checkpoints = BlackjackDealerInspectionPlan.CHECKPOINT_SLOTS;
        assertEquals(5, checkpoints.size());
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            assertTrue(checkpoints.contains(BlackjackSlotLayout.pregameCountdownSlot(seatSlot)));
        }
    }

    @Test
    void checkpointSeatSlotResolvesEachCheckpointBackToItsSeatAndNullElsewhere() {
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            int checkpoint = BlackjackSlotLayout.pregameCountdownSlot(seatSlot);
            assertEquals(seatSlot, BlackjackDealerInspectionPlan.checkpointSeatSlot(checkpoint));
        }
        assertNull(BlackjackDealerInspectionPlan.checkpointSeatSlot(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT));
        assertNull(BlackjackDealerInspectionPlan.checkpointSeatSlot(BlackjackSlotLayout.DEALER_UP_CARD_SLOT));
    }

    @Test
    void onlyWageredSeatsCheckpointsGetTheSlowdown() {
        int wageredSeat = BlackjackSlotLayout.SEAT_SLOTS[2]; // seat slot 18 -> checkpoint 20
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(wageredSeat), BASE_TICKS, SLOWDOWN_TICKS);
        List<Integer> path = BlackjackSlotLayout.dealerUPath();

        for (int i = 1; i < steps.size(); i++) {
            int arrivingSlot = path.get(i - 1); // the leg *into* steps.get(i) departs from the previous checkpoint's slot
            long gap = steps.get(i).getDelayTicks() - steps.get(i - 1).getDelayTicks();
            Integer checkpointSeat = BlackjackDealerInspectionPlan.checkpointSeatSlot(arrivingSlot);
            if (checkpointSeat != null && checkpointSeat == wageredSeat) {
                assertEquals(BASE_TICKS + SLOWDOWN_TICKS, gap, "the wagered seat's checkpoint leg must include the slowdown");
            } else {
                assertEquals(BASE_TICKS, gap, "every other leg must use the base duration");
            }
        }
    }

    @Test
    void everySeatWageredSlowsDownAllFiveCheckpoints() {
        Set<Integer> allSeats = Set.of(
            BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[1], BlackjackSlotLayout.SEAT_SLOTS[2],
            BlackjackSlotLayout.SEAT_SLOTS[3], BlackjackSlotLayout.SEAT_SLOTS[4]
        );
        List<BlackjackAnimationStep> withAll = BlackjackDealerInspectionPlan.build(allSeats, BASE_TICKS, SLOWDOWN_TICKS);
        List<BlackjackAnimationStep> withNone = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);

        long lastWithAll = withAll.get(withAll.size() - 1).getDelayTicks();
        long lastWithNone = withNone.get(withNone.size() - 1).getDelayTicks();
        assertEquals(5 * SLOWDOWN_TICKS, lastWithAll - lastWithNone, "all five checkpoints slowed down must add exactly five slowdown increments to the total");
    }

    // ==================================================================
    // Multiple committed players -- cumulative pauses shift every later step
    // ==================================================================

    @Test
    void multipleCommittedPlayersProduceMultipleCumulativePausesThatShiftEveryLaterStep() {
        // Seat 0 (checkpoint index 6, slot 2) and seat 2 (checkpoint index 8,
        // slot 20) are committed; seats 1, 3, 4 are not.
        Set<Integer> committed = Set.of(BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[2]);
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(committed, BASE_TICKS, SLOWDOWN_TICKS);

        // Before either pause, plain cumulative base ticks.
        assertEquals(0L, steps.get(0).getDelayTicks());
        assertEquals(25L, steps.get(5).getDelayTicks());
        // idx6 = slot 2 (seat 0's checkpoint) -- reached before its own pause is applied.
        assertEquals(30L, steps.get(6).getDelayTicks());
        // idx7 = slot 11 -- one pause (seat 0's) already folded in: 30 + BASE + SLOWDOWN.
        assertEquals(30L + BASE_TICKS + SLOWDOWN_TICKS, steps.get(7).getDelayTicks());
        // idx8 = slot 20 (seat 2's checkpoint) -- still only the first pause applied so far.
        long idx8Expected = 30L + BASE_TICKS + SLOWDOWN_TICKS + BASE_TICKS;
        assertEquals(idx8Expected, steps.get(8).getDelayTicks());
        // idx9 = slot 29 -- now both pauses are folded into every subsequent step.
        long idx9Expected = idx8Expected + BASE_TICKS + SLOWDOWN_TICKS;
        assertEquals(idx9Expected, steps.get(9).getDelayTicks());
        long idx10Expected = idx9Expected + BASE_TICKS;
        assertEquals(idx10Expected, steps.get(10).getDelayTicks());
        // The first bottom-row step (idx11, slot 47) carries both pauses too.
        long idx11Expected = idx10Expected + BASE_TICKS;
        assertEquals(idx11Expected, steps.get(11).getDelayTicks());

        // Total shift over the no-committed-players baseline is exactly two slowdown increments.
        List<BlackjackAnimationStep> baseline = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        assertEquals(2 * SLOWDOWN_TICKS, BlackjackDealerInspectionPlan.totalDurationTicks(steps) - BlackjackDealerInspectionPlan.totalDurationTicks(baseline));
    }

    @Test
    void stepTimesRemainMonotonicallyNondecreasingWithMultiplePauses() {
        Set<Integer> allSeats = Set.of(
            BlackjackSlotLayout.SEAT_SLOTS[0], BlackjackSlotLayout.SEAT_SLOTS[1], BlackjackSlotLayout.SEAT_SLOTS[2],
            BlackjackSlotLayout.SEAT_SLOTS[3], BlackjackSlotLayout.SEAT_SLOTS[4]
        );
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(allSeats, BASE_TICKS, SLOWDOWN_TICKS);
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(steps.get(i).getDelayTicks() >= steps.get(i - 1).getDelayTicks(),
                "step " + i + " must not be scheduled earlier than step " + (i - 1));
        }
    }

    // ==================================================================
    // withBottomRowCoordination: minimum-required bottom-row gate
    // ==================================================================

    @Test
    void coordinationShiftsOnlyTheBottomRowByExactlyTheAmountNeeded() {
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        long naturalFirstBottomRow = steps.get(11).getDelayTicks(); // slot 47
        assertEquals(55L, naturalFirstBottomRow);

        long requiredNotBefore = 80L; // later than the natural arrival -- a shift is genuinely required
        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, requiredNotBefore);

        long expectedShift = requiredNotBefore - naturalFirstBottomRow;
        // Every top/side step (before the bottom row) is completely untouched.
        for (int i = 0; i <= 10; i++) {
            assertEquals(steps.get(i).getDelayTicks(), coordinated.get(i).getDelayTicks(), "top/side step " + i + " must never be shifted");
            assertSame(steps.get(i), coordinated.get(i), "an unshifted step should be reused, not copied");
        }
        // The first bottom-row step lands exactly on the required time -- the minimum possible shift.
        assertEquals(requiredNotBefore, coordinated.get(11).getDelayTicks());
        // Every bottom-row step (including the first) is shifted by the exact same constant, preserving spacing.
        for (int i = 11; i < steps.size(); i++) {
            assertEquals(steps.get(i).getDelayTicks() + expectedShift, coordinated.get(i).getDelayTicks());
            assertEquals(steps.get(i).getSlot(), coordinated.get(i).getSlot());
            assertEquals(steps.get(i).getKind(), coordinated.get(i).getKind());
        }
    }

    @Test
    void coordinationAddsNoUnnecessaryGapWhenTheNaturalArrivalIsAlreadyLateEnough() {
        // Committed-player pauses can already push the natural bottom-row
        // arrival past the required time -- in that case, no shift may be
        // added at all (the actual production scenario under real timing
        // constants, where 11 top/side legs already exceed the conceal
        // window even with zero pauses).
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        long naturalFirstBottomRow = steps.get(11).getDelayTicks(); // 55

        List<BlackjackAnimationStep> exactlyOnTime = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, naturalFirstBottomRow);
        List<BlackjackAnimationStep> alreadyLate = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, naturalFirstBottomRow - 20);

        assertSame(steps, exactlyOnTime, "arriving exactly on time needs no shift -- the original list must be reused, not copied");
        assertSame(steps, alreadyLate, "arriving later than required needs no shift either");
    }

    @Test
    void coordinationKeepsEveryStepMonotonicallyNondecreasing() {
        Set<Integer> committed = Set.of(BlackjackSlotLayout.SEAT_SLOTS[0]);
        List<BlackjackAnimationStep> steps = BlackjackDealerInspectionPlan.build(committed, BASE_TICKS, SLOWDOWN_TICKS);
        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(steps, 500L);

        for (int i = 1; i < coordinated.size(); i++) {
            assertTrue(coordinated.get(i).getDelayTicks() >= coordinated.get(i - 1).getDelayTicks(),
                "coordinated step " + i + " must not be scheduled earlier than step " + (i - 1));
        }
    }

    @Test
    void totalDurationTicksMatchesTheActualFinalScheduledStep() {
        List<BlackjackAnimationStep> uncoordinated = BlackjackDealerInspectionPlan.build(Set.of(), BASE_TICKS, SLOWDOWN_TICKS);
        assertEquals(uncoordinated.get(uncoordinated.size() - 1).getDelayTicks(), BlackjackDealerInspectionPlan.totalDurationTicks(uncoordinated));

        List<BlackjackAnimationStep> coordinated = BlackjackDealerInspectionPlan.withBottomRowCoordination(uncoordinated, 200L);
        assertEquals(coordinated.get(coordinated.size() - 1).getDelayTicks(), BlackjackDealerInspectionPlan.totalDurationTicks(coordinated));
        assertTrue(BlackjackDealerInspectionPlan.totalDurationTicks(coordinated) > BlackjackDealerInspectionPlan.totalDurationTicks(uncoordinated),
            "a genuinely required shift must be reflected in the reported total duration");
    }

    @Test
    void totalDurationTicksIsZeroForAnEmptyPath() {
        assertEquals(0L, BlackjackDealerInspectionPlan.totalDurationTicks(List.of()));
    }
}
