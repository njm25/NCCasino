package org.nc.nccasino.components;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMenuLayoutTest {
    @Test
    void currentLocaleCountUsesCompactTwoRowMenu() {
        assertEquals(18, LanguageMenu.menuSizeForLocaleCount(5, 2));
        assertEquals(7, LanguageMenu.localeCapacity(18, 2));
        assertEquals(1, LanguageMenu.pageCount(5, 18, 2));
    }

    @Test
    void expandedRegistryGrowsAndEventuallyPaginates() {
        assertEquals(27, LanguageMenu.menuSizeForLocaleCount(9, 1));
        assertEquals(54, LanguageMenu.menuSizeForLocaleCount(45, 1));
        assertEquals(44, LanguageMenu.localeCapacity(54, 1));
        assertEquals(2, LanguageMenu.pageCount(45, 54, 1));
        assertEquals(3, LanguageMenu.pageCount(100, 54, 1));
    }

    @Test
    void fullRegistryFitsOnTwoPagesWithBothFixedButtons() {
        assertEquals(54, LanguageMenu.menuSizeForLocaleCount(86, 2));
        assertEquals(43, LanguageMenu.localeCapacity(54, 2));
        assertEquals(2, LanguageMenu.pageCount(86, 54, 2));
    }

    @Test
    void localesAreOrderedByNativeNameWithAccentsFolded() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("en_US", "English");
        names.put("cs_CZ", "Čeština");
        names.put("de_DE", "Deutsch");
        names.put("ca_ES", "Català");
        names.put("ru_RU", "Русский");
        names.put("af_ZA", "Afrikaans");
        assertEquals(
            List.of("af_ZA", "ca_ES", "cs_CZ", "de_DE", "en_US", "ru_RU"),
            LanguageMenu.displayOrder(names)
        );
    }
}
