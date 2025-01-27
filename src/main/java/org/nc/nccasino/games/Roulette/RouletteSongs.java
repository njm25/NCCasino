package org.nc.nccasino.games.Roulette;

import org.nc.VSE.Note;
import org.nc.VSE.Song;


public class RouletteSongs {

    public static Song getTimerTick() {
        Song timerTick = new Song("TimerTick", 20);
        timerTick.addNote(new Note("minecraft:block.note_block.hat", 1, 1, 1.0f, 1.0f));
        return timerTick;
    }

    public static Song getBallLaunch() {
        Song BallLaunch = new Song("BallLaunch", 20);

        BallLaunch.addNote(new Note("minecraft:item.lodestone_compass.lock",     1, 1, 4.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:music_disc.creator_music_box",    30, 50, 3.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:block.beacon.activate",           40, 40, 3.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.inhale",            60, 60, 2.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.inhale",            62, 62, 2.0f, 1.2f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.inhale",            64, 64, 2.0f, 1.4f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.inhale",            66, 66, 2.0f, 1.6f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.inhale",            68, 68, 2.0f, 1.8f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.charge",            64, 64, 1.0f, 1.4f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.charge",            66, 66, 1.0f, 1.6f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.charge",            68, 68, 1.0f, 1.8f));
        BallLaunch.addNote(new Note("minecraft:item.crossbow.loading_middle",    70, 70, 3.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.shoot",             80, 80, 2.0f, 0.5f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.shoot",             80, 80, 2.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:entity.breeze.jump",              80, 80, 3.0f, 1.0f));
        BallLaunch.addNote(new Note("minecraft:item.crossbow.shoot",             80, 80, 3.0f, 1.0f));
        return BallLaunch;
    }

    public static Song getDynamicFastTick() {
        Song dynamicFastTick = new Song("DynamicFastTick", 20);
        int totalTicks = 100;
        int currentTick = 0;
        float initialPitch = 1.0f;
        float maxPitch = 2.0f;
        int initialInterval = 10;
        int minInterval = 1;

        while (currentTick < totalTicks) {
            float progress = (float) currentTick / totalTicks;
            float pitch = initialPitch + progress * (maxPitch - initialPitch);

            dynamicFastTick.addNote(new Note("minecraft:block.note_block.hat",
                                             currentTick,
                                             currentTick + 1,
                                             pitch,
                                             1.0f));
            int interval = (int) (initialInterval - progress * (initialInterval - minInterval));
            currentTick += Math.max(interval, minInterval);
        }

        dynamicFastTick.addNote(new Note("minecraft:block.piston.extend", 100, 100, 0.5f, 1.0f));
        return dynamicFastTick;
    }

    public static Song getSpinTick() {
        Song spinTick = new Song("SpinTick", 20);
        spinTick.addNote(new Note("minecraft:item.shield.block", 0, 0, 1.5f, 0.05f));
        return spinTick;
    }


    public static Song getWhoosh(int pitch) {
        Song cornerWhoosh = new Song("CornerWhoosh", 20);
        float adjustedPitch = (pitch * 0.2f);
        cornerWhoosh.addNote(new Note("minecraft:entity.phantom.flap", 0, 0, adjustedPitch, 10f));
        return cornerWhoosh;
    }

    public static Song getSkibidi(int pitch) {
        Song skibidi = new Song("skibidi", 20);
        float adjustedPitch = (pitch * 0.2f);
        skibidi.addNote(new Note("minecraft:item.crossbow.loading_start", 0, 0, adjustedPitch, 2f));
        return skibidi;
    }

    public static Song getBallScraping(int pitch) {
        Song ballScraping = new Song("BallScraping", 20);
        float adjustedPitch = (pitch * 0.2f);
        ballScraping.addNote(new Note("minecraft:block.grindstone.use", 0, 0, adjustedPitch, 0.2f));
        return ballScraping;
    }

    public static Song getSlowSpinTick() {
        Song slowSpinTick = new Song("SlowSpinTick", 20);
        slowSpinTick.addNote(new Note("minecraft:item.shield.block", 1,1, 1.5f, 0.05f));
        return slowSpinTick;
    }

    public static Song getFinalSpot() {
        Song finalSpot = new Song("FinalSpot", 20);
        finalSpot.addNote(new Note("minecraft:block.creaking_heart.spawn", 1,1, 1, 1f));
        return finalSpot;
    }

    public static Song getDrumroll() {
        Song drumroll = new Song("Drumroll", 20);
        int tick = 0;
        for (int i = 0; i < 20; i++) {
            drumroll.addNote(new Note("minecraft:block.note_block.snare", tick, tick, 1.0f, 1.0f));
            tick += 2;
        }
        // final crescendo
        drumroll.addNote(new Note("minecraft:block.note_block.basedrum", tick, tick+1, 1.5f, 1.0f));
        return drumroll;
    }
}
