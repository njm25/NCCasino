package org.nc.nccasino.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preferences-menu overflow control, as a pure rule.
 *
 * <p>The failure worth guarding against is quiet: while the server forces a
 * mode, writing the forced value into the player's own stored choice looks
 * correct on screen and only surfaces later, when the server returns to player
 * choice and the player's real preference has been replaced.
 */
class OverflowPreferenceToggleTest {

    private static OverflowSettings settings(OverflowMode mode, OverflowPreference serverDefault) {
        return new OverflowSettings(mode, serverDefault, 36, 3600L, true);
    }

    private static OverflowSettings playerChoice() {
        return settings(OverflowMode.PLAYER_CHOICE, OverflowPreference.BANK);
    }

    @Test
    void aPlayerChoiceServerLetsTheToggleFlipBothWays() {
        OverflowPreferenceToggle.Result toDrop =
            OverflowPreferenceToggle.toggle(playerChoice(), OverflowPreference.BANK);
        assertTrue(toDrop.accepted());
        assertEquals(OverflowPreference.DROP, toDrop.storedChoice());

        OverflowPreferenceToggle.Result backToBank =
            OverflowPreferenceToggle.toggle(playerChoice(), OverflowPreference.DROP);
        assertTrue(backToBank.accepted());
        assertEquals(OverflowPreference.BANK, backToBank.storedChoice());
    }

    @Test
    void togglingTwiceReturnsToTheOriginalChoice() {
        OverflowPreference start = OverflowPreference.BANK;
        OverflowPreference once = OverflowPreferenceToggle.toggle(playerChoice(), start).storedChoice();
        OverflowPreference twice = OverflowPreferenceToggle.toggle(playerChoice(), once).storedChoice();
        assertEquals(start, twice);
    }

    @Test
    void aPlayerWhoNeverChoseMovesOffTheInheritedDefaultRatherThanReselectingIt() {
        // Server default is BANK and the player has never chosen, so BANK is
        // what is in force. A first click has to mean something.
        OverflowPreferenceToggle.Result result =
            OverflowPreferenceToggle.toggle(playerChoice(), null);
        assertTrue(result.accepted());
        assertEquals(OverflowPreference.DROP, result.storedChoice());

        OverflowSettings dropDefault = settings(OverflowMode.PLAYER_CHOICE, OverflowPreference.DROP);
        assertEquals(
            OverflowPreference.BANK,
            OverflowPreferenceToggle.toggle(dropDefault, null).storedChoice());
    }

    @ParameterizedTest
    @EnumSource(value = OverflowMode.class, names = {"BANK", "DROP"})
    void aForcedServerRefusesTheClickAndLeavesTheStoredChoiceUntouched(OverflowMode forcedMode) {
        OverflowSettings forced = settings(forcedMode, OverflowPreference.BANK);

        for (OverflowPreference stored : new OverflowPreference[] {
            OverflowPreference.BANK, OverflowPreference.DROP}) {

            OverflowPreferenceToggle.Result result = OverflowPreferenceToggle.toggle(forced, stored);
            assertFalse(result.accepted(), "a forced mode must refuse the click");
            assertEquals(stored, result.storedChoice(),
                "the player's own choice must survive a forced server mode");
        }
    }

    @ParameterizedTest
    @EnumSource(value = OverflowMode.class, names = {"BANK", "DROP"})
    void aForcedServerDoesNotGiveAnUnchosenPlayerAChoice(OverflowMode forcedMode) {
        OverflowPreferenceToggle.Result result =
            OverflowPreferenceToggle.toggle(settings(forcedMode, OverflowPreference.BANK), null);
        assertFalse(result.accepted());
        assertNull(result.storedChoice(),
            "clicking a control they cannot use must not silently record a preference");
    }

    @Test
    void aRememberedChoiceBecomesEffectiveAgainWhenTheServerReleasesControl() {
        // The player picked DROP, the server then forced BANK, and clicking
        // while forced changed nothing.
        OverflowPreference stored = OverflowPreference.DROP;
        OverflowSettings forced = settings(OverflowMode.BANK, OverflowPreference.BANK);
        assertEquals(OverflowPreference.BANK, forced.effectivePreference(stored),
            "while forced, the server's value is what is in force");

        stored = OverflowPreferenceToggle.toggle(forced, stored).storedChoice();

        // The server returns to player choice.
        assertEquals(OverflowPreference.DROP, playerChoice().effectivePreference(stored),
            "the player's original choice must come back intact");
    }

    @Test
    void everyServerModeIsClassifiedAsForcedOrNot() {
        assertFalse(OverflowPreferenceToggle.isForced(playerChoice()));
        assertTrue(OverflowPreferenceToggle.isForced(settings(OverflowMode.BANK, OverflowPreference.BANK)));
        assertTrue(OverflowPreferenceToggle.isForced(settings(OverflowMode.DROP, OverflowPreference.BANK)));
        // Defensive: a missing settings block must not be treated as an
        // editable player choice.
        assertTrue(OverflowPreferenceToggle.isForced(null));
    }

    @Test
    void aMissingSettingsBlockRefusesTheClickRatherThanThrowing() {
        OverflowPreferenceToggle.Result result =
            OverflowPreferenceToggle.toggle(null, OverflowPreference.DROP);
        assertFalse(result.accepted());
        assertEquals(OverflowPreference.DROP, result.storedChoice());
    }
}
