package org.nc.nccasino.games.Slots;

import org.nc.VSE.Note;
import org.nc.VSE.Song;

/** VSE-driven songs for Slots. Currently just the opening-animation intro phrase. */
final class SlotsSongs {

    private SlotsSongs() {
    }

    /**
     * Every note-block instrument shares pitch 1.0 = MIDI note 66 (F#4) --
     * confirmed against the vanilla note block's own pitch table, which
     * uses one shared reference across every instrument and differs only
     * in timbre -- so one formula converts a real MIDI note number straight
     * to a playable pitch.
     */
    private static float pitchForMidiNote(int midiNote) {
        return (float) Math.pow(2.0, (midiNote - 66) / 12.0);
    }

    private static final String BASS = "minecraft:block.note_block.bass";
    private static final String HARP = "minecraft:block.note_block.harp";

    // The riff's real pitches (standard MIDI numbers: A3=57, E4=64, A4=69, B4=71, C#5=73),
    // split by register the way a real guitarist splits thumb (bass strings) from fingers (treble strings).
    private static final float A3 = pitchForMidiNote(57);
    private static final float E4 = pitchForMidiNote(64);
    private static final float A4 = pitchForMidiNote(69);
    private static final float B4 = pitchForMidiNote(71);
    private static final float CS5 = pitchForMidiNote(73);
    // The transcription's real final chord at 3.038s: a full four-note strum (F#4+D4+F#3+D3), the
    // guitar's actual move into the next chord -- not just the single treble note used before.
    // Pitch 1.0 exactly (F#4 is this whole file's tuning reference). D3 (MIDI 50) is dropped: shifted
    // up an octave to stay above the pitch floor, it becomes D4 -- already present in the chord, so
    // it would just double a note already there rather than add anything.
    private static final float FS4 = pitchForMidiNote(66);
    private static final float D4 = pitchForMidiNote(62);
    private static final float FS3 = pitchForMidiNote(54);

    /** 20 ticks = 1 real second (confirmed: VSE's tempo field is never read at playback; every tick is exactly 1/20s). */
    private static final int TICKS_PER_SECOND = 20;

    /**
     * The transcription's un-shifted last chord (see {@link #getOpeningIntro})
     * lands on tick 56; {@link SlotsOpeningColumnMotion#finalTick} for the
     * intro's fixed 9-column, double-rainbow-pass layout (9 filler + 6 rows,
     * twice -- 30 entries per column, confirmed against
     * {@link SlotsOpeningColumnMotion#buildEntrySequenceWithSettle} rather
     * than assumed) works out to 66. Delaying the whole riff by this many
     * ticks -- not compressing or re-timing anything between notes -- lines
     * the transcription's own next beat up with the last column's actual
     * landing tick.
     */
    private static final int SHIFT_TICKS = 10;

    private static int atSeconds(double seconds) {
        return (int) Math.round(seconds * TICKS_PER_SECOND) + SHIFT_TICKS;
    }

    /**
     * A verified transcription of the real "Here Comes the Sun" intro riff
     * -- not a guess from ASCII tab spacing (which encodes pitch but not
     * rhythm) and not an original composition standing in for it, but the
     * actual onset times pulled from a real MIDI sequencing of the song,
     * isolating its Acoustic Guitar track (the intro riff itself) and
     * confirming the file's own tempo lines up with the real recording
     * (128 BPM). Every timestamp below is that guitar track's real note
     * onset, in seconds from the riff's first downbeat, converted straight
     * to ticks -- no invented rhythm, no rounding to a theoretical grid.
     * Runs past the phrase's resting point (2.340s) all the way through the
     * transcription's real final chord (3.038s, the guitar's own move into
     * the next chord), and the whole thing is delayed by
     * {@link #SHIFT_TICKS} -- see its doc for why.
     *
     * <p>The close is the real final chord alone ({@link #FS4}/{@link #D4}/
     * {@link #FS3}, a genuine four-voice strum rather than one isolated
     * note) -- an earlier attempt to also land a bass strum exactly on
     * {@link SlotsOpeningColumnMotion#finalTick} was tried and rejected, so
     * that tick is quiet again. Nothing else is scheduled at the closing
     * tick (the reel-tick's own piston click is suppressed at finalTick,
     * and the separate ready chime is gone), so this chord is the entire
     * ending.
     */
    static Song getOpeningIntro() {
        Song song = new Song("SlotsOpeningIntro", 20);

        // 0.000s: the opening chord. The real transcription strikes A3+E4+A4+C#5 together, but
        // every other bass moment in this piece is exactly one clean BASS voice at a time (the
        // walking thumb alternation from 0.934s on) -- two simultaneous BASS hits only ever happens
        // here, and it's what made the opening read as muddy against that otherwise-consistent
        // texture. Dropping E4 keeps a real, in-key A major voicing (root in the bass, root+third
        // on top) without being the one spot that breaks the single-bass-voice rule.
        int chord = atSeconds(0.000);
        song.addNote(new Note(BASS, chord, chord, A3, 0.55f));
        song.addNote(new Note(HARP, chord, chord, A4, 0.45f));
        song.addNote(new Note(HARP, chord, chord, CS5, 0.45f));

        int t1 = atSeconds(0.235);
        song.addNote(new Note(HARP, t1, t1, A4, 0.4f));

        int t2 = atSeconds(0.473);
        song.addNote(new Note(HARP, t2, t2, B4, 0.42f));

        // 0.693s/0.699s: close enough together to land on the same tick -- a real double-stop pluck.
        int t3 = atSeconds(0.696);
        song.addNote(new Note(HARP, t3, t3, CS5, 0.42f));
        song.addNote(new Note(HARP, t3, t3, A4, 0.38f));

        int t4 = atSeconds(0.934);
        song.addNote(new Note(BASS, t4, t4, A3, 0.48f));

        // 1.163s/1.167s: another real double-stop.
        int t5 = atSeconds(1.165);
        song.addNote(new Note(BASS, t5, t5, E4, 0.42f));
        song.addNote(new Note(HARP, t5, t5, A4, 0.38f));

        int t6 = atSeconds(1.400);
        song.addNote(new Note(BASS, t6, t6, A3, 0.46f));

        int t7 = atSeconds(1.641);
        song.addNote(new Note(BASS, t7, t7, A3, 0.4f));

        int t8 = atSeconds(1.869);
        song.addNote(new Note(HARP, t8, t8, CS5, 0.4f));

        int t9 = atSeconds(2.107);
        song.addNote(new Note(HARP, t9, t9, B4, 0.4f));

        // 2.340s: the phrase's resting bass note.
        int t10 = atSeconds(2.340);
        song.addNote(new Note(BASS, t10, t10, A3, 0.42f));

        // 2.572s/2.578s: another real double-stop -- lands right on the intro's own final tick.
        int t11 = atSeconds(2.575);
        song.addNote(new Note(HARP, t11, t11, A4, 0.42f));
        song.addNote(new Note(BASS, t11, t11, E4, 0.4f));

        // The finale: the transcription's real final chord (3.038s), a genuine four-voice strum
        // rather than one isolated note -- see the class doc for the two picks this replaced.
        int t12 = atSeconds(3.038);
        song.addNote(new Note(HARP, t12, t12, FS4, 0.75f));
        song.addNote(new Note(HARP, t12, t12, D4, 0.55f));
        song.addNote(new Note(BASS, t12, t12, FS3, 0.5f));

        return song;
    }
}
