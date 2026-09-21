package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing a dealer's {@code budget:} block, in particular the distinction
 * between "never configured" (backward-compatible UNLIMITED) and "configured
 * with a value that is not UNLIMITED or LIMITED" (must fail closed, not fail
 * open into risk-free unlimited).
 */
class DealerBudgetSettingsTest {

    private static DealerBudgetSettings parseMode(String rawMode) {
        return DealerBudgetSettings.parse(
            rawMode, "5000", "1", "NONE", null, null, null, null);
    }

    @Test
    void anAbsentModeIsUnlimitedWithoutAnyProblem() {
        DealerBudgetSettings settings = parseMode(null);
        assertEquals(DealerBudgetMode.UNLIMITED, settings.mode());
        assertTrue(settings.isUsable());
        assertTrue(settings.problems().isEmpty(),
            "a dealer that was never configured must not be reported as a problem");
    }

    @Test
    void aBlankModeIsUnlimitedWithoutAnyProblem() {
        DealerBudgetSettings settings = parseMode("   ");
        assertEquals(DealerBudgetMode.UNLIMITED, settings.mode());
        assertTrue(settings.problems().isEmpty());
    }

    @Test
    void anExplicitInvalidModeFailsClosedRatherThanBecomingUnlimited() {
        DealerBudgetSettings settings = parseMode("LIMTED");

        assertEquals(DealerBudgetMode.LIMITED, settings.mode(),
            "a typo must never be silently indistinguishable from an absent budget block");
        assertFalse(settings.isUsable(),
            "an unrecognized mode must make the dealer refuse every wager, not become risk-free");
        assertFalse(settings.problems().isEmpty(), "the typo must be reported");
    }

    @Test
    void anExplicitInvalidModeRefusesAdmission() {
        DealerBudgetSettings settings = parseMode("LIMTED");

        AdmissionDecision decision = AdmissionPolicy.admit(settings, Money.of(1_000_000L), Exposure.of(1L, 1L));

        assertEquals(AdmissionDecision.CONFIGURATION_INVALID, decision,
            "a dealer with an unusable explicit mode must fail closed on every commitment");
    }

    @Test
    void anExplicitUnlimitedModeStillParsesNormally() {
        DealerBudgetSettings settings = parseMode("UNLIMITED");
        assertEquals(DealerBudgetMode.UNLIMITED, settings.mode());
        assertTrue(settings.isUsable());
        assertTrue(settings.problems().isEmpty());
    }

    @Test
    void anExplicitLimitedModeWithAUsableBaselineParsesNormally() {
        DealerBudgetSettings settings = parseMode("LIMITED");
        assertEquals(DealerBudgetMode.LIMITED, settings.mode());
        assertTrue(settings.isUsable());
    }
}
