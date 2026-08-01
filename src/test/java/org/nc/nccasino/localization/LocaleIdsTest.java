package org.nc.nccasino.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocaleIdsTest {
    @Test
    void normalizesCommonMinecraftLocaleSpellings() {
        assertEquals("en_US", LocaleIds.normalize("en_US"));
        assertEquals("en_US", LocaleIds.normalize("EN-us"));
        assertEquals("pt_BR", LocaleIds.normalize(" pt-br "));
        assertEquals("es", LocaleIds.normalize("ES"));
    }

    @Test
    void rejectsBlankAndMalformedIdentifiers() {
        assertNull(LocaleIds.normalize(null));
        assertNull(LocaleIds.normalize(""));
        assertNull(LocaleIds.normalize("english"));
        assertNull(LocaleIds.normalize("en_US_extra"));
        assertNull(LocaleIds.normalize("../en_US"));
    }
}
