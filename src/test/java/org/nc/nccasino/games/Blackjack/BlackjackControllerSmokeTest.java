package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Confirms the harness itself works before it's trusted for the real regression suite. */
class BlackjackControllerSmokeTest {

    @Test
    void harnessConstructsAControllerAndSeatsAPlayer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            assertNotNull(h.inventory);
            Player p = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(p, BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertEquals(1, h.inventory.playerSeatsSizeForTest());
        }
    }
}
