package org.nc.nccasino.helpers;

import org.bukkit.Sound;
import org.nc.VSE.*;

import java.util.HashMap;
import java.util.Map;

public class AnimationSongs {

    private static final Map<String, Song> animationSongMap = new HashMap<>();

    static {
        // Register animations per game
        animationSongMap.put("Roulette", getRouletteIntro());
        animationSongMap.put("Blackjack", getBlackjackIntro());
        animationSongMap.put("Mines", getMinesIntro());
    }

    public static Song getAnimationSong(String gameType) {
        return animationSongMap.getOrDefault(gameType, getDefaultIntro());
    }

    private static Song getRouletteIntro() {
        Song song = new Song("RouletteIntro", 20);
        song.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_BELL, 0, 2, 1.0f, 1.2f));
        song.addNote(new Note(Sound.ENTITY_PLAYER_LEVELUP, 10, 12, 0.8f, 1.5f));
        return song;
    }

    private static Song getBlackjackIntro() {
        Song song = new Song("BlackjackIntro", 20);
        song.addNote(new Note(Sound.ITEM_BOOK_PAGE_TURN, 0, 2, 1.0f, 1.0f));
        song.addNote(new Note(Sound.BLOCK_ANVIL_USE, 10, 12, 0.8f, 1.2f));
        return song;
    }

    private static Song getMinesIntro() {
        Song song = new Song("MinesIntro", 20);
        song.addNote(new Note(Sound.ENTITY_VILLAGER_YES, 0, 2, 1.0f, 1.1f));
        song.addNote(new Note(Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 10, 12, 0.8f, 1.3f));
        return song;
    }

    private static Song getDefaultIntro() {
        Song song = new Song("DefaultIntro", 20);
        song.addNote(new Note(Sound.BLOCK_NOTE_BLOCK_PLING, 0, 2, 1.0f, 1.0f));
        return song;
    }
}
