package org.nc.nccasino.payout;

/**
 * What a click on the overflow preference control does.
 *
 * <p>Extracted from the preferences menu so the two rules that actually matter
 * can be tested without a server, a player, or an inventory:
 *
 * <ol>
 *   <li>While the server forces a mode, a click must not change the effective
 *       behavior; and
 *   <li>it must not overwrite the player's stored choice either -- that choice
 *       has to come back intact when the server returns to
 *       {@link OverflowMode#PLAYER_CHOICE}.
 * </ol>
 *
 * <p>The second rule is the subtle one. Writing the forced value into the
 * player's own setting would look identical while forced and only reveal
 * itself much later, as a silently changed preference.
 */
public final class OverflowPreferenceToggle {

    private OverflowPreferenceToggle() {
    }

    /**
     * The outcome of a click.
     *
     * @param storedChoice what the player's saved preference should be
     *     afterwards; {@code null} means they still have never chosen
     * @param accepted whether the click actually changed anything. A refused
     *     click is the forced-mode case and is worth telling the player about.
     */
    public record Result(OverflowPreference storedChoice, boolean accepted) {
    }

    /** Whether the server, rather than the player, decides the effective value. */
    public static boolean isForced(OverflowSettings settings) {
        return settings == null || settings.mode() != OverflowMode.PLAYER_CHOICE;
    }

    /**
     * @param storedChoice the player's saved preference, or {@code null} if
     *     they have never chosen
     * @return the new stored choice and whether the click was accepted. While
     *     forced, {@code storedChoice} is returned completely untouched --
     *     including {@code null}, so a player who never chose does not acquire
     *     a choice merely by clicking a control they cannot use.
     */
    public static Result toggle(OverflowSettings settings, OverflowPreference storedChoice) {
        if (isForced(settings)) {
            return new Result(storedChoice, false);
        }
        // Toggle away from what is currently in force, so the first click on an
        // inherited default moves off that default rather than re-selecting it.
        OverflowPreference effective = settings.effectivePreference(storedChoice);
        return new Result(
            effective == OverflowPreference.BANK ? OverflowPreference.DROP : OverflowPreference.BANK,
            true);
    }
}
