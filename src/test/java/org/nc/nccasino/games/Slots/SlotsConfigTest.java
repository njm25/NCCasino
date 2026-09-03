package org.nc.nccasino.games.Slots;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link SlotsConfig}'s redesign addition: the per-dealer {@code slots-rows} default height. */
class SlotsConfigTest {

    private static final String DEALER = "vault";

    private Nccasino plugin;
    private FileConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
    }

    @Test
    void missingSlotsRowsDefaultsToThree() {
        SlotsConfig loaded = SlotsConfig.load(plugin, DEALER);
        assertEquals(3, loaded.visibleRows());
    }

    @Test
    void ensureDefaultsAddsSlotsRowsWhenMissing() {
        assertTrue(SlotsConfig.ensureDefaults(plugin, DEALER));
        assertEquals(3, config.getInt("dealers." + DEALER + ".slots-rows"));
    }

    @Test
    void ensureDefaultsIsANoOpOnceEveryKeyExists() {
        SlotsConfig.ensureDefaults(plugin, DEALER);
        assertTrue(!SlotsConfig.ensureDefaults(plugin, DEALER), "a second call must change nothing");
    }

    @Test
    void supportedAndInvalidRowValuesNormalize() {
        for (int rows : new int[] {1, 3, 5}) {
            config.set("dealers." + DEALER + ".slots-rows", rows);
            assertEquals(rows, SlotsConfig.load(plugin, DEALER).visibleRows());
        }
        config.set("dealers." + DEALER + ".slots-rows", 2);
        assertEquals(3, SlotsConfig.load(plugin, DEALER).visibleRows(), "an invalid stored height normalizes to 3");
        config.set("dealers." + DEALER + ".slots-rows", -7);
        assertEquals(1, SlotsConfig.load(plugin, DEALER).visibleRows());
        config.set("dealers." + DEALER + ".slots-rows", 999);
        assertEquals(5, SlotsConfig.load(plugin, DEALER).visibleRows());
    }

    @Test
    void heightOneEffectiveLinesIsAlwaysOneEvenIfStaleConfigSaysNine() {
        config.set("dealers." + DEALER + ".slots-rows", 1);
        config.set("dealers." + DEALER + ".slots-lines", 9);
        SlotsConfig loaded = SlotsConfig.load(plugin, DEALER);
        assertEquals(1, loaded.visibleRows());
        assertEquals(1, loaded.activeLines(), "a height-1 machine must never load with more than 1 active line");
    }

    @Test
    void adminCyclingWrapsAndPersists() {
        SlotsConfig.setRows(plugin, DEALER, 1);
        assertEquals(1, SlotsConfig.load(plugin, DEALER).visibleRows());
        SlotsConfig.setRows(plugin, DEALER, 3);
        assertEquals(3, SlotsConfig.load(plugin, DEALER).visibleRows());
        SlotsConfig.setRows(plugin, DEALER, 5);
        assertEquals(5, SlotsConfig.load(plugin, DEALER).visibleRows());
        // An out-of-range write still normalizes rather than corrupting config.
        SlotsConfig.setRows(plugin, DEALER, 4);
        assertEquals(3, SlotsConfig.load(plugin, DEALER).visibleRows());
    }

    @Test
    void existingColumnsAndLineSettingsAreRetainedAlongsideTheNewKey() {
        config.set("dealers." + DEALER + ".slots-columns", 7);
        config.set("dealers." + DEALER + ".slots-lines", 9);
        config.set("dealers." + DEALER + ".slots-rows", 5);
        SlotsConfig loaded = SlotsConfig.load(plugin, DEALER);
        assertEquals(7, loaded.columns());
        assertEquals(9, loaded.activeLines());
        assertEquals(5, loaded.visibleRows());
    }

    @Test
    void configPathIsDirectlyUnderTheDealerAlongsideExistingSlotsKeys() {
        SlotsConfig.setRows(plugin, DEALER, 5);
        assertTrue(config.contains("dealers." + DEALER + ".slots-rows"));
    }

    @Test
    void enteringHeightOneClampsToOneAndLeavingItStaysAtOne() {
        SlotsConfig config5 = SlotsConfig.of(5, 5, 9, 0.03, SlotsVariance.BALANCED);
        assertEquals(9, config5.activeLines());
        SlotsConfig switchedToOne = config5.withVisibleRows(1);
        assertEquals(1, switchedToOne.activeLines());
        SlotsConfig switchedBackToThree = switchedToOne.withVisibleRows(3);
        assertEquals(1, switchedBackToThree.activeLines(),
            "switching back to height 3 must not secretly restore the earlier line count");
    }

    @Test
    void directHeightThreeToFiveChangesPreserveTheActiveLineCount() {
        // Resolved decision (redesign audit Section 12): 3 <-> 5 both offer
        // the full 1..9 range, so a direct change between them keeps the
        // player's current selection rather than discarding it.
        SlotsConfig height3 = SlotsConfig.of(5, 3, 7, 0.03, SlotsVariance.BALANCED);
        assertEquals(7, height3.activeLines());

        SlotsConfig height5 = height3.withVisibleRows(5);
        assertEquals(7, height5.activeLines(), "3 -> 5 must preserve the active line count");

        SlotsConfig backToThree = height5.withVisibleRows(3);
        assertEquals(7, backToThree.activeLines(), "5 -> 3 must preserve the active line count");
    }

    // ---- SlotsMenu.cycleRows's actual persistence orchestration (post-audit
    // correction Section 2) -- these reproduce the real read-transition-write
    // sequence against the mocked config fixture and read back the raw
    // stored slots-lines value, not just SlotsConfig.load()'s clamped
    // activeLines(), so a bug that only shows up in the persisted config
    // cannot hide behind the load-time clamp.

    private void cycleRowsLikeSlotsMenu(int direction) {
        SlotsConfig loaded = SlotsConfig.load(plugin, DEALER);
        SlotsAdminSettingsTransitions.RowsTransition transition =
            SlotsAdminSettingsTransitions.rowsTransition(loaded.visibleRows(), loaded.activeLines(), direction);
        SlotsConfig.setRows(plugin, DEALER, transition.nextRows());
        SlotsConfig.setLines(plugin, DEALER, transition.nextPersistedLines());
    }

    @Test
    void cyclingFromStaleHeightOneRawNineLinesPersistsOneNotNine() {
        // A hand-edited or legacy config: SlotsConfig.load() already
        // displays activeLines()==1 (it clamps at height 1), but the raw
        // stored slots-lines is a stale 9 predating this persistence rule.
        config.set("dealers." + DEALER + ".slots-rows", 1);
        config.set("dealers." + DEALER + ".slots-lines", 9);

        cycleRowsLikeSlotsMenu(1); // height 1 -> 3

        assertEquals(3, SlotsConfig.load(plugin, DEALER).visibleRows());
        assertEquals(1, config.getInt("dealers." + DEALER + ".slots-lines"),
            "the stale raw 9 must be overwritten with 1, never resurface");
        assertEquals(1, SlotsConfig.load(plugin, DEALER).activeLines());
    }

    @Test
    void cyclingBetweenThreeAndFivePersistsTheRawLineCountUnchanged() {
        config.set("dealers." + DEALER + ".slots-rows", 3);
        config.set("dealers." + DEALER + ".slots-lines", 7);

        cycleRowsLikeSlotsMenu(1); // 3 -> 5

        assertEquals(5, SlotsConfig.load(plugin, DEALER).visibleRows());
        assertEquals(7, config.getInt("dealers." + DEALER + ".slots-lines"),
            "a 3<->5 move must never alter the stored line count");
    }

    @Test
    void cyclingIntoHeightOnePersistsRawLinesAsOne() {
        config.set("dealers." + DEALER + ".slots-rows", 5);
        config.set("dealers." + DEALER + ".slots-lines", 7);

        cycleRowsLikeSlotsMenu(1); // 5 -> 1 (wrap)

        assertEquals(1, SlotsConfig.load(plugin, DEALER).visibleRows());
        assertEquals(1, config.getInt("dealers." + DEALER + ".slots-lines"));
    }

    @Test
    void cyclingOutOfHeightOneThenBackInNeverResurrectsAPreHeightOneValue() {
        config.set("dealers." + DEALER + ".slots-rows", 3);
        config.set("dealers." + DEALER + ".slots-lines", 7);

        cycleRowsLikeSlotsMenu(-1); // 3 -> 1
        assertEquals(1, config.getInt("dealers." + DEALER + ".slots-lines"));

        cycleRowsLikeSlotsMenu(1); // 1 -> 3
        assertEquals(1, config.getInt("dealers." + DEALER + ".slots-lines"),
            "the pre-height-1 value of 7 must not resurface once height 1 has cleaned it");
    }

    @Test
    void withActiveLinesClampsAgainstTheCurrentHeight() {
        SlotsConfig heightOne = SlotsConfig.of(5, 1, 1, 0.03, SlotsVariance.BALANCED);
        assertEquals(1, heightOne.withActiveLines(9).activeLines());

        SlotsConfig heightThree = SlotsConfig.of(5, 3, 1, 0.03, SlotsVariance.BALANCED);
        assertEquals(7, heightThree.withActiveLines(7).activeLines());
    }
}
