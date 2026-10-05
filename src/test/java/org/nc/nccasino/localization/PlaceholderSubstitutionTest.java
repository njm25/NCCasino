package org.nc.nccasino.localization;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderSubstitutionTest {
    @Test
    void insertedValuesAreNeverSubstitutedAgain() {
        assertEquals(
            "Dealer '{amount}' paid 5.",
            LocalizationService.substitute("Dealer '{dealer}' paid {amount}.", Map.of("dealer", "{amount}", "amount", 5))
        );
    }

    @Test
    void repeatedAndUnknownPlaceholdersAreHandled() {
        assertEquals("a-a {other} $1", LocalizationService.substitute("{x}-{x} {other} {y}", Map.of("x", "a", "y", "$1")));
    }
}
