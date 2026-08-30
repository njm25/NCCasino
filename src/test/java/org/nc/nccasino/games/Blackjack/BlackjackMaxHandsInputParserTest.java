package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The admin max-hands chat prompt must never throw -- an overflowing digit
 * string previously reached an uncaught {@code Long.parseLong}
 * NumberFormatException inside the async chat handler. This parser is the
 * single place that decision is made, and BlackjackMenu's own
 * {@code handleMaxHandsInput} calls it directly (not a parallel
 * simulation).
 */
class BlackjackMaxHandsInputParserTest {

    @Test
    void negativeOneMapsToUnboundedConfigValue() {
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("-1"));
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("  -1  "));
    }

    @Test
    void legacyUnboundedAliasRemainsAcceptedCaseInsensitively() {
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("unbounded"));
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("UNBOUNDED"));
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("UnBounded"));
        assertEquals(Optional.of("UNBOUNDED"), BlackjackMaxHandsInputParser.parse("  unbounded  "));
    }

    @Test
    void integerTwoIsTheSmallestValidValue() {
        assertEquals(Optional.of("2"), BlackjackMaxHandsInputParser.parse("2"));
    }

    @Test
    void largerIntegersAreValid() {
        assertEquals(Optional.of("8"), BlackjackMaxHandsInputParser.parse("8"));
        assertEquals(Optional.of("500"), BlackjackMaxHandsInputParser.parse("500"));
    }

    @Test
    void zeroIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("0").isEmpty());
    }

    @Test
    void oneIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("1").isEmpty());
    }

    @Test
    void negativeIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("-5").isEmpty());
    }

    @Test
    void decimalIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("2.5").isEmpty());
    }

    @Test
    void emptyIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("").isEmpty());
        assertTrue(BlackjackMaxHandsInputParser.parse("   ").isEmpty());
    }

    @Test
    void nullIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse(null).isEmpty());
    }

    @Test
    void nonNumericIsRejected() {
        assertTrue(BlackjackMaxHandsInputParser.parse("banana").isEmpty());
        assertTrue(BlackjackMaxHandsInputParser.parse("5 hands").isEmpty());
    }

    @Test
    void overflowingDigitStringIsRejectedNotThrown() {
        // More digits than fit in a long -- must never let
        // Long.parseLong's NumberFormatException escape uncaught.
        assertTrue(BlackjackMaxHandsInputParser.parse("999999999999999999999999999999999999999").isEmpty());
    }
}
