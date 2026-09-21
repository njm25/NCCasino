package org.nc.nccasino.games.Slots;

import org.nc.nccasino.budget.Commitment;

/**
 * The dealer-budget side of a spin, as three callbacks the pure
 * {@link SlotsSpinController} can sequence without knowing about Bukkit,
 * config, or persistence.
 *
 * <p>The sequencing is the whole point, and it is not interchangeable:
 *
 * <ol>
 *   <li>{@link #underwrite} runs <em>before</em> the player is debited and
 *       before any random outcome exists. A refusal there costs nothing,
 *       because nothing has happened yet.
 *   <li>{@link #cancel} unwinds the dealer side if the player's own debit then
 *       fails. Without it a spin the player could not afford would leave the
 *       dealer holding a stake it never received.
 *   <li>{@link #settle} runs once, when the result is <em>awarded</em> --
 *       not when it is physically delivered. A win that goes to the overflow
 *       bank or a pending record has already left the dealer; claiming or
 *       retrying delivery later must have no budget effect at all.
 * </ol>
 */
public interface SlotsUnderwriting {

    /**
     * Asks the dealer to take on this spin: credits the stake and reserves the
     * worst case, atomically.
     *
     * @param maxPossiblePayout every active line hitting the top symbol at
     *     full width -- the actual ceiling of this spin, not an estimate
     * @return an accepted or unlimited {@link Commitment} to proceed, or a
     *     refusal. On a refusal nothing has changed and no outcome may be
     *     generated.
     */
    Commitment underwrite(long totalBetUnits, long maxPossiblePayout);

    /** Unwinds an accepted commitment whose player-side debit then failed. */
    void cancel(Commitment commitment, long totalBetUnits);

    /** Debits the awarded payout from the dealer, exactly once per commitment. */
    void settle(Commitment commitment, long payout);

    /**
     * The behavior of an unlimited dealer, and the default in tests: accept
     * everything, record nothing. Identical to pre-Phase-2 Slots.
     */
    static SlotsUnderwriting unlimited() {
        return new SlotsUnderwriting() {
            @Override
            public Commitment underwrite(long totalBetUnits, long maxPossiblePayout) {
                return Commitment.forUnlimitedDealer();
            }

            @Override
            public void cancel(Commitment commitment, long totalBetUnits) {
            }

            @Override
            public void settle(Commitment commitment, long payout) {
            }
        };
    }
}
