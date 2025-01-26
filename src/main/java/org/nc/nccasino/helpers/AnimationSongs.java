package org.nc.nccasino.helpers;

import org.nc.VSE.*;

import java.util.HashMap;
import java.util.Map;

public class AnimationSongs {

    private static final Map<String, Song> animationSongMap = new HashMap<>();

    static {
        // Register animations per game
        animationSongMap.put("NCCasino - Roulette", getRouletteIntro());
        animationSongMap.put("NCCasino - Blackjack", getBlackjackIntro());
        animationSongMap.put("NCCasino - Mines", getMinesIntro());
    }

    public static Song getAnimationSong(String gameType) {
        return animationSongMap.getOrDefault(gameType, getDefaultIntro());
    }

    private static Song getRouletteIntro() {
        Song song = new Song("RouletteIntro", 20);
        song.addNote(new Note("minecraft:music_disc.cat", 0, 0, 2f, 5f));
        return song;
    }

    private static Song getBlackjackIntro() {
        Song song = new Song("BlackjackIntro", 20);
        song.addNote(new Note("minecraft:music_disc.cat", 0, 0, 2f, 5f));
        return song;
    }

    private static Song getMinesIntro() {
        Song song = new Song("MinesIntro", 20);
        song.addNote(new Note("minecraft:music_disc.cat", 0, 0, 2f, 5f));
        return song;
    }

    private static Song getDefaultIntro() {
        Song song = new Song("DefaultIntro", 20);
        song.addNote(new Note("minecraft:music_disc.cat", 0, 0, 2f, 5f));
        return song;
    }
}

