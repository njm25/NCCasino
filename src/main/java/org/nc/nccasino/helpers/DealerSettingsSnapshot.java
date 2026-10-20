package org.nc.nccasino.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Every setting stored under one dealer's config section, captured before
 * the dealer is despawned (which deletes the section) and written back
 * afterwards. Copying the whole section, rather than a list of known keys,
 * keeps settings added by later features too (budget, currency mode, game
 * rules).
 */
public final class DealerSettingsSnapshot {
    private final Map<String, Object> leaves;

    private DealerSettingsSnapshot(Map<String, Object> leaves) {
        this.leaves = leaves;
    }

    public static DealerSettingsSnapshot capture(ConfigurationSection root, String path) {
        Map<String, Object> leaves = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    Object value = section.get(key);
                    leaves.put(key, value instanceof List<?> list ? new ArrayList<>(list) : value);
                }
            }
        }
        return new DealerSettingsSnapshot(leaves);
    }

    /**
     * Replaces whatever is at {@code path} now with the captured settings.
     * Does nothing if there was nothing to capture, keeping fresh defaults.
     */
    public void restore(ConfigurationSection root, String path) {
        if (leaves.isEmpty()) {
            return;
        }
        root.set(path, null);
        leaves.forEach((key, value) -> root.set(path + "." + key, value));
    }
}
