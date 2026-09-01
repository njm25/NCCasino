package org.nc.nccasino.budget;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    /** Reservation id -> reservation. Insertion-ordered so the file is stable. */
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    /**
     * Settled reservation ids -> when they were settled (epoch seconds), so a
     * settled commitment cannot be recreated via {@link
     * DealerBudgetStore#creditAndReserve}. Insertion-ordered so the oldest
     * entry is always first, which is what {@link #tombstone} evicts once
     * {@link #MAX_TOMBSTONES} is exceeded.
     */
    private final Map<String, Long> settledTombstones = new LinkedHashMap<>();

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

    void putReservation(Reservation reservation) {
        reservations.put(reservation.id(), reservation);
    }

    Reservation removeReservation(String id) {
        return id == null ? null : reservations.remove(id);
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

    /** A deep-enough copy to restore this state after a failed persist. */
    DealerBudgetState copy() {
        DealerBudgetState copy = new DealerBudgetState(
            dealerInternalName, liveBalance, refillBoundaryEpochSeconds);
        copy.reservations.putAll(reservations);
        copy.settledTombstones.putAll(settledTombstones);
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
