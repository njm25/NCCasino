package org.nc.nccasino.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Brings an existing {@code config.yml} up to the bundled options.
 *
 * <p>{@code saveDefaultConfig} only writes a config that does not exist yet,
 * so a server upgrading from an older release would otherwise run new
 * options on hidden defaults it never sees. Missing global options are added
 * with their bundled values (existing values always win), after a backup, and
 * the administrator is told what was added. {@code config.reference.yml}
 * always holds the bundled file with its explanatory comments.
 */
public final class ConfigMigration {
    public static final int CURRENT_VERSION = 2;
    static final String VERSION_KEY = "config-version";
    private static final String DEALERS = "dealers";

    private ConfigMigration() {
    }

    public static void apply(JavaPlugin plugin) {
        byte[] bundledBytes;
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return;
            }
            bundledBytes = stream.readAllBytes();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled config.yml.", exception);
            return;
        }
        writeReference(plugin, bundledBytes);

        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
            new InputStreamReader(new java.io.ByteArrayInputStream(bundledBytes), StandardCharsets.UTF_8)
        );
        FileConfiguration config = plugin.getConfig();
        List<String> added = missingOptions(bundled, config);
        int version = config.getInt(VERSION_KEY, 1);
        if (added.isEmpty() && version >= CURRENT_VERSION) {
            return;
        }
        if (version > CURRENT_VERSION) {
            plugin.getLogger().warning("config.yml was written by a newer NCCasino (config-version " + version + "); it is left unchanged.");
            return;
        }

        Path file = new java.io.File(plugin.getDataFolder(), "config.yml").toPath();
        try {
            if (Files.exists(file)) {
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                Files.copy(file, file.resolveSibling("config.yml.backup-" + stamp), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not back up config.yml; it was not migrated.", exception);
            return;
        }
        for (String key : added) {
            config.set(key, bundled.get(key));
        }
        config.set(VERSION_KEY, CURRENT_VERSION);
        plugin.saveConfig();

        if (!added.isEmpty()) {
            plugin.getLogger().warning("Added " + added.size() + " new option(s) to config.yml with their default values: "
                + String.join(", ", added) + ". See config.reference.yml for what each one does; a backup of the "
                + "previous config.yml was saved next to it.");
            if (added.stream().anyMatch(key -> key.startsWith("payouts."))) {
                plugin.getLogger().warning("Winnings that do not fit in a player's inventory are now held for them by "
                    + "default (payouts.overflow-mode: PLAYER_CHOICE). Change payouts.* in config.yml to restore dropping.");
            }
        }
    }

    /** Bundled global options the live config lacks; per-dealer data is never touched. */
    static List<String> missingOptions(YamlConfiguration bundled, FileConfiguration config) {
        List<String> missing = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key) || key.equals(VERSION_KEY)
                || key.equals(DEALERS) || key.startsWith(DEALERS + ".")) {
                continue;
            }
            if (!config.isSet(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    private static void writeReference(JavaPlugin plugin, byte[] bundledBytes) {
        Path reference = new java.io.File(plugin.getDataFolder(), "config.reference.yml").toPath();
        try {
            Files.createDirectories(reference.getParent());
            if (!Files.exists(reference) || !Arrays.equals(Files.readAllBytes(reference), bundledBytes)) {
                Files.write(reference, bundledBytes);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write config.reference.yml.", exception);
        }
    }
}
