package org.nc.nccasino.games.Blackjack;

/**
 * Whether a committed total wager is insurance-compatible -- insurance
 * always costs exactly half a hand's original pre-split wager (see
 * {@link BlackjackInsuranceRules#cost}), and Vault economies support exact
 * fractional balances, so a Vault wager is always representable regardless
 * of parity. Every other currency mode (plain material items, custom item
 * currency) only ever deals in whole units, so half of an <em>odd</em>
 * whole-item wager (e.g. 25 items -> a 12.5 stake) is never exactly
 * representable -- rather than silently truncating/rounding that stake to
 * zero, or probabilistically rounding it (which would make insurance's
 * cost non-deterministic, a real economic-drift risk this class exists to
 * avoid), the least-invasive policy is to reject the commit itself while
 * insurance is enabled, so half of any wager that's actually allowed to
 * exist is always a whole item.
 */
public final class BlackjackInsuranceWagerPolicy {

    private BlackjackInsuranceWagerPolicy() {
    }

    /**
     * @param totalWager      the prospective total committed wager (existing ledger total plus this increment)
     * @param insuranceEnabled this dealer's {@code insurance.enabled} config
     * @param vaultMode        whether this dealer's currency mode is Vault
     * @return true if this wager may be committed as-is
     */
    public static boolean isRepresentable(double totalWager, boolean insuranceEnabled, boolean vaultMode) {
        if (!insuranceEnabled || vaultMode) {
            return true;
        }
        // Physical-item currency is always a whole number of items by
        // construction (chip denominations, cursor-drag stack sizes) --
        // round only to absorb harmless floating-point drift, never to
        // paper over a genuinely fractional amount.
        long wholeItems = Math.round(totalWager);
        return wholeItems % 2 == 0;
    }
}
