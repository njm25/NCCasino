package org.nc.nccasino.games.Slots;

import java.util.Objects;

/**
 * The authoritative record of one accepted, real-money spin: everything
 * needed to reconstruct the exact visible outcome from strip identity plus
 * stops plus geometry, without drawing any further randomness.
 *
 * <p>The committed <em>stops</em> -- not the derived grid -- are the
 * authoritative result, and construction is stop-authoritative to make that
 * a structural guarantee rather than a documented convention. This is a
 * plain final class rather than a record specifically so there is no public
 * constructor a caller could hand a mismatched {@link SlotsOutcome} to in the
 * first place: the only way to build one is {@link #fromStops}, which always
 * derives {@link #outcome()} itself via
 * {@link SlotsSpinGenerator#outcomeFromStops(int[], int, SlotsVariance)} from
 * {@link #stops()}, {@link #visibleRows()}, and {@link #variance()}. This is
 * what lets a later height change re-derive only the visible window from the
 * same physical result, and what a replay/audit tool needs to prove a payout
 * without trusting the grid alone.
 *
 * <p>{@code variance} is a required, non-null input to {@link #fromStops} --
 * it is never silently normalized to a default here. A caller with an
 * optional/nullable variance (e.g. a possibly-unset {@link SlotsPaytable})
 * must resolve its own default before calling in, so this class's contract
 * stays "exactly what was asked for," not "whatever seemed reasonable."
 */
public final class SlotsCommittedResult {

    private final int columns;
    private final int visibleRows;
    private final SlotsVariance variance;
    private final int[] stops;
    private final SlotsOutcome outcome;

    private SlotsCommittedResult(int columns, int visibleRows, SlotsVariance variance, int[] stops) {
        this.columns = columns;
        this.visibleRows = visibleRows;
        this.variance = variance;
        this.stops = stops;
        this.outcome = SlotsSpinGenerator.outcomeFromStops(stops, visibleRows, variance);
    }

    /**
     * The sole construction path. Draws no randomness: derives the outcome
     * from already-committed stops.
     *
     * @throws NullPointerException if {@code variance} is null
     * @throws IllegalArgumentException if the geometry is unsupported,
     *     {@code stops} is null/wrong-length, or any stop is outside
     *     {@code [0, SlotsReelStrip.SIZE)}
     */
    public static SlotsCommittedResult fromStops(
        int columns, int visibleRows, SlotsVariance variance, int[] stops) {

        if (stops == null || stops.length != columns) {
            throw new IllegalArgumentException(
                "stops must have exactly " + columns + " entries; got "
                    + (stops == null ? "null" : stops.length));
        }
        SlotsGeometry.requireSupportedColumnCount(columns);
        SlotsGeometry.requireSupportedRowCount(visibleRows);
        Objects.requireNonNull(variance, "variance must not be null");
        for (int stop : stops) {
            if (stop < 0 || stop >= SlotsReelStrip.SIZE) {
                throw new IllegalArgumentException(
                    "stop must be in [0, " + SlotsReelStrip.SIZE + "); got " + stop);
            }
        }
        return new SlotsCommittedResult(columns, visibleRows, variance, stops.clone());
    }

    public int columns() {
        return columns;
    }

    public int visibleRows() {
        return visibleRows;
    }

    public SlotsVariance variance() {
        return variance;
    }

    /** Defensive copy -- mutating the returned array never affects this result. */
    public int[] stops() {
        return stops.clone();
    }

    /** Immutable by construction: always the fresh derivation from {@link #stops()}. */
    public SlotsOutcome outcome() {
        return outcome;
    }

    /**
     * The same committed stops re-derived at a different visible height --
     * changing only the centred window offsets, never the physical result.
     */
    public SlotsCommittedResult withVisibleRows(int newVisibleRows) {
        if (newVisibleRows == visibleRows) {
            return this;
        }
        return fromStops(columns, newVisibleRows, variance, stops);
    }
}
