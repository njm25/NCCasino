package org.nc.nccasino.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wagering block as a pure rule, independent of any server.
 *
 * <p>The bug these tests exist for: the gate originally lived only inside the
 * provider-withdrawal helpers, so a player dragging currency straight onto a
 * bet spot was debited by {@code setItemOnCursor(null)} without ever being
 * checked. The rule must therefore be stated in terms that make the funding
 * source irrelevant, and every funding source must be proven to obey it.
 */
class WagerAdmissionPolicyTest {

    @ParameterizedTest
    @EnumSource(WagerFunding.class)
    void anyBankedBalanceBlocksAWagerFromEveryFundingSource(WagerFunding funding) {
        assertFalse(WagerAdmissionPolicy.admits(1L, funding));
        assertFalse(WagerAdmissionPolicy.admits(900L, funding));
        assertFalse(WagerAdmissionPolicy.admits(Long.MAX_VALUE, funding));
    }

    @ParameterizedTest
    @EnumSource(WagerFunding.class)
    void anEmptyBankAdmitsAWagerFromEveryFundingSource(WagerFunding funding) {
        assertTrue(WagerAdmissionPolicy.admits(0L, funding));
    }

    @ParameterizedTest
    @EnumSource(WagerFunding.class)
    void aNegativeBalanceIsTreatedAsEmptyRatherThanBlocking(WagerFunding funding) {
        // Defensive: a bookkeeping slip must not lock a player out forever.
        assertTrue(WagerAdmissionPolicy.admits(-5L, funding));
    }

    @Test
    void theFundingSourceNeverChangesTheDecision() {
        // The whole point: an irreversible cursor debit is admitted or refused
        // on exactly the same terms as a reversible inventory withdrawal.
        for (long banked : new long[] {-1L, 0L, 1L, 64L, 10_000L, Long.MAX_VALUE}) {
            assertTrue(
                WagerAdmissionPolicy.admits(banked, WagerFunding.INVENTORY)
                    == WagerAdmissionPolicy.admits(banked, WagerFunding.CURSOR),
                "cursor and inventory wagers must be judged identically at banked=" + banked);
        }
    }

    @Test
    void bothFundingSourcesAreModelled() {
        // If a third debit mechanic is ever added, it must be declared here
        // (and gated) rather than quietly bypassing the block.
        assertTrue(WagerFunding.values().length == 2);
    }
}
