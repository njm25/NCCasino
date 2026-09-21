package org.nc.nccasino.games.CoinFlip;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

/**
 * Dispatches events to a {@link CoinFlipMatch}. In PLAYER_VS_PLAYER there is
 * exactly one shared match (a single table). In PLAYER_VS_DEALER every
 * player who interacts with this dealer gets their own private match
 * against the house, created lazily and kept independent of every other
 * player's -- mirroring RockPaperScissorsServer's RpsMatch dispatcher.
 */
public class CoinFlipServer extends Server {

    /** Fixed compounding multiplier for a PvE chain win -- same 1% house edge convention as RPS/Mines/Dragon Descent. */
    private static final double CHAIN_MULTIPLIER = 1.98;
    /**
     * PvE's own pacing for the fallback flip resolution -- there's no
     * second human to keep pace with. Coin Flip's client-side flip
     * animation is a fixed ~30-tick cycle (5 slots at 5 ticks apart, plus a
     * short settle beat), so this sits comfortably past it, mirroring how
     * PvP's own 70-tick fallback comfortably outlasts its animation.
     */
    private static final long REVEAL_WINDOW_PVE_TICKS = 40L;
    private static final long REVEAL_WINDOW_TICKS = 70L;

    /** The single shared table -- always live, exactly like PvP has always worked. */
    private final CoinFlipMatch sharedMatch = new CoinFlipMatch(null);

    /** One private match per player, created on first interaction, independent of the shared table. */
    private final Map<UUID, CoinFlipMatch> pveMatches = new HashMap<>();

    /**
     * Each player's own currently-selected view of this dealer -- PvP (the
     * shared table) or PvE (their own private match). Seeded lazily from
     * the admin's configured default on first interaction, then mutable
     * per player via the in-game toggle button, independent of every other
     * player's choice.
     */
    private final Map<UUID, CoinFlipMode> playerView = new HashMap<>();

    public CoinFlipServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        return new CoinFlipClient(this, player, plugin, internalName);
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        // A delayed callback scheduled by an old Client instance (e.g. one
        // dropped by a disconnect/reconnect while its flip animation was
        // still in flight) can still fire after a new Client has been
        // registered for the same player. Reject it here rather than
        // letting it act on whichever match the player's *current* view
        // now resolves to -- possibly a different match than the one that
        // scheduled the callback. GET_CHAIRS is exempt: the constructor
        // sends it before Server.getOrCreateClient has a chance to insert
        // this very client into `clients`, so the guard would otherwise
        // reject every initial snapshot request; it's a read-only query
        // with no state to corrupt.
        if (!"GET_CHAIRS".equals(eventType) && clients.get(client.getPlayer().getUniqueId()) != client) {
            return;
        }
        if ("PLAYER_TOGGLE_MODE".equals(eventType)) {
            handleToggleMode(client);
            return;
        }
        matchFor(client.getPlayer().getUniqueId()).handle(client, eventType, data);
    }

    CoinFlipMode viewFor(UUID playerId) {
        return playerView.computeIfAbsent(playerId, id -> plugin.getCoinFlipMode(internalName));
    }

    private CoinFlipMatch matchFor(UUID playerId) {
        if (viewFor(playerId) == CoinFlipMode.PLAYER_VS_DEALER) {
            return pveMatches.computeIfAbsent(playerId, CoinFlipMatch::new);
        }
        return sharedMatch;
    }

    /**
     * The inherited {@code Server.playCountdownSound()} plays to every
     * client attached to this dealer, regardless of which match they're
     * currently viewing -- fine when every viewer shares the one PvP table,
     * but the shared table's own countdown has no business being audible to
     * a player currently inside their own private PvE match. Scoped the
     * same way {@code CoinFlipMatch.send()} scopes its PvP broadcasts.
     */
    private void playPvpCountdownSound() {
        for (Map.Entry<UUID, Client> entry : clients.entrySet()) {
            if (viewFor(entry.getKey()) != CoinFlipMode.PLAYER_VS_PLAYER) continue;
            Player player = entry.getValue().getPlayer();
            if (player != null && SoundHelper.getSoundSafely("block.note_block.hat", player) != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Switches the requesting player's own personal view between the
     * shared PvP table and their private PvE match, independent of every
     * other player currently at this dealer. Blocked outright while the
     * requester's own current match is active -- leaving mid-round would
     * either abandon a live PvP opponent or orphan their own PvE round, and
     * switching is no longer offered as an implicit "cash out and switch"
     * shortcut -- cash out (or finish the round) first, then switch.
     */
    private void handleToggleMode(Client client) {
        if (!plugin.getCoinFlipModeSwitchingEnabled(internalName)) {
            // Authoritative -- the client is expected to hide its own
            // toggle button when this is disabled, but a stale client
            // (or one bypassing the UI entirely) must still be refused here.
            client.onServerUpdate("TOGGLE_MODE_DENIED", null);
            return;
        }
        UUID playerId = client.getPlayer().getUniqueId();
        CoinFlipMatch currentMatch = matchFor(playerId);
        if (currentMatch.gameActive) {
            // Authoritative -- the client is expected to hide/no-op its own
            // toggle button while a round is active, but a stale client (or
            // one bypassing the UI entirely) must still be refused here.
            client.onServerUpdate("TOGGLE_MODE_DENIED", null);
            return;
        }
        forfeitPlayer(playerId);

        CoinFlipMode next = viewFor(playerId) == CoinFlipMode.PLAYER_VS_PLAYER
            ? CoinFlipMode.PLAYER_VS_DEALER
            : CoinFlipMode.PLAYER_VS_PLAYER;
        playerView.put(playerId, next);
        client.onServerUpdate("MODE_CHANGED", next);

        CoinFlipMatch newMatch = matchFor(playerId);
        newMatch.ensurePveSeated(client);
        newMatch.sendChairSnapshotTo(client);
    }

    /** Kicked players forfeit unconditionally, regardless of round phase -- no refund, no pending payout. */
    void forfeitPlayer(UUID playerId) {
        matchFor(playerId).forfeitPlayer(playerId);
    }

    /** Settles playerId's in-flight match during shutdown (refund pregame/pick-phase, pay out a committed winner). */
    void refundForShutdown(UUID playerId) {
        matchFor(playerId).refundForShutdown();
    }

    void registerRidingSession(UUID playerId) {
        matchFor(playerId).registerRidingSession(playerId);
    }

    /**
     * Makes a disconnected/closed PvE match terminal without changing an
     * already-committed flip. Safe checkpoints cash out immediately;
     * otherwise the flip finishes and its result decides the settlement.
     */
    void requestPveExitSettlement(UUID playerId) {
        matchFor(playerId).requestExitSettlement(playerId);
    }

    /**
     * Frees a PvE player's private match once it's fully idle -- no-op in
     * PvP (the shared table isn't per-player and must never be removed) and
     * a no-op if the match is still riding out an active round.
     */
    void cleanupIdleMatch(UUID playerId) {
        CoinFlipMatch match = pveMatches.get(playerId);
        if (match != null && !match.gameActive && !hasClient(playerId)) {
            pveMatches.remove(playerId, match);
        }
    }

    private void queuePendingPayout(UUID playerId, double amount) {
        queuePendingPayout(playerId, amount, PayoutMessages.disconnectedMidGameContext("Coin Flip"));
    }

    private void queuePendingPayout(UUID playerId, double amount, String context) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout pendingPayout = PendingPayout.create(
            playerId,
            "Coin Flip",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            context
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(pendingPayout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Coin Flip pending payout failed to persist for " + playerId + ".");
        }
    }

    /**
     * One table's worth of round state and logic -- two chairs, a bet, a
     * flip, and payout. Instantiated once as the shared PvP table
     * ({@code owningPlayerId == null}, updates go to every client via the
     * outer {@code broadcastUpdate}), or once per player in PvE
     * ({@code owningPlayerId} set, updates go only to that player's own
     * client -- chair 2 is always the house).
     */
    private final class CoinFlipMatch {

        private final UUID owningPlayerId;

        private int countdownTaskId = -1;
        private int timeLeft = 0;

        /**
         * Bumped once per accepted bet, and again on every PvE chain
         * continuation. The flip's own fallback timer and the client's
         * echoed ANIMATION_FINISHED capture this at schedule/broadcast
         * time and compare it back before resolving, so a callback that
         * outlives its round becomes a safe no-op instead of resolving the
         * wrong flip.
         */
        private int roundToken = 0;

        private Player chairOneOccupant;
        private Player chairTwoOccupant;
        /** Long, not int -- a PvP pot doubles two accepted stakes together, which can exceed Integer.MAX_VALUE even though neither individual stake does. */
        private long betAmount;
        /**
         * The single dealer-budget promise covering this PvE chain,
         * reserved on the first pick and grown before each further flip.
         * Never opened for PvP -- the dealer does not fund a PvP pot.
         * Null between chains and after settlement.
         */
        private org.nc.nccasino.budget.Commitment budgetCommitment;
        private final String budgetSessionId = java.util.UUID.randomUUID().toString();
        private long budgetRoundCounter = 0;
        /** The player's own stake before any chain compounding, captured once per accepted bet. */
        private long originalWager;
        /** PvE-only: consecutive chain wins since the bet was accepted. */
        private int chainWins;
        private boolean gameActive;
        private boolean revealInProgress;
        /** PvE only: the owner left, so the current flip is the final one. */
        private boolean exitSettlementPending;
        private Integer committedWinner;
        /** PvE only: 0 = left, 1 = right; null until the player locks in a pick for the current flip. */
        private Integer playerPick;
        private final Set<UUID> forfeited = new HashSet<>();
        private final Map<UUID, TerminableSession> ridingSessions = new HashMap<>();

        private CoinFlipMatch(UUID owningPlayerId) {
            this.owningPlayerId = owningPlayerId;
        }

        private boolean isPve() {
            return owningPlayerId != null;
        }

        /**
         * PvP and PvE share a single Client instance per player (whichever
         * one they currently have open), so broadcastUpdate() -- which
         * reaches every client attached to this dealer -- is NOT safe to
         * use for the shared table: a player currently looking at their
         * own private PvE match would still receive the PvP table's
         * broadcasts and apply them to fields describing their unrelated
         * PvE state. Only clients whose current view is actually PvP get this.
         */
        private void send(String eventType, Object data) {
            if (owningPlayerId == null) {
                for (Map.Entry<UUID, Client> entry : clients.entrySet()) {
                    if (viewFor(entry.getKey()) == CoinFlipMode.PLAYER_VS_PLAYER) {
                        entry.getValue().onServerUpdate(eventType, data);
                    }
                }
            } else {
                Client owner = clients.get(owningPlayerId);
                if (owner != null) {
                    owner.onServerUpdate(eventType, data);
                }
            }
        }

        private void handle(Client client, String eventType, Object data) {
            ensurePveSeated(client);
            switch (eventType) {
                case "PLAYER_SIT_ONE":
                    if (gameActive) return;
                    chairOneOccupant = client.getPlayer();
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                    break;
                case "PLAYER_SIT_TWO":
                    if (gameActive || isPve()) return;
                    chairTwoOccupant = client.getPlayer();
                    send("PLAYER_SIT_TWO", chairTwoOccupant);
                    break;
                case "PLAYER_LEAVE_ONE":
                    if (gameActive) return;
                    if (chairTwoOccupant != null) {
                        chairOneOccupant = chairTwoOccupant;
                        chairTwoOccupant = null;
                        betAmount = 0;
                        send("PLAYER_LEAVE_TWO", null);
                        send("PLAYER_LEAVE_ONE", null);
                        send("PLAYER_SIT_ONE", chairOneOccupant);
                    } else {
                        chairOneOccupant = null;
                        betAmount = 0;
                        send("PLAYER_LEAVE_ONE", null);
                    }
                    break;
                case "PLAYER_LEAVE_TWO":
                    if (gameActive || isPve()) return;
                    chairTwoOccupant = null;
                    send("PLAYER_LEAVE_TWO", null);
                    break;
                case "PLAYER_SUBMIT_BET":
                    if (gameActive) return;
                    if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(client.getPlayer().getUniqueId())) {
                        if (betAmount == 0) {
                            betAmount = (long) data;
                            if (isPve()) {
                                // No second human to accept -- the round
                                // starts the instant the bet is placed.
                                beginActiveRound(Boolean.TRUE);
                            } else {
                                send("PLAYER_SUBMIT_BET", data);
                            }
                        } else {
                            betAmount = 0;
                            send("PLAYER_CANCEL_BET", null);
                        }
                    }
                    break;
                case "PLAYER_ACCEPT_BET":
                    if (gameActive) return;
                    if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(client.getPlayer().getUniqueId())
                        && betAmount != 0) {
                        beginActiveRound(data);
                    } else {
                        // The accepting client already deducted the wager
                        // locally before this arrived (e.g. chair one left
                        // and chair two got promoted/cleared in the same
                        // window) -- tell that client directly so it can
                        // refund what it already took, instead of silently
                        // dropping the accept and losing the player's currency.
                        client.onServerUpdate("PLAYER_ACCEPT_REJECTED", null);
                    }
                    break;
                case "PLAYER_PICK_LEFT":
                    handlePick(client, 0);
                    break;
                case "PLAYER_PICK_RIGHT":
                    handlePick(client, 1);
                    break;
                case "ANIMATION_FINISHED":
                    if (data instanceof Object[] arr && arr.length == 2
                        && arr[0] instanceof Integer w && arr[1] instanceof Integer token) {
                        if (token == roundToken) {
                            settleRound(committedWinner != null ? committedWinner : w);
                        }
                    }
                    break;
                case "PLAYER_CASH_OUT":
                    handleCashOut(client);
                    break;
                case "GET_CHAIRS":
                    sendChairSnapshotTo(client);
                    break;
                default:
                    break;
            }
        }

        /** Pushes this match's current chairs/bet/round state to a single client -- shared by GET_CHAIRS and a just-completed mode switch. */
        private void sendChairSnapshotTo(Client client) {
            Object[] chairs = {
                chairOneOccupant,
                chairTwoOccupant,
                betAmount,
                gameActive,
                timeLeft,
                playerPick,
            };
            client.onServerUpdate("GET_CHAIRS", chairs);
        }

        /**
         * PvE only: this match belongs to exactly one player, so there's no
         * real "sit" gesture to wait for -- seat them the moment any event
         * reaches this match, silently and without a PLAYER_SIT_ONE
         * broadcast (the client no longer has a seat-click UI to react to).
         */
        private void ensurePveSeated(Client client) {
            if (isPve() && chairOneOccupant == null) {
                chairOneOccupant = client.getPlayer();
            }
        }

        /**
         * Shared tail of both "player 2 accepted" and "the house
         * auto-accepted" -- starts the round. PvP doubles the pot (the
         * opponent/house matches the stake); PvE does not -- its whole
         * payout curve is the chain multiplier itself, starting from the
         * bare wager.
         */
        private void beginActiveRound(Object acceptPayload) {
            send("PLAYER_ACCEPT_BET", acceptPayload);
            gameActive = true;
            revealInProgress = false;
            roundToken++;
            originalWager = betAmount;
            // betAmount/originalWager are long specifically so this doubling
            // can't wrap or need clamping -- a clamped int pot would still
            // silently destroy the difference between the real combined
            // stake (already withdrawn from both wallets) and whatever it
            // got capped to, paying the winner less than what was actually
            // put in.
            betAmount = isPve() ? betAmount : betAmount * 2;
            chainWins = 0;
            exitSettlementPending = false;
            playerPick = null;
            startTimer();
        }

        /**
         * Whether the just-applied win (chainWins/betAmount already reflect
         * it, both exact and never clamped -- see below) must stop the
         * chain here instead of offering another pick -- either the
         * admin-configured cap (a cap <= 0, the -1 default, means
         * unbounded), or offering one more round could produce a win that
         * exceeds CoinFlipPayoutMath.MAX_SAFE_POT, the currency system's
         * representable ceiling.
         *
         * <p>This is deliberately a forward-looking check on the round that
         * would come NEXT, not a check of whether the current pot already
         * sits at the ceiling. Checking backward (whether betAmount itself
         * had already reached the ceiling) would only ever notice AFTER
         * compound() had already clamped that same round's own win,
         * silently underpaying the exact win the player just earned.
         * Checking forward instead means a round is only ever offered when
         * its win, if it happens, is guaranteed to compound to an exact
         * value -- compound() itself never actually needs to clamp along
         * this path.
         */
        /**
         * Checks and, if covered, atomically opens or grows this chain's
         * single dealer-budget reservation to the pot that would exist if
         * the next flip is a win. Denies before the flip, before any random
         * result is generated.
         */
        private boolean ensureBudgetCoversNextFlip() {
            org.nc.nccasino.budget.DealerBudgetService budget = plugin.getDealerBudgetService();
            if (budget == null) {
                return true;
            }
            org.nc.nccasino.budget.Exposure updatedExposure =
                org.nc.nccasino.budget.ProgressiveLiability.chainExposureAfterNextRound(
                    originalWager, betAmount, CHAIN_MULTIPLIER);

            org.nc.nccasino.budget.Commitment result;
            if (budgetCommitment == null) {
                budgetRoundCounter++;
                Material material = plugin.getCurrency(internalName);
                org.nc.nccasino.payout.BankedCurrency currency = new org.nc.nccasino.payout.BankedCurrency(
                    currencyMode, material == null ? null : material.name(), currencyName);
                result = budget.reserve(
                    internalName, owningPlayerId, "Coin Flip",
                    budgetSessionId + "-chain-" + budgetRoundCounter, currency, updatedExposure);
            } else {
                result = budget.increase(
                    internalName, budgetCommitment, updatedExposure, org.nc.nccasino.budget.Money.ZERO);
            }

            if (!result.isAccepted()) {
                Player denied = Bukkit.getPlayer(owningPlayerId);
                if (denied != null && denied.isOnline()) {
                    switch (plugin.getPreferences(owningPlayerId).getMessageSetting()) {
                        case STANDARD, VERBOSE ->
                            denied.sendMessage(plugin.getLocalization().text(
                                denied, "coin-flip.dealer-cannot-cover"));
                        case NONE -> {
                        }
                    }
                }
                return false;
            }
            budgetCommitment = result;
            return true;
        }

        /** Pays the chain's result and releases its reservation, exactly once. */
        private void settleBudget(java.math.BigDecimal payout) {
            org.nc.nccasino.budget.DealerBudgetService budget = plugin.getDealerBudgetService();
            if (budget == null || budgetCommitment == null) {
                budgetCommitment = null;
                return;
            }
            org.nc.nccasino.budget.Settlement result =
                budget.settle(internalName, budgetCommitment, payout);
            if (result.status() != org.nc.nccasino.budget.Settlement.Status.FAILED) {
                budgetCommitment = null;
            }
        }

        private boolean chainCapped() {
            int cap = plugin.getCoinFlipMaxChainRounds(internalName);
            if (cap > 0 && chainWins >= cap) {
                return true;
            }
            return CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(betAmount, CHAIN_MULTIPLIER);
        }

        private void handlePick(Client client, int pick) {
            if (!isPve() || !gameActive || revealInProgress || playerPick != null) return;
            UUID playerId = client.getPlayer().getUniqueId();
            if (chairOneOccupant == null || !chairOneOccupant.getUniqueId().equals(playerId)) return;

            if (!ensureBudgetCoversNextFlip()) {
                return;
            }
            playerPick = pick;
            revealInProgress = true;
            beginFlip();
        }

        /**
         * Flips the coin. PvP reaches this only from startTimer()'s
         * countdown expiring; PvE reaches it the instant a pick locks in,
         * with no countdown at all.
         */
        private void beginFlip() {
            int winner = ThreadLocalRandom.current().nextInt(2);
            committedWinner = winner;
            int token = roundToken;
            send("WINNER", new Object[]{winner, token});
            // Authoritative fallback: resolves the round even if every
            // client disconnects before its local flip animation reports
            // back. Guarded by roundToken so a fallback scheduled for a
            // since-superseded flip can't fire. Routed through settleRound
            // (not resolveRound directly) so a PvE win still advances the
            // chain even when this fallback -- rather than the client's own
            // ANIMATION_FINISHED echo -- is what actually resolves it.
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (token == roundToken) {
                        settleRound(committedWinner != null ? committedWinner : winner);
                    }
                },
                isPve() ? REVEAL_WINDOW_PVE_TICKS : REVEAL_WINDOW_TICKS
            );
        }

        /**
         * Routes a decisive flip's outcome. A PvE win always compounds the
         * pot at the fixed 1%-edge multiplier first -- including the win
         * that meets the cap, otherwise that capping win would be paid out
         * at the PREVIOUS win's pot instead of its own. PvP, a PvE loss, or
         * a PvE win that (now) meets the cap all fall through to
         * resolveRound() to pay out; a PvE win still under the cap instead
         * reopens the pick phase via advanceChain().
         */
        private void settleRound(int winner) {
            if (!gameActive) return;

            if (isPve() && playerPick != null && winner == playerPick) {
                chainWins++;
                betAmount = CoinFlipPayoutMath.compound(betAmount, CHAIN_MULTIPLIER);
                boolean cappedWin = chainCapped();
                CoinFlipExitSettlementPolicy.Action action = CoinFlipExitSettlementPolicy.afterReveal(
                    CoinFlipExitSettlementPolicy.Outcome.WIN, exitSettlementPending, cappedWin);
                if (action == CoinFlipExitSettlementPolicy.Action.CASH_OUT) {
                    // The committed win still earns this flip's multiplier,
                    // but an owner who left must not be advanced into
                    // another untimed decision point.
                    revealInProgress = false;
                    cashOut(owningPlayerId);
                    return;
                }
                if (action == CoinFlipExitSettlementPolicy.Action.CONTINUE) {
                    advanceChain();
                    return;
                }
            }
            resolveRound(winner);
        }

        /**
         * Reopens the pick phase after a PvE win under the chain cap.
         * Invalidates the round token itself (unlike resolveRound, this
         * path deliberately keeps the round alive), or the loser of the
         * ANIMATION_FINISHED/fallback race would still pass the token
         * check and re-run this exact win a second time.
         */
        private void advanceChain() {
            if (!gameActive) return;

            revealInProgress = false;
            committedWinner = null;
            roundToken++;
            playerPick = null;
            send("CHAIN_WIN", new Object[]{chainWins, betAmount});
        }

        /**
         * Cashes out the current chain pot on request, valid only while PvE
         * and awaiting a pick. betAmount is never doubled at accept for
         * PvE (see beginActiveRound), so it already equals originalWager
         * before any win and the compounded pot after one.
         */
        private void handleCashOut(Client client) {
            if (!isPve() || !gameActive || revealInProgress) return;
            UUID playerId = client.getPlayer().getUniqueId();
            if (chairOneOccupant == null || !chairOneOccupant.getUniqueId().equals(playerId)) return;

            cashOut(playerId);
        }

        /** Authoritative PvE cash-out shared by clicks, mode-switch checkpoints, and disconnect settlement. */
        private void cashOut(UUID playerId) {
            if (!isPve() || !gameActive) return;
            if (chairOneOccupant == null || !chairOneOccupant.getUniqueId().equals(playerId)) return;

            send("ANIMATION_FINISHED", 0);
            long wager = originalWager;
            long payout = betAmount;
            settleBudget(org.nc.nccasino.budget.Money.of(payout));
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            playerPick = null;
            timeLeft = 0;
            countdownTaskId = -1;

            if (payout > 0 && !forfeited.contains(playerId)) {
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    creditPlayer(onlinePlayer, payout);
                    sendPayoutMessage(onlinePlayer, payout, true, payout - wager);
                    applyWinEffects(onlinePlayer);
                } else {
                    queuePendingPayout(playerId, payout);
                }
            }
            clearRidingSession(playerId);
            forfeited.clear();
            reseatDisconnectedOccupants();
            cleanupPrivateMatchIfAbandoned();
        }

        /**
         * Called after a voluntary close or true disconnect has claimed the
         * client session. At a pick checkpoint there is no future timer, so
         * settle immediately. Mid-flip, leave the authoritative result
         * untouched and let settleRound finish it exactly once.
         */
        private void requestExitSettlement(UUID playerId) {
            if (!isPve() || !gameActive || !owningPlayerId.equals(playerId)) return;

            exitSettlementPending = true;
            if (!revealInProgress) {
                cashOut(playerId);
            }
        }

        /**
         * Authoritative resolution for a decisive flip, called either by
         * whichever seated client's local flip animation reports back
         * first, or by the server's own fixed-delay fallback timer
         * scheduled alongside the WINNER broadcast.
         */
        private void resolveRound(int winner) {
            if (!gameActive) return;

            // The only way settleRound() ever reaches resolveRound() with a
            // PvE win is chainCapped() having been true -- a genuine
            // advanceChain-eligible win never gets here.
            boolean pveWin = isPve() && playerPick != null && winner == playerPick;
            send("ANIMATION_FINISHED", winner);
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            long payout = betAmount;
            if (isPve()) {
                settleBudget(org.nc.nccasino.budget.Money.of(pveWin ? payout : 0L));
            }
            long wager = originalWager;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            playerPick = null;
            timeLeft = 0;
            countdownTaskId = -1;
            handlePayout(payoutOne, payoutTwo, payout, winner, wager, pveWin);
            if (payoutOne != null) {
                clearRidingSession(payoutOne.getUniqueId());
            }
            if (payoutTwo != null) {
                clearRidingSession(payoutTwo.getUniqueId());
            }
            forfeited.clear();
            reseatDisconnectedOccupants();
            cleanupPrivateMatchIfAbandoned();
        }

        /** Post-round seat cleanup for whichever occupant's client already disconnected. */
        private void reseatDisconnectedOccupants() {
            if (chairOneOccupant != null && !hasClient(chairOneOccupant.getUniqueId())) {
                if (chairTwoOccupant != null) {
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_TWO", null);
                    send("PLAYER_LEAVE_ONE", null);
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_ONE", null);
                }
            }
            if (chairTwoOccupant != null && !hasClient(chairTwoOccupant.getUniqueId())) {
                chairTwoOccupant = null;
                send("PLAYER_LEAVE_TWO", null);
            }
        }

        /**
         * PvP pays whichever seat matches the coin's landing index, exactly
         * as before. PvE has no seat-based winner at all -- chair 2 is
         * never occupied -- so it pays the human in chair 1 only when the
         * coin's landing index matches their own locked-in pick, mirroring
         * RPS's null-occupant-is-a-no-op pattern but simplified since
         * there's no second UUID to look up in the first place.
         */
        private void handlePayout(Player one, Player two, long payout, int winner, long wager, boolean pveWin) {
            if (isPve()) {
                if (one == null) return;
                UUID playerId = one.getUniqueId();
                if (forfeited.contains(playerId)) return;

                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    if (pveWin && payout > 0) {
                        queuePendingPayout(playerId, payout);
                    } else {
                        // PendingPayout explicitly supports zero-value outcome
                        // records. Persist the loss so an Alt+F4/network-drop
                        // player still learns how the round ended on their
                        // next join; no currency is deposited for amount 0.
                        queuePendingPayout(playerId, 0);
                    }
                    return;
                }

                if (pveWin) {
                    if (payout > 0) {
                        switch (plugin.getPreferences(playerId).getMessageSetting()) {
                            case STANDARD:
                            case VERBOSE:
                                // resolveRound only ever sees a PvE win once
                                // chainCapped() was true, for one of two
                                // reasons -- distinguish them so a pot that
                                // stopped because one more round could have
                                // exceeded the representable ceiling doesn't
                                // claim it hit an admin-configured round
                                // count instead (which may not even be set).
                                if (CoinFlipPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(payout, CHAIN_MULTIPLIER)) {
                                    onlinePlayer.sendMessage(plugin.getLocalization().text(
                                        onlinePlayer, "coin-flip.max-pot-hit"));
                                } else {
                                    onlinePlayer.sendMessage(plugin.getLocalization().text(
                                        onlinePlayer,
                                        "coin-flip.max-chain-hit",
                                        "rounds", plugin.getCoinFlipMaxChainRounds(internalName)
                                    ));
                                }
                                break;
                            case NONE:
                                break;
                        }
                        creditPlayer(onlinePlayer, payout);
                        sendPayoutMessage(onlinePlayer, payout, true, payout - wager);
                        applyWinEffects(onlinePlayer);
                    }
                } else {
                    sendPayoutMessage(onlinePlayer, payout, false, wager);
                    applyLoseEffects(onlinePlayer);
                }
                return;
            }

            UUID winnerId = (winner == 0)
                ? (one != null ? one.getUniqueId() : null)
                : (two != null ? two.getUniqueId() : null);
            UUID loserId = (winner == 0)
                ? (two != null ? two.getUniqueId() : null)
                : (one != null ? one.getUniqueId() : null);

            // Resolve fresh Player references by UUID rather than trusting
            // the cached chair-occupant objects, which can go stale across
            // a reconnect while the game was active.
            if (winnerId != null && payout > 0 && !forfeited.contains(winnerId)) {
                Player winnerPlayer = Bukkit.getPlayer(winnerId);
                if (winnerPlayer != null && winnerPlayer.isOnline()) {
                    creditPlayer(winnerPlayer, payout);
                    sendPayoutMessage(winnerPlayer, payout, true, payout - wager);
                    applyWinEffects(winnerPlayer);
                } else {
                    queuePendingPayout(winnerId, payout);
                }
            }

            if (loserId != null && !forfeited.contains(loserId)) {
                Player loserPlayer = Bukkit.getPlayer(loserId);
                if (loserPlayer != null && loserPlayer.isOnline()) {
                    sendPayoutMessage(loserPlayer, payout, false, wager);
                    applyLoseEffects(loserPlayer);
                } else {
                    // PendingPayout explicitly supports zero-value outcome
                    // records. Persist the loss so an Alt+F4/network-drop
                    // player still learns how the round ended on their
                    // next join; no currency is deposited for amount 0.
                    queuePendingPayout(loserId, 0);
                }
            }
        }

        /**
         * Settles an in-flight round during shutdown. Once a winner has
         * been committed (decisive flip or, in PvE, a chain already under
         * way), that authoritative payout is saved; before that, each
         * side's stake is refunded -- PvE refunds the whole compounded pot
         * back to its sole owner rather than splitting it.
         */
        private void refundForShutdown() {
            if (!gameActive) {
                return;
            }

            long payout = betAmount;
            long stake = isPve() ? payout : payout / 2;
            Integer winner = committedWinner;
            Integer pick = playerPick;
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            playerPick = null;
            timeLeft = 0;
            countdownTaskId = -1;

            if (winner != null) {
                // settleRound() applies this flip's chain multiplier before
                // paying out a PvE win; a shutdown caught between committing
                // the winner (beginFlip) and settleRound() running must
                // apply that same multiplier itself, or the saved payout is
                // short by 1.98x.
                boolean pveWin = isPve() && pick != null && winner.intValue() == pick.intValue();
                if (isPve()) {
                    settleBudget(org.nc.nccasino.budget.Money.of(
                        pveWin ? CoinFlipPayoutMath.compound(payout, CHAIN_MULTIPLIER) : 0L));
                }
                if (isPve()) {
                    if (payoutOne != null && !forfeited.contains(payoutOne.getUniqueId())) {
                        if (pveWin) {
                            long winnerPayout = CoinFlipPayoutMath.compound(payout, CHAIN_MULTIPLIER);
                            if (winnerPayout > 0) {
                                queuePendingPayout(
                                    payoutOne.getUniqueId(),
                                    winnerPayout,
                                    PayoutMessages.committedResultContext("Coin Flip")
                                );
                            }
                        } else {
                            // No winning player to pay, but the player
                            // still needs to learn the round's outcome on
                            // next join, same as handlePayout()'s silent
                            // loss -- except here there is no live client
                            // to show it to, so it must be persisted.
                            queuePendingPayout(
                                payoutOne.getUniqueId(),
                                0,
                                PayoutMessages.committedResultContext("Coin Flip")
                            );
                        }
                    }
                } else {
                    Player winningPlayer = winner == 0 ? payoutOne : payoutTwo;
                    Player losingPlayer = winner == 0 ? payoutTwo : payoutOne;
                    if (winningPlayer != null && payout > 0
                        && !forfeited.contains(winningPlayer.getUniqueId())) {
                        queuePendingPayout(
                            winningPlayer.getUniqueId(),
                            payout,
                            PayoutMessages.committedResultContext("Coin Flip")
                        );
                    }
                    if (losingPlayer != null && !forfeited.contains(losingPlayer.getUniqueId())) {
                        // Same zero-value outcome-only record as the normal
                        // (non-shutdown) offline-loser path -- otherwise a
                        // committed PvP result caught by a shutdown pays the
                        // winner but leaves the loser with no record at all
                        // of how the round they rode into ended.
                        queuePendingPayout(
                            losingPlayer.getUniqueId(),
                            0,
                            PayoutMessages.committedResultContext("Coin Flip")
                        );
                    }
                }
            } else {
                if (isPve()) {
                    settleBudget(org.nc.nccasino.budget.Money.of(stake));
                }
                if (payoutOne != null && stake > 0 && !forfeited.contains(payoutOne.getUniqueId())) {
                    queuePendingPayout(payoutOne.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Coin Flip"));
                }
                if (payoutTwo != null && stake > 0 && !forfeited.contains(payoutTwo.getUniqueId())) {
                    queuePendingPayout(payoutTwo.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Coin Flip"));
                }
            }
            if (payoutOne != null) {
                clearRidingSession(payoutOne.getUniqueId());
            }
            if (payoutTwo != null) {
                clearRidingSession(payoutTwo.getUniqueId());
            }
            forfeited.clear();
            cleanupPrivateMatchIfAbandoned();
        }

        private void cleanupPrivateMatchIfAbandoned() {
            if (owningPlayerId != null) {
                cleanupIdleMatch(owningPlayerId);
            }
        }

        /**
         * Kicked players forfeit unconditionally, regardless of round phase
         * -- no refund, no pending payout. Pregame, this clears their
         * seat/bet state directly. Mid-round, it just marks them so
         * handlePayout/refund helpers deny them even if they'd have won.
         */
        private void forfeitPlayer(UUID playerId) {
            clearRidingSession(playerId);
            if (gameActive) {
                if (isPve()) {
                    settleBudget(org.nc.nccasino.budget.Money.ZERO);
                }
                forfeited.add(playerId);
                if (isPve() && owningPlayerId.equals(playerId)) {
                    // A kick still forfeits, but it must also make the
                    // private no-timer match terminal. At a checkpoint
                    // discard it now; mid-flip, the committed result
                    // finishes and cashOut's forfeited guard prevents any award.
                    exitSettlementPending = true;
                    if (!revealInProgress) {
                        cashOut(playerId);
                    }
                }
                return;
            }

            forfeited.remove(playerId);

            if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId)) {
                if (chairTwoOccupant != null) {
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_TWO", null);
                    send("PLAYER_LEAVE_ONE", null);
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_ONE", null);
                }
            } else if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
                chairTwoOccupant = null;
                send("PLAYER_LEAVE_TWO", null);
            }
        }

        private void registerRidingSession(UUID playerId) {
            TerminableSession session = ridingSessions.computeIfAbsent(
                playerId,
                RidingSession::new
            );
            SessionRegistry.register(playerId, session);
        }

        private void clearRidingSession(UUID playerId) {
            TerminableSession session = ridingSessions.remove(playerId);
            if (session != null) {
                SessionRegistry.unregister(playerId, session);
            }
        }

        private final class RidingSession implements TerminableSession {
            private final UUID playerId;

            private RidingSession(UUID playerId) {
                this.playerId = playerId;
            }

            @Override
            public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
                if (!gameActive) {
                    ridingSessions.remove(playerId, this);
                    return;
                }
                if (reason == ExitReason.KICKED) {
                    forfeitPlayer(playerId);
                } else if (reason == ExitReason.PLUGIN_DISABLE) {
                    refundForShutdown();
                } else {
                    SessionRegistry.register(playerId, this);
                }
            }
        }

        private void startTimer() {
            // PvE never runs a pick timer -- the whole point of a chain is
            // letting the player decide when to bank, with no pressure to
            // pick before they're ready.
            if (isPve()) return;
            if (countdownTaskId != -1) return; // Timer is already running

            gameState = GameState.WAITING;
            timeLeft = plugin.getTimer(internalName);

            countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (timeLeft <= 0) {
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    countdownTaskId = -1;
                    beginFlip();
                    return;
                }

                send("UPDATE_TIMER", timeLeft);
                timeLeft--;

                if (timeLeft <= 3) {
                    // Only ever reached in PvP -- startTimer() returns
                    // immediately for PvE, so this repeating task never runs there.
                    playPvpCountdownSound();
                }

            }, 0L, 20L); // Run every second
        }
    }
}
