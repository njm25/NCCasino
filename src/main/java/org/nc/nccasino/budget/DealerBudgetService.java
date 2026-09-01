package org.nc.nccasino.budget;

import org.nc.nccasino.Nccasino;
import org.nc.nccasino.payout.BankedCurrency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The one entry point games use to ask "can the dealer afford this?" and to
 * record the answer.
 *
 * <p>Games never touch {@link DealerBudgetStore} directly. Everything a game
 * needs is here, in the order a round actually happens:
 *
 * <ol>
 *   <li>{@link #admit} -- before taking money and before choosing any random
 *       outcome;
 *   <li>{@link #reserve} -- credits the stake and promises the worst case;
 *   <li>{@link #increase} -- for a split, a double, another Roulette bet,
 *       another Mines tile;
 *   <li>{@link #settle}, {@link #releaseLoss} or {@link #refund} -- exactly
 *       once per commitment, however many times it is called.
 * </ol>
 *
 * <h2>Unlimited dealers</h2>
 *
 * <p>Every existing dealer is {@link DealerBudgetMode#UNLIMITED} and must
 * behave exactly as it did before Phase 2. So every method here short-circuits
 * on unlimited before doing anything at all: no config parsing beyond the mode,
 * no arithmetic, no reservation, no disk write. An unlimited dealer costs one
 * string read per call, which is why the mode is checked first everywhere
 * rather than modelled as a very large balance.
 */
public class DealerBudgetService {

    private final Nccasino plugin;
    private final DealerBudgetStore store;
    /** Dealers whose invalid configuration has already been reported, so the log is not spammed. */
    private final Set<String> reportedProblems = new LinkedHashSet<>();

    public DealerBudgetService(Nccasino plugin, DealerBudgetStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public DealerBudgetStore store() {
        return store;
    }

    // ---- configuration ---------------------------------------------------

    /**
     * Reads one dealer's budget policy from config.
     *
     * <p>Read per call rather than cached: the Bukkit config is an in-memory
     * map, so this is cheap, and an administrator editing a dealer mid-session
     * should not need a restart for the change to apply. Configuration
     * problems are logged once per dealer per session.
     */
    public DealerBudgetSettings settingsFor(String dealerInternalName) {
        if (plugin == null || dealerInternalName == null) {
            return DealerBudgetSettings.unlimited();
        }
        String base = "dealers." + dealerInternalName + ".";
        DealerBudgetSettings settings = DealerBudgetSettings.parse(
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_MODE),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_BASELINE),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_GUARANTEED_ROUNDS),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_REFILL_MODE),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_REFILL_AMOUNT),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_REFILL_PERIOD),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_REFILL_CAP),
            plugin.getConfig().getString(base + DealerBudgetSettings.PATH_RESET_TARGET));

        reportProblemsOnce(dealerInternalName, settings);
        return settings;
    }

    private void reportProblemsOnce(String dealer, DealerBudgetSettings settings) {
        if (settings.problems().isEmpty() || !reportedProblems.add(dealer)) {
            return;
        }
        for (String problem : settings.problems()) {
            plugin.getLogger().warning("[NCCasino] Dealer '" + dealer + "' budget config -- " + problem);
        }
        if (!settings.isUsable()) {
            plugin.getLogger().severe("[NCCasino] Dealer '" + dealer
                + "' is LIMITED but its risk policy is unusable, so it will refuse every wager."
                + " Fix its budget block, or set budget.mode to UNLIMITED. No stored balance was changed.");
        }
    }

    /** Clears the once-per-session problem reporting, e.g. after a config reload. */
    public void onConfigReloaded() {
        reportedProblems.clear();
    }

    public boolean isUnlimited(String dealerInternalName) {
        return !settingsFor(dealerInternalName).mode().isLimited();
    }

    // ---- admission -------------------------------------------------------

    /**
     * Whether this dealer can cover {@code exposure} right now.
     *
     * <p>Must be called <em>before</em> any money moves and before any random
     * outcome is generated. A denial here means nothing has happened yet and
     * the game can simply decline.
     *
     * <p>Applies any elapsed refill first, so a dealer whose funding period
     * came round while nobody was looking is judged on its real balance.
     */
    public AdmissionDecision admit(String dealerInternalName, Exposure exposure) {
        DealerBudgetSettings settings = settingsFor(dealerInternalName);
        if (!settings.mode().isLimited()) {
            return AdmissionDecision.ADMITTED;
        }
        seedInitialFunding(dealerInternalName, settings);
        refreshFunding(dealerInternalName, settings);
        return AdmissionPolicy.admit(settings, store.available(dealerInternalName), exposure);
    }

    /**
     * A LIMITED dealer's one-time funding bootstrap: see {@link
     * DealerBudgetStore#ensureInitialFunding}. Safe to call on every
     * admission check -- it is a no-op the moment the dealer has ever been
     * touched before.
     */
    private void seedInitialFunding(String dealerInternalName, DealerBudgetSettings settings) {
        if (!settings.isUsable()) {
            return;
        }
        store.ensureInitialFunding(
            dealerInternalName, settings.underwritingBaseline(), Instant.now().getEpochSecond());
    }

    /** Applies any elapsed refill periods. Cheap and idempotent within a period. */
    public void refreshFunding(String dealerInternalName, DealerBudgetSettings settings) {
        if (settings == null || !settings.hasRefill()) {
            return;
        }
        store.applyRefill(dealerInternalName, settings, Instant.now().getEpochSecond());
    }

    /**
     * Which of {@code denominations} this dealer could underwrite, given the
     * exposure each one creates.
     *
     * <p>Deliberately a filter rather than a calculation: the design forbids
     * inventing new, smaller wager amounts as a dealer's balance falls. A
     * denomination the dealer cannot currently cover becomes unavailable and
     * comes back unchanged when funding recovers.
     */
    public List<BigDecimal> affordableDenominations(
        String dealerInternalName,
        List<BigDecimal> denominations,
        java.util.function.Function<BigDecimal, Exposure> exposureOf
    ) {
        List<BigDecimal> affordable = new ArrayList<>();
        if (denominations == null) {
            return affordable;
        }
        DealerBudgetSettings settings = settingsFor(dealerInternalName);
        if (!settings.mode().isLimited()) {
            return new ArrayList<>(denominations);
        }
        seedInitialFunding(dealerInternalName, settings);
        refreshFunding(dealerInternalName, settings);
        BigDecimal available = store.available(dealerInternalName);
        for (BigDecimal denomination : denominations) {
            if (AdmissionPolicy.admit(settings, available, exposureOf.apply(denomination)).isAdmitted()) {
                affordable.add(denomination);
            }
        }
        return affordable;
    }

    // ---- commitments -----------------------------------------------------

    /**
     * Accepts a commitment: credits the stake and promises the worst-case
     * payout, atomically and exactly once.
     *
     * @param commitmentKey stable for this commitment -- a round id, a hand
     *     id, a spin number. Never a fresh random value per attempt, or the
     *     replay protection is defeated (see {@link Reservation}).
     * @return an accepted {@link Commitment} to settle later, an
     *     {@link Commitment#forUnlimitedDealer()} one that needs no settlement, or a
     *     refusal carrying the reason. On a refusal nothing has changed and
     *     the wager must not be taken.
     */
    public Commitment reserve(
        String dealerInternalName,
        UUID playerId,
        String gameType,
        String commitmentKey,
        BankedCurrency currency,
        Exposure exposure
    ) {
        DealerBudgetSettings settings = settingsFor(dealerInternalName);
        if (!settings.mode().isLimited()) {
            return Commitment.forUnlimitedDealer();
        }
        AdmissionDecision decision = admit(dealerInternalName, exposure);
        if (!decision.isAdmitted()) {
            return Commitment.refused(decision);
        }
        Reservation reservation = new Reservation(
            Reservation.forCommitment(dealerInternalName, playerId, commitmentKey),
            dealerInternalName,
            playerId,
            gameType,
            currency,
            exposure.maxGrossPayout(),
            Instant.now().getEpochSecond());
        Reservation stored = store.creditAndReserve(reservation, exposure.stake());
        if (stored == null) {
            // Admitted a moment ago, so this is a write failure rather than a
            // funding one. Refuse: an unpersisted reservation would vanish at
            // the next restart while the game carried on believing in it.
            return Commitment.refused(AdmissionDecision.PERSISTENCE_FAILED);
        }
        return Commitment.accepted(stored);
    }

    /**
     * Raises an open commitment's worst case -- a Blackjack split or double,
     * another Roulette bet, the next Mines tile.
     *
     * <p>Checked and applied in one step so a refused increase cannot leave the
     * additional stake taken. The check uses the exposure the commitment would
     * have <em>after</em> the increase, not the increment alone, because that
     * is what the dealer must be able to cover.
     *
     * @return the updated commitment, or a refusal carrying the reason -- in
     *     which case nothing changed and the action must be refused before any
     *     card, tile or random result is chosen
     */
    public Commitment increase(
        String dealerInternalName,
        Commitment open,
        Exposure totalExposureAfterIncrease,
        BigDecimal additionalStake
    ) {
        if (open == null) {
            return Commitment.refused(AdmissionDecision.CONFIGURATION_INVALID);
        }
        if (open.unlimited()) {
            return open;
        }
        Reservation existing = open.reservation();
        if (existing == null) {
            return Commitment.refused(AdmissionDecision.CONFIGURATION_INVALID);
        }
        DealerBudgetSettings settings = settingsFor(dealerInternalName);
        if (!settings.mode().isLimited()) {
            return Commitment.forUnlimitedDealer();
        }
        refreshFunding(dealerInternalName, settings);

        // Judge the increase against what is available once this commitment's
        // existing reservation is set aside -- it is being replaced, not added
        // to, so counting it twice would refuse legitimate increases.
        BigDecimal availableIgnoringThis = Money.add(
            store.available(dealerInternalName), existing.amount());
        AdmissionDecision decision = AdmissionPolicy.admit(
            settings, availableIgnoringThis, totalExposureAfterIncrease);
        if (!decision.isAdmitted()) {
            return Commitment.refused(decision);
        }
        Reservation updated = store.adjustReservation(
            dealerInternalName,
            existing.id(),
            totalExposureAfterIncrease.maxGrossPayout(),
            additionalStake);
        return updated == null
            ? Commitment.refused(AdmissionDecision.PERSISTENCE_FAILED)
            : Commitment.accepted(updated);
    }

    // ---- settlement ------------------------------------------------------

    /**
     * Pays a commitment and releases its reservation, exactly once.
     *
     * <p>Call this at the moment the result is known and the payout is
     * <em>awarded</em>, not when it is physically delivered. A payout that
     * goes to the overflow bank or a pending record has already left the
     * dealer; claiming or retrying it later must have no budget effect, which
     * is exactly what the idempotent reservation id gives.
     */
    public Settlement settle(String dealerInternalName, Commitment commitment, BigDecimal payout) {
        if (commitment == null || !commitment.requiresSettlement()) {
            // Nothing was reserved -- an unlimited dealer, or a commitment that
            // was never accepted. Reporting this as already settled keeps a
            // caller's settlement path identical in both modes.
            return Settlement.alreadySettled();
        }
        Reservation reservation = commitment.reservation();
        Settlement result = store.settle(dealerInternalName, reservation.id(), payout);
        logSettlementAnomaly(dealerInternalName, reservation, payout, result);
        return result;
    }

    /** A player loss: the dealer keeps the stake and the reservation is released. */
    public Settlement releaseLoss(String dealerInternalName, Commitment commitment) {
        return settle(dealerInternalName, commitment, Money.ZERO);
    }

    /** A push or cancellation: the stake goes back and the reservation is released. */
    public Settlement refund(String dealerInternalName, Commitment commitment, BigDecimal stake) {
        return settle(dealerInternalName, commitment, stake);
    }

    private void logSettlementAnomaly(
        String dealer, Reservation reservation, BigDecimal payout, Settlement result) {

        if (result.exposureViolation()) {
            plugin.getLogger().severe("[NCCasino] Dealer '" + dealer + "' was asked to pay "
                + Money.store(payout) + " against a reservation of only "
                + Money.store(reservation.amount()) + " (commitment " + reservation.id()
                + "). The player was paid in full, but this means the game's pre-commitment"
                + " exposure calculation is wrong and must be fixed -- the dealer's live"
                + " balance no longer accurately reflects what it can safely underwrite.");
        }
        if (result.insolvent()) {
            plugin.getLogger().severe("[NCCasino] Dealer '" + dealer
                + "' did not hold enough live balance to cover the full payout of "
                + Money.store(payout) + " for commitment " + reservation.id()
                + ". Its balance was floored at zero rather than driven negative, but real"
                + " money left this dealer's economy with no backing. This requires manual"
                + " reconciliation and almost certainly means an earlier exposure-calculation"
                + " bug already let this dealer take on more risk than it could afford.");
        }
        if (result.status() == Settlement.Status.FAILED) {
            plugin.getLogger().severe("[NCCasino] Dealer '" + dealer
                + "' could not persist the settlement of commitment " + reservation.id()
                + " for " + Money.store(payout) + ". Nothing was debited; the reservation"
                + " is still held and requires manual reconciliation.");
        }
    }

    // ---- diagnostics -----------------------------------------------------

    /** A human-readable summary for an administrator. */
    public String describe(String dealerInternalName) {
        DealerBudgetSettings settings = settingsFor(dealerInternalName);
        if (!settings.mode().isLimited()) {
            return dealerInternalName + ": UNLIMITED";
        }
        DealerBudgetState state = store.state(dealerInternalName);
        return dealerInternalName + ": LIMITED"
            + " balance=" + Money.store(state.liveBalance())
            + " reserved=" + Money.store(state.reservedTotal())
            + " available=" + Money.store(state.available())
            + " baseline=" + Money.store(settings.underwritingBaseline())
            + " guaranteed-rounds=" + settings.guaranteedWorstCaseRounds()
            + " max-loss-per-round=" + Money.store(settings.maxHouseLossPerRound())
            + " refill=" + settings.refillMode()
            + " open-commitments=" + state.reservations().size();
    }
}
