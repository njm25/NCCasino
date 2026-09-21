package org.nc.nccasino.games.Slots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nc.nccasino.Nccasino;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Durable, per-player Slots profiles, backed by its own
 * {@code data/slots-profiles.yml} -- the same shape as
 * {@link org.nc.nccasino.payout.OverflowBankStore} and
 * {@link org.nc.nccasino.budget.DealerBudgetStore}: a store class that owns
 * one file under {@code data/}, loads it once at construction, and persists
 * synchronously on every mutation.
 *
 * <p>Profiles are keyed by player UUID only. They are deliberately not keyed
 * by dealer: a profile saved at one Slots machine must be loadable at every
 * other one, which is what {@link SlotsProfileNormalizer} exists to make
 * safe.
 *
 * <p>Order within a player's list is insertion order and is persisted
 * explicitly as a numeric index, so the Profiles view's row-major layout is
 * stable across restarts. Overwriting an existing name keeps that profile's
 * position; deleting one compacts the rest up.
 *
 * <p>Every mutation reports whether the change actually reached disk. A
 * caller must not tell the player a profile was saved off a failed write, so
 * the in-memory state is rolled back to exactly what is persisted whenever
 * the write fails.
 */
public class SlotsProfileStore {

    /** The hard per-player cap: the Profiles view is one un-paginated 45-slot canvas. */
    public static final int MAX_PROFILES_PER_PLAYER = 45;

    private static final String ROOT = "profiles";

    private final Nccasino plugin;
    private final File file;
    /** playerId -&gt; that player's profiles, in display order. */
    private final Map<UUID, List<SlotsProfile>> profiles = new LinkedHashMap<>();

    public SlotsProfileStore(Nccasino plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "slots-profiles.yml");

        load();
    }

    private synchronized void load() {
        profiles.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }

        for (String playerKey : root.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[NCCasino] Skipping malformed Slots profile owner '"
                    + playerKey + "' in slots-profiles.yml");
                continue;
            }

            ConfigurationSection playerSection = root.getConfigurationSection(playerKey);
            if (playerSection == null) {
                continue;
            }

            List<String> indexKeys = new ArrayList<>(playerSection.getKeys(false));
            indexKeys.sort(SlotsProfileStore::compareNumericKeys);

            List<SlotsProfile> loaded = new ArrayList<>();
            for (String indexKey : indexKeys) {
                ConfigurationSection entry = playerSection.getConfigurationSection(indexKey);
                if (entry == null) {
                    continue;
                }
                SlotsProfile profile = readProfile(playerKey, indexKey, entry);
                if (profile == null) {
                    continue;
                }
                if (indexOfName(loaded, profile.name()) >= 0) {
                    plugin.getLogger().warning("[NCCasino] Skipping duplicate Slots profile name '"
                        + profile.name() + "' for " + playerKey + " in slots-profiles.yml");
                    continue;
                }
                if (loaded.size() >= MAX_PROFILES_PER_PLAYER) {
                    plugin.getLogger().warning("[NCCasino] Player " + playerKey
                        + " has more than " + MAX_PROFILES_PER_PLAYER
                        + " stored Slots profiles; the extras were not loaded.");
                    break;
                }
                loaded.add(profile);
            }
            if (!loaded.isEmpty()) {
                profiles.put(playerId, loaded);
            }
        }
    }

    private SlotsProfile readProfile(String playerKey, String indexKey, ConfigurationSection entry) {
        String rawName = entry.getString("name");
        String name = SlotsProfileName.normalize(rawName);
        if (!SlotsProfileName.isValid(name)) {
            plugin.getLogger().warning("[NCCasino] Skipping Slots profile '" + playerKey + "." + indexKey
                + "' with an illegal name in slots-profiles.yml");
            return null;
        }
        int height = SlotsGeometry.normalizeRowCount(entry.getInt("height", SlotsConfig.DEFAULT_ROWS));
        int reels = SlotsGeometry.normalizeColumnCount(entry.getInt("reels", SlotsConfig.DEFAULT_COLUMNS));
        int paylines = SlotsPaylineCatalog.normalizeLineCount(
            height, entry.getInt("paylines", SlotsConfig.DEFAULT_LINES));
        double wager = entry.getDouble("wager-per-line", 0.0);
        SlotsSpinSpeed speed = SlotsSpinSpeed.parse(entry.getString("spin-speed"));
        SlotsAutoSpinSettings auto = SlotsAutoSpinSettings.of(
            entry.getLong("auto.spin-limit", SlotsAutoSpinSettings.DEFAULT_SPIN_LIMIT),
            entry.getBoolean("auto.stop-on-any-win", false),
            entry.getDouble("auto.big-win-multiplier", 0.0),
            entry.getDouble("auto.profit-target", 0.0),
            entry.getDouble("auto.loss-limit", 0.0));
        return new SlotsProfile(name, height, reels, paylines, wager, speed, auto);
    }

    private static int compareNumericKeys(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }

    private synchronized boolean persist() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, List<SlotsProfile>> playerEntry : profiles.entrySet()) {
            List<SlotsProfile> list = playerEntry.getValue();
            for (int i = 0; i < list.size(); i++) {
                SlotsProfile profile = list.get(i);
                String base = ROOT + "." + playerEntry.getKey() + "." + i;
                config.set(base + ".name", profile.name());
                config.set(base + ".height", profile.height());
                config.set(base + ".reels", profile.reels());
                config.set(base + ".paylines", profile.paylines());
                config.set(base + ".wager-per-line", profile.wagerPerLine());
                config.set(base + ".spin-speed", profile.spinSpeed().name());
                config.set(base + ".auto.spin-limit", profile.autoSettings().spinLimit());
                config.set(base + ".auto.stop-on-any-win", profile.autoSettings().stopOnAnyWin());
                config.set(base + ".auto.big-win-multiplier", profile.autoSettings().bigWinMultiplier());
                config.set(base + ".auto.profit-target", profile.autoSettings().profitTarget());
                config.set(base + ".auto.loss-limit", profile.autoSettings().lossLimit());
            }
        }

        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NCCasino] Failed to save slots-profiles.yml", e);
            return false;
        }
    }

    /** This player's profiles in display (row-major) order. Never null; never modifiable. */
    public synchronized List<SlotsProfile> profilesFor(UUID playerId) {
        List<SlotsProfile> list = profiles.get(playerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public synchronized int countFor(UUID playerId) {
        List<SlotsProfile> list = profiles.get(playerId);
        return list == null ? 0 : list.size();
    }

    public synchronized boolean isFullFor(UUID playerId) {
        return countFor(playerId) >= MAX_PROFILES_PER_PLAYER;
    }

    /** The profile at a Profiles-view slot index, or {@code null} if that position is empty. */
    public synchronized SlotsProfile profileAt(UUID playerId, int index) {
        List<SlotsProfile> list = profiles.get(playerId);
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** Whether this player already has a profile with {@code name}, ignoring case. */
    public synchronized boolean hasProfileNamed(UUID playerId, String name) {
        List<SlotsProfile> list = profiles.get(playerId);
        return list != null && indexOfName(list, name) >= 0;
    }

    /**
     * Saves {@code profile}, replacing any same-named profile in place.
     *
     * @param overwrite whether replacing an existing same-named profile is
     *     authorized; a duplicate without this returns
     *     {@link SaveResult#DUPLICATE} rather than silently overwriting
     */
    public synchronized SaveResult save(UUID playerId, SlotsProfile profile, boolean overwrite) {
        if (playerId == null || profile == null) {
            return SaveResult.FAILED;
        }
        List<SlotsProfile> list = profiles.computeIfAbsent(playerId, id -> new ArrayList<>());
        List<SlotsProfile> snapshot = new ArrayList<>(list);

        int existing = indexOfName(list, profile.name());
        if (existing >= 0) {
            if (!overwrite) {
                return SaveResult.DUPLICATE;
            }
            list.set(existing, profile);
        } else {
            if (list.size() >= MAX_PROFILES_PER_PLAYER) {
                return SaveResult.FULL;
            }
            list.add(profile);
        }

        if (persist()) {
            return existing >= 0 ? SaveResult.OVERWROTE : SaveResult.SAVED;
        }
        restore(playerId, snapshot);
        return SaveResult.FAILED;
    }

    /**
     * Deletes the profile at {@code index}, compacting the rest up so the
     * Profiles view's positions stay contiguous.
     *
     * @return whether a profile was actually removed and the removal reached disk
     */
    public synchronized boolean deleteAt(UUID playerId, int index) {
        List<SlotsProfile> list = profiles.get(playerId);
        if (list == null || index < 0 || index >= list.size()) {
            return false;
        }
        List<SlotsProfile> snapshot = new ArrayList<>(list);
        list.remove(index);
        if (list.isEmpty()) {
            profiles.remove(playerId);
        }
        if (persist()) {
            return true;
        }
        restore(playerId, snapshot);
        return false;
    }

    private void restore(UUID playerId, List<SlotsProfile> snapshot) {
        if (snapshot.isEmpty()) {
            profiles.remove(playerId);
        } else {
            profiles.put(playerId, new ArrayList<>(snapshot));
        }
    }

    private static int indexOfName(List<SlotsProfile> list, String name) {
        String key = SlotsProfileName.uniquenessKey(name);
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (key.equals(SlotsProfileName.uniquenessKey(list.get(i).name()))) {
                return i;
            }
        }
        return -1;
    }

    /** What a {@link #save} attempt did. */
    public enum SaveResult {
        /** Stored as a brand-new profile. */
        SAVED,
        /** Replaced an existing same-named profile, keeping its position. */
        OVERWROTE,
        /** A profile with that name already exists and overwriting was not authorized. */
        DUPLICATE,
        /** This player already has {@link #MAX_PROFILES_PER_PLAYER} profiles. */
        FULL,
        /** The change could not be persisted; nothing was stored. */
        FAILED;

        public boolean succeeded() {
            return this == SAVED || this == OVERWROTE;
        }
    }
}
