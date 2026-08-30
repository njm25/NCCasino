package org.nc.nccasino.payout;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.MoneyHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Durable, UUID-keyed store for {@link PendingPayout} records, backed by a
 * dedicated {@code data/pending-payouts.yml} file (deliberately separate
 * from {@code preferences.yml} — this is money-bearing state, not a sound/
 * message preference). Survives server restarts between when a
 * disconnected player's outcome is calculated and when they reconnect.
 *
 * <p>Write ordering is deliberate throughout:
 * <ul>
 *   <li>{@link #addPendingPayout} only reports success once the record is
 *       actually written to disk — callers must not clear their in-memory
 *       game state unless this returns {@code true}, so a failed write
 *       never silently drops a payout.</li>
 *   <li>{@link #attemptDeliver} only removes a record (and persists that
 *       removal) after the actual currency deposit succeeds — a failed
 *       deposit leaves the record pending for a later retry rather than
 *       losing it.</li>
 * </ul>
 */
public class PendingPayoutStore {

    private final Nccasino plugin;
    private final File file;
    private final Map<UUID, PendingPayout> byId = new LinkedHashMap<>();
    private final Map<UUID, List<PendingPayout>> byPlayer = new HashMap<>();

    public PendingPayoutStore(Nccasino plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "pending-payouts.yml");

        load();
    }

    private synchronized void load() {
        byId.clear();
        byPlayer.clear();

        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("payouts");
        if (section == null) {
            return;
        }

        for (String idKey : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idKey);
                UUID playerId = UUID.fromString(section.getString(idKey + ".player"));
                String gameType = section.getString(idKey + ".game-type", "");
                String dealer = section.getString(idKey + ".dealer", null);
                CurrencyMode mode = CurrencyMode.valueOf(section.getString(idKey + ".currency-mode", "STANDARD"));
                String material = section.getString(idKey + ".currency-material", null);
                String currencyName = section.getString(idKey + ".currency-name", "");
                double amount = section.getDouble(idKey + ".amount", 0);
                long createdAt = section.getLong(idKey + ".created-at", System.currentTimeMillis());
                String context = section.getString(idKey + ".context", "");

                PendingPayout payout = new PendingPayout(
                    id, playerId, gameType, dealer, mode, material, currencyName, amount, createdAt, context);
                indexAdd(payout);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().log(Level.WARNING,
                    "[NCCasino] Skipping malformed pending payout record '" + idKey + "' in pending-payouts.yml", e);
            }
        }
    }

    private synchronized boolean persist() {
        FileConfiguration config = new YamlConfiguration();
        for (PendingPayout payout : byId.values()) {
            String base = "payouts." + payout.id();
            config.set(base + ".player", payout.playerId().toString());
            config.set(base + ".game-type", payout.gameType());
            config.set(base + ".dealer", payout.dealerInternalName());
            config.set(base + ".currency-mode", payout.currencyMode().name());
            config.set(base + ".currency-material", payout.currencyMaterial());
            config.set(base + ".currency-name", payout.currencyName());
            config.set(base + ".amount", payout.amount());
            config.set(base + ".created-at", payout.createdAtEpochMillis());
            config.set(base + ".context", payout.context());
        }

        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NCCasino] Failed to save pending-payouts.yml", e);
            return false;
        }
    }

    private void indexAdd(PendingPayout payout) {
        byId.put(payout.id(), payout);
        byPlayer.computeIfAbsent(payout.playerId(), k -> new ArrayList<>()).add(payout);
    }

    private void indexRemove(PendingPayout payout) {
        byId.remove(payout.id());
        List<PendingPayout> list = byPlayer.get(payout.playerId());
        if (list != null) {
            list.remove(payout);
            if (list.isEmpty()) {
                byPlayer.remove(payout.playerId());
            }
        }
    }

    /**
     * Durably persists a new pending payout. Only returns {@code true} if
     * the write to disk succeeded; on failure the in-memory addition is
     * rolled back so the store's state matches what is actually on disk.
     */
    public synchronized boolean addPendingPayout(PendingPayout payout) {
        PendingPayout existing = byId.get(payout.id());
        if (existing != null) {
            if (!existing.equals(payout)) {
                plugin.getLogger().severe("[NCCasino] Refusing pending payout ID collision for "
                    + payout.id() + ".");
                return false;
            }
            // Retrying the exact same durable operation is a no-op. Without
            // this guard, byPlayer could contain the record twice and pay it
            // twice even though byId only contained one copy.
            return true;
        }

        indexAdd(payout);

        boolean ok = persist();
        if (!ok) {
            indexRemove(payout);
        }
        return ok;
    }

    public synchronized List<PendingPayout> getPending(UUID playerId) {
        return List.copyOf(byPlayer.getOrDefault(playerId, List.of()));
    }

    public synchronized boolean hasPending(UUID playerId) {
        List<PendingPayout> list = byPlayer.get(playerId);
        return list != null && !list.isEmpty();
    }

    /**
     * Removes a record whose payout has ALREADY been delivered (the
     * currency has already moved) and persists the removal. Idempotent: if
     * the record is already gone, this is a harmless no-op.
     *
     * <p>Unlike a normal removal, this deliberately never rolls back the
     * in-memory removal on a persist failure. By the time this is called
     * the player has already been paid — re-adding the record so it looks
     * pending again would make the next {@link #attemptDeliver} call pay
     * them a second time, which is strictly worse than the alternative: a
     * stale, already-delivered record surviving on disk that needs manual
     * cleanup (logged loudly below) but can never cause a duplicate
     * payment from this running instance.
     */
    private synchronized void markDelivered(UUID payoutId) {
        PendingPayout removed = byId.get(payoutId);
        if (removed == null) {
            return;
        }

        indexRemove(removed);

        if (!persist()) {
            plugin.getLogger().log(Level.SEVERE, "[NCCasino] Delivered pending payout " + payoutId
                + " to " + removed.playerId() + " but failed to persist its removal from disk. "
                + "The record may still be present in pending-payouts.yml and should be removed "
                + "manually to avoid a duplicate payout on a future server restart.");
        }
    }

    /**
     * Attempts to deliver every pending payout currently on record for
     * {@code player}. A record with {@code amount() <= 0} needs no
     * currency movement and is always delivered immediately (there is
     * nothing that can fail). A record with a positive amount is only
     * removed from the store if the actual deposit succeeds; on failure it
     * is left pending for a later retry and logged. A deposit that
     * succeeds is always reported as delivered, even if persisting its
     * removal from disk fails afterward — see {@link #markDelivered}.
     */
    public synchronized DeliveryResult attemptDeliver(Player player) {
        List<PendingPayout> pending = getPending(player.getUniqueId());
        List<PendingPayout> delivered = new ArrayList<>();
        List<PendingPayout> stillPending = new ArrayList<>();

        for (PendingPayout payout : pending) {
            boolean depositOk = payout.amount() <= 0 || depositCurrency(player, payout);
            if (!depositOk) {
                stillPending.add(payout);
                plugin.getLogger().warning("[NCCasino] Could not deliver pending payout " + payout.id()
                    + " (" + payout.amount() + " " + payout.currencyMode() + ") to " + player.getUniqueId()
                    + "; left pending for retry.");
                continue;
            }

            markDelivered(payout.id());
            delivered.add(payout);
        }

        return new DeliveryResult(delivered, stillPending);
    }

    private boolean depositCurrency(Player player, PendingPayout payout) {
        try {
            if (payout.currencyMode() == CurrencyMode.VAULT) {
                return depositVault(player, payout);
            }
            return depositItems(player, payout);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                "[NCCasino] Exception delivering pending payout " + payout.id(), e);
            return false;
        }
    }

    private boolean depositVault(Player player, PendingPayout payout) {
        if (plugin.getVaultHook() == null || !plugin.getVaultHook().isEconomyAvailable()) {
            return false;
        }

        Economy economy = plugin.getVaultHook().getEconomy();
        double amount = MoneyHelper.toVaultDouble(MoneyHelper.bd(payout.amount()));
        if (amount <= 0) {
            return true;
        }

        EconomyResponse resp = economy.depositPlayer(player, amount);
        return resp != null && resp.transactionSuccess();
    }

    /**
     * Hands a pending item payout over through {@link OverflowBankService},
     * so the part that does not fit is banked rather than scattered on the
     * ground. This is the hand-off point between the two systems: the record
     * stops being an unresolved outcome and whatever could not physically
     * fit becomes a bank balance the player already owns.
     *
     * <p>Returns {@code false} unless every unit reached the player, the
     * ground, or durable bank storage -- a failed bank write leaves the
     * record pending for a later retry instead of reporting a delivery that
     * did not fully happen.
     */
    private boolean depositItems(Player player, PendingPayout payout) {
        Material material = payout.currencyMaterial() != null
            ? Material.matchMaterial(payout.currencyMaterial())
            : null;
        if (material == null) {
            return false;
        }

        int wholeAmount = MoneyHelper.probabilisticItemAmount(
            payout.amount(),
            ThreadLocalRandom.current().nextDouble()
        );
        if (wholeAmount <= 0) {
            return true;
        }

        OverflowBankService bank = plugin.getOverflowBankService();
        if (bank == null) {
            // No safe destination for a remainder yet; leave the record
            // pending rather than risk dropping winnings on the floor.
            return false;
        }

        ItemDeliveryOutcome outcome = bank.deliver(
            player,
            new BankedCurrency(payout.currencyMode(), material.name(), payout.currencyName()),
            wholeAmount);
        return outcome.settled();
    }
}
