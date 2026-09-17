package org.nc.nccasino.budget;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Small bounded at-most-once guard for recent wager actions.
 *
 * <p>The durable budget store independently rejects a replayed operation id,
 * but game handlers must also stop before debiting the player or appending to
 * their own wager ledger. Tables keep one guard for their lifetime and pass
 * the same action id to both layers.
 */
public final class WagerActionGuard {

    public static final int DEFAULT_CAPACITY = 512;

    private final int capacity;
    private final Set<String> accepted = new LinkedHashSet<>();

    public WagerActionGuard() {
        this(DEFAULT_CAPACITY);
    }

    WagerActionGuard(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /** Returns {@code true} exactly once for each nonblank action id retained. */
    public synchronized boolean accept(String actionId) {
        if (actionId == null || actionId.isBlank() || !accepted.add(actionId)) {
            return false;
        }
        if (accepted.size() > capacity) {
            Iterator<String> iterator = accepted.iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }
}
