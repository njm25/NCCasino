package org.nc.nccasino.helpers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A player who turned Sound off must hear nothing from NCCasino. The setting
 * is only checked inside {@link SoundHelper#getSoundSafely}, so every
 * {@code playSound} call has to be preceded by that check (or a direct read
 * of the sound setting) somewhere in its own method -- a call that skips it
 * plays for everyone, muted or not.
 */
class SoundPreferenceGuardTest {

    /** The start of a method declaration: modifiers, a return type, a name and "(". */
    private static final Pattern SIGNATURE = Pattern.compile(
        "^\\s*(?:(?:public|protected|private|static|final|synchronized|abstract|default)\\s+)*"
            + "(?:<[^>]*>\\s+)?[\\w.]+(?:<[^>()]*>)?(?:\\[\\])*\\s+\\w+\\s*\\(.*");
    /** Statements that can look like one: control flow, assignments, anything ending in ";". */
    private static final Pattern STATEMENT = Pattern.compile(
        "^\\s*(?:return|new|throw|if|for|while|switch|catch|else|try|do|case|assert|yield)\\b.*|.*[=;].*");
    private static final Pattern GUARD = Pattern.compile("getSoundSafely\\(|getSoundSetting\\(\\)");

    @Test
    void everySoundChecksThePlayersSoundSetting() throws IOException {
        List<String> unguarded = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.contains(".playSound(") || line.startsWith("//") || line.startsWith("*")) {
                        continue;
                    }
                    if (!guardedWithinMethod(lines, i)) {
                        unguarded.add(file.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(unguarded.isEmpty(), "playSound without a Sound-setting check: " + unguarded);
    }

    /** Whether a guard appears between the nearest enclosing method declaration and this call. */
    private static boolean guardedWithinMethod(List<String> lines, int callLine) {
        for (int i = callLine; i >= 0; i--) {
            String line = lines.get(i);
            if (GUARD.matcher(line).find()) {
                return true;
            }
            if (i < callLine && SIGNATURE.matcher(line).matches() && !STATEMENT.matcher(line).matches()) {
                return false;
            }
        }
        return false;
    }
}
