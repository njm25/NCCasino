package org.nc.nccasino.components;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

/**
 * Data-driven, paginated language selection menu. Every page starts with
 * "Use Server Default" and, when client detection is on, "Match My Game
 * Language"; the locales follow, ordered by native name.
 */
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
        LocalizationService language = plugin.getLocalization();
        return menuSizeForLocaleCount(
            language.supportedLanguages().size(),
            fixedItemCount(language)
        );
    }

    private static int fixedItemCount(LocalizationService language) {
        return language.isClientDetectionEnabled() ? 2 : 1;
    }

    static int menuSizeForLocaleCount(int localeCount, int fixedItems) {
        int entries = localeCount + fixedItems;
        int rows = Math.max(2, Math.min(6, (entries + 8) / 9 + 1));
        return rows * 9;
    }

    static int localeCapacity(int inventorySize, int fixedItems) {
        return inventorySize - 9 - fixedItems;
    }

    static int pageCount(int localeCount, int inventorySize, int fixedItems) {
        int capacity = localeCapacity(inventorySize, fixedItems);
        return Math.max(1, (localeCount + capacity - 1) / capacity);
    }

    /** Locale ids ordered by native name, accents folded, so Čeština sorts with C. */
    static List<String> displayOrder(Map<String, String> namesById) {
        Collator collator = Collator.getInstance(Locale.ROOT);
        List<String> ids = new ArrayList<>(namesById.keySet());
        ids.sort((left, right) -> collator.compare(namesById.get(left), namesById.get(right)));
        return ids;
    }

    @Override
    protected void initializeMenu() {
        localeBySlot.clear();
        slotMapping.clear();
        previousSlot = -1;
        nextSlot = -1;

        LocalizationService language = plugin.getLocalization();
        Preferences preferences = plugin.getPreferences(ownerId);
        Map<String, String> names = language.supportedLanguages();
        String serverLanguage = names.get(language.getServerDefault());
        int fixedItems = fixedItemCount(language);
        int controlRowStart = inventory.getSize() - 9;
        int localeCapacity = localeCapacity(inventory.getSize(), fixedItems);
        List<String> locales = displayOrder(names);
        int pageCount = pageCount(locales.size(), inventory.getSize(), fixedItems);
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
            selectionLore(language, preferences.getLanguageMode() == LanguageMode.SERVER_DEFAULT)
        );

        if (language.isClientDetectionEnabled()) {
            String clientLocale = language.clientLocale(ownerId);
            slotMapping.put(SlotOption.LANGUAGE_CLIENT, 1);
            addItemAndLore(
                Material.SPYGLASS,
                1,
                language.text(ownerId, "language-menu.use-client"),
                1,
                clientLocale != null
                    ? language.text(
                        ownerId,
                        "language-menu.client-description",
                        "language",
                        names.get(clientLocale)
                    )
                    : language.text(
                        ownerId,
                        "language-menu.client-unavailable",
                        "language",
                        serverLanguage
                    ),
                selectionLore(language, preferences.getLanguageMode() == LanguageMode.CLIENT)
            );
        }

        int from = page * localeCapacity;
        int to = Math.min(locales.size(), from + localeCapacity);
        int slot = fixedItems;
        for (String locale : locales.subList(from, to)) {
            localeBySlot.put(slot, locale);
            boolean selected = preferences.getLanguageMode() == LanguageMode.EXPLICIT
                && locale.equals(preferences.getExplicitLanguage());
            addItemAndLore(
                Material.PAPER,
                1,
                names.get(locale),
                slot,
                selectionLore(language, selected)
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

    private String selectionLore(LocalizationService language, boolean selected) {
        return selected
            ? language.text(ownerId, "language-menu.selected")
            : language.text(ownerId, "language-menu.select");
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
        if (option == SlotOption.LANGUAGE_CLIENT) {
            preferences.useClientLanguage();
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
