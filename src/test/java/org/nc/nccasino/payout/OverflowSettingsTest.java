package org.nc.nccasino.payout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OverflowSettingsTest {

    @Test
    void playerChoiceUsesTheStoredChoiceWhenThereIsOne() {
        assertEquals(OverflowPreference.DROP,
            OverflowMode.PLAYER_CHOICE.effectivePreference(OverflowPreference.DROP, OverflowPreference.BANK));
        assertEquals(OverflowPreference.BANK,
            OverflowMode.PLAYER_CHOICE.effectivePreference(OverflowPreference.BANK, OverflowPreference.DROP));
    }

    @Test
    void playerChoiceFallsBackToTheServerDefaultForAPlayerWhoNeverChose() {
        assertEquals(OverflowPreference.BANK,
            OverflowMode.PLAYER_CHOICE.effectivePreference(null, OverflowPreference.BANK));
        assertEquals(OverflowPreference.DROP,
            OverflowMode.PLAYER_CHOICE.effectivePreference(null, OverflowPreference.DROP));
    }

    @Test
    void forcedModesOverrideTheStoredChoiceWithoutDestroyingIt() {
        assertEquals(OverflowPreference.BANK,
            OverflowMode.BANK.effectivePreference(OverflowPreference.DROP, OverflowPreference.DROP));
        assertEquals(OverflowPreference.DROP,
            OverflowMode.DROP.effectivePreference(OverflowPreference.BANK, OverflowPreference.BANK));

        // The stored value itself is untouched -- returning to PLAYER_CHOICE
        // restores exactly what the player had picked.
        assertEquals(OverflowPreference.DROP,
            OverflowMode.PLAYER_CHOICE.effectivePreference(OverflowPreference.DROP, OverflowPreference.BANK));
    }

    @Test
    void aFreshInstallDefaultsToPlayerChoiceWithBankSelected() {
        OverflowSettings defaults = OverflowSettings.defaults();

        assertEquals(OverflowMode.PLAYER_CHOICE, defaults.mode());
        assertEquals(OverflowPreference.BANK, defaults.serverDefault());
        assertEquals(36, defaults.maxDropStacks());
        assertEquals(true, defaults.clearBankBeforeWager());
    }

    @Test
    void unparseableModesAndPreferencesFallBackInsteadOfThrowing() {
        assertEquals(OverflowMode.BANK, OverflowMode.parse("nonsense", OverflowMode.BANK));
        assertEquals(OverflowMode.DROP, OverflowMode.parse(null, OverflowMode.DROP));
        assertEquals(OverflowMode.PLAYER_CHOICE, OverflowMode.parse("  player_choice  ", OverflowMode.BANK));

        assertEquals(OverflowPreference.BANK, OverflowPreference.parse("nope", OverflowPreference.BANK));
        assertEquals(OverflowPreference.DROP, OverflowPreference.parse("drop", OverflowPreference.BANK));
        assertNull(OverflowPreference.parse("nope", null));
    }

    @Test
    void dropStackCountsAreClampedToASaneRange() {
        assertEquals(0, OverflowSettings.normalizeDropStacks(-5));
        assertEquals(0, OverflowSettings.normalizeDropStacks(0));
        assertEquals(36, OverflowSettings.normalizeDropStacks(36));
        assertEquals(1024, OverflowSettings.normalizeDropStacks(999_999));
    }

    @Test
    void reminderPeriodsParseCommonSuffixes() {
        assertEquals(3600L, OverflowSettings.parseDurationSeconds("1h", 1L));
        assertEquals(1800L, OverflowSettings.parseDurationSeconds("30m", 1L));
        assertEquals(86400L, OverflowSettings.parseDurationSeconds("1d", 1L));
        assertEquals(300L, OverflowSettings.parseDurationSeconds("300", 1L));
    }

    @Test
    void aReminderPeriodCanBeDisabledExplicitly() {
        assertEquals(0L, OverflowSettings.parseDurationSeconds("0", 3600L));
        assertEquals(0L, OverflowSettings.parseDurationSeconds("off", 3600L));
        assertEquals(0L, OverflowSettings.parseDurationSeconds("none", 3600L));
    }

    @Test
    void anAbsurdlyShortReminderPeriodIsRaisedToTheMinimum() {
        assertEquals(OverflowSettings.MIN_REMINDER_PERIOD_SECONDS,
            OverflowSettings.parseDurationSeconds("1s", 3600L));
    }

    @Test
    void anUnparseableReminderPeriodKeepsTheFallback() {
        assertEquals(3600L, OverflowSettings.parseDurationSeconds("soon", 3600L));
        assertEquals(3600L, OverflowSettings.parseDurationSeconds(null, 3600L));
        assertEquals(3600L, OverflowSettings.parseDurationSeconds("   ", 3600L));
    }
}
