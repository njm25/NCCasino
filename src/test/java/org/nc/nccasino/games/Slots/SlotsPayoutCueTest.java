package org.nc.nccasino.games.Slots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

/**
 * The Paytable Sound Lab's payout cue family: the transcription of the
 * supplied ringing reference and the three cues derived from it.
 *
 * <p>The reference loop is twelve 64 ms units (0.768 s). Because a unit is
 * 1.28 server ticks the grid cannot be reproduced exactly; onsets are rounded
 * from absolute unit indices, so these tests pin the resulting tick grid
 * rather than an even one. See {@code CasinoSongs}' payout-family comment for
 * the full derivation.
 */
class SlotsPayoutCueTest {

    private static final String CHIME = "minecraft:block.note_block.chime";
    private static final String BELL = "minecraft:block.note_block.bell";
    private static final String HARP = "minecraft:block.note_block.harp";
    private static final String BASS = "minecraft:block.note_block.bass";

    /** The chime/bell samples sound F#6 at pitch 1.0. */
    private static double chimePitch(int midi) {
        return Math.pow(2.0, (midi - 90) / 12.0);
    }

    // ---- tier and slot mapping -------------------------------------------

    @Test
    void everyReturnLandsInExactlyOneBandWithTheNamedEdgesOpeningTheirTier() {
        long bet = 100L;
        // Nothing back at all.
        assertEquals(SlotsPayoutSoundTier.NO_RETURN, SlotsPayoutSoundTier.forReturn(0L, bet));
        // Back, but under the stake.
        assertEquals(SlotsPayoutSoundTier.PARTIAL_RETURN, SlotsPayoutSoundTier.forReturn(1L, bet));
        assertEquals(SlotsPayoutSoundTier.PARTIAL_RETURN, SlotsPayoutSoundTier.forReturn(99L, bet));
        // Each named edge opens its own tier rather than closing the one below.
        assertEquals(SlotsPayoutSoundTier.SMALL_WIN, SlotsPayoutSoundTier.forReturn(100L, bet));
        assertEquals(SlotsPayoutSoundTier.SMALL_WIN, SlotsPayoutSoundTier.forReturn(299L, bet));
        assertEquals(SlotsPayoutSoundTier.MEDIUM_WIN, SlotsPayoutSoundTier.forReturn(300L, bet));
        assertEquals(SlotsPayoutSoundTier.MEDIUM_WIN, SlotsPayoutSoundTier.forReturn(999L, bet));
        assertEquals(SlotsPayoutSoundTier.BIG_WIN, SlotsPayoutSoundTier.forReturn(1000L, bet));
        assertEquals(SlotsPayoutSoundTier.BIG_WIN, SlotsPayoutSoundTier.forReturn(2499L, bet));
        assertEquals(SlotsPayoutSoundTier.JACKPOT, SlotsPayoutSoundTier.forReturn(2500L, bet));
        assertEquals(SlotsPayoutSoundTier.JACKPOT, SlotsPayoutSoundTier.forReturn(1_000_000L, bet));
    }

    @Test
    void theBandsCoverEveryReturnWithoutOverlapOrGap() {
        long bet = 40L;
        SlotsPayoutSoundTier previous = SlotsPayoutSoundTier.NO_RETURN;
        for (long payout = 0L; payout <= 40L * 30L; payout++) {
            SlotsPayoutSoundTier tier = SlotsPayoutSoundTier.forReturn(payout, bet);
            assertNotNull(tier, "every return must classify");
            // Rising payouts may hold a band or step up, never step back down.
            assertTrue(tier.ordinal() >= previous.ordinal(),
                "payout " + payout + " fell from " + previous + " to " + tier);
            previous = tier;
        }
        assertEquals(SlotsPayoutSoundTier.JACKPOT, previous);
    }

    @Test
    void aNonPositiveStakeCannotDivideByZero() {
        assertEquals(SlotsPayoutSoundTier.NO_RETURN, SlotsPayoutSoundTier.forReturn(0L, 0L));
        assertEquals(SlotsPayoutSoundTier.JACKPOT, SlotsPayoutSoundTier.forReturn(25L, 0L));
        assertEquals(SlotsPayoutSoundTier.JACKPOT, SlotsPayoutSoundTier.forReturn(25L, -5L));
        assertEquals(SlotsPayoutSoundTier.SMALL_WIN, SlotsPayoutSoundTier.forReturn(1L, 1L));
    }

    @Test
    void theUnitGridRoundsFromAbsoluteIndicesSoTheLoopPeriodNeverDrifts() {
        assertEquals(12, CasinoSongs.PAYOUT_CELL_UNITS);
        // 12 units x 1.28 ticks = 15.36; four loops must stay on 15.36, not 15.
        assertEquals(0, CasinoSongs.payoutTick(0));
        assertEquals(15, CasinoSongs.payoutTick(12));
        assertEquals(31, CasinoSongs.payoutTick(24));
        assertEquals(46, CasinoSongs.payoutTick(36));
        assertEquals(61, CasinoSongs.payoutTick(48));
        for (int unit = 1; unit < 64; unit++) {
            assertTrue(CasinoSongs.payoutTick(unit) > CasinoSongs.payoutTick(unit - 1),
                "consecutive units must never collide on one tick at unit " + unit);
        }
    }

    // ---- the transcription ------------------------------------------------

    @Test
    void bigWinIsTheTranscribedTwelveUnitCellFourTimesOverWithALandingDownbeat() {
        Song score = CasinoSongs.slotsPayoutBig(0);
        List<Note> notes = score.getNotes();
        assertEquals(57, notes.size(), "4 cells of 14 attacks plus the landing downbeat");
        for (Note note : notes) {
            assertEquals(CHIME, note.getInstr(), "the transcription is chime alone");
        }

        // The ten transcribed onsets of the first cell, on the rounded grid.
        assertArrayEquals(new int[] {0, 1, 3, 4, 5, 8, 10, 12, 13, 14},
            notes.stream().filter(n -> n.getStartTick() < 15)
                .mapToInt(Note::getStartTick).distinct().sorted().toArray());

        // The complete first cell, onset by onset. Units 5 and 7 are the
        // figure's two rests; units 2, 4, 8, 9 and 11 carry the quieter
        // second strand under the lead (unit 4 is the strand alone).
        assertCellOne(notes, 0, 84);                 // u0  C6, the downbeat
        assertCellOne(notes, 1, 81);                 // u1  A5
        assertCellOne(notes, 3, 84, 79);             // u2  C6 over G5
        assertCellOne(notes, 4, 81);                 // u3  A5
        assertCellOne(notes, 5, 79);                 // u4  G5 alone
        assertCellOne(notes, 8, 88);                 // u6  E6
        assertCellOne(notes, 10, 88, 84);            // u8  E6 over C6
        assertCellOne(notes, 12, 90, 81);            // u9  F#6 over A5
        assertCellOne(notes, 13, 84);                // u10 C6
        assertCellOne(notes, 14, 90, 81);            // u11 F#6 over A5

        // Each repetition is identical and lands exactly 15.36 ticks later.
        for (int cell = 1; cell < 4; cell++) {
            int base = CasinoSongs.payoutTick(cell * 12);
            assertEquals(14, notes.stream()
                .filter(n -> n.getStartTick() >= base && n.getStartTick() < base + 15).count(),
                "cell " + cell + " must repeat all fourteen attacks");
        }

        Note landing = notes.get(notes.size() - 1);
        assertEquals(61, landing.getStartTick());
        assertEquals(chimePitch(84), landing.getPitch(), 1e-6);
        assertEquals(0.90f, landing.getVolume(), 1e-6);
        assertEquals(0.90f, notes.getFirst().getVolume(), 1e-6,
            "the loop's downbeat is its loudest attack");
    }

    @Test
    void theSmallestWinIsDeliberatelyPinnedToTheVolumeCeiling() {
        // RECORDED DECISION, not an accident: 1-3x was set to Minecraft's 1.0
        // ceiling on explicit request for audibility, which knowingly inverts
        // the family's loudness ordering -- it is now louder than the jackpot.
        // Escalation between the tiers is carried entirely by content instead
        // (attacks and length), which the tests below still enforce.
        List<Note> small = CasinoSongs.slotsPayoutSmall(0).getNotes();
        assertEquals(1.0f, peakVolume(small), 1e-6, "1-3x sits at the ceiling");
        for (Note note : small) {
            assertEquals(1.0f, note.getVolume(), 1e-6, "every attack is at the ceiling");
            assertTrue(note.getVolume() <= 1.0f,
                "above 1.0 only widens the audible radius, it is not louder");
        }
        assertTrue(peakVolume(small) > peakVolume(CasinoSongs.slotsPayoutJackpot(0).getNotes()),
            "this inversion is intentional; change it deliberately, not by accident");
    }

    @Test
    void smallReturnIsOnlyTheMotifHead() {
        List<Note> notes = CasinoSongs.slotsPayoutSmall(0).getNotes();
        assertEquals(3, notes.size());
        assertArrayEquals(new int[] {0, 1, 3},
            notes.stream().mapToInt(Note::getStartTick).toArray());
        assertArrayEquals(new double[] {chimePitch(84), chimePitch(81), chimePitch(84)},
            notes.stream().mapToDouble(Note::getPitch).toArray(), 1e-6);
        for (Note note : notes) {
            assertEquals(CHIME, note.getInstr());
            assertTrue(note.getVolume() <= 1.0f, "1.0 is the real ceiling");
        }
    }

    @Test
    void mediumWinStatesTheWholeCellOnceAndCloses() {
        List<Note> notes = CasinoSongs.slotsPayoutMedium(0).getNotes();
        assertEquals(15, notes.size(), "one complete cell plus its closing downbeat");
        assertArrayEquals(new int[] {0, 1, 3, 4, 5, 8, 10, 12, 13, 14, 15},
            notes.stream().mapToInt(Note::getStartTick).distinct().sorted().toArray());
        Note close = notes.get(notes.size() - 1);
        assertEquals(15, close.getStartTick());
        assertEquals(chimePitch(84), close.getPitch(), 1e-6);
        // Same figure as the big win, one repetition, 80% of its level.
        List<Note> big = CasinoSongs.slotsPayoutBig(0).getNotes();
        assertEquals(big.getFirst().getPitch(), notes.getFirst().getPitch(), 1e-6);
        assertEquals(0.92f * big.getFirst().getVolume(), notes.getFirst().getVolume(), 1e-6);
    }

    @Test
    void jackpotGrowsTheSameLoopByVoiceCountAndRegisterRatherThanGain() {
        Song score = CasinoSongs.slotsPayoutJackpot(0);
        List<Note> notes = score.getNotes();
        assertEquals(113, notes.size());

        Set<String> instruments = new LinkedHashSet<>(notes.stream().map(Note::getInstr).toList());
        assertEquals(Set.of(CHIME, BASS, HARP, BELL), instruments);

        // The chime figure itself is unchanged: five identical cells.
        List<Note> chime = notes.stream().filter(n -> CHIME.equals(n.getInstr())).toList();
        assertEquals(74, chime.size(), "5 cells of 14 attacks plus the 4-note rise");
        assertArrayEquals(new int[] {0, 1, 3, 4, 5, 8, 10, 12, 13, 14},
            chime.stream().filter(n -> n.getStartTick() < 15)
                .mapToInt(Note::getStartTick).distinct().sorted().toArray());

        // Staggered entrances: bass from cell 0, harp from cell 1, bell from cell 3.
        assertEquals(0, firstTick(notes, BASS));
        assertEquals(CasinoSongs.payoutTick(12), firstTick(notes, HARP));
        assertEquals(CasinoSongs.payoutTick(37), firstTick(notes, BELL));

        // A louder pile is not how this escalates -- the peak level matches the
        // big win; the extra size comes from the added voices.
        assertEquals(peakVolume(CasinoSongs.slotsPayoutBig(0).getNotes()), peakVolume(notes), 1e-6);
        assertTrue(notes.stream().filter(n -> !CHIME.equals(n.getInstr()))
            .allMatch(n -> n.getVolume() <= 0.44f), "added layers must stay under the lead");

        // The rise leaves the loop on its own scale and lands on tonic A6.
        int rise = CasinoSongs.payoutTick(60);
        assertArrayEquals(new double[] {
            chimePitch(84), chimePitch(88), chimePitch(90), chimePitch(93)},
            chime.stream().filter(n -> n.getStartTick() >= rise)
                .mapToDouble(Note::getPitch).toArray(), 1e-6);
        assertEquals(81, notes.stream().mapToInt(Note::getStartTick).max().orElseThrow());
    }

    // ---- shared family invariants ----------------------------------------

    @Test
    void everyCueIsOneShotNotesInsideItsSamplePlayableRange() {
        for (Song score : profitableCues()) {
            for (Note note : score.getNotes()) {
                assertEquals(note.getStartTick(), note.getEndTick(),
                    score.getTitle() + ": bells must decay naturally, never be stopped");
                assertTrue(note.getPitch() >= 0.5f && note.getPitch() <= 2.0f,
                    score.getTitle() + ": pitch " + note.getPitch() + " leaves the 0.5..2.0 range");
                assertTrue(note.getVolume() > 0.0f,
                    score.getTitle() + ": a payout cue has no silent markers");
                // Each sample's own reference register, per the composition guide.
                int reference = switch (note.getInstr()) {
                    case CHIME, BELL -> 90;
                    case HARP -> 66;
                    case BASS -> 42;
                    default -> fail(score.getTitle() + ": unexpected sound " + note.getInstr());
                };
                double midi = reference + 12.0 * (Math.log(note.getPitch()) / Math.log(2.0));
                assertEquals(Math.round(midi), midi, 1e-4,
                    score.getTitle() + ": every pitch is an exact equal-tempered semitone");
            }
        }
    }

    @Test
    void theFourProfitableCuesEscalateAsOneFamily() {
        List<Song> cues = profitableCues();
        for (int i = 1; i < cues.size(); i++) {
            List<Note> lower = cues.get(i - 1).getNotes();
            List<Note> higher = cues.get(i).getNotes();
            assertTrue(higher.size() > lower.size(),
                cues.get(i).getTitle() + " must carry more attacks than " + cues.get(i - 1).getTitle());
            assertTrue(endTick(higher) > endTick(lower),
                cues.get(i).getTitle() + " must last longer than " + cues.get(i - 1).getTitle());
            // Loudness is deliberately NOT part of the escalation any more --
            // see theSmallestWinIsDeliberatelyPinnedToTheVolumeCeiling. The
            // ladder is carried by attacks and length, asserted above.
            assertTrue(peakVolume(higher) <= 1.0f,
                cues.get(i).getTitle() + " must stay inside the volume ceiling");
        }
        // All four open with the same motif head, so the family is audible
        // from the smallest return upward.
        for (Song cue : cues) {
            List<Note> head = cue.getNotes().stream().filter(n -> n.getStartTick() <= 3)
                .filter(n -> CHIME.equals(n.getInstr())).toList();
            assertArrayEquals(new int[] {0, 1, 3},
                head.stream().mapToInt(Note::getStartTick).distinct().sorted().toArray(),
                cue.getTitle() + " must open on the transcribed C6 A5 C6 head");
        }
        // No coin/metal-wash sounds anywhere in the family.
        for (Song cue : cues) {
            assertTrue(cue.getNotes().stream().map(Note::getInstr)
                .allMatch(i -> i.startsWith("minecraft:block.note_block.")),
                cue.getTitle() + " must stay inside the note-block family");
        }
    }

    @Test
    void bundledVsePlaysEachCueToCompletionExactlyOnce() {
        for (Song score : profitableCues()) {
            Player player = mock(Player.class);
            Location location = new Location(null, 0, 0, 0);
            when(player.getLocation()).thenReturn(location);
            int last = endTick(score.getNotes());
            ActiveSong playback = new ActiveSong("audition", score, false);
            for (int tick = 0; tick < last; tick++) {
                assertTrue(playback.tick(List.of(player)),
                    score.getTitle() + " ended prematurely at tick " + tick);
            }
            assertFalse(playback.tick(List.of(player)), score.getTitle() + " must not loop");
            assertTrue(playback.isStopped());
            verify(player, times(score.getNotes().size()))
                .playSound(eq(location), anyString(), anyFloat(), anyFloat());
            verify(player, never()).stopSound(anyString(), any(SoundCategory.class));
            clearInvocations(player);
            assertFalse(playback.tick(List.of(player)));
            verifyNoInteractions(player); // No late callbacks after completion.
        }
    }

    // ---- the Sound Lab is gone --------------------------------------------

    @Test
    void theSoundLabIsFullyRemovedFromEveryLayer() throws IOException {
        // The six audition tiles became the real result bands, so no trace of
        // the Lab may survive in code, layout or player-visible text.
        for (String source : List.of("games/Slots/SlotsMachine.java",
                "games/Slots/SlotsPaytableLayout.java",
                "games/Slots/SlotsPayoutSoundTier.java",
                "games/Slots/CasinoSongs.java")) {
            String body = readSource(source);
            for (String gone : List.of("soundLabSlots", "soundLabIndexAtSlot",
                    "activeSoundDemoTier", "returnToSoundLabAfterDemo",
                    "renderSoundLabButtons", "handleSoundLabClick",
                    "playSoundLabFinale", "atPaytableSlot", "SOUND_LAB_SLOTS")) {
                assertFalse(body.contains(gone), source + " still references " + gone);
            }
        }
        Path english = Paths.get("src/main/resources/lang/en_US.yml");
        assertTrue(Files.isRegularFile(english));
        assertFalse(Files.readString(english).contains("sound-lab"),
            "the Sound Lab's player-visible text must be gone");

        // The Paytable's content is inert -- its housing is the shared Golden
        // Slumbers toggle, which is SlotsHousingToggleTest's subject.
        String machine = readSource("games/Slots/SlotsMachine.java");
        assertTrue(machine.contains("case GAME, PAYTABLE -> { }"),
            "neither the reel grid nor the Paytable cards may be controls");
    }

    @Test
    void oneTierDrivenCueServesEveryFinishedSpin() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");

        // The two old routers are gone; one remains.
        assertFalse(machine.contains("private void playFinale("),
            "the old ratio-branching paid finale must be gone");
        assertFalse(machine.contains("private void playDemoFinale("),
            "the separate demo finale must be gone");
        assertEquals(1, countOccurrences(machine, "private void playResultCue(long payout)"));

        // It classifies purely on what came back against what was staked.
        int cue = machine.indexOf("private void playResultCue(long payout)");
        String body = machine.substring(cue, machine.indexOf("\n    }", cue));
        assertTrue(body.contains("SlotsPayoutSoundTier.forReturn(payout, currentTotalBet())"),
            "the band must come from the return against the stake");
        for (String tier : List.of("NO_RETURN", "PARTIAL_RETURN", "SMALL_WIN",
                "MEDIUM_WIN", "BIG_WIN", "JACKPOT")) {
            assertTrue(body.contains("case " + tier + " ->"), "unhandled band " + tier);
        }
        // Both non-winning bands keep the held beat; all four wins are VSE.
        assertTrue(body.contains("case NO_RETURN -> playDelayedLoss();"));
        assertTrue(body.contains("case PARTIAL_RETURN -> playDelayedPartialReturn();"));
        assertEquals(4, countOccurrences(body, "playPayoutSong(CasinoSongs.slotsPayout"));
        assertEquals(4, countOccurrences(body, "activeSpinTranspose"),
            "every win cue must answer in the spin's own key");

        // Paid and demo spins both end on it -- two paid routes (the reveal
        // and fast-forward) and two demo routes.
        assertEquals(2, countOccurrences(machine,
            "playResultCue(controller.pendingPayoutAmount());"), "paid spin routes");
        assertEquals(1, countOccurrences(machine, "playResultCue(hypotheticalPayout);"));
        assertEquals(1, countOccurrences(machine, "playResultCue(demoHypotheticalPayout);"));

        // No cue factory can reach spin or settlement code.
        String songs = readSource("games/Slots/CasinoSongs.java");
        for (String forbidden : List.of("SlotsSpinController", "WagerTransaction",
                "PendingPayout", "settle", "payout(")) {
            assertFalse(songs.contains(forbidden),
                "CasinoSongs must stay a pure score factory; found " + forbidden);
        }
    }


    // ---- following the reels' key -----------------------------------------

    /** Every transpose any reel count can roll. 3 reels is the widest: -1..+7. */
    private static final int LOWEST_ROLL = -1;
    private static final int HIGHEST_ROLL = 7;

    @Test
    void everyCueSurvivesEveryTransposeTheReelLadderCanRoll() {
        for (int transpose = LOWEST_ROLL; transpose <= HIGHEST_ROLL; transpose++) {
            List<Song> cues = List.of(
                CasinoSongs.slotsPayoutSmall(transpose),
                CasinoSongs.slotsPayoutMedium(transpose),
                CasinoSongs.slotsPayoutBig(transpose),
                CasinoSongs.slotsPayoutJackpot(transpose));
            for (Song cue : cues) {
                for (Note note : cue.getNotes()) {
                    assertTrue(note.getPitch() >= 0.5f && note.getPitch() <= 2.0f,
                        cue.getTitle() + " at transpose " + transpose + " puts "
                            + note.getInstr() + " at pitch " + note.getPitch());
                }
            }
        }
    }

    @Test
    void theLadderNeverRollsATransposeTheCuesCannotFollow() {
        // Sampled rather than asserted against copied bounds, so the guard
        // tracks randomReelStopScale itself if its ranges are ever changed.
        for (int columns : new int[] {3, 5, 7}) {
            for (int trial = 0; trial < 3000; trial++) {
                double[] scale = SlotsMachine.randomReelStopScale(columns);
                assertNotNull(scale, columns + " reels must have a ladder");
                int transpose = (int) Math.round(scale[0]);
                assertTrue(transpose >= LOWEST_ROLL && transpose <= HIGHEST_ROLL,
                    columns + " reels rolled " + transpose + ", outside the tested range");
                for (double step : scale) {
                    double pitch = SlotsMachine.REEL_STOP_SCALE_BASE_PITCH
                        * Math.pow(2.0, step / 12.0);
                    assertTrue(pitch >= 0.5 && pitch <= 2.0,
                        columns + " reels put a stop at pitch " + pitch);
                }
            }
        }
    }

    @Test
    void theLadderRootSitsOnAnExactSemitoneSoItCannotClashWithTheCues() {
        // The cues are written in exact semitones; a root even a third of a
        // semitone off would hand over out of tune. This is G#1 on bass.
        double midi = 42 + 12.0
            * (Math.log(SlotsMachine.REEL_STOP_SCALE_BASE_PITCH) / Math.log(2.0));
        assertEquals(32.0, midi, 0.001, "the ladder root must be an exact semitone");
        assertEquals(Math.round(midi), midi, 0.001);
    }

    @Test
    void aTransposedCueKeepsEveryIntervalOfTheTranscription() {
        List<Note> home = CasinoSongs.slotsPayoutBig(0).getNotes();
        for (int transpose : new int[] {LOWEST_ROLL, 3, HIGHEST_ROLL}) {
            List<Note> moved = CasinoSongs.slotsPayoutBig(transpose).getNotes();
            assertEquals(home.size(), moved.size(),
                "transposing must not add or drop an attack");
            double factor = Math.pow(2.0, transpose / 12.0);
            for (int i = 0; i < home.size(); i++) {
                assertEquals(home.get(i).getStartTick(), moved.get(i).getStartTick(),
                    "transposing must not move an onset");
                assertEquals(home.get(i).getVolume(), moved.get(i).getVolume(), 1e-6,
                    "transposing must not change a level");
                assertEquals(home.get(i).getPitch() * factor, moved.get(i).getPitch(), 1e-5,
                    "every note must shift by the same factor at transpose " + transpose);
            }
        }
    }

    @Test
    void theSpinsTransposeIsRolledWithItsLadderAndReachesEveryCue() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        // The transpose is rolled with the ladder, so the two can never split.
        assertTrue(machine.contains("private void beginSpinScale(int columns) {"),
            "one place must roll the ladder and its transpose together");
        assertEquals(1, countOccurrences(machine,
            "activeSpinReelScale = randomReelStopScale(columns);"),
            "only beginSpinScale may assign the ladder, so no path can set it "
                + "without also setting the transpose");
        assertEquals(2, countOccurrences(machine, "beginSpinScale(columns);"),
            "both the paid and demo spin paths must roll it");
    }


    @Test
    void aNewSpinSilencesWhateverTheLastResultLeftSounding() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        // Both spin starts clear the previous result rather than only
        // cancelling its pending task.
        assertEquals(2, countOccurrences(machine, "silenceResultCue();"),
            "both the paid and demo spin starts must silence the last result");
        assertEquals(0, countOccurrences(machine,
            "cancelResultCueTask();" + System.lineSeparator() + "        beginSpinScale"),
            "a spin start must not merely cancel the pending task");

        int at = machine.indexOf("private void silenceResultCue()");
        assertTrue(at > 0, "the silencer must exist");
        String body = machine.substring(at, machine.indexOf("private void cancelResultCueTask()"));
        assertTrue(body.contains("cancelResultCueTask();"),
            "a held verdict must be dropped");
        assertTrue(body.contains("mce.stopSong(PAYOUT_CHANNEL, PAYOUT_SONG_ID);"),
            "a still-ringing win phrase must be stopped, not left to the next result");
        assertTrue(body.contains("resultCueSounded = false;"),
            "the new spin is allowed to sound its own result");
    }

    @Test
    void fastForwardingInsideTheFinaleHoldNeverReplaysTheCue() throws IOException {
        String machine = readSource("games/Slots/SlotsMachine.java");
        int at = machine.indexOf("private void playResultCue(long payout)");
        assertTrue(at > 0);
        String body = machine.substring(at, machine.indexOf("switch (SlotsPayoutSoundTier", at));
        assertTrue(body.contains("if (resultCueSounded)"),
            "the cue must be guarded against a second play");
        assertTrue(body.contains("return;"), "the guard must short-circuit");
        assertTrue(machine.contains("resultCueSounded = true;"),
            "playing the cue must mark this presentation as sounded");
        // Only a new spin may clear it, so the guard spans the whole
        // presentation including the fast-forward window.
        assertEquals(1, countOccurrences(machine, "resultCueSounded = false;"),
            "only a spin start may re-arm the cue");
    }

    // ---- helpers ----------------------------------------------------------

    private static List<Song> profitableCues() {
        return List.of(CasinoSongs.slotsPayoutSmall(0), CasinoSongs.slotsPayoutMedium(0),
            CasinoSongs.slotsPayoutBig(0), CasinoSongs.slotsPayoutJackpot(0));
    }

    /**
     * Asserts the exact content of one onset of the first cell: the given
     * MIDI notes, loudest first, and nothing else on that tick.
     */
    private static void assertCellOne(List<Note> notes, int tick, int... midiLoudestFirst) {
        List<Note> at = notes.stream().filter(n -> n.getStartTick() == tick)
            .sorted(java.util.Comparator.comparing(Note::getVolume).reversed()).toList();
        assertEquals(midiLoudestFirst.length, at.size(), "attacks on tick " + tick);
        for (int i = 0; i < midiLoudestFirst.length; i++) {
            assertEquals(chimePitch(midiLoudestFirst[i]), at.get(i).getPitch(), 1e-6,
                "voice " + i + " on tick " + tick);
        }
    }

    private static int firstTick(List<Note> notes, String instrument) {
        return notes.stream().filter(n -> instrument.equals(n.getInstr()))
            .mapToInt(Note::getStartTick).min().orElseThrow();
    }

    private static int endTick(List<Note> notes) {
        return notes.stream().mapToInt(Note::getEndTick).max().orElseThrow();
    }

    private static float peakVolume(List<Note> notes) {
        return (float) notes.stream().mapToDouble(Note::getVolume).max().orElseThrow();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static String readSource(String relativePath) throws IOException {
        Path path = Paths.get("src/main/java/org/nc/nccasino").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "expected source file at " + path.toAbsolutePath());
        return Files.readString(path);
    }
}
