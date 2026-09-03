package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the setting-control ItemStack-amount contract (control redesign Section 4). */
class SlotsStackSizeTest {

    @Test
    void heightStackMatchesVisibleRowCount() {
        assertEquals(1, SlotsStackSize.forHeight(1));
        assertEquals(3, SlotsStackSize.forHeight(3));
        assertEquals(5, SlotsStackSize.forHeight(5));
    }

    @Test
    void reelsStackMatchesColumnCount() {
        assertEquals(3, SlotsStackSize.forReels(3));
        assertEquals(5, SlotsStackSize.forReels(5));
        assertEquals(7, SlotsStackSize.forReels(7));
    }

    @Test
    void paylinesStackMatchesActiveLineCount() {
        for (int lines = 1; lines <= 9; lines++) {
            assertEquals(lines, SlotsStackSize.forPaylines(lines));
        }
    }

    @Test
    void wagerStackEqualsExactWholeAmountsFromTwoThroughSixtyFour() {
        assertEquals(2, SlotsStackSize.forWager(2.0));
        assertEquals(10, SlotsStackSize.forWager(10.0));
        assertEquals(64, SlotsStackSize.forWager(64.0));
    }

    @Test
    void wagerStackFallsBackToOneForAWagerOfOne() {
        // A stack of 1 looks identical to "no value shown" -- a wager of
        // exactly 1 must never be confused with the fallback itself, but the
        // rule is the same: fall back to 1 rather than assert a misleading
        // distinct number.
        assertEquals(1, SlotsStackSize.forWager(1.0));
    }

    @Test
    void wagerStackFallsBackToOneAboveSixtyFour() {
        assertEquals(1, SlotsStackSize.forWager(65.0));
        assertEquals(1, SlotsStackSize.forWager(1000.0));
    }

    @Test
    void wagerStackFallsBackToOneForFractionalAmounts() {
        assertEquals(1, SlotsStackSize.forWager(10.5));
        assertEquals(1, SlotsStackSize.forWager(2.01));
    }

    @Test
    void wagerStackFallsBackToOneForNonFiniteAmounts() {
        assertEquals(1, SlotsStackSize.forWager(Double.NaN));
        assertEquals(1, SlotsStackSize.forWager(Double.POSITIVE_INFINITY));
        assertEquals(1, SlotsStackSize.forWager(Double.NEGATIVE_INFINITY));
    }

    @Test
    void wagerStackFallsBackToOneForZeroOrNegative() {
        assertEquals(1, SlotsStackSize.forWager(0.0));
        assertEquals(1, SlotsStackSize.forWager(-5.0));
    }

    @Test
    void profilesStackShowsTheExactCountFromTwoUpwards() {
        for (int count = 2; count <= SlotsProfileStore.MAX_PROFILES_PER_PLAYER; count++) {
            assertEquals(count, SlotsStackSize.forProfiles(count));
        }
    }

    @Test
    void profilesStackFallsBackToOneForNoneOrOne() {
        // A stack of 1 cannot distinguish "one profile" from "none", and the
        // Ender Chest's lore already states both cases authoritatively.
        assertEquals(1, SlotsStackSize.forProfiles(0));
        assertEquals(1, SlotsStackSize.forProfiles(1));
        assertEquals(1, SlotsStackSize.forProfiles(-3));
    }

    @Test
    void profilesStackNeverExceedsALegalStack() {
        // The store caps a player at 45, but the amount must stay legal even
        // if a hand-edited file somehow presented more.
        assertEquals(64, SlotsStackSize.forProfiles(64));
        assertEquals(64, SlotsStackSize.forProfiles(500));
    }

    @Test
    void spinLimitStackShowsTheExactLimitWhenRepresentable() {
        assertEquals(2, SlotsStackSize.forSpinLimit(2L));
        assertEquals(15, SlotsStackSize.forSpinLimit(SlotsAutoSpinSettings.DEFAULT_SPIN_LIMIT));
        assertEquals(64, SlotsStackSize.forSpinLimit(64L));
    }

    @Test
    void spinLimitStackFallsBackToOneForUnlimitedOneOrTooLarge() {
        assertEquals(1, SlotsStackSize.forSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS));
        assertEquals(1, SlotsStackSize.forSpinLimit(1L));
        assertEquals(1, SlotsStackSize.forSpinLimit(65L));
        assertEquals(1, SlotsStackSize.forSpinLimit(Long.MAX_VALUE));
    }
}
