package org.nc.nccasino.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final String[] LOCALES = {
        "en_US",
        "es_ES",
        "pt_BR",
        "de_DE",
        "fr_FR"
    };

    @Test
    void everyBundledLanguageContainsEveryEnglishKeyWithMatchingPlaceholders() {
        YamlConfiguration english = load("en_US");

        for (String locale : LOCALES) {
            YamlConfiguration translation = load(locale);
            assertEquals(locale, translation.getString("_meta.locale"));

            for (String key : english.getKeys(true)) {
                if (key.startsWith("_meta") || !english.isString(key)) {
                    continue;
                }
                assertTrue(
                    translation.isString(key),
                    () -> locale + " is missing " + key
                );
                assertEquals(
                    LocalizationService.placeholders(english.getString(key)),
                    LocalizationService.placeholders(translation.getString(key)),
                    () -> locale + " has mismatched placeholders for " + key
                );
            }
        }
    }

    @Test
    void placeholderExtractionUsesNamedTokensOnly() {
        assertEquals(
            Map.of("amount", true, "player", true).keySet(),
            LocalizationService.placeholders(
                "{player} won {amount}; {player} keeps formatting."
            )
        );
    }

    @Test
    void currentlySelectedWagerLabelIsPresentInEveryLocale() {
        for (String locale : LOCALES) {
            YamlConfiguration translation = load(locale);
            assertTrue(
                translation.isString("blackjack.currently-selected"),
                () -> locale + " is missing blackjack.currently-selected"
            );
        }
    }

    @Test
    void confirmationLabelsRemainAddressableByTheirLocalizationPaths() {
        for (String locale : LOCALES) {
            YamlConfiguration translation = load(locale);
            assertTrue(
                translation.isString("confirm.yes"),
                () -> locale + " is missing confirm.yes"
            );
            assertTrue(
                translation.isString("confirm.no"),
                () -> locale + " is missing confirm.no"
            );
        }
    }

    /**
     * The player-facing/admin-menu Blackjack Action Timer keys (formerly
     * worded "Turn Timer") must be present with a non-blank value in every
     * bundled locale -- key presence/placeholder parity is already covered
     * by {@link #everyBundledLanguageContainsEveryEnglishKeyWithMatchingPlaceholders()};
     * this additionally pins the English wording itself to the "Action
     * Timer" terminology so a future edit can't silently regress back to
     * "Turn Timer". Internal config keys (turn-timer.enabled/timeout-seconds)
     * and existing gameplay text like "Time to decide: {seconds}" are
     * deliberately out of scope -- see BlackjackInventory's own turn-timer
     * field docs.
     */
    @Test
    void blackjackActionTimerMenuTextUsesActionTimerWordingInEveryLocale() {
        String[] keys = {
            "blackjack-settings.toggle-turn-timer",
            "blackjack-settings.edit-turn-timer-timeout",
            "blackjack-settings.turn-timer-updated",
            "blackjack-settings.prompt-turn-timer-timeout",
            "blackjack-settings.turn-timer-timeout-updated"
        };
        for (String locale : LOCALES) {
            YamlConfiguration translation = load(locale);
            for (String key : keys) {
                assertTrue(translation.isString(key), () -> locale + " is missing " + key);
                assertTrue(
                    !translation.getString(key).isBlank(),
                    () -> locale + "'s " + key + " must not be blank"
                );
            }
        }

        YamlConfiguration english = load("en_US");
        for (String key : keys) {
            String value = english.getString(key);
            assertTrue(
                value.toLowerCase(java.util.Locale.ROOT).contains("action timer"),
                () -> "en_US " + key + " must read \"Action Timer\", got: " + value
            );
            assertFalse(
                value.toLowerCase(java.util.Locale.ROOT).contains("turn timer"),
                () -> "en_US " + key + " must no longer read \"Turn Timer\", got: " + value
            );
        }
    }

    private static YamlConfiguration load(String locale) {
        String path = "lang/" + locale + ".yml";
        InputStream stream =
            LocalizationResourcesTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing classpath resource " + path);
        return YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
}
