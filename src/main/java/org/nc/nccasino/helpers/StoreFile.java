package org.nc.nccasino.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loading and saving rules shared by the data stores that rebuild their
 * whole file from memory on every save (overflow bank, pending payouts,
 * dealer budgets, Slots profiles).
 *
 * <ul>
 *   <li>A file that cannot be parsed is copied aside and the store becomes
 *       read-only: it starts empty in memory but never writes over the
 *       original, so a typo in a hand edit cannot erase balances.</li>
 *   <li>Records a store skips as unusable are kept under a top-level
 *       {@code unloaded:} section, mirroring their original path, and are
 *       written back on every save instead of disappearing.</li>
 * </ul>
 */
public final class StoreFile {
    public static final String UNLOADED = "unloaded";

    private final File file;
    private final Logger logger;
    private boolean readOnly;
    private boolean readOnlyReported;
    private YamlConfiguration raw = new YamlConfiguration();
    private final Map<String, Object> preserved = new LinkedHashMap<>();

    /** @param logger may be {@code null} in tests */
    public StoreFile(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /**
     * For files that are read, changed in one place and saved whole (such as
     * {@code dealers.yaml}): returns the parsed file, an empty one if it does
     * not exist yet, or {@code null} if it exists but cannot be parsed -- in
     * which case the caller must not save, or it would erase every entry.
     */
    public static FileConfiguration loadForUpdate(File file, Logger logger) {
        StoreFile store = new StoreFile(file, logger);
        FileConfiguration loaded = store.load();
        return store.isReadOnly() ? null : loaded;
    }

    /** Strictly parses the file; an unreadable file yields an empty, read-only store. */
    public FileConfiguration load() {
        readOnly = false;
        readOnlyReported = false;
        preserved.clear();
        raw = new YamlConfiguration();
        if (!file.exists()) {
            return raw;
        }
        try {
            raw.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            readOnly = true;
            File copy = new File(file.getParentFile(), file.getName() + ".unreadable-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
            try {
                Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyFailure) {
                log(Level.SEVERE, "Could not copy unreadable " + file.getName() + " aside.", copyFailure);
            }
            log(Level.SEVERE, file.getName() + " could not be read, so NCCasino will not change it until it is "
                + "fixed and the server restarted. A copy was saved as " + copy.getName() + ".", exception);
            return new YamlConfiguration();
        }
        copyLeaves(raw, UNLOADED, UNLOADED);
        return raw;
    }

    /** Keeps the record at {@code path} of the loaded file so the next save writes it back. */
    public void preserve(String path) {
        copyLeaves(raw, path, UNLOADED + "." + path);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Adds the preserved records to a freshly built file, for stores with their own writer. */
    public void addPreserved(FileConfiguration config) {
        preserved.forEach(config::set);
    }

    /**
     * Saves {@code config} plus every preserved record, through a temp file
     * moved into place, so an interrupted write cannot leave a half-written
     * file behind. Falls back to a non-atomic replace only where the
     * filesystem refuses an atomic move.
     *
     * @return {@code false} without writing if the original could not be read
     */
    public boolean save(FileConfiguration config) throws IOException {
        if (!canWrite()) {
            return false;
        }
        addPreserved(config);
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        return true;
    }

    /** {@code false}, logged once, while the store must not overwrite its unreadable original. */
    public boolean canWrite() {
        if (!readOnly) {
            return true;
        }
        if (!readOnlyReported) {
            readOnlyReported = true;
            log(Level.SEVERE, "Not saving " + file.getName() + " because the original could not be read; "
                + "changes are kept in memory only until the file is fixed.", null);
        }
        return false;
    }

    private void copyLeaves(ConfigurationSection from, String path, String target) {
        if (from.isConfigurationSection(path)) {
            ConfigurationSection section = from.getConfigurationSection(path);
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    preserved.put(target + "." + key, section.get(key));
                }
            }
        } else if (from.isSet(path)) {
            preserved.put(target, from.get(path));
        }
    }

    private void log(Level level, String message, Throwable cause) {
        if (logger != null) {
            logger.log(level, "[NCCasino] " + message, cause);
        }
    }
}
