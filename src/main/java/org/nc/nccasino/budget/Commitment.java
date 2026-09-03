package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * The result of asking a dealer to take on a commitment.
 *
 * <p>This type exists to remove an ambiguity that would otherwise be a
 * money bug waiting to happen. If accepting a commitment simply returned a
 * nullable {@link Reservation}, {@code null} would mean two opposite things:
 * "this dealer is unlimited, carry on" and "refused, do not take the wager".
 * A caller that read one as the other would either block every wager on an
 * ordinary server or let a limited dealer accept exposure it cannot cover.
 *
 * <p>So the three cases are named:
 *
 * <ul>
 *   <li>{@link #forUnlimitedDealer()} -- no budget applies; proceed, settle nothing.
 *   <li>{@link #accepted} -- reserved; proceed, and settle exactly once.
 *   <li>anything else -- refused, with a reason; nothing has changed.
 * </ul>
 *
 * @param reservation the promise to settle later, or {@code null} for an
 *     unlimited dealer and for every refusal
 */
public record Commitment(AdmissionDecision decision, Reservation reservation, boolean unlimited) {

    /** A dealer with no budget constraints: nothing was reserved, nothing needs settling. */
    public static Commitment forUnlimitedDealer() {
        return new Commitment(AdmissionDecision.ADMITTED, null, true);
    }

    public static Commitment accepted(Reservation reservation) {
        return new Commitment(AdmissionDecision.ADMITTED, reservation, false);
    }

    /** A limited-dealer commitment that was durably closed by an adjustment. */
    public static Commitment released() {
        return new Commitment(AdmissionDecision.ADMITTED, null, false);
    }

    public static Commitment refused(AdmissionDecision decision) {
        return new Commitment(decision, null, false);
    }

    /**
     * Whether the game may proceed. True for an unlimited dealer and for an
     * accepted reservation alike -- which is the point: an integrated game
     * branches on this one method and behaves correctly in both modes.
     */
    public boolean isAccepted() {
        return decision.isAdmitted();
    }

    /** Whether there is a reservation that must eventually be settled exactly once. */
    public boolean requiresSettlement() {
        return reservation != null;
    }

    /** What was promised, or zero when nothing was reserved. */
    public BigDecimal reservedAmount() {
        return reservation == null ? Money.ZERO : reservation.amount();
    }
}
