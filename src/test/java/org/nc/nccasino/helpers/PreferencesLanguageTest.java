package org.nc.nccasino.helpers;

import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.localization.LanguageMode;
import org.nc.nccasino.localization.LocalizationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every stored language shape restores to the intended mode without saving. */
class PreferencesLanguageTest {
    private MockedStatic<JavaPlugin> javaPluginStatic;
    private Nccasino plugin;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(Nccasino.class);
        LocalizationService localization = Mockito.mock(LocalizationService.class);
        when(localization.supportedLanguages()).thenReturn(Map.of("en_US", "English", "de_DE", "Deutsch"));
        when(plugin.getLocalization()).thenReturn(localization);
        javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);
        javaPluginStatic.when(() -> JavaPlugin.getPlugin(Nccasino.class)).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        javaPluginStatic.close();
    }

    @Test
    void explicitChoicesAreKeptAndNormalizedEvenWhenNotInThisBuild() {
        Preferences registered = restore(LanguageMode.EXPLICIT, "de-de", null);
        assertEquals(LanguageMode.EXPLICIT, registered.getLanguageMode());
        assertEquals("de_DE", registered.getExplicitLanguage());

        Preferences missing = restore(LanguageMode.EXPLICIT, "xx_XX", null);
        assertEquals(LanguageMode.EXPLICIT, missing.getLanguageMode());
        assertEquals("xx_XX", missing.getExplicitLanguage());
    }

    @Test
    void serverDefaultIsKeptAndEverythingElseFollowsTheClient() {
        assertEquals(LanguageMode.SERVER_DEFAULT, restore(LanguageMode.SERVER_DEFAULT, null, null).getLanguageMode());

        Preferences explicitWithoutLanguage = restore(LanguageMode.EXPLICIT, " ", null);
        assertEquals(LanguageMode.CLIENT, explicitWithoutLanguage.getLanguageMode());
        assertNull(explicitWithoutLanguage.getExplicitLanguage());

        Preferences client = restore(LanguageMode.CLIENT, null, "pt-br");
        assertEquals(LanguageMode.CLIENT, client.getLanguageMode());
        assertEquals("pt_BR", client.getClientLanguage());
    }

    @Test
    void restoringSettingsNeverSaves() {
        Preferences preferences = new Preferences(UUID.randomUUID());
        preferences.loadSettings(Preferences.SoundSetting.OFF, Preferences.MessageSetting.NONE);
        assertEquals(Preferences.SoundSetting.OFF, preferences.getSoundSetting());
        assertEquals(Preferences.MessageSetting.NONE, preferences.getMessageSetting());
        verify(plugin, never()).savePreferences();
    }

    private static Preferences restore(LanguageMode mode, String language, String client) {
        Preferences preferences = new Preferences(UUID.randomUUID());
        preferences.loadLanguage(mode, language, client);
        return preferences;
    }
}
