package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile-name rules: 1-24 characters of letters, digits, spaces, hyphens
 * and underscores, and case-insensitive uniqueness.
 *
 * <p>Rejecting the section sign and ampersand is a safety rule, not a style
 * one -- a saved name is rendered as an item display name, so a formatting
 * code smuggled into one could impersonate one of the machine's own controls.
 */
class SlotsProfileNameTest {

    @Test
    void theLengthBoundsAreOneThroughTwentyFour() {
        assertEquals(1, SlotsProfileName.MIN_LENGTH);
        assertEquals(24, SlotsProfileName.MAX_LENGTH);
    }

    @Test
    void ordinaryNamesAreAccepted() {
        for (String name : new String[] {"a", "High Roller", "wide-5x5", "my_profile_2", "Test 123"}) {
            assertNull(SlotsProfileName.validate(name), name + " must be a legal name");
            assertTrue(SlotsProfileName.isValid(name));
        }
    }

    @Test
    void aNameOfExactlyTwentyFourCharactersIsAcceptedAndTwentyFiveIsNot() {
        assertNull(SlotsProfileName.validate("a".repeat(24)));
        assertEquals(SlotsProfileName.Rejection.TOO_LONG, SlotsProfileName.validate("a".repeat(25)));
    }

    @Test
    void anEmptyOrWhitespaceOnlyNameIsRejected() {
        assertEquals(SlotsProfileName.Rejection.EMPTY, SlotsProfileName.validate(""));
        assertEquals(SlotsProfileName.Rejection.EMPTY, SlotsProfileName.validate("   "));
        assertEquals(SlotsProfileName.Rejection.EMPTY, SlotsProfileName.validate(null));
    }

    @Test
    void formattingCodesAndOtherPunctuationAreRejected() {
        String[] rejected = {
            "&aGold", "§cRed", "name!", "a.b", "50%", "quote\"", "back\\slash", "tab\there",
            "new\nline", "emoji 🎰", "co:lon", "semi;colon", "sla/sh", "brace{}"
        };
        for (String name : rejected) {
            assertEquals(SlotsProfileName.Rejection.ILLEGAL_CHARACTERS, SlotsProfileName.validate(name),
                "must reject " + name);
            assertFalse(SlotsProfileName.isValid(name));
        }
    }

    @Test
    void everyRejectionHasItsOwnLocalizationKey() {
        assertEquals("slots.profile-name-empty", SlotsProfileName.Rejection.EMPTY.messageKey());
        assertEquals("slots.profile-name-too-long", SlotsProfileName.Rejection.TOO_LONG.messageKey());
        assertEquals("slots.profile-name-illegal-characters",
            SlotsProfileName.Rejection.ILLEGAL_CHARACTERS.messageKey());
    }

    @Test
    void normalizeTrimsAndCollapsesInnerSpacesSoTwoVisuallyIdenticalNamesCannotBothExist() {
        assertEquals("High Roller", SlotsProfileName.normalize("  High   Roller  "));
        assertEquals("a b", SlotsProfileName.normalize("a     b"));
        assertEquals("solo", SlotsProfileName.normalize("solo"));
        assertNull(SlotsProfileName.normalize(null));
    }

    @Test
    void lengthIsValidatedAgainstTheTrimmedName() {
        assertNull(SlotsProfileName.validate("   ok   "));
        assertEquals(SlotsProfileName.Rejection.TOO_LONG,
            SlotsProfileName.validate("  " + "a".repeat(25) + "  "));
    }

    @Test
    void uniquenessIsCaseInsensitive() {
        assertEquals(SlotsProfileName.uniquenessKey("High Roller"),
            SlotsProfileName.uniquenessKey("high roller"));
        assertEquals(SlotsProfileName.uniquenessKey("HIGH ROLLER"),
            SlotsProfileName.uniquenessKey("hIgH rOlLeR"));
        assertNotEquals(SlotsProfileName.uniquenessKey("High Roller"),
            SlotsProfileName.uniquenessKey("High Rollers"));
        assertNull(SlotsProfileName.uniquenessKey(null));
    }

    @Test
    void uniquenessAlsoIgnoresSurroundingAndRepeatedSpaces() {
        assertEquals(SlotsProfileName.uniquenessKey("High Roller"),
            SlotsProfileName.uniquenessKey("  high    roller "));
    }
}
