package org.nc.nccasino.localization;

import org.junit.jupiter.api.Test;

import java.text.Bidi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signed amount in a right-to-left line, laid out with the Unicode bidi
 * algorithm the way Minecraft's client lays it out (paragraph direction from
 * the first strong letter, color codes not part of the text).
 */
class RightToLeftSignsTest {

    @Test
    void aLossInHebrewShowsItsMinusOnTheNumbersLeft() {
        String line = "§f§lסך הכול: -5 emeralds";
        assertTrue(screen(line).contains("5-"), "the bug: " + screen(line));

        String fixed = RightToLeftSigns.apply("he_IL", line);

        assertEquals("§f§lסך הכול: 5- emeralds", fixed);
        assertTrue(screen(fixed).startsWith("emeralds -5 "), screen(fixed));
    }

    @Test
    void vaultMoneyAndWinsToo() {
        assertTrue(screen(RightToLeftSigns.apply("he_IL", "סך הכול: -$5.00")).startsWith("-$5.00 "));
        assertTrue(screen(RightToLeftSigns.apply("he_IL", "§aיד 1: ניצחון +1,250 emeralds")).startsWith("emeralds +1,250 "));
    }

    @Test
    void arabicAndPersianDigitsFollowTheArabicRules() {
        for (String[] localeAndLine : new String[][] {
            {"ar_SA", "§cالإجمالي: -5 emeralds"},
            {"fa_IR", "§cمجموع: -5 emeralds"},
        }) {
            String line = localeAndLine[1];
            assertTrue(screen(line).contains("5-"), "the bug: " + screen(line));
            String shown = screen(RightToLeftSigns.apply(localeAndLine[0], line));
            assertTrue(shown.startsWith("emeralds -5 "), localeAndLine[0] + ": " + shown);
        }
    }

    @Test
    void aSignAfterALeftToRightWordAlreadyShowsCorrectlyAndIsLeftAlone() {
        String line = "§7הימור של Steve -5";
        assertTrue(screen(line).contains("-5"));
        assertEquals(line, RightToLeftSigns.apply("he_IL", line));
    }

    @Test
    void onlySignsOfNumbersAreMoved() {
        assertEquals("שורות 1-5", RightToLeftSigns.apply("he_IL", "שורות 1-5"), "a range");
        assertEquals("ערך מ־1", RightToLeftSigns.apply("he_IL", "ערך מ־1"), "a maqaf");
        assertEquals("לחיצה - יציאה", RightToLeftSigns.apply("he_IL", "לחיצה - יציאה"), "a dash");
        assertEquals("§cהפסד -$", RightToLeftSigns.apply("he_IL", "§cהפסד -$"));
    }

    @Test
    void leftToRightLocalesAreUntouched() {
        assertEquals("Total: -5 emeralds", RightToLeftSigns.apply("en_US", "Total: -5 emeralds"));
        assertFalse(RightToLeftSigns.isRightToLeft("de_DE"));
        assertTrue(RightToLeftSigns.isRightToLeft("yi_DE"));
    }

    /** The line as drawn, read left to right across the screen. */
    private static String screen(String line) {
        String text = line.replaceAll("§.", "");
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int[] levels = new int[text.length()];
        int highest = 0;
        int lowestOdd = Integer.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            levels[i] = bidi.getLevelAt(i);
            highest = Math.max(highest, levels[i]);
            if ((levels[i] & 1) == 1) {
                lowestOdd = Math.min(lowestOdd, levels[i]);
            }
        }
        char[] chars = text.toCharArray();
        // Rule L2: from the highest level down to the lowest odd one, reverse
        // every run at that level or higher.
        for (int level = highest; level >= lowestOdd && level > 0; level--) {
            int i = 0;
            while (i < chars.length) {
                if (levels[i] < level) {
                    i++;
                    continue;
                }
                int end = i;
                while (end < chars.length && levels[end] >= level) {
                    end++;
                }
                reverse(chars, levels, i, end);
                i = end;
            }
        }
        return new String(chars);
    }

    private static void reverse(char[] chars, int[] levels, int from, int to) {
        for (int a = from, b = to - 1; a < b; a++, b--) {
            char c = chars[a];
            chars[a] = chars[b];
            chars[b] = c;
            int l = levels[a];
            levels[a] = levels[b];
            levels[b] = l;
        }
    }
}
