package org.nc.nccasino.games.Roulette;
import org.bukkit.Sound;
import org.nc.VSE.*;


//end tick doesnt seem to do anything currently

public class RouletteSongs {
    public static Song getTimerTick() {
        Song timerTick = new Song("TimerTick", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        timerTick.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 1, 1, 1.0f, 1.0f));
        return timerTick;
    }

    public static Song getSlowSpinSong() {
        Song spinSong = new Song("SlowSpin", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 0, 1, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 6, 5, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 12, 9, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 18, 13, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 24, 13, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 30, 13, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 36, 13, 1.0f, 1.0f));
        return spinSong;
    }


    public static Song getChargeSong() {
        Song chargeSong = new Song("ChargeUp", 20);
 
        // Example ascending scale
        // Format: new Note(Sound instrument, startTick, endTick, pitch, volume)

        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 0, 1, 0.8f, 1.0f));
        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 2, 3, 0.9f, 1.0f));
        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 4, 5, 1.0f, 1.0f));
        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 6, 7, 1.1f, 1.0f));
        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 8, 9, 1.2f, 1.0f));
        chargeSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HARP, 10, 11, 1.3f, 1.0f));

        // Also add a breeze.charge sound at tick 0:
        try {
            // Some versions might require Sound.valueOf("ENTITY_BREEZE_CHARGE")
            chargeSong.addNote(new Note(Sound.valueOf("ENTITY_BREEZE_CHARGE"), 0, 1, 1.0f, 1.0f));
        } catch (IllegalArgumentException e) {
            // If your server version doesn't have breeze, fallback or ignore
        }

        return chargeSong;
    }

    /**
     * Returns a short "shoot" effect for when the ball is actually launched.
     */
    public static Song getShootSong() {
        Song shootSong = new Song("Shoot", 20);

        // A short chord at tick 0
        shootSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_BASS, 0, 1, 1.2f, 1.0f));

        // The breeze.shoot sound
        try {
            shootSong.addNote(new Note(Sound.valueOf("ENTITY_BREEZE_SHOOT"), 0, 1, 1.0f, 1.0f));
        } catch (IllegalArgumentException e) {
            // Fallback if not present
        }

        return shootSong;
    }

    /**
     * Returns a repeating "spin" or "rattle" pattern that might loop while the wheel is spinning.
     */
    public static Song getSpinSong() {
        Song spinSong = new Song("SpinEffect", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 0, 1, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 4, 5, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 8, 9, 1.0f, 1.0f));
        spinSong.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, 12, 13, 1.0f, 1.0f));
        return spinSong;
    }
}
