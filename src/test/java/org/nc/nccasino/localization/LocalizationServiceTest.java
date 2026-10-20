package org.nc.nccasino.localization;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationServiceTest {
    @TempDir
    Path tempDir;

    private final YamlConfiguration config = new YamlConfiguration();
    private LocalizationService localization;

    @BeforeEach
    void setUp() {
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocalizationServiceTest"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getResource(anyString())).thenAnswer(call ->
            LocalizationServiceTest.class.getClassLoader().getResourceAsStream(call.getArgument(0)));
        localization = new LocalizationService(plugin);
    }

    @Test
    void startupKeepsOnlyEnglishAndTheServerDefaultInMemory() {
        config.set("language.default", "de_DE");
        localization.load();

        assertEquals(Set.of("en_US", "de_DE"), localization.loadedLocales());
        assertEquals("§cKeine Dealer gefunden.", localization.text("de_DE", "commands.no-dealers"));
    }

    @Test
    void aLanguageIsLoadedTheFirstTimeItIsUsed() {
        localization.load();
        assertEquals(Set.of("en_US"), localization.loadedLocales());

        assertEquals("§cAucun croupier trouvé.", localization.text("fr_FR", "commands.no-dealers"));
        assertEquals(Set.of("en_US", "fr_FR"), localization.loadedLocales());
    }

    @Test
    void reloadDropsTheLanguagesLoadedSince() {
        localization.load();
        localization.text("fr_FR", "commands.no-dealers");

        localization.reload();

        assertEquals(Set.of("en_US"), localization.loadedLocales());
    }

    @Test
    void anUnknownLanguageOrKeyFallsBackToEnglish() {
        localization.load();

        assertEquals("§cNo dealers found.", localization.text("xx_YY", "commands.no-dealers"));
        assertEquals("§c[missing:no.such.key]", localization.text("fr_FR", "no.such.key"));
    }
}
