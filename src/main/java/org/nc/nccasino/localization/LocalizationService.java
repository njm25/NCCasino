package org.nc.nccasino.localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.Preferences;

/**
 * YAML-backed localization with an immutable bundled-English final fallback.
 *
 * <p>AnimationMessage is deliberately outside this service: its fixed glyph
 * renderer remains English-only.
 */
public final class LocalizationService {
    public static final String ENGLISH = "en_US";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_-]*)}");
    private static final Map<String, String> SUPPORTED;

    static {
        Map<String, String> supported = new LinkedHashMap<>();
        supported.put("en_US", "English");
        supported.put("es_ES", "Español");
        supported.put("pt_BR", "Português (Brasil)");
        supported.put("de_DE", "Deutsch");
        supported.put("fr_FR", "Français");
        SUPPORTED = Collections.unmodifiableMap(supported);
    }

    private final Nccasino plugin;
    private final Map<String, YamlConfiguration> bundled = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> overrides = new LinkedHashMap<>();
    private String serverDefault = ENGLISH;

    public LocalizationService(Nccasino plugin) {
        this.plugin = plugin;
    }

    public void load() {
        bundled.clear();
        overrides.clear();

        File languageDirectory = new File(plugin.getDataFolder(), "lang");
        try {
            Files.createDirectories(languageDirectory.toPath());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not create NCCasino lang directory.", exception);
        }

        for (String locale : SUPPORTED.keySet()) {
            loadBundled(locale);
            File external = new File(languageDirectory, locale + ".yml");
            if (!external.exists()) {
                try {
                    plugin.saveResource("lang/" + locale + ".yml", false);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Missing bundled language resource: " + locale);
                }
            }
            if (external.isFile()) {
                overrides.put(locale, YamlConfiguration.loadConfiguration(external));
            }
        }

        String configured = LocaleIds.normalize(
            plugin.getConfig().getString("language.default", ENGLISH)
        );
        if (configured == null || !SUPPORTED.containsKey(configured)) {
            plugin.getLogger().warning("Unsupported language.default; falling back to " + ENGLISH + ".");
            serverDefault = ENGLISH;
        } else {
            serverDefault = configured;
        }

        validateLanguages();
    }

    public void reload() {
        load();
    }

    public String getServerDefault() {
        return serverDefault;
    }

    public Map<String, String> supportedLanguages() {
        return SUPPORTED;
    }

    public String effectiveLocale(UUID playerId) {
        Preferences preferences = plugin.getPreferences(playerId);
        if (preferences.getLanguageMode() == LanguageMode.EXPLICIT) {
            String explicit = preferences.getExplicitLanguage();
            if (explicit != null && SUPPORTED.containsKey(explicit)) {
                return explicit;
            }
        }
        return serverDefault;
    }

    public String text(Player player, String key, Object... placeholders) {
        return text(effectiveLocale(player.getUniqueId()), key, placeholderMap(placeholders));
    }

    public String text(UUID playerId, String key, Object... placeholders) {
        return text(effectiveLocale(playerId), key, placeholderMap(placeholders));
    }

    public String text(String locale, String key) {
        return text(locale, key, Map.of());
    }

    public String text(String locale, String key, Map<String, ?> placeholders) {
        String normalized = LocaleIds.normalize(locale);
        if (normalized == null || !SUPPORTED.containsKey(normalized)) {
            normalized = ENGLISH;
        }

        String value = validValue(overrides.get(normalized), key);
        if (value == null) {
            value = validValue(bundled.get(normalized), key);
        }
        if (value == null) {
            value = validValue(overrides.get(ENGLISH), key);
        }
        if (value == null) {
            value = value(bundled.get(ENGLISH), key);
        }
        if (value == null) {
            return "§c[missing:" + key + "]";
        }

        // Parse template formatting before substitution so dynamic values
        // remain opaque and cannot inject color codes.
        value = ChatColor.translateAlternateColorCodes('&', value);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace(
                "{" + entry.getKey() + "}",
                String.valueOf(entry.getValue())
            );
        }
        return value;
    }

    public static Set<String> placeholders(String value) {
        if (value == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Collections.unmodifiableSet(names);
    }

    private void loadBundled(String locale) {
        try (InputStream stream = plugin.getResource("lang/" + locale + ".yml")) {
            if (stream == null) {
                plugin.getLogger().warning("Missing bundled language resource: " + locale);
                return;
            }
            bundled.put(
                locale,
                YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
                )
            );
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not read bundled language " + locale + ".", exception);
        }
    }

    private void validateLanguages() {
        YamlConfiguration english = bundled.get(ENGLISH);
        if (english == null) {
            plugin.getLogger().severe("Bundled en_US.yml is missing; localization fallback is unavailable.");
            return;
        }
        for (Map.Entry<String, YamlConfiguration> language : bundled.entrySet()) {
            if (language.getKey().equals(ENGLISH)) {
                continue;
            }
            for (String key : english.getKeys(true)) {
                if (key.startsWith("_meta") || !english.isString(key)) {
                    continue;
                }
                String translated = language.getValue().getString(key);
                if (translated == null) {
                    plugin.getLogger().warning(language.getKey() + " is missing translation key " + key + ".");
                } else if (!placeholders(english.getString(key)).equals(placeholders(translated))) {
                    plugin.getLogger().warning(language.getKey() + " has mismatched placeholders for " + key + ".");
                }
            }
        }
    }

    private static String value(YamlConfiguration configuration, String key) {
        return configuration != null ? configuration.getString(key) : null;
    }

    private String validValue(YamlConfiguration configuration, String key) {
        String candidate = value(configuration, key);
        String canonical = value(bundled.get(ENGLISH), key);
        if (candidate == null || canonical == null) {
            return candidate;
        }
        return placeholders(candidate).equals(placeholders(canonical))
            ? candidate
            : null;
    }

    private static Map<String, Object> placeholderMap(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholder arguments must be name/value pairs.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
