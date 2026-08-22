package org.nc.nccasino.games.Blackjack;

import java.util.Optional;

/**
 * Pure parser for the admin max-hands chat prompt (see
 * {@code BlackjackMenu#handleMaxHandsInput}) -- accepts case-insensitive
 * {@code "unbounded"}, or an integer &gt;= 2, and returns exactly the
 * string that should be persisted to config (mirroring
 * {@link BlackjackMaxHands#configValue()}'s own shape). Never throws: an
 * overflowing digit string (more digits than fit in a {@code long}) is
 * rejected the same as any other invalid input, not left to propagate an
 * uncaught {@link NumberFormatException} out of the async chat handler.
 */
public final class BlackjackMaxHandsInputParser {

    private BlackjackMaxHandsInputParser() {
    }

    /** @return the config value to persist ("UNBOUNDED" or the integer as a string), or empty if {@code input} is not valid */
    public static Optional<String> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if ("unbounded".equalsIgnoreCase(trimmed)) {
            return Optional.of("UNBOUNDED");
        }
        if (!trimmed.matches("\\d+")) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(trimmed);
            if (value < 2) {
                return Optional.empty();
            }
            return Optional.of(String.valueOf(value));
        } catch (NumberFormatException overflow) {
            return Optional.empty();
        }
    }
}
