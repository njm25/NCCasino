package org.nc.nccasino.payout;

import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single rule every NCCasino wager must pass: while a player has any
 * overflow-banked winnings at all, they cannot start a new wager.
 *
 * <p>The block is deliberately currency-blind. Banked emeralds stop a diamond
 * wager, and a balance earned at Slots stops a Roulette bet, because the bank
 * is a temporary delivery exception rather than a second wallet to accumulate
 * in. Every gate call first tries to hand the whole balance back, so a player
 * who has since made room never even notices the rule.
 *
 * <p>This lives in one place on purpose. It is called from each game's single
 * currency-debit chokepoint rather than from individual bet buttons, so a game
 * cannot accidentally bypass it by growing a new betting path -- if money
 * leaves a player's balance, it went through a method that asked this first.
 */
public final class WagerGate {

    /**
     * Debounces the denial notice per player. A single click can drive several
     * debit attempts (Roulette portfolios, Blackjack splits), and repeating the
     * same line for each one reads as spam rather than as one clear refusal.
     */
    private static final Map<UUID, Long> LAST_NOTICE = new ConcurrentHashMap<>();
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;

    private WagerGate() {
    }

    /**
     * @return {@code true} when the player may wager. A {@code false} return
     *     means nothing has been debited and the caller must abandon the
     *     wager before withdrawing currency or choosing a random result.
     */
    public static boolean allowsWager(Nccasino plugin, Player player) {
        return allowsWager(plugin, player, WagerFunding.INVENTORY);
    }

    /**
     * @param funding how this wager would be debited. Recorded at the call
     *     site rather than inferred, so the irreversible cursor-drag paths are
     *     visibly gated; it never changes the decision itself
     *     ({@link WagerAdmissionPolicy}).
     * @return {@code true} when the player may wager. A {@code false} return
     *     means nothing has been debited and the caller must abandon the
     *     wager before withdrawing currency, clearing the cursor, or choosing
     *     a random result.
     */
    public static boolean allowsWager(Nccasino plugin, Player player, WagerFunding funding) {
        if (plugin == null || player == null) {
            return true;
        }
        OverflowBankService bank = plugin.getOverflowBankService();
        if (bank == null) {
            return true;
        }

        long remaining;
        try {
            remaining = bank.clearForWager(player);
        } catch (RuntimeException e) {
            // A gate failure must not hand out free wagers, but it also must
            // not brick every game: refuse this one attempt and log it.
            plugin.getLogger().severe("[NCCasino] Overflow-bank wager gate failed for "
                + player.getUniqueId() + "; refusing the wager. " + e);
            return false;
        }

        if (WagerAdmissionPolicy.admits(remaining, funding)) {
            LAST_NOTICE.remove(player.getUniqueId());
            return true;
        }

        notifyBlocked(plugin, player, remaining);
        return false;
    }

    private static void notifyBlocked(Nccasino plugin, Player player, long remaining) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = LAST_NOTICE.get(playerId);
        if (previous != null && now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        LAST_NOTICE.put(playerId, now);
        player.sendMessage(plugin.getLocalization().text(
            player, "payout.wager-blocked", "amount", remaining));
    }

    /** Drops debounce state for a player who has left. */
    public static void clearPlayerState(UUID playerId) {
        LAST_NOTICE.remove(playerId);
    }
}
