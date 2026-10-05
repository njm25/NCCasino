package org.nc.nccasino.helpers;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.games.Blackjack.BlackjackMaxHandsInputParser;
import org.nc.nccasino.games.Slots.SlotsHouseEdgeInput;
import org.nc.nccasino.games.Slots.SlotsPromptValues;
import org.nc.nccasino.games.Slots.SlotsVariance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericInputTest {
    // 25 in Arabic-Indic, Persian, Devanagari, Thai and fullwidth digits.
    private static final String[] TWENTY_FIVE = {
        "٢٥", "۲۵", "२५", "๒๕", "２５",
    };

    @Test
    void everyScriptsDigitsBecomeAscii() {
        for (String digits : TWENTY_FIVE) {
            assertEquals("25", NumericInput.normalize(digits), digits);
            assertEquals(OptionalLong.of(25), NumericInput.parseNonNegativeLong(" " + digits + " "));
        }
        // Mathematical bold digits live outside the BMP.
        assertEquals("25", NumericInput.normalize("𝟐𝟓"));
    }

    @Test
    void decimalCommaAndArabicSeparatorReadAsPoint() {
        assertEquals("2.5", NumericInput.normalizeDecimal("2,5"));
        assertEquals("2.5", NumericInput.normalizeDecimal("٢٫٥"));
        assertEquals("1,234.5", NumericInput.normalizeDecimal("1,234.5"));
        assertEquals("1,2,3", NumericInput.normalizeDecimal("1,2,3"));
    }

    @Test
    void overflowAndJunkAreRejectedWithoutThrowing() {
        assertTrue(NumericInput.parseNonNegativeLong("9999999999999999999999").isEmpty());
        assertTrue(NumericInput.parseNonNegativeLong("-5").isEmpty());
        assertTrue(NumericInput.parseNonNegativeLong("").isEmpty());
        assertTrue(NumericInput.parseNonNegativeLong("12a").isEmpty());
        assertEquals(OptionalLong.of(7), NumericInput.parseNonNegativeLong("0007"));
    }

    @Test
    void slotsPromptsAcceptNativeDigitsAndDecimalComma() {
        for (String digits : TWENTY_FIVE) {
            assertEquals(SlotsPromptValues.Kind.VALUE, SlotsPromptValues.parseSpinLimit(digits).kind());
            assertEquals(25L, SlotsPromptValues.parseSpinLimit(digits).value());
        }
        assertEquals(SlotsPromptValues.Kind.UNLIMITED, SlotsPromptValues.parseSpinLimit("－1").kind());
        assertEquals(2.5, SlotsPromptValues.parsePositiveAmount("2,5").value());
        assertEquals(2.5, SlotsPromptValues.parsePositiveAmount("۲٫۵").value());
        assertEquals(SlotsPromptValues.Kind.OFF, SlotsPromptValues.parsePositiveAmount("OFF").kind());
        assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parsePositiveAmount("1,234.5").kind());
    }

    @Test
    void houseEdgeAndMaxHandsAcceptNativeDigits() {
        assertEquals(0.025, SlotsHouseEdgeInput.parse("٢٫٥%").orElseThrow(), 1e-12);
        assertEquals(0.025, SlotsHouseEdgeInput.parse("२,५").orElseThrow(), 1e-12);
        assertTrue(SlotsHouseEdgeInput.parse("%2.5").isEmpty());
        assertEquals(Optional.of("4"), BlackjackMaxHandsInputParser.parse("٤"));
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("-1"));
    }

    @Test
    void machineKeysIgnoreATurkishDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(SlotsVariance.HIGH, SlotsVariance.parse("high", SlotsVariance.BALANCED));
            assertTrue(SlotsPromptValues.isCancel("CANCEL"));
            assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("UNBOUNDED"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
