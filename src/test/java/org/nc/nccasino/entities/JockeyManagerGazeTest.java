package org.nc.nccasino.entities;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JockeyManagerGazeTest {
    @Test
    void aDealerLooksAtAPlayersEyesNotTheirFeet() {
        // A villager-height dealer and a player on the same floor, 3 blocks apart.
        Location dealerEyes = new Location(null, 0, 64 + 1.62, 0);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 3));
        when(player.getEyeLocation()).thenReturn(new Location(null, 0, 64 + 1.62, 3));

        assertEquals(0f, JockeyManager.pitchToward(dealerEyes, JockeyManager.gazePoint(player)), 0.01f);
    }

    @Test
    void positivePitchLooksDownAndNegativeLooksUp() {
        Location eyes = new Location(null, 0, 10, 0);
        assertEquals(45f, JockeyManager.pitchToward(eyes, new Location(null, 0, 8, 2)), 0.01f);
        assertEquals(-45f, JockeyManager.pitchToward(eyes, new Location(null, 2, 12, 0)), 0.01f);
    }

    @Test
    void aShortDealerLooksUpAtAStandingPlayer() {
        Location chickenEyes = new Location(null, 0, 64 + 0.64, 0);
        Location playerEyes = new Location(null, 0, 64 + 1.62, 2);
        float pitch = JockeyManager.pitchToward(chickenEyes, playerEyes);
        assertEquals(-26.1f, pitch, 0.1f);
    }
}
