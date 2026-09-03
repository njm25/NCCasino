package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Pins the opening animation's fixed vertical-rainbow filler contract. */
class SlotsOpeningFillerTest {

    private static final Material[] EXPECTED_ORDER = {
        Material.RED_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
    };

    @Test
    void fixedRainbowHasTheExactApprovedNineColorOrder() {
        Material[] sequence = SlotsOpeningFiller.fixedRainbowSequence();
        assertEquals(9, sequence.length);
        assertEquals(SlotsOpeningFiller.FIXED_RAINBOW_LENGTH, sequence.length);
        assertArrayEquals(EXPECTED_ORDER, sequence);
    }

    @Test
    void fixedRainbowContainsNoRandomFillerOrDuplicateColors() {
        Material[] sequence = SlotsOpeningFiller.fixedRainbowSequence();
        Set<Material> unique = new HashSet<>(Arrays.asList(sequence));
        assertEquals(sequence.length, unique.size());
        assertFalse(unique.contains(Material.WHITE_STAINED_GLASS_PANE));
        assertFalse(unique.contains(Material.GRAY_STAINED_GLASS_PANE));
        assertFalse(unique.contains(Material.GREEN_STAINED_GLASS_PANE));
        assertFalse(unique.contains(Material.MAGENTA_STAINED_GLASS_PANE));
    }

    @Test
    void fixedRainbowIsDeterministicAndDefensivelyCopied() {
        Material[] first = SlotsOpeningFiller.fixedRainbowSequence();
        Material[] second = SlotsOpeningFiller.fixedRainbowSequence();
        assertArrayEquals(first, second);
        first[0] = Material.BLACK_STAINED_GLASS_PANE;
        assertSame(Material.RED_STAINED_GLASS_PANE, SlotsOpeningFiller.fixedRainbowSequence()[0]);
    }
}
