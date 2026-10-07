package org.nc.nccasino.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KickClassifierTest {

    @Test
    void paperCausesThatAreNotThePlayersFaultDoNotForfeit() {
        for (String cause : KickClassifier.NON_PUNITIVE_CAUSES) {
            assertFalse(KickClassifier.isPunitive(cause, "anything", null), cause);
        }
    }

    @Test
    void paperPunitiveCausesForfeitWhateverTheReasonSays() {
        assertTrue(KickClassifier.isPunitive("KICK_COMMAND", "Timed out", null));
        assertTrue(KickClassifier.isPunitive("PLUGIN", "", null));
        assertTrue(KickClassifier.isPunitive("BANNED", null, null));
        assertTrue(KickClassifier.isPunitive("FLYING_PLAYER", null, null));
        assertTrue(KickClassifier.isPunitive("SPAM", null, null));
    }

    @Test
    void unknownPaperCauseFallsBackToTheReasonText() {
        assertFalse(KickClassifier.isPunitive("UNKNOWN", "Timed out", null));
        assertTrue(KickClassifier.isPunitive("UNKNOWN", "Kicked by an operator", null));
    }

    @Test
    void spigotVanillaReasonsThatAreNotThePlayersFaultDoNotForfeit() {
        assertFalse(KickClassifier.isPunitive(null, "Timed out", null));
        assertFalse(KickClassifier.isPunitive(null, "§cTimed out", null));
        assertFalse(KickClassifier.isPunitive(null, "You have been idle for too long!", null));
        assertFalse(KickClassifier.isPunitive(null, "You logged in from another location", null));
        assertFalse(KickClassifier.isPunitive(null, "Server closed", null));
        assertFalse(KickClassifier.isPunitive(null, "This server requires a custom resource pack", null));
    }

    @Test
    void spigotRestartMessageDoesNotForfeit() {
        assertFalse(KickClassifier.isPunitive(null, "Server is restarting", "Server is restarting"));
        assertFalse(KickClassifier.isPunitive(null, "&6Back in a minute!", "&6Back in a minute!"));
        // Spigot kicks with the configured message's "\n" already turned into a line break.
        assertFalse(KickClassifier.isPunitive(null, "§cRestarting\nBack soon", "&cRestarting\\nBack soon"));
    }

    @Test
    void anythingElseOnSpigotForfeits() {
        assertTrue(KickClassifier.isPunitive(null, "Kicked by an operator", "Server is restarting"));
        assertTrue(KickClassifier.isPunitive(null, "Flying is not enabled on this server", null));
        assertTrue(KickClassifier.isPunitive(null, "You timed out of my patience", null));
        assertTrue(KickClassifier.isPunitive(null, "", null));
        assertTrue(KickClassifier.isPunitive(null, null, null));
    }
}
