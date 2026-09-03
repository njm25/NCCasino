package org.nc.nccasino.budget;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Stable identities for one inventory-driven wager action.
 *
 * <p>Bukkit does not expose a packet or event sequence number, so this id is
 * built from two complementary parts:
 *
 * <ul>
 *   <li>The player's current game tick plus the click's own immutable
 *       semantics (raw slot, slot, click type, action, hotbar button,
 *       caller-supplied target, and normalized amount) -- this alone would
 *       already reproduce identically for a genuine duplicate dispatch of the
 *       same click.</li>
 *   <li>{@link System#identityHashCode(Object)} of the event object itself --
 *       a true replay (the very same event object handed to this method a
 *       second time, e.g. by a duplicated listener registration or a retried
 *       internal call) reproduces the identical hash every time, while two
 *       genuinely separate physical clicks are two distinct event objects and
 *       therefore (short of an astronomically unlikely hash collision) get
 *       distinct ids even when they land in the same server tick with
 *       otherwise identical characteristics -- e.g. a player rapidly
 *       committing the same chip denomination to the same bet spot twice in a
 *       row. Tick-plus-characteristics alone cannot distinguish that case,
 *       since Bukkit can process more than one packet from the same client in
 *       one tick; the worst case of a hash collision is a single spurious
 *       "click ignored" rather than a duplicated debit or credit, which is an
 *       acceptable failure direction for an identity scheme with no true
 *       source of packet-level uniqueness to draw on.</li>
 * </ul>
 *
 * <p>The table's random session id prevents collisions across reopened
 * inventories and server restarts.
 */
public final class WagerActionIds {

    private WagerActionIds() {
    }

    public static String inventoryClick(
        String sessionId,
        Player player,
        InventoryClickEvent event,
        String target,
        BigDecimal amount
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");

        String click = event.getClick() == null ? "UNKNOWN" : event.getClick().name();
        String action = event.getAction() == null ? "UNKNOWN" : event.getAction().name();
        String normalizedAmount = Money.of(amount).toPlainString();
        return sessionId
            + "-click-" + player.getUniqueId()
            + "-tick-" + player.getTicksLived()
            + "-event-" + System.identityHashCode(event)
            + "-raw-" + event.getRawSlot()
            + "-slot-" + event.getSlot()
            + "-clicktype-" + click
            + "-action-" + action
            + "-hotbar-" + event.getHotbarButton()
            + "-target-" + target
            + "-amount-" + normalizedAmount;
    }
}
