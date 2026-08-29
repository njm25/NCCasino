package org.nc.nccasino.localization.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

/** Read-only view of a localization catalog used by the offline validator. */
final class Catalog {
    private final LinkedHashMap<String, String> values;

    private Catalog(LinkedHashMap<String, String> values) {
        this.values = values;
    }

    static Catalog load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new Catalog(new LinkedHashMap<>());
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        LinkedHashMap<String, String> strings = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                strings.put(key, yaml.getString(key));
            }
        }
        return new Catalog(strings);
    }

    Map<String, String> translatableValues() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getKey().startsWith("_meta.")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    String value(String key) {
        return values.get(key);
    }
}
