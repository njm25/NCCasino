package org.nc.nccasino.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurrencyDisplayTest {

    @Test
    void standardChipNamesDoNotDependOnPluralGrammar() {
        assertEquals("1 \u00d7 Emerald", CurrencyDisplay.chipName(CurrencyMode.STANDARD, "Emerald", 1));
        assertEquals("25 \u00d7 Emerald", CurrencyDisplay.chipName(CurrencyMode.STANDARD, "Emerald", 25));
        assertEquals("5 \u00d7 Jeton", CurrencyDisplay.chipName(CurrencyMode.STANDARD, "Jeton", 5));
    }

    @Test
    void vaultChipNamesKeepExactMoneyFormatting() {
        assertEquals("$5.00", CurrencyDisplay.chipName(CurrencyMode.VAULT, "ignored", 5));
        assertEquals("$2.75", CurrencyDisplay.chipName(CurrencyMode.VAULT, "ignored", 2.75));
    }

    @Test
    void blankStandardNameUsesExistingEmeraldFallback() {
        assertEquals("5 \u00d7 Emerald", CurrencyDisplay.chipName(CurrencyMode.STANDARD, " ", 5));
    }
}
