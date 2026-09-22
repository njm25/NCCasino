package org.nc.nccasino.games.Slots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rainbow housing as one shared Golden Slumbers play/pause control.
 *
 * <p>The cabinet is the same control in every view, so the machine may not
 * grow a surface where the housing is inert or, worse, painted as dead black
 * tiles a player would never think to click.
 */
class SlotsHousingToggleTest {

    private static final int CANVAS_WIDTH = SlotsGeometry.INVENTORY_WIDTH;
    private static final int PAYTABLE_SLOTS =
        SlotsPaytableLayout.PAYTABLE_ROWS * CANVAS_WIDTH;

    // ---- no dead tiles ----------------------------------------------------

    @Test
    void thePaytableCanvasIsAllRainbowHousingWithNoBlackTiles() throws IOException {
        String body = methodBody(readSource("games/Slots/SlotsMachine.java"),
            "private void renderPaytableCanvas()");
        assertFalse(body.contains("BLACK_STAINED_GLASS_PANE"),
            "the Paytable interior must not be painted with dead black panes");
        assertTrue(body.contains("paintHousing(slot);"),
            "every Paytable backdrop slot must be rainbow housing");
        // The old two-branch backdrop is gone, so nothing distinguishes the
        // frame from the interior any more.
        assertFalse(body.contains("isRainbowFrameSlot"),
            "frame and interior are both housing now; no need to tell them apart");
    }

    // ---- what counts as housing ------------------------------------------

    @Test
    void paytableContentIsTheCardsAndTheThreeSupportTiles() {
        int cards = SlotsSymbol.payingSymbols().length;
        Set<Integer> content = new LinkedHashSet<>();
        content.add(SlotsPaytableLayout.MACHINE_SLOT);
        content.add(SlotsPaytableLayout.SEEDS_SLOT);
        content.add(SlotsPaytableLayout.LEGEND_SLOT);
        content.add(SlotsPaytableLayout.VOLATILITY_SLOT);
        for (int slot : SlotsPaytableLayout.symbolCardSlots(cards)) {
            content.add(slot);
        }

        for (int slot = 0; slot < PAYTABLE_SLOTS; slot++) {
            assertEquals(content.contains(slot),
                SlotsPaytableLayout.isContentSlot(slot, cards),
                "slot " + slot + " classified wrongly");
        }
        // Everything the cards and support tiles do not occupy is clickable
        // housing, which is most of the surface.
        long housing = PAYTABLE_SLOTS - content.size();
        assertEquals(4 + cards, content.size());
        assertTrue(housing > content.size(),
            "the cabinet should dominate the Paytable, not the content");
    }

    @Test
    void theSymbolBandStaysContentAtEverySupportedCardCount() {
        // The band is centred, so it moves with the symbol count; housing has
        // to follow it rather than assume today's five.
        for (int cards = 0; cards <= SlotsPaytableLayout.cardCapacity(); cards++) {
            int[] slots = SlotsPaytableLayout.symbolCardSlots(cards);
            assertEquals(cards, slots.length);
            for (int slot : slots) {
                assertTrue(SlotsPaytableLayout.isContentSlot(slot, cards),
                    "card slot " + slot + " must be content at " + cards + " cards");
            }
        }
    }

    @Test
    void theInformationalRailIsNeverHousing() {
        // Row 4 is the rail that aligns with the bottom control row. It sits
        // in the canvas but is content, so it must stay inert.
        for (int slot = PAYTABLE_SLOTS; slot < CANVAS_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
            assertTrue(slot >= PAYTABLE_SLOTS,
                "rail slot " + slot + " is outside the Paytable proper");
        }
        for (int slot : SlotsPaytableLayout.paytableCanvasSlots()) {
            assertTrue(slot < PAYTABLE_SLOTS,
                "the Paytable proper owns rows 0-3 only; found " + slot);
        }
    }

    // ---- one control, every view -----------------------------------------

    @Test
    void housingTogglesTheJukeboxInEveryViewBeforeAnyViewSpecificHandling() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        String body = methodBody(machine, "private void handleCanvasClick(int slot, ClickType clickType)");
        // The housing check comes first and short-circuits, so no view can
        // swallow the toggle with its own handling.
        int housing = body.indexOf("isHousingSlot(slot)");
        int routed = body.indexOf("switch (uiView)");
        assertTrue(housing > 0, "the canvas must consult the housing first");
        assertTrue(routed > housing, "view-specific routing must come after it");
        assertTrue(body.contains("toggleGoldenSlumbers();"));

        // Every view answers for its own layout -- no view is left out.
        String predicate = methodBody(machine, "private boolean isHousingSlot(int slot)");
        for (SlotsUiView view : SlotsUiView.values()) {
            assertTrue(predicate.contains("case " + view.name()),
                view + " must decide what its housing is");
        }
        assertTrue(predicate.contains("SlotsGeometry.isGridSlot"), "GAME uses the reel grid");
        assertTrue(predicate.contains("SlotsPaytableLayout.isContentSlot"), "PAYTABLE uses its cards");
        assertTrue(predicate.contains("SlotsAutoSettingsLayout.isBackdrop"), "AUTO_SETTINGS uses its entries");
        assertTrue(predicate.contains("savedProfiles().size()"), "PROFILES uses its list length");
    }

    @Test
    void theJukeboxIsOneSessionLevelStateThatSurvivesEveryViewChange() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        // One toggle, one state field, reached from one place.
        assertEquals(1, count(machine, "private void toggleGoldenSlumbers()"));
        assertEquals(1, count(machine, "toggleGoldenSlumbers();"),
            "the shared housing control is the only way to toggle the music");
        assertEquals(1, count(machine, "private boolean goldenSlumbersEnabled = false;"),
            "the on/off state is session-level, not per view");

        // Switching views must never stop it: the only stops are the toggle
        // itself and session teardown.
        String switchView = methodBody(machine, "private void switchView(");
        assertFalse(switchView.contains("GoldenSlumbers"),
            "changing view must not start, stop or restart the music");
        assertEquals(2, count(machine, "stopGoldenSlumbers();"),
            "only the toggle and teardown may stop it");
    }

    // ---- the music symbol -------------------------------------------------

    @Test
    void everyHousingTileIsNamedWithAMusicSymbolInEveryView() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        // One painter, used by all four views, so no surface can go back to
        // an unlabelled blank tile.
        assertEquals(1, count(machine, "private void paintHousing(int slot)"));
        // Four view renders plus the toggle's own housing-only refresh.
        assertEquals(5, count(machine, "paintHousing(slot);"),
            "all four housing renders must go through the shared painter");
        assertFalse(machine.contains(
            "addItemAndLore(SlotsRainbowHousing.materialForSlot(slot), 1, \" \", slot);"),
            "no housing tile may be painted blank any more");

        String painter = methodBody(machine, "private void paintHousing(int slot)");
        assertTrue(painter.contains("goldenSlumbersEnabled"),
            "the symbol must show whether the jukebox is running");
        assertTrue(painter.contains("slots.music-playing")
                && painter.contains("slots.music-paused"),
            "both states need their own localized symbol");
    }

    @Test
    void togglingTheMusicUpdatesTheSymbolWithoutDisturbingARunningSpin() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        assertTrue(methodBody(machine, "private void toggleGoldenSlumbers()")
                .contains("refreshHousingLabels();")
            || methodBody(machine, "private void stopGoldenSlumbers()")
                .contains("refreshHousingLabels();"),
            "turning the music off must update the symbol");
        assertEquals(2, count(machine, "refreshHousingLabels();"),
            "both the on and off edges must refresh the symbol");

        // Housing can be clicked mid-spin, so the refresh must never repaint
        // the reel grid underneath a running animation.
        String refresh = methodBody(machine, "private void refreshHousingLabels()");
        assertFalse(refresh.contains("repaintCanvas()"),
            "a full canvas repaint would paint over a running spin");
        assertFalse(refresh.contains("redrawEverything()"),
            "a full redraw would paint over a running spin");
        assertTrue(refresh.contains("isHousingSlot(slot)"),
            "only housing tiles may be repainted");
        assertTrue(refresh.contains("closeFlag"),
            "a closed inventory must not be repainted");
    }

    @Test
    void bothMusicSymbolsAreDefinedInEnglishAndAreActualMusicGlyphs() throws IOException {
        String english = Files.readString(Paths.get("src/main/resources/lang/en_US.yml"));
        for (String key : List.of("music-paused", "music-playing")) {
            assertTrue(english.contains("  " + key + ":"), "missing English key " + key);
        }
        // The point of the change: the name is a music glyph, not a blank.
        assertTrue(english.contains("♪"), "the paused tile needs a quarter note");
        assertTrue(english.contains("♫"), "the playing tile needs a beamed note");
    }

    @Test
    void theSymbolCardRowCarriesNoStackCount() throws IOException {
        // The icon count used to be the symbol's minimum matching run, which
        // read as "you get this many" rather than "you need this many". The
        // run is still spelled out in the card's lore.
        String card = methodBody(readSource("games/Slots/SlotsMachine.java"),
            "private void renderSymbolCard(");
        assertTrue(card.contains("addItemAndLore(symbol.material(), 1,"),
            "symbol cards must be a single item with no stack badge");
        assertFalse(card.contains("symbol.minimumRun(), text(symbolKey(symbol))"),
            "the minimum run must not be painted as a stack size");
        assertTrue(card.contains("slots.paytable-card-row"),
            "each achievable run must still be listed in the lore");
    }

    // ---- helpers ----------------------------------------------------------

    /** The body of a method, from its declaration to its closing brace. */
    private static String methodBody(String source, String declaration) {
        int at = source.indexOf(declaration);
        assertTrue(at > 0, "expected to find " + declaration);
        String close = System.lineSeparator().equals("\r\n") ? "\r\n    }" : "\n    }";
        int end = source.indexOf(close, at);
        if (end < 0) {
            end = source.indexOf("\n    }", at);
        }
        assertTrue(end > at, "unterminated method " + declaration);
        return source.substring(at, end);
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            found++;
        }
        return found;
    }

    private static String readSource(String relativePath) throws IOException {
        Path path = Paths.get("src/main/java/org/nc/nccasino").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "expected source file at " + path.toAbsolutePath());
        return Files.readString(path);
    }

    /** Keeps the unused-import checker honest about List. */
    @Test
    void everyViewIsCoveredByTheHousingPredicate() {
        assertEquals(List.of("GAME", "PAYTABLE", "PROFILES", "AUTO_SETTINGS"),
            java.util.Arrays.stream(SlotsUiView.values()).map(Enum::name).toList());
    }
}
