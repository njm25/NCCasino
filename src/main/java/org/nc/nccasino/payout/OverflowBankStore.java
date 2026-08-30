package org.nc.nccasino.payout;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Durable per-player balances of item winnings that are already awarded but
 * could not physically fit anywhere. Backed by its own
 * {@code data/overflow-bank.yml}, deliberately separate from both
 * {@code preferences.yml} (not a preference) and
 * {@code pending-payouts.yml} (not an unresolved outcome).
 *
 * <p>The distinction from {@link PendingPayoutStore} is the entire point of
 * this class and must not be blurred: a pending payout means "this result
 * still needs to be delivered and the originating game still owes it"; a
 * bank balance means "this already belongs to the player and is only
 * waiting for inventory space." A pending item payout that overflows on
 * delivery therefore moves into this store, never the other way around.
 *
 * <p>Write ordering mirrors {@link PendingPayoutStore} and is deliberate:
 * <ul>
 *   <li>{@link #credit} reports success only once the balance is actually
 *       on disk, so a caller must never report a payout settled off a
 *       failed write.</li>
 *   <li>{@link #debit} is called only after the items have already physically
 *       reached the player, so it never rolls back its in-memory removal on a
 *       persist failure -- re-adding a balance the player is already holding
 *       would pay them twice. It logs loudly instead.</li>
 * </ul>
 *
 * <p>Balances are {@code long} unit counts rather than {@code double}: a
 * banked balance can legitimately be enormous now that Slots has no payout
 * ceiling, and a {@code double} would start silently losing whole items
 * above 2^53.
 */
public class OverflowBankStore {

    private final Nccasino plugin;
    private final File file;
    /** playerId -&gt; storageKey -&gt; entry. Insertion-ordered for stable files. */
    private final Map<UUID, Map<String, Entry>> balances = new LinkedHashMap<>();

    /** One currency's banked balance for one player. */
    public record Entry(BankedCurrency currency, long amount) {
    }

    public OverflowBankStore(Nccasino plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "overflow-bank.yml");

        load();
    }

    private synchronized void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("banks");
        if (root == null) {
            return;
        }

        for (String playerKey : root.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[NCCasino] Skipping malformed overflow-bank player id '"
                    + playerKey + "' in overflow-bank.yml");
                continue;
            }

            ConfigurationSection playerSection = root.getConfigurationSection(playerKey);
            if (playerSection == null) {
                continue;
            }

            for (String currencyKey : playerSection.getKeys(false)) {
                try {
                    CurrencyMode mode = CurrencyMode.valueOf(
                        playerSection.getString(currencyKey + ".mode", "STANDARD"));
                    String material = playerSection.getString(currencyKey + ".material", null);
                    String name = playerSection.getString(currencyKey + ".name", "");
                    long amount = playerSection.getLong(currencyKey + ".amount", 0L);
                    if (amount <= 0) {
                        continue;
                    }
                    BankedCurrency currency = new BankedCurrency(mode, material, name);
                    balances.computeIfAbsent(playerId, k -> new LinkedHashMap<>())
                        .put(currency.storageKey(), new Entry(currency, amount));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "[NCCasino] Skipping malformed overflow-bank entry '"
                        + playerKey + "." + currencyKey + "' in overflow-bank.yml", e);
                }
            }
        }
    }

    private synchronized boolean persist() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Entry>> playerEntry : balances.entrySet()) {
            for (Map.Entry<String, Entry> currencyEntry : playerEntry.getValue().entrySet()) {
                Entry entry = currencyEntry.getValue();
                String base = "banks." + playerEntry.getKey() + "." + currencyEntry.getKey();
                config.set(base + ".mode", entry.currency().mode().name());
                config.set(base + ".material", entry.currency().material());
                config.set(base + ".name", entry.currency().name());
                config.set(base + ".amount", entry.amount());
            }
        }

        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NCCasino] Failed to save overflow-bank.yml", e);
            return false;
        }
    }

    /**
     * Adds {@code amount} to the player's balance for {@code currency},
     * returning {@code true} only if the new balance reached disk. On a
     * failed write the in-memory change is rolled back so the store matches
     * what is actually persisted, and the caller must treat the payout as
     * unsettled rather than delivered.
     */
    public synchronized boolean credit(UUID playerId, BankedCurrency currency, long amount) {
        if (playerId == null || currency == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }

        Map<String, Entry> forPlayer = balances.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
        String key = currency.storageKey();
        Entry previous = forPlayer.get(key);
        long previousAmount = previous == null ? 0L : previous.amount();

        long updated;
        try {
            updated = Math.addExact(previousAmount, amount);
        } catch (ArithmeticException e) {
            plugin.getLogger().severe("[NCCasino] Refusing overflow-bank credit that would overflow a long for "
                + playerId + " (" + key + ", existing=" + previousAmount + ", adding=" + amount + ").");
            return false;
        }

        forPlayer.put(key, new Entry(currency, updated));

        if (persist()) {
            return true;
        }

        // Roll back to exactly the prior state so nothing is reported banked
        // that is not actually on disk.
        if (previous == null) {
            forPlayer.remove(key);
            if (forPlayer.isEmpty()) {
                balances.remove(playerId);
            }
        } else {
            forPlayer.put(key, previous);
        }
        return false;
    }

    /**
     * Removes {@code amount} from a balance whose items have ALREADY reached
     * the player. Never rolls back on a persist failure: the player is
     * holding the items, so restoring the balance would hand them the same
     * winnings again on the next claim. A stale on-disk balance is logged
     * for manual reconciliation instead, which is strictly the safer of the
     * two failure modes.
     */
    public synchronized void debit(UUID playerId, BankedCurrency currency, long amount) {
        if (playerId == null || currency == null || amount <= 0) {
            return;
        }
        Map<String, Entry> forPlayer = balances.get(playerId);
        if (forPlayer == null) {
            return;
        }
        String key = currency.storageKey();
        Entry existing = forPlayer.get(key);
        if (existing == null) {
            return;
        }

        long remaining = Math.max(0L, existing.amount() - amount);
        if (remaining == 0) {
            forPlayer.remove(key);
            if (forPlayer.isEmpty()) {
                balances.remove(playerId);
            }
        } else {
            forPlayer.put(key, new Entry(existing.currency(), remaining));
        }

        if (!persist()) {
            plugin.getLogger().severe("[NCCasino] Delivered " + amount + " banked " + key + " to " + playerId
                + " but failed to persist the updated overflow-bank balance. overflow-bank.yml may still show the"
                + " old balance and should be reconciled manually to avoid handing out the same winnings twice.");
        }
    }

    public synchronized List<Entry> entriesFor(UUID playerId) {
        Map<String, Entry> forPlayer = balances.get(playerId);
        if (forPlayer == null || forPlayer.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(forPlayer.values());
    }

    public synchronized long balanceOf(UUID playerId, BankedCurrency currency) {
        Map<String, Entry> forPlayer = balances.get(playerId);
        if (forPlayer == null || currency == null) {
            return 0L;
        }
        Entry entry = forPlayer.get(currency.storageKey());
        return entry == null ? 0L : entry.amount();
    }

    /**
     * Whether the player has any banked balance at all, in any currency.
     * This is the wagering gate's question: banked emeralds block a diamond
     * wager just as much as a diamond one.
     */
    public synchronized boolean hasAnyBalance(UUID playerId) {
        Map<String, Entry> forPlayer = balances.get(playerId);
        return forPlayer != null && !forPlayer.isEmpty();
    }

    /** Total banked units across every currency, for summary messaging. */
    public synchronized long totalUnits(UUID playerId) {
        long total = 0L;
        for (Entry entry : entriesFor(playerId)) {
            long next = total + entry.amount();
            // Saturate rather than wrap: this figure is only ever displayed.
            total = next < total ? Long.MAX_VALUE : next;
        }
        return total;
    }
}
