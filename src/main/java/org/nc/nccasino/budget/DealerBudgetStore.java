package org.nc.nccasino.budget;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.payout.BankedCurrency;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Durable dealer balances and reservations, in {@code data/dealer-budgets.yml}.
 *
 * <h2>Stored schema (version 2)</h2>
 *
 * <pre>
 * version: 2
 * dealers:
 *   &lt;internal-name&gt;:
 *     live-balance: "1000.000000"   # exact decimal, plain string
 *     refill-boundary: 1735689600   # epoch seconds of the last applied ADD/RESET period
 *     baseline-initialized: 1735689600  # epoch seconds the one-time LIMITED
 *                                    # baseline seed was applied, or 0/absent
 *                                    # if never. Deliberately separate from
 *                                    # refill-boundary -- see ensureInitialFunding.
 *     reservations:                 # a LIST, not a map: ids contain '.' and
 *       - id: "dealer|uuid|spin-7"  # '|', which YAML paths would split
 *         player: "…-uuid-…"
 *         game: "Slots"
 *         amount: "250.000000"
 *         created: 1735689600
 *         currency-mode: STANDARD
 *         currency-material: EMERALD
 *         currency-name: "Casino Token"
 *     settled:                      # tombstones: ids that can never be recreated
 *       - id: "dealer|uuid|spin-6"
 *         settled-at: 1735689650
 *     shortfalls:                   # settlements that could not be fully backed
 *       - reservation-id: "dealer|uuid|spin-9"
 *         amount: "499.000000"      # exact unbacked remainder, player already paid
 *         recorded-at: 1735689700
 * </pre>
 *
 * <p>Version 1 (no {@code shortfalls} key) loads unchanged with an empty
 * shortfall list -- the field is purely additive.</p>
 *
 * <p>Amounts are stored as plain decimal strings rather than YAML numbers so a
 * value round-trips exactly: a YAML double would reintroduce the binary
 * floating point this system exists to avoid.
 *
 * <h2>Durability</h2>
 *
 * <p>Writes go to a sibling temp file and are then moved over the target, so a
 * crash mid-write leaves the previous complete file rather than a truncated
 * one. Every mutation persists before it is reported successful, and rolls the
 * in-memory state back if the write fails -- so a caller can never act on a
 * reservation that is not on disk.
 *
 * <h2>Recovery</h2>
 *
 * <p>On load, a dealer whose reservations exceed its balance (a hand-edited
 * file, or an older version) is reported and left <em>intact</em>: the
 * reservations are real promises to real players, so they are honored and the
 * balance is raised to cover them rather than reservations being deleted.
 * Losing a promise is worse than a dealer being temporarily over-funded, and
 * silently deleting economically meaningful state is forbidden outright.
 * Malformed individual entries are skipped with a warning rather than
 * discarding the whole file.
 */
public class DealerBudgetStore {

    /**
     * Version 2 adds the {@code shortfalls} list (see {@link
     * DealerBudgetState.Shortfall}), recording any settlement that could not
     * be fully backed without consuming another active reservation's
     * protected balance. Additive: a version-1 file (no {@code shortfalls}
     * key) loads with an empty shortfall list, since no such record could
     * exist before this fix.
     */
    public static final int SCHEMA_VERSION = 2;

    private final Nccasino plugin;
    private final File file;
    private final Map<String, DealerBudgetState> states = new LinkedHashMap<>();

    public DealerBudgetStore(Nccasino plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "dealer-budgets.yml");
        load();
    }

    /** Test seam: a store backed by an explicit file. */
    DealerBudgetStore(Nccasino plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    // ---- loading ------------------------------------------------------

    private synchronized void load() {
        states.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("dealers");
        if (root == null) {
            return;
        }

        for (String dealer : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(dealer);
            if (section == null) {
                continue;
            }
            BigDecimal balance = Money.parse(section.getString("live-balance"));
            if (balance == null) {
                warn("Dealer '" + dealer + "' has an unreadable live-balance; treating it as 0."
                    + " Its reservations are still honored.");
                balance = Money.ZERO;
            }
            DealerBudgetState state = new DealerBudgetState(
                dealer, balance, section.getLong("refill-boundary", 0L));
            // Absent in a file written before this marker existed -- defaults
            // to 0 ("never seeded"), which is always the safe migration
            // choice (see ensureInitialFunding's javadoc): the seed only ever
            // raises the balance to a floor, so applying it once more on an
            // already-healthy dealer changes nothing.
            state.restoreBaselineInitialized(section.getLong("baseline-initialized", 0L));

            for (Map<?, ?> raw : section.getMapList("reservations")) {
                Reservation reservation = readReservation(dealer, raw);
                if (reservation != null) {
                    state.putReservation(reservation);
                    BigDecimal creditedStake = Money.parse(string(raw, "credited-stake"));
                    if (creditedStake != null) {
                        state.restoreCreditedStake(reservation.id(), creditedStake);
                    }
                    BigDecimal settlementPayout = Money.parse(string(raw, "settlement-payout"));
                    if (settlementPayout != null) {
                        state.restoreSettlementIntent(reservation.id(), settlementPayout);
                    }
                    Object operationsRaw = raw.get("applied-operations");
                    if (operationsRaw instanceof Iterable<?> operations) {
                        for (Object operation : operations) {
                            if (operation != null && !String.valueOf(operation).isBlank()) {
                                state.restoreOperation(reservation.id(), String.valueOf(operation));
                            }
                        }
                    }
                }
            }

            for (Map<?, ?> raw : section.getMapList("settled")) {
                String id = string(raw, "id");
                Object settledAtRaw = raw.get("settled-at");
                if (id != null && !id.isBlank() && settledAtRaw instanceof Number number) {
                    state.restoreTombstone(id, number.longValue());
                }
            }

            for (Map<?, ?> raw : section.getMapList("shortfalls")) {
                String reservationId = string(raw, "reservation-id");
                BigDecimal amount = Money.parse(string(raw, "amount"));
                Object recordedAtRaw = raw.get("recorded-at");
                if (reservationId != null && !reservationId.isBlank() && amount != null
                    && recordedAtRaw instanceof Number number) {
                    state.restoreShortfall(reservationId, amount, number.longValue());
                }
            }

            reconcileOnLoad(state);
            states.put(dealer, state);
        }
    }

    private Reservation readReservation(String dealer, Map<?, ?> raw) {
        try {
            String id = string(raw, "id");
            String playerRaw = string(raw, "player");
            BigDecimal amount = Money.parse(string(raw, "amount"));
            if (id == null || id.isBlank() || playerRaw == null || amount == null) {
                warn("Skipping a malformed reservation on dealer '" + dealer + "': " + raw);
                return null;
            }
            UUID player = UUID.fromString(playerRaw);
            CurrencyMode mode = CurrencyMode.valueOf(
                string(raw, "currency-mode") == null ? "STANDARD" : string(raw, "currency-mode"));
            BankedCurrency currency = new BankedCurrency(
                mode, string(raw, "currency-material"), string(raw, "currency-name"));

            long created = 0L;
            Object createdRaw = raw.get("created");
            if (createdRaw instanceof Number number) {
                created = number.longValue();
            }
            return new Reservation(
                id, dealer, player, string(raw, "game"), currency, amount, created);
        } catch (IllegalArgumentException e) {
            warn("Skipping an unreadable reservation on dealer '" + dealer + "': " + raw + " (" + e + ")");
            return null;
        }
    }

    private static String string(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Repairs the one inconsistency that can survive a bad shutdown or a hand
     * edit: reservations totalling more than the stored balance. The
     * reservations win -- they are promises already made to players -- and the
     * balance is raised to cover them, loudly.
     */
    private void reconcileOnLoad(DealerBudgetState state) {
        BigDecimal reserved = state.reservedTotal();
        if (Money.atLeast(state.liveBalance(), reserved)) {
            return;
        }
        warn("Dealer '" + state.dealerInternalName() + "' loaded with "
            + Money.store(reserved) + " reserved but only " + Money.store(state.liveBalance())
            + " on hand. Honoring the reservations and raising the balance to match --"
            + " no promised payout is being cancelled. Please check this dealer's funding.");
        state.setLiveBalance(reserved);
    }

    private void warn(String message) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().warning("[NCCasino] dealer-budgets.yml: " + message);
        }
    }

    // ---- persistence --------------------------------------------------

    private synchronized boolean persist() {
        FileConfiguration config = new YamlConfiguration();
        config.set("version", SCHEMA_VERSION);
        for (DealerBudgetState state : states.values()) {
            String base = "dealers." + state.dealerInternalName();
            config.set(base + ".live-balance", Money.store(state.liveBalance()));
            config.set(base + ".refill-boundary", state.refillBoundaryEpochSeconds());
            config.set(base + ".baseline-initialized", state.baselineInitializedAtEpochSeconds());

            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Reservation reservation : state.reservations()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", reservation.id());
                entry.put("player", reservation.playerId() == null ? null : reservation.playerId().toString());
                entry.put("game", reservation.gameType());
                entry.put("amount", Money.store(reservation.amount()));
                entry.put("created", reservation.createdAtEpochSeconds());
                BankedCurrency currency = reservation.currency();
                entry.put("currency-mode", currency == null ? null : currency.mode().name());
                entry.put("currency-material", currency == null ? null : currency.material());
                entry.put("currency-name", currency == null ? null : currency.name());
                BigDecimal creditedStake = state.creditedStake(reservation.id());
                if (creditedStake != null) {
                    entry.put("credited-stake", Money.store(creditedStake));
                }
                BigDecimal settlementPayout = state.settlementIntent(reservation.id());
                if (settlementPayout != null) {
                    entry.put("settlement-payout", Money.store(settlementPayout));
                }
                if (!state.operations(reservation.id()).isEmpty()) {
                    entry.put("applied-operations", new ArrayList<>(state.operations(reservation.id())));
                }
                serialized.add(entry);
            }
            config.set(base + ".reservations", serialized);

            List<Map<String, Object>> settled = new ArrayList<>();
            for (Map.Entry<String, Long> tombstone : state.tombstoneEntries()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", tombstone.getKey());
                entry.put("settled-at", tombstone.getValue());
                settled.add(entry);
            }
            config.set(base + ".settled", settled);

            List<Map<String, Object>> shortfalls = new ArrayList<>();
            for (DealerBudgetState.Shortfall shortfall : state.shortfalls()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("reservation-id", shortfall.reservationId());
                entry.put("amount", Money.store(shortfall.amount()));
                entry.put("recorded-at", shortfall.recordedAtEpochSeconds());
                shortfalls.add(entry);
            }
            config.set(base + ".shortfalls", shortfalls);
        }
        return writeAtomically(config);
    }

    /**
     * Writes through a temp file and moves it into place, so an interrupted
     * write cannot leave a half-written economic record. Falls back to a
     * non-atomic replace only where the filesystem refuses an atomic move.
     */
    private boolean writeAtomically(FileConfiguration config) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().log(Level.SEVERE,
                    "[NCCasino] Failed to save dealer-budgets.yml; the change was rolled back.", e);
            }
            temp.delete();
            return false;
        }
    }

    /**
     * Runs {@code mutation} against the dealer's state, persists, and rolls
     * the in-memory state back if the write fails.
     *
     * <p>This is the single place the "never report success off a failed
     * write" rule is enforced. A mutation that returns {@code null} is
     * abandoned without touching the disk.
     */
    private synchronized <T> T mutate(String dealer, java.util.function.Function<DealerBudgetState, T> mutation) {
        DealerBudgetState state = states.computeIfAbsent(dealer, DealerBudgetState::new);
        DealerBudgetState snapshot = state.copy();
        T result = mutation.apply(state);
        if (result == null) {
            state.restoreFrom(snapshot);
            return null;
        }
        if (persist()) {
            return result;
        }
        state.restoreFrom(snapshot);
        return null;
    }

    // ---- queries ------------------------------------------------------

    public synchronized DealerBudgetState state(String dealer) {
        return states.computeIfAbsent(dealer, DealerBudgetState::new);
    }

    /** A read-only snapshot for diagnostics; never used to make decisions. */
    public synchronized List<String> dealers() {
        return List.copyOf(states.keySet());
    }

    public synchronized BigDecimal liveBalance(String dealer) {
        return state(dealer).liveBalance();
    }

    public synchronized BigDecimal available(String dealer) {
        return state(dealer).available();
    }

    public synchronized BigDecimal reservedTotal(String dealer) {
        return state(dealer).reservedTotal();
    }

    // ---- administrative funding ---------------------------------------

    /**
     * Sets a dealer's balance outright. Administrative only -- never called
     * from a game path.
     *
     * @return false if the write failed, in which case nothing changed
     */
    public synchronized boolean setBalance(String dealer, BigDecimal amount) {
        if (!Money.isSafe(amount)) {
            return false;
        }
        return mutate(dealer, state -> {
            // Refuse to drop below what is already promised; a promise
            // outranks an administrative adjustment.
            state.setLiveBalance(Money.max(amount, state.reservedTotal()));
            return Boolean.TRUE;
        }) != null;
    }

    /** Adds to a dealer's balance. Administrative or refill only. */
    public synchronized boolean deposit(String dealer, BigDecimal amount) {
        if (!Money.isSafe(amount) || !Money.isPositive(amount)) {
            return false;
        }
        return mutate(dealer, state -> {
            state.setLiveBalance(Money.min(Money.add(state.liveBalance(), amount), Money.MAX));
            return Boolean.TRUE;
        }) != null;
    }

    /**
     * Withdraws unreserved funds. Reserved money is never withdrawable -- that
     * is the rule that stops a dealer owing more than it holds.
     *
     * @return false if the write failed or the funds are not free
     */
    public synchronized boolean withdrawUnreserved(String dealer, BigDecimal amount) {
        if (!Money.isSafe(amount) || !Money.isPositive(amount)) {
            return false;
        }
        return mutate(dealer, state -> {
            if (!Money.atLeast(state.available(), amount)) {
                return null;
            }
            state.setLiveBalance(Money.subtract(state.liveBalance(), amount));
            return Boolean.TRUE;
        }) != null;
    }

    /**
     * One-time funding bootstrap for a freshly created LIMITED dealer: seeds
     * {@code live-balance} to the underwriting baseline exactly once, marked
     * by a dedicated, explicit {@code baseline-initialized} marker persisted
     * in the same step so a reload or restart can never repeat it.
     *
     * <p>Deliberately <strong>not</strong> gated on {@code refillBoundaryEpochSeconds}:
     * that field can become positive for reasons unrelated to this seed ever
     * having run (an ADD/RESET refill applying against a dealer that
     * predates this seed feature, or a dealer that was manually funded and
     * later refilled) -- treating it as proof of seeding is not
     * migration-safe, since a file written before this method existed can
     * have a positive refill boundary while never having received the
     * baseline floor. This method uses its own marker instead, which starts
     * at {@code 0} ("never seeded") for every file written before it
     * existed, regardless of the refill boundary's value. Because the
     * balance is only ever raised with {@code max(liveBalance, baseline)},
     * running this once during migration on an already-healthy, long-lived
     * dealer is a safe no-op, never a double-mint.
     *
     * <p>The refill boundary itself is still started here (unchanged
     * behavior) -- but only if it has genuinely never been touched
     * ({@code <= 0}); an already-running ADD/RESET clock is never reset back
     * to "now" merely because this seed is (re)considered during migration,
     * which would otherwise discard legitimately accrued refill periods.
     *
     * <p>A later change to the configured baseline never moves already-seeded
     * money -- this method only ever applies its floor once per dealer,
     * gated by the baseline-initialized marker, regardless of how many times
     * the baseline is edited afterward.
     *
     * @return whether the seed was actually applied. {@code false} for a
     *     dealer that has ever been seeded before, one with nothing to seed
     *     ({@code baseline <= 0}), or a write failure.
     */
    public synchronized boolean ensureInitialFunding(String dealer, BigDecimal baseline, long nowEpochSeconds) {
        if (!Money.isSafe(baseline) || !Money.isPositive(baseline)) {
            return false;
        }
        DealerBudgetState current = state(dealer);
        if (current.isBaselineInitialized()) {
            return false;
        }
        Boolean applied = mutate(dealer, state -> {
            if (state.isBaselineInitialized()) {
                // Lost a race with another caller between the check above
                // and this mutation; the other caller already seeded it.
                return null;
            }
            state.setLiveBalance(Money.max(state.liveBalance(), baseline));
            state.markBaselineInitialized(nowEpochSeconds);
            if (state.refillBoundaryEpochSeconds() <= 0L) {
                state.setRefillBoundary(nowEpochSeconds);
            }
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(applied);
    }

    // ---- refills ------------------------------------------------------

    /**
     * Applies any elapsed refill periods for this dealer.
     *
     * @return the number of periods applied, or 0 if nothing changed
     */
    public synchronized long applyRefill(
        String dealer, DealerBudgetSettings settings, long nowEpochSeconds) {

        if (settings == null || !settings.hasRefill()) {
            return 0L;
        }
        DealerBudgetState current = state(dealer);
        RefillPolicy.Result result = RefillPolicy.apply(
            settings,
            current.liveBalance(),
            current.reservedTotal(),
            current.refillBoundaryEpochSeconds(),
            nowEpochSeconds);

        if (!result.applied()) {
            return 0L;
        }
        Long applied = mutate(dealer, state -> {
            state.setLiveBalance(result.liveBalance());
            state.setRefillBoundary(result.boundaryEpochSeconds());
            return result.periodsElapsed();
        });
        return applied == null ? 0L : applied;
    }

    // ---- reservations -------------------------------------------------

    /**
     * Credits a stake and reserves the commitment's worst-case payout in one
     * persisted step.
     *
     * <p>Both halves land together or neither does: a crash between them would
     * otherwise leave a dealer holding a stake it never promised to cover, or
     * promising money it never received. Idempotent on
     * {@link Reservation#id()} -- a repeated call for the same commitment
     * returns the existing reservation and credits nothing a second time,
     * <em>provided</em> the replayed payload actually matches: a reused id
     * carrying a different player, game, currency or exposure is refused
     * rather than silently treated as the original commitment, since that
     * would mix two unrelated commitments under one identity. A commitment id
     * that was already settled (see {@link DealerBudgetState#tombstone}) is
     * refused rather than recreated as a brand-new reservation, which would
     * otherwise double-pay/double-reserve it.
     *
     * @return the reservation, or {@code null} if the funds are unavailable,
     *     the payload does not match a live commitment under the same id, the
     *     id was already settled, or the write failed. A {@code null} return
     *     means nothing changed and the caller must not take the wager.
     */
    public synchronized Reservation creditAndReserve(Reservation reservation, BigDecimal stake) {
        if (reservation == null || !Money.isSafe(reservation.amount()) || !Money.isSafe(stake)) {
            return null;
        }
        String dealer = reservation.dealerInternalName();
        DealerBudgetState existingState = state(dealer);
        Reservation existing = existingState.reservation(reservation.id());
        if (existing != null) {
            if (!samePayload(existing, reservation)) {
                warn("Commitment '" + reservation.id() + "' on dealer '" + dealer
                    + "' was reused with different player/game/currency/exposure data;"
                    + " refusing rather than mixing two commitments under one id.");
                return null;
            }
            BigDecimal creditedStake = existingState.creditedStake(reservation.id());
            if (creditedStake != null && creditedStake.compareTo(Money.of(stake)) != 0) {
                warn("Commitment '" + reservation.id() + "' on dealer '" + dealer
                    + "' was replayed with a different stake (" + Money.store(stake)
                    + " vs. the originally credited " + Money.store(creditedStake)
                    + "); refusing rather than silently treating a mismatched replay as the"
                    + " original request.");
                return null;
            }
            // Same commitment, replayed with the same stake. The stake is
            // already credited and the payout already promised; doing either
            // again would be the duplicate-payment bug this id exists to
            // prevent.
            return existing;
        }
        if (existingState.isTombstoned(reservation.id())) {
            warn("Commitment '" + reservation.id() + "' on dealer '" + dealer
                + "' was already settled and cannot be recreated; refusing.");
            return null;
        }

        return mutate(dealer, state -> {
            BigDecimal balanceAfterStake = Money.min(Money.add(state.liveBalance(), stake), Money.MAX);
            BigDecimal reservedAfter = Money.add(state.reservedTotal(), reservation.amount());
            if (!Money.atLeast(balanceAfterStake, reservedAfter)) {
                // Would promise more than the dealer holds.
                return null;
            }
            state.setLiveBalance(balanceAfterStake);
            state.putReservation(reservation);
            state.recordCreditedStake(reservation.id(), stake);
            return reservation;
        });
    }

    /** Whether an existing reservation and a fresh replayed payload describe the same commitment. */
    private static boolean samePayload(Reservation existing, Reservation incoming) {
        return java.util.Objects.equals(existing.playerId(), incoming.playerId())
            && java.util.Objects.equals(existing.gameType(), incoming.gameType())
            && java.util.Objects.equals(existing.currency(), incoming.currency())
            && existing.amount().compareTo(incoming.amount()) == 0;
    }

    /**
     * Grows or shrinks an existing reservation in place -- a Roulette
     * portfolio taking another bet, a Blackjack hand doubling.
     *
     * <p><strong>Legacy, exposure-only idempotency.</strong> Prefer {@link
     * #adjustReservation(String, String, String, BigDecimal, BigDecimal)},
     * which distinguishes a true replay from a different, legitimate
     * operation that happens to land on the same resulting exposure. This
     * overload cannot make that distinction: it treats the reservation
     * already being at {@code newAmount} as proof of a replay, which is
     * wrong whenever a portfolio's <em>worst-case</em> payout does not
     * change even though a real, distinct additional stake was just placed
     * (e.g. a new Roulette bet whose own worst case does not exceed the
     * portfolio's existing maximum) -- that stake would be silently never
     * credited. Kept only for callers not yet migrated to an explicit
     * operation id.
     *
     * @param additionalStake stake posted alongside the increase, credited in
     *     the same persisted step
     * @return the updated reservation, or {@code null} if it cannot be covered
     *     or the write failed, in which case nothing changed
     */
    public synchronized Reservation adjustReservation(
        String dealer, String reservationId, BigDecimal newAmount, BigDecimal additionalStake) {

        if (!Money.isSafe(newAmount) || !Money.isSafe(additionalStake)) {
            return null;
        }
        DealerBudgetState currentState = state(dealer);
        Reservation current = currentState.reservation(reservationId);
        if (current != null && current.amount().compareTo(Money.of(newAmount)) == 0) {
            // Already at the requested exposure: either nothing to do, or a
            // replay of an increase that already applied. Crediting
            // additionalStake again would double-count a real deposit.
            return current;
        }
        return mutate(dealer, state -> {
            Reservation existing = state.reservation(reservationId);
            if (existing == null) {
                return null;
            }
            BigDecimal balanceAfter = Money.min(Money.add(state.liveBalance(), additionalStake), Money.MAX);
            BigDecimal reservedAfter = Money.add(
                Money.subtract(state.reservedTotal(), existing.amount()), newAmount);
            if (!Money.atLeast(balanceAfter, reservedAfter)) {
                return null;
            }
            Reservation updated = existing.withAmount(newAmount);
            state.setLiveBalance(balanceAfter);
            state.putReservation(updated);
            BigDecimal credited = state.creditedStake(reservationId);
            if (credited != null) {
                state.setCreditedStake(reservationId, Money.add(credited, additionalStake));
            }
            return updated;
        });
    }

    /**
     * Grows or shrinks an existing reservation in place, guarded by an
     * explicit, caller-supplied {@code operationId} identifying this
     * specific economic action (e.g. one Roulette bet-placement click, one
     * Blackjack split) -- distinct from {@code reservationId}, which
     * identifies the whole commitment being adjusted.
     *
     * <p>Idempotent on {@code operationId}, not on {@code newAmount}: a
     * replay of the exact same operation (the same duplicated Bukkit event
     * firing twice) is a no-op, but a <em>different</em> legitimate
     * operation that happens to leave the reservation's worst-case payout
     * unchanged still credits its {@code additionalStake} exactly once --
     * which {@link #adjustReservation(String, String, BigDecimal, BigDecimal)}
     * cannot do, since it can only see the resulting amount, not the
     * operation that produced it.
     *
     * <p>The operation-identity guard is persisted with the active
     * reservation, so an older operation replayed after a newer one, or after
     * a restart, remains a no-op.
     *
     * @param operationId a stable identity for this specific attempt -- the
     *     same value on every retry of the *same* real action, and a
     *     different value for every genuinely distinct action, even if two
     *     distinct actions happen to produce the same {@code newAmount}
     * @param additionalStake stake posted alongside the increase, credited in
     *     the same persisted step
     * @return the updated reservation, or {@code null} if it cannot be
     *     covered, {@code operationId} is missing, or the write failed, in
     *     which case nothing changed
     */
    public synchronized Reservation adjustReservation(
        String dealer, String reservationId, String operationId, BigDecimal newAmount, BigDecimal additionalStake) {

        if (operationId == null || operationId.isBlank()
            || !Money.isSafe(newAmount) || !Money.isSafe(additionalStake)) {
            return null;
        }
        DealerBudgetState currentState = state(dealer);
        Reservation current = currentState.reservation(reservationId);
        if (current == null) {
            return null;
        }
        if (currentState.isSameOperation(reservationId, operationId)) {
            // A true replay of this exact action: the stake was already
            // credited and the exposure already updated. Applying either
            // again would double-count a real deposit.
            return current;
        }
        return mutate(dealer, state -> {
            Reservation existing = state.reservation(reservationId);
            if (existing == null) {
                return null;
            }
            BigDecimal balanceAfter = Money.min(Money.add(state.liveBalance(), additionalStake), Money.MAX);
            BigDecimal reservedAfter = Money.add(
                Money.subtract(state.reservedTotal(), existing.amount()), newAmount);
            if (!Money.atLeast(balanceAfter, reservedAfter)) {
                return null;
            }
            Reservation updated = existing.withAmount(newAmount);
            state.setLiveBalance(balanceAfter);
            state.putReservation(updated);
            BigDecimal credited = state.creditedStake(reservationId);
            if (credited != null) {
                state.setCreditedStake(reservationId, Money.add(credited, additionalStake));
            }
            state.recordOperation(reservationId, operationId);
            return updated;
        });
    }

    /**
     * Atomically removes a returned/failed stake from an open commitment and
     * replaces its exposure with the amount still legitimately wagered.
     */
    public synchronized ReservationAdjustment reduceReservation(
        String dealer,
        String reservationId,
        String operationId,
        BigDecimal newAmount,
        BigDecimal stakeToRemove
    ) {
        if (reservationId == null || operationId == null || operationId.isBlank()
            || !Money.isSafe(newAmount) || !Money.isSafe(stakeToRemove)) {
            return ReservationAdjustment.failed();
        }
        DealerBudgetState current = state(dealer);
        Reservation existing = current.reservation(reservationId);
        if (existing == null) {
            return current.isTombstoned(reservationId)
                ? ReservationAdjustment.closed()
                : ReservationAdjustment.failed();
        }
        if (current.isSameOperation(reservationId, operationId)) {
            return ReservationAdjustment.updated(existing);
        }

        ReservationAdjustment result = mutate(dealer, state -> {
            Reservation active = state.reservation(reservationId);
            if (active == null) {
                return null;
            }
            BigDecimal normalizedRemoval = Money.of(stakeToRemove);
            BigDecimal balanceAfter = Money.subtract(state.liveBalance(), normalizedRemoval);
            if (Money.isNegative(balanceAfter)) {
                return null;
            }
            BigDecimal otherReserved = Money.subtract(state.reservedTotal(), active.amount());
            BigDecimal reservedAfter = Money.add(otherReserved, newAmount);
            if (!Money.atLeast(balanceAfter, reservedAfter)) {
                return null;
            }

            BigDecimal credited = state.creditedStake(reservationId);
            if (credited != null && credited.compareTo(normalizedRemoval) < 0) {
                return null;
            }
            state.setLiveBalance(balanceAfter);
            if (!Money.isPositive(newAmount)) {
                state.removeReservation(reservationId);
                state.tombstone(reservationId);
                return ReservationAdjustment.closed();
            }

            Reservation updated = active.withAmount(newAmount);
            state.putReservation(updated);
            if (credited != null) {
                state.setCreditedStake(reservationId, Money.subtract(credited, normalizedRemoval));
            }
            state.recordOperation(reservationId, operationId);
            return ReservationAdjustment.updated(updated);
        });
        return result == null ? ReservationAdjustment.failed() : result;
    }

    /**
     * Settles a commitment: pays {@code payout} out of the dealer and releases
     * the reservation, in one persisted step.
     *
     * <p>Idempotent by construction. The reservation is removed as part of
     * settling, so a replayed settlement finds nothing and reports
     * {@link Settlement.Status#ALREADY_SETTLED} without moving money. That is
     * what makes a duplicated event, a reconnect, or a retried payout delivery
     * safe. The commitment id is also retained afterward (see
     * {@link DealerBudgetState#tombstone}) so a later call cannot recreate it
     * as a brand-new reservation.
     *
     * <p>A player's awarded result is never reduced here: {@link
     * Settlement#paid()} is always the full requested {@code payout} on a
     * {@link Settlement.Status#SETTLED} result. A payout larger than the
     * reservation set aside for it ({@link Settlement#exposureViolation()})
     * always means the pre-commitment exposure calculation had a bug, and is
     * logged loudly by {@link DealerBudgetService} -- but that is a signal to
     * fix, not permission to underpay the player. The dealer's stored {@code
     * live-balance} is debited by the full payout, floored at zero rather
     * than driven negative ({@link Settlement#insolvent()} reports when that
     * floor was actually hit) -- an untracked negative balance is exactly the
     * corrupt state this floor exists to prevent, and the loud logging is
     * what keeps the shortfall from being silently lost.
     */
    public synchronized Settlement settle(String dealer, String reservationId, BigDecimal payout) {
        if (!Money.isSafe(payout)) {
            return Settlement.numericLimit();
        }
        DealerBudgetState current = state(dealer);
        if (!current.hasReservation(reservationId)) {
            return Settlement.alreadySettled();
        }

        Reservation reservation = current.reservation(reservationId);
        BigDecimal normalizedPayout = Money.of(payout);
        BigDecimal existingIntent = current.settlementIntent(reservationId);
        if (existingIntent != null && existingIntent.compareTo(normalizedPayout) != 0) {
            warn("Commitment '" + reservationId + "' on dealer '" + dealer
                + "' was retried with payout " + Money.store(normalizedPayout)
                + " but its durable settlement intent is " + Money.store(existingIntent)
                + "; refusing the mismatched retry.");
            return Settlement.failed();
        }
        if (existingIntent == null) {
            Boolean recorded = mutate(dealer, state -> {
                if (!state.hasReservation(reservationId)) {
                    return null;
                }
                state.recordSettlementIntent(reservationId, normalizedPayout);
                return Boolean.TRUE;
            });
            if (!Boolean.TRUE.equals(recorded)) {
                return Settlement.failed();
            }
            current = state(dealer);
            reservation = current.reservation(reservationId);
        }
        boolean exposureViolation = normalizedPayout.compareTo(reservation.amount()) > 0;

        Settlement result = mutate(dealer, state -> {
            Reservation removed = state.removeReservation(reservationId);
            if (removed == null) {
                return null;
            }
            state.tombstone(reservationId);
            // Other active reservations' backing must never be consumed to
            // cover this settlement: bound the debit to what remains of the
            // live balance once every *other* still-active reservation is
            // left fully covered. reservedTotal() here already excludes the
            // reservation just removed above, so it is exactly "everyone
            // else's" promise.
            BigDecimal otherReserved = state.reservedTotal();
            BigDecimal availableForThis = Money.clampNonNegative(
                Money.subtract(state.liveBalance(), otherReserved));
            BigDecimal debit = Money.min(normalizedPayout, availableForThis);
            boolean insolvent = debit.compareTo(normalizedPayout) < 0;
            state.setLiveBalance(Money.subtract(state.liveBalance(), debit));
            if (insolvent) {
                // The player is still paid the full payout by the game/
                // delivery layer; this is the durable record of the part
                // that left the dealer's economy with no backing, so an
                // administrator can reconcile it exactly instead of the
                // dealer's books silently claiming to be healthy.
                state.recordShortfall(reservationId, Money.subtract(normalizedPayout, debit));
            }
            return Settlement.settled(normalizedPayout, exposureViolation, insolvent);
        });
        return result == null ? Settlement.failed() : result;
    }

    /** Durable awarded results still waiting for their ledger debit. */
    public synchronized Map<String, BigDecimal> settlementIntents(String dealer) {
        Map<String, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : state(dealer).settlementIntentEntries()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    /**
     * Releases a reservation without paying it -- a player loss, or a
     * cancelled commitment. Idempotent for the same reason {@link #settle} is.
     */
    public synchronized Settlement release(String dealer, String reservationId) {
        return settle(dealer, reservationId, Money.ZERO);
    }

    /**
     * Returns a stake to a player for a push or cancellation, and releases the
     * reservation, in one persisted step.
     */
    public synchronized Settlement refund(String dealer, String reservationId, BigDecimal stake) {
        return settle(dealer, reservationId, stake);
    }

    /**
     * Outstanding shortfall records for a dealer, for administrative
     * reconciliation. Never auto-cleared -- an operator resolves them
     * deliberately (e.g. by depositing the missing amount), the same way
     * {@link #staleReservations} are reported, never auto-released.
     */
    public synchronized List<DealerBudgetState.Shortfall> shortfalls(String dealer) {
        return state(dealer).shortfalls();
    }

    /**
     * Marks a shortfall resolved once an administrator has restored the
     * missing backing (e.g. via {@link #deposit}) -- a deliberate, explicit
     * operator action, mirroring the design's rule that a stale reservation
     * is reported, never auto-released. Persisted atomically: if the write
     * fails, the shortfall record is restored exactly as it was, so a caller
     * can never believe a shortfall is resolved when it is not durably so.
     *
     * <p>Idempotent: resolving an id with no outstanding shortfall (already
     * resolved, or never recorded) returns {@code false} and changes nothing
     * -- it is a harmless no-op, not an error.
     *
     * @return whether a shortfall was actually present and durably cleared
     */
    public synchronized boolean resolveShortfall(String dealer, String reservationId) {
        if (reservationId == null || !state(dealer).hasShortfall(reservationId)) {
            return false;
        }
        Boolean resolved = mutate(dealer, state -> state.resolveShortfall(reservationId) ? Boolean.TRUE : null);
        return Boolean.TRUE.equals(resolved);
    }

    /** Reservations older than {@code olderThanSeconds}, for administrative review. */
    public synchronized List<Reservation> staleReservations(long nowEpochSeconds, long olderThanSeconds) {
        List<Reservation> stale = new ArrayList<>();
        for (DealerBudgetState state : states.values()) {
            for (Reservation reservation : state.reservations()) {
                long created = reservation.createdAtEpochSeconds();
                if (created > 0 && nowEpochSeconds - created > olderThanSeconds) {
                    stale.add(reservation);
                }
            }
        }
        return stale;
    }

    /** Forces a reload from disk. Test and administrative use. */
    public synchronized void reload() {
        load();
    }
}
