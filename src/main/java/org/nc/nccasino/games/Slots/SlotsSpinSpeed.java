package org.nc.nccasino.games.Slots;

/**
 * The session-local Spin Speed selection. Always defaults to {@link #NORMAL}
 * whenever a new Slots session opens; it is only ever persisted as part of a
 * saved profile, never as a dealer setting.
 *
 * <p>There are two genuinely different timing models here, and both must
 * agree on the same three speeds:
 *
 * <ul>
 *   <li>{@link #scaled(long)} rescales a <em>discrete scheduled delay</em>
 *   (the Auto Spin gap, the win-meter step). {@link #SLOW} stretches it to
 *   150%, {@link #NORMAL} leaves it alone, {@link #FAST} halves it -- never
 *   below one real tick, because a zero-tick scheduled delay is not a faster
 *   event, it is a bug.
 *   <li>{@link #cadenceNumerator()}/{@link #cadenceDenominator()} rescale the
 *   <em>reel animation's simulated-tick cadence</em>. The reel schedule
 *   ({@link SlotsReelPlan}) is defined in simulated ticks and must stay
 *   tick-exact at every speed, so the speed changes only how many simulated
 *   ticks each real server tick is worth: 2/1 for FAST (twice as fast), 1/1
 *   for NORMAL, and 2/3 for SLOW (two simulated ticks every three real
 *   ticks, i.e. 150% duration). See {@link SlotsReelCadence}.
 * </ul>
 *
 * <p>The earlier integer-only {@code speedStep} model could express FAST but
 * had no way to express anything slower than one simulated tick per real
 * tick, which is why SLOW needs the rational cadence rather than one more
 * integer constant.
 *
 * <p>Only paid/Demo Spin presentation timing is ever scaled here -- the
 * once-per-session opening animation, and every RNG/paytable/settlement
 * value, are untouched by this enum entirely.
 */
public enum SlotsSpinSpeed {
    /** 150% of Normal duration: two simulated ticks every three real ticks. */
    SLOW(1.5, 2L, 3L),
    /** The baseline: one simulated tick per real tick. */
    NORMAL(1.0, 1L, 1L),
    /** 50% of Normal duration: two simulated ticks every real tick. */
    FAST(0.5, 2L, 1L);

    private final double durationFactor;
    private final long cadenceNumerator;
    private final long cadenceDenominator;

    SlotsSpinSpeed(double durationFactor, long cadenceNumerator, long cadenceDenominator) {
        this.durationFactor = durationFactor;
        this.cadenceNumerator = cadenceNumerator;
        this.cadenceDenominator = cadenceDenominator;
    }

    /** The next speed in the SLOW -&gt; NORMAL -&gt; FAST -&gt; SLOW cycle. */
    public SlotsSpinSpeed next() {
        return switch (this) {
            case SLOW -> NORMAL;
            case NORMAL -> FAST;
            case FAST -> SLOW;
        };
    }

    /** This speed's presentation duration relative to {@link #NORMAL}: 1.5, 1.0, or 0.5. */
    public double durationFactor() {
        return durationFactor;
    }

    /** How many simulated animation ticks {@link #cadenceDenominator()} real ticks are worth. */
    public long cadenceNumerator() {
        return cadenceNumerator;
    }

    /** How many real server ticks {@link #cadenceNumerator()} simulated ticks are spread over. */
    public long cadenceDenominator() {
        return cadenceDenominator;
    }

    /**
     * Scales a discrete scheduled delay for this speed. {@link #NORMAL}
     * returns {@code ticks} unchanged; {@link #SLOW} stretches it by 1.5 and
     * {@link #FAST} halves it, both rounded, and both floored at 1 tick for
     * any originally-positive value so a scheduled delay can never collapse
     * to zero. A {@code ticks} of 0 stays 0 at every speed -- scaling "no
     * delay at all" is not meaningful.
     */
    public long scaled(long ticks) {
        if (this == NORMAL || ticks <= 0) {
            return ticks;
        }
        long rescaled = Math.round(ticks * durationFactor);
        return Math.max(1L, rescaled);
    }

    /** This speed's {@code slots.spin-speed-*} localization key. */
    public String labelKey() {
        return "slots.spin-speed-" + name().toLowerCase();
    }

    /**
     * Parses a stored profile value, falling back to {@link #NORMAL} for
     * anything unrecognized (including the {@code null} of a hand-edited or
     * truncated profile entry).
     */
    public static SlotsSpinSpeed parse(String raw) {
        if (raw == null) {
            return NORMAL;
        }
        String trimmed = raw.trim();
        for (SlotsSpinSpeed speed : values()) {
            if (speed.name().equalsIgnoreCase(trimmed)) {
                return speed;
            }
        }
        return NORMAL;
    }
}
