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
    public static final String KEY_LINES = "slots-lines";
    public static final String KEY_HOUSE_EDGE = "slots-house-edge";
    public static final String KEY_VARIANCE = "slots-variance";

    public static final int DEFAULT_COLUMNS = 5;
    public static final int DEFAULT_LINES = 5;

    private final int columns;
    private final int activeLines;
    private final double houseEdge;
    private final SlotsVariance variance;
    private final SlotsPaytable paytable;

    private SlotsConfig(int columns, int activeLines, double houseEdge, SlotsVariance variance) {
        this.columns = columns;
        this.activeLines = activeLines;
        this.houseEdge = houseEdge;
        this.variance = variance;
        this.paytable = SlotsPaytable.forConfig(columns, houseEdge, variance);
    }

    public static SlotsConfig load(Nccasino plugin, String internalName) {
        int rawColumns = plugin.getConfig().getInt(path(internalName, KEY_COLUMNS), DEFAULT_COLUMNS);
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
        return of(rawColumns, rawLines, rawEdge, variance);
    }

    /** {@link #of(int, int, double, SlotsVariance)} at {@link SlotsVariance#BALANCED}. */
    public static SlotsConfig of(int columns, int activeLines, double houseEdge) {
        return of(columns, activeLines, houseEdge, SlotsVariance.BALANCED);
    }

    /** Normalizing factory, usable without a live plugin instance (tests, previews). */
    public static SlotsConfig of(int columns, int activeLines, double houseEdge, SlotsVariance variance) {
        int normalizedColumns = SlotsGeometry.normalizeColumnCount(columns);
        int normalizedLines = SlotsPayline.normalizeLineCount(activeLines);
        double normalizedEdge = SlotsPaytable.normalizeHouseEdge(houseEdge);
        SlotsVariance normalizedVariance = variance == null ? SlotsVariance.BALANCED : variance;
        return new SlotsConfig(normalizedColumns, normalizedLines, normalizedEdge, normalizedVariance);
    }

    /** Writes defaults for any key this dealer does not have yet. Returns true if anything changed. */
    public static boolean ensureDefaults(Nccasino plugin, String internalName) {
        boolean changed = false;
        if (!plugin.getConfig().contains(path(internalName, KEY_COLUMNS))) {
            plugin.getConfig().set(path(internalName, KEY_COLUMNS), DEFAULT_COLUMNS);
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

    /** The dealer's configured maximum width. Players may play fewer lines, but not a different width. */
    public int columns() {
        return columns;
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

    /** A copy of this config with a different active-line count, for in-session line toggling. */
    public SlotsConfig withActiveLines(int lines) {
        return new SlotsConfig(columns, SlotsPayline.normalizeLineCount(lines), houseEdge, variance);
    }
}
