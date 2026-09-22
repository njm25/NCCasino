package org.nc.nccasino.games.Slots;

import org.nc.VSE.Note;
import org.nc.VSE.Song;

/** Accepted note-block arrangements used by NCCasino games. */
public final class CasinoSongs {
    private static final int DAY_TRIPPER_BPM = 138;
    private static final int I_FEEL_FINE_BPM = 180;
    private static final int GOLDEN_SLUMBERS_BPM = 81;
    static final int GOLDEN_SLUMBERS_ACCEPTED_END_TICK = 985;
    static final int GOLDEN_SLUMBERS_EXTENSION_START_TICK = 986;
    static final int GOLDEN_SLUMBERS_CARRY_THAT_WEIGHT_BOUNDARY_TICK = 1860;
    static final int GOLDEN_SLUMBERS_FINAL_AUDIBLE_TICK = 1853;
    static final int[] GOLDEN_SLUMBERS_LOOP_CLICK_TICKS = {1860, 1875, 1890, 1905};
    static final int GOLDEN_SLUMBERS_LOOP_END_TICK = 1918;
    private static final double GOLDEN_SLUMBERS_EXTENSION_SOURCE_BEAT = 71.0;
    private static final int GOLDEN_SLUMBERS_TRANSPOSE = 4; // C major -> E major for full flute range.
    private static final String GUITAR = "minecraft:block.note_block.guitar";
    private static final String BASS = "minecraft:block.note_block.bass";
    private static final String HARP = "minecraft:block.note_block.harp";
    private static final String FLUTE = "minecraft:block.note_block.flute";
    private static final String CHIME = "minecraft:block.note_block.chime";
    private static final String DIDGERIDOO = "minecraft:block.note_block.didgeridoo";
    private CasinoSongs() {
    }

    // ---- Slots payout cue family -----------------------------------------
    //
    // All four profitable Slots payout cues are built from ONE transcribed
    // motif so they escalate as a family instead of as unrelated jingles.
    //
    // SOURCE. The user-supplied reference recording ("Video Project 2.m4a",
    // 17.3 s, audible 6.40-16.78 s) is a slot-machine payout wash: a broadband
    // metallic coin jingle with a high pitched loop ringing through it. Only
    // the pitched loop is transcribed here; the coin wash is deliberately
    // omitted because vanilla has no comparable sample and the brief asked for
    // the identifiable figure.
    //
    // DERIVATION. The loop was recovered by spectral analysis rather than by
    // ear: a 48 kHz mono decode, an STFT (4096-point Hann window, 1 ms hop,
    // 11.7 Hz bins) and per-semitone band envelopes. Autocorrelation of the
    // summed high-band energy over 7.0-16.8 s fixes the loop period at
    // 0.768 s, confirmed independently by the twelve successive cell downbeats
    // measured at 7.194 s through 16.406 s. Folding all twelve cycles onto
    // that period and reading each semitone band's peak yields ten onsets
    // whose spacing is a constant 64 ms -- exactly 0.768 / 12, so the cell is
    // twelve equal units long.
    //
    // PITCH. Narrowband peaks sit at 799.6, 892, 1067, 1348 and 1497.5 Hz.
    // Those are 30 cents sharp of A440 but land within 5 cents of equal
    // temperament against A4 = 448 Hz, so the source device simply runs about
    // a third of a semitone sharp. The sounding set is G5 A5 C6 (D6) E6 F#6 --
    // an A-Dorian collection. The arrangement is written at concert pitch: a
    // uniform +30 cent offset is a tuning artefact of the source device, not
    // part of the figure's identity, and the guide's rule is not to add
    // fractional-MIDI pitches without a reason the sample supports.
    //
    // CONFIDENCE. Every one of the ten transcribed onsets is present in all
    // twelve cycles individually, not merely in the average, and each sits
    // clearly above the energy measured at the cell's two rest units. Least
    // certain: a faint D6 band (about a fifth of the lead's level) that tracks
    // units 1 and 11 in some cycles. It is omitted -- it may be a partial of
    // the coin wash, and adding it thickened the figure without helping it.
    //
    // 20 TPS LIMIT. A 64 ms unit is 1.28 server ticks, so the grid cannot be
    // reproduced exactly. Onsets are rounded from ABSOLUTE unit indices, which
    // keeps the 0.768 s loop period and the overall tempo correct at the cost
    // of up to 24 ms of per-onset jitter (adjacent units land 1 or 2 ticks
    // apart, averaging 1.28). This is the single largest fidelity compromise
    // and is audible as a slightly uneven trill; preserving the true period
    // was judged more important than an even but wrong-tempo grid.

    // ---- following the reels' key ----------------------------------------
    //
    // Every payout cue is shifted by the same whole number of semitones the
    // spin's reel-stop ladder was randomly transposed by, so a win answers in
    // the key the reels just spent the whole spin establishing rather than
    // always in a fixed one. The rule is deliberately the crudest possible
    // one -- the ladder's transpose integer applied verbatim -- because that
    // needs no key analysis and keeps every interval of the transcription
    // exactly as measured.
    //
    // Headroom. Every sample can only play 0.5..2.0, and the reel ladder can
    // roll -1..+7 (3 reels), -1..+4 (5) or -1..+2 (7). The chime motif spans
    // G5..F#6 inside chime's F#5..F#7, which leaves -1..+12 -- the whole range
    // fits with the -1 roll landing exactly on chime's floor. The jackpot's
    // extra voices did NOT fit and were revoiced for this: its harp and bass
    // each dropped an octave, and its bell stopped doubling F#6 (which reached
    // F#7, the sample's literal ceiling, capping the entire cue at +0). After
    // revoicing the jackpot clears -1..+9. SlotsPayoutCueTest walks every reel
    // count against every roll it can make and asserts the bounds hold.

    /** The transcribed loop's unit grid: 12 units of 64 ms = 0.768 s. */
    static final int PAYOUT_CELL_UNITS = 12;
    private static final double PAYOUT_UNIT_TICKS = 0.064 * 20.0; // 1.28

    /**
     * The chime and bell samples sound F#6 at pitch 1.0 (their note-block
     * range is F#5..F#7), so those pitches are expressed against MIDI 90.
     */
    private static final int CHIME_REFERENCE_MIDI = 90;
    private static final int BELL_REFERENCE_MIDI = 90;
    private static final int HARP_REFERENCE_MIDI = 66;
    private static final int BASS_REFERENCE_MIDI = 42;
    private static final String BELL = "minecraft:block.note_block.bell";

    // The folded cell. Columns: unit, MIDI, volume in hundredths. The lead is
    // the figure's upper ringing line; the under-voice is the quieter second
    // strand sounding with it at units 2, 4, 8, 9 and 11. Volumes are the
    // folded STFT peak amplitudes normalised to 0.90 at the loop's downbeat
    // and softened with a 0.8 exponent, preserving the measured ~10 dB spread.
    private static final int[][] PAYOUT_CELL_LEAD = {
        {0, 84, 90}, {1, 81, 78}, {2, 84, 79}, {3, 81, 70},
        {6, 88, 39}, {8, 88, 39}, {9, 90, 57}, {10, 84, 35}, {11, 90, 45},
    };
    private static final int[][] PAYOUT_CELL_UNDER = {
        {2, 79, 42}, {4, 79, 39}, {8, 84, 35}, {9, 81, 35}, {11, 81, 34},
    };

    /**
     * 1-3x: the smallest profitable return. Only the motif's first three
     * attacks (C6 A5 C6) at a little over half the big win's level, so the
     * family is recognisable from the first cue without ever sounding
     * celebratory. No under-voice, no repetition, no ending gesture.
     */
    public static Song slotsPayoutSmall(int transpose) {
        Song song = new Song("SlotsPayoutSmall", 120);
        // Pinned to Minecraft's 1.0 ceiling on every attack, by explicit
        // request, because three short chime hits read as far quieter than
        // their numbers suggest and the measured balance left the smallest win
        // inaudible in play. Two deliberate consequences: the C6 downbeat no
        // longer stands out from the two notes after it (the reference's
        // accent shape is gone), and this is now the loudest cue in the
        // family -- louder than the jackpot. Volumes above 1.0 would NOT be
        // louder still; in Minecraft they only widen the audible radius, and
        // these play at the player's own location.
        for (int[] note : new int[][] {{0, 84, 100}, {1, 81, 100}, {2, 84, 100}}) {
            payoutChime(song, note[0], note[1] + transpose, note[2] / 100.0f);
        }
        return song;
    }

    /**
     * 3-10x: one complete statement of the cell -- both strands, no repeat --
     * at 80% of the big win's level, closed by the loop's own downbeat C6 at
     * unit 12. Clearly bigger than the small return because it is the whole
     * figure rather than its head, and clearly smaller than the big win
     * because it states the figure once instead of ringing.
     */
    public static Song slotsPayoutMedium(int transpose) {
        Song song = new Song("SlotsPayoutMedium", 120);
        // Lifted from 0.80 to 0.92 only to stay above the raised 1-3x peak --
        // a medium win must never read as quieter than a small one. The two
        // are now within a third of a decibel, so the step between them is
        // carried by content (fifteen attacks against three) rather than gain,
        // which is how the rest of this family escalates anyway.
        addPayoutCell(song, 0, 0.92f, transpose);
        payoutChime(song, PAYOUT_CELL_UNITS, 84 + transpose, 0.71f);
        return song;
    }

    /**
     * 10-25x: the faithful transcription. Four complete cells of the reference
     * loop at full level plus the fifth cell's downbeat as a landing, which is
     * the note the loop itself plays there. 3.05 s -- long enough that the
     * repetition reads as the reference's ringing, short enough for a spin
     * finale. The reference simply keeps looping; stopping on a downbeat
     * rather than mid-cell is the one arrangement decision here.
     */
    public static Song slotsPayoutBig(int transpose) {
        Song song = new Song("SlotsPayoutBig", 120);
        for (int cell = 0; cell < 4; cell++) {
            addPayoutCell(song, cell * PAYOUT_CELL_UNITS, 1.0f, transpose);
        }
        payoutChime(song, 4 * PAYOUT_CELL_UNITS, 84 + transpose, 0.90f);
        return song;
    }

    /**
     * 25x+: the same loop made bigger by voice count and register width
     * rather than by gain -- the guide's rule for a drop. Five cells of the
     * identical chime figure; a bass foundation on every cell (A2 under the
     * first half, D3 under the second, the A-Dorian implication of the
     * figure's own pitch set); a spread harp dyad entering at cell 1; a bell
     * doubling the motif an octave up on the strong units of the last two
     * cells; then a four-note rise out of the cell's own scale onto a tonic
     * A6 with the whole ensemble. Nothing here uses a sound the family does
     * not already own, and no layer is a unison reinforcement of another.
     */
    public static Song slotsPayoutJackpot(int transpose) {
        Song song = new Song("SlotsPayoutJackpot", 120);
        for (int cell = 0; cell < 5; cell++) {
            int base = cell * PAYOUT_CELL_UNITS;
            addPayoutCell(song, base, 1.0f, transpose);
            // Bass from the first cell: the figure's implied Am -> D, voiced
            // in the sample's bottom octave so the whole cue still transposes.
            payoutNote(song, BASS, BASS_REFERENCE_MIDI, base, 33 + transpose, 0.40f);
            payoutNote(song, BASS, BASS_REFERENCE_MIDI, base + 6, 38 + transpose, 0.40f);
            if (cell >= 1) {
                // Harp is the second layer in, voiced as spread dyads rather
                // than doubling the lead, so it adds body without masking it.
                payoutNote(song, HARP, HARP_REFERENCE_MIDI, base, 57 + transpose, 0.26f);
                payoutNote(song, HARP, HARP_REFERENCE_MIDI, base, 64 + transpose, 0.26f);
                payoutNote(song, HARP, HARP_REFERENCE_MIDI, base + 6, 62 + transpose, 0.26f);
                payoutNote(song, HARP, HARP_REFERENCE_MIDI, base + 6, 66 + transpose, 0.26f);
            }
            if (cell >= 3) {
                // Bell is the third layer: the motif's A an octave up, pinging
                // on the trill's two A5 attacks and the two answering ones.
                // It deliberately no longer doubles F#6 -- that reached F#7,
                // the sample's absolute ceiling, which left the whole jackpot
                // unable to transpose by even one semitone.
                payoutNote(song, BELL, BELL_REFERENCE_MIDI, base + 1, 93 + transpose, 0.24f);
                payoutNote(song, BELL, BELL_REFERENCE_MIDI, base + 3, 93 + transpose, 0.24f);
                payoutNote(song, BELL, BELL_REFERENCE_MIDI, base + 9, 93 + transpose, 0.24f);
                payoutNote(song, BELL, BELL_REFERENCE_MIDI, base + 11, 93 + transpose, 0.24f);
            }
        }
        int rise = 5 * PAYOUT_CELL_UNITS;
        payoutChime(song, rise, 84 + transpose, 0.70f);
        payoutChime(song, rise + 1, 88 + transpose, 0.76f);
        payoutChime(song, rise + 2, 90 + transpose, 0.82f);
        payoutChime(song, rise + 3, 93 + transpose, 0.90f);
        payoutNote(song, BELL, BELL_REFERENCE_MIDI, rise + 3, 93 + transpose, 0.35f);
        payoutNote(song, HARP, HARP_REFERENCE_MIDI, rise + 3, 57 + transpose, 0.30f);
        payoutNote(song, HARP, HARP_REFERENCE_MIDI, rise + 3, 60 + transpose, 0.30f);
        payoutNote(song, HARP, HARP_REFERENCE_MIDI, rise + 3, 64 + transpose, 0.30f);
        payoutNote(song, BASS, BASS_REFERENCE_MIDI, rise + 3, 33 + transpose, 0.44f);
        return song;
    }

    /** How far the losing cue sits below the motif: four octaves. */
    private static final int PAYOUT_LOSS_DROP = 48;

    /** The motif's opening attack -- the note every payout cue begins on. */
    static int payoutMotifStartMidi() {
        return PAYOUT_CELL_LEAD[0][1];
    }

    /**
     * The losing cue resolves to the payout motif's own starting note instead
     * of an arbitrary low thud, so a loss reads as the bottom of the same
     * tonal world the wins live in rather than an unrelated sound.
     *
     * <p>It is that note dropped four octaves onto the bass sample -- the
     * lowest octave of it the sample can still reach, since another octave
     * down would fall below bass's F#1 floor. With the motif starting on C6
     * this is C2, and because C6 sits six semitones below chime's F#6
     * reference exactly as C2 sits six below bass's F#2, the two share the
     * identical 0.70711 multiplier: the same note, four octaves apart.
     *
     * <p>Derived rather than hard-coded, so retuning the motif's first note
     * automatically retunes the loss with it.
     */
    static float payoutLossBassPitch() {
        int midi = payoutMotifStartMidi() - PAYOUT_LOSS_DROP;
        float pitch = (float) Math.pow(2.0, (midi - BASS_REFERENCE_MIDI) / 12.0);
        if (pitch < 0.5f || pitch > 2.0f) {
            throw new IllegalStateException(
                "losing cue note outside the vanilla bass range: " + midi);
        }
        return pitch;
    }

    /** One complete cell of the transcribed loop, both strands, scaled and transposed. */
    private static void addPayoutCell(Song song, int baseUnit, float scale, int transpose) {
        for (int[] note : PAYOUT_CELL_LEAD) {
            payoutChime(song, baseUnit + note[0], note[1] + transpose, scale * note[2] / 100.0f);
        }
        for (int[] note : PAYOUT_CELL_UNDER) {
            payoutChime(song, baseUnit + note[0], note[1] + transpose, scale * note[2] / 100.0f);
        }
    }

    private static void payoutChime(Song song, int unit, int midi, float volume) {
        payoutNote(song, CHIME, CHIME_REFERENCE_MIDI, unit, midi, volume);
    }

    /**
     * One one-shot attack on the payout grid. The unit index is absolute, so
     * rounding never accumulates across the loop's repetitions.
     */
    private static void payoutNote(Song song, String sound, int referenceMidi,
                                   int unit, int midi, float volume) {
        float pitch = (float) Math.pow(2.0, (midi - referenceMidi) / 12.0);
        if (pitch < 0.5f || pitch > 2.0f) {
            throw new IllegalArgumentException("Note outside vanilla instrument range: " + midi);
        }
        int onset = payoutTick(unit);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    /** Absolute 64 ms unit index to server tick. */
    static int payoutTick(int unit) {
        return (int) Math.round(unit * PAYOUT_UNIT_TICKS);
    }

    /**
     * Day Tripper's two-bar ostinato, three times as a progressive Roulette intro.
     * Pitch/rhythm reference: Alan W. Pollack's Notes on Day Tripper (DT.1),
     * corroborated by Ethan Hein's Musical simples: Day Tripper.
     * The tempo, orchestration and repeat count are audition choices.
     */
    public static Song dayTripper() {
        Song song = new Song("DayTripper", DAY_TRIPPER_BPM);
        addDayTripperPhrase(song, 0.0, false, false);
        addDayTripperPhrase(song, 8.0, true, false);
        addDayTripperPhrase(song, 16.0, true, true);
        addSilentMarker(song, tick(24.0));
        return song;
    }

    /** The backed second half used after Roulette's one-time progressive intro. */
    public static Song dayTripperBackedLoop() {
        Song song = new Song("DayTripperBackedLoop", DAY_TRIPPER_BPM);
        addDayTripperPhrase(song, 0.0, true, true);
        addDayTripperPhrase(song, 8.0, true, true);
        // ActiveSong resets only after processing its maximum tick. Ending
        // one tick before beat 16 makes the next engine tick the downbeat.
        addSilentMarker(song, tick(16.0) - 1);
        return song;
    }

    /** Scheduler delay for handing the progressive intro to {@link #dayTripperBackedLoop()}. */
    public static int dayTripperIntroDurationTicks() {
        // The newly-started backed song emits tick zero on the following VSE
        // update, so hand it off one tick before the intended downbeat.
        return tick(24.0) - 1;
    }

    private static void addDayTripperPhrase(Song song, double start, boolean withBass, boolean withDrums) {
        // E3 G3 G#3 B3 E4 D4 B3 F#4 B3 D4 E4.
        // Positions are eighth-note indices in TWO 4/4 bars, not tab spacing.
        int[] pitches = {52, 55, 56, 59, 64, 62, 59, 66, 59, 62, 64};
        int[] eighths = {0, 3, 4, 5, 6, 7, 10, 11, 13, 14, 15};
        for (int i = 0; i < pitches.length; i++) {
            double beat = start + eighths[i] / 2.0;
            float accent = i == 0 || i == 5 || i == 7 ? 0.65f : 0.52f;
            pluck(song, GUITAR, 54, pitches[i], beat, accent);
            if (withBass) {
                pluck(song, BASS, 42, pitches[i] - 12, beat, 0.42f);
            }
        }
        if (withDrums) {
            for (int eighth = 0; eighth < 16; eighth++) {
                hit(song, "minecraft:block.note_block.hat", start + eighth / 2.0,
                    eighth % 2 == 0 ? 0.12f : 0.08f);
            }
            for (int beat = 0; beat < 8; beat++) {
                hit(song, beat % 2 == 0 ? "minecraft:block.note_block.basedrum"
                    : "minecraft:block.note_block.snare", start + beat, 0.22f);
            }
        }
    }

    private static void addSilentMarker(Song song, int boundaryTick) {
        song.addNote(new Note(GUITAR, boundaryTick, boundaryTick, 1.0f, 0.0f));
    }

    /**
     * I Feel Fine's eight-bar opening riff, repeated once with an arranged
     * rhythm-section entrance and a seamless loop boundary. The source riff moves
     * D7-C7-G7-G7; this version is coherently transposed down one semitone so
     * its complete two-octave span fits the vanilla guitar sample.
     *
     * <p>Pitch/rhythm references: the Hal Leonard Guitar Tab Play-Along
     * preview (quarter note = 180, swung eighths) and Howard Wright's
     * independent fret transcription. Alan W. Pollack corroborates the key,
     * meter and opening dominant-chain harmony. The second repeat, bass,
     * and percussion are audition arrangement choices.
     */
    public static Song iFeelFine() {
        Song song = new Song("IFeelFine", I_FEEL_FINE_BPM);

        // Relative to each riff root: root, fifth, octave, b7, fifth,
        // fourth, major third, second, major third, second. The written
        // eighths use the source's long-short swing feel.
        int[] intervals = {0, 7, 12, 10, 7, 17, 16, 14, 16, 14};
        double[] beats = {0, 1, 2, 2 + 2.0 / 3.0, 3, 3 + 2.0 / 3.0,
            4 + 2.0 / 3.0, 5 + 2.0 / 3.0, 6 + 2.0 / 3.0, 7};
        // C#7, B7, F#7, F#7: the recording's D7, C7, G7, G7 down one semitone.
        int[] roots = {49, 47, 42, 42};

        for (int pass = 0; pass < 2; pass++) {
            double passStart = pass * 32.0;
            for (int phrase = 0; phrase < roots.length; phrase++) {
                double phraseStart = passStart + phrase * 8.0;
                for (int i = 0; i < intervals.length; i++) {
                    float volume = i == 0 ? 0.62f : (i == 5 || i == 6 ? 0.56f : 0.48f);
                    pluck(song, GUITAR, 54, roots[phrase] + intervals[i],
                        phraseStart + beats[i], volume, I_FEEL_FINE_BPM);
                }

                if (pass == 1) {
                    // Two low anchors per bar preserve the roots without
                    // doubling every moving note of the guitar figure.
                    for (int barBeat = 0; barBeat < 8; barBeat += 2) {
                        pluck(song, BASS, 42, roots[phrase] - 12,
                            phraseStart + barBeat, 0.34f, I_FEEL_FINE_BPM);
                    }
                }
            }

            if (pass == 1) {
                // A restrained rock pulse under the second pass. The guitar's
                // swung attacks remain the rhythmic foreground.
                for (int beat = 0; beat < 32; beat++) {
                    hit(song, "minecraft:block.note_block.hat", passStart + beat,
                        beat % 2 == 0 ? 0.09f : 0.07f, I_FEEL_FINE_BPM);
                    if (beat % 4 == 0) {
                        hit(song, "minecraft:block.note_block.basedrum", passStart + beat,
                            0.18f, I_FEEL_FINE_BPM);
                    } else if (beat % 2 == 1) {
                        hit(song, "minecraft:block.note_block.snare", passStart + beat,
                            0.15f, I_FEEL_FINE_BPM);
                    }
                }
            }
        }

        // VSE derives song length from the last event. Preserve the beat-64
        // boundary without an audible cadence so looping returns naturally
        // to the opening riff instead of sounding like a finished song restart.
        int loopBoundary = tick(64.0, I_FEEL_FINE_BPM);
        song.addNote(new Note(GUITAR, loopBoundary, loopBoundary, 1.0f, 0.0f));
        return song;
    }

    /**
     * The complete original Golden Slumbers recording, ending with its own
     * transition fill immediately before Carry That Weight (about 0:00-1:31).
     * The extension follows the accepted 0:00-0:50 baseline without an
     * artificial comparison gap; no Carry That Weight downbeat or vocal is included.
     *
     * <p>Alan W. Pollack and Patrick S. Gutman establish the C-major tonal
     * center, 4/4 meter, asymmetric form, harmony, rocking eighth-note piano
     * texture and dramatic rhythm-section/orchestra entrance. Gutman's score
     * parses a one-bar intro followed by a 10.5-bar verse, a 9.5-bar chorus,
     * and the returning 10.5-bar verse. A BitMidi sequence (SHA-256
     * 849EE12550BEDB64AC1EF0C6CB8FFD000B62B9A264334952D568B8FD204EF4E7)
     * supplied a candidate onset grid, cross-checked against Machin's notated
     * vocal figures rather than treated as the recording itself.
     */
    public static Song goldenSlumbers() {
        Song song = new Song("GoldenSlumbers", GOLDEN_SLUMBERS_BPM);
        addGoldenSlumbersPiano(song);
        addGoldenSlumbersVocal(song);
        addGoldenSlumbersBass(song);
        addGoldenSlumbersStrings(song);
        addGoldenSlumbersBrass(song);
        addGoldenSlumbersDrums(song);

        // Continue on the scheduler tick immediately after the accepted score's
        // final attack at tick 985; all extension-relative timing stays unchanged.
        addGoldenSlumbersChorusClose(song);
        addGoldenSlumbersHomewardReturn(song);
        addGoldenSlumbersFinalLullabyAndTransition(song);

        // Preserve the 81-BPM quarter-note grid after the asymmetric final fill.
        // These four restrained clicks occupy one empty 4/4 measure; VSE loops
        // after the silent marker so the opening lands on the next downbeat.
        for (int onset : GOLDEN_SLUMBERS_LOOP_CLICK_TICKS) {
            song.addNote(new Note("minecraft:block.note_block.hat", onset, onset,
                1.0f, 0.14f));
        }
        song.addNote(new Note(HARP, GOLDEN_SLUMBERS_LOOP_END_TICK,
            GOLDEN_SLUMBERS_LOOP_END_TICK, 1.0f, 0.0f));
        return song;
    }

    /** Released-recording chorus close, source beats 71..<88 (about 0:50-1:02). */
    private static void addGoldenSlumbersChorusClose(Song song) {
        // Piano: preserve the established upper harp chords / lower guitar rocking pulse.
        extensionPianoRoot(song, 71, 36, 0.39f);
        extensionRockingPiano(song, 71, 72, 55, 0.41f, 60, 64, 67); // C
        extensionPianoRoot(song, 72, 35, 0.39f);
        extensionRockingPiano(song, 72, 74, 59, 0.42f, 59, 62, 68); // E7
        extensionPianoRoot(song, 74, 45, 0.38f);
        extensionRockingPiano(song, 74, 76, 57, 0.42f, 60, 64);     // Am
        extensionPianoRoot(song, 76, 38, 0.39f);
        extensionRockingPiano(song, 76, 80, 53, 0.42f, 57, 64);     // Dm7
        extensionPianoRoot(song, 80, 43, 0.40f);
        int[][] risingG7 = {{55,59,65}, {57,60,65}, {59,62,65}, {55,59,65}};
        for (int i = 0; i < risingG7.length; i++) {
            goldenExtensionChord(song, HARP, 66, 80 + i, 0.40f, risingG7[i]);
        }
        extensionPianoRoot(song, 84, 36, 0.40f);
        extensionRockingPiano(song, 84, 88, 55, 0.40f, 60, 64, 67); // C cadence

        // Vocal: same flute, coherent major-third transposition and renewal policy.
        goldenExtensionVocalPhrase(song, 0.72f,
            new int[] {72,81,79,79,79,76,72,69,72,74,69},
            new double[] {71.75,72,72.25,72.5,72.75,73,74.25,74.5,75,75.25,75.75},
            new double[] {.24,.24,.24,.24,.24,1.24,.24,.49,.24,.49,2.24});
        goldenExtensionVocalPhrase(song, 0.62f,
            new int[] {67,71,72,74,71,67,65,64,72},
            new double[] {80.5,80.75,81.25,81.75,82.5,83,83+1.0/3.0,83+2.0/3.0,84.5},
            new double[] {.24,.49,.49,.74,.49,.32,.32,.82,1.49});

        // Bass walks out of the loud refrain, then climbs into the tonic cadence.
        for (int[] note : new int[][] {{71,36},{72,35},{73,35},{74,33},{75,33},
                {76,38},{80,43},{81,45},{82,47},{83,43},{84,36}}) {
            goldenExtensionPluck(song, BASS, 42, note[1], note[0], 0.43f);
        }

        // Strings and brass retain the source's moving inner voices, then thin on C.
        extensionOrchestralChord(song, 72, 0.32f, 59,62,68);
        extensionOrchestralChord(song, 74, 0.31f, 64,69);
        extensionOrchestralChord(song, 75, 0.30f, 64,67,69);
        extensionOrchestralChord(song, 76, 0.31f, 64,65,69);
        extensionOrchestralChord(song, 78, 0.27f, 62);
        extensionOrchestralChord(song, 80, 0.28f, 59,65);
        extensionOrchestralChord(song, 81, 0.28f, 60,65);
        extensionOrchestralChord(song, 82, 0.28f, 62,65);
        extensionOrchestralChord(song, 83, 0.28f, 59,65);
        extensionOrchestralChord(song, 84, 0.29f, 60,64);
        goldenExtensionChord(song, DIDGERIDOO, 42, 72, 0.22f, 35,40);
        goldenExtensionChord(song, DIDGERIDOO, 42, 74, 0.21f, 33,40);
        goldenExtensionChord(song, DIDGERIDOO, 42, 76, 0.22f, 38,45);

        // Refrain kit through beat 76; a soft two-hit pickup announces verse two.
        for (double beat : new double[] {71.5,72.5,73.5,74.5,75.5}) {
            goldenExtensionHit(song, "minecraft:block.note_block.hat", beat, 1.0f, 0.17f);
        }
        for (double beat : new double[] {72,74,76}) {
            goldenExtensionHit(song, "minecraft:block.note_block.basedrum", beat, 1.0f, 0.40f);
        }
        for (double beat : new double[] {71,73,75}) {
            goldenExtensionHit(song, "minecraft:block.note_block.snare", beat, 1.0f, 0.34f);
        }
        goldenExtensionHit(song, "minecraft:block.note_block.basedrum", 75.75, 1.08f, 0.24f);
        goldenExtensionHit(song, "minecraft:block.note_block.basedrum", 75.875, 1.18f, 0.23f);
        goldenExtensionHit(song, "minecraft:block.note_block.basedrum", 76, 1.28f, 0.28f);
        goldenExtensionHit(song, "minecraft:block.note_block.basedrum", 87+5.0/6.0, 0.92f, 0.18f);
        goldenExtensionHit(song, "minecraft:block.note_block.basedrum", 87+11.0/12.0, 1.05f, 0.20f);
    }

    /** Quieter second-verse return, source beats 88..<112 (about 1:02-1:20). */
    private static void addGoldenSlumbersHomewardReturn(Song song) {
        // The opening's harmonic/piano language returns, but without another intro bar.
        extensionRockingPiano(song, 88, 100, 57, 0.29f, 60, 67); // Am7
        extensionPianoRoot(song, 96, 45, 0.23f);
        extensionRockingPiano(song, 100, 102, 57, 0.30f, 62, 65);
        extensionRockingPiano(song, 102, 104, 57, 0.30f, 64, 67);
        extensionPianoRoot(song, 104, 50, 0.24f);
        extensionRockingPiano(song, 104, 106, 57, 0.31f, 65, 69);
        extensionRockingPiano(song, 106, 108, 57, 0.30f, 64, 67);
        extensionPianoRoot(song, 108, 43, 0.25f);
        extensionRockingPiano(song, 108, 112, 55, 0.31f, 59, 65);

        goldenExtensionVocalPhrase(song, 0.43f,
            new int[] {67,67,67,67,67,64,69,72,64,62},
            new double[] {88,88.25,88.5,88.75,89,93.75,94,94.5,95.5,96},
            new double[] {.24,.24,.24,.24,.99,.24,.49,.99,.49,.99});
        goldenExtensionVocalPhrase(song, 0.46f,
            new int[] {71,71,71,71,71,67,72,74,76},
            new double[] {104,104.25,104.5,104.75,105,109.75,110,110.5,111.5},
            new double[] {.24,.24,.24,.24,.99,.24,.49,.99,.49});

        extensionBassSlide(song, 88, 43, 45, 0.25f);
        extensionBassSlide(song, 92, 43, 45, 0.25f);
        extensionBassSlide(song, 100, 36, 38, 0.26f);
        extensionBassSlide(song, 108, 41, 43, 0.27f);

        // Soft orchestral bed plus the exposed rising/falling string countermelody.
        extensionOrchestralChord(song, 92, 0.12f, 57,60,67);
        int[] stringLine = {57,62,64,65,64,62,64,65,69,65,64,57,62,64,65,62,60,59};
        double[] stringBeats = {96.5,97,97.5,97.75,98,98.5,99,99.5,100,100.5,101,101.5,
            102,102.5,102.75,103,103.5,104};
        for (int i = 0; i < stringLine.length; i++) {
            goldenExtensionPluck(song, CHIME, 90, stringLine[i] + 24, stringBeats[i], 0.16f);
        }
        for (int[] note : new int[][] {{106,55},{107,59},{108,55}}) {
            extensionOrchestralChord(song, note[0], 0.14f, note[1]);
        }
        // The source cello G2 falls below chime's playable range; bass retains it.

        goldenExtensionChord(song, DIDGERIDOO, 42, 92, 0.13f, 40);
        goldenExtensionChord(song, DIDGERIDOO, 42, 96, 0.13f, 38);
        goldenExtensionChord(song, DIDGERIDOO, 42, 98, 0.13f, 40);
        goldenExtensionChord(song, DIDGERIDOO, 42, 100, 0.14f, 41);
        goldenExtensionChord(song, DIDGERIDOO, 42, 102, 0.13f, 40);
        goldenExtensionChord(song, DIDGERIDOO, 42, 103, 0.13f, 38);
        goldenExtensionChord(song, DIDGERIDOO, 42, 104, 0.14f, 38);
        goldenExtensionChord(song, DIDGERIDOO, 42, 108, 0.10f, 38);

        // Ringo drops to sparse cymbal/downbeat colour through the quiet return.
        for (double beat : new double[] {88,96,104}) {
            goldenExtensionHit(song, "minecraft:block.note_block.hat", beat, 0.86f, 0.16f);
        }
        for (double beat : new double[] {87+5.0/6.0,95+5.0/6.0,103+5.0/6.0}) {
            goldenExtensionHit(song, "minecraft:block.note_block.basedrum", beat, 0.94f, 0.14f);
            goldenExtensionHit(song, "minecraft:block.note_block.basedrum", beat + 1.0/12.0,
                1.06f, 0.15f);
        }
    }

    /** Final lullaby/cadence and GS-side transition fill, beats 112..<130 (about 1:20-1:31). */
    private static void addGoldenSlumbersFinalLullabyAndTransition(Song song) {
        extensionPianoRoot(song, 112, 36, 0.29f);
        extensionRockingPiano(song, 112, 114, 55, 0.34f, 60,64,67); // C
        extensionPianoRoot(song, 114, 40, 0.28f);
        extensionRockingPiano(song, 114, 116, 59, 0.34f, 59,62,68); // E7
        extensionPianoRoot(song, 116, 45, 0.28f);
        extensionRockingPiano(song, 116, 118, 57, 0.34f, 60,64);    // Am
        extensionPianoRoot(song, 118, 38, 0.29f);
        extensionRockingPiano(song, 118, 122, 53, 0.35f, 57,64);    // Dm7
        extensionPianoRoot(song, 122, 43, 0.30f);
        int[][] finalG7 = {{55,59,65}, {57,60,65}, {59,62,65}, {55,59,65}};
        for (int i = 0; i < finalG7.length; i++) {
            goldenExtensionChord(song, HARP, 66, 122 + i, 0.36f, finalG7[i]);
        }
        extensionPianoRoot(song, 126, 36, 0.34f);
        extensionRockingPiano(song, 126, 130, 55, 0.38f, 60,64,67); // C up to CTW boundary

        goldenExtensionVocalPhrase(song, 0.48f,
            new int[] {72,81,79,79,79,76,74,69,72,74,69},
            new double[] {113.75,114,114.25,114.5,114.75,115,116.25,116.5,
                117,117.25,117.75},
            new double[] {.24,.24,.24,.24,.24,1.24,.24,.49,.24,.49,2.24});
        goldenExtensionVocalPhrase(song, 0.50f,
            new int[] {67,71,72,74,71,67,65,64,72},
            new double[] {122.5,122.75,123.25,123.75,124.75,125,125+1.0/3.0,
                125+2.0/3.0,126.5},
            new double[] {.24,.49,.49,.99,.24,.32,.32,.82,1.49});

        for (int[] note : new int[][] {{112,36},{114,40},{116,45},{118,38},
                {122,43},{123,45},{124,47},{125,50},{126,36}}) {
            goldenExtensionPluck(song, BASS, 42, note[1], note[0], 0.31f);
        }

        extensionOrchestralChord(song, 112, 0.17f, 60,64,67);
        extensionOrchestralChord(song, 114, 0.17f, 59,62,68);
        extensionOrchestralChord(song, 116, 0.18f, 57,60,69);
        extensionOrchestralChord(song, 117, 0.13f, 59);
        extensionOrchestralChord(song, 120, 0.14f, 60);
        extensionOrchestralChord(song, 122, 0.18f, 55,59,65);
        extensionOrchestralChord(song, 123, 0.18f, 57,60,65);
        extensionOrchestralChord(song, 124, 0.19f, 59,62,65);
        extensionOrchestralChord(song, 125, 0.19f, 55,59,65);
        extensionOrchestralChord(song, 126, 0.22f, 60,64,67);

        // Brass becomes a quiet descending/answering line rather than chorus weight.
        // The source's opening G doubles the piano exactly, so omit that redundant attack.
        for (int[] note : new int[][] {{113,69},{114,64},{117,57},{118,53}}) {
            goldenExtensionPluck(song, HARP, 66, note[1], note[0], 0.16f);
        }
        goldenExtensionChord(song, DIDGERIDOO, 42, 122, 0.14f, 43,49);
        goldenExtensionChord(song, DIDGERIDOO, 42, 123, 0.14f, 45,48);
        goldenExtensionChord(song, DIDGERIDOO, 42, 124, 0.15f, 47,50);
        goldenExtensionChord(song, DIDGERIDOO, 42, 125, 0.15f, 43,47);
        goldenExtensionChord(song, DIDGERIDOO, 42, 126, 0.17f, 36,43);

        for (double beat : new double[] {112,114,116,118}) {
            goldenExtensionHit(song, "minecraft:block.note_block.basedrum", beat, 1.0f, 0.28f);
            goldenExtensionHit(song, "minecraft:block.note_block.hat", beat, 0.88f, 0.16f);
        }
        for (double beat : new double[] {113,115,117}) {
            goldenExtensionHit(song, "minecraft:block.note_block.snare", beat, 1.0f, 0.22f);
        }

        // The two-beat fill belongs to Golden Slumbers' final measure. Stop before
        // source beat 130, where the separate Carry That Weight tracks begin.
        double[] transitionBeats = {128,128.25,128.75,129,129.25,129.5};
        float[] transitionPitch = {0.82f,0.92f,0.92f,1.02f,1.12f,1.24f};
        for (int i = 0; i < transitionBeats.length; i++) {
            goldenExtensionHit(song, "minecraft:block.note_block.basedrum",
                transitionBeats[i], transitionPitch[i], i == 0 || i == 3 || i == 5 ? 0.30f : 0.22f);
        }
    }

    private static void addGoldenSlumbersPiano(Song song) {
        // Paul rocks treble dyads/chords against a single lower note in eighths.
        rockingPiano(song, 0, 12, 57, 60, 67);       // Am7 without E: C-G / A.
        pianoRoot(song, 8, 45, 0.24f);
        rockingPiano(song, 12, 14, 57, 62, 65);      // Dm, inner voices rising.
        rockingPiano(song, 14, 16, 57, 64, 67);
        pianoRoot(song, 16, 50, 0.24f);
        rockingPiano(song, 16, 18, 57, 65, 69);
        rockingPiano(song, 18, 20, 57, 64, 67);
        pianoRoot(song, 20, 43, 0.25f);
        pianoRoot(song, 24, 43, 0.22f);
        rockingPiano(song, 20, 28, 55, 59, 65);      // G7.
        pianoRoot(song, 28, 48, 0.28f);
        rockingPiano(song, 28, 30, 55, 60, 64, 67);  // C.
        pianoRoot(song, 30, 52, 0.27f);
        rockingPiano(song, 30, 32, 59, 62, 68);      // E7, with G#.
        pianoRoot(song, 32, 45, 0.27f);
        rockingPiano(song, 32, 34, 57, 60, 64);      // Am.
        pianoRoot(song, 34, 50, 0.27f);
        rockingPiano(song, 34, 38, 53, 57, 64);      // Dm9, sustained E color.

        int[][] risingG7 = {{55, 59, 65}, {57, 60, 65}, {59, 62, 65}, {55, 59, 65}};
        for (int i = 0; i < risingG7.length; i++) {
            goldenChord(song, HARP, 66, 38 + i, 0.31f, risingG7[i]);
        }

        pianoRoot(song, 42, 48, 0.29f);
        rockingPiano(song, 42, 46, 55, 60, 64, 67);

        // The refrain keeps the same pulse but opens the register and dynamics.
        pianoRoot(song, 46, 48, 0.42f);
        pianoRoot(song, 49, 48, 0.35f);
        rockingPiano(song, 46, 50, 55, 0.43f, 60, 64, 67);
        pianoRoot(song, 50, 53, 0.42f);
        rockingPiano(song, 50, 54, 57, 0.44f, 60, 65, 67);
        pianoRoot(song, 54, 48, 0.40f);
        rockingPiano(song, 54, 58, 55, 0.42f, 60, 64, 67);
        pianoRoot(song, 58, 48, 0.40f);
        pianoRoot(song, 61, 48, 0.34f);
        rockingPiano(song, 58, 62, 55, 0.42f, 60, 64, 67);
        pianoRoot(song, 62, 53, 0.42f);
        rockingPiano(song, 62, 66, 57, 0.44f, 60, 65, 67);
        goldenChord(song, GUITAR, 54, 66, 0.43f, 48, 55);
        goldenChord(song, HARP, 66, 66, 0.48f, 60, 64, 67);
    }

    private static void addGoldenSlumbersVocal(Song song) {
        // One coherent major-third transposition keeps the complete source
        // register/contour in flute range and separates it from the backing.
        vocalPhrase(song, 0.40f,
            new int[] {67,67,67,67,67,64,69,72,64,62},
            new double[] {4,4.25,4.5,4.75,5,9.75,10,10.5,11.5,12},
            new double[] {.24,.24,.24,.24,.99,.24,.49,.99,.49,.99});
        vocalPhrase(song, 0.42f,
            new int[] {71,71,71,71,71,67,72,74,76,72,81,79,79,79,76,74,69,72,74,69},
            new double[] {20,20.25,20.5,20.75,21,25.75,26,26.5,27.5,29.75,30,30.25,
                30.5,30.75,31,32.25,32.5,33,33.25,33.75},
            new double[] {.24,.24,.24,.24,.99,.24,.49,.99,.49,.24,.24,.24,.24,.24,
                1.24,.24,.49,.24,.49,2.24});
        vocalPhrase(song, 0.44f,
            new int[] {67,71,72,74,71,67,65,64,72},
            new double[] {38.5,38.75,39.25,39.75,40.75,41,41+1.0/3.0,41+2.0/3.0,42.5},
            new double[] {.24,.49,.49,.99,.24,.32,.32,.82,1.49});

        // Refrain: the large vocal change is chiefly volume and tessitura,
        // not a different sound, so the melody remains one identifiable voice.
        vocalPhrase(song, 0.78f,
            new int[] {76,76,76,69,72,69,67,76},
            new double[] {46,48.5,49.5,51,51.5,53,53.25,53.5},
            new double[] {2.49,.99,1.49,.49,1.49,.24,.24,3.49});
        vocalPhrase(song, 0.80f,
            new int[] {76,81,76,69,72,69,67,76},
            new double[] {58,61,61.5,63,63.5,65,65.25,65.5},
            new double[] {2.99,.49,1.49,.49,1.49,.24,.24,1.49});
    }

    private static void addGoldenSlumbersBass(Song song) {
        // Sparse held roots in the verse; the close grace-note pickups are
        // two-tick grace attacks at this tempo and remain distinct after rounding.
        bassSlide(song, 8, 43, 45, 0.25f);
        bassSlide(song, 16, 36, 38, 0.25f);
        bassSlide(song, 24, 41, 43, 0.27f);
        for (int[] note : new int[][] {{28,36},{30,40},{32,45},{34,38},{38,43},
            {39,45},{40,47},{41,43},{42,36}}) {
            goldenPluck(song, BASS, 42, note[1], note[0], 0.28f);
        }

        goldenPluck(song, BASS, 42, 36, 46, 0.48f);
        chorusBassCell(song, 47.5, 36);
        chorusBassCell(song, 51.5, 41);
        chorusBassCell(song, 55.5, 36);
        chorusBassCell(song, 59.5, 36);
        chorusBassCell(song, 63.5, 41);
        goldenPluck(song, BASS, 42, 36, 66, 0.52f);
    }

    private static void addGoldenSlumbersStrings(Song song) {
        // Chime's natural register is two octaves above harp. Moving every
        // source string voice up two octaves (before the global transposition)
        // preserves its internal voicing while giving a long, quiet sheen.
        orchestralChord(song, 8, 0.11f, 57, 60, 67);
        orchestralChord(song, 12, 0.11f, 62, 65);
        orchestralChord(song, 14, 0.12f, 64, 67);
        orchestralChord(song, 16, 0.12f, 65, 69);
        orchestralChord(song, 18, 0.11f, 64, 67);
        orchestralChord(song, 20, 0.13f, 55, 59, 65);
        orchestralChord(song, 24, 0.14f, 55, 59, 65);
        orchestralChord(song, 28, 0.15f, 60, 64, 67);
        orchestralChord(song, 30, 0.15f, 59, 62, 68);
        orchestralChord(song, 32, 0.16f, 57, 60, 69);
        orchestralChord(song, 35, 0.09f, 69);
        orchestralChord(song, 38, 0.17f, 55, 59, 65);
        orchestralChord(song, 39, 0.17f, 57, 60, 65);
        orchestralChord(song, 40, 0.18f, 59, 62, 65);
        orchestralChord(song, 41, 0.18f, 55, 59, 65);
        orchestralChord(song, 42, 0.21f, 55, 60, 64);

        orchestralChord(song, 46, 0.34f, 60, 64, 67);
        orchestralChord(song, 50, 0.36f, 57, 60, 65);
        orchestralChord(song, 54, 0.34f, 60, 64, 67);
        orchestralChord(song, 58, 0.35f, 60, 64, 67);
        orchestralChord(song, 62, 0.37f, 57, 60, 65);
        orchestralChord(song, 66, 0.38f, 60, 64, 67);
    }

    private static void addGoldenSlumbersBrass(Song song) {
        // Low didgeridoo attacks stand in for the recording's horn/brass body.
        // They enter only at the dramatic refrain and leave the verse transparent.
        goldenChord(song, DIDGERIDOO, 42, 46, 0.24f, 36, 43);
        goldenChord(song, DIDGERIDOO, 42, 50, 0.25f, 36, 41);
        goldenChord(song, DIDGERIDOO, 42, 54, 0.24f, 36, 43);
        goldenChord(song, DIDGERIDOO, 42, 58, 0.25f, 36, 43);
        goldenChord(song, DIDGERIDOO, 42, 62, 0.27f, 36, 41);
        goldenChord(song, DIDGERIDOO, 42, 66, 0.28f, 36, 43);
    }

    private static void addGoldenSlumbersDrums(Song song) {
        // Ascending tom-like fill begins two beats before the refrain downbeat.
        double[] fillBeats = {44,44.25,44.75,45,45.25,45.5};
        float[] fillPitch = {0.80f,0.88f,0.88f,1.00f,1.12f,1.26f};
        for (int i = 0; i < fillBeats.length; i++) {
            pitchedHit(song, "minecraft:block.note_block.basedrum", fillBeats[i],
                fillPitch[i], i == 0 || i == 3 || i == 5 ? 0.30f : 0.22f);
        }
        for (double start = 46; start < 66; start += 4) {
            hit(song, "minecraft:block.note_block.basedrum", start, 0.42f, GOLDEN_SLUMBERS_BPM);
            hit(song, "minecraft:block.note_block.hat", start, 0.24f, GOLDEN_SLUMBERS_BPM);
            hit(song, "minecraft:block.note_block.basedrum", start + 1.5, 0.32f, GOLDEN_SLUMBERS_BPM);
            hit(song, "minecraft:block.note_block.hat", start + 1.5, 0.18f, GOLDEN_SLUMBERS_BPM);
            pitchedHit(song, "minecraft:block.note_block.basedrum", start + 2.5, 0.92f, 0.25f);
            hit(song, "minecraft:block.note_block.snare", start + 3, 0.36f, GOLDEN_SLUMBERS_BPM);
            pitchedHit(song, "minecraft:block.note_block.basedrum", start + 3.25, 1.08f, 0.23f);
            pitchedHit(song, "minecraft:block.note_block.basedrum", start + 3.5, 1.20f, 0.28f);
        }
        hit(song, "minecraft:block.note_block.basedrum", 66, 0.46f, GOLDEN_SLUMBERS_BPM);
        hit(song, "minecraft:block.note_block.hat", 66, 0.25f, GOLDEN_SLUMBERS_BPM);
    }

    private static void rockingPiano(Song song, int startBeat, int endBeat,
                                     int lowerMidi, int... upperMidi) {
        rockingPiano(song, startBeat, endBeat, lowerMidi, 0.30f, upperMidi);
    }

    private static void rockingPiano(Song song, int startBeat, int endBeat,
                                     int lowerMidi, float volume, int... upperMidi) {
        for (int beat = startBeat; beat < endBeat; beat++) {
            goldenChord(song, HARP, 66, beat, volume, upperMidi);
            goldenPluck(song, GUITAR, 54, lowerMidi, beat + 0.5, volume * 0.72f);
        }
    }

    private static void pianoRoot(Song song, double beat, int midi, float volume) {
        // Low source octaves below guitar range are deliberately moved up;
        // the independent bass part retains the true bottom octave.
        while (midi < 42) midi += 12;
        goldenPluck(song, GUITAR, 54, midi, beat, volume);
    }

    private static void vocalPhrase(Song song, float volume, int[] pitches,
                                    double[] beats, double[] durations) {
        for (int i = 0; i < pitches.length; i++) {
            goldenPluck(song, FLUTE, 78, pitches[i], beats[i], volume);
            // VSE cannot sustain per pitch safely. Quiet breath-like renewals
            // keep only the longest sung notes present without hard note-offs.
            for (double held = 1.0; held < durations[i] - 0.25; held += 1.0) {
                goldenPluck(song, FLUTE, 78, pitches[i], beats[i] + held, volume * 0.38f);
            }
        }
    }

    private static void bassSlide(Song song, double beat, int from, int to, float volume) {
        goldenPluck(song, BASS, 42, from, beat, volume * 0.72f);
        goldenPluck(song, BASS, 42, to, beat + 0.125, volume);
    }

    private static void chorusBassCell(Song song, double rootBeat, int root) {
        goldenPluck(song, BASS, 42, root, rootBeat, 0.46f);
        goldenPluck(song, BASS, 42, 31, rootBeat + 1.5, 0.30f);
        goldenPluck(song, BASS, 42, 33, rootBeat + 1.75, 0.33f);
        goldenPluck(song, BASS, 42, 36, rootBeat + 2.0, 0.39f);
    }

    private static void orchestralChord(Song song, double beat, float volume, int... sourceMidi) {
        for (int midi : sourceMidi) {
            goldenPluck(song, CHIME, 90, midi + 24, beat, volume);
        }
    }

    private static void extensionRockingPiano(Song song, int startBeat, int endBeat,
                                              int lowerMidi, float volume, int... upperMidi) {
        for (int beat = startBeat; beat < endBeat; beat++) {
            goldenExtensionChord(song, HARP, 66, beat, volume, upperMidi);
            goldenExtensionPluck(song, GUITAR, 54, lowerMidi, beat + 0.5, volume * 0.72f);
        }
    }

    private static void extensionPianoRoot(Song song, double sourceBeat, int midi, float volume) {
        while (midi < 42) midi += 12;
        goldenExtensionPluck(song, GUITAR, 54, midi, sourceBeat, volume);
    }

    private static void extensionBassSlide(Song song, double sourceBeat, int from, int to,
                                           float volume) {
        goldenExtensionPluck(song, BASS, 42, from, sourceBeat, volume * 0.72f);
        goldenExtensionPluck(song, BASS, 42, to, sourceBeat + 0.125, volume);
    }

    private static void goldenExtensionVocalPhrase(Song song, float volume, int[] pitches,
                                                    double[] sourceBeats, double[] durations) {
        for (int i = 0; i < pitches.length; i++) {
            goldenExtensionPluck(song, FLUTE, 78, pitches[i], sourceBeats[i], volume);
            for (double held = 1.0; held < durations[i] - 0.25; held += 1.0) {
                goldenExtensionPluck(song, FLUTE, 78, pitches[i], sourceBeats[i] + held,
                    volume * 0.38f);
            }
        }
    }

    private static void extensionOrchestralChord(Song song, double sourceBeat, float volume,
                                                 int... sourceMidi) {
        for (int midi : sourceMidi) {
            goldenExtensionPluck(song, CHIME, 90, midi + 24, sourceBeat, volume);
        }
    }

    private static void goldenExtensionChord(Song song, String sound, int referenceMidi,
                                              double sourceBeat, float volume, int... sourceMidi) {
        for (int midi : sourceMidi) {
            goldenExtensionPluck(song, sound, referenceMidi, midi, sourceBeat, volume);
        }
    }

    private static void goldenExtensionPluck(Song song, String sound, int referenceMidi,
                                              int sourceMidi, double sourceBeat, float volume) {
        float pitch = (float) Math.pow(2.0,
            (sourceMidi + GOLDEN_SLUMBERS_TRANSPOSE - referenceMidi) / 12.0);
        if (pitch < 0.5f || pitch > 2.0f) {
            throw new IllegalArgumentException("Note outside vanilla instrument range: " + sourceMidi);
        }
        int onset = goldenExtensionTick(sourceBeat);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    private static void goldenExtensionHit(Song song, String sound, double sourceBeat,
                                            float pitch, float volume) {
        int onset = goldenExtensionTick(sourceBeat);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    private static int goldenExtensionTick(double sourceBeat) {
        return GOLDEN_SLUMBERS_EXTENSION_START_TICK + (int) Math.round(
            (sourceBeat - GOLDEN_SLUMBERS_EXTENSION_SOURCE_BEAT) * 1200.0
                / GOLDEN_SLUMBERS_BPM);
    }

    private static void goldenChord(Song song, String sound, int referenceMidi, double beat,
                                    float volume, int... sourceMidi) {
        for (int midi : sourceMidi) goldenPluck(song, sound, referenceMidi, midi, beat, volume);
    }

    private static void goldenPluck(Song song, String sound, int referenceMidi, int sourceMidi,
                                    double beat, float volume) {
        pluck(song, sound, referenceMidi, sourceMidi + GOLDEN_SLUMBERS_TRANSPOSE,
            beat, volume, GOLDEN_SLUMBERS_BPM);
    }

    private static void pitchedHit(Song song, String sound, double beat, float pitch, float volume) {
        int onset = tick(beat, GOLDEN_SLUMBERS_BPM);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    /** Round absolute beat positions so fractional ticks never accumulate drift. */
    private static int tick(double beat) {
        return tick(beat, DAY_TRIPPER_BPM);
    }

    private static int tick(double beat, int bpm) {
        return (int) Math.round(beat * 1200.0 / bpm);
    }

    private static void pluck(Song song, String sound, int referenceMidi, int midi,
                              double beat, float volume) {
        float pitch = (float) Math.pow(2.0, (midi - referenceMidi) / 12.0);
        if (pitch < 0.5f || pitch > 2.0f) {
            throw new IllegalArgumentException("Note outside vanilla instrument range: " + midi);
        }
        int onset = tick(beat);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    private static void pluck(Song song, String sound, int referenceMidi, int midi,
                              double beat, float volume, int bpm) {
        float pitch = (float) Math.pow(2.0, (midi - referenceMidi) / 12.0);
        if (pitch < 0.5f || pitch > 2.0f) {
            throw new IllegalArgumentException("Note outside vanilla instrument range: " + midi);
        }
        int onset = tick(beat, bpm);
        song.addNote(new Note(sound, onset, onset, pitch, volume));
    }

    private static void hit(Song song, String sound, double beat, float volume) {
        int onset = tick(beat);
        song.addNote(new Note(sound, onset, onset, 1.0f, volume));
    }

    private static void hit(Song song, String sound, double beat, float volume, int bpm) {
        int onset = tick(beat, bpm);
        song.addNote(new Note(sound, onset, onset, 1.0f, volume));
    }
}
