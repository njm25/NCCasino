package org.nc.nccasino.budget;

/**
 * Why a commitment was accepted or refused.
 *
 * <p>The reasons are kept apart deliberately: they mean genuinely different
 * things to a player and require different action from an administrator.
 * "This dealer will never take a wager this size" and "this dealer cannot take
 * it right now" must not collapse into one message, or a player will keep
 * retrying a wager that can never succeed, or give up on one that will work in
 * a minute.
 */
public enum AdmissionDecision {

    /** The dealer can cover the worst case; the commitment may proceed. */
    ADMITTED,

    /**
     * Permanent for this dealer as configured: the worst-case house loss
     * exceeds what the underwriting baseline underwrites for a single round.
     * A larger balance does not change this -- the baseline and the
     * guaranteed-worst-case-rounds setting do.
     */
    EXCEEDS_RISK_TIER,

    /**
     * Temporary: the baseline underwrites this commitment, but the dealer's
     * unreserved funds cannot currently cover it. The same wager becomes
     * available again when the balance recovers, and the paytable never
     * changes to compensate.
     */
    INSUFFICIENT_FUNDS,

    /**
     * The numbers themselves are unusable -- negative, absent, non-finite, or
     * beyond {@link Money#MAX}. Always a bug or a bad configuration value, and
     * never something a player can fix by wagering differently.
     */
    NUMERIC_LIMIT,

    /**
     * The dealer's budget configuration is invalid, so no commitment can be
     * safely underwritten. Fails closed and asks for an administrator, rather
     * than guessing at a baseline.
     */
    CONFIGURATION_INVALID,

    /**
     * The dealer could afford it, but the economic record could not be
     * written. Distinct from every reason above because nothing is wrong with
     * the wager or the dealer's funding -- the disk is. Retrying may work; the
     * commitment must not proceed meanwhile, because an unpersisted
     * reservation would be forgotten by a restart while the game played on.
     */
    PERSISTENCE_FAILED;

    public boolean isAdmitted() {
        return this == ADMITTED;
    }

    /** Whether retrying the identical commitment later could succeed. */
    public boolean isTemporary() {
        return this == INSUFFICIENT_FUNDS || this == PERSISTENCE_FAILED;
    }
}
