package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat-prompt parsers. Every Slots prompt shares the same literal
 * keywords, and every numeric prompt has to reject a pasted absurdity rather
 * than letting it overflow, become infinity, or silently round to something
 * the player did not type.
 */
class SlotsPromptValuesTest {

    // ---- shared keywords -------------------------------------------------

    @Test
    void cancelIsAcceptedCaseInsensitivelyAndWithSurroundingWhitespace() {
        assertTrue(SlotsPromptValues.isCancel("cancel"));
        assertTrue(SlotsPromptValues.isCancel("CANCEL"));
        assertTrue(SlotsPromptValues.isCancel("  Cancel  "));
        assertFalse(SlotsPromptValues.isCancel("cancelled"));
        assertFalse(SlotsPromptValues.isCancel("please cancel"));
        assertFalse(SlotsPromptValues.isCancel(null));
    }

    @Test
    void overwriteIsAcceptedTheSameWay() {
        assertTrue(SlotsPromptValues.isOverwrite("overwrite"));
        assertTrue(SlotsPromptValues.isOverwrite(" OverWrite "));
        assertFalse(SlotsPromptValues.isOverwrite("yes"));
        assertFalse(SlotsPromptValues.isOverwrite(null));
    }

    @Test
    void cancelIsCheckedBeforeAnyNumericParseInEveryPrompt() {
        assertEquals(SlotsPromptValues.Kind.CANCEL, SlotsPromptValues.parseSpinLimit("cancel").kind());
        assertEquals(SlotsPromptValues.Kind.CANCEL, SlotsPromptValues.parsePositiveAmount("CANCEL").kind());
    }

    // ---- spin limit ------------------------------------------------------

    @Test
    void spinLimitAcceptsAnyPositiveWholeNumberWithNoSmallArbitraryMaximum() {
        assertEquals(1L, SlotsPromptValues.parseSpinLimit("1").value());
        assertEquals(15L, SlotsPromptValues.parseSpinLimit("15").value());
        assertEquals(1_000_000L, SlotsPromptValues.parseSpinLimit("1000000").value());
        assertEquals(SlotsPromptValues.Kind.VALUE, SlotsPromptValues.parseSpinLimit("999999999").kind());
    }

    @Test
    void spinLimitAcceptsTheUnlimitedKeyword() {
        SlotsPromptValues.SpinLimit parsed = SlotsPromptValues.parseSpinLimit("Unlimited");
        assertEquals(SlotsPromptValues.Kind.UNLIMITED, parsed.kind());
        assertEquals(SlotsAutoSpinSettings.UNLIMITED_SPINS, parsed.value());
    }

    @Test
    void spinLimitAcceptsTheLargestStorableValue() {
        SlotsPromptValues.SpinLimit parsed =
            SlotsPromptValues.parseSpinLimit(String.valueOf(SlotsPromptValues.MAX_SPIN_LIMIT));
        assertEquals(SlotsPromptValues.Kind.VALUE, parsed.kind());
        assertEquals(SlotsPromptValues.MAX_SPIN_LIMIT, parsed.value());
    }

    @Test
    void spinLimitToleratesLeadingZeros() {
        assertEquals(7L, SlotsPromptValues.parseSpinLimit("0007").value());
        assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parseSpinLimit("0").kind());
        assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parseSpinLimit("0000").kind());
    }

    @Test
    void spinLimitRejectsAnythingThatIsNotAPlainPositiveInteger() {
        String[] rejected = {
            "", "   ", "-5", "5.5", "1e6", "5 spins", "five", "off", "1,000", "+5", "0x10", "١٢٣"
        };
        for (String input : rejected) {
            assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parseSpinLimit(input).kind(),
                "must reject " + input);
        }
        assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parseSpinLimit(null).kind());
    }

    @Test
    void spinLimitRejectsAPastedNumberTooLargeToStoreRatherThanOverflowing() {
        assertEquals(SlotsPromptValues.Kind.INVALID,
            SlotsPromptValues.parseSpinLimit("99999999999999999999999999999").kind());
        assertEquals(SlotsPromptValues.Kind.INVALID,
            SlotsPromptValues.parseSpinLimit("1".repeat(400)).kind());
    }

    // ---- amounts and multipliers ----------------------------------------

    @Test
    void amountsAcceptPositiveWholeAndDecimalValues() {
        assertEquals(250.0, SlotsPromptValues.parsePositiveAmount("250").value(), 1e-9);
        assertEquals(12.5, SlotsPromptValues.parsePositiveAmount("12.5").value(), 1e-9);
        assertEquals(0.25, SlotsPromptValues.parsePositiveAmount("0.25").value(), 1e-9);
        assertEquals(SlotsPromptValues.Kind.VALUE, SlotsPromptValues.parsePositiveAmount(" 7 ").kind());
    }

    @Test
    void amountsAcceptTheOffKeyword() {
        SlotsPromptValues.Amount parsed = SlotsPromptValues.parsePositiveAmount("OFF");
        assertEquals(SlotsPromptValues.Kind.OFF, parsed.kind());
        assertEquals(0.0, parsed.value(), 1e-9);
    }

    @Test
    void amountsRejectZeroNegativeAndMalformedInput() {
        String[] rejected = {"", "  ", "0", "0.0", "-1", "-0.5", "abc", "1,5", "1.2.3", ".5", "5.", "1e3", "unlimited"};
        for (String input : rejected) {
            assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parsePositiveAmount(input).kind(),
                "must reject " + input);
        }
        assertEquals(SlotsPromptValues.Kind.INVALID, SlotsPromptValues.parsePositiveAmount(null).kind());
    }

    @Test
    void amountsRejectAValueTooLargeToBeMeaningfulRatherThanBecomingInfinity() {
        assertEquals(SlotsPromptValues.Kind.INVALID,
            SlotsPromptValues.parsePositiveAmount("9".repeat(19)).kind());
        assertEquals(SlotsPromptValues.Kind.INVALID,
            SlotsPromptValues.parsePositiveAmount("9".repeat(400)).kind());
    }

    @Test
    void aParsedAmountIsAlwaysFiniteAndStrictlyPositive() {
        for (String input : new String[] {"1", "0.001", "1000000", "999999999999999"}) {
            SlotsPromptValues.Amount parsed = SlotsPromptValues.parsePositiveAmount(input);
            assertEquals(SlotsPromptValues.Kind.VALUE, parsed.kind(), input);
            assertTrue(Double.isFinite(parsed.value()));
            assertTrue(parsed.value() > 0.0);
        }
    }

    @Test
    void theSharedKeywordsAreExactlyTheOnesTheInstructionsAdvertise() {
        assertEquals("cancel", SlotsPromptValues.CANCEL);
        assertEquals("unlimited", SlotsPromptValues.UNLIMITED);
        assertEquals("off", SlotsPromptValues.OFF);
        assertEquals("overwrite", SlotsPromptValues.OVERWRITE);
    }
}
