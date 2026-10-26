package org.nc.nccasino.entities;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A hand-edited "game: roulette" opened Blackjack (the game switch is
 * case-sensitive) while the permission check, which lower-cases, asked for the
 * Roulette permission: a player denied Blackjack could play it there.
 */
class DealerGameNameTest {

    private MockedStatic<JavaPlugin> javaPluginStatic;

    @BeforeEach
    void setUp() {
        // Dealer's static keys need a providing plugin outside a real server.
        JavaPlugin providingPlugin = mock(JavaPlugin.class);
        when(providingPlugin.getName()).thenReturn("NCCasino");
        javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);
        javaPluginStatic.when(() -> JavaPlugin.getProvidingPlugin(any())).thenReturn(providingPlugin);
    }

    @AfterEach
    void tearDown() {
        javaPluginStatic.close();
    }

    @Test
    void anyCaseNamesTheGameThatActuallyOpens() {
        assertEquals("Roulette", Dealer.canonicalGameName("roulette"));
        assertEquals("Rock Paper Scissors", Dealer.canonicalGameName("ROCK PAPER SCISSORS"));
        assertEquals("Dragon Descent", Dealer.canonicalGameName(" dragon descent "));
    }

    @Test
    void anUnknownNameIsLeftAloneSoThePermissionCheckRefusesIt() {
        assertEquals("Menu", Dealer.canonicalGameName("Menu"));
        assertEquals("Poker", Dealer.canonicalGameName("Poker"));
    }
}
