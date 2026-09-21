package org.nc.nccasino.games.Slots;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Injection point for randomness so spin generation is deterministically
 * testable. Production code must only ever use {@link #production()} --
 * clients must never be able to observe or influence the seed backing it.
 */
public interface SlotsRandomSource {

    /** @return a uniformly distributed value in {@code [0, bound)} */
    int nextInt(int bound);

    static SlotsRandomSource production() {
        return bound -> ThreadLocalRandom.current().nextInt(bound);
    }
}
