package org.nc.nccasino.components;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LanguageMode;
import org.nc.nccasino.localization.LocalizationService;

public final class LanguageMenu extends Menu {
    private static final Map<SlotOption, String> LOCALES = Map.of(
        SlotOption.LANGUAGE_ENGLISH, "en_US",
        SlotOption.LANGUAGE_SPANISH, "es_ES",
        SlotOption.LANGUAGE_PORTUGUESE, "pt_BR",
        SlotOption.LANGUAGE_GERMAN, "de_DE",
        SlotOption.LANGUAGE_FRENCH, "fr_FR"
    );

    public LanguageMenu(
        Player player,
        Nccasino plugin,
        UUID dealerId,
        Consumer<Player> returnToPreferences
    ) {
        super(
            player,
            plugin,
            dealerId,
            plugin.getLocalization().text(player, "language-menu.title"),
            18,
            plugin.getLocalization().text(player, "common.return-preferences"),
            returnToPreferences
        );
        slotMapping.put(SlotOption.LANGUAGE_SERVER_DEFAULT, 0);
        slotMapping.put(SlotOption.LANGUAGE_ENGLISH, 2);
        slotMapping.put(SlotOption.LANGUAGE_SPANISH, 3);
        slotMapping.put(SlotOption.LANGUAGE_PORTUGUESE, 4);
        slotMapping.put(SlotOption.LANGUAGE_GERMAN, 5);
        slotMapping.put(SlotOption.LANGUAGE_FRENCH, 6);
        slotMapping.put(SlotOption.RETURN, 9);
        slotMapping.put(SlotOption.EXIT, 17);
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        LocalizationService language = plugin.getLocalization();
        Preferences preferences = plugin.getPreferences(ownerId);
        String serverLanguage =
            language.supportedLanguages().get(language.getServerDefault());

        addItemAndLore(
            Material.COMPASS,
            1,
            language.text(ownerId, "language-menu.use-server-default"),
            slotMapping.get(SlotOption.LANGUAGE_SERVER_DEFAULT),
            language.text(
                ownerId,
                "language-menu.default-description",
                "language",
                serverLanguage
            ),
            preferences.getLanguageMode() == LanguageMode.SERVER_DEFAULT
                ? language.text(ownerId, "language-menu.selected")
                : language.text(ownerId, "language-menu.select")
        );

        for (Map.Entry<SlotOption, String> entry : LOCALES.entrySet()) {
            boolean selected =
                preferences.getLanguageMode() == LanguageMode.EXPLICIT
                    && entry.getValue().equals(preferences.getExplicitLanguage());
            addItemAndLore(
                Material.PAPER,
                1,
                language.supportedLanguages().get(entry.getValue()),
                slotMapping.get(entry.getKey()),
                selected
                    ? language.text(ownerId, "language-menu.selected")
                    : language.text(ownerId, "language-menu.select")
            );
        }

        addItemAndLore(
            Material.MAGENTA_GLAZED_TERRACOTTA,
            1,
            language.text(ownerId, "common.return-preferences"),
            slotMapping.get(SlotOption.RETURN)
        );
        addItemAndLore(
            Material.SPRUCE_DOOR,
            1,
            language.text(ownerId, "common.exit"),
            slotMapping.get(SlotOption.EXIT)
        );
    }

    @Override
    protected void handleCustomClick(
        SlotOption option,
        Player player,
        InventoryClickEvent event
    ) {
        Preferences preferences = plugin.getPreferences(player.getUniqueId());
        if (option == SlotOption.LANGUAGE_SERVER_DEFAULT) {
            preferences.useServerDefaultLanguage();
            refresh();
            playDefaultSound(player);
            return;
        }

        String locale = LOCALES.get(option);
        if (locale != null) {
            preferences.useExplicitLanguage(locale);
            refresh();
            playDefaultSound(player);
            return;
        }

        if (preferences.getMessageSetting() == Preferences.MessageSetting.VERBOSE) {
            player.sendMessage(
                plugin.getLocalization().text(player, "errors.invalid-language-option")
            );
        } else if (preferences.getMessageSetting() == Preferences.MessageSetting.STANDARD) {
            player.sendMessage(
                plugin.getLocalization().text(player, "errors.invalid-option")
            );
        }
    }

    private void refresh() {
        inventory.clear();
        initializeMenu();
    }
}
