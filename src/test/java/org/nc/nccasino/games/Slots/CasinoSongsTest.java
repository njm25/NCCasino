package org.nc.nccasino.games.Slots;

import java.util.List;
import java.util.HashSet;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.VSE.ActiveSong;
import org.nc.VSE.Note;
import org.nc.VSE.Song;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CasinoSongsTest {
    // The Slots payout cue family (the transcription of the supplied ringing
    // reference and the three cues derived from it) lives in SlotsPayoutCueTest.

    @Test
    void preservesSyncopatedTwoBarPhraseAndInstrumentRegisters() {
        List<Note> notes = CasinoSongs.dayTripper().getNotes();
        List<Note> firstGuitar = notes.stream()
            .filter(n -> n.getInstr().endsWith(".guitar") && n.getStartTick() < 70).toList();
        assertArrayEquals(new int[] {0, 13, 17, 22, 26, 30, 43, 48, 57, 61, 65},
            firstGuitar.stream().mapToInt(Note::getStartTick).toArray());
        assertEquals(Math.pow(2, -2.0 / 12), firstGuitar.getFirst().getPitch(), 0.00001);
        assertEquals(2.0f, firstGuitar.get(7).getPitch()); // F#4 on guitar, upper limit.
        Note firstBass = notes.stream().filter(n -> n.getInstr().endsWith(".bass")).findFirst().orElseThrow();
        assertEquals(70, firstBass.getStartTick()); // Enters on the second repetition.
        assertEquals(firstGuitar.getFirst().getPitch(), firstBass.getPitch()); // Sounds an octave lower.
        for (Note note : notes) {
            assertTrue(note.getPitch() >= 0.5f && note.getPitch() <= 2.0f);
            assertEquals(note.getStartTick(), note.getEndTick(), "Plucks must decay naturally");
        }
    }

    @Test
    void bundledVsePlaysProgressiveDayTripperIntroWithoutAnAudibleFinalChord() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(player.getLocation()).thenReturn(location);
        Song score = CasinoSongs.dayTripper();
        ActiveSong playback = new ActiveSong("audition", score, false);
        for (int tick = 0; tick < 209; tick++) {
            assertTrue(playback.tick(List.of(player)), "Ended prematurely at tick " + tick);
        }
        assertFalse(playback.tick(List.of(player)));
        assertTrue(playback.isStopped());
        assertEquals(80, score.getNotes().size());
        assertEquals(79, score.getNotes().stream().filter(note -> note.getVolume() > 0.0f).count());
        assertTrue(score.getNotes().stream().anyMatch(note -> note.getStartTick() == 209
            && note.getVolume() == 0.0f));
        assertFalse(score.getNotes().stream().anyMatch(note -> note.getStartTick() == 209
            && note.getVolume() > 0.0f));
        verify(player, times(80)).playSound(eq(location), anyString(), anyFloat(), anyFloat());
        verify(player, never()).stopSound(anyString(), any(SoundCategory.class));
        clearInvocations(player);
        assertFalse(playback.tick(List.of(player)));
        verifyNoInteractions(player); // No looping or late callbacks after completion.
    }

    @Test
    void iFeelFinePreservesSwingContourHarmonyAndPlayableRegisters() {
        List<Note> notes = CasinoSongs.iFeelFine().getNotes();
        List<Note> guitar = notes.stream().filter(n -> n.getInstr().endsWith(".guitar")).toList();
        assertArrayEquals(new int[] {0, 7, 13, 18, 20, 24, 31, 38, 44, 47},
            guitar.subList(0, 10).stream().mapToInt(Note::getStartTick).toArray());

        // Source D7-C7-G7-G7 roots, transposed coherently down to C#7-B7-F#7-F#7.
        assertArrayEquals(new int[] {0, 53, 107, 160},
            new int[] {guitar.get(0).getStartTick(), guitar.get(10).getStartTick(),
                guitar.get(20).getStartTick(), guitar.get(30).getStartTick()});
        assertEquals(Math.pow(2, -5.0 / 12), guitar.getFirst().getPitch(), 0.00001);
        assertEquals(2.0f, guitar.get(5).getPitch()); // F#4, the guitar sample's upper limit.

        Note firstBass = notes.stream().filter(n -> n.getInstr().endsWith(".bass")).findFirst().orElseThrow();
        assertEquals(213, firstBass.getStartTick()); // Rhythm section enters on pass two.
        for (Note note : notes) {
            assertTrue(note.getPitch() >= 0.5f && note.getPitch() <= 2.0f);
            assertEquals(note.getStartTick(), note.getEndTick(), "Attacks must decay naturally");
        }
    }

    @Test
    void bundledVsePreservesIFeelFineLoopBoundaryWithoutAnAudibleFinalChord() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(player.getLocation()).thenReturn(location);
        Song score = CasinoSongs.iFeelFine();
        ActiveSong playback = new ActiveSong("audition", score, false);
        for (int tick = 0; tick < 427; tick++) {
            assertTrue(playback.tick(List.of(player)), "Ended prematurely at tick " + tick);
        }
        assertFalse(playback.tick(List.of(player)));
        assertTrue(playback.isStopped());
        assertEquals(153, score.getNotes().size());
        Note marker = score.getNotes().stream()
            .filter(note -> note.getStartTick() == 427 && note.getVolume() == 0.0f)
            .findFirst().orElseThrow();
        assertEquals(marker.getStartTick(), marker.getEndTick());
        assertEquals(152, score.getNotes().stream().filter(note -> note.getVolume() > 0.0f).count());
        assertEquals(0, score.getNotes().stream()
            .filter(note -> note.getStartTick() == 427 && note.getVolume() > 0.0f).count());
        verify(player, times(153)).playSound(eq(location), anyString(), anyFloat(), anyFloat());
        verify(player, never()).stopSound(anyString(), any(SoundCategory.class));
        clearInvocations(player);
        assertFalse(playback.tick(List.of(player)));
        verifyNoInteractions(player);
    }

    @Test
    void rouletteBackedLoopOmitsTheNakedIntroAndFinalityChord() {
        List<Note> notes = CasinoSongs.dayTripperBackedLoop().getNotes();
        assertEquals(93, notes.size());
        assertTrue(notes.stream().anyMatch(note -> note.getStartTick() == 0
            && note.getInstr().endsWith(".bass") && note.getVolume() > 0.0f));
        assertTrue(notes.stream().anyMatch(note -> note.getStartTick() == 0
            && note.getInstr().endsWith(".basedrum") && note.getVolume() > 0.0f));
        assertTrue(notes.stream().anyMatch(note -> note.getStartTick() == 70
            && note.getInstr().endsWith(".basedrum") && note.getVolume() > 0.0f));
        Note marker = notes.stream().filter(note -> note.getStartTick() == 138).findFirst().orElseThrow();
        assertEquals(0.0f, marker.getVolume());
        assertEquals(138, notes.stream().mapToInt(Note::getEndTick).max().orElseThrow());
        assertFalse(notes.stream().anyMatch(note -> note.getStartTick() == 138
            && note.getVolume() > 0.0f));
        assertEquals(208, CasinoSongs.dayTripperIntroDurationTicks());
    }

    @Test
    void goldenSlumbersKeepsTheVocalIndependentAndMakesTheRefrainEntranceDramatic() {
        List<Note> notes = CasinoSongs.goldenSlumbers().getNotes();
        List<Note> vocal = notes.stream().filter(n -> n.getInstr().endsWith(".flute")).toList();
        assertArrayEquals(new int[] {59, 63, 67, 70, 74},
            vocal.subList(0, 5).stream().mapToInt(Note::getStartTick).toArray());
        assertTrue(vocal.stream().anyMatch(n -> n.getStartTick() == 681
            && Math.abs(n.getPitch() - Math.pow(2, (80 - 78) / 12.0)) < 0.00001));
        assertTrue(vocal.stream().anyMatch(n -> n.getStartTick() == 904
            && Math.abs(n.getPitch() - Math.pow(2, (85 - 78) / 12.0)) < 0.00001));

        int refrainTick = 681; // Beat 46 at 81 BPM.
        assertTrue(notes.stream().filter(n -> n.getStartTick() < refrainTick)
            .noneMatch(n -> n.getInstr().endsWith(".didgeridoo")));
        assertTrue(notes.stream().anyMatch(n -> n.getStartTick() == refrainTick
            && n.getInstr().endsWith(".basedrum")));
        assertTrue(notes.stream().anyMatch(n -> n.getStartTick() == refrainTick
            && n.getInstr().endsWith(".didgeridoo")));
        assertTrue(notes.stream().filter(n -> n.getInstr().endsWith(".flute")
                && n.getStartTick() < refrainTick).mapToDouble(Note::getVolume).max().orElseThrow()
            < notes.stream().filter(n -> n.getInstr().endsWith(".flute")
                && n.getStartTick() >= refrainTick).mapToDouble(Note::getVolume).max().orElseThrow());
    }

    @Test
    void goldenSlumbersPreservesAcceptedSectionAndContinuesWithoutArtificialGap() {
        Song score = CasinoSongs.goldenSlumbers();
        List<Note> notes = score.getNotes();
        List<Note> accepted = notes.stream()
            .filter(n -> n.getStartTick() <= CasinoSongs.GOLDEN_SLUMBERS_ACCEPTED_END_TICK)
            .toList();
        assertEquals(471, accepted.size());
        assertEquals(985, accepted.stream().mapToInt(Note::getStartTick).max().orElseThrow());
        assertEquals(986, notes.stream().mapToInt(Note::getStartTick)
            .filter(tick -> tick > CasinoSongs.GOLDEN_SLUMBERS_ACCEPTED_END_TICK)
            .min().orElseThrow());
        assertEquals(CasinoSongs.GOLDEN_SLUMBERS_ACCEPTED_END_TICK + 1,
            CasinoSongs.GOLDEN_SLUMBERS_EXTENSION_START_TICK);
    }

    @Test
    void goldenSlumbersCompleteScoreIsFinitePlayableAndStopsBeforeCarryThatWeight() {
        Song score = CasinoSongs.goldenSlumbers();
        List<Note> notes = score.getNotes();
        assertEquals(916, notes.size());
        assertEquals(CasinoSongs.GOLDEN_SLUMBERS_LOOP_END_TICK,
            notes.stream().mapToInt(Note::getStartTick).max().orElseThrow());
        assertEquals(CasinoSongs.GOLDEN_SLUMBERS_FINAL_AUDIBLE_TICK,
            notes.stream().filter(n -> n.getVolume() > 0.0f
                    && !n.getInstr().endsWith(".hat"))
                .mapToInt(Note::getStartTick).max().orElseThrow());
        assertArrayEquals(CasinoSongs.GOLDEN_SLUMBERS_LOOP_CLICK_TICKS,
            notes.stream().filter(n -> n.getStartTick()
                    >= CasinoSongs.GOLDEN_SLUMBERS_CARRY_THAT_WEIGHT_BOUNDARY_TICK)
                .filter(n -> n.getVolume() > 0.0f)
                .mapToInt(Note::getStartTick).toArray());
        assertEquals(0, notes.stream().mapToInt(Note::getStartTick).min().orElseThrow());
        for (String instrument : List.of(".flute", ".harp", ".guitar", ".bass", ".chime",
                ".didgeridoo", ".basedrum", ".snare", ".hat")) {
            assertTrue(notes.stream().anyMatch(n -> n.getInstr().endsWith(instrument)), instrument);
        }
        for (Note note : notes) {
            assertTrue(note.getPitch() >= 0.5f && note.getPitch() <= 2.0f,
                note.getInstr() + " pitch " + note.getPitch());
            assertEquals(note.getStartTick(), note.getEndTick(), "Attacks must decay naturally");
        }
    }

    @Test
    void goldenSlumbersExtensionHasNoDuplicateAttacks() {
        HashSet<String> attacks = new HashSet<>();
        for (Note note : CasinoSongs.goldenSlumbers().getNotes()) {
            if (note.getStartTick() <= CasinoSongs.GOLDEN_SLUMBERS_ACCEPTED_END_TICK) continue;
            String attack = note.getInstr() + ":" + note.getStartTick() + ":"
                + Float.floatToIntBits(note.getPitch());
            assertTrue(attacks.add(attack), "duplicate extension attack: " + attack);
        }
    }

    @Test
    void bundledVseCompletesGoldenSlumbersWithoutPlayingTheNextSection() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(player.getLocation()).thenReturn(location);
        Song score = CasinoSongs.goldenSlumbers();
        ActiveSong playback = new ActiveSong("audition", score, false);
        for (int tick = 0; tick < CasinoSongs.GOLDEN_SLUMBERS_LOOP_END_TICK; tick++) {
            assertTrue(playback.tick(List.of(player)), "Ended prematurely at tick " + tick);
        }
        assertFalse(playback.tick(List.of(player)));
        assertTrue(playback.isStopped());
        verify(player, times(score.getNotes().size()))
            .playSound(eq(location), anyString(), anyFloat(), anyFloat());
        verify(player, never()).stopSound(anyString(), any(SoundCategory.class));
        clearInvocations(player);
        assertFalse(playback.tick(List.of(player)));
        verifyNoInteractions(player);
    }

    @Test
    void bundledVseLoopsGoldenSlumbersAfterFourBeatBreak() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 0, 0);
        when(player.getLocation()).thenReturn(location);
        Song score = CasinoSongs.goldenSlumbers();
        ActiveSong playback = new ActiveSong("jukebox", score, true);
        int openingAttacks = (int) score.getNotes().stream()
            .filter(note -> note.getStartTick() == 0).count();

        for (int tick = 0; tick <= CasinoSongs.GOLDEN_SLUMBERS_FINAL_AUDIBLE_TICK; tick++) {
            assertTrue(playback.tick(List.of(player)), "Loop stopped at tick " + tick);
        }
        verify(player, times(911)).playSound(eq(location), anyString(), anyFloat(), anyFloat());

        for (int tick = CasinoSongs.GOLDEN_SLUMBERS_FINAL_AUDIBLE_TICK + 1;
                tick <= CasinoSongs.GOLDEN_SLUMBERS_LOOP_END_TICK; tick++) {
            assertTrue(playback.tick(List.of(player)), "Loop stopped during pause at tick " + tick);
        }
        verify(player, times(916)).playSound(eq(location), anyString(), anyFloat(), anyFloat());

        assertTrue(playback.tick(List.of(player)));
        verify(player, times(916 + openingAttacks))
            .playSound(eq(location), anyString(), anyFloat(), anyFloat());
        assertFalse(playback.isStopped());
    }

}
