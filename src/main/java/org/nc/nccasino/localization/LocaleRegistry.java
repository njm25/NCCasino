package org.nc.nccasino.localization;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads the bundled locale catalog shared by the runtime and translation tooling. */
public final class LocaleRegistry {
    public static final String RESOURCE = "lang/locales.yml";

    private LocaleRegistry() {
    }

    public static Map<String, LocaleSpec> load(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("Missing bundled " + RESOURCE + ".");
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
        ConfigurationSection locales = configuration.getConfigurationSection("locales");
        if (locales == null) {
            throw new IllegalArgumentException(RESOURCE + " must contain a locales section.");
        }

        Map<String, LocaleSpec> result = new LinkedHashMap<>();
        for (String id : locales.getKeys(false)) {
            String normalized = LocaleIds.normalize(id);
            if (!id.equals(normalized)) {
                throw new IllegalArgumentException("Invalid normalized locale id in registry: " + id);
            }
            String name = locales.getString(id + ".name");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Locale " + id + " is missing its native name.");
            }
            result.put(id, new LocaleSpec(id, name));
        }

        if (!result.containsKey(LocalizationService.ENGLISH)) {
            throw new IllegalArgumentException("The locale registry must contain " + LocalizationService.ENGLISH + ".");
        }
        return Collections.unmodifiableMap(result);
    }

    public record LocaleSpec(String id, String name) {
    }
}
