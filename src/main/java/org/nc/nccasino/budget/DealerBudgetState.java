package org.nc.nccasino.budget;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One limited dealer's live economic state: what it holds, what it has already
 * promised, and when its refill clock last ticked.
 *
 * <p>Mutable and deliberately not thread-safe on its own -- every mutation
 * goes through {@link DealerBudgetStore}, which holds the lock and owns the
 * persist-or-roll-back decision. Keeping the locking in one place is what lets
 * a failed disk write leave memory and disk agreeing.
 *
 * <p>The invariant this type exists to hold is single:
 *
 * <blockquote>{@code reservedTotal() <= liveBalance()}, and both are
 * non-negative.</blockquote>
 *
 * <p>Everything else -- no double payment, no promised money spent twice, no
 * negative dealer -- follows from maintaining it on every operation.
 */
public final class DealerBudgetState {

    /**
     * How many settled commitment ids one dealer retains as tombstones, so a
     * settled commitment cannot be recreated. Bounded so a very long-lived
     * dealer's memory/file does not grow without limit; oldest-settled ids
     * are evicted first once the cap is reached. Recreating an id that has
     * aged out of this bound is the one residual gap this leaves -- accepted
     * because retaining every settlement ever made is not proportionate, and
     * 5,000 outstanding ids is already far more than any real game keeps a
     * commitment key "hot" for.
     */
    static final int MAX_TOMBSTONES = 5_000;

    private final String dealerInternalName;
    private BigDecimal liveBalance;
    private long refillBoundaryEpochSeconds;
    /**
     * Epoch seconds this dealer's one-time LIMITED underwriting-baseline seed
     * was applied, or {@code 0} if it never has been. Deliberately separate
     * from {@link #refillBoundaryEpochSeconds}: the refill boundary can
     * become positive for reasons that have nothing to do with the baseline
     * seed ever having run (an ADD/RESET refill applying against a dealer
     * that predates the seed feature, or that was manually funded and then
     * refilled) -- treating {@code refillBoundaryEpochSeconds > 0} as proof
     * of seeding is not migration-safe, since it can falsely mark an
     * unseeded dealer as already-seeded and permanently deny it the intended
     * one-time floor. A file written before this field existed loads it as
     * {@code 0} (never seeded), which is always the safe migration default:
     * {@link DealerBudgetStore#ensureInitialFunding} only ever raises the
     * balance with {@code max(liveBalance, baseline)}, so re-running it once
     * during migration on an already-healthy dealer is a no-op, never a
     * double-mint.
     */
    private long baselineInitializedAtEpochSeconds;
    /** Reservation id -> reservation. Insertion-ordered so the file is stable. */
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    /**
     * Reservation id -> the stake that was credited when it was first
     * created via {@link DealerBudgetStore#creditAndReserve}. The stake
     * itself is never re-credited on a replay (the id already exists, so
     * {@code creditAndReserve} returns early), but this lets a replay whose
     * payload carries a genuinely different stake be <em>rejected</em>
     * rather than silently accepted under the assumption it must be the
     * same original request -- a reused id with a different stake is a bug
     * signal worth refusing loudly, the same way a reused id with a
     * different player/game/currency/exposure already is. Persisted
     * alongside {@link #reservations} so this detection survives a restart.
     */
    private final Map<String, BigDecimal> creditedStakes = new LinkedHashMap<>();
    /**
     * Settled reservation ids -> when they were settled (epoch seconds), so a
     * settled commitment cannot be recreated via {@link
     * DealerBudgetStore#creditAndReserve}. Insertion-ordered so the oldest
     * entry is always first, which is what {@link #tombstone} evicts once
     * {@link #MAX_TOMBSTONES} is exceeded.
     */
    private final Map<String, Long> settledTombstones = new LinkedHashMap<>();

    /**
     * A settlement that could not be fully backed without eating into another
     * active reservation's protected balance. {@code amount} is the exact
     * unbacked remainder -- the player was still paid this in full by the
     * game/delivery layer; this record is what lets an administrator restore
     * exact backing later, and what keeps the dealer's books from silently
     * claiming to be healthy when they are not.
     *
     * <p>Keyed by {@code reservationId} -- a settled reservation id is
     * tombstoned and can never be recreated (see {@link
     * DealerBudgetStore#creditAndReserve}), so a given id can produce at most
     * one shortfall ever, making the id a stable, unique identity for this
     * record. Unlike tombstones, this collection is <strong>never
     * bounded</strong> and never evicts: a shortfall is unresolved economic
     * debt, and silently discarding one to save memory would be exactly the
     * "hide the missing amount" failure mode this record exists to prevent.
     * Shortfalls are expected to be rare (a genuine bug or a dealer running
     * out of money), unlike tombstones which accumulate on every ordinary
     * settlement, so unbounded growth here is not a realistic operational
     * concern the way it would be for tombstones. The only way a shortfall
     * leaves this collection is an explicit, persisted {@link
     * DealerBudgetStore#resolveShortfall} call once an administrator has
     * actually restored the missing backing.
     */
    public record Shortfall(String reservationId, BigDecimal amount, long recordedAtEpochSeconds) {
    }

    /** Reservation id -> its shortfall. Insertion-ordered so the file is stable. */
    private final Map<String, Shortfall> shortfalls = new LinkedHashMap<>();

    /**
     * Reservation id -> the last {@code operationId} applied by {@link
     * DealerBudgetStore#adjustReservation(String, String, String, BigDecimal, BigDecimal)}.
     * Lets a caller distinguish "the same increase attempt, replayed" (same
     * operation id -- a no-op) from "a different increase that happens to
     * land on the same resulting exposure" (a different operation id --
     * credits its stake), which {@code newAmount} alone cannot: a legitimate
     * additional bet can leave a portfolio's worst-case payout unchanged even
     * though it must still add real stake exactly once.
     *
     * <p>Deliberately in-memory only, not persisted: the threat this guards
     * against is a duplicated event within one live session (a double-fired
     * Bukkit click handler), not a replay across a restart -- cross-restart
     * safety already comes from the reservation id itself being stable and
     * derived from commitment identity. Not persisting this also means no
     * schema growth for a purely same-session concern. Cleaned up
     * automatically when the reservation is removed, so it never outlives
     * what it guards.
     */
    private final Map<String, String> lastOperationByReservation = new LinkedHashMap<>();

    public DealerBudgetState(String dealerInternalName) {
        this(dealerInternalName, Money.ZERO, 0L);
    }

    public DealerBudgetState(String dealerInternalName, BigDecimal liveBalance, long refillBoundary) {
        this.dealerInternalName = dealerInternalName;
        this.liveBalance = Money.clampNonNegative(liveBalance);
        this.refillBoundaryEpochSeconds = refillBoundary;
    }

    public String dealerInternalName() {
        return dealerInternalName;
    }

    public BigDecimal liveBalance() {
        return liveBalance;
    }

    public long refillBoundaryEpochSeconds() {
        return refillBoundaryEpochSeconds;
    }

    /** Whether the one-time LIMITED underwriting-baseline seed has ever been applied. */
    public boolean isBaselineInitialized() {
        return baselineInitializedAtEpochSeconds > 0L;
    }

    public long baselineInitializedAtEpochSeconds() {
        return baselineInitializedAtEpochSeconds;
    }

    public Collection<Reservation> reservations() {
        return reservations.values();
    }

    public Reservation reservation(String id) {
        return id == null ? null : reservations.get(id);
    }

    public boolean hasReservation(String id) {
        return id != null && reservations.containsKey(id);
    }

    /** Everything currently promised to live commitments. */
    public BigDecimal reservedTotal() {
        BigDecimal total = Money.ZERO;
        for (Reservation reservation : reservations.values()) {
            total = Money.add(total, reservation.amount());
        }
        return total;
    }

    /**
     * What may still be promised: the balance less what is already promised.
     * This, never {@link #liveBalance()}, is what an admission check looks at.
     */
    public BigDecimal available() {
        return Money.clampNonNegative(Money.subtract(liveBalance, reservedTotal()));
    }

    // ---- mutation (callers must hold the store's lock) -----------------

    void setLiveBalance(BigDecimal value) {
        this.liveBalance = Money.clampNonNegative(value);
    }

    void setRefillBoundary(long epochSeconds) {
        this.refillBoundaryEpochSeconds = epochSeconds;
    }

    /** Marks the one-time baseline seed as applied. Idempotent -- setting it again is harmless. */
    void markBaselineInitialized(long epochSeconds) {
        this.baselineInitializedAtEpochSeconds = epochSeconds;
    }

    /** Load seam: restores the baseline-initialized marker read from disk. */
    void restoreBaselineInitialized(long epochSeconds) {
        markBaselineInitialized(epochSeconds);
    }

    void putReservation(Reservation reservation) {
        reservations.put(reservation.id(), reservation);
    }

    /** Records the stake credited when {@code reservationId} was first created. */
    void recordCreditedStake(String reservationId, BigDecimal stake) {
        if (reservationId == null || stake == null) {
            return;
        }
        creditedStakes.putIfAbsent(reservationId, Money.of(stake));
    }

    /** Load seam: restores a credited-stake record read from disk. */
    void restoreCreditedStake(String reservationId, BigDecimal stake) {
        recordCreditedStake(reservationId, stake);
    }

    /** The stake credited when this reservation was first created, or {@code null} if unknown. */
    BigDecimal creditedStake(String reservationId) {
        return reservationId == null ? null : creditedStakes.get(reservationId);
    }

    Reservation removeReservation(String id) {
        if (id == null) {
            return null;
        }
        lastOperationByReservation.remove(id);
        creditedStakes.remove(id);
        return reservations.remove(id);
    }

    /** Whether {@code operationId} is the same one last applied to this reservation. */
    boolean isSameOperation(String reservationId, String operationId) {
        return operationId != null && operationId.equals(lastOperationByReservation.get(reservationId));
    }

    /** Records the operation id that was just applied to this reservation. */
    void recordOperation(String reservationId, String operationId) {
        if (reservationId == null || operationId == null) {
            return;
        }
        lastOperationByReservation.put(reservationId, operationId);
    }

    /**
     * Marks a commitment id as settled so it can never be recreated as a
     * fresh reservation. Evicts the oldest tombstone once {@link
     * #MAX_TOMBSTONES} is exceeded.
     */
    void tombstone(String id) {
        if (id == null) {
            return;
        }
        settledTombstones.remove(id);
        settledTombstones.put(id, System.currentTimeMillis() / 1000L);
        while (settledTombstones.size() > MAX_TOMBSTONES) {
            String oldest = settledTombstones.keySet().iterator().next();
            settledTombstones.remove(oldest);
        }
    }

    /** Test/load seam: restores a tombstone with its original settled-at time. */
    void restoreTombstone(String id, long settledAtEpochSeconds) {
        if (id == null) {
            return;
        }
        settledTombstones.put(id, settledAtEpochSeconds);
    }

    boolean isTombstoned(String id) {
        return id != null && settledTombstones.containsKey(id);
    }

    /** Settled commitment ids with their settled-at time, for persistence. */
    Set<Map.Entry<String, Long>> tombstoneEntries() {
        return settledTombstones.entrySet();
    }

    /**
     * Records that {@code amount} of a settlement could not be backed without
     * consuming another active reservation's protected balance. Idempotent on
     * {@code reservationId}: since a reservation id can only ever settle once
     * (enforced by tombstoning), a second call for the same id is a replay of
     * the same settlement attempt, not a second, additional debt, so it
     * leaves the existing record untouched rather than overwriting or
     * duplicating it.
     */
    void recordShortfall(String reservationId, BigDecimal amount) {
        if (reservationId == null || !Money.isPositive(amount)) {
            return;
        }
        shortfalls.putIfAbsent(
            reservationId, new Shortfall(reservationId, Money.of(amount), System.currentTimeMillis() / 1000L));
    }

    /** Test/load seam: restores a shortfall record with its original timestamp. */
    void restoreShortfall(String reservationId, BigDecimal amount, long recordedAtEpochSeconds) {
        if (reservationId == null || !Money.isPositive(amount)) {
            return;
        }
        shortfalls.putIfAbsent(reservationId, new Shortfall(reservationId, Money.of(amount), recordedAtEpochSeconds));
    }

    /**
     * Marks a shortfall resolved -- an administrator has restored the missing
     * backing outside the normal wager/settlement flow (e.g. a manual
     * deposit). Idempotent: resolving an id with no outstanding shortfall
     * (already resolved, or never recorded) is a harmless no-op, exactly like
     * replaying {@link DealerBudgetStore#settle} on an unknown id.
     *
     * @return whether a shortfall was actually present and removed
     */
    boolean resolveShortfall(String reservationId) {
        return reservationId != null && shortfalls.remove(reservationId) != null;
    }

    /** Whether this reservation id has an outstanding, unresolved shortfall. */
    public boolean hasShortfall(String reservationId) {
        return reservationId != null && shortfalls.containsKey(reservationId);
    }

    /** Outstanding shortfall records, for persistence and administrative reconciliation. */
    public List<Shortfall> shortfalls() {
        return List.copyOf(shortfalls.values());
    }

    /** A deep-enough copy to restore this state after a failed persist. */
    DealerBudgetState copy() {
        DealerBudgetState copy = new DealerBudgetState(
            dealerInternalName, liveBalance, refillBoundaryEpochSeconds);
        copy.reservations.putAll(reservations);
        copy.settledTombstones.putAll(settledTombstones);
        copy.shortfalls.putAll(shortfalls);
        copy.lastOperationByReservation.putAll(lastOperationByReservation);
        copy.creditedStakes.putAll(creditedStakes);
        copy.baselineInitializedAtEpochSeconds = baselineInitializedAtEpochSeconds;
        return copy;
    }

    /** Restores this state in place from a snapshot taken before a failed write. */
    void restoreFrom(DealerBudgetState snapshot) {
        this.liveBalance = snapshot.liveBalance;
        this.refillBoundaryEpochSeconds = snapshot.refillBoundaryEpochSeconds;
        this.reservations.clear();
        this.reservations.putAll(snapshot.reservations);
        this.settledTombstones.clear();
        this.settledTombstones.putAll(snapshot.settledTombstones);
        this.shortfalls.clear();
        this.shortfalls.putAll(snapshot.shortfalls);
        this.lastOperationByReservation.clear();
        this.lastOperationByReservation.putAll(snapshot.lastOperationByReservation);
        this.creditedStakes.clear();
        this.creditedStakes.putAll(snapshot.creditedStakes);
        this.baselineInitializedAtEpochSeconds = snapshot.baselineInitializedAtEpochSeconds;
    }

    /**
     * Whether the core invariant holds. Checked after loading a file that may
     * have been hand-edited or written by an older version.
     */
    public boolean isConsistent() {
        return !Money.isNegative(liveBalance) && Money.atLeast(liveBalance, reservedTotal());
    }

    @Override
    public String toString() {
        return "DealerBudgetState[" + dealerInternalName
            + ", live=" + Money.store(liveBalance)
            + ", reserved=" + Money.store(reservedTotal())
            + ", reservations=" + reservations.size() + "]";
    }
}
