package org.nc.nccasino.helpers;

import org.nc.nccasino.Nccasino;
import org.nc.nccasino.localization.LanguageMode;
import org.nc.nccasino.localization.LocaleIds;
import java.util.UUID;

public class Preferences {
    public enum SoundSetting { ON, OFF }
    public enum MessageSetting { NONE, STANDARD, VERBOSE }

    //private UUID playerId;
    private SoundSetting soundSetting;
    private MessageSetting messageSetting;
    private LanguageMode languageMode;
    private String explicitLanguage;
    private final Nccasino plugin;

    public Preferences(UUID playerId) {
       // this.playerId = playerId;
        this.soundSetting = SoundSetting.ON; // Default
        this.messageSetting = MessageSetting.STANDARD; // Default
        this.languageMode = LanguageMode.SERVER_DEFAULT;
        this.explicitLanguage = null;
        this.plugin = Nccasino.getPlugin(Nccasino.class); // Get plugin instance
    }

    public SoundSetting getSoundSetting() {
        return soundSetting;
    }

    public void setSoundSetting(SoundSetting setting) {
        this.soundSetting = setting;
        plugin.savePreferences(); // Save immediately when changed
    }

    public void toggleSound() {
        this.soundSetting = (this.soundSetting == SoundSetting.ON) ? SoundSetting.OFF : SoundSetting.ON;
        plugin.savePreferences();
    }

    public MessageSetting getMessageSetting() {
        return messageSetting;
    }

    public void setMessageSetting(MessageSetting setting) {
        this.messageSetting = setting;
        plugin.savePreferences();
    }

    public void cycleMessageSetting() {
        switch (this.messageSetting) {
            case NONE -> this.messageSetting = MessageSetting.STANDARD;
            case STANDARD -> this.messageSetting = MessageSetting.VERBOSE;
            case VERBOSE -> this.messageSetting = MessageSetting.NONE;
        }
        plugin.savePreferences();
    }

    public LanguageMode getLanguageMode() {
        return languageMode;
    }

    public String getExplicitLanguage() {
        return explicitLanguage;
    }

    public void useServerDefaultLanguage() {
        languageMode = LanguageMode.SERVER_DEFAULT;
        explicitLanguage = null;
        plugin.savePreferences();
    }

    public void useExplicitLanguage(String locale) {
        String normalized = LocaleIds.normalize(locale);
        if (normalized == null
            || !plugin.getLocalization().supportedLanguages().containsKey(normalized)) {
            throw new IllegalArgumentException("Unsupported locale: " + locale);
        }
        languageMode = LanguageMode.EXPLICIT;
        explicitLanguage = normalized;
        plugin.savePreferences();
    }

    /**
     * Loads persisted values without triggering a save for every field.
     */
    public void load(
        SoundSetting sound,
        MessageSetting messages,
        LanguageMode mode,
        String language
    ) {
        soundSetting = sound != null ? sound : SoundSetting.ON;
        messageSetting = messages != null ? messages : MessageSetting.STANDARD;
        String normalized = LocaleIds.normalize(language);
        if (mode == LanguageMode.EXPLICIT
            && normalized != null
            && plugin.getLocalization().supportedLanguages().containsKey(normalized)) {
            languageMode = LanguageMode.EXPLICIT;
            explicitLanguage = normalized;
        } else {
            languageMode = LanguageMode.SERVER_DEFAULT;
            explicitLanguage = null;
        }
    }

}
