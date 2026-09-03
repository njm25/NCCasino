package org.nc.nccasino.games.Slots;

import org.nc.nccasino.Nccasino;

/**
 * Per-dealer Slots settings, read from {@code dealers.<name>.*} the same way
 * every other game's options are.
 *
 * <p>Every value is normalized on read rather than trusted, so a hand-edited
 * config can never put a machine into a state the layout or the payout maths
 * cannot represent -- an even column count, a tenth payline, or a 40% house
 * edge all clamp to something legal instead of throwing at render time.
 */
public final class SlotsConfig {

    public static final String KEY_COLUMNS = "slots-columns";
    public static final String KEY_ROWS = "slots-rows";
    public static final String KEY_LINES = "slots-lines";
    public static final String KEY_HOUSE_EDGE = "slots-house-edge";
    public static final String KEY_VARIANCE = "slots-variance";

    public static final int DEFAULT_COLUMNS = 5;
    public static final int DEFAULT_ROWS = 3;
    public static final int DEFAULT_LINES = 5;

    private final int columns;
    private final int visibleRows;
    private final int activeLines;
    private final double houseEdge;
    private final SlotsVariance variance;
    private final SlotsPaytable paytable;

    private SlotsConfig(int columns, int visibleRows, int activeLines, double houseEdge, SlotsVariance variance) {
        this.columns = columns;
        this.visibleRows = visibleRows;
        this.activeLines = activeLines;
        this.houseEdge = houseEdge;
        this.variance = variance;
        this.paytable = SlotsPaytable.forConfig(columns, houseEdge, variance);
    }

    public static SlotsConfig load(Nccasino plugin, String internalName) {
        int rawColumns = plugin.getConfig().getInt(path(internalName, KEY_COLUMNS), DEFAULT_COLUMNS);
        int rawRows = plugin.getConfig().getInt(path(internalName, KEY_ROWS), DEFAULT_ROWS);
        int rawLines = plugin.getConfig().getInt(path(internalName, KEY_LINES), DEFAULT_LINES);
        double rawEdge = plugin.getConfig().getDouble(
            path(internalName, KEY_HOUSE_EDGE), SlotsPaytable.DEFAULT_HOUSE_EDGE);
        String rawVariance = plugin.getConfig().getString(path(internalName, KEY_VARIANCE));
        SlotsVariance variance = SlotsVariance.parse(rawVariance, null);
        if (variance == null) {
            if (rawVariance != null && !rawVariance.isBlank()) {
                plugin.getLogger().warning("[NCCasino] Dealer '" + internalName + "' slots-variance '"
                    + rawVariance + "' is not a recognized level; using BALANCED. The stored"
                    + " configuration value was left unchanged.");
            }
            variance = SlotsVariance.BALANCED;
        }
        return of(rawColumns, rawRows, rawLines, rawEdge, variance);
    }

    /** {@link #of(int, int, int, double, SlotsVariance)} at {@link SlotsVariance#BALANCED} and {@link #DEFAULT_ROWS}. */
    public static SlotsConfig of(int columns, int activeLines, double houseEdge) {
        return of(columns, DEFAULT_ROWS, activeLines, houseEdge, SlotsVariance.BALANCED);
    }

    /** {@link #of(int, int, int, double, SlotsVariance)} at {@link #DEFAULT_ROWS}. */
    public static SlotsConfig of(int columns, int activeLines, double houseEdge, SlotsVariance variance) {
        return of(columns, DEFAULT_ROWS, activeLines, houseEdge, variance);
    }

    /**
     * Normalizing factory, usable without a live plugin instance (tests,
     * previews). A height-1 machine's line count always normalizes to
     * exactly 1, regardless of the requested value -- see
     * {@link SlotsPaylineCatalog#normalizeLineCount}.
     */
    public static SlotsConfig of(int columns, int visibleRows, int activeLines, double houseEdge, SlotsVariance variance) {
        int normalizedColumns = SlotsGeometry.normalizeColumnCount(columns);
        int normalizedRows = SlotsGeometry.normalizeRowCount(visibleRows);
        int normalizedLines = SlotsPaylineCatalog.normalizeLineCount(normalizedRows, activeLines);
        double normalizedEdge = SlotsPaytable.normalizeHouseEdge(houseEdge);
        SlotsVariance normalizedVariance = variance == null ? SlotsVariance.BALANCED : variance;
        return new SlotsConfig(normalizedColumns, normalizedRows, normalizedLines, normalizedEdge, normalizedVariance);
    }

    /** Writes defaults for any key this dealer does not have yet. Returns true if anything changed. */
    public static boolean ensureDefaults(Nccasino plugin, String internalName) {
        boolean changed = false;
        if (!plugin.getConfig().contains(path(internalName, KEY_COLUMNS))) {
            plugin.getConfig().set(path(internalName, KEY_COLUMNS), DEFAULT_COLUMNS);
            changed = true;
        }
        if (!plugin.getConfig().contains(path(internalName, KEY_ROWS))) {
            plugin.getConfig().set(path(internalName, KEY_ROWS), DEFAULT_ROWS);
            changed = true;
        }
        if (!plugin.getConfig().contains(path(internalName, KEY_LINES))) {
            plugin.getConfig().set(path(internalName, KEY_LINES), DEFAULT_LINES);
            changed = true;
        }
        if (!plugin.getConfig().contains(path(internalName, KEY_HOUSE_EDGE))) {
            plugin.getConfig().set(path(internalName, KEY_HOUSE_EDGE), SlotsPaytable.DEFAULT_HOUSE_EDGE);
            changed = true;
        }
        return changed;
    }

    public static void setColumns(Nccasino plugin, String internalName, int columns) {
        plugin.getConfig().set(path(internalName, KEY_COLUMNS), SlotsGeometry.normalizeColumnCount(columns));
    }

    /** Administrative default height. Never moves an in-progress session's own selection. */
    public static void setRows(Nccasino plugin, String internalName, int rows) {
        plugin.getConfig().set(path(internalName, KEY_ROWS), SlotsGeometry.normalizeRowCount(rows));
    }

    public static void setLines(Nccasino plugin, String internalName, int lines) {
        plugin.getConfig().set(path(internalName, KEY_LINES), SlotsPayline.normalizeLineCount(lines));
    }

    public static void setHouseEdge(Nccasino plugin, String internalName, double houseEdge) {
        plugin.getConfig().set(path(internalName, KEY_HOUSE_EDGE), SlotsPaytable.normalizeHouseEdge(houseEdge));
    }

    /** Administrative only -- never called from a config-load/validation path. */
    public static void setVariance(Nccasino plugin, String internalName, SlotsVariance variance) {
        plugin.getConfig().set(
            path(internalName, KEY_VARIANCE), (variance == null ? SlotsVariance.BALANCED : variance).name());
    }

    private static String path(String internalName, String key) {
        return "dealers." + internalName + "." + key;
    }

    /**
     * The dealer's configured default width. A session can still change its
     * own width in-machine via the Reels control ({@code REELS_SLOT} in
     * {@link SlotsMachine}) -- this is only the starting value a freshly
     * opened machine loads.
     */
    public int columns() {
        return columns;
    }

    /** The machine's selected visible height: 1, 3, or 5. */
    public int visibleRows() {
        return visibleRows;
    }

    /** How many paylines are live. Also the number of per-line stakes in a spin's total bet. */
    public int activeLines() {
        return activeLines;
    }

    public double houseEdge() {
        return houseEdge;
    }

    public SlotsVariance variance() {
        return variance;
    }

    public SlotsPaytable paytable() {
        return paytable;
    }

    /**
     * A copy of this config with a different active-line count, for
     * in-session line toggling. Clamped against this config's own
     * {@link #visibleRows()} -- a height-1 machine always lands back on 1.
     */
    public SlotsConfig withActiveLines(int lines) {
        return new SlotsConfig(columns, visibleRows, SlotsPaylineCatalog.normalizeLineCount(visibleRows, lines), houseEdge, variance);
    }

    /**
     * A copy of this config at a different visible height, for in-session
     * height toggling.
     *
     * <p>Entering height 1 always clamps active lines to exactly 1, and
     * returning from height 1 to 3 or 5 stays at 1 -- it never secretly
     * restores a previous larger line count. A direct height 3 &lt;-&gt; 5
     * change instead <em>preserves</em> the current active-line count,
     * because both geometries offer the full 1..9 range and there is no
     * reason to discard the player's selection just for moving between two
     * heights that both support it (resolved decision, redesign audit
     * Section 12 -- narrower than the earlier "every height change resets
     * to 1" rule).
     */
    public SlotsConfig withVisibleRows(int rows) {
        int normalizedRows = SlotsGeometry.normalizeRowCount(rows);
        int lines = normalizedRows == 1 ? 1 : SlotsPaylineCatalog.normalizeLineCount(normalizedRows, activeLines);
        return new SlotsConfig(columns, normalizedRows, lines, houseEdge, variance);
    }

    /** A copy of this config with a different column count, keeping height and line count where possible. */
    public SlotsConfig withColumns(int newColumns) {
        int normalizedColumns = SlotsGeometry.normalizeColumnCount(newColumns);
        return new SlotsConfig(normalizedColumns, visibleRows, activeLines, houseEdge, variance);
    }
}
