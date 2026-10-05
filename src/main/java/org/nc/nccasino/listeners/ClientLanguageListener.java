package org.nc.nccasino.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LanguageMode;
import org.nc.nccasino.localization.LocalizationService;

/**
 * Keeps each player's resolved Minecraft client language current.
 *
 * <p>The resolved catalog is stored for every player, whatever their
 * {@link LanguageMode}, but only affects text for players in
 * {@link LanguageMode#CLIENT}; a language picked in the menu is never
 * overridden. Those players are told once, in the new language, whenever
 * detection changes what they see.
 *
 * <p>{@link PlayerLocaleChangeEvent} fires before the server stores the new
 * value, so it is read from the event rather than from the player. On modern
 * servers the client reports its language before {@link PlayerJoinEvent},
 * so the join check normally already sees the real value.
 */
public class ClientLanguageListener implements Listener {
    private final Nccasino plugin;

    public ClientLanguageListener(Nccasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), event.getPlayer().getLocale());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
        // A change reported while still connecting is picked up by the join check.
        if (event.getPlayer().isOnline()) {
            refresh(event.getPlayer(), event.getLocale());
        }
    }

    private void refresh(Player player, String rawClientLocale) {
        LocalizationService language = plugin.getLocalization();
        if (!language.isClientDetectionEnabled()) {
            return;
        }
        String resolved = language.resolveClientLocale(rawClientLocale);
        Preferences preferences = plugin.getPreferences(player.getUniqueId());
        if (!preferences.updateClientLanguage(resolved)
            || preferences.getLanguageMode() != LanguageMode.CLIENT
            || resolved == null
            || resolved.equals(language.getServerDefault())
            || preferences.getMessageSetting() == Preferences.MessageSetting.NONE) {
            return;
        }
        player.sendMessage(language.text(
            player,
            "preferences.language.client-applied",
            "language",
            language.supportedLanguages().get(resolved)
        ));
    }
}
