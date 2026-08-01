package org.nc.nccasino.components;

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

public class PreferencesMenu extends Menu {
    public PreferencesMenu(
        Player player,
        Nccasino plugin,
        UUID dealerId,
        Consumer<Player> returnToPlayerMenu
    ) {
        super(
            player,
            plugin,
            dealerId,
            plugin.getLocalization().text(player, "preferences.title"),
            9,
            plugin.getLocalization().text(player, "common.return-player-menu"),
            returnToPlayerMenu
        );
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.SOUNDS, 1);
        slotMapping.put(SlotOption.MESSAGES, 2);
        slotMapping.put(SlotOption.LANGUAGE, 3);
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        Preferences preferences = plugin.getPreferences(ownerId);
        LocalizationService language = plugin.getLocalization();

        addItemAndLore(
            Material.BELL,
            1,
            language.text(ownerId, "preferences.sound.title"),
            slotMapping.get(SlotOption.SOUNDS),
            language.text(ownerId, "common.current", "value", soundDisplay(preferences)),
            language.text(ownerId, "common.click-toggle")
        );
        addItemAndLore(
            Material.BOOKSHELF,
            1,
            language.text(ownerId, "preferences.messages.title"),
            slotMapping.get(SlotOption.MESSAGES),
            language.text(ownerId, "common.current", "value", messageDisplay(preferences)),
            language.text(ownerId, "common.click-cycle")
        );
        addItemAndLore(
            Material.MAP,
            1,
            language.text(ownerId, "preferences.language.title"),
            slotMapping.get(SlotOption.LANGUAGE),
            language.text(
                ownerId,
                "preferences.language.mode",
                "mode",
                language.text(
                    ownerId,
                    preferences.getLanguageMode() == LanguageMode.SERVER_DEFAULT
                        ? "preferences.language.server-default"
                        : "preferences.language.explicit"
                )
            ),
            language.text(
                ownerId,
                "preferences.language.effective",
                "language",
                language.supportedLanguages().get(language.effectiveLocale(ownerId))
            ),
            language.text(ownerId, "common.click-choose")
        );
        addItemAndLore(
            Material.MAGENTA_GLAZED_TERRACOTTA,
            1,
            language.text(ownerId, "common.return-player-menu"),
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
        switch (option) {
            case SOUNDS -> {
                preferences.toggleSound();
                refresh();
                playDefaultSound(player);
            }
            case MESSAGES -> {
                preferences.cycleMessageSetting();
                refresh();
                playDefaultSound(player);
            }
            case LANGUAGE -> {
                LanguageMenu languageMenu = new LanguageMenu(
                    player,
                    plugin,
                    dealerId,
                    p -> {
                        PreferencesMenu preferencesMenu =
                            new PreferencesMenu(p, plugin, dealerId, returnCallback);
                        p.openInventory(preferencesMenu.getInventory());
                    }
                );
                player.openInventory(languageMenu.getInventory());
                playDefaultSound(player);
            }
            default -> {
                if (preferences.getMessageSetting() == Preferences.MessageSetting.STANDARD) {
                    player.sendMessage(
                        plugin.getLocalization().text(player, "errors.invalid-option")
                    );
                } else if (preferences.getMessageSetting() == Preferences.MessageSetting.VERBOSE) {
                    player.sendMessage(
                        plugin.getLocalization().text(
                            player,
                            "errors.invalid-preferences-option"
                        )
                    );
                }
            }
        }
    }

    private void refresh() {
        inventory.clear();
        initializeMenu();
    }

    private String soundDisplay(Preferences preferences) {
        return plugin.getLocalization().text(
            ownerId,
            preferences.getSoundSetting() == Preferences.SoundSetting.ON
                ? "preferences.sound.enabled"
                : "preferences.sound.muted"
        );
    }

    private String messageDisplay(Preferences preferences) {
        String key = switch (preferences.getMessageSetting()) {
            case NONE -> "preferences.messages.minimal";
            case STANDARD -> "preferences.messages.standard";
            case VERBOSE -> "preferences.messages.verbose";
        };
        return plugin.getLocalization().text(ownerId, key);
    }
}
