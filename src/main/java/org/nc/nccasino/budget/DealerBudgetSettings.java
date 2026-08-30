package org.nc.nccasino.budget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One dealer's {@code budget:} configuration block, already validated.
 *
 * <p>Configuration and economic state are kept strictly apart. This record is
 * what an administrator writes: the risk policy. The live balance, the active
 * reservations and the refill clock are money, and live in
 * {@link DealerBudgetStore} instead. Editing config must never silently move a
 * dealer's balance, and a balance change must never rewrite config.
 *
 * <p>Every field has a default that reproduces pre-Phase-2 behavior exactly:
 * an absent block means {@link DealerBudgetMode#UNLIMITED} with no refill.
 */
public record DealerBudgetSettings(
    DealerBudgetMode mode,
    BigDecimal underwritingBaseline,
    int guaranteedWorstCaseRounds,
    RefillMode refillMode,
    BigDecimal refillAmount,
    long refillPeriodSeconds,
    BigDecimal refillCap,
    BigDecimal resetTarget,
    List<String> problems
) {

    public static final String PATH_MODE = "budget.mode";
    public static final String PATH_BASELINE = "budget.underwriting-baseline";
    public static final String PATH_GUARANTEED_ROUNDS = "budget.guaranteed-worst-case-rounds";
    public static final String PATH_REFILL_MODE = "budget.refill-mode";
    public static final String PATH_REFILL_AMOUNT = "budget.refill-amount";
    public static final String PATH_REFILL_PERIOD = "budget.refill-period";
    public static final String PATH_REFILL_CAP = "budget.refill-cap";
    public static final String PATH_RESET_TARGET = "budget.reset-target";

    public static final int DEFAULT_GUARANTEED_ROUNDS = 1;
    public static final long DEFAULT_REFILL_PERIOD_SECONDS = 3600L;

    /**
     * Shortest period that may be configured. A one-second refill would make
     * the lazy catch-up loop do arbitrarily much work after a long downtime
     * for no administrative benefit.
     */
    public static final long MIN_REFILL_PERIOD_SECONDS = 60L;

    /**
     * Ceiling on how many missed periods one lazy catch-up applies. A dealer
     * untouched for months must not iterate millions of times on the first
     * click; the cap is applied as a single multiplication, so the resulting
     * balance is the same as looping would give, just bounded.
     */
    public static final long MAX_CATCHUP_PERIODS = 100_000L;

    public DealerBudgetSettings {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /** The behavior of a dealer with no {@code budget:} block: exactly as before Phase 2. */
    public static DealerBudgetSettings unlimited() {
        return new DealerBudgetSettings(
            DealerBudgetMode.UNLIMITED,
            Money.ZERO,
            DEFAULT_GUARANTEED_ROUNDS,
            RefillMode.NONE,
            Money.ZERO,
            DEFAULT_REFILL_PERIOD_SECONDS,
            null,
            Money.ZERO,
            List.of());
    }

    /**
     * Builds settings from already-read raw values, reporting rather than
     * throwing. Kept free of Bukkit so the whole validation matrix is
     * testable; {@code DealerBudgetService} does the config reading.
     *
     * <p>Invalid values never damage stored balances and never rewrite the
     * configuration file. A limited dealer whose risk policy cannot be
     * understood fails closed -- {@link #isUsable()} returns false and every
     * commitment is refused with {@link AdmissionDecision#CONFIGURATION_INVALID}
     * -- because guessing a baseline would underwrite real money on a guess.
     */
    public static DealerBudgetSettings parse(
        String rawMode,
        String rawBaseline,
        String rawGuaranteedRounds,
        String rawRefillMode,
        String rawRefillAmount,
        String rawRefillPeriod,
        String rawRefillCap,
        String rawResetTarget
    ) {
        List<String> problems = new ArrayList<>();

        DealerBudgetMode mode = DealerBudgetMode.parse(rawMode, null);
        if (mode == null) {
            if (rawMode != null && !rawMode.isBlank()) {
                problems.add(PATH_MODE + ": '" + rawMode
                    + "' is not UNLIMITED or LIMITED; treating this dealer as UNLIMITED.");
            }
            mode = DealerBudgetMode.UNLIMITED;
        }

        if (mode == DealerBudgetMode.UNLIMITED) {
            // Nothing else in the block can matter, and an unlimited dealer
            // must not be made unusable by a stray refill typo.
            return new DealerBudgetSettings(
                DealerBudgetMode.UNLIMITED, Money.ZERO, DEFAULT_GUARANTEED_ROUNDS,
                RefillMode.NONE, Money.ZERO, DEFAULT_REFILL_PERIOD_SECONDS, null, Money.ZERO,
                problems);
        }

        BigDecimal baseline = Money.parse(rawBaseline);
        if (baseline == null) {
            problems.add(PATH_BASELINE + ": '" + rawBaseline
                + "' is not a number. A LIMITED dealer needs a baseline to underwrite anything.");
            baseline = Money.ZERO;
        } else if (!Money.isSafe(baseline)) {
            problems.add(PATH_BASELINE + ": " + baseline.toPlainString()
                + " is negative or beyond the supported maximum of " + Money.MAX.toPlainString() + ".");
            baseline = Money.ZERO;
        } else if (!Money.isPositive(baseline)) {
            problems.add(PATH_BASELINE
                + ": a baseline of 0 underwrites nothing, so this dealer would refuse every wager.");
        }

        int guaranteedRounds = parseRounds(rawGuaranteedRounds, problems);

        RefillMode refillMode = RefillMode.parse(rawRefillMode, null);
        if (refillMode == null) {
            if (rawRefillMode != null && !rawRefillMode.isBlank()) {
                problems.add(PATH_REFILL_MODE + ": '" + rawRefillMode
                    + "' is not NONE, ADD or RESET; no refill will be applied.");
            }
            refillMode = RefillMode.NONE;
        }

        long period = parsePeriod(rawRefillPeriod, refillMode, problems);
        BigDecimal refillAmount = parseRefillAmount(rawRefillAmount, refillMode, problems);
        BigDecimal refillCap = parseCap(rawRefillCap, refillMode, problems);
        BigDecimal resetTarget = parseResetTarget(rawResetTarget, refillMode, problems);

        return new DealerBudgetSettings(
            DealerBudgetMode.LIMITED, baseline, guaranteedRounds,
            refillMode, refillAmount, period, refillCap, resetTarget, problems);
    }

    private static int parseRounds(String raw, List<String> problems) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_GUARANTEED_ROUNDS;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < 1) {
                problems.add(PATH_GUARANTEED_ROUNDS + ": " + parsed
                    + " is not a round count; using " + DEFAULT_GUARANTEED_ROUNDS + ".");
                return DEFAULT_GUARANTEED_ROUNDS;
            }
            if (parsed > Integer.MAX_VALUE) {
                problems.add(PATH_GUARANTEED_ROUNDS + ": " + parsed
                    + " is implausibly large; clamping to " + Integer.MAX_VALUE + ".");
                return Integer.MAX_VALUE;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            problems.add(PATH_GUARANTEED_ROUNDS + ": '" + raw
                + "' is not a whole number; using " + DEFAULT_GUARANTEED_ROUNDS + ".");
            return DEFAULT_GUARANTEED_ROUNDS;
        }
    }

    private static long parsePeriod(String raw, RefillMode refillMode, List<String> problems) {
        if (refillMode == RefillMode.NONE) {
            return DEFAULT_REFILL_PERIOD_SECONDS;
        }
        long parsed = parseDurationSeconds(raw);
        if (parsed <= 0) {
            problems.add(PATH_REFILL_PERIOD + ": '" + raw
                + "' is not a duration like 30m, 1h or 1d; using 1h.");
            return DEFAULT_REFILL_PERIOD_SECONDS;
        }
        if (parsed < MIN_REFILL_PERIOD_SECONDS) {
            problems.add(PATH_REFILL_PERIOD + ": " + parsed
                + "s is shorter than the minimum " + MIN_REFILL_PERIOD_SECONDS + "s; using the minimum.");
            return MIN_REFILL_PERIOD_SECONDS;
        }
        return parsed;
    }

    private static BigDecimal parseRefillAmount(String raw, RefillMode refillMode, List<String> problems) {
        if (refillMode != RefillMode.ADD) {
            return Money.ZERO;
        }
        BigDecimal parsed = Money.parse(raw);
        if (parsed == null || !Money.isSafe(parsed)) {
            problems.add(PATH_REFILL_AMOUNT + ": '" + raw
                + "' is not a usable amount; ADD refills are disabled for this dealer.");
            return Money.ZERO;
        }
        if (!Money.isPositive(parsed)) {
            problems.add(PATH_REFILL_AMOUNT + ": 0 adds nothing, so this dealer will never refill.");
        }
        return parsed;
    }

    private static BigDecimal parseCap(String raw, RefillMode refillMode, List<String> problems) {
        if (refillMode != RefillMode.ADD || raw == null || raw.isBlank()) {
            return null;
        }
        BigDecimal parsed = Money.parse(raw);
        if (parsed == null || !Money.isSafe(parsed)) {
            problems.add(PATH_REFILL_CAP + ": '" + raw
                + "' is not a usable amount; refills will be uncapped.");
            return null;
        }
        return parsed;
    }

    private static BigDecimal parseResetTarget(String raw, RefillMode refillMode, List<String> problems) {
        if (refillMode != RefillMode.RESET) {
            return Money.ZERO;
        }
        BigDecimal parsed = Money.parse(raw);
        if (parsed == null || !Money.isSafe(parsed)) {
            problems.add(PATH_RESET_TARGET + ": '" + raw
                + "' is not a usable amount; RESET refills are disabled for this dealer.");
            return Money.ZERO;
        }
        return parsed;
    }

    /** Parses {@code 45s} / {@code 30m} / {@code 1h} / {@code 2d} / a bare second count. */
    public static long parseDurationSeconds(String raw) {
        if (raw == null) {
            return -1L;
        }
        String trimmed = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) {
            return -1L;
        }
        char suffix = trimmed.charAt(trimmed.length() - 1);
        long multiplier = switch (suffix) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86400L;
            default -> 0L;
        };
        String numeric = multiplier == 0L ? trimmed : trimmed.substring(0, trimmed.length() - 1).trim();
        if (multiplier == 0L) {
            multiplier = 1L;
        }
        try {
            long value = Long.parseLong(numeric);
            if (value <= 0) {
                return -1L;
            }
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException | NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Whether this dealer can underwrite commitments at all. An UNLIMITED
     * dealer always can; a LIMITED one needs a positive baseline and no
     * problem that would make its risk policy meaningless.
     */
    public boolean isUsable() {
        if (mode == DealerBudgetMode.UNLIMITED) {
            return true;
        }
        return Money.isPositive(underwritingBaseline) && Money.isSafe(underwritingBaseline);
    }

    /**
     * The most one commitment may cost the house, derived from the fixed
     * baseline rather than the live balance.
     *
     * <p>Using the baseline is the entire point of section 17 of the design:
     * deriving it from the falling live balance would shrink the allowed wager
     * after every loss, then shrink it again, converging on zero. The live
     * balance still applies as a separate, hard affordability check.
     */
    public BigDecimal maxHouseLossPerRound() {
        if (mode == DealerBudgetMode.UNLIMITED) {
            return Money.MAX;
        }
        return Money.divideFloor(underwritingBaseline, Math.max(1, guaranteedWorstCaseRounds));
    }

    /** Whether a refill clock is running at all. */
    public boolean hasRefill() {
        return mode == DealerBudgetMode.LIMITED && refillMode != RefillMode.NONE;
    }
}
