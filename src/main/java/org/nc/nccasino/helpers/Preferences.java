package org.nc.nccasino.helpers;

import org.nc.nccasino.Nccasino;
import java.util.UUID;

public class Preferences {
    public enum SoundSetting { ON, OFF }
    public enum MessageSetting { NONE, STANDARD, VERBOSE }

    //private UUID playerId;
    private SoundSetting soundSetting;
    private MessageSetting messageSetting;
    private final Nccasino plugin;

    public Preferences(UUID playerId) {
       // this.playerId = playerId;
        this.soundSetting = SoundSetting.ON; // Default
        this.messageSetting = MessageSetting.STANDARD; // Default
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

    public String getSoundDisplay() {
        return (soundSetting == SoundSetting.ON) ? "§aEnabled" : "§cMuted";
    }

    public String getMessageDisplay() {
        return switch (messageSetting) {
            case NONE -> "§cMinimal";
            case STANDARD -> "§eStandard";
            case VERBOSE -> "§aVerbose";
        };
    }
}
