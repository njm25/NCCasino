package org.nc.nccasino.games.Blackjack;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * Round-lifecycle coordination for {@code pregameWagerIncrements}, the
 * controller's committed-increment deque per player, consumed by
 * {@link BlackjackWagerLedger}. The controller genuinely delegates to
 * {@link #clearConsumed} -- this is not a parallel simulation of its
 * behavior, it is the behavior.
 *
 * <p>Extracted specifically because leaving this state behind after a round
 * settles or aborts was a real currency-integrity defect: a player's
 * committed increments from the just-finished round stayed in the ledger,
 * so once wagering reopened for the next round, Undo All/Undo Last could
 * refund the prior round's already-settled wager a second time, and a
 * fresh commit would land on top of the stale total instead of starting
 * clean.
 *
 * <p>Deliberately does NOT touch the controller's separate {@code
 * selectedWager} map -- a player's persistent wager-selection tool
 * (fixed denomination or All In) survives a normal round reset for as long
 * as they stay seated; only leaving the chair (or explicitly picking a
 * different selection) clears it. See BlackjackInventory#selectedWager's
 * own doc and the table redesign plan for that lifecycle split.
 */
public final class BlackjackRoundWagerLedger {

    private BlackjackRoundWagerLedger() {
    }

    /**
     * Clears every player's committed wager-increment ledger.
     * <b>Ordering invariant:</b> the caller must have already fully
     * calculated and delivered (or queued) whatever payout/refund this
     * round's committed wagers are owed -- calling this any earlier would
     * destroy the very state those calculations read. Safe to call with an
     * empty map (a no-op).
     */
    public static void clearConsumed(Map<UUID, Deque<Double>> pregameWagerIncrements) {
        pregameWagerIncrements.clear();
    }
}
