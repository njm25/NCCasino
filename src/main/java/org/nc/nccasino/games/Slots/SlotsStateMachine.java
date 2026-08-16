package org.nc.nccasino.games.Slots;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.nc.nccasino.games.Slots.SlotsSessionState.ANIMATING;
import static org.nc.nccasino.games.Slots.SlotsSessionState.DEBIT_ACCEPTED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.IDLE;
import static org.nc.nccasino.games.Slots.SlotsSessionState.RESOLVED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.RESULT_COMMITTED;
import static org.nc.nccasino.games.Slots.SlotsSessionState.SETTLING;
import static org.nc.nccasino.games.Slots.SlotsSessionState.TERMINATED;

/**
 * Centralizes every legal Slots state transition in one pure, testable
 * place. Critically, there is no route from {@link SlotsSessionState#RESULT_COMMITTED}
 * (or anything reachable from it) back to {@link SlotsSessionState#IDLE} or
 * {@link SlotsSessionState#DEBIT_ACCEPTED} -- once a spin's outcome is
 * committed it can only move forward to settlement or sideways to
 * {@link SlotsSessionState#TERMINATED}, never back to a refundable pregame
 * state.
 */
public final class SlotsStateMachine {

    private static final Map<SlotsSessionState, Set<SlotsSessionState>> ALLOWED = new EnumMap<>(SlotsSessionState.class);

    static {
        ALLOWED.put(IDLE, EnumSet.of(DEBIT_ACCEPTED, TERMINATED));
        ALLOWED.put(DEBIT_ACCEPTED, EnumSet.of(RESULT_COMMITTED, TERMINATED));
        ALLOWED.put(RESULT_COMMITTED, EnumSet.of(ANIMATING, SETTLING, TERMINATED));
        ALLOWED.put(ANIMATING, EnumSet.of(SETTLING, TERMINATED));
        ALLOWED.put(SETTLING, EnumSet.of(RESOLVED, TERMINATED));
        ALLOWED.put(RESOLVED, EnumSet.of(IDLE, TERMINATED));
        ALLOWED.put(TERMINATED, EnumSet.noneOf(SlotsSessionState.class));
    }

    private SlotsStateMachine() {
    }

    public static boolean canTransition(SlotsSessionState from, SlotsSessionState to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        Set<SlotsSessionState> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * @return {@code to}
     * @throws IllegalStateException if this transition is not permitted
     */
    public static SlotsSessionState transition(SlotsSessionState from, SlotsSessionState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal Slots state transition: " + from + " -> " + to);
        }
        return to;
    }
}
