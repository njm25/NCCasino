package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * The pure decision: may this dealer accept this commitment right now?
 *
 * <p>Four genuinely separate questions are asked in a fixed order, and the
 * first failure is what the caller is told. The order is chosen so the answer
 * is the most actionable one available:
 *
 * <ol>
 *   <li><b>Numbers usable at all?</b> Garbage in is never a gameplay message.
 *   <li><b>Risk policy understandable?</b> A LIMITED dealer with no valid
 *       baseline fails closed rather than underwriting on a guess.
 *   <li><b>Within the baseline's risk tier?</b> Derived from the fixed
 *       underwriting baseline, so it does not shrink as the dealer loses.
 *   <li><b>Affordable from unreserved funds right now?</b> The final hard
 *       check. A dealer never pays out of money it has already promised.
 * </ol>
 *
 * <p>Tier before funding matters: a wager that is permanently too large should
 * say so, not report a temporary shortage that will never resolve.
 */
public final class AdmissionPolicy {

    private AdmissionPolicy() {
    }

    /**
     * @param available the dealer's currently unreserved balance -- live
     *     balance minus everything already promised to commitments in flight
     * @return the first failing check, or {@link AdmissionDecision#ADMITTED}
     */
    public static AdmissionDecision admit(
        DealerBudgetSettings settings,
        BigDecimal available,
        Exposure exposure
    ) {
        if (settings == null) {
            return AdmissionDecision.CONFIGURATION_INVALID;
        }
        // An unlimited dealer answers before touching any of the arithmetic
        // below: this is the default for every existing dealer and runs on
        // every wager, so it must stay free.
        if (settings.mode() == DealerBudgetMode.UNLIMITED) {
            return AdmissionDecision.ADMITTED;
        }

        if (exposure == null || !exposure.isNumericallySafe() || !Money.isSafe(available)) {
            return AdmissionDecision.NUMERIC_LIMIT;
        }
        if (!settings.isUsable()) {
            return AdmissionDecision.CONFIGURATION_INVALID;
        }

        BigDecimal houseLoss = exposure.maxHouseLoss();

        // A commitment that cannot cost the house anything (the payout never
        // exceeds the stake) is always admissible -- refusing it would deny a
        // wager the dealer provably cannot lose money on.
        if (Money.isZero(houseLoss)) {
            return AdmissionDecision.ADMITTED;
        }

        if (!Money.atLeast(settings.maxHouseLossPerRound(), houseLoss)) {
            return AdmissionDecision.EXCEEDS_RISK_TIER;
        }
        if (!Money.atLeast(available, houseLoss)) {
            return AdmissionDecision.INSUFFICIENT_FUNDS;
        }
        return AdmissionDecision.ADMITTED;
    }

    /**
     * The largest worst-case house loss this dealer could underwrite right
     * now: the tighter of its fixed risk tier and its unreserved funds.
     *
     * <p>Used to answer "which of the configured wager denominations can this
     * dealer offer?" without trial-and-error. Note what it is <em>not</em>
     * used for: inventing a new, smaller wager. Denominations stay fixed and
     * simply become unavailable, per section 17 of the design.
     */
    public static BigDecimal headroom(DealerBudgetSettings settings, BigDecimal available) {
        if (settings == null) {
            return Money.ZERO;
        }
        if (settings.mode() == DealerBudgetMode.UNLIMITED) {
            return Money.MAX;
        }
        if (!settings.isUsable() || !Money.isSafe(available)) {
            return Money.ZERO;
        }
        return Money.min(settings.maxHouseLossPerRound(), Money.clampNonNegative(available));
    }

    /**
     * Whether a denomination is offerable, ignoring the current balance.
     *
     * <p>Separated from {@link #admit} so a menu can distinguish "this dealer
     * does not do stakes this size" from "not right now" without pretending to
     * know the live balance at render time.
     */
    public static boolean withinRiskTier(DealerBudgetSettings settings, Exposure exposure) {
        if (settings == null || exposure == null) {
            return false;
        }
        if (settings.mode() == DealerBudgetMode.UNLIMITED) {
            return true;
        }
        if (!exposure.isNumericallySafe() || !settings.isUsable()) {
            return false;
        }
        return Money.atLeast(settings.maxHouseLossPerRound(), exposure.maxHouseLoss());
    }
}
