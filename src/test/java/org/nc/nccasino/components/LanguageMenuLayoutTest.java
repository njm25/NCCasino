package org.nc.nccasino.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMenuLayoutTest {
    @Test
    void currentLocaleCountUsesCompactTwoRowMenu() {
        assertEquals(18, LanguageMenu.menuSizeForLocaleCount(5));
        assertEquals(8, LanguageMenu.localeCapacity(18));
        assertEquals(1, LanguageMenu.pageCount(5, 18));
    }

    @Test
    void expandedRegistryGrowsAndEventuallyPaginates() {
        assertEquals(27, LanguageMenu.menuSizeForLocaleCount(9));
        assertEquals(54, LanguageMenu.menuSizeForLocaleCount(45));
        assertEquals(44, LanguageMenu.localeCapacity(54));
        assertEquals(2, LanguageMenu.pageCount(45, 54));
        assertEquals(3, LanguageMenu.pageCount(100, 54));
    }
}
