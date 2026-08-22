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
    private boolean blackjackChairGuidanceSeen;
    private boolean blackjackWagerGuidanceSeen;
    private final Nccasino plugin;

    public Preferences(UUID playerId) {
       // this.playerId = playerId;
        this.soundSetting = SoundSetting.ON; // Default
        this.messageSetting = MessageSetting.STANDARD; // Default
        this.languageMode = LanguageMode.SERVER_DEFAULT;
        this.explicitLanguage = null;
        this.blackjackChairGuidanceSeen = false;
        this.blackjackWagerGuidanceSeen = false;
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

    /** Whether this player has ever sat down at a Blackjack table before -- once true, the chair-guidance blink never shows for them again, on any table, ever. */
    public boolean hasSeenBlackjackChairGuidance() {
        return blackjackChairGuidanceSeen;
    }

    /** Marks the Blackjack chair guidance as permanently seen for this player and persists it immediately -- never reset by anything, including a fresh round or a server restart. */
    public void markBlackjackChairGuidanceSeen() {
        if (!blackjackChairGuidanceSeen) {
            blackjackChairGuidanceSeen = true;
            plugin.savePreferences();
        }
    }

    /** Whether this player has ever selected a Blackjack wager before -- once true, the wager-guidance blink never shows for them again, on any table, ever. */
    public boolean hasSeenBlackjackWagerGuidance() {
        return blackjackWagerGuidanceSeen;
    }

    /** Marks the Blackjack wager guidance as permanently seen for this player and persists it immediately -- never reset by anything, including a fresh round or a server restart. */
    public void markBlackjackWagerGuidanceSeen() {
        if (!blackjackWagerGuidanceSeen) {
            blackjackWagerGuidanceSeen = true;
            plugin.savePreferences();
        }
    }

    /** Used only by {@link Nccasino#loadPreferences()} to restore these flags from disk without re-triggering a save. */
    public void loadBlackjackGuidanceSeen(boolean chairSeen, boolean wagerSeen) {
        this.blackjackChairGuidanceSeen = chairSeen;
        this.blackjackWagerGuidanceSeen = wagerSeen;
    }

    public void loadLanguage(
        LanguageMode mode,
        String language
    ) {
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
