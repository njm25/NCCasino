package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, locale-neutral computation of which {@link BlackjackAction}s a
 * player can currently take and which action-row slot each one renders
 * into. No Bukkit types, no funds lookups -- callers resolve
 * afford-ability and split-eligibility (see {@link BlackjackSplitEligibility})
 * and pass the results in, so this stays trivially unit-testable and
 * reusable for both rendering and re-validating a click.
 *
 * <p>Slots are a fixed identity mapping (Hit=47/Stand=48/Double=49/Split=50,
 * see {@link BlackjackSlotLayout}), not dynamically centered -- gaps are
 * simply left empty when fewer actions are available.
 */
public final class BlackjackActionLayout {

    private static final Map<BlackjackAction, Integer> FIXED_SLOTS = Map.of(
        BlackjackAction.HIT, BlackjackSlotLayout.ACTION_HIT_SLOT,
        BlackjackAction.STAND, BlackjackSlotLayout.ACTION_STAND_SLOT,
        BlackjackAction.DOUBLE_DOWN, BlackjackSlotLayout.ACTION_DOUBLE_SLOT,
        BlackjackAction.SPLIT, BlackjackSlotLayout.ACTION_SPLIT_SLOT
    );

    private BlackjackActionLayout() {
    }

    /**
     * Determines which actions are available for an ordinary (non-split-ace)
     * hand mid-turn, in canonical Hit/Stand/Double-Down/Split order.
     *
     * @param handValue                current best total of the hand
     * @param isInitialTwoCardDecision true only on the hand's first decision (exactly two cards, no action taken yet)
     * @param canAffordDoubleDown      whether the player can cover the matching additional wager
     * @param splitEligible            whether this hand may currently split -- always false unless it's the initial two-card decision (see {@link BlackjackSplitEligibility})
     */
    public static List<BlackjackAction> availableActions(int handValue, boolean isInitialTwoCardDecision, boolean canAffordDoubleDown, boolean splitEligible) {
        List<BlackjackAction> actions = new ArrayList<>();
        if (handValue >= 21) {
            return actions;
        }
        actions.add(BlackjackAction.HIT);
        actions.add(BlackjackAction.STAND);
        if (isInitialTwoCardDecision && canAffordDoubleDown) {
            actions.add(BlackjackAction.DOUBLE_DOWN);
        }
        if (isInitialTwoCardDecision && splitEligible) {
            actions.add(BlackjackAction.SPLIT);
        }
        return actions;
    }

    /** Overload for callers that never offer Split (splitting disabled, or a hand that structurally can't split). */
    public static List<BlackjackAction> availableActions(int handValue, boolean isInitialTwoCardDecision, boolean canAffordDoubleDown) {
        return availableActions(handValue, isInitialTwoCardDecision, canAffordDoubleDown, false);
    }

    /**
     * The resolved action set for a split-ace hand's first decision (exactly
     * two cards: the Ace plus its one replacement card) -- the exact matrix
     * from the table redesign plan's "Real splitting" section:
     * <ul>
     *   <li>{@code acesHitAllowed=false, acesDoubleAllowed=false, resplitEligible=false}:
     *       callers must never reach this method in that combination -- the
     *       hand auto-completes with no action prompt at all (see the
     *       controller's split-ace activation logic), not an empty list from
     *       here.</li>
     *   <li>{@code hit=false, double=true}: Stand and Double.</li>
     *   <li>{@code hit=true, double=false}: Hit and Stand.</li>
     *   <li>{@code hit=true, double=true}: Hit, Stand, and Double.</li>
     *   <li>{@code resplitEligible=true} in any of the above: Split is added
     *       on top, never collapsing the other already-permitted actions
     *       away.</li>
     * </ul>
     * Stand is always present (timeout always behaves as Stand).
     *
     * @param acesDoubleAllowed already accounts for {@code double-after-split} -- pass false if that's disabled even when {@code aces.double=true}
     */
    public static List<BlackjackAction> splitAceActions(boolean acesHitAllowed, boolean acesDoubleAllowed, boolean resplitEligible) {
        List<BlackjackAction> actions = new ArrayList<>();
        if (acesHitAllowed) {
            actions.add(BlackjackAction.HIT);
        }
        actions.add(BlackjackAction.STAND);
        if (acesDoubleAllowed) {
            actions.add(BlackjackAction.DOUBLE_DOWN);
        }
        if (resplitEligible) {
            actions.add(BlackjackAction.SPLIT);
        }
        return actions;
    }

    /**
     * Whether a split-ace hand's first decision should auto-complete with no
     * action prompt at all: neither hitting nor doubling is permitted, and
     * there's no eligible resplit to offer either.
     */
    public static boolean splitAceHandAutoCompletes(boolean acesHitAllowed, boolean acesDoubleAllowed, boolean resplitEligible) {
        return !acesHitAllowed && !acesDoubleAllowed && !resplitEligible;
    }

    /**
     * Maps each available action to its fixed action-row slot. Returns an
     * empty map when no actions are available; unavailable actions simply
     * have no entry (no dynamic re-centering).
     */
    public static Map<BlackjackAction, Integer> layout(List<BlackjackAction> availableActionsInOrder) {
        Map<BlackjackAction, Integer> result = new LinkedHashMap<>();
        for (BlackjackAction action : availableActionsInOrder) {
            Integer slot = FIXED_SLOTS.get(action);
            if (slot != null) {
                result.put(action, slot);
            }
        }
        return result;
    }

    /** Resolves which action (if any) is bound to {@code slot} for the given available actions. */
    public static BlackjackAction actionAt(List<BlackjackAction> availableActionsInOrder, int slot) {
        Map<BlackjackAction, Integer> layout = layout(availableActionsInOrder);
        for (Map.Entry<BlackjackAction, Integer> entry : layout.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return null;
    }
}
