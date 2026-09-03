package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins SlotsMenu's Default Height / Default Paylines interaction (redesign audit Section 3). */
class SlotsAdminSettingsTransitionsTest {

    @Test
    void rowsWrapsBothDirectionsAcrossOneThreeFive() {
        assertEquals(3, SlotsAdminSettingsTransitions.nextRows(1, 1));
        assertEquals(5, SlotsAdminSettingsTransitions.nextRows(3, 1));
        assertEquals(1, SlotsAdminSettingsTransitions.nextRows(5, 1));
        assertEquals(5, SlotsAdminSettingsTransitions.nextRows(1, -1));
        assertEquals(1, SlotsAdminSettingsTransitions.nextRows(3, -1));
        assertEquals(3, SlotsAdminSettingsTransitions.nextRows(5, -1));
    }

    @Test
    void movingToOrFromHeightOnePersistsLinesAsOne() {
        // Entering height 1: from 5 going forward (5 -> 1, wrap), or from 3
        // going backward (3 -> 1).
        assertEquals(1, SlotsAdminSettingsTransitions.rowsTransition(5, 7, 1).nextPersistedLines());
        assertEquals(1, SlotsAdminSettingsTransitions.rowsTransition(3, 7, -1).nextPersistedLines());
        // Leaving height 1, regardless of direction or the stored value.
        assertEquals(1, SlotsAdminSettingsTransitions.rowsTransition(1, 1, 1).nextPersistedLines());
        assertEquals(1, SlotsAdminSettingsTransitions.rowsTransition(1, 1, -1).nextPersistedLines());
    }

    @Test
    void aThreeToFiveOrFiveToThreeMoveNeverTouchesTheStoredLineCount() {
        assertEquals(7, SlotsAdminSettingsTransitions.rowsTransition(3, 7, 1).nextPersistedLines());
        assertEquals(7, SlotsAdminSettingsTransitions.rowsTransition(5, 7, -1).nextPersistedLines());
    }

    @Test
    void leavingHeightOneWithAStaleRawLineCountPersistsOneNeverTheStaleValue() {
        // The exact reported production defect: current effective height is
        // 1 (so SlotsConfig.load already displays activeLines()==1), but a
        // hand-edited or legacy config could still carry a stale raw value.
        // Leaving height 1 must persist 1 regardless of what that stale
        // value was -- never resurrect it.
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(1, 9, 1);
        assertEquals(3, transition.nextRows());
        assertEquals(1, transition.nextPersistedLines(),
            "a stale raw 9 must never resurface when leaving height 1");
    }

    @Test
    void heightOneLeftRightPaylineClicksAreInertNoOps() {
        assertEquals(SlotsAdminSettingsTransitions.INERT,
            SlotsAdminSettingsTransitions.nextLinesOrInert(1, 1, 1));
        assertEquals(SlotsAdminSettingsTransitions.INERT,
            SlotsAdminSettingsTransitions.nextLinesOrInert(1, 1, -1));
        // Even if raw stored state were somehow stale/larger, height 1 stays inert.
        assertEquals(SlotsAdminSettingsTransitions.INERT,
            SlotsAdminSettingsTransitions.nextLinesOrInert(1, 9, 1));
    }

    @Test
    void heightThreeAndFiveWrapOneThroughNineInBothDirections() {
        for (int rows : new int[] {3, 5}) {
            assertEquals(2, SlotsAdminSettingsTransitions.nextLinesOrInert(rows, 1, 1));
            assertEquals(1, SlotsAdminSettingsTransitions.nextLinesOrInert(rows, 9, 1), "9 -> 1 wraps forward");
            assertEquals(9, SlotsAdminSettingsTransitions.nextLinesOrInert(rows, 1, -1), "1 -> 9 wraps backward");
            assertEquals(8, SlotsAdminSettingsTransitions.nextLinesOrInert(rows, 9, -1));
        }
    }

    // ---- exact scenarios named in the audit -------------------------------

    @Test
    void rowsThreeLinesSevenThenRowsFiveRemainsSeven() {
        // Direct 3 <-> 5 never touches lines at all.
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(3, 7, 1);
        assertEquals(5, transition.nextRows());
        assertEquals(7, transition.nextPersistedLines());
    }

    @Test
    void rowsFiveLinesSevenThenRowsOnePersistsOne() {
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(5, 7, 1); // 5 -> 1 (wrap)
        assertEquals(1, transition.nextRows());
        assertEquals(1, transition.nextPersistedLines(),
            "moving to height 1 must force the stored default line count to 1 too");
    }

    @Test
    void rowsOneThenRowsThreeRemainsOne() {
        // Because entering height 1 always persists lines=1 (see the
        // previous test), the raw stored value returning from height 1 is
        // already 1 -- there is no hidden larger count left to resurface.
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(1, 1, 1);
        assertEquals(3, transition.nextRows());
        assertEquals(1, transition.nextPersistedLines());
    }
}
