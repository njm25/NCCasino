package org.nc.nccasino.payout;

/**
 * The pure decision behind the universal wagering block.
 *
 * <p>Extracted from {@link WagerGate} so the rule itself can be tested without
 * a server, a player, or an inventory. The rule is deliberately blunt:
 *
 * <blockquote>Any nonzero overflow-bank balance blocks every wager.</blockquote>
 *
 * <p>Neither the wager's currency nor its funding source participates. A
 * banked emerald balance blocks a diamond wager, and a cursor-dragged stack is
 * refused exactly like a provider withdrawal -- the cursor path was previously
 * able to slip past the block precisely because it never touched the
 * withdrawal helpers, so "funding source is irrelevant" is the property worth
 * pinning down rather than assuming.
 */
public final class WagerAdmissionPolicy {

    private WagerAdmissionPolicy() {
    }

    /**
     * @param bankedUnitsRemaining what is still banked after the automatic
     *     pre-wager claim attempt has already run
     * @param funding how this wager would be debited; accepted so every call
     *     site declares it, and asserted here to make no difference
     * @return whether the wager may proceed
     */
    public static boolean admits(long bankedUnitsRemaining, WagerFunding funding) {
        // funding is intentionally not consulted: an irreversible cursor debit
        // must be blocked on exactly the same terms as a reversible one.
        return bankedUnitsRemaining <= 0;
    }
}
