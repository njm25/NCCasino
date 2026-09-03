package org.nc.nccasino.games.Slots;

import java.text.Normalizer;

/**
 * The profile-name rules, as a pure validator so the chat prompt and the
 * store can never disagree about what a legal name is.
 *
 * <p>A name is 1-24 characters of letters (any script -- Unicode's own
 * letter classification, not just A-Z), digits, spaces, hyphens and
 * underscores. Everything else is rejected -- most importantly the section
 * sign and ampersand, so a saved profile can never smuggle a formatting code
 * into an item name, and a name can never be crafted to impersonate one of
 * the machine's own controls. Emoji and other symbol characters are rejected
 * the same way: they are not letters under Unicode's own classification
 * either, so nothing about accepting every script also opens the door to
 * pictographic names.
 *
 * <p>Every name is folded to Unicode Normalization Form C ({@link Normalizer.Form#NFC})
 * before anything else looks at it. Some input paths (certain IMEs and
 * keyboard layouts, particularly for languages built on combining marks --
 * Vietnamese chief among them) can hand this class an accented letter as two
 * code points, a base letter plus a separate combining mark, rather than one
 * precomposed character. A bare combining mark is not a letter on its own
 * ({@link Character#isLetter(int)} correctly says so), so without this
 * normalization a perfectly ordinary accented name typed on some keyboards
 * would be rejected while the same name typed on another keyboard would not
 * -- an inconsistency with no visible cause from the player's side. NFC is
 * also what makes {@link #uniquenessKey} reliable: two visually identical
 * names must fold to the same key regardless of which composed form either
 * one arrived in.
 *
 * <p>Uniqueness is case-insensitive: "High Roller" and "high roller" are the
 * same profile, and saving the second offers to overwrite the first rather
 * than silently creating a confusing near-duplicate. Case-folding is
 * script-aware ({@link String#toLowerCase(java.util.Locale)}), the same
 * mechanism already relied on for every script this validator accepts.
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
        String trimmed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        if (trimmed.length() < MIN_LENGTH) {
            return Rejection.EMPTY;
        }
        if (trimmed.length() > MAX_LENGTH) {
            return Rejection.TOO_LONG;
        }
        // Walked by code point, not by char: a surrogate pair encoding one
        // real letter (rare CJK extension ideographs, for instance) must be
        // classified as the one letter it is, never as two lone, illegal
        // surrogate halves.
        for (int i = 0; i < trimmed.length(); ) {
            int codePoint = trimmed.codePointAt(i);
            boolean allowed = Character.isLetter(codePoint)
                || Character.isDigit(codePoint)
                || codePoint == ' '
                || codePoint == '-'
                || codePoint == '_';
            if (!allowed) {
                return Rejection.ILLEGAL_CHARACTERS;
            }
            i += Character.charCount(codePoint);
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return validate(raw) == null;
    }

    /**
     * The canonical stored form of a legal name: NFC-folded, trimmed, with
     * runs of inner spaces collapsed so two names that are visually identical
     * cannot both exist.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).replaceAll(" {2,}", " ");
    }

    /** The case-insensitive key two names must share to be considered the same profile. */
    public static String uniquenessKey(String raw) {
        String normalized = normalize(raw);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
