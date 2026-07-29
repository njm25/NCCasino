package org.nc.nccasino.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
