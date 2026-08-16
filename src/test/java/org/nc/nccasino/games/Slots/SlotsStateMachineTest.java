package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nc.nccasino.games.Slots.SlotsSessionState.ANIMATING;
import static org.nc.nccasino.games.Slots.SlotsSessionState.DEBIT_ACCEPTED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.IDLE;
import static org.nc.nccasino.games.Slots.SlotsSessionState.RESOLVED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.RESULT_COMMITTED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.SETTLING;
import static org.nc.nccasino.games.Slots.SlotsSessionState.TERMINATED;

class SlotsStateMachineTest {

    @Test
    void happyPathSpinTraversesEveryStateInOrder() {
        assertTrue(SlotsStateMachine.canTransition(IDLE, DEBIT_ACCEPTED));
        assertTrue(SlotsStateMachine.canTransition(DEBIT_ACCEPTED, RESULT_COMMITTED));
        assertTrue(SlotsStateMachine.canTransition(RESULT_COMMITTED, ANIMATING));
        assertTrue(SlotsStateMachine.canTransition(ANIMATING, SETTLING));
        assertTrue(SlotsStateMachine.canTransition(SETTLING, RESOLVED));
        assertTrue(SlotsStateMachine.canTransition(RESOLVED, IDLE));
    }

    @Test
    void everyNonTerminalStateCanTerminate() {
        for (SlotsSessionState state : SlotsSessionState.values()) {
            if (state == TERMINATED) {
                continue;
            }
            assertTrue(SlotsStateMachine.canTransition(state, TERMINATED),
                state + " -> TERMINATED must always be legal");
        }
    }

    @Test
    void terminatedIsAbsorbing() {
        for (SlotsSessionState state : SlotsSessionState.values()) {
            assertFalse(SlotsStateMachine.canTransition(TERMINATED, state));
        }
    }

    @Test
    void thereIsNoRouteFromACommittedResultBackToARefundablePregameState() {
        for (SlotsSessionState committedOrLater : new SlotsSessionState[] {RESULT_COMMITTED, ANIMATING, SETTLING}) {
            assertFalse(SlotsStateMachine.canTransition(committedOrLater, IDLE));
            assertFalse(SlotsStateMachine.canTransition(committedOrLater, DEBIT_ACCEPTED));
        }
    }

    @Test
    void skippingStatesIsIllegal() {
        assertFalse(SlotsStateMachine.canTransition(IDLE, RESULT_COMMITTED));
        assertFalse(SlotsStateMachine.canTransition(DEBIT_ACCEPTED, ANIMATING));
        assertFalse(SlotsStateMachine.canTransition(IDLE, RESOLVED));
    }

    @Test
    void sameStateIsNeverALegalTransition() {
        for (SlotsSessionState state : SlotsSessionState.values()) {
            assertFalse(SlotsStateMachine.canTransition(state, state));
        }
    }

    @Test
    void nullEndpointsAreNeverLegal() {
        assertFalse(SlotsStateMachine.canTransition(null, IDLE));
        assertFalse(SlotsStateMachine.canTransition(IDLE, null));
        assertFalse(SlotsStateMachine.canTransition(null, null));
    }

    @Test
    void transitionReturnsTheNewStateWhenLegal() {
        assertEquals(DEBIT_ACCEPTED, SlotsStateMachine.transition(IDLE, DEBIT_ACCEPTED));
    }

    @Test
    void transitionThrowsWhenIllegal() {
        assertThrows(IllegalStateException.class, () -> SlotsStateMachine.transition(IDLE, RESOLVED));
    }
}
