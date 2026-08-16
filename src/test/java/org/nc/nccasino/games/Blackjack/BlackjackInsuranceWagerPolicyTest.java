package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Insurance always costs exactly half a hand's original pre-split wager.
 * Vault supports exact fractional balances, so any wager is always
 * representable there; every other currency mode is whole-unit-only, so an
 * odd whole-item wager's half is never exactly representable. This class
 * is the least-invasive policy the controller enforces at wager-commit
 * time (see BlackjackInventory#commitWagerFundsAlreadyRemoved) rather than
 * ever truncating, rounding, or probabilistically resolving an insurance
 * debit.
 */
class BlackjackInsuranceWagerPolicyTest {

    // --- Vault: always representable regardless of parity ---

    @Test
    void vaultModeAlwaysRepresentableEvenWager() {
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(20.0, true, true));
    }

    @Test
    void vaultModeAlwaysRepresentableOddWager() {
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(25.0, true, true));
    }

    // --- Insurance disabled: parity never matters, for any currency mode ---

    @Test
    void insuranceDisabledAlwaysRepresentableEvenForItemCurrency() {
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(25.0, false, false));
    }

    // --- Physical-item currency (material or custom item) with insurance enabled ---

    @Test
    void evenPhysicalWagerIsRepresentable() {
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(20.0, true, false));
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(2.0, true, false));
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(0.0, true, false));
    }

    @Test
    void oddPhysicalWagerIsNotRepresentable() {
        assertFalse(BlackjackInsuranceWagerPolicy.isRepresentable(25.0, true, false));
        assertFalse(BlackjackInsuranceWagerPolicy.isRepresentable(1.0, true, false));
        assertFalse(BlackjackInsuranceWagerPolicy.isRepresentable(99.0, true, false));
    }

    @Test
    void floatingPointDriftNearAWholeNumberIsToleratedNotMistakenForFractional() {
        // 25.0000000001 must still be read as the whole item amount 25 (odd) --
        // never silently treated as some other, unintended parity due to
        // float drift from an unrelated computation upstream.
        assertFalse(BlackjackInsuranceWagerPolicy.isRepresentable(24.9999999999, true, false));
        assertTrue(BlackjackInsuranceWagerPolicy.isRepresentable(20.0000000001, true, false));
    }
}
