package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Durable, per-player, globally-portable Slots profiles.
 *
 * <p>Two things have to hold at once here: the Profiles view is one
 * un-paginated 45-slot canvas, so the per-player cap and the contiguous
 * row-major ordering are load-bearing UI facts, not merely storage details;
 * and every mutation has to report honestly whether it reached disk, because
 * telling a player "saved" off a failed write is worse than refusing.
 */
class SlotsProfileStoreTest {

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private SlotsProfileStore store;
    private UUID player;
    private UUID otherPlayer;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SlotsProfileStoreTest"));
        store = new SlotsProfileStore(plugin);
        player = UUID.randomUUID();
        otherPlayer = UUID.randomUUID();
    }

    private static SlotsProfile profile(String name) {
        return new SlotsProfile(name, 3, 5, 5, 10.0, SlotsSpinSpeed.NORMAL,
            SlotsAutoSpinSettings.defaults());
    }

    private SlotsProfileStore reload() {
        return new SlotsProfileStore(plugin);
    }

    // ---- capacity --------------------------------------------------------

    @Test
    void theCapIsExactlyOneUnPaginatedCanvasOfFortyFive() {
        assertEquals(45, SlotsProfileStore.MAX_PROFILES_PER_PLAYER);
    }

    @Test
    void aPlayerMaySaveUpToFortyFiveProfilesAndNoMore() {
        for (int i = 0; i < SlotsProfileStore.MAX_PROFILES_PER_PLAYER; i++) {
            assertEquals(SlotsProfileStore.SaveResult.SAVED,
                store.save(player, profile("p" + i), false), "profile " + i);
        }
        assertEquals(45, store.countFor(player));
        assertTrue(store.isFullFor(player));
        assertEquals(SlotsProfileStore.SaveResult.FULL, store.save(player, profile("p45"), false));
        assertEquals(45, store.countFor(player));
    }

    @Test
    void aFullPlayerMayStillOverwriteAnExistingProfile() {
        for (int i = 0; i < SlotsProfileStore.MAX_PROFILES_PER_PLAYER; i++) {
            store.save(player, profile("p" + i), false);
        }
        assertEquals(SlotsProfileStore.SaveResult.OVERWROTE,
            store.save(player, new SlotsProfile("p7", 5, 7, 9, 25.0, SlotsSpinSpeed.SLOW,
                SlotsAutoSpinSettings.defaults()), true));
        assertEquals(45, store.countFor(player));
        assertNotNull(store.profileAt(player, 7), "an overwrite must keep the profile's position");
        assertEquals("p7", store.profileAt(player, 7).name());
        assertEquals(5, store.profileAt(player, 7).height());
    }

    // ---- ordering --------------------------------------------------------

    @Test
    void profilesAreListedInDeterministicInsertionOrder() {
        store.save(player, profile("alpha"), false);
        store.save(player, profile("bravo"), false);
        store.save(player, profile("charlie"), false);
        List<SlotsProfile> listed = store.profilesFor(player);
        assertEquals(List.of("alpha", "bravo", "charlie"),
            listed.stream().map(SlotsProfile::name).toList());
        assertEquals("alpha", store.profileAt(player, 0).name());
        assertEquals("bravo", store.profileAt(player, 1).name());
        assertEquals("charlie", store.profileAt(player, 2).name());
    }

    @Test
    void anOverwriteKeepsTheProfileWhereItAlreadySatInTheList() {
        store.save(player, profile("alpha"), false);
        store.save(player, profile("bravo"), false);
        store.save(player, profile("charlie"), false);
        store.save(player, new SlotsProfile("alpha", 5, 7, 9, 100.0, SlotsSpinSpeed.FAST,
            SlotsAutoSpinSettings.defaults()), true);
        assertEquals(List.of("alpha", "bravo", "charlie"),
            store.profilesFor(player).stream().map(SlotsProfile::name).toList());
        assertEquals(7, store.profileAt(player, 0).reels());
    }

    @Test
    void anEmptyPositionInTheListIsGenuinelyNothing() {
        store.save(player, profile("only"), false);
        assertNotNull(store.profileAt(player, 0));
        assertNull(store.profileAt(player, 1));
        assertNull(store.profileAt(player, 44));
        assertNull(store.profileAt(player, -1));
        assertNull(store.profileAt(UUID.randomUUID(), 0));
    }

    @Test
    void theReturnedListIsAnUnmodifiableSnapshot() {
        store.save(player, profile("alpha"), false);
        List<SlotsProfile> listed = store.profilesFor(player);
        try {
            listed.add(profile("smuggled"));
            throw new AssertionError("the store's list must not be mutable from outside");
        } catch (UnsupportedOperationException expected) {
            // exactly right
        }
        assertEquals(1, store.countFor(player));
        assertEquals(List.of(), store.profilesFor(UUID.randomUUID()));
    }

    // ---- names -----------------------------------------------------------

    @Test
    void uniquenessIsCaseInsensitive() {
        assertEquals(SlotsProfileStore.SaveResult.SAVED, store.save(player, profile("High Roller"), false));
        assertTrue(store.hasProfileNamed(player, "high roller"));
        assertTrue(store.hasProfileNamed(player, "HIGH ROLLER"));
        assertFalse(store.hasProfileNamed(player, "High Rollers"));
        assertEquals(SlotsProfileStore.SaveResult.DUPLICATE,
            store.save(player, profile("hIgH rOlLeR"), false));
        assertEquals(1, store.countFor(player));
    }

    @Test
    void aDuplicateOverwritesOnlyWhenTheOverwriteIsAuthorized() {
        store.save(player, profile("solo"), false);
        assertEquals(SlotsProfileStore.SaveResult.DUPLICATE, store.save(player, profile("SOLO"), false));
        assertEquals(SlotsProfileStore.SaveResult.OVERWROTE,
            store.save(player, new SlotsProfile("SOLO", 1, 3, 1, 5.0, SlotsSpinSpeed.SLOW,
                SlotsAutoSpinSettings.defaults()), true));
        assertEquals(1, store.countFor(player));
        assertEquals("SOLO", store.profileAt(player, 0).name());
        assertEquals(1, store.profileAt(player, 0).height());
    }

    // ---- deletion --------------------------------------------------------

    @Test
    void deletionIsImmediateAndCompactsTheListUp() {
        store.save(player, profile("a"), false);
        store.save(player, profile("b"), false);
        store.save(player, profile("c"), false);
        assertTrue(store.deleteAt(player, 1));
        assertEquals(List.of("a", "c"), store.profilesFor(player).stream().map(SlotsProfile::name).toList());
        assertEquals("c", store.profileAt(player, 1).name());
        assertNull(store.profileAt(player, 2));
    }

    @Test
    void deletingAnEmptyPositionChangesNothing() {
        store.save(player, profile("a"), false);
        assertFalse(store.deleteAt(player, 1));
        assertFalse(store.deleteAt(player, -1));
        assertFalse(store.deleteAt(UUID.randomUUID(), 0));
        assertEquals(1, store.countFor(player));
    }

    @Test
    void deletingTheLastProfileLeavesTheOwnerWithNothing() {
        store.save(player, profile("only"), false);
        assertTrue(store.deleteAt(player, 0));
        assertEquals(0, store.countFor(player));
        assertFalse(store.isFullFor(player));
        assertEquals(List.of(), store.profilesFor(player));
        assertEquals(0, reload().countFor(player));
    }

    // ---- persistence -----------------------------------------------------

    @Test
    void everyStoredValueSurvivesARestartExactly() {
        SlotsProfile saved = new SlotsProfile("High Roller", 5, 7, 9, 25.0, SlotsSpinSpeed.SLOW,
            SlotsAutoSpinSettings.of(40L, true, 12.5, 900.0, 250.0));
        assertEquals(SlotsProfileStore.SaveResult.SAVED, store.save(player, saved, false));

        SlotsProfile loaded = reload().profileAt(player, 0);
        assertNotNull(loaded);
        assertEquals("High Roller", loaded.name());
        assertEquals(5, loaded.height());
        assertEquals(7, loaded.reels());
        assertEquals(9, loaded.paylines());
        assertEquals(25.0, loaded.wagerPerLine(), 1e-9);
        assertEquals(SlotsSpinSpeed.SLOW, loaded.spinSpeed());
        assertEquals(40L, loaded.autoSettings().spinLimit());
        assertTrue(loaded.autoSettings().stopOnAnyWin());
        assertEquals(12.5, loaded.autoSettings().bigWinMultiplier(), 1e-9);
        assertEquals(900.0, loaded.autoSettings().profitTarget(), 1e-9);
        assertEquals(250.0, loaded.autoSettings().lossLimit(), 1e-9);
    }

    @Test
    void anUnlimitedSpinLimitSurvivesARestartAsUnlimitedRatherThanAsFifteen() {
        store.save(player, new SlotsProfile("endless", 3, 5, 5, 10.0, SlotsSpinSpeed.NORMAL,
            SlotsAutoSpinSettings.defaults().withSpinLimit(SlotsAutoSpinSettings.UNLIMITED_SPINS)), false);
        SlotsProfile loaded = reload().profileAt(player, 0);
        assertEquals(SlotsAutoSpinSettings.UNLIMITED_SPINS, loaded.autoSettings().spinLimit());
        assertFalse(loaded.autoSettings().hasSpinLimit());
    }

    @Test
    void displayOrderIsStableAcrossARestart() {
        for (int i = 0; i < 12; i++) {
            store.save(player, profile("profile " + i), false);
        }
        store.deleteAt(player, 3);
        List<String> before = store.profilesFor(player).stream().map(SlotsProfile::name).toList();
        List<String> after = reload().profilesFor(player).stream().map(SlotsProfile::name).toList();
        assertEquals(before, after);
        assertEquals(11, after.size());
        assertFalse(after.contains("profile 3"));
    }

    @Test
    void profilesAreKeyedByPlayerOnlySoOnePlayersListNeverLeaksIntoAnothers() {
        store.save(player, profile("mine"), false);
        store.save(otherPlayer, profile("theirs"), false);
        assertEquals(List.of("mine"), store.profilesFor(player).stream().map(SlotsProfile::name).toList());
        assertEquals(List.of("theirs"),
            store.profilesFor(otherPlayer).stream().map(SlotsProfile::name).toList());

        SlotsProfileStore reloaded = reload();
        assertEquals(1, reloaded.countFor(player));
        assertEquals(1, reloaded.countFor(otherPlayer));
        // The same name is fine for two different players -- uniqueness is
        // per player, and a profile carries no dealer identity at all.
        assertEquals(SlotsProfileStore.SaveResult.SAVED, store.save(otherPlayer, profile("mine"), false));
    }

    @Test
    void theStoreOwnsExactlyOneFileUnderTheDataFolder() {
        store.save(player, profile("alpha"), false);
        File expected = new File(new File(tempDir.toFile(), "data"), "slots-profiles.yml");
        assertTrue(expected.isFile(), "profiles must persist to data/slots-profiles.yml");
    }

    @Test
    void aNullOwnerOrProfileIsRefusedRatherThanStored() {
        assertEquals(SlotsProfileStore.SaveResult.FAILED, store.save(null, profile("x"), false));
        assertEquals(SlotsProfileStore.SaveResult.FAILED, store.save(player, null, false));
        assertEquals(0, store.countFor(player));
    }

    @Test
    void onlyASucceededSaveReportsSuccess() {
        assertTrue(SlotsProfileStore.SaveResult.SAVED.succeeded());
        assertTrue(SlotsProfileStore.SaveResult.OVERWROTE.succeeded());
        assertFalse(SlotsProfileStore.SaveResult.DUPLICATE.succeeded());
        assertFalse(SlotsProfileStore.SaveResult.FULL.succeeded());
        assertFalse(SlotsProfileStore.SaveResult.FAILED.succeeded());
    }
}
