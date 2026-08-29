package org.nc.nccasino.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/** Data-driven, paginated language selection menu. */
public final class LanguageMenu extends Menu {
    private final Map<Integer, String> localeBySlot = new HashMap<>();
    private int page;
    private int previousSlot = -1;
    private int nextSlot = -1;

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
            menuSize(plugin),
            plugin.getLocalization().text(player, "common.return-preferences"),
            returnToPreferences
        );
        initializeMenu();
    }

    private static int menuSize(Nccasino plugin) {
        return menuSizeForLocaleCount(plugin.getLocalization().supportedLanguages().size());
    }

    static int menuSizeForLocaleCount(int localeCount) {
        int entries = localeCount + 1;
        int rows = Math.max(2, Math.min(6, (entries + 8) / 9 + 1));
        return rows * 9;
    }

    static int localeCapacity(int inventorySize) {
        return inventorySize - 10;
    }

    static int pageCount(int localeCount, int inventorySize) {
        int capacity = localeCapacity(inventorySize);
        return Math.max(1, (localeCount + capacity - 1) / capacity);
    }

    @Override
    protected void initializeMenu() {
        localeBySlot.clear();
        slotMapping.clear();
        previousSlot = -1;
        nextSlot = -1;

        LocalizationService language = plugin.getLocalization();
        Preferences preferences = plugin.getPreferences(ownerId);
        String serverLanguage = language.supportedLanguages().get(language.getServerDefault());
        int controlRowStart = inventory.getSize() - 9;
        int localeCapacity = localeCapacity(inventory.getSize());
        List<Map.Entry<String, String>> locales = new ArrayList<>(
            language.supportedLanguages().entrySet()
        );
        int pageCount = pageCount(locales.size(), inventory.getSize());
        page = Math.min(page, pageCount - 1);

        slotMapping.put(SlotOption.LANGUAGE_SERVER_DEFAULT, 0);
        addItemAndLore(
            Material.COMPASS,
            1,
            language.text(ownerId, "language-menu.use-server-default"),
            0,
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

        int from = page * localeCapacity;
        int to = Math.min(locales.size(), from + localeCapacity);
        int slot = 1;
        for (Map.Entry<String, String> locale : locales.subList(from, to)) {
            localeBySlot.put(slot, locale.getKey());
            boolean selected = preferences.getLanguageMode() == LanguageMode.EXPLICIT
                && locale.getKey().equals(preferences.getExplicitLanguage());
            addItemAndLore(
                Material.PAPER,
                1,
                locale.getValue(),
                slot,
                selected
                    ? language.text(ownerId, "language-menu.selected")
                    : language.text(ownerId, "language-menu.select")
            );
            slot++;
        }

        slotMapping.put(SlotOption.RETURN, controlRowStart);
        slotMapping.put(SlotOption.EXIT, inventory.getSize() - 1);
        addItemAndLore(
            Material.MAGENTA_GLAZED_TERRACOTTA,
            1,
            language.text(ownerId, "common.return-preferences"),
            controlRowStart
        );
        addItemAndLore(
            Material.SPRUCE_DOOR,
            1,
            language.text(ownerId, "common.exit"),
            inventory.getSize() - 1
        );

        if (page > 0) {
            previousSlot = controlRowStart + 3;
            addItemAndLore(
                Material.ARROW,
                1,
                language.text(ownerId, "common.previous-page"),
                previousSlot
            );
        }
        if (page + 1 < pageCount) {
            nextSlot = controlRowStart + 5;
            addItemAndLore(
                Material.ARROW,
                1,
                language.text(ownerId, "common.next-page"),
                nextSlot
            );
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        String locale = localeBySlot.get(slot);
        if (locale != null) {
            plugin.getPreferences(player.getUniqueId()).useExplicitLanguage(locale);
            refresh();
            playDefaultSound(player);
            return;
        }
        if (slot == previousSlot) {
            page--;
            refresh();
            playDefaultSound(player);
            return;
        }
        if (slot == nextSlot) {
            page++;
            refresh();
            playDefaultSound(player);
            return;
        }
        super.handleClick(slot, player, event);
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

        if (preferences.getMessageSetting() == Preferences.MessageSetting.VERBOSE) {
            player.sendMessage(
                plugin.getLocalization().text(player, "errors.invalid-language-option")
            );
        } else if (preferences.getMessageSetting() == Preferences.MessageSetting.STANDARD) {
            player.sendMessage(plugin.getLocalization().text(player, "errors.invalid-option"));
        }
    }

    private void refresh() {
        inventory.clear();
        initializeMenu();
    }
}
