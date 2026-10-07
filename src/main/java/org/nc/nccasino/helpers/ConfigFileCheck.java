package org.nc.nccasino.helpers;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Strict parse of {@code config.yml}. Bukkit's own loader logs a YAML error
 * and carries on with an empty config; NCCasino then rebuilds every dealer
 * from defaults and saves, which would overwrite the admin's file. Checking
 * first lets the plugin keep its settings and leave the file alone instead.
 */
public final class ConfigFileCheck {

    private ConfigFileCheck() {
    }

    /** @return why {@code file} cannot be read, or {@code null} if it is readable or does not exist */
    public static String problem(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (IOException | InvalidConfigurationException exception) {
            String message = exception.getMessage();
            return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        }
    }
}
