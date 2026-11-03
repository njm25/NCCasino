package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which grid cells a shown result lights up, kept as data so the glow can be
 * repainted after any canvas redraw (a Paytable, Profiles or Auto Settings
 * visit) instead of living only in the inventory until the next repaint wipes
 * it.
 *
 * <p>Only the matched run of each winning line is lit, never the whole line.
 * A cell covered by several winning lines maps to every one of them, in
 * reveal order, so its lore can list each win. The glow remembers the
 * geometry and line count it was computed for: {@link #matches} rejects it
 * once any of those change, so an old win is never painted over a different
 * machine shape.
 */
public final class SlotsWinGlow {

    public static final SlotsWinGlow NONE = new SlotsWinGlow(0, 0, 0, false, List.of(), Map.of());

    private final int columns;
    private final int rows;
    private final int activeLines;
    private final boolean demo;
    private final List<SlotsMath.CatalogLineResult> winners;
    private final Map<Integer, List<SlotsMath.CatalogLineResult>> winsBySlot;

    private SlotsWinGlow(int columns, int rows, int activeLines, boolean demo,
                         List<SlotsMath.CatalogLineResult> winners,
                         Map<Integer, List<SlotsMath.CatalogLineResult>> winsBySlot) {
        this.columns = columns;
        this.rows = rows;
        this.activeLines = activeLines;
        this.demo = demo;
        this.winners = winners;
        this.winsBySlot = winsBySlot;
    }

    /** The glow for {@code winners} (losing results are ignored) on a {@code columns} x {@code rows} grid. */
    public static SlotsWinGlow of(int columns, int rows, int activeLines, boolean demo,
                                  List<SlotsMath.CatalogLineResult> winners) {
        List<SlotsMath.CatalogLineResult> kept = new ArrayList<>();
        Map<Integer, List<SlotsMath.CatalogLineResult>> bySlot = new LinkedHashMap<>();
        for (SlotsMath.CatalogLineResult win : winners) {
            if (win == null || !win.winning() || win.runLength() <= 0) {
                continue;
            }
            int[] path = win.line().rows();
            if (path.length != columns) {
                continue;
            }
            kept.add(win);
            for (int col = 0; col < Math.min(win.runLength(), columns); col++) {
                int slot = SlotsGeometry.gridSlot(columns, rows, path[col], col);
                bySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(win);
            }
        }
        if (kept.isEmpty()) {
            return NONE;
        }
        Map<Integer, List<SlotsMath.CatalogLineResult>> frozen = new LinkedHashMap<>();
        bySlot.forEach((slot, wins) -> frozen.put(slot, List.copyOf(wins)));
        return new SlotsWinGlow(columns, rows, activeLines, demo, List.copyOf(kept),
            Collections.unmodifiableMap(frozen));
    }

    public boolean isEmpty() {
        return winners.isEmpty();
    }

    /** Whether this glow still describes a grid of this shape and line count. */
    public boolean matches(int columns, int rows, int activeLines) {
        return !isEmpty() && this.columns == columns && this.rows == rows && this.activeLines == activeLines;
    }

    public boolean demo() {
        return demo;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public List<SlotsMath.CatalogLineResult> winners() {
        return winners;
    }

    /** Every lit slot, each with the winning lines that cover it, in reveal order. */
    public Map<Integer, List<SlotsMath.CatalogLineResult>> winsBySlot() {
        return winsBySlot;
    }

    public boolean isLit(int slot) {
        return winsBySlot.containsKey(slot);
    }
}
