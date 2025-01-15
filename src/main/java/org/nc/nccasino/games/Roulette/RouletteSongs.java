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
        BallLaunch.addNote(new Note(Sound.ITEM_LODESTONE_COMPASS_LOCK, 1, 1, 4.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.MUSIC_DISC_CREATOR_MUSIC_BOX, 30, 50, 3.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.BLOCK_BEACON_ACTIVATE, 40, 40, 3.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 60,60, 2.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 62,62, 2.0f, 1.2f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 64,64, 2.0f, 1.4f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 66,66, 2.0f, 1.6f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_INHALE, 68,68, 2.0f, 1.8f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 64,64, 1.0f, 1.4f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 66,66, 1.0f, 1.6f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_CHARGE, 68,68, 1.0f, 1.8f));
        BallLaunch.addNote(new Note(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 70,70, 3.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_SHOOT, 80,80, 2.0f, 0.5f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_SHOOT, 80,80, 2.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ENTITY_BREEZE_JUMP, 80,80, 3.0f, 1.0f));
        BallLaunch.addNote(new Note(Sound.ITEM_CROSSBOW_SHOOT, 80,80, 3.0f, 1.0f));
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
    
    public static Song getSpinTick() {
        Song spinTick = new Song("SpinTick", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        spinTick.addNote(new Note(Sound.ITEM_SHIELD_BLOCK, 0,0, 1.5f, .05f));
        return spinTick;
    }

    public static Song getEarlyWhoosh() {
        Song earlyWhoosh = new Song("EarlyWhoosh", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        earlyWhoosh.addNote(new Note(Sound.ENTITY_PHANTOM_FLAP, 0,0, 1f, 10f));
        return earlyWhoosh;
    }

    public static Song getCornerWhoosh() {
        Song cornerWhoosh = new Song("CornerWhoosh", 20);
        // We'll do a short repeating pattern for 16 ticks: a "tick" every 4 ticks
        cornerWhoosh.addNote(new Note(Sound.ENTITY_PHANTOM_FLAP, 0,0, .5f, 10f));
        return cornerWhoosh;
    }


    public static Song getSkibidi(int pitch) {
        Song skibidi = new Song("skibidi", 20); // 20 ticks per second
        float adjustedPitch = (pitch * 0.2f); 
        skibidi.addNote(new Note(Sound.ITEM_CROSSBOW_LOADING_START, 0, 0, adjustedPitch, 2f));
        return skibidi;
    }

    public static Song getBallScraping(int pitch) {
        Song ballScraping = new Song("BallScraping", 20); // 20 ticks per second
        // Adjust the pitch dynamically based on speed
        float adjustedPitch = (pitch * 0.2f); // Scale pitch based on input
        // A continuous scraping sound with dynamic pitch
        ballScraping.addNote(new Note(Sound.BLOCK_GRINDSTONE_USE, 0, 0, adjustedPitch, .2f));
        return ballScraping;
    }
    
    public static Song getSlowSpinTick() {
        Song slowSpinTick = new Song("SlowSpinTick", 20);
         slowSpinTick.addNote(new Note(Sound.ITEM_SHIELD_BLOCK, 1,1, 1.5f, .05f));
        return slowSpinTick;
    }

    public static Song getFinalSpot() {
        Song finalSpot= new Song("FinalSpot", 20);
        finalSpot.addNote(new Note(Sound.BLOCK_CREAKING_HEART_SPAWN, 1,1, 1, 1f));
        return finalSpot;
    }

    public static Song getDrumroll() {
        Song drumroll = new Song("Drumroll", 20); // 20 ticks per second
        int tick = 0;
        for (int i = 0; i < 20; i++) { // 10 drum beats
            drumroll.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_SNARE, tick, tick, 1.0f, 1.0f));
            tick += 2; // Slight pause between beats
        }
        // Final crescendo
        drumroll.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, tick, tick + 1, 1.5f, 1.0f));
        return drumroll;
    }
    
    
}
