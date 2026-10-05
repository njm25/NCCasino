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
import org.bukkit.command.CommandSender;
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
    private final Nccasino plugin;
    private Map<String, String> supported = Map.of(ENGLISH, "English");
    private final Map<String, YamlConfiguration> bundled = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
    private int overrideProblems;
    private String serverDefault = ENGLISH;
    private boolean clientDetection = true;

    public LocalizationService(Nccasino plugin) {
        this.plugin = plugin;
    }

    /**
     * Bundled catalogs are authoritative. Administrators change individual
     * strings in sparse {@code lang/overrides/<locale>.yml} files, which are
     * validated key by key; {@code lang/reference/} holds read-only copies of
     * the bundled catalogs to copy keys from. Full catalog copies written by
     * earlier development builds are moved aside once, because as overrides
     * they would hide every later wording fix.
     */
    public void load() {
        bundled.clear();
        overrides.clear();
        overrideProblems = 0;
        loadLocaleRegistry();
        for (String locale : supported.keySet()) {
            loadBundled(locale);
        }

        File languageDirectory = new File(plugin.getDataFolder(), "lang");
        File overrideDirectory = new File(languageDirectory, "overrides");
        File referenceDirectory = new File(languageDirectory, "reference");
        try {
            Files.createDirectories(overrideDirectory.toPath());
            Files.createDirectories(referenceDirectory.toPath());
            moveLegacyExports(languageDirectory);
            writeOverrideReadme(overrideDirectory);
            writeReferenceCopies(referenceDirectory);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not prepare the NCCasino lang directory.", exception);
        }
        loadOverrides(overrideDirectory);

        String configured = LocaleIds.normalize(
            plugin.getConfig().getString("language.default", ENGLISH)
        );
        if (configured == null || !supported.containsKey(configured)) {
            plugin.getLogger().warning("Unsupported language.default; falling back to " + ENGLISH + ".");
            serverDefault = ENGLISH;
        } else {
            serverDefault = configured;
        }
        clientDetection = plugin.getConfig().getBoolean("language.detect-client", true);

        validateLanguages();
    }

    public void reload() {
        load();
    }

    public String getServerDefault() {
        return serverDefault;
    }

    public Map<String, String> supportedLanguages() {
        return supported;
    }

    /** Whether {@code language.detect-client} lets players follow their Minecraft language. */
    public boolean isClientDetectionEnabled() {
        return clientDetection;
    }

    /** Maps a raw {@code Player#getLocale()} value to a registered locale, or {@code null}. */
    public String resolveClientLocale(String rawClientLocale) {
        return ClientLocaleResolver.resolve(rawClientLocale, supported.keySet());
    }

    /** The catalog a player following their client would get right now, or {@code null} for the server default. */
    public String clientLocale(UUID playerId) {
        if (!clientDetection) {
            return null;
        }
        String client = plugin.getPreferences(playerId).getClientLanguage();
        return client != null && supported.containsKey(client) ? client : null;
    }

    public String effectiveLocale(UUID playerId) {
        Preferences preferences = plugin.getPreferences(playerId);
        switch (preferences.getLanguageMode()) {
            case EXPLICIT -> {
                String explicit = preferences.getExplicitLanguage();
                if (explicit != null && supported.containsKey(explicit)) {
                    return explicit;
                }
            }
            case CLIENT -> {
                String client = clientLocale(playerId);
                if (client != null) {
                    return client;
                }
            }
            case SERVER_DEFAULT -> {
            }
        }
        return serverDefault;
    }

    public String text(Player player, String key, Object... placeholders) {
        return text(effectiveLocale(player.getUniqueId()), key, placeholderMap(placeholders));
    }

    public String text(CommandSender sender, String key, Object... placeholders) {
        return sender instanceof Player player
            ? text(player, key, placeholders)
            : text(serverDefault, key, placeholderMap(placeholders));
    }

    public String text(UUID playerId, String key, Object... placeholders) {
        return text(effectiveLocale(playerId), key, placeholderMap(placeholders));
    }

    public String text(String locale, String key) {
        return text(locale, key, Map.of());
    }

    public String text(String locale, String key, Map<String, ?> placeholders) {
        String normalized = LocaleIds.normalize(locale);
        if (normalized == null || !supported.containsKey(normalized)) {
            normalized = ENGLISH;
        }

        String value = overrides.getOrDefault(normalized, Map.of()).get(key);
        if (value == null) {
            value = validValue(bundled.get(normalized), key);
        }
        if (value == null) {
            value = overrides.getOrDefault(ENGLISH, Map.of()).get(key);
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
        return substitute(value, placeholders);
    }

    /**
     * Fills every {@code {name}} in one pass, so an inserted value that itself
     * looks like a placeholder (a dealer called "{amount}") is never filled
     * again. Unknown names are left as written.
     */
    static String substitute(String template, Map<String, ?> placeholders) {
        if (placeholders.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = placeholders.containsKey(name)
                ? String.valueOf(placeholders.get(name))
                : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
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

    /** How many override values the last load or reload rejected. */
    public int overrideProblemCount() {
        return overrideProblems;
    }

    private void moveLegacyExports(File languageDirectory) throws IOException {
        File[] legacy = languageDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (legacy == null || legacy.length == 0) {
            return;
        }
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date());
        File backup = new File(languageDirectory, "legacy-backup-" + stamp);
        Files.createDirectories(backup.toPath());
        for (File file : legacy) {
            Files.move(file.toPath(), backup.toPath().resolve(file.getName()));
        }
        plugin.getLogger().warning(
            "Moved " + legacy.length + " full language file(s) from an earlier build to "
                + backup.getPath() + ". NCCasino now uses its bundled text; copy only the strings you "
                + "changed into lang/overrides/<locale>.yml to keep them."
        );
    }

    private void writeOverrideReadme(File overrideDirectory) throws IOException {
        File readme = new File(overrideDirectory, "README.txt");
        if (readme.exists()) {
            return;
        }
        Files.writeString(readme.toPath(), String.join(System.lineSeparator(),
            "NCCasino language overrides",
            "",
            "Create <locale>.yml here (for example de_DE.yml) containing only the keys you want",
            "to change, copied from ../reference/<locale>.yml with the same nesting. Everything",
            "you leave out keeps NCCasino's bundled text, including future fixes.",
            "",
            "Each value must keep the English placeholders, such as {amount}, in the same order.",
            "Invalid values are ignored and listed in the console on startup and /ncc reload.",
            ""
        ), StandardCharsets.UTF_8);
    }

    private void writeReferenceCopies(File referenceDirectory) throws IOException {
        for (String locale : supported.keySet()) {
            try (InputStream stream = plugin.getResource("lang/" + locale + ".yml")) {
                if (stream == null) {
                    continue;
                }
                byte[] bundledBytes = stream.readAllBytes();
                java.nio.file.Path target = referenceDirectory.toPath().resolve(locale + ".yml");
                if (!Files.exists(target) || !java.util.Arrays.equals(Files.readAllBytes(target), bundledBytes)) {
                    Files.write(target, bundledBytes);
                }
            }
        }
    }

    private void loadOverrides(File overrideDirectory) {
        File[] files = overrideDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (files == null) {
            return;
        }
        YamlConfiguration english = bundled.get(ENGLISH);
        java.util.List<String> problems = new java.util.ArrayList<>();
        int acceptedCount = 0;
        for (File file : files) {
            String locale = LocaleIds.normalize(file.getName().substring(0, file.getName().length() - 4));
            if (locale == null || !supported.containsKey(locale)) {
                problems.add(file.getName() + " is not a registered locale");
                continue;
            }
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            Map<String, Object> values = new LinkedHashMap<>();
            for (String key : configuration.getKeys(true)) {
                if (!configuration.isConfigurationSection(key)) {
                    values.put(key, configuration.get(key));
                }
            }
            LanguageOverrides.Result result = LanguageOverrides.validate(
                locale,
                values,
                key -> english != null && english.isString(key) ? english.getString(key) : null
            );
            if (!result.accepted().isEmpty()) {
                overrides.put(locale, result.accepted());
                acceptedCount += result.accepted().size();
            }
            problems.addAll(result.problems());
        }
        overrideProblems = problems.size();
        if (acceptedCount > 0) {
            plugin.getLogger().info("Loaded " + acceptedCount + " language override value(s) for " + overrides.size() + " locale(s).");
        }
        if (!problems.isEmpty()) {
            plugin.getLogger().warning("Ignored " + problems.size() + " language override value(s); bundled text is used instead:");
            problems.stream().limit(20).forEach(problem -> plugin.getLogger().warning("  " + problem));
            if (problems.size() > 20) {
                plugin.getLogger().warning("  ... and " + (problems.size() - 20) + " more.");
            }
        }
    }

    private static String value(YamlConfiguration configuration, String key) {
        return configuration != null ? configuration.getString(key) : null;
    }

    private void loadLocaleRegistry() {
        try (InputStream stream = plugin.getResource(LocaleRegistry.RESOURCE)) {
            Map<String, String> names = new LinkedHashMap<>();
            for (LocaleRegistry.LocaleSpec locale : LocaleRegistry.load(stream).values()) {
                names.put(locale.id(), locale.name());
            }
            supported = Collections.unmodifiableMap(names);
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not load the locale registry; only English will be available.",
                exception
            );
            supported = Map.of(ENGLISH, "English");
        }
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
