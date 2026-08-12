package org.nc.nccasino.games.RockPaperScissors;

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
 * Dispatches events to a {@link RpsMatch}. In PLAYER_VS_PLAYER there is
 * exactly one shared match (a single table, same as Coin Flip). In
 * PLAYER_VS_DEALER every player who interacts with this dealer gets their
 * own private match against the house, created lazily and kept independent
 * of every other player's -- mirroring how Mines hands out a private
 * MinesTable per player rather than sharing one table.
 */
public class RockPaperScissorsServer extends Server {

    /** Stands in for a real player UUID in a match's {@code picks} when the opponent is the house. */
    private static final UUID DEALER_ID = new UUID(0L, 0L);
    private static final long PRE_REVEAL_DELAY_TICKS = 10L;
    private static final long REVEAL_WINDOW_TICKS = 70L;
    /**
     * PvE's own pacing -- there's no second human to keep pace with, and the
     * chain format means a player sits through this same beat many times in
     * a row. REVEAL_WINDOW_PVE_TICKS drives both the decisive-round fallback
     * below and the tie's dead hold before rethrow -- it's split, on the
     * client side (see RockPaperScissorsClient's PVE constants), into a
     * 24-tick cadence chant and a 16-tick post-shoot hold. That hold is
     * shorter than the loser's ~1.5s creeper-hiss sound, so the explosion
     * overlaps it -- deliberate, per explicit request. Both constants here
     * MUST stay equal to their client-side counterparts, or the client's own
     * ANIMATION_FINISHED echo and this fallback stop agreeing on timing.
     */
    private static final long PRE_REVEAL_DELAY_PVE_TICKS = 8L;
    private static final long REVEAL_WINDOW_PVE_TICKS = 40L;

    /**
     * The single shared table -- always live, exactly like Coin Flip.
     * Independent of any player's individually-chosen view below.
     */
    private final RpsMatch sharedMatch = new RpsMatch(null);

    /** One private match per player, created on first interaction, independent of the shared table. */
    private final Map<UUID, RpsMatch> pveMatches = new HashMap<>();

    /**
     * Each player's own currently-selected view of this dealer -- PvP (the
     * shared table) or PvE (their own private match). Seeded lazily from
     * the admin's configured default on first interaction, then mutable
     * per player via the in-game toggle button, independent of every other
     * player's choice.
     */
    private final Map<UUID, RpsMode> playerView = new HashMap<>();

    public RockPaperScissorsServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        RockPaperScissorsClient client = new RockPaperScissorsClient(this, player, plugin, internalName);
        return client;
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        // A delayed callback scheduled by an old Client instance (e.g. one
        // dropped by a disconnect/reconnect while its reveal animation was
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

    RpsMode viewFor(UUID playerId) {
        return playerView.computeIfAbsent(playerId, id -> plugin.getRockPaperScissorsMode(internalName));
    }

    private RpsMatch matchFor(UUID playerId) {
        if (viewFor(playerId) == RpsMode.PLAYER_VS_DEALER) {
            return pveMatches.computeIfAbsent(playerId, RpsMatch::new);
        }
        return sharedMatch;
    }

    /**
     * The inherited {@code Server.playCountdownSound()} plays to every
     * client attached to this dealer, regardless of which match they're
     * currently viewing -- fine when every viewer shares the one PvP table,
     * but the shared table's own countdown has no business being audible to
     * a player currently inside their own private PvE match. Scoped the
     * same way {@code RpsMatch.send()} scopes its PvP broadcasts.
     */
    private void playPvpCountdownSound() {
        for (Map.Entry<UUID, Client> entry : clients.entrySet()) {
            if (viewFor(entry.getKey()) != RpsMode.PLAYER_VS_PLAYER) continue;
            Player player = entry.getValue().getPlayer();
            if (player != null && SoundHelper.getSoundSafely("block.note_block.hat", player) != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Switches the requesting player's own personal view between the
     * shared PvP table and their private PvE match, independent of every
     * other player currently at this dealer. Blocked while the requester's
     * own current match is active -- leaving mid-round would either abandon
     * a live PvP opponent or orphan their own PvE round -- except at a safe
     * PvE checkpoint (awaiting a pick, chain pot live), where switching
     * first auto-cashes-out the same way closing the inventory there does,
     * rather than just refusing the switch. Cleanly exits whichever seat
     * they're in via the same eviction/promotion path a normal chair-leave
     * uses, then -- if they were seated and switching INTO their own PvE
     * match -- drops them straight into chair 1 there, rather than making
     * them click to sit down again. Switching INTO PvP never auto-seats:
     * that table is shared, so guessing a chair could put them in another
     * real player's way; landing unseated and letting them choose is the
     * only version of this convenience that can never step on someone else.
     */
    private void handleToggleMode(Client client) {
        if (!plugin.getRpsModeSwitchingEnabled(internalName)) {
            // Authoritative -- the client is expected to hide its own
            // toggle button when this is disabled, but a stale client
            // (or one bypassing the UI entirely) must still be refused here.
            client.onServerUpdate("TOGGLE_MODE_DENIED", null);
            return;
        }
        UUID playerId = client.getPlayer().getUniqueId();
        RpsMatch currentMatch = matchFor(playerId);
        if (currentMatch.gameActive) {
            currentMatch.handleCashOut(client);
            if (currentMatch.gameActive) {
                // Still active -- not a safe checkpoint (PvP, or mid-reveal).
                client.onServerUpdate("TOGGLE_MODE_DENIED", null);
                return;
            }
        }
        boolean wasSeated = currentMatch.isSeated(playerId);
        forfeitPlayer(playerId);

        RpsMode next = viewFor(playerId) == RpsMode.PLAYER_VS_PLAYER
            ? RpsMode.PLAYER_VS_DEALER
            : RpsMode.PLAYER_VS_PLAYER;
        playerView.put(playerId, next);
        client.onServerUpdate("MODE_CHANGED", next);

        RpsMatch newMatch = matchFor(playerId);
        if (wasSeated && next == RpsMode.PLAYER_VS_DEALER && newMatch.chairOneEmpty()) {
            newMatch.handle(client, "PLAYER_SIT_ONE", null);
        }
        newMatch.sendChairSnapshotTo(client);
    }

    /**
     * Kicked players forfeit unconditionally, regardless of round phase --
     * no refund, no pending payout.
     */
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
     * already-committed reveal. Safe checkpoints cash out immediately;
     * otherwise the reveal finishes and its result decides the settlement.
     */
    void requestPveExitSettlement(UUID playerId) {
        matchFor(playerId).requestExitSettlement(playerId);
    }

    /**
     * Frees a PvE player's private match once it's fully idle -- no-op in
     * PvP (the shared table isn't per-player and must never be removed) and
     * a no-op if the match is still riding out an active round, since that
     * round's own resolution path (resolveRound/refundForShutdown) is what
     * eventually leaves it idle. Without this, {@link #pveMatches} grows by
     * one permanent entry per unique player who ever interacts with this
     * dealer, mirroring the eviction Mines does per-player via removeTable.
     */
    void cleanupIdleMatch(UUID playerId) {
        RpsMatch match = pveMatches.get(playerId);
        if (match != null && !match.gameActive && !hasClient(playerId)) {
            pveMatches.remove(playerId, match);
        }
    }

    private void queuePendingPayout(UUID playerId, double amount) {
        queuePendingPayout(playerId, amount, PayoutMessages.disconnectedMidGameContext("Rock Paper Scissors"));
    }

    private void queuePendingPayout(UUID playerId, double amount, String context) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout pendingPayout = PendingPayout.create(
            playerId,
            "Rock Paper Scissors",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            context
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(pendingPayout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Rock Paper Scissors pending payout failed to persist for " + playerId + ".");
        }
    }

    /**
     * One table's worth of round state and logic -- two chairs, a bet, a
     * pick phase, tie-rethrow, timeout, and payout. Instantiated once as
     * the shared PvP table ({@code owningPlayerId == null}, updates go to
     * every client via the outer {@code broadcastUpdate}), or once per
     * player in PvE ({@code owningPlayerId} set, updates go only to that
     * player's own client -- chair 2 is always the house).
     */
    /** Fixed compounding multiplier for a PvE chain win -- bakes in the same 1% house edge convention as Mines/Dragon Descent's 0.99 multiplier. */
    private static final double CHAIN_MULTIPLIER = 1.98;

    private final class RpsMatch {

        private final UUID owningPlayerId;

        private int countdownTaskId = -1;
        private int timeLeft = 0;

        /**
         * Bumped once per accepted bet (never on a tie's rethrow, which
         * stays the same round). Deferred resolution callbacks -- the
         * reveal's own fallback timer and the client's echoed
         * ANIMATION_FINISHED -- capture this at schedule/broadcast time and
         * compare it back before resolving, so a callback that outlives its
         * round (e.g. a delayed packet arriving after a new round already
         * started) becomes a safe no-op instead of resolving the wrong round.
         */
        private int roundToken = 0;

        private Player chairOneOccupant;
        private Player chairTwoOccupant;
        /** Long, not int -- a PvP pot doubles two accepted stakes together, which can exceed Integer.MAX_VALUE even though neither individual stake does. */
        private long betAmount;
        /** The player's own stake before the house/opponent match, captured once per accepted bet -- used to report true profit even after a chain has compounded the pot past a simple double. */
        private long originalWager;
        /** PvE-only: consecutive chain wins since the bet was accepted. Never incremented by a tie. */
        private int chainWins;
        private boolean gameActive;
        private boolean revealInProgress;
        /** PvE only: the owner left, so the current reveal is the final one. */
        private boolean exitSettlementPending;
        private Integer committedWinner;
        private final Map<UUID, Throw> picks = new HashMap<>();
        private final Set<UUID> forfeited = new HashSet<>();
        private final Map<UUID, TerminableSession> ridingSessions = new HashMap<>();

        private RpsMatch(UUID owningPlayerId) {
            this.owningPlayerId = owningPlayerId;
        }

        private boolean isPve() {
            return owningPlayerId != null;
        }

        /** Broadcasts to every client of the dealer (PvP), or pushes straight to the one owning client (PvE). */
        /**
         * PvP and PvE share a single Client instance per player (whichever
         * one they currently have open), so broadcastUpdate() -- which
         * reaches every client attached to this dealer, PvP or PvE viewer
         * alike -- is NOT safe to use for the shared table: a player
         * currently looking at their own private PvE match would still
         * receive the PvP table's chair-leave/reveal/etc. broadcasts and
         * apply them to fields describing their unrelated PvE state (a real
         * crash: PLAYER_LEAVE_ONE on a client whose own chairOneOccupant is
         * null). Only clients whose current view is actually PvP get this.
         */
        private void send(String eventType, Object data) {
            if (owningPlayerId == null) {
                for (Map.Entry<UUID, Client> entry : clients.entrySet()) {
                    if (viewFor(entry.getKey()) == RpsMode.PLAYER_VS_PLAYER) {
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
                                // No second human to accept -- the house
                                // takes the other side of the bet immediately.
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
                case "PLAYER_CHOOSE":
                    handlePlayerChoose(client, data);
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
            UUID viewerId = client.getPlayer().getUniqueId();
            UUID opponentId = opponentOf(viewerId);
            Object[] chairs = {
                chairOneOccupant,
                chairTwoOccupant,
                betAmount,
                gameActive,
                timeLeft,
                picks.containsKey(viewerId),
                opponentId != null && picks.containsKey(opponentId),
            };
            client.onServerUpdate("GET_CHAIRS", chairs);
        }

        /** Whether playerId currently occupies either chair of this match. */
        private boolean isSeated(UUID playerId) {
            return (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId))
                || (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId));
        }

        private boolean chairOneEmpty() {
            return chairOneOccupant == null;
        }

        /**
         * Shared tail of both "player 2 accepted" and "the house
         * auto-accepted" -- opens the pick phase. PvP doubles the pot (the
         * opponent/house matches the stake, fair 2x on a win); PvE does not
         * -- the whole payout curve there is CHAIN_MULTIPLIER itself
         * (wager, then wager*1.98 on the first win, wager*1.98^2 on the
         * second, ...), so starting from an already-doubled pot would pay
         * 3.96x on a single win instead of the intended 1.98x.
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
            picks.clear();
            startTimer();
        }

        /**
         * Whether the just-applied win (chainWins/betAmount already reflect
         * it, both exact and never clamped -- see below) must stop the
         * chain here instead of offering another round -- either the
         * admin-configured cap (a cap <= 0, the -1 default, means
         * unbounded), or offering one more round could produce a win that
         * exceeds RpsPayoutMath.MAX_SAFE_POT, the currency system's
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
        private boolean chainCapped() {
            int cap = plugin.getRpsMaxChainRounds(internalName);
            if (cap > 0 && chainWins >= cap) {
                return true;
            }
            return RpsPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(betAmount, CHAIN_MULTIPLIER);
        }

        private void handlePlayerChoose(Client client, Object data) {
            if (!gameActive || revealInProgress) return;
            if (!(data instanceof Throw chosen)) return;

            UUID chooserId = client.getPlayer().getUniqueId();
            boolean isChairOne = chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(chooserId);
            boolean isChairTwo = chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(chooserId);
            if (!isChairOne && !isChairTwo) return;
            if (picks.containsKey(chooserId)) return;

            picks.put(chooserId, chosen);

            if (isPve() && isChairOne) {
                // The house only ever throws in direct response to the
                // player's own pick -- never ahead of time -- so there's
                // nothing to notify and no opponent client to look up.
                picks.put(DEALER_ID, randomThrow());
            } else {
                UUID opponentId = opponentOf(chooserId);
                if (opponentId != null) {
                    Client opponentClient = clients.get(opponentId);
                    if (opponentClient != null) {
                        opponentClient.onServerUpdate("OPPONENT_LOCKED_IN", null);
                    }
                }
            }

            evaluatePicks();
        }

        private UUID opponentOf(UUID playerId) {
            if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId)) {
                return chairTwoKey();
            }
            if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
                return chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
            }
            return null;
        }

        /** Chair 2's identity for {@code picks} lookups: the real occupant in PvP, the dealer sentinel in PvE. */
        private UUID chairTwoKey() {
            if (chairTwoOccupant != null) {
                return chairTwoOccupant.getUniqueId();
            }
            return isPve() ? DEALER_ID : null;
        }

        /**
         * Compares both picks once they're both in. A tie clears both picks
         * and lets the same accepted-bet round continue (pot untouched)
         * rather than resolving anything, so the disconnect policy can keep
         * treating the whole span as a single "ride to result" session.
         */
        private void evaluatePicks() {
            if (chairOneOccupant == null) return;
            UUID twoKey = chairTwoKey();
            if (twoKey == null) return;

            Throw one = picks.get(chairOneOccupant.getUniqueId());
            Throw two = picks.get(twoKey);
            if (one == null || two == null) return;
            revealInProgress = true;

            // Stop the pick timer as soon as both choices lock. Hold for a
            // short anticipation beat before beginning the reveal cadence.
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> beginReveal(one, two),
                isPve() ? PRE_REVEAL_DELAY_PVE_TICKS : PRE_REVEAL_DELAY_TICKS
            );
        }

        private void beginReveal(Throw one, Throw two) {
            if (!gameActive || !revealInProgress) return;
            if (one == two) {
                send("TIE_REVEAL", new Object[]{one, two});
                // Deliberately not resolved from a client callback the way
                // the decisive branch is: there's no payout at stake on a
                // tie, so a single fixed delay (matching the client's
                // cycle-then-settle animation length) is all that's needed
                // before the same accepted round throws again.
                Bukkit.getScheduler().runTaskLater(plugin, this::rethrow, isPve() ? REVEAL_WINDOW_PVE_TICKS : REVEAL_WINDOW_TICKS);
                return;
            }

            int winner = one.beats(two) ? 0 : 1;
            committedWinner = winner;

            int token = roundToken;
            send("REVEAL", new Object[]{one, two, winner, token});
            // Authoritative fallback, same safety net Coin Flip uses:
            // resolves the round even if every client disconnects before
            // its local reveal animation reports back. Guarded by roundToken
            // so a fallback scheduled for a since-superseded round can't
            // fire. Routed through settleRound (not resolveRound directly)
            // so a PvE win still advances the chain even when this fallback
            // -- rather than the client's own ANIMATION_FINISHED echo --
            // is what actually resolves it.
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
         * Fires ~70 ticks after a tied REVEAL -- clears both picks and lets
         * the same accepted round throw again with a fresh pick timer. A
         * no-op if the round was already torn down (kick/shutdown) during
         * the reveal.
         */
        private void rethrow() {
            if (!gameActive) return;

            if (isPve() && RpsExitSettlementPolicy.afterReveal(
                    RpsExitSettlementPolicy.Outcome.TIE,
                    exitSettlementPending,
                    false
                ) == RpsExitSettlementPolicy.Action.CASH_OUT) {
                // A tie does not change the chain pot. Once the owner has
                // left, bank that existing amount instead of reopening an
                // untimed rethrow with no client attached.
                revealInProgress = false;
                cashOut(owningPlayerId);
                return;
            }

            revealInProgress = false;
            picks.clear();
            send("RETHROW", null);
            if (!isPve()) {
                timeLeft = plugin.getTimer(internalName);
                startTimer();
            }
        }

        /**
         * Routes a decisive round's outcome. A PvE win always compounds the
         * pot at the fixed 1%-edge multiplier first -- including the win
         * that meets the cap, otherwise that capping win would be paid out
         * at the PREVIOUS win's pot instead of its own (a cap of 1 would
         * pay back only the bare wager; a cap of 2 would pay 1.98x instead
         * of 1.98^2x). PvP, a PvE loss, or a PvE win that (now) meets the
         * cap all fall through to resolveRound() to pay out; a PvE win
         * still under the cap instead reopens the pick phase via advanceChain().
         */
        private void settleRound(int winner) {
            if (!gameActive) return;

            if (isPve() && winner == 0) {
                chainWins++;
                betAmount = RpsPayoutMath.compound(betAmount, CHAIN_MULTIPLIER);
                boolean cappedWin = chainCapped();
                RpsExitSettlementPolicy.Action action = RpsExitSettlementPolicy.afterReveal(
                    RpsExitSettlementPolicy.Outcome.WIN,
                    exitSettlementPending,
                    cappedWin
                );
                if (action == RpsExitSettlementPolicy.Action.CASH_OUT) {
                    // The committed win still earns this round's multiplier,
                    // but an owner who left must not be advanced into another
                    // untimed decision point.
                    revealInProgress = false;
                    cashOut(owningPlayerId);
                    return;
                }
                if (action == RpsExitSettlementPolicy.Action.CONTINUE) {
                    advanceChain();
                    return;
                }
            }
            resolveRound(winner);
        }

        /**
         * Reopens the pick phase after a PvE win under the chain cap --
         * same shape as a tie's rethrow() (same accepted bet, no timer).
         * Called only after settleRound() has already applied this win's
         * multiplier and confirmed the cap isn't met.
         *
         * Unlike a tie, a decisive reveal always has two independent
         * completions racing for the same token: the client's own
         * ANIMATION_FINISHED echo, and beginReveal's server-side fallback
         * timer, both guarded by "token == roundToken" so whichever loses
         * the race becomes a safe no-op. resolveRound() satisfies that
         * guard by setting gameActive=false, but this path deliberately
         * keeps the round alive -- so it must invalidate the token itself,
         * or the loser of the race still passes the check and re-runs this
         * exact win a second time (double payout multiplier, doubled chat
         * message, and a stale duplicate that can fire arbitrarily later
         * and force a bogus resolveRound mid-chain).
         */
        private void advanceChain() {
            if (!gameActive) return;

            revealInProgress = false;
            committedWinner = null;
            roundToken++;
            picks.clear();
            send("CHAIN_WIN", new Object[]{chainWins, betAmount});
        }

        /**
         * Cashes out the current chain pot on request, valid only while PvE
         * and awaiting a pick. betAmount is never doubled at accept for PvE
         * (see beginActiveRound), so it already equals originalWager before
         * any win and the compounded pot after one -- no special-casing
         * needed, the exact wager comes back on a pre-first-win cash-out.
         */
        private void handleCashOut(Client client) {
            if (!isPve() || !gameActive || revealInProgress) return;
            UUID playerId = client.getPlayer().getUniqueId();
            if (chairOneOccupant == null || !chairOneOccupant.getUniqueId().equals(playerId)) return;

            cashOut(playerId);
        }

        /** Authoritative PvE cash-out shared by clicks, closes, and disconnect settlement. */
        private void cashOut(UUID playerId) {
            if (!isPve() || !gameActive) return;
            if (chairOneOccupant == null || !chairOneOccupant.getUniqueId().equals(playerId)) return;

            send("ANIMATION_FINISHED", 0);
            long wager = originalWager;
            long payout = betAmount;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();

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
         * settle immediately. During a reveal, leave the authoritative result
         * untouched and let rethrow/settleRound finish it exactly once.
         */
        private void requestExitSettlement(UUID playerId) {
            if (!isPve() || !gameActive || !owningPlayerId.equals(playerId)) return;

            exitSettlementPending = true;
            if (!revealInProgress) {
                cashOut(playerId);
            }
        }

        /**
         * Authoritative resolution for a decisive round, called either by
         * whichever seated client's local reveal animation reports back
         * first, or by the server's own fixed-delay fallback timer
         * scheduled alongside the REVEAL broadcast -- whichever fires first
         * wins, the other becomes a safe no-op via the gameActive guard.
         */
        private void resolveRound(int winner) {
            if (!gameActive) return;

            // The only way settleRound() ever reaches resolveRound() with a
            // PvE win is chainCapped() having been true -- a genuine
            // advanceChain-eligible win never gets here.
            boolean cappedWin = isPve() && winner == 0;
            send("ANIMATION_FINISHED", winner);
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            long payout = betAmount;
            long wager = originalWager;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();
            handlePayout(payoutOne, payoutTwo, payout, winner, wager, cappedWin);
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

        /**
         * Neither seated player locked in a choice before the pick timer
         * expired -- there's no one to award the pot to, so both stakes are
         * simply refunded and the round is void.
         */
        private void voidRound() {
            if (!gameActive) return;

            send("ROUND_VOID", null);
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            long stake = betAmount / 2;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();

            refundStakeIfDue(payoutOne, stake);
            refundStakeIfDue(payoutTwo, stake);

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

        private void refundStakeIfDue(Player seatedPlayer, long stake) {
            if (seatedPlayer == null || stake <= 0 || forfeited.contains(seatedPlayer.getUniqueId())) {
                return;
            }
            Player onlinePlayer = Bukkit.getPlayer(seatedPlayer.getUniqueId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                creditPlayer(onlinePlayer, stake);
            } else {
                queuePendingPayout(seatedPlayer.getUniqueId(), stake, PayoutMessages.disconnectedMidGameContext("Rock Paper Scissors"));
            }
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

        private void handlePayout(Player one, Player two, long payout, int winner, long wager, boolean cappedWin) {
            UUID winnerId = (winner == 0)
                ? (one != null ? one.getUniqueId() : null)
                : (two != null ? two.getUniqueId() : null);
            UUID loserId = (winner == 0)
                ? (two != null ? two.getUniqueId() : null)
                : (one != null ? one.getUniqueId() : null);

            // Resolve fresh Player references by UUID rather than trusting
            // the cached chair-occupant objects, which can go stale across
            // a reconnect while the game was active (seating is locked
            // while gameActive, so those fields can't be refreshed
            // mid-round).
            if (winnerId != null && payout > 0 && !forfeited.contains(winnerId)) {
                Player winnerPlayer = Bukkit.getPlayer(winnerId);
                if (winnerPlayer != null && winnerPlayer.isOnline()) {
                    if (cappedWin) {
                        switch (plugin.getPreferences(winnerId).getMessageSetting()) {
                            case STANDARD:
                            case VERBOSE:
                                // resolveRound only ever sees a PvE win once
                                // chainCapped() was true, for one of two
                                // reasons -- distinguish them so a pot that
                                // stopped because one more round could have
                                // exceeded the representable ceiling doesn't
                                // claim it hit an admin-configured round
                                // count instead (which may not even be set).
                                if (RpsPayoutMath.wouldExceedSafeMaxIfCompoundedAgain(payout, CHAIN_MULTIPLIER)) {
                                    winnerPlayer.sendMessage(plugin.getLocalization().text(
                                        winnerPlayer, "rock-paper-scissors.max-pot-hit"));
                                } else {
                                    winnerPlayer.sendMessage(plugin.getLocalization().text(
                                        winnerPlayer,
                                        "rock-paper-scissors.max-chain-hit",
                                        "rounds", plugin.getRpsMaxChainRounds(internalName)
                                    ));
                                }
                                break;
                            case NONE:
                                break;
                        }
                    }
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
                    // player still learns how the committed reveal ended on
                    // their next join; no currency is deposited for amount 0.
                    queuePendingPayout(loserId, 0);
                }
            }
        }

        /**
         * Settles an in-flight round during shutdown. Once a winner has
         * been committed (decisive reveal or a timeout forfeit), that
         * authoritative payout is saved; before that, each side's stake is
         * refunded -- this covers the pick phase and any tie-rethrow loop
         * the round was in.
         */
        private void refundForShutdown() {
            if (!gameActive) {
                return;
            }

            long payout = betAmount;
            // In PvE there's no second real party to split the pot with --
            // a chain's compounded pot is entirely the player's own stake
            // and winnings, so the whole thing is refunded rather than half.
            long stake = isPve() ? payout : payout / 2;
            Integer winner = committedWinner;
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            originalWager = 0;
            chainWins = 0;
            exitSettlementPending = false;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();

            if (winner != null) {
                // settleRound() applies this round's chain multiplier before
                // paying out a PvE win; a shutdown caught between committing
                // the winner (beginReveal) and settleRound() running must
                // apply that same multiplier itself, or the saved payout is
                // short by 1.98x.
                boolean pveWin = isPve() && winner == 0;
                long winnerPayout = pveWin ? RpsPayoutMath.compound(payout, CHAIN_MULTIPLIER) : payout;
                Player winningPlayer = winner == 0 ? payoutOne : payoutTwo;
                if (winningPlayer != null && winnerPayout > 0
                    && !forfeited.contains(winningPlayer.getUniqueId())) {
                    queuePendingPayout(
                        winningPlayer.getUniqueId(),
                        winnerPayout,
                        PayoutMessages.committedResultContext("Rock Paper Scissors")
                    );
                } else if (isPve() && winner == 1 && payoutOne != null
                    && !forfeited.contains(payoutOne.getUniqueId())) {
                    // PvE loss: there's no winning player to pay, but the
                    // player still needs to learn the round's outcome on
                    // next join, same as handlePayout()'s zero-value record.
                    queuePendingPayout(
                        payoutOne.getUniqueId(),
                        0,
                        PayoutMessages.committedResultContext("Rock Paper Scissors")
                    );
                }
                if (!isPve()) {
                    // PvP: the winner branch above only pays the winning
                    // occupant -- the non-forfeited loser still needs a
                    // zero-value outcome-only record so they learn how the
                    // round they rode into ended, same as the normal
                    // (non-shutdown) offline-loser path in handlePayout().
                    Player losingPlayer = winner == 0 ? payoutTwo : payoutOne;
                    if (losingPlayer != null && !forfeited.contains(losingPlayer.getUniqueId())) {
                        queuePendingPayout(
                            losingPlayer.getUniqueId(),
                            0,
                            PayoutMessages.committedResultContext("Rock Paper Scissors")
                        );
                    }
                }
            } else {
                if (payoutOne != null && stake > 0 && !forfeited.contains(payoutOne.getUniqueId())) {
                    queuePendingPayout(payoutOne.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Rock Paper Scissors"));
                }
                if (payoutTwo != null && stake > 0 && !forfeited.contains(payoutTwo.getUniqueId())) {
                    queuePendingPayout(payoutTwo.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Rock Paper Scissors"));
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
         * seat/bet state directly (bypassing the normal PLAYER_LEAVE
         * broadcast so their own already-removed client can't self-refund).
         * Mid-round, it just marks them so handlePayout/refund helpers deny
         * them even if they'd have won -- the round's own post-payout
         * cleanup frees the seat once it ends.
         */
        private void forfeitPlayer(UUID playerId) {
            clearRidingSession(playerId);
            if (gameActive) {
                forfeited.add(playerId);
                if (isPve() && owningPlayerId.equals(playerId)) {
                    // A kick still forfeits, but it must also make the private
                    // no-timer match terminal. At a checkpoint discard it now;
                    // during a reveal, the committed loss/win/tie finishes and
                    // cashOut's forfeited guard prevents any award.
                    exitSettlementPending = true;
                    if (!revealInProgress) {
                        cashOut(playerId);
                    }
                }
                return;
            }

            forfeited.remove(playerId);
            picks.remove(playerId);

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
                    handleTimeout();
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

        /**
         * The pick timer ran out (PvP only -- PvE never schedules one). If
         * exactly one seated player locked in a throw, they win by forfeit;
         * if neither did, there's no one to award the pot to, so the round
         * is voided instead.
         */
        private void handleTimeout() {
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }

            UUID oneId = chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
            UUID twoId = chairTwoOccupant != null ? chairTwoOccupant.getUniqueId() : null;
            boolean oneChose = oneId != null && picks.containsKey(oneId);
            boolean twoChose = twoId != null && picks.containsKey(twoId);

            if (oneChose == twoChose) {
                voidRound();
                return;
            }

            // Block any pick that was still in flight when the timer hit
            // zero -- otherwise a late arrival could pair up with the
            // forfeiting side's pick, trigger its own tie/rethrow via
            // evaluatePicks, and leave this forfeit's committedWinner stale
            // for the fallback resolveRound scheduled below to wrongly act on.
            revealInProgress = true;
            int winner = oneChose ? 0 : 1;
            committedWinner = winner;
            int token = roundToken;
            send("FORFEIT_TIMEOUT", winner);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (token == roundToken) {
                    resolveRound(committedWinner != null ? committedWinner : winner);
                }
            }, 20L);
        }

        private Throw randomThrow() {
            Throw[] values = Throw.values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }
    }
}
