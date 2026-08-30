package org.nc.nccasino.payout;

/**
 * Where a wager's money is coming from at the moment it is committed.
 *
 * <p>This exists to make the two genuinely different debit mechanics explicit
 * at every call site. {@link #INVENTORY} runs through a provider withdrawal
 * that can be checked, rolled back and reported on; {@link #CURSOR} is a
 * player dragging real currency onto a bet spot, where the debit is simply
 * clearing the cursor stack and is irreversible the instant it happens.
 *
 * <p>The distinction is deliberately NOT allowed to affect whether a wager is
 * admitted -- see {@link WagerAdmissionPolicy}. It exists so a reader can see
 * which paths are irreversible, and so tests can prove both are gated.
 */
public enum WagerFunding {
    /** Debited via a provider withdrawal from the player's inventory. */
    INVENTORY,
    /** Debited by clearing currency the player dragged onto a bet spot. */
    CURSOR
}
