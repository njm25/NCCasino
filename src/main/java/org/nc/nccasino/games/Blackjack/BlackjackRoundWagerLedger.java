package org.nc.nccasino.games.Blackjack;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * Round-lifecycle coordination for the two pregame wager-ledger maps the
 * controller holds ({@code pregameWagerIncrements}, the committed-increment
 * deque per player consumed by {@link BlackjackWagerLedger}, and
 * {@code selectedWager}, a pending-but-never-committed amount per player).
 * The controller genuinely delegates to {@link #clearConsumed} -- this is
 * not a parallel simulation of its behavior, it is the behavior.
 *
 * <p>Extracted specifically because leaving this state behind after a round
 * settles or aborts was a real currency-integrity defect: a player's
 * committed increments from the just-finished round stayed in the ledger,
 * so once wagering reopened for the next round, Undo All/Undo Last could
 * refund the prior round's already-settled wager a second time, and a
 * fresh commit would land on top of the stale total instead of starting
 * clean.
 */
public final class BlackjackRoundWagerLedger {

    private BlackjackRoundWagerLedger() {
    }

    /**
     * Clears every player's committed wager-increment ledger and any
     * pending, never-committed selection. <b>Ordering invariant:</b> the
     * caller must have already fully calculated and delivered (or queued)
     * whatever payout/refund this round's committed wagers are owed --
     * calling this any earlier would destroy the very state those
     * calculations read. Safe to call with empty maps (a no-op).
     */
    public static void clearConsumed(Map<UUID, Deque<Double>> pregameWagerIncrements, Map<UUID, Double> selectedWager) {
        pregameWagerIncrements.clear();
        selectedWager.clear();
    }
}
