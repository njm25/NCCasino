package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, locale-neutral computation of which {@link BlackjackAction}s a
 * player can currently take and which action-row slot each one renders
 * into. No Bukkit types, no funds lookups -- callers resolve
 * afford-ability and pass it in as {@code canAffordDoubleDown}, so this
 * stays trivially unit-testable and reusable for both rendering and
 * re-validating a click.
 *
 * <p>Slots are a fixed identity mapping (Hit=47/Stand=48/Double=49/Split=50,
 * see {@link BlackjackSlotLayout}), not dynamically centered -- gaps are
 * simply left empty when fewer actions are available. Split gating
 * (config/hand/funds-aware) is a later phase; this phase's
 * {@link BlackjackAction} enum has no SPLIT value yet.
 */
public final class BlackjackActionLayout {

    private static final Map<BlackjackAction, Integer> FIXED_SLOTS = Map.of(
        BlackjackAction.HIT, BlackjackSlotLayout.ACTION_HIT_SLOT,
        BlackjackAction.STAND, BlackjackSlotLayout.ACTION_STAND_SLOT,
        BlackjackAction.DOUBLE_DOWN, BlackjackSlotLayout.ACTION_DOUBLE_SLOT
    );

    private BlackjackActionLayout() {
    }

    /**
     * Determines which actions are available for a hand mid-turn, in
     * canonical Hit/Stand/Double-Down order.
     *
     * @param handValue               current best total of the hand
     * @param isInitialTwoCardDecision true only on the hand's first decision (exactly two cards, no action taken yet)
     * @param canAffordDoubleDown     whether the player can cover the matching additional wager
     */
    public static List<BlackjackAction> availableActions(int handValue, boolean isInitialTwoCardDecision, boolean canAffordDoubleDown) {
        List<BlackjackAction> actions = new ArrayList<>();
        if (handValue >= 21) {
            return actions;
        }
        actions.add(BlackjackAction.HIT);
        actions.add(BlackjackAction.STAND);
        if (isInitialTwoCardDecision && canAffordDoubleDown) {
            actions.add(BlackjackAction.DOUBLE_DOWN);
        }
        return actions;
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
