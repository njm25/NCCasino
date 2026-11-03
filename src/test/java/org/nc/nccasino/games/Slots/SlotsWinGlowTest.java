package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsWinGlowTest {

    private static SlotsPaylineCatalog.Line line(int columns, int rows, int index) {
        return SlotsPaylineCatalog.forGeometry(columns, rows).get(index);
    }

    private static SlotsMath.CatalogLineResult win(SlotsPaylineCatalog.Line line, SlotsSymbol symbol, int run) {
        return new SlotsMath.CatalogLineResult(line, true, symbol, run, 2.5);
    }

    private static Set<Integer> slotsOf(SlotsPaylineCatalog.Line line, int columns, int rows, int run) {
        Set<Integer> slots = new HashSet<>();
        int[] all = line.slots(columns, rows);
        for (int col = 0; col < run; col++) {
            slots.add(all[col]);
        }
        return slots;
    }

    @Test
    void aRunOfThreeOnAFiveReelLineLightsExactlyThoseThreeCells() {
        SlotsPaylineCatalog.Line middle = line(5, 3, 0);
        SlotsWinGlow glow = SlotsWinGlow.of(5, 3, 9, false, List.of(win(middle, SlotsSymbol.BELL, 3)));

        assertEquals(slotsOf(middle, 5, 3, 3), glow.winsBySlot().keySet());
        int[] path = middle.slots(5, 3);
        assertFalse(glow.isLit(path[3]));
        assertFalse(glow.isLit(path[4]));
    }

    @Test
    void overlappingWinningLinesLightTheirUnionAndSharedCellsListEveryWin() {
        SlotsPaylineCatalog.Line a = line(5, 3, 0);
        SlotsPaylineCatalog.Line b = line(5, 3, 3);
        SlotsMath.CatalogLineResult winA = win(a, SlotsSymbol.CHERRY, 5);
        SlotsMath.CatalogLineResult winB = win(b, SlotsSymbol.CHERRY, 3);
        SlotsWinGlow glow = SlotsWinGlow.of(5, 3, 9, false, List.of(winA, winB));

        Set<Integer> expected = new HashSet<>(slotsOf(a, 5, 3, 5));
        expected.addAll(slotsOf(b, 5, 3, 3));
        assertEquals(expected, glow.winsBySlot().keySet());

        boolean sawShared = false;
        for (int slot : slotsOf(a, 5, 3, 5)) {
            if (slotsOf(b, 5, 3, 3).contains(slot)) {
                sawShared = true;
                assertEquals(List.of(winA, winB), glow.winsBySlot().get(slot));
            }
        }
        assertTrue(sawShared, "lines 1 and 4 must share at least one cell for this test to mean anything");
    }

    @Test
    void noWinnersMeansNoGlowAndNothingLit() {
        SlotsWinGlow glow = SlotsWinGlow.of(5, 3, 9, false, List.of());
        assertSame(SlotsWinGlow.NONE, glow);
        assertTrue(glow.isEmpty());
        assertFalse(glow.matches(5, 3, 9));

        SlotsMath.CatalogLineResult losing = SlotsMath.CatalogLineResult.losing(line(5, 3, 0));
        assertSame(SlotsWinGlow.NONE, SlotsWinGlow.of(5, 3, 9, false, List.of(losing)));
    }

    @Test
    void aGlowNoLongerMatchesOnceGeometryOrLineCountChanges() {
        SlotsWinGlow glow = SlotsWinGlow.of(5, 3, 9, false, List.of(win(line(5, 3, 0), SlotsSymbol.SEVEN, 3)));
        assertTrue(glow.matches(5, 3, 9));
        assertFalse(glow.matches(5, 3, 8), "paylines change");
        assertFalse(glow.matches(5, 5, 9), "height change");
        assertFalse(glow.matches(7, 3, 9), "reels change");
    }

    @Test
    void aLineBuiltForADifferentReelCountIsIgnored() {
        SlotsWinGlow glow = SlotsWinGlow.of(5, 3, 9, false, List.of(win(line(7, 3, 0), SlotsSymbol.SEVEN, 3)));
        assertTrue(glow.isEmpty());
    }

    @Test
    void theDemoFlagIsKept() {
        assertTrue(SlotsWinGlow.of(3, 1, 1, true, List.of(win(line(3, 1, 0), SlotsSymbol.LEMON, 3))).demo());
    }

    /**
     * The glow's lifecycle lives in SlotsMachine, which needs a live
     * inventory; these pin its wiring at the source so a refactor cannot
     * quietly drop a clear or the repaint.
     */
    @Test
    void theMachineRepaintsTheGlowAndClearsItOnEveryInvalidatingChange() throws IOException {
        String machine = Files.readString(
            Path.of("src/main/java/org/nc/nccasino/games/Slots/SlotsMachine.java")).replace("\r\n", "\n");

        int game = machine.indexOf("private void renderCanvas()");
        String renderCanvas = machine.substring(game, machine.indexOf("\n    }\n", game));
        assertTrue(renderCanvas.contains("paintWinGlow(displayedWin)"), "Game View repaint must re-apply the glow");
        assertTrue(renderCanvas.contains("displayedWin.matches(columns, config.visibleRows(), config.activeLines())"),
            "a stale glow must be rejected on repaint");

        assertTrue(section(machine, "private void seedReelDisplay(").contains("displayedWin = SlotsWinGlow.NONE"),
            "a spin start must clear the shown glow");
        assertTrue(section(machine, "private void onGeometryChanged()").contains("clearWinGlow()"),
            "height/reels/profile changes must clear the glow");
        assertTrue(section(machine, "private void handleChangeLines(").contains("clearWinGlow()"),
            "a paylines change must clear the glow");
        assertTrue(section(machine, "private void restorePlayCanvas()").contains("displayedWin = lastPaidWin"),
            "returning to Game View must restore the last paid glow");
        assertFalse(section(machine, "private void handleChangeDenomination").contains("clearWinGlow()"),
            "a denomination change keeps the glow");
        assertTrue(machine.contains("meta.setEnchantmentGlintOverride(Boolean.TRUE)"));
    }

    private static String section(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature + " not found");
        return source.substring(start, source.indexOf("\n    }\n", start));
    }
}
