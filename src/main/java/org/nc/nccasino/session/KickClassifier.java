package org.nc.nccasino.session;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a kick should cost the player their in-flight game.
 *
 * <p>Every game forfeits on {@link ExitReason#KICKED}, which is right for a
 * kick that is a judgement about the player -- {@code /kick}, a ban, an
 * anti-cheat or moderation plugin. But the server also routes plenty of
 * disconnects that are not the player's fault through the kick path: a
 * keep-alive timeout on a laggy connection, the vanilla AFK kick, logging in
 * from a second client, {@code /restart}. Treating those as kicks would take
 * a committed win away from a player whose Wi-Fi hiccuped. They are classified
 * as ordinary disconnects instead, so each game's normal disconnect policy
 * (honor the committed result, refund, or ride it out) applies.
 *
 * <p>Paper reports a structured cause, used when available. Spigot only gives
 * the reason text, which for these server-originated kicks is the vanilla
 * message rendered in English, plus the restart message from spigot.yml.
 */
public final class KickClassifier {

    /** Paper {@code PlayerKickEvent.Cause} names that are not a judgement about the player. */
    static final Set<String> NON_PUNITIVE_CAUSES = Set.of(
        "TIMEOUT",
        "IDLING",
        "DUPLICATE_LOGIN",
        "RESTART_COMMAND",
        "RESOURCE_PACK_REJECTION"
    );

    /** The same kicks as Spigot reports them: vanilla's English messages, normalized. */
    static final List<String> NON_PUNITIVE_REASONS = List.of(
        "timed out",
        "you have been idle for too long",
        "you logged in from another location",
        "server closed",
        "this server requires a custom resource pack"
    );

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[§&][0-9A-FK-ORX]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private KickClassifier() {
    }

    /**
     * @param paperCause the Paper kick cause's enum name, or {@code null} on Spigot
     * @param reason the kick reason text
     * @param restartMessage spigot.yml's {@code messages.restart}, or {@code null}
     * @return {@code true} when the kick should forfeit like any other kick
     */
    public static boolean isPunitive(String paperCause, String reason, String restartMessage) {
        if (paperCause != null && !paperCause.equals("UNKNOWN")) {
            return !NON_PUNITIVE_CAUSES.contains(paperCause);
        }
        String normalized = normalize(reason);
        if (normalized.isEmpty()) {
            return true;
        }
        String restart = normalize(restartMessage);
        if (!restart.isEmpty() && normalized.equals(restart)) {
            return false;
        }
        for (String nonPunitive : NON_PUNITIVE_REASONS) {
            if (normalized.equals(nonPunitive)) {
                return false;
            }
        }
        return true;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        // spigot.yml stores a line break as a literal "\n", which Spigot turns
        // into a real one before kicking; compare both as plain spaces.
        String flattened = WHITESPACE.matcher(text.replace("\\n", "\n")).replaceAll(" ");
        String stripped = COLOR_CODES.matcher(flattened).replaceAll("").trim().toLowerCase(Locale.ROOT);
        while (!stripped.isEmpty() && ".!".indexOf(stripped.charAt(stripped.length() - 1)) >= 0) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }
}
