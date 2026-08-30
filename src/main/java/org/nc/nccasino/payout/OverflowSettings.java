package org.nc.nccasino.payout;

import org.nc.nccasino.Nccasino;

import java.util.Locale;

/**
 * The server-side {@code payouts:} policy block, resolved once per read so a
 * config reload takes effect without a restart.
 *
 * <p>Defaults deliberately preserve the design's stated fresh-install
 * behavior: player choice with Bank selected, a drop limit of one empty
 * inventory, and the bank cleared before any wager.
 */
public record OverflowSettings(
    OverflowMode mode,
    OverflowPreference serverDefault,
    int maxDropStacks,
    long reminderPeriodSeconds,
    boolean clearBankBeforeWager
) {

    public static final String PATH_MODE = "payouts.overflow-mode";
    public static final String PATH_DEFAULT = "payouts.default-player-overflow";
    public static final String PATH_MAX_DROP_STACKS = "payouts.max-drop-stacks";
    public static final String PATH_REMINDER_PERIOD = "payouts.reminder-period";
    public static final String PATH_CLEAR_BEFORE_WAGER = "payouts.clear-bank-before-wager";

    public static final int DEFAULT_MAX_DROP_STACKS = 36;
    public static final long DEFAULT_REMINDER_PERIOD_SECONDS = 3600L;

    /** A reminder period of zero disables the informational reminder entirely. */
    public static final long MIN_REMINDER_PERIOD_SECONDS = 60L;

    public static OverflowSettings defaults() {
        return new OverflowSettings(
            OverflowMode.PLAYER_CHOICE,
            OverflowPreference.BANK,
            DEFAULT_MAX_DROP_STACKS,
            DEFAULT_REMINDER_PERIOD_SECONDS,
            true);
    }

    public static OverflowSettings load(Nccasino plugin) {
        if (plugin == null) {
            return defaults();
        }
        OverflowSettings fallback = defaults();
        return new OverflowSettings(
            OverflowMode.parse(plugin.getConfig().getString(PATH_MODE), fallback.mode()),
            OverflowPreference.parse(plugin.getConfig().getString(PATH_DEFAULT), fallback.serverDefault()),
            normalizeDropStacks(plugin.getConfig().getInt(PATH_MAX_DROP_STACKS, fallback.maxDropStacks())),
            parseDurationSeconds(
                plugin.getConfig().getString(PATH_REMINDER_PERIOD),
                fallback.reminderPeriodSeconds()),
            plugin.getConfig().getBoolean(PATH_CLEAR_BEFORE_WAGER, fallback.clearBankBeforeWager()));
    }

    /**
     * A drop limit of zero is meaningful (never drop, always bank) so it is
     * allowed; negatives are configuration mistakes and clamp to zero. The
     * upper clamp keeps a typo from spawning an unbounded entity burst.
     */
    public static int normalizeDropStacks(int raw) {
        if (raw < 0) {
            return 0;
        }
        return Math.min(raw, 1024);
    }

    /**
     * Parses {@code 1h} / {@code 30m} / {@code 45s} / a bare number of
     * seconds. Pure and total: anything unparseable yields {@code fallback}
     * rather than throwing, since this runs during config load.
     */
    public static long parseDurationSeconds(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (trimmed.equals("0") || trimmed.equals("off") || trimmed.equals("none")) {
            return 0L;
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
                return 0L;
            }
            long seconds = Math.multiplyExact(value, multiplier);
            return Math.max(MIN_REMINDER_PERIOD_SECONDS, seconds);
        } catch (ArithmeticException | NumberFormatException e) {
            return fallback;
        }
    }

    /** The preference actually in force for {@code playerPreference}. */
    public OverflowPreference effectivePreference(OverflowPreference playerPreference) {
        return mode.effectivePreference(playerPreference, serverDefault);
    }
}
