package org.nc.nccasino.games.Slots;

/**
 * The profile-name rules, as a pure validator so the chat prompt and the
 * store can never disagree about what a legal name is.
 *
 * <p>A name is 1-24 characters of letters, digits, spaces, hyphens and
 * underscores. Everything else is rejected -- most importantly the section
 * sign and ampersand, so a saved profile can never smuggle a formatting code
 * into an item name, and a name can never be crafted to impersonate one of
 * the machine's own controls.
 *
 * <p>Uniqueness is case-insensitive: "High Roller" and "high roller" are the
 * same profile, and saving the second offers to overwrite the first rather
 * than silently creating a confusing near-duplicate.
 */
public final class SlotsProfileName {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 24;

    private SlotsProfileName() {
    }

    /** Why a proposed name was rejected. */
    public enum Rejection {
        EMPTY,
        TOO_LONG,
        ILLEGAL_CHARACTERS;

        /** This rejection's {@code slots.profile-name-*} localization key. */
        public String messageKey() {
            return "slots.profile-name-" + name().toLowerCase().replace('_', '-');
        }
    }

    /** @return the rejection for {@code raw}, or {@code null} if it is a legal name */
    public static Rejection validate(String raw) {
        if (raw == null) {
            return Rejection.EMPTY;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_LENGTH) {
            return Rejection.EMPTY;
        }
        if (trimmed.length() > MAX_LENGTH) {
            return Rejection.TOO_LONG;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == ' '
                || c == '-'
                || c == '_';
            if (!allowed) {
                return Rejection.ILLEGAL_CHARACTERS;
            }
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return validate(raw) == null;
    }

    /**
     * The canonical stored form of a legal name: trimmed, with runs of inner
     * spaces collapsed so two names that are visually identical cannot both
     * exist.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().replaceAll(" {2,}", " ");
    }

    /** The case-insensitive key two names must share to be considered the same profile. */
    public static String uniquenessKey(String raw) {
        String normalized = normalize(raw);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
