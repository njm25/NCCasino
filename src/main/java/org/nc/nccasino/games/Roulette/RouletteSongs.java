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

    public static Song getBallLaunch() {
        Song BallLaunch = new Song("BallLaunch", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        BallLaunch.addNote(new Note(Sound.ITEM_LODESTONE_COMPASS_LOCK, 1, 1, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.MUSIC_DISC_CREATOR_MUSIC_BOX, 30, 80, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.BLOCK_BEACON_ACTIVATE, 40, 40, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 60,60, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 62,62, 1.0f, 1.2f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 64,64, 1.0f, 1.4f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 66,66, 1.0f, 1.6f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 68,68, 1.0f, 1.8f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 64,64, 1.0f, 1.4f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 66,66, 1.0f, 1.6f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 68,68, 1.0f, 1.8f));
        BallLaunch.addNote(new Note(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 70,70, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_SHOOT, 80,80, 1.0f, 0.5f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_SHOOT, 80,80, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_JUMP, 80,80, 1.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ITEM_CROSSBOW_SHOOT, 80,80, 1.0f, 1.0f));
        return BallLaunch;
    }
    public static Song getDynamicFastTick() {
        Song dynamicFastTick = new Song("DynamicFastTick", 20); // 20 ticks per second
        int totalTicks = 100; // Total duration of the song (5 seconds * 20 ticks/sec)
        int currentTick = 0; // Start at tick 0
        float initialPitch = 1.0f;
        float maxPitch = 2.0f; // Maximum pitch
        int initialInterval = 10; // Start with 10 ticks between notes
        int minInterval = 1; // Minimum interval between notes (closer notes as the song progresses)
    
        while (currentTick < totalTicks) {
            // Calculate pitch progression: linearly increase from initialPitch to maxPitch
            float progress = (float) currentTick / totalTicks;
            float pitch = initialPitch + progress * (maxPitch - initialPitch);
    
            // Add the note to the song
            dynamicFastTick.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_HAT, currentTick, currentTick + 1, pitch, 1.0f));
    
            // Decrease the interval as we approach the end of the song
            int interval = (int) (initialInterval - progress * (initialInterval - minInterval));
            currentTick += Math.max(interval, minInterval); // Ensure interval doesn't go below minInterval
        }
        dynamicFastTick.addNote(new Note(Sound.BLOCK_PISTON_EXTEND, 100, 100, 0.5f, 1.0f));
        return dynamicFastTick;
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
